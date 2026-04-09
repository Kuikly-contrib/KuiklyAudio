# @yuki8273/kuikly-audio

适配 Kuikly 框架的鸿蒙端音频播放组件，基于 HarmonyOS AVPlayer 实现。

项目地址：[https://github.com/Kuikly-contrib/KuiklyAudio](https://github.com/Kuikly-contrib/KuiklyAudio)

## 功能特性

- **基础播放控制**：play / pause / resume / stop / seekTo / seekToProgress
- **缓冲状态回调**：播放器缓冲时上报 `buffering` 状态
- **播放进度回调**：500ms 间隔的实时进度更新
- **播放列表**：多曲目切换，支持 next / previous
- **播放模式**：顺序播放 / 单曲循环 / 列表循环
- **后台播放**：鸿蒙长时任务（backgroundTaskManager）
- **锁屏控制**：AVSession 锁屏媒体控制
- **音量控制**：0.0 ~ 1.0
- **倍速控制**：0.5x ~ 2.0x

## 安装

```bash
ohpm install @yuki8273/kuikly-audio
```

## 引入方式

```typescript
import { KRAudioPlayerModule } from '@yuki8273/kuikly-audio'
```

## 注册 Module

在 Adapter 中注册：

```typescript
registerExternalModule("KRAudioPlayerModule", () => new KRAudioPlayerModule())
```

## 权限配置

后台播放需要在 module.json5 中声明：

```json5
"requestPermissions": [
  { "name": "ohos.permission.KEEP_BACKGROUND_RUNNING" }
],
"backgroundModes": ["audioPlayback"]
```

## API

| 方法 | 说明 |
|------|------|
| `play(url, title?, artist?, coverUrl?)` | 播放指定音频 |
| `pause()` | 暂停 |
| `resume()` | 恢复播放 |
| `stop()` | 停止并释放 |
| `seekTo(positionMs)` | 跳转到指定位置（毫秒） |
| `setVolume(volume)` | 设置音量 0.0 ~ 1.0 |
| `setSpeed(speed)` | 设置倍速 0.5 ~ 2.0 |
| `setPlaylist(items, startIndex?)` | 设置播放列表 |
| `next()` | 下一曲 |
| `previous()` | 上一曲 |
| `setPlayMode(mode)` | 设置播放模式：sequence / single / loop |
| `enableBackgroundPlay(enable)` | 启用/禁用后台播放 |

## 播放状态

`onPlayStateChanged` 回调可能返回的状态：

| 状态 | 说明 |
|------|------|
| `idle` | 初始状态 |
| `buffering` | 正在缓冲 |
| `playing` | 正在播放 |
| `paused` | 已暂停 |
| `completed` | 播放完成 |
| `stopped` | 已停止 |
| `error` | 播放出错 |
