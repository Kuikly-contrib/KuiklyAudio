import { media } from '@kit.MediaKit';
import { audio } from '@kit.AudioKit';
import { avSession } from '@kit.AVSessionKit';
import { backgroundTaskManager } from '@kit.BackgroundTasksKit';
import { wantAgent, WantAgent } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';

/**
 * 鸿蒙端音频播放 Module 实现
 *
 * 基于 HarmonyOS AVPlayer 实现，支持：
 * - 基础播放控制（play/pause/resume/stop/seekTo）
 * - 播放进度回调
 * - 播放列表管理
 * - 后台播放（长时任务）
 * - 音量/倍速控制
 * - 锁屏控制（AVSession）
 *
 * AVPlayer 状态机：idle -> initialized -> prepared -> playing -> paused -> playing
 *                                                            -> completed
 *                                                  -> stopped -> idle
 *                                                  -> error -> idle (after reset)
 *
 * 关键约束：
 * - url 只能在 idle 状态下设置（设 url 触发 idle -> initialized -> prepared）
 * - setVolume/setSpeed/setLoop 只在 prepared/playing/paused/completed 下可用
 * - error 后必须 reset() 才能重新使用
 */
export class KRAudioPlayerModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRAudioPlayerModule';

  private avPlayer: media.AVPlayer | null = null;
  private progressTimer: number | null = null;

  // 待播放的 url，由 playUrl() 设置，在 stateChange idle 回调中消费
  private pendingUrl: string = '';
  // 待设置的 url 对应的播放列表 index
  private pendingIndex: number = -1;

  // 长期回调
  private timeUpdateCallback: KuiklyRenderCallback | null = null;
  private playStateCallback: KuiklyRenderCallback | null = null;
  private errorCallback: KuiklyRenderCallback | null = null;
  private playlistIndexCallback: KuiklyRenderCallback | null = null;

  // 播放列表
  private playlist: AudioItemInfo[] = [];
  private currentIndex: number = 0;
  private playMode: string = 'sequence';
  private backgroundPlayEnabled: boolean = false;
  private lastReportedState: string = 'idle';

  // AVSession
  private session: avSession.AVSession | null = null;

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    const paramsStr = params as string;
    switch (method) {
      case 'play':
        this.handlePlay(paramsStr);
        break;
      case 'pause':
        this.handlePause();
        break;
      case 'resume':
        this.handleResume();
        break;
      case 'stop':
        this.handleStop();
        break;
      case 'seekTo':
        this.handleSeekTo(paramsStr);
        break;
      case 'setVolume':
        this.handleSetVolume(paramsStr);
        break;
      case 'setSpeed':
        this.handleSetSpeed(paramsStr);
        break;
      case 'setPlaylist':
        this.handleSetPlaylist(paramsStr);
        break;
      case 'next':
        this.handleNext();
        break;
      case 'previous':
        this.handlePrevious();
        break;
      case 'setPlayMode':
        this.handleSetPlayMode(paramsStr);
        break;
      case 'enableBackgroundPlay':
        this.handleEnableBackgroundPlay(paramsStr);
        break;
      case 'getPlayState':
        return this.lastReportedState;
      case 'getCurrentPosition':
        return this.avPlayer ? Math.floor(this.avPlayer.currentTime || 0).toString() : '0';
      case 'getDuration':
        return this.avPlayer ? Math.floor(this.avPlayer.duration || 0).toString() : '0';
      case 'onTimeUpdate':
        this.timeUpdateCallback = callback;
        break;
      case 'onPlayStateChanged':
        this.playStateCallback = callback;
        break;
      case 'onError':
        this.errorCallback = callback;
        break;
      case 'getVolume':
        return this.avPlayer ? this.avPlayer.volume.toString() : '1.0';
      case 'getSpeed':
        return this.avPlayer ? this.avPlayer.speed.toString() : '1.0';
      case 'onPlaylistIndexChanged':
        this.playlistIndexCallback = callback;
        break;
    }
    return null;
  }

  // ======================== 播放控制 ========================

  /**
   * 核心：请求播放指定 url
   * 统一入口：设置 pendingUrl，然后驱动 AVPlayer 进入 idle 状态来消费它
   */
  private playUrl(url: string, index: number = -1): void {
    console.info(`KRAudioPlayerModule: playUrl url=${url}, index=${index}`);
    this.pendingUrl = url;
    this.pendingIndex = index;

    if (!this.avPlayer) {
      // 首次：创建 AVPlayer，创建后进入 idle，主动消费 pendingUrl
      this.ensurePlayer().then(() => {
        // ensurePlayer 完成后，AVPlayer 处于 idle 状态
        // stateChange idle 可能已经在 ensurePlayer 中触发过了（回调注册前就已到 idle）
        // 所以这里主动消费 pendingUrl
        if (this.pendingUrl) {
          this.consumePendingUrl();
        }
      });
      return;
    }

    // AVPlayer 已存在，需要回到 idle 状态才能设新 url
    const state = this.lastReportedState;
    if (state === 'idle') {
      // 已经在 idle，直接设置 url
      this.consumePendingUrl();
    } else if (state === 'initialized') {
      // initialized 状态也不能设 url，需要先 reset
      this.avPlayer.reset().catch((e: Error) => {
        console.error('KRAudioPlayerModule: reset from initialized failed', e);
      });
      // reset 后 stateChange 会到 idle，idle 中会消费 pendingUrl
    } else if (state === 'error') {
      // error 状态必须 reset
      this.avPlayer.reset().catch((e: Error) => {
        console.error('KRAudioPlayerModule: reset from error failed', e);
      });
    } else if (state === 'stopped') {
      // stopped -> reset -> idle
      this.avPlayer.reset().catch((e: Error) => {
        console.error('KRAudioPlayerModule: reset from stopped failed', e);
      });
    } else {
      // playing/paused/prepared/completed -> 先 stop 再 reset -> idle
      try {
        this.avPlayer.stop();
      } catch (e) {
        // ignore
      }
      this.avPlayer.reset().catch((e: Error) => {
        console.error('KRAudioPlayerModule: reset failed', e);
      });
    }
  }

  /**
   * 在 idle 状态下消费 pendingUrl，设置 url 触发 initialized -> prepared -> playing
   */
  private consumePendingUrl(): void {
    const player = this.avPlayer;
    const url = this.pendingUrl;
    if (!player || !url) return;

    this.pendingUrl = '';
    try {
      player.url = url;
      console.info(`KRAudioPlayerModule: set url success, url=${url}`);
    } catch (e) {
      console.error(`KRAudioPlayerModule: set url failed`, e);
    }
  }

  private handlePlay(params: string): void {
    const json = this.parseParams(params);
    if (!json || !json.url) return;

    this.playUrl(json.url);

    if (this.playlist.length === 0) {
      this.playlist.push({
        url: json.url,
        title: json.title || '',
        artist: json.artist || '',
        coverUrl: json.coverUrl || ''
      });
    }
  }

  private handlePause(): void {
    if (this.isPlayerReady()) {
      this.avPlayer?.pause();
    }
  }

  private handleResume(): void {
    if (this.isPlayerReady()) {
      this.avPlayer?.play();
    }
  }

  private handleStop(): void {
    this.stopProgressUpdates();
    try {
      this.avPlayer?.stop();
    } catch (e) {
      // ignore
    }
  }

  private handleSeekTo(params: string): void {
    if (!this.isPlayerReady()) return;
    const json = this.parseParams(params);
    if (!json) return;
    const position = parseInt(json.position || '0');
    this.avPlayer?.seek(position);
  }

  // ======================== 音量 & 倍速 ========================

  private handleSetVolume(params: string): void {
    if (!this.isPlayerReady()) return;
    const json = this.parseParams(params);
    if (!json) return;
    const volume = Math.max(0, Math.min(1, parseFloat(json.volume || '1.0')));
    if (this.avPlayer) {
      this.avPlayer.setVolume(volume);
    }
  }

  private handleSetSpeed(params: string): void {
    if (!this.isPlayerReady()) return;
    const json = this.parseParams(params);
    if (!json) return;
    const speed = Math.max(0.5, Math.min(2.0, parseFloat(json.speed || '1.0')));
    if (this.avPlayer) {
      this.avPlayer.setSpeed(this.mapSpeed(speed));
    }
  }

  /**
   * AVPlayer 是否处于可操作状态（prepared/playing/paused/completed）
   * idle/initialized/stopped/error 状态下不能调用 setVolume/setSpeed/pause/play/seek 等
   */
  private isPlayerReady(): boolean {
    const state = this.lastReportedState;
    return state === 'buffering' || state === 'playing' || state === 'paused' || state === 'completed';
  }

  private mapSpeed(speed: number): media.PlaybackSpeed {
    if (speed <= 0.75) return media.PlaybackSpeed.SPEED_FORWARD_0_75_X;
    if (speed <= 1.0) return media.PlaybackSpeed.SPEED_FORWARD_1_00_X;
    if (speed <= 1.25) return media.PlaybackSpeed.SPEED_FORWARD_1_25_X;
    if (speed <= 1.75) return media.PlaybackSpeed.SPEED_FORWARD_1_75_X;
    return media.PlaybackSpeed.SPEED_FORWARD_2_00_X;
  }

  // ======================== 播放列表 ========================

  private handleSetPlaylist(params: string): void {
    const json = this.parseParams(params);
    if (!json) return;

    const items: AudioItemInfo[] = JSON.parse(json.items || '[]');
    const startIndex = parseInt(json.startIndex || '0');

    this.playlist = items;
    this.currentIndex = startIndex;

    if (this.currentIndex < this.playlist.length) {
      this.playAtIndex(this.currentIndex);
    }
  }

  private handleNext(): void {
    if (this.playlist.length === 0) return;

    let nextIndex = this.currentIndex + 1;
    if (nextIndex >= this.playlist.length) {
      if (this.playMode === 'loop') {
        nextIndex = 0;
      } else {
        return;
      }
    }
    this.playAtIndex(nextIndex);
  }

  private handlePrevious(): void {
    if (this.playlist.length === 0) return;

    let prevIndex = this.currentIndex - 1;
    if (prevIndex < 0) {
      if (this.playMode === 'loop') {
        prevIndex = this.playlist.length - 1;
      } else {
        return;
      }
    }
    this.playAtIndex(prevIndex);
  }

  private playAtIndex(index: number): void {
    if (index < 0 || index >= this.playlist.length) return;

    this.currentIndex = index;
    const item = this.playlist[index];
    this.playUrl(item.url, index);
  }

  private handleSetPlayMode(params: string): void {
    const json = this.parseParams(params);
    if (!json) return;
    this.playMode = json.mode || 'sequence';

    if (this.avPlayer && this.isPlayerReady()) {
      this.avPlayer.loop = (this.playMode === 'single');
    }
  }

  // ======================== 后台播放 ========================

  private handleEnableBackgroundPlay(params: string): void {
    const json = this.parseParams(params);
    if (!json) return;
    this.backgroundPlayEnabled = json.enable === '1';

    if (this.backgroundPlayEnabled) {
      this.startBackgroundTask();
      this.setupAVSession();
    } else {
      this.stopBackgroundTask();
      this.teardownAVSession();
    }
  }

  private async startBackgroundTask(): Promise<void> {
    try {
      const wantAgentInfo: wantAgent.WantAgentInfo = {
        wants: [
          {
            bundleName: '',
            abilityName: ''
          }
        ],
        actionType: wantAgent.OperationType.START_ABILITY,
        requestCode: 0,
        actionFlags: [wantAgent.WantAgentFlags.UPDATE_PRESENT_FLAG]
      };
      const agent: WantAgent = await wantAgent.getWantAgent(wantAgentInfo);
      await backgroundTaskManager.startBackgroundRunning(
        getContext(this) as Context,
        backgroundTaskManager.BackgroundMode.AUDIO_PLAYBACK,
        agent
      );
    } catch (e) {
      console.error('KRAudioPlayerModule: startBackgroundTask failed', e);
    }
  }

  private stopBackgroundTask(): void {
    try {
      backgroundTaskManager.stopBackgroundRunning(getContext(this) as Context);
    } catch (e) {
      console.error('KRAudioPlayerModule: stopBackgroundTask failed', e);
    }
  }

  private async setupAVSession(): Promise<void> {
    try {
      this.session = await avSession.createAVSession(getContext(this) as Context, 'KuiklyAudio', 'audio');
      await this.session.activate();

      this.session.on('play', () => this.handleResume());
      this.session.on('pause', () => this.handlePause());
      this.session.on('playNext', () => this.handleNext());
      this.session.on('playPrevious', () => this.handlePrevious());
      this.session.on('seek', (time: number) => {
        if (this.isPlayerReady()) {
          this.avPlayer?.seek(time);
        }
      });
    } catch (e) {
      console.error('KRAudioPlayerModule: setupAVSession failed', e);
    }
  }

  private teardownAVSession(): void {
    try {
      if (this.session) {
        this.session.deactivate();
        this.session.destroy();
        this.session = null;
      }
    } catch (e) {
      console.error('KRAudioPlayerModule: teardownAVSession failed', e);
    }
  }

  // ======================== Player 管理 ========================

  private async ensurePlayer(): Promise<void> {
    if (this.avPlayer) return;

    this.avPlayer = await media.createAVPlayer();
    if (!this.avPlayer) return;

    this.avPlayer.on('stateChange', async (state: string) => {
      console.info(`KRAudioPlayerModule: stateChange -> ${state}`);
      switch (state) {
        case 'idle':
          this.reportStateIfChanged('idle');
          // idle 状态下消费 pendingUrl
          if (this.pendingUrl) {
            this.consumePendingUrl();
          }
          break;
        case 'initialized':
          // url 设置后自动进入 initialized，等待 prepared
          break;
        case 'prepared':
          // prepared 表示数据已缓冲准备好，映射为 buffering（等待真正 playing）
          this.reportStateIfChanged('buffering');
          if (this.playMode === 'single' && this.avPlayer) {
            this.avPlayer.loop = true;
          }
          this.avPlayer?.play();
          // 通知播放列表 index 变化
          if (this.pendingIndex >= 0 && this.playlistIndexCallback) {
            this.playlistIndexCallback({
              index: this.pendingIndex.toString(),
              url: this.playlist[this.pendingIndex]?.url || '',
              title: this.playlist[this.pendingIndex]?.title || ''
            });
            this.pendingIndex = -1;
          }
          break;
        case 'playing':
          this.reportStateIfChanged('playing');
          this.startProgressUpdates();
          break;
        case 'paused':
          this.reportStateIfChanged('paused');
          this.stopProgressUpdates();
          break;
        case 'completed':
          this.stopProgressUpdates();
          this.handlePlayCompleted();
          break;
        case 'stopped':
          this.reportStateIfChanged('stopped');
          this.stopProgressUpdates();
          break;
        case 'error':
          this.reportStateIfChanged('error');
          this.stopProgressUpdates();
          // error 后必须 reset 才能重新使用
          try {
            this.avPlayer?.reset();
          } catch (e) {
            console.error('KRAudioPlayerModule: reset after error failed', e);
          }
          break;
      }
    });

    this.avPlayer.on('error', (err: BusinessError) => {
      console.error(`KRAudioPlayerModule: AVPlayer error, code=${err.code}, message=${err.message}`);
      this.reportStateIfChanged('error');
      if (this.errorCallback) {
        this.errorCallback({
          code: (err.code ?? -1).toString(),
          message: err.message || 'Unknown error'
        });
      }
    });
  }

  private handlePlayCompleted(): void {
    if (this.playMode === 'single') {
      // 单曲循环由 avPlayer.loop 处理
      return;
    } else if (this.playMode === 'loop') {
      this.handleNext();
    } else {
      // 顺序播放
      if (this.currentIndex + 1 < this.playlist.length) {
        this.handleNext();
      } else {
        this.reportStateIfChanged('completed');
      }
    }
  }

  // ======================== 进度更新 ========================

  private startProgressUpdates(): void {
    this.stopProgressUpdates();
    this.progressTimer = setInterval(() => {
      if (!this.avPlayer || !this.timeUpdateCallback) return;
      const current = this.avPlayer.currentTime || 0;
      const duration = this.avPlayer.duration || 0;
      this.timeUpdateCallback({
        current: Math.floor(current).toString(),
        duration: Math.floor(duration).toString()
      });
    }, 500);
  }

  private stopProgressUpdates(): void {
    if (this.progressTimer !== null) {
      clearInterval(this.progressTimer);
      this.progressTimer = null;
    }
  }

  // ======================== 状态管理 ========================

  private reportStateIfChanged(state: string): void {
    if (state !== this.lastReportedState) {
      this.lastReportedState = state;
      if (this.playStateCallback) {
        this.playStateCallback({ state: state });
      }
    }

    // 更新 AVSession 播放状态
    if (this.session && this.backgroundPlayEnabled) {
      const sessionState = state === 'playing'
        ? avSession.PlaybackState.PLAYBACK_STATE_PLAY
        : state === 'buffering'
          ? avSession.PlaybackState.PLAYBACK_STATE_PLAY
          : avSession.PlaybackState.PLAYBACK_STATE_PAUSE;
      this.session.setAVPlaybackState({ state: sessionState });
    }
  }

  // ======================== 工具方法 ========================

  private parseParams(params: string): Record<string, string> | null {
    if (!params) return null;
    try {
      return JSON.parse(params) as Record<string, string>;
    } catch {
      return null;
    }
  }

  // ======================== 生命周期 ========================

  onDestroy(): void {
    this.stopProgressUpdates();
    this.teardownAVSession();
    this.stopBackgroundTask();

    if (this.avPlayer) {
      this.avPlayer.release();
      this.avPlayer = null;
    }

    this.timeUpdateCallback = null;
    this.playStateCallback = null;
    this.errorCallback = null;
    this.playlistIndexCallback = null;
    this.playlist = [];
  }
}

interface AudioItemInfo {
  url: string;
  title: string;
  artist: string;
  coverUrl: string;
}
