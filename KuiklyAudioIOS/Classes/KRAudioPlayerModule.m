#import "KRAudioPlayerModule.h"
#import <AVFoundation/AVFoundation.h>
#import <MediaPlayer/MediaPlayer.h>

static NSString * const kStateIdle = @"idle";
static NSString * const kStatePlaying = @"playing";
static NSString * const kStatePaused = @"paused";
static NSString * const kStateStopped = @"stopped";
static NSString * const kStateCompleted = @"completed";
static NSString * const kStateError = @"error";

@interface KRAudioPlayerModule ()

@property (nonatomic, strong) AVPlayer *player;
@property (nonatomic, strong) AVPlayerItem *currentItem;
@property (nonatomic, strong) id timeObserver;

// 播放列表
@property (nonatomic, strong) NSMutableArray<NSDictionary *> *playlist;
@property (nonatomic, assign) NSInteger currentIndex;
@property (nonatomic, copy) NSString *playMode; // sequence / single / loop

// 状态
@property (nonatomic, copy) NSString *currentState;
@property (nonatomic, assign) BOOL backgroundPlayEnabled;

// 长期回调
@property (nonatomic, copy) KuiklyRenderCallback timeUpdateCallback;
@property (nonatomic, copy) KuiklyRenderCallback playStateCallback;
@property (nonatomic, copy) KuiklyRenderCallback errorCallback;
@property (nonatomic, copy) KuiklyRenderCallback playlistIndexCallback;

@end

@implementation KRAudioPlayerModule

- (instancetype)init {
    if (self = [super init]) {
        _playlist = [NSMutableArray new];
        _currentIndex = 0;
        _playMode = @"sequence";
        _currentState = kStateIdle;
        _backgroundPlayEnabled = NO;
    }
    return self;
}

#pragma mark - Module Dispatch

- (id)hrv_callWithMethod:(NSString *)method params:(NSString *)params callback:(KuiklyRenderCallback)callback {
    if ([method isEqualToString:@"play"]) {
        [self handlePlay:params];
    } else if ([method isEqualToString:@"pause"]) {
        [self handlePause];
    } else if ([method isEqualToString:@"resume"]) {
        [self handleResume];
    } else if ([method isEqualToString:@"stop"]) {
        [self handleStop];
    } else if ([method isEqualToString:@"seekTo"]) {
        [self handleSeekTo:params];
    } else if ([method isEqualToString:@"setVolume"]) {
        [self handleSetVolume:params];
    } else if ([method isEqualToString:@"setSpeed"]) {
        [self handleSetSpeed:params];
    } else if ([method isEqualToString:@"setPlaylist"]) {
        [self handleSetPlaylist:params];
    } else if ([method isEqualToString:@"next"]) {
        [self handleNext];
    } else if ([method isEqualToString:@"previous"]) {
        [self handlePrevious];
    } else if ([method isEqualToString:@"setPlayMode"]) {
        [self handleSetPlayMode:params];
    } else if ([method isEqualToString:@"enableBackgroundPlay"]) {
        [self handleEnableBackgroundPlay:params];
    } else if ([method isEqualToString:@"getPlayState"]) {
        return self.currentState;
    } else if ([method isEqualToString:@"getCurrentPosition"]) {
        return [self getCurrentPositionString];
    } else if ([method isEqualToString:@"getDuration"]) {
        return [self getDurationString];
    } else if ([method isEqualToString:@"onTimeUpdate"]) {
        self.timeUpdateCallback = callback;
    } else if ([method isEqualToString:@"onPlayStateChanged"]) {
        self.playStateCallback = callback;
    } else if ([method isEqualToString:@"onError"]) {
        self.errorCallback = callback;
    } else if ([method isEqualToString:@"onPlaylistIndexChanged"]) {
        self.playlistIndexCallback = callback;
    }
    return nil;
}

#pragma mark - 播放控制

- (void)handlePlay:(NSString *)params {
    NSDictionary *json = [self parseParams:params];
    if (!json) return;
    
    NSString *url = json[@"url"] ?: @"";
    if (url.length == 0) return;
    
    NSString *title = json[@"title"] ?: @"";
    NSString *artist = json[@"artist"] ?: @"";
    NSString *coverUrl = json[@"coverUrl"] ?: @"";
    
    [self playURL:url title:title artist:artist coverUrl:coverUrl];
}

