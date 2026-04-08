#import <Foundation/Foundation.h>

// 兼容 kuikly-open 和 kuikly 内部版
#if __has_include(<OpenKuiklyIOSRender/KRBaseModule.h>)
#import <OpenKuiklyIOSRender/KRBaseModule.h>
#elif __has_include(<KuiklyIOSRender/KRBaseModule.h>)
#import <KuiklyIOSRender/KRBaseModule.h>
#endif

NS_ASSUME_NONNULL_BEGIN

/**
 * iOS 端音频播放 Module
 *
 * 基于 AVPlayer 实现，支持：
 * - 基础播放控制（play/pause/resume/stop/seekTo）
 * - 播放进度回调
 * - 播放列表管理
 * - 后台播放（AVAudioSession）
 * - 音量/倍速控制
 * - 锁屏控制（MPNowPlayingInfoCenter + MPRemoteCommandCenter）
 *
 * ⚠️ 类名必须与 DSL 层 moduleName() 返回值一致
 */
@interface KRAudioPlayerModule : KRBaseModule

@end

NS_ASSUME_NONNULL_END
