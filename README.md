# KuiklyAudio

适配 Kuikly 框架的跨平台音频播放组件，支持 Android / iOS / 鸿蒙三端。

## 功能特性

- **基础播放控制**：play / pause / resume / stop / seekTo / seekToProgress
- **缓冲状态回调**：播放器缓冲时上报 `buffering` 状态，可用于 UI Loading 展示
- **播放进度回调**：500ms 间隔的实时进度更新
- **播放列表**：多曲目切换，支持 next / previous
- **播放模式**：顺序播放 / 单曲循环 / 列表循环
- **后台播放**：Android Foreground Service / iOS Audio Background Mode / 鸿蒙长时任务
- **锁屏控制**：显示曲目信息（标题、艺术家、封面），响应锁屏播放按钮
- **音量控制**：0.0 ~ 1.0，支持同步查询当前音量
- **倍速控制**：0.5x ~ 2.0x，支持同步查询当前倍速

## 接入指南

**统一仓库配置（所有端通用）：**
```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
    }
}
```

### 1. KMP 层（DSL 侧）

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.tencent.kuiklybase:KuiklyAudio:1.0.0-2.0.21")
}
```

### 2. Android 端

**Maven 依赖（在 app 的 build.gradle.kts）：**
```kotlin
dependencies {
    // KuiklyAudio Android 原生实现
    implementation("com.tencent.kuiklybase:KuiklyAudioAndroid:1.0.0-2.0.21")
}
```


**注册 Module（在 KuiklyRenderView 中）：**
```kotlin
override fun registerExternalModule(kuiklyRenderExport: IKuiklyRenderExport) {
    super.registerExternalModule(kuiklyRenderExport)
    with(kuiklyRenderExport) {
        moduleExport("KRAudioPlayerModule") { KRAudioPlayerModule() }
    }
}
```

### 3. iOS 端

**方式一：CocoaPods（推荐）**

在 Podfile 中添加：
```ruby
pod 'KuiklyAudio', '~> 1.0.0'
```

**方式二：手动复制**

将 `KuiklyAudioIOS/Classes/` 下的 `.h` 和 `.m` 文件添加到 Xcode 项目。

**⚠️ iOS 后台播放配置：**
- 在 Xcode 中打开 Target → Signing & Capabilities → + Background Modes → 勾选 Audio
- 或在 Info.plist 中添加 `UIBackgroundModes: audio`

**iOS 无需手动注册 Module**，框架通过类名 `KRAudioPlayerModule` 自动发现。

### 4. 鸿蒙端

**复制原生 Module 文件：**
- 将 `KuiklyAudioOhos/KRAudioPlayerModule.ets` 添加到鸿蒙项目中

**注册 Module（在 Adapter 中）：**
```typescript
registerExternalModule("KRAudioPlayerModule", () => new KRAudioPlayerModule())
```

## API 文档

### 在 Pager 中注册和使用

Module 通过类型化回调自动解析原生 JSON 数据。在 Pager 的 `created()` 中注册回调，将解析后的值赋给 `observable` 属性即可驱动 UI 刷新：

```kotlin
@Page("audio_demo")
class AudioDemoPage : Pager() {

    // 用 observable 定义状态，UI 自动刷新
    private var playState by observable("idle")
    private var position by observable(0L)
    private var duration by observable(0L)

    override fun createExternalModules(): Map<String, Module>? {
        return mapOf(AudioPlayerModule.MODULE_NAME to AudioPlayerModule())
    }