- (void)playURL:(NSString *)url title:(NSString *)title artist:(NSString *)artist coverUrl:(NSString *)coverUrl {
    [self removePlayerObservers];
    
    NSURL *audioURL = [NSURL URLWithString:url];
    if (!audioURL) {
        audioURL = [NSURL fileURLWithPath:url];
    }
    
    AVPlayerItem *item = [AVPlayerItem playerItemWithURL:audioURL];
    self.currentItem = item;
    
    if (!self.player) {
        self.player = [AVPlayer playerWithPlayerItem:item];
    } else {
        [self.player replaceCurrentItemWithPlayerItem:item];
    }
    
    [self addPlayerObservers];
    [self.player play];
    [self updateState:kStatePlaying];
    [self updateNowPlayingInfo:title artist:artist coverUrl:coverUrl];
}

- (void)handlePause {
    [self.player pause];
    [self updateState:kStatePaused];
}

- (void)handleResume {
    float currentRate = self.player.rate;
    [self.player play];
    // AVPlayer play 会将 rate 重置为 1.0，需要恢复之前的倍速设置
    if (currentRate > 0 && currentRate != 1.0) {
        self.player.rate = currentRate;
    }
    [self updateState:kStatePlaying];
}

- (void)handleStop {
    [self removeTimeObserver];
    [self.player pause];
    [self.player replaceCurrentItemWithPlayerItem:nil];
    [self updateState:kStateStopped];
    [self clearNowPlayingInfo];
}

- (void)handleSeekTo:(NSString *)params {
    NSDictionary *json = [self parseParams:params];
    if (!json) return;
    
    NSString *posStr = json[@"position"] ?: @"0";
    long long positionMs = [posStr longLongValue];
    CMTime time = CMTimeMake(positionMs, 1000);
    [self.player seekToTime:time];
}

#pragma mark - 音量 & 倍速

- (void)handleSetVolume:(NSString *)params {
    NSDictionary *json = [self parseParams:params];
    if (!json) return;
    
    float volume = [json[@"volume"] ?: @"1.0" floatValue];
    self.player.volume = MAX(0, MIN(1, volume));
}

- (void)handleSetSpeed:(NSString *)params {
    NSDictionary *json = [self parseParams:params];
    if (!json) return;
    
    float speed = [json[@"speed"] ?: @"1.0" floatValue];
    self.player.rate = MAX(0.5, MIN(2.0, speed));
}

#pragma mark - 播放列表

- (void)handleSetPlaylist:(NSString *)params {
    NSDictionary *json = [self parseParams:params];
    if (!json) return;
    
    NSString *itemsStr = json[@"items"] ?: @"[]";
    NSInteger startIndex = [json[@"startIndex"] ?: @"0" integerValue];
    
    NSData *data = [itemsStr dataUsingEncoding:NSUTF8StringEncoding];
    NSArray *items = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    
    [self.playlist removeAllObjects];
    if ([items isKindOfClass:[NSArray class]]) {
        [self.playlist addObjectsFromArray:items];
    }
    
    self.currentIndex = startIndex;
    if (self.currentIndex < self.playlist.count) {
        NSDictionary *item = self.playlist[self.currentIndex];
        [self playURL:item[@"url"] ?: @""
                title:item[@"title"] ?: @""
               artist:item[@"artist"] ?: @""
             coverUrl:item[@"coverUrl"] ?: @""];
    }
}

- (void)handleNext {
    if (self.playlist.count == 0) return;
    
    NSInteger nextIndex = self.currentIndex + 1;
    if (nextIndex >= (NSInteger)self.playlist.count) {
        if ([self.playMode isEqualToString:@"loop"]) {
            nextIndex = 0;
        } else {
            return;
        }
    }
    
    [self playAtIndex:nextIndex];
}

- (void)handlePrevious {
    if (self.playlist.count == 0) return;
    
    NSInteger prevIndex = self.currentIndex - 1;
    if (prevIndex < 0) {
        if ([self.playMode isEqualToString:@"loop"]) {
            prevIndex = self.playlist.count - 1;
        } else {
            return;
        }
    }
    
    [self playAtIndex:prevIndex];
}

- (void)playAtIndex:(NSInteger)index {
    if (index < 0 || index >= (NSInteger)self.playlist.count) return;
    
    self.currentIndex = index;
    NSDictionary *item = self.playlist[index];
    [self playURL:item[@"url"] ?: @""
            title:item[@"title"] ?: @""
           artist:item[@"artist"] ?: @""
         coverUrl:item[@"coverUrl"] ?: @""];
    
    if (self.playlistIndexCallback) {
        self.playlistIndexCallback(@{
            @"index": [@(index) stringValue],
            @"url": item[@"url"] ?: @"",
            @"title": item[@"title"] ?: @""
        });
    }
}

