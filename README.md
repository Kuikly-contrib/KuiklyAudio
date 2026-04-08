# KuiklyAudio

适配 Kuikly 框架的跨平台音频播放组件。基于 Kuikly 内置 Module 机制实现，纯逻辑组件（无 UI），支持 Android / iOS / 鸿蒙三端。

## 功能特性

- **基础播放控制**：play / pause / resume / stop / seekTo
- **播放进度回调**：500ms 间隔的实时进度更新
- **播放列表**：多曲目切换，支持 next / previous
- **播放模式**：顺序播放 / 单曲循环 / 列表循环
- **后台播放**：Android Foreground Service / iOS Audio Background Mode / 鸿蒙长时任务
- **锁屏控制**：显示曲目信息（标题、艺术家、封面），响应锁屏播放按钮
- **音量控制**：0.0 ~ 1.0
- **倍速控制**：0.5x ~ 2.0x


## 接入指南

### 1. KMP 层（DSL 侧）

**Maven 依赖：**
```kotlin
// build.gradle.kts
dependencies {
    implementation("com.tencent.kuiklybase:KuiklyAudio:1.0.0")
}
```


### 2. Android 端

**添加依赖（在 app 的 build.gradle.kts）：**
```kotlin
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
}
```

**复制原生 Module 文件到项目中：**
- `KuiklyAudioAndroid/src/main/kotlin/` 下的所有文件
- `KuiklyAudioAndroid/src/main/AndroidManifest.xml` 中的 Service 声明

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

**CocoaPods 引入或直接复制文件：**
- 将 `KuiklyAudioIOS/Classes/` 下的 `.h` 和 `.m` 文件添加到 Xcode 项目

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

```kotlin
@Page("audio_demo")
class AudioDemoPage : Pager() {

    override fun createExternalModules(): Map<String, Module>? {
        return mapOf(AudioPlayerModule.MODULE_NAME to AudioPlayerModule())
    }

    override fun created() {
        super.created()
        val audio = acquireModule<AudioPlayerModule>(AudioPlayerModule.MODULE_NAME)

        // 监听播放状态
        audio.onPlayStateChanged { result ->
            val state = result?.optString("state") // idle/playing/paused/stopped/completed/error
        }

        // 监听进度
        audio.onTimeUpdate { result ->
            val current = result?.optString("current")?.toLongOrNull() ?: 0  // 毫秒
            val duration = result?.optString("duration")?.toLongOrNull() ?: 0 // 毫秒
        }

        // 播放
        audio.play("https://example.com/song.mp3", title = "歌曲名", artist = "艺术家")
    }
}
```

### 播放控制

| 方法 | 说明 |
|------|------|
| `play(url, title?, artist?, coverUrl?)` | 播放指定音频 |
| `play(item: AudioPlayItem)` | 播放指定的 AudioPlayItem |
| `pause()` | 暂停 |
| `resume()` | 恢复播放 |
| `stop()` | 停止并释放 |
| `seekTo(positionMs)` | 跳转到指定位置（毫秒） |

### 音量 & 倍速

| 方法 | 说明 |
|------|------|
| `setVolume(volume: Float)` | 设置音量，0.0 ~ 1.0 |
| `setSpeed(speed: Float)` | 设置倍速，0.5 ~ 2.0 |

### 播放列表

| 方法 | 说明 |
|------|------|
| `setPlaylist(items, startIndex?)` | 设置播放列表 |
| `next()` | 下一曲 |
| `previous()` | 上一曲 |
| `setPlayMode(mode: AudioPlayMode)` | SEQUENCE / SINGLE_LOOP / LIST_LOOP |

### 后台播放

| 方法 | 说明 |
|------|------|
| `enableBackgroundPlay(enable: Boolean)` | 启用/禁用后台播放 |

### 状态查询（同步）

| 方法 | 返回值 |
|------|--------|
| `getPlayState()` | idle / playing / paused / stopped / completed / error |
| `getCurrentPosition()` | 当前位置（毫秒） |
| `getDuration()` | 总时长（毫秒） |

### 事件回调（异步，长期监听）

| 方法 | 回调数据 |
|------|---------|
| `onTimeUpdate(callback)` | `{"current": "12500", "duration": "180000"}` |
| `onPlayStateChanged(callback)` | `{"state": "playing"}` |
| `onError(callback)` | `{"code": "...", "message": "..."}` |
| `onPlaylistIndexChanged(callback)` | `{"index": "2", "url": "...", "title": "..."}` |

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
audio.onPlaylistIndexChanged { result ->
    val index = result?.optString("index")?.toIntOrNull() ?: 0
    val title = result?.optString("title") ?: ""
}

// 控制
audio.next()        // 下一曲
audio.previous()    // 上一曲
audio.setVolume(0.8f)   // 音量 80%
audio.setSpeed(1.5f)    // 1.5 倍速
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
3. **长期回调**：`onTimeUpdate` / `onPlayStateChanged` 等使用 `keepCallbackAlive=true`，会持续触发
4. **compileOnly 依赖**：组件不打包 kuikly core，使用方需自行依赖
5. **iOS 后台播放**：必须在 Info.plist 声明 `UIBackgroundModes: audio`
6. **Android 后台播放**：需要 `FOREGROUND_SERVICE` 权限（已在 AndroidManifest 中声明）
