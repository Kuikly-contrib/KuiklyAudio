import { media } from '@kit.MediaKit';
import { audio } from '@kit.AudioKit';
import { avSession } from '@kit.AVSessionKit';
import { backgroundTaskManager } from '@kit.BackgroundTasksKit';
import { wantAgent, WantAgent } from '@kit.AbilityKit';
import { KuiklyRenderBaseModule, KuiklyRenderCallback } from '@aspect/core';

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
 */
export class KRAudioPlayerModule extends KuiklyRenderBaseModule {
  private avPlayer: media.AVPlayer | null = null;
  private progressTimer: number | null = null;

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
  private currentState: string = 'idle';

  // AVSession
  private session: avSession.AVSession | null = null;

  call(method: string, params: string, callback: KuiklyRenderCallback): string | null {
    switch (method) {
      case 'play':
        this.handlePlay(params);
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
        this.handleSeekTo(params);
        break;
      case 'setVolume':
        this.handleSetVolume(params);
        break;
      case 'setSpeed':
        this.handleSetSpeed(params);
        break;
      case 'setPlaylist':
        this.handleSetPlaylist(params);
        break;
      case 'next':
        this.handleNext();
        break;
      case 'previous':
        this.handlePrevious();
        break;
      case 'setPlayMode':
        this.handleSetPlayMode(params);
        break;
      case 'enableBackgroundPlay':
        this.handleEnableBackgroundPlay(params);
        break;
      case 'getPlayState':
        return this.currentState;
      case 'getCurrentPosition':
        return '0'; // 通过 avPlayer.currentTime 获取
      case 'getDuration':
        return '0'; // 通过 avPlayer.duration 获取
      case 'onTimeUpdate':
        this.timeUpdateCallback = callback;
        break;
      case 'onPlayStateChanged':
        this.playStateCallback = callback;
        break;
      case 'onError':
        this.errorCallback = callback;
        break;
      case 'onPlaylistIndexChanged':
        this.playlistIndexCallback = callback;
        break;
    }
    return null;
  }

  // ======================== 播放控制 ========================

  private async handlePlay(params: string): Promise<void> {
    const json = this.parseParams(params);
    if (!json || !json.url) return;

    await this.ensurePlayer();
    if (!this.avPlayer) return;

    this.avPlayer.url = json.url;

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
    this.avPlayer?.pause();
    this.updateState('paused');
  }

  private handleResume(): void {
    this.avPlayer?.play();
    this.updateState('playing');
  }

  private handleStop(): void {
    this.stopProgressUpdates();
    this.avPlayer?.stop();
    this.updateState('stopped');
  }

  private handleSeekTo(params: string): void {
    const json = this.parseParams(params);
    if (!json) return;
    const position = parseInt(json.position || '0');
    this.avPlayer?.seek(position);
  }

  // ======================== 音量 & 倍速 ========================

  private handleSetVolume(params: string): void {
    const json = this.parseParams(params);
    if (!json) return;
    const volume = Math.max(0, Math.min(1, parseFloat(json.volume || '1.0')));
    if (this.avPlayer) {
      // 鸿蒙 AVPlayer 的音量通过 AudioRenderer 设置
      // 这里通过 setVolume 方法设置
      this.avPlayer.setVolume(volume);
    }
  }

  private handleSetSpeed(params: string): void {
    const json = this.parseParams(params);
    if (!json) return;
    const speed = Math.max(0.5, Math.min(2.0, parseFloat(json.speed || '1.0')));
    if (this.avPlayer) {
      // 将 speed 映射到 PlaybackSpeed 枚举
      this.avPlayer.setSpeed(this.mapSpeed(speed));
    }
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
    this.playAtIndex(nextIndex).catch((e: Error) => console.error('KRAudioPlayerModule: next failed', e));
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
    this.playAtIndex(prevIndex).catch((e: Error) => console.error('KRAudioPlayerModule: previous failed', e));
  }

  private async playAtIndex(index: number): Promise<void> {
    if (index < 0 || index >= this.playlist.length) return;

    this.currentIndex = index;
    const item = this.playlist[index];

    await this.ensurePlayer();
    if (this.avPlayer) {
      this.avPlayer.url = item.url;
    }

    if (this.playlistIndexCallback) {
      this.playlistIndexCallback({
        index: index.toString(),
        url: item.url,
        title: item.title
      });
    }
  }

  private handleSetPlayMode(params: string): void {
    const json = this.parseParams(params);
    if (!json) return;
    this.playMode = json.mode || 'sequence';

    if (this.avPlayer) {
      if (this.playMode === 'single') {
        this.avPlayer.loop = true;
      } else {
        this.avPlayer.loop = false;
      }
    }
  }

  // ======================== 后台播放 ========================

  private async handleEnableBackgroundPlay(params: string): Promise<void> {
    const json = this.parseParams(params);
    if (!json) return;
    this.backgroundPlayEnabled = json.enable === '1';

    if (this.backgroundPlayEnabled) {
      await this.startBackgroundTask();
      await this.setupAVSession();
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
        this.avPlayer?.seek(time);
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

    this.avPlayer.on('stateChange', (state: string) => {
      switch (state) {
        case 'idle':
          this.updateState('idle');
          break;
        case 'prepared':
          this.avPlayer?.play();
          break;
        case 'playing':
          this.updateState('playing');
          this.startProgressUpdates();
          break;
        case 'paused':
          this.updateState('paused');
          this.stopProgressUpdates();
          break;
        case 'completed':
          this.stopProgressUpdates();
          this.handlePlayCompleted();
          break;
        case 'stopped':
          this.updateState('stopped');
          this.stopProgressUpdates();
          break;
        case 'error':
          this.updateState('error');
          this.stopProgressUpdates();
          break;
      }
    });

    this.avPlayer.on('error', (err: Error) => {
      this.updateState('error');
      if (this.errorCallback) {
        this.errorCallback({
          code: '-1',
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
        this.updateState('completed');
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

  private updateState(state: string): void {
    this.currentState = state;
    if (this.playStateCallback) {
      this.playStateCallback({ state: state });
    }

    // 更新 AVSession 播放状态
    if (this.session && this.backgroundPlayEnabled) {
      const sessionState = state === 'playing'
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
