package com.tencent.kuiklybase.audio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android 端音频播放 Module 实现
 *
 * 基于 ExoPlayer (Media3) 实现，支持：
 * - 基础播放控制（play/pause/resume/stop/seekTo）
 * - 播放进度回调
 * - 播放列表管理
 * - 后台播放（Foreground Service）
 * - 音量/倍速控制
 * - 锁屏控制（MediaSession）
 */
class KRAudioPlayerModule : KuiklyRenderBaseModule() {

    private var player: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    // 长期回调引用
    private var timeUpdateCallback: KuiklyRenderCallback? = null
    private var playStateCallback: KuiklyRenderCallback? = null
    private var errorCallback: KuiklyRenderCallback? = null
    private var playlistIndexCallback: KuiklyRenderCallback? = null

    // 播放列表
    private var playlist: MutableList<AudioItemInfo> = mutableListOf()
    private var currentPlayMode: String = "sequence"
    private var backgroundPlayEnabled: Boolean = false

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "play" -> handlePlay(params)
            "pause" -> handlePause()
            "resume" -> handleResume()
            "stop" -> handleStop()
            "seekTo" -> handleSeekTo(params)
            "setVolume" -> handleSetVolume(params)
            "setSpeed" -> handleSetSpeed(params)
            "setPlaylist" -> handleSetPlaylist(params)
            "next" -> handleNext()
            "previous" -> handlePrevious()
            "setPlayMode" -> handleSetPlayMode(params)
            "enableBackgroundPlay" -> handleEnableBackgroundPlay(params)
            "getPlayState" -> getPlayStateString()
            "getCurrentPosition" -> (player?.currentPosition ?: 0L).toString()
            "getDuration" -> (player?.duration?.takeIf { it > 0 } ?: 0L).toString()
            "getVolume" -> (player?.volume?.toString() ?: "1.0")
            "getSpeed" -> (player?.playbackParameters?.speed?.toString() ?: "1.0")
            "onTimeUpdate" -> { timeUpdateCallback = callback; null }
            "onPlayStateChanged" -> { playStateCallback = callback; null }
            "onError" -> { errorCallback = callback; null }
            "onPlaylistIndexChanged" -> { playlistIndexCallback = callback; null }
            else -> super.call(method, params, callback)
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        player?.let { return it }
        val ctx = getAppContext() ?: throw IllegalStateException("Context is null")
        val newPlayer = ExoPlayer.Builder(ctx).build()
        newPlayer.addListener(playerListener)
        player = newPlayer
        return newPlayer
    }

    // ======================== 播放控制 ========================

    private fun handlePlay(params: String?): Any? {
        val json = parseParams(params) ?: return null
        val url = json.optString("url", "")
        if (url.isEmpty()) return null

        val title = json.optString("title", "")
        val artist = json.optString("artist", "")
        val coverUrl = json.optString("coverUrl", "")

        val p = getOrCreatePlayer()
        val mediaItem = buildMediaItem(url, title, artist, coverUrl)
        p.setMediaItem(mediaItem)
        p.prepare()
        p.play()

        if (playlist.isEmpty()) {
            playlist.add(AudioItemInfo(url, title, artist, coverUrl))
        }

        return null
    }

    private fun handlePause(): Any? {
        player?.pause()
        return null
    }

    private fun handleResume(): Any? {
        player?.play()
        return null
    }

    private fun handleStop(): Any? {
        stopProgressUpdates()
        player?.stop()
        player?.clearMediaItems()
        notifyPlayStateChanged("stopped")
        return null
    }

    private fun handleSeekTo(params: String?): Any? {
        val json = parseParams(params) ?: return null
        val position = json.optString("position", "0").toLongOrNull() ?: 0L
        player?.seekTo(position)
        return null
    }

    // ======================== 音量 & 倍速 ========================

    private fun handleSetVolume(params: String?): Any? {
        val json = parseParams(params) ?: return null
        val volume = json.optString("volume", "1.0").toFloatOrNull() ?: 1.0f
        player?.volume = volume.coerceIn(0f, 1f)
        return null
    }

    private fun handleSetSpeed(params: String?): Any? {
        val json = parseParams(params) ?: return null
        val speed = json.optString("speed", "1.0").toFloatOrNull() ?: 1.0f
        player?.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
        return null
    }

    // ======================== 播放列表 ========================

    private fun handleSetPlaylist(params: String?): Any? {
        val json = parseParams(params) ?: return null
        val itemsStr = json.optString("items", "[]")
        val startIndex = json.optString("startIndex", "0").toIntOrNull() ?: 0

        val itemsArray = JSONArray(itemsStr)
        playlist.clear()
        val mediaItems = mutableListOf<MediaItem>()

        for (i in 0 until itemsArray.length()) {
            val item = itemsArray.getJSONObject(i)
            val url = item.optString("url", "")
            val title = item.optString("title", "")
            val artist = item.optString("artist", "")
            val coverUrl = item.optString("coverUrl", "")

            playlist.add(AudioItemInfo(url, title, artist, coverUrl))
            mediaItems.add(buildMediaItem(url, title, artist, coverUrl))
        }

        val p = getOrCreatePlayer()
        p.setMediaItems(mediaItems, startIndex, 0L)
        p.prepare()
        p.play()

        return null
    }

    private fun handleNext(): Any? {
        val p = player ?: return null
        if (p.hasNextMediaItem()) {
            p.seekToNextMediaItem()
        } else if (currentPlayMode == "loop" && p.mediaItemCount > 0) {
            p.seekTo(0, 0L)
        }
        return null
    }

    private fun handlePrevious(): Any? {
        val p = player ?: return null
        if (p.hasPreviousMediaItem()) {
            p.seekToPreviousMediaItem()
        } else if (currentPlayMode == "loop" && p.mediaItemCount > 0) {
            p.seekTo(p.mediaItemCount - 1, 0L)
        }
        return null
    }

    private fun handleSetPlayMode(params: String?): Any? {
        val json = parseParams(params) ?: return null
        currentPlayMode = json.optString("mode", "sequence")
        player?.let { p ->
            when (currentPlayMode) {
                "single" -> {
                    p.repeatMode = Player.REPEAT_MODE_ONE
                }
                "loop" -> {
                    p.repeatMode = Player.REPEAT_MODE_ALL
                }
                else -> {
                    p.repeatMode = Player.REPEAT_MODE_OFF
                }
            }
        }
        return null
    }

    // ======================== 后台播放 ========================

    private fun handleEnableBackgroundPlay(params: String?): Any? {
        val json = parseParams(params) ?: return null
        backgroundPlayEnabled = json.optString("enable", "0") == "1"

        val ctx = getAppContext() ?: return null
        if (backgroundPlayEnabled) {
            val intent = Intent(ctx, AudioPlaybackService::class.java)
            ctx.startForegroundService(intent)
        } else {
            val intent = Intent(ctx, AudioPlaybackService::class.java)
            ctx.stopService(intent)
        }
        return null
    }

    // ======================== Player Listener ========================

    private var lastReportedState: String = "idle"

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> reportStateIfChanged("idle")
                Player.STATE_BUFFERING -> {
                    reportStateIfChanged("buffering")
                }
                Player.STATE_READY -> {
                    if (player?.isPlaying == true) {
                        reportStateIfChanged("playing")
                        startProgressUpdates()
                    } else {
                        // playWhenReady = false 时才是真正暂停
                        if (player?.playWhenReady == false) {
                            reportStateIfChanged("paused")
                        }
                    }
                }
                Player.STATE_ENDED -> {
                    stopProgressUpdates()
                    reportStateIfChanged("completed")
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                reportStateIfChanged("playing")
                startProgressUpdates()
            } else {
                val p = player ?: return
                // 只有在 STATE_READY 且用户主动暂停（playWhenReady=false）时才报 paused
                // STATE_BUFFERING 时 isPlaying 也为 false，但不应报 paused
                // STATE_ENDED 由 onPlaybackStateChanged 处理
                if (p.playbackState == Player.STATE_READY && !p.playWhenReady) {
                    reportStateIfChanged("paused")
                }
                stopProgressUpdates()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            stopProgressUpdates()
            reportStateIfChanged("error")
            errorCallback?.invoke(
                mapOf(
                    "code" to (error.errorCode.toString()),
                    "message" to (error.message ?: "Unknown error")
                )
            )
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val p = player ?: return
            val index = p.currentMediaItemIndex
            if (index in playlist.indices) {
                val item = playlist[index]
                playlistIndexCallback?.invoke(
                    mapOf(
                        "index" to index.toString(),
                        "url" to item.url,
                        "title" to item.title
                    )
                )
            }
        }
    }

    private fun reportStateIfChanged(state: String) {
        if (state != lastReportedState) {
            lastReportedState = state
            notifyPlayStateChanged(state)
        }
    }

    // ======================== 进度更新 ========================

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressRunnable = object : Runnable {
            override fun run() {
                val p = player ?: return
                if (p.isPlaying) {
                    val current = p.currentPosition
                    val duration = p.duration.takeIf { it > 0 } ?: 0L
                    timeUpdateCallback?.invoke(
                        mapOf(
                            "current" to current.toString(),
                            "duration" to duration.toString()
                        )
                    )
                    mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
                }
            }
        }
        mainHandler.postDelayed(progressRunnable!!, PROGRESS_UPDATE_INTERVAL)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    // ======================== 辅助方法 ========================

    private fun notifyPlayStateChanged(state: String) {
        playStateCallback?.invoke(mapOf("state" to state))
    }

    private fun getPlayStateString(): String {
        val p = player ?: return "idle"
        return when {
            p.playbackState == Player.STATE_ENDED -> "completed"
            p.isPlaying -> "playing"
            p.playbackState == Player.STATE_BUFFERING -> "buffering"
            p.playbackState == Player.STATE_READY -> "paused"
            p.playbackState == Player.STATE_IDLE -> "idle"
            else -> "idle"
        }
    }

    private fun buildMediaItem(
        url: String,
        title: String,
        artist: String,
        coverUrl: String
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(if (coverUrl.isNotEmpty()) android.net.Uri.parse(coverUrl) else null)
            .build()

        return MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun parseParams(params: String?): JSONObject? {
        if (params.isNullOrEmpty()) return null
        return try {
            JSONObject(params)
        } catch (e: Exception) {
            null
        }
    }

    private fun getAppContext(): Context? {
        return context
    }

    override fun onDestroy() {
        stopProgressUpdates()
        player?.removeListener(playerListener)
        player?.release()
        player = null
        // 停止后台播放 Service
        if (backgroundPlayEnabled) {
            try {
                val ctx = getAppContext()
                ctx?.stopService(Intent(ctx, AudioPlaybackService::class.java))
            } catch (_: Exception) {}
        }
        timeUpdateCallback = null
        playStateCallback = null
        errorCallback = null
        playlistIndexCallback = null
        playlist.clear()
    }

    private data class AudioItemInfo(
        val url: String,
        val title: String,
        val artist: String,
        val coverUrl: String
    )

    companion object {
        private const val PROGRESS_UPDATE_INTERVAL = 500L
    }
}