- (void)handleSetPlayMode:(NSString *)params {
    NSDictionary *json = [self parseParams:params];
    if (!json) return;
    self.playMode = json[@"mode"] ?: @"sequence";
}

#pragma mark - 后台播放

- (void)handleEnableBackgroundPlay:(NSString *)params {
    NSDictionary *json = [self parseParams:params];
    if (!json) return;
    
    self.backgroundPlayEnabled = [json[@"enable"] ?: @"0" isEqualToString:@"1"];
    
    if (self.backgroundPlayEnabled) {
        NSError *error = nil;
        [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:&error];
        [[AVAudioSession sharedInstance] setActive:YES error:&error];
        [self setupRemoteCommandCenter];
    } else {
        [self teardownRemoteCommandCenter];
    }
}

#pragma mark - Player Observers

- (void)addPlayerObservers {
    // 播放完成通知
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(playerDidFinishPlaying:)
                                                 name:AVPlayerItemDidPlayToEndTimeNotification
                                               object:self.currentItem];
    
    // 播放失败通知
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(playerDidFailPlaying:)
                                                 name:AVPlayerItemFailedToPlayToEndTimeNotification
                                               object:self.currentItem];
    
    // 进度定时回调（500ms）
    __weak typeof(self) weakSelf = self;
    CMTime interval = CMTimeMakeWithSeconds(0.5, NSEC_PER_SEC);
    self.timeObserver = [self.player addPeriodicTimeObserverForInterval:interval
                                                                 queue:dispatch_get_main_queue()
                                                            usingBlock:^(CMTime time) {
        [weakSelf handleTimeUpdate:time];
    }];
}

- (void)removePlayerObservers {
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:AVPlayerItemDidPlayToEndTimeNotification
                                                  object:nil];
    [[NSNotificationCenter defaultCenter] removeObserver:self
                                                    name:AVPlayerItemFailedToPlayToEndTimeNotification
                                                  object:nil];
    [self removeTimeObserver];
}

- (void)removeTimeObserver {
    if (self.timeObserver) {
        [self.player removeTimeObserver:self.timeObserver];
        self.timeObserver = nil;
    }
}

- (void)handleTimeUpdate:(CMTime)time {
    if (!self.timeUpdateCallback) return;
    
    long long currentMs = (long long)(CMTimeGetSeconds(time) * 1000);
    long long durationMs = 0;
    if (self.currentItem) {
        CMTime dur = self.currentItem.duration;
        if (CMTIME_IS_VALID(dur) && !CMTIME_IS_INDEFINITE(dur)) {
            durationMs = (long long)(CMTimeGetSeconds(dur) * 1000);
        }
    }
    
    self.timeUpdateCallback(@{
        @"current": [@(currentMs) stringValue],
        @"duration": [@(durationMs) stringValue]
    });
}

- (void)playerDidFinishPlaying:(NSNotification *)notification {
    if ([self.playMode isEqualToString:@"single"]) {
        // 单曲循环：重新播放当前曲目
        [self.player seekToTime:kCMTimeZero];
        [self.player play];
    } else if ([self.playMode isEqualToString:@"loop"]) {
        // 列表循环：自动下一曲
        [self handleNext];
    } else {
        // 顺序播放：播放下一曲或结束
        if (self.currentIndex + 1 < (NSInteger)self.playlist.count) {
            [self handleNext];
        } else {
            [self updateState:kStateCompleted];
        }
    }
}

- (void)playerDidFailPlaying:(NSNotification *)notification {
    [self updateState:kStateError];
    if (self.errorCallback) {
        NSError *error = notification.userInfo[AVPlayerItemFailedToPlayToEndTimeErrorKey];
        self.errorCallback(@{
            @"code": [@(error.code) stringValue] ?: @"-1",
            @"message": error.localizedDescription ?: @"Unknown error"
        });
    }
}

#pragma mark - 状态管理

- (void)updateState:(NSString *)state {
    self.currentState = state;
    if (self.playStateCallback) {
        self.playStateCallback(@{@"state": state});
    }
}

- (NSString *)getCurrentPositionString {
    if (!self.player) return @"0";
    long long ms = (long long)(CMTimeGetSeconds(self.player.currentTime) * 1000);
    return [@(MAX(0, ms)) stringValue];
}