    override fun created() {
        super.created()
        val audio = acquireModule<AudioPlayerModule>(AudioPlayerModule.MODULE_NAME)

        // 类型化回调，无需手动 optString
        // 回调中赋值给 observable 属性，触发 UI 刷新
        audio.onPlayStateChanged { state -> playState = state }
        audio.onTimeUpdate { pos, dur -> position = pos; duration = dur }
        audio.onError { code, message -> /* ... */ }
        audio.onPlaylistIndexChanged { index, url, title -> /* ... */ }

        // 播放
        audio.play("https://example.com/song.mp3", title = "歌曲名", artist = "艺术家")
    }
}
```

### 数据类

#### AudioPlayItem

```kotlin
class AudioPlayItem(
    val url: String,           // 音频 URL（支持网络 URL 和本地路径）
    val title: String = "",    // 标题（锁屏信息展示）
    val artist: String = "",   // 艺术家（锁屏信息展示）
    val coverUrl: String = "", // 封面图 URL（锁屏信息展示）
)
```

| 方法 | 说明 |
|------|------|
| `toJson(): String` | 序列化为 JSON 字符串 |
| `AudioPlayItem.listToJson(items): String` | 将列表序列化为 JSON 数组字符串（伴生对象方法） |

#### AudioPlayMode

```kotlin
enum class AudioPlayMode(val value: String) {
    SEQUENCE("sequence"),    // 顺序播放
    SINGLE_LOOP("single"),   // 单曲循环
    LIST_LOOP("loop"),       // 列表循环
}
```

| 方法 | 说明 |
|------|------|
| `AudioPlayMode.fromValue(value): AudioPlayMode` | 从字符串值反序列化，未匹配时默认返回 `SEQUENCE`（伴生对象方法） |

### 播放控制

| 方法 | 说明 |
|------|------|
| `play(url, title?, artist?, coverUrl?)` | 播放指定音频 |
| `play(item: AudioPlayItem)` | 播放指定的 AudioPlayItem |
| `pause()` | 暂停 |
| `resume()` | 恢复播放 |
| `stop()` | 停止并释放 |
| `seekTo(positionMs: Long)` | 跳转到指定位置（毫秒） |
| `seekToProgress(progress: Float)` | 跳转到指定进度（0.0 ~ 1.0，需 totalDuration > 0） |

### 音量 & 倍速

| 方法 | 说明 |
|------|------|
| `setVolume(volume: Float)` | 设置音量，0.0 ~ 1.0 |
| `setSpeed(speed: Float)` | 设置倍速，0.5 ~ 2.0 |
| `getVolume(): Float` | 同步查询当前音量（0.0 ~ 1.0） |
| `getSpeed(): Float` | 同步查询当前播放速度（0.5 ~ 2.0） |

### 播放列表

| 方法 | 说明 |
|------|------|
| `setPlaylist(items: List<AudioPlayItem>, startIndex: Int = 0)` | 设置播放列表及起始索引 |
| `next()` | 下一曲 |
| `previous()` | 上一曲 |
| `setPlayMode(mode: AudioPlayMode)` | 设置播放模式：`SEQUENCE` / `SINGLE_LOOP` / `LIST_LOOP` |

### 后台播放

| 方法 | 说明 |
|------|------|
| `enableBackgroundPlay(enable: Boolean)` | 启用/禁用后台播放 |

### 内部缓存状态属性

Module 内部缓存了回调解析后的状态值（`private set`），可在非 UI 场景中直接读取。但在 Pager `body()` 中使用时，**应从 Pager 的 `observable` 属性读取**以驱动 UI 刷新。

| 属性 | 类型 | 说明 |
|------|------|------|
| `state` | `String` | 当前播放状态：`idle` / `buffering` / `playing` / `paused` / `stopped` / `completed` / `error` |
| `position` | `Long` | 当前播放位置（毫秒） |
| `totalDuration` | `Long` | 音频总时长（毫秒） |
| `playlistIndex` | `Int` | 播放列表当前索引 |
| `playlistUrl` | `String` | 播放列表当前曲目 URL |
| `playlistTitle` | `String` | 播放列表当前曲目标题 |
| `lastErrorMessage` | `String` | 最近一次错误信息 |
| `lastErrorCode` | `String` | 最近一次错误码 |

### 便捷计算属性

| 属性 | 类型 | 说明 |
|------|------|------|
| `isPlaying` | `Boolean` | 是否正在播放（`state == "playing"`） |
| `isPaused` | `Boolean` | 是否暂停（`state == "paused"`） |
| `isBuffering` | `Boolean` | 是否正在缓冲（`state == "buffering"`） |
| `progress` | `Float` | 播放进度 0.0 ~ 1.0，未播放时为 0 |

### 状态查询（同步，主动调用）

| 方法 | 返回值 | 说明 |
|------|--------|------|
| `getPlayState(): String` | `idle` / `buffering` / `playing` / `paused` / `stopped` / `completed` / `error` | 同步查询当前播放状态 |
| `getCurrentPosition(): Long` | 当前位置（毫秒），未播放时返回 0 | |
| `getDuration(): Long` | 总时长（毫秒），未播放时返回 0 | |
| `getVolume(): Float` | 当前音量 0.0 ~ 1.0 | |
| `getSpeed(): Float` | 当前倍速 0.5 ~ 2.0 | |

### 事件回调（类型化，推荐在 created() 中注册一次）

回调方法内部自动解析 JSON，参数为类型化值，同时自动维护上方状态属性：

| 方法 | 回调签名 | 自动维护的属性 | 说明 |
|------|---------|---------------|------|
| `onPlayStateChanged(listener)` | `(state: String) -> Unit` | `state` | 播放状态变化，包括 `buffering` 状态 |
| `onTimeUpdate(listener)` | `(position: Long, duration: Long) -> Unit` | `position`, `totalDuration` | 播放进度更新（约 500ms 一次） |
| `onError(listener)` | `(code: String, message: String) -> Unit` | `lastErrorCode`, `lastErrorMessage` | 播放错误 |
| `onPlaylistIndexChanged(listener)` | `(index: Int, url: String, title: String) -> Unit` | `playlistIndex`, `playlistUrl`, `playlistTitle` | 播放列表当前曲目变化 |

### 播放状态流转

```
                    play() / setPlaylist()
                           ↓
  idle ─────────────→ buffering ──→ playing ⇄ paused
                           │              │
                           │              ↓
                           │         completed
                           │              │
                           ↓              ↓
                        error ←─────── stopped
