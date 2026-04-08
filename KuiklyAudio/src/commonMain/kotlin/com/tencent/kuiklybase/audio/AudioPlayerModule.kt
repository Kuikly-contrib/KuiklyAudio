package com.tencent.kuiklybase.audio

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module

/**
 * KuiklyAudio 音频播放 Module
 *
 * 基于 Kuikly Module 机制实现的跨平台音频播放组件。
 * 支持基础播放控制、播放列表、后台播放、锁屏控制、音量倍速等功能。
 *
 * 回调自动解析为类型化参数，无需手动处理 JSON。
 * 在 Pager 的 created() 中注册回调后，回调中赋值给 observable 属性即可触发 UI 刷新。
 *
 * 使用方式：
 * ```kotlin
 * @Page("audio_demo")
 * class AudioDemoPage : Pager() {
 *
 *     // 用 observable 定义状态，UI 自动刷新
 *     private var playState by observable("idle")
 *     private var position by observable(0L)
 *     private var duration by observable(0L)
 *
 *     override fun createExternalModules() = mapOf(
 *         AudioPlayerModule.MODULE_NAME to AudioPlayerModule()
 *     )
 *
 *     override fun created() {
 *         super.created()
 *         val audio = acquireModule<AudioPlayerModule>(AudioPlayerModule.MODULE_NAME)
 *
 *         // 类型化回调，无需手动 optString
 *         audio.onPlayStateChanged { state -> playState = state }
 *         audio.onTimeUpdate { pos, dur -> position = pos; duration = dur }
 *         audio.onError { code, message -> /* ... */ }
 *         audio.onPlaylistIndexChanged { index, url, title -> /* ... */ }
 *
 *         // 播放
 *         audio.play("https://example.com/audio.mp3", title = "歌曲名")
 *     }
 * }
 * ```
 */
class AudioPlayerModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    // ======================== 内部缓存状态 ========================

    /** 当前播放状态：idle / buffering / playing / paused / stopped / completed / error */
    var state: String = "idle"
        private set

    /** 当前播放位置（毫秒） */
    var position: Long = 0L
        private set

    /** 音频总时长（毫秒） */
    var totalDuration: Long = 0L
        private set

    /** 播放列表当前索引 */
    var playlistIndex: Int = 0
        private set

    /** 最近一次错误信息 */
    var lastErrorMessage: String = ""
        private set

    /** 最近一次错误码 */
    var lastErrorCode: String = ""
        private set

    /** 播放列表当前曲目 URL */
    var playlistUrl: String = ""
        private set

    /** 播放列表当前曲目标题 */
    var playlistTitle: String = ""
        private set

    // ======================== 便捷计算属性 ========================

    /** 是否正在播放 */
    val isPlaying: Boolean get() = state == "playing"

    /** 是否处于暂停状态 */
    val isPaused: Boolean get() = state == "paused"

    /** 是否正在缓冲 */
    val isBuffering: Boolean get() = state == "buffering"

    /** 播放进度 0.0 ~ 1.0，未播放时为 0 */
    val progress: Float get() = if (totalDuration > 0) (position.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f) else 0f

    // ======================== 类型化回调 ========================

    /**
     * 监听播放状态变化（类型化回调，自动解析 JSON）
     * 同时更新 [state] 内部属性。回调中赋值给 Pager observable 属性可触发 UI 刷新。
     *
     * ```kotlin
     * audio.onPlayStateChanged { state -> playState = state }
     * ```
     */
    fun onPlayStateChanged(listener: (state: String) -> Unit) {
        toNative(
            keepCallbackAlive = true,
            methodName = METHOD_ON_PLAY_STATE_CHANGED,
            param = null,
            callback = { data ->
                if (data != null) {
                    val json = data as? com.tencent.kuikly.core.nvi.serialization.json.JSONObject ?: return@toNative
                    val state = json.optString("state", "idle")
                    this.state = state
                    listener.invoke(state)
                }
            },
            syncCall = false,
        )
    }

    /**
     * 监听播放进度更新（类型化回调，约 500ms 一次，自动解析 JSON）
     * 同时更新 [position]、[totalDuration] 内部属性。
     *
     * ```kotlin
     * audio.onTimeUpdate { pos, dur -> position = pos; duration = dur }
     * ```
     */
    fun onTimeUpdate(listener: (position: Long, duration: Long) -> Unit) {
        toNative(
            keepCallbackAlive = true,
            methodName = METHOD_ON_TIME_UPDATE,
            param = null,
            callback = { data ->
                if (data != null) {
                    val json = data as? com.tencent.kuikly.core.nvi.serialization.json.JSONObject ?: return@toNative
                    val pos = json.optString("current", "0").toLongOrNull() ?: 0L
                    val dur = json.optString("duration", "0").toLongOrNull() ?: 0L
                    position = pos
                    totalDuration = dur
                    listener.invoke(pos, dur)
                }
            },
            syncCall = false,
        )
    }

    /**
     * 监听播放错误（类型化回调，自动解析 JSON）
     * 同时更新 [lastErrorCode]、[lastErrorMessage] 内部属性。
     *
     * ```kotlin
     * audio.onError { code, message -> errorMsg = message }
     * ```
     */
    fun onError(listener: (code: String, message: String) -> Unit) {
        toNative(
            keepCallbackAlive = true,
            methodName = METHOD_ON_ERROR,
            param = null,
            callback = { data ->
                if (data != null) {
                    val json = data as? com.tencent.kuikly.core.nvi.serialization.json.JSONObject ?: return@toNative
                    val code = json.optString("code", "")
                    val msg = json.optString("message", "未知错误")
                    lastErrorCode = code
                    lastErrorMessage = msg
                    listener.invoke(code, msg)
                }
            },
            syncCall = false,
        )
    }

    /**
     * 监听播放列表当前曲目变化（类型化回调，自动解析 JSON）
     * 同时更新 [playlistIndex]、[playlistUrl]、[playlistTitle] 内部属性。
     *
     * ```kotlin
     * audio.onPlaylistIndexChanged { index, url, title -> currentIndex = index }
     * ```
     */
    fun onPlaylistIndexChanged(listener: (index: Int, url: String, title: String) -> Unit) {
        toNative(
            keepCallbackAlive = true,
            methodName = METHOD_ON_PLAYLIST_INDEX_CHANGED,
            param = null,
            callback = { data ->
                if (data != null) {
                    val json = data as? com.tencent.kuikly.core.nvi.serialization.json.JSONObject ?: return@toNative
                    val index = json.optString("index", "0").toIntOrNull() ?: 0
                    val url = json.optString("url", "")
                    val title = json.optString("title", "")
                    playlistIndex = index
                    playlistUrl = url
                    playlistTitle = title
                    listener.invoke(index, url, title)
                }
            },
            syncCall = false,
        )
    }

    // ======================== 基础播放控制 ========================

    /** 播放指定音频 */
    fun play(
        url: String,
        title: String = "",
        artist: String = "",
        coverUrl: String = "",
    ) {
        val params =
            buildJsonObject {
                put("url", url)
                put("title", title)
                put("artist", artist)
                put("coverUrl", coverUrl)
            }
        asyncToNativeMethod(METHOD_PLAY, params, null)
    }

    /** 播放指定的 AudioPlayItem */
    fun play(item: AudioPlayItem) {
        play(item.url, item.title, item.artist, item.coverUrl)
    }

    /** 暂停播放 */
    fun pause() {
        asyncToNativeMethod(METHOD_PAUSE, null, null)
    }

    /** 恢复播放 */
    fun resume() {
        asyncToNativeMethod(METHOD_RESUME, null, null)
    }

    /** 停止播放并释放资源 */
    fun stop() {
        asyncToNativeMethod(METHOD_STOP, null, null)
    }

    /** 跳转到指定播放位置（毫秒） */
    fun seekTo(positionMs: Long) {
        val params =
            buildJsonObject {
                put("position", positionMs.toString())
            }
        asyncToNativeMethod(METHOD_SEEK_TO, params, null)
    }

    /** 跳转到指定播放进度（0.0 ~ 1.0） */
    fun seekToProgress(progress: Float) {
        if (totalDuration > 0) {
            seekTo((progress * totalDuration).toLong())
        }
    }

    // ======================== 音量 & 倍速 ========================

    /** 设置音量，范围 0.0 ~ 1.0 */
    fun setVolume(volume: Float) {
        val params =
            buildJsonObject {
                put("volume", volume.toString())
            }
        asyncToNativeMethod(METHOD_SET_VOLUME, params, null)
    }

    /** 设置播放速度，范围 0.5 ~ 2.0 */
    fun setSpeed(speed: Float) {
        val params =
            buildJsonObject {
                put("speed", speed.toString())
            }
        asyncToNativeMethod(METHOD_SET_SPEED, params, null)
    }

    // ======================== 播放列表 ========================

    /** 设置播放列表及起始索引 */
    fun setPlaylist(
        items: List<AudioPlayItem>,
        startIndex: Int = 0,
    ) {
        val params =
            buildJsonObject {
                put("items", AudioPlayItem.listToJson(items))
                put("startIndex", startIndex.toString())
            }
        asyncToNativeMethod(METHOD_SET_PLAYLIST, params, null)
    }

    /** 切换到下一曲 */
    fun next() {
        asyncToNativeMethod(METHOD_NEXT, null, null)
    }

    /** 切换到上一曲 */
    fun previous() {
        asyncToNativeMethod(METHOD_PREVIOUS, null, null)
    }

    /** 设置播放模式 */
    fun setPlayMode(mode: AudioPlayMode) {
        val params =
            buildJsonObject {
                put("mode", mode.value)
            }
        asyncToNativeMethod(METHOD_SET_PLAY_MODE, params, null)
    }

    // ======================== 后台播放 ========================

    /** 启用/禁用后台播放 */
    fun enableBackgroundPlay(enable: Boolean) {
        val params =
            buildJsonObject {
                put("enable", if (enable) "1" else "0")
            }
        asyncToNativeMethod(METHOD_ENABLE_BACKGROUND_PLAY, params, null)
    }

    // ======================== 状态查询（同步） ========================

    /** 同步查询当前播放状态 */
    fun getPlayState(): String =
        toNative(
            keepCallbackAlive = false,
            methodName = METHOD_GET_PLAY_STATE,
            param = null,
            syncCall = true,
        )?.toString() ?: "idle"

    /** 同步查询当前播放位置（毫秒） */
    fun getCurrentPosition(): Long {
        val result =
            toNative(
                keepCallbackAlive = false,
                methodName = METHOD_GET_CURRENT_POSITION,
                param = null,
                syncCall = true,
            )?.toString() ?: "0"
        return result.toLongOrNull() ?: 0L
    }

    /** 同步查询音频总时长（毫秒） */
    fun getDuration(): Long {
        val result =
            toNative(
                keepCallbackAlive = false,
                methodName = METHOD_GET_DURATION,
                param = null,
                syncCall = true,
            )?.toString() ?: "0"
        return result.toLongOrNull() ?: 0L
    }

    /** 同步查询当前音量（0.0 ~ 1.0） */
    fun getVolume(): Float {
        val result =
            toNative(
                keepCallbackAlive = false,
                methodName = METHOD_GET_VOLUME,
                param = null,
                syncCall = true,
            )?.toString() ?: "1.0"
        return result.toFloatOrNull() ?: 1.0f
    }

    /** 同步查询当前播放速度（0.5 ~ 2.0） */
    fun getSpeed(): Float {
        val result =
            toNative(
                keepCallbackAlive = false,
                methodName = METHOD_GET_SPEED,
                param = null,
                syncCall = true,
            )?.toString() ?: "1.0"
        return result.toFloatOrNull() ?: 1.0f
    }

    // ======================== 工具方法 ========================

    private fun asyncToNativeMethod(
        methodName: String,
        data: Any?,
        callbackFn: CallbackFn?,
    ) {
        toNative(
            keepCallbackAlive = false,
            methodName = methodName,
            param = data,
            callback = callbackFn,
            syncCall = false,
        )
    }

    companion object {
        const val MODULE_NAME = "KRAudioPlayerModule"

        private const val METHOD_PLAY = "play"
        private const val METHOD_PAUSE = "pause"
        private const val METHOD_RESUME = "resume"
        private const val METHOD_STOP = "stop"
        private const val METHOD_SEEK_TO = "seekTo"
        private const val METHOD_SET_VOLUME = "setVolume"
        private const val METHOD_SET_SPEED = "setSpeed"
        private const val METHOD_SET_PLAYLIST = "setPlaylist"
        private const val METHOD_NEXT = "next"
        private const val METHOD_PREVIOUS = "previous"
        private const val METHOD_SET_PLAY_MODE = "setPlayMode"
        private const val METHOD_ENABLE_BACKGROUND_PLAY = "enableBackgroundPlay"
        private const val METHOD_GET_PLAY_STATE = "getPlayState"
        private const val METHOD_GET_CURRENT_POSITION = "getCurrentPosition"
        private const val METHOD_GET_DURATION = "getDuration"
        private const val METHOD_GET_VOLUME = "getVolume"
        private const val METHOD_GET_SPEED = "getSpeed"
        private const val METHOD_ON_TIME_UPDATE = "onTimeUpdate"
        private const val METHOD_ON_PLAY_STATE_CHANGED = "onPlayStateChanged"
        private const val METHOD_ON_ERROR = "onError"
        private const val METHOD_ON_PLAYLIST_INDEX_CHANGED = "onPlaylistIndexChanged"

        internal inline fun buildJsonObject(block: JsonBuilder.() -> Unit): String {
            val builder = JsonBuilder()
            builder.block()
            return builder.build()
        }
    }
}

/**
 * 简单的 JSON 构建器（避免依赖 org.json）
 */
internal class JsonBuilder {
    private val entries = mutableListOf<Pair<String, String>>()

    fun put(
        key: String,
        value: String,
    ) {
        entries.add(key to value)
    }

    fun build(): String {
        val sb = StringBuilder()
        sb.append("{")
        entries.forEachIndexed { index, (key, value) ->
            if (index > 0) sb.append(",")
            sb.append("\"").append(escapeJson(key)).append("\"")
            sb.append(":")
            sb.append("\"").append(escapeJson(value)).append("\"")
        }
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
