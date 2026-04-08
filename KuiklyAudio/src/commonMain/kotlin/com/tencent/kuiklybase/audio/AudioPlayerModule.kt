package com.tencent.kuiklybase.audio

import com.tencent.kuikly.core.module.CallbackFn
import com.tencent.kuikly.core.module.Module

/**
 * KuiklyAudio 音频播放 Module
 *
 * 基于 Kuikly Module 机制实现的跨平台音频播放组件。
 * 支持基础播放控制、播放列表、后台播放、锁屏控制、音量倍速等功能。
 *
 * 使用方式：
 * ```kotlin
 * // 在 Pager 中注册
 * override fun createExternalModules(): Map<String, Module>? {
 *     return mapOf(AudioPlayerModule.MODULE_NAME to AudioPlayerModule())
 * }
 *
 * // 获取并使用
 * val audio = acquireModule<AudioPlayerModule>(AudioPlayerModule.MODULE_NAME)
 * audio.play("https://example.com/audio.mp3", title = "歌曲名")
 * ```
 */
class AudioPlayerModule : Module() {
    override fun moduleName(): String = MODULE_NAME

    // ======================== 基础播放控制 ========================

    /**
     * 播放指定音频
     * @param url 音频 URL（支持网络 URL 和本地路径）
     * @param title 标题（用于锁屏展示）
     * @param artist 艺术家（用于锁屏展示）
     * @param coverUrl 封面图 URL（用于锁屏展示）
     */
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

    /**
     * 播放指定的 AudioPlayItem
     */
    fun play(item: AudioPlayItem) {
        play(item.url, item.title, item.artist, item.coverUrl)
    }

    /**
     * 暂停播放
     */
    fun pause() {
        asyncToNativeMethod(METHOD_PAUSE, null, null)
    }

    /**
     * 恢复播放
     */
    fun resume() {
        asyncToNativeMethod(METHOD_RESUME, null, null)
    }

    /**
     * 停止播放并释放资源
     */
    fun stop() {
        asyncToNativeMethod(METHOD_STOP, null, null)
    }

    /**
     * 跳转到指定播放位置
     * @param positionMs 目标位置（毫秒）
     */
    fun seekTo(positionMs: Long) {
        val params =
            buildJsonObject {
                put("position", positionMs.toString())
            }
        asyncToNativeMethod(METHOD_SEEK_TO, params, null)
    }

    // ======================== 音量 & 倍速 ========================

    /**
     * 设置音量
     * @param volume 音量值，范围 0.0 ~ 1.0
     */
    fun setVolume(volume: Float) {
        val params =
            buildJsonObject {
                put("volume", volume.toString())
            }
        asyncToNativeMethod(METHOD_SET_VOLUME, params, null)
    }

    /**
     * 设置播放速度
     * @param speed 播放速度，范围 0.5 ~ 2.0
     */
    fun setSpeed(speed: Float) {
        val params =
            buildJsonObject {
                put("speed", speed.toString())
            }
        asyncToNativeMethod(METHOD_SET_SPEED, params, null)
    }

    // ======================== 播放列表 ========================

    /**
     * 设置播放列表
     * @param items 播放项列表
     * @param startIndex 起始播放索引，默认 0
     */
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

    /**
     * 切换到下一曲
     */
    fun next() {
        asyncToNativeMethod(METHOD_NEXT, null, null)
    }

    /**
     * 切换到上一曲
     */
    fun previous() {
        asyncToNativeMethod(METHOD_PREVIOUS, null, null)
    }

    /**
     * 设置播放模式
     * @param mode 播放模式（顺序播放/单曲循环/列表循环）
     */
    fun setPlayMode(mode: AudioPlayMode) {
        val params =
            buildJsonObject {
                put("mode", mode.value)
            }
        asyncToNativeMethod(METHOD_SET_PLAY_MODE, params, null)
    }

    // ======================== 后台播放 ========================

    /**
     * 启用/禁用后台播放
     * @param enable 是否启用
     */
    fun enableBackgroundPlay(enable: Boolean) {
        val params =
            buildJsonObject {
                put("enable", if (enable) "1" else "0")
            }
        asyncToNativeMethod(METHOD_ENABLE_BACKGROUND_PLAY, params, null)
    }

    // ======================== 状态查询 ========================

    /**
     * 同步获取当前播放状态
     * @return 状态字符串：idle / playing / paused / stopped / completed / error
     */
    fun getPlayState(): String =
        toNative(
            keepCallbackAlive = false,
            methodName = METHOD_GET_PLAY_STATE,
            param = null,
            syncCall = true,
        )?.toString() ?: "idle"

    /**
     * 同步获取当前播放位置（毫秒）
     */
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

    /**
     * 同步获取音频总时长（毫秒）
     */
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

    // ======================== 事件回调 ========================

    /**
     * 监听播放进度更新（约 500ms 一次）
     * 回调数据：{"current": "12500", "duration": "180000"}
     * @param callback 回调函数
     */
    fun onTimeUpdate(callback: CallbackFn) {
        toNative(
            keepCallbackAlive = true,
            methodName = METHOD_ON_TIME_UPDATE,
            param = null,
            callback = callback,
            syncCall = false,
        )
    }

    /**
     * 监听播放状态变化
     * 回调数据：{"state": "playing"} — idle/playing/paused/stopped/completed/error
     * @param callback 回调函数
     */
    fun onPlayStateChanged(callback: CallbackFn) {
        toNative(
            keepCallbackAlive = true,
            methodName = METHOD_ON_PLAY_STATE_CHANGED,
            param = null,
            callback = callback,
            syncCall = false,
        )
    }

    /**
     * 监听播放错误
     * 回调数据：{"code": "error_code", "message": "error message"}
     * @param callback 回调函数
     */
    fun onError(callback: CallbackFn) {
        toNative(
            keepCallbackAlive = true,
            methodName = METHOD_ON_ERROR,
            param = null,
            callback = callback,
            syncCall = false,
        )
    }

    /**
     * 监听播放列表当前曲目变化
     * 回调数据：{"index": "2", "url": "...", "title": "..."}
     * @param callback 回调函数
     */
    fun onPlaylistIndexChanged(callback: CallbackFn) {
        toNative(
            keepCallbackAlive = true,
            methodName = METHOD_ON_PLAYLIST_INDEX_CHANGED,
            param = null,
            callback = callback,
            syncCall = false,
        )
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

        // 方法名常量
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
        private const val METHOD_ON_TIME_UPDATE = "onTimeUpdate"
        private const val METHOD_ON_PLAY_STATE_CHANGED = "onPlayStateChanged"
        private const val METHOD_ON_ERROR = "onError"
        private const val METHOD_ON_PLAYLIST_INDEX_CHANGED = "onPlaylistIndexChanged"

        /**
         * JSON 构建辅助
         */
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