```

- **idle**：初始状态 / 停止后 / 错误恢复后
- **buffering**：正在缓冲数据，UI 可展示 Loading 指示器
- **playing**：正在播放
- **paused**：用户主动暂停
- **completed**：播放完成（列表最后一曲结束或单曲播放结束）
- **stopped**：用户调用 `stop()` 后
- **error**：播放出错

### 播放列表示例

```kotlin
val audio = acquireModule<AudioPlayerModule>(AudioPlayerModule.MODULE_NAME)

// 设置播放列表
val songs = listOf(
    AudioPlayItem("https://example.com/song1.mp3", "歌曲1", "艺术家A", "https://example.com/cover1.jpg"),
    AudioPlayItem("https://example.com/song2.mp3", "歌曲2", "艺术家B"),
    AudioPlayItem("https://example.com/song3.mp3", "歌曲3", "艺术家C"),
)
audio.setPlaylist(songs, startIndex = 0)

// 设置列表循环
audio.setPlayMode(AudioPlayMode.LIST_LOOP)

// 启用后台播放 + 锁屏控制
audio.enableBackgroundPlay(true)

// 监听曲目切换
audio.onPlaylistIndexChanged { index, url, title ->
    // index: Int, url: String, title: String — 自动解析，无需手动 optString
}

// 监听缓冲状态（展示 Loading）
audio.onPlayStateChanged { state ->
    when (state) {
        "buffering" -> showLoading()
        "playing" -> hideLoading()
        // ...
    }
}

// 控制
audio.next()              // 下一曲
audio.previous()          // 上一曲
audio.setVolume(0.8f)     // 音量 80%
audio.setSpeed(1.5f)      // 1.5 倍速
audio.seekToProgress(0.5f) // 跳到 50% 位置

// 同步查询
val currentVolume = audio.getVolume()  // 0.8f
val currentSpeed = audio.getSpeed()    // 1.5f
```

## Maven 发布

```bash
# 发布到 Maven 仓库
./publish-maven.sh
```

发布参数已在 `gradle.properties` 中配置：
- **仓库**: `REDACTED_MAVEN_URL`
- **GroupId**: `com.tencent.kuiklybase`
- **ArtifactId**: `KuiklyAudio`

使用方通过以下仓库引用：
```kotlin
maven("https://mirrors.tencent.com/nexus/repository/maven-tencent/")
```

## 注意事项

1. **Module 名称一致性**：DSL 层 `moduleName()` = Android 注册名 = iOS 类名 = `"KRAudioPlayerModule"`
2. **不要单例持有 Module**：始终通过 `acquireModule` 从当前 Pager 获取
3. **长期回调**：`onTimeUpdate` / `onPlayStateChanged` 等使用 `keepCallbackAlive=true`，会持续触发，Module 销毁时自动清空
4. **compileOnly 依赖**：组件不打包 kuikly core，使用方需自行依赖
5. **iOS 后台播放**：必须在 Info.plist 声明 `UIBackgroundModes: audio`
6. **Android 后台播放**：需要声明权限，AndroidManifest 中需包含：
   ```xml
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
   ```
   Android 14+ 必须声明 `FOREGROUND_SERVICE_MEDIA_PLAYBACK`，并在 Service 中指定 `foregroundServiceType="mediaPlayback"`
7. **鸿蒙后台播放**：需要在 module.json5 中声明权限和后台模式：
   ```json5
   "requestPermissions": [{ "name": "ohos.permission.KEEP_BACKGROUND_RUNNING" }],
   "backgroundModes": ["audioPlayback"]
   ```
8. **iOS 内外网兼容**：组件 `.h` 文件已做 `#if __has_include` 兼容，同时支持 `OpenKuiklyIOSRender` 和 `KuiklyIOSRender`；但 podspec 中 `dependency 'OpenKuiklyIOSRender'` 需使用方根据实际环境调整
9. **类型化回调**：`onPlayStateChanged` / `onTimeUpdate` / `onError` / `onPlaylistIndexChanged` 已自动解析 JSON 为类型化参数，无需手动 `optString` / `toLongOrNull()`
10. **buffering 状态**：三端均已上报 `buffering` 状态，在 UI 中可用于展示 Loading 指示器；`isBuffering` 便捷属性可直接判断