- (NSString *)getDurationString {
    if (!self.currentItem) return @"0";
    CMTime dur = self.currentItem.duration;
    if (!CMTIME_IS_VALID(dur) || CMTIME_IS_INDEFINITE(dur)) return @"0";
    long long ms = (long long)(CMTimeGetSeconds(dur) * 1000);
    return [@(MAX(0, ms)) stringValue];
}

#pragma mark - 锁屏控制

- (void)updateNowPlayingInfo:(NSString *)title artist:(NSString *)artist coverUrl:(NSString *)coverUrl {
    if (!self.backgroundPlayEnabled) return;
    
    NSMutableDictionary *info = [NSMutableDictionary dictionary];
    info[MPMediaItemPropertyTitle] = title.length > 0 ? title : @"未知曲目";
    info[MPMediaItemPropertyArtist] = artist.length > 0 ? artist : @"未知艺术家";
    
    CMTime dur = self.currentItem.duration;
    if (CMTIME_IS_VALID(dur) && !CMTIME_IS_INDEFINITE(dur)) {
        info[MPMediaItemPropertyPlaybackDuration] = @(CMTimeGetSeconds(dur));
    }
    info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = @(CMTimeGetSeconds(self.player.currentTime));
    info[MPNowPlayingInfoPropertyPlaybackRate] = @(self.player.rate);
    
    // 异步加载封面图
    if (coverUrl.length > 0) {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
            NSURL *url = [NSURL URLWithString:coverUrl];
            NSData *data = [NSData dataWithContentsOfURL:url];
            if (data) {
                UIImage *image = [UIImage imageWithData:data];
                if (image) {
                    MPMediaItemArtwork *artwork = [[MPMediaItemArtwork alloc]
                        initWithBoundsSize:image.size
                        requestHandler:^UIImage * _Nonnull(CGSize size) {
                            return image;
                        }];
                    NSMutableDictionary *updatedInfo = [[MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo mutableCopy] ?: [NSMutableDictionary new];
                    updatedInfo[MPMediaItemPropertyArtwork] = artwork;
                    dispatch_async(dispatch_get_main_queue(), ^{
                        [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = updatedInfo;
                    });
                }
            }
        });
    }
    
    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = info;
}

- (void)clearNowPlayingInfo {
    [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nil;
}

- (void)setupRemoteCommandCenter {
    MPRemoteCommandCenter *center = [MPRemoteCommandCenter sharedCommandCenter];
    
    __weak typeof(self) weakSelf = self;
    
    [center.playCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent * _Nonnull event) {
        [weakSelf handleResume];
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    
    [center.pauseCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent * _Nonnull event) {
        [weakSelf handlePause];
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    
    [center.nextTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent * _Nonnull event) {
        [weakSelf handleNext];
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    
    [center.previousTrackCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent * _Nonnull event) {
        [weakSelf handlePrevious];
        return MPRemoteCommandHandlerStatusSuccess;
    }];
    
    [center.changePlaybackPositionCommand addTargetWithHandler:^MPRemoteCommandHandlerStatus(MPRemoteCommandEvent * _Nonnull event) {
        MPChangePlaybackPositionCommandEvent *posEvent = (MPChangePlaybackPositionCommandEvent *)event;
        CMTime time = CMTimeMakeWithSeconds(posEvent.positionTime, NSEC_PER_SEC);
        [weakSelf.player seekToTime:time];
        return MPRemoteCommandHandlerStatusSuccess;
    }];
}

- (void)teardownRemoteCommandCenter {
    MPRemoteCommandCenter *center = [MPRemoteCommandCenter sharedCommandCenter];
    [center.playCommand removeTarget:nil];
    [center.pauseCommand removeTarget:nil];
    [center.nextTrackCommand removeTarget:nil];
    [center.previousTrackCommand removeTarget:nil];
    [center.changePlaybackPositionCommand removeTarget:nil];
}

#pragma mark - 工具方法

- (NSDictionary *)parseParams:(NSString *)params {
    if (!params || params.length == 0) return nil;
    NSData *data = [params dataUsingEncoding:NSUTF8StringEncoding];
    if (!data) return nil;
    return [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
}

#pragma mark - 生命周期

- (void)invalidate {
    [self removePlayerObservers];
    [self teardownRemoteCommandCenter];
    [self clearNowPlayingInfo];
    [self.player pause];
    self.player = nil;
    self.currentItem = nil;
    self.timeUpdateCallback = nil;
    self.playStateCallback = nil;
    self.errorCallback = nil;
    self.playlistIndexCallback = nil;
    [self.playlist removeAllObjects];
}

@end
