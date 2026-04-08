package com.example.myapplication

import com.example.myapplication.base.BasePager
import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.*
import com.tencent.kuikly.core.views.compose.Button
import com.tencent.kuikly.core.views.layout.Column
import com.tencent.kuikly.core.views.layout.Row
import com.tencent.kuiklybase.audio.AudioPlayItem
import com.tencent.kuiklybase.audio.AudioPlayMode
import com.tencent.kuiklybase.audio.AudioPlayerModule

@Page("router", supportInLocal = true)
internal class AudioPlayerDemoPage : BasePager() {

    // 播放状态
    private var playState: String by observable("idle")
    private var currentPosition: Long by observable(0L)
    private var duration: Long by observable(0L)
    private var currentIndex: Int by observable(0)
    private var errorMsg: String by observable("")
    private var currentPlayMode: AudioPlayMode by observable(AudioPlayMode.SEQUENCE)
    private var volume: Float by observable(1.0f)
    private var speed: Float by observable(1.0f)
    private var isSeeking: Boolean = false

    // 播放列表
    private val playlist = listOf(
        AudioPlayItem(
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            title = "SoundHelix Song 1",
            artist = "T. Schürger",
            coverUrl = ""
        ),
        AudioPlayItem(
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            title = "SoundHelix Song 2",
            artist = "T. Schürger",
            coverUrl = ""
        ),
        AudioPlayItem(
            url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            title = "SoundHelix Song 3",
            artist = "T. Schürger",
            coverUrl = ""
        )
    )

    private lateinit var audioModule: AudioPlayerModule

    override fun created() {
        super.created()
        audioModule = acquireModule(AudioPlayerModule.MODULE_NAME)

        // 注册回调
        audioModule.onPlayStateChanged { data ->
            if (data != null) {
                playState = data.optString("state", "idle")
            }
        }

        audioModule.onTimeUpdate { data ->
            if (data != null) {
                currentPosition = data.optString("current", "0").toLongOrNull() ?: 0L
                duration = data.optString("duration", "0").toLongOrNull() ?: 0L
            }
        }

        audioModule.onError { data ->
            if (data != null) {
                errorMsg = data.optString("message", "未知错误")
            }
        }

        audioModule.onPlaylistIndexChanged { data ->
            if (data != null) {
                currentIndex = data.optString("index", "0").toIntOrNull() ?: 0
            }
        }
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            attr {
                backgroundColor(Color.WHITE)
            }

            Scroller {
                attr {
                    flex(1f)
                    padding(16f)
                }

                // ===== 当前曲目信息 =====
                SectionTitle("当前曲目")

                View {
                    attr {
                        backgroundColor(Color(0xFFF5F5F5))
                        borderRadius(12f)
                        padding(16f)
                        marginBottom(16f)
                    }
                    Text {
                        attr {
                            text(if (ctx.currentIndex < ctx.playlist.size) ctx.playlist[ctx.currentIndex].title else "未选择")
                            fontSize(18f)
                            fontWeightBold()
                            color(Color(0xFF333333))
                        }
                    }
                    Text {
                        attr {
                            marginTop(4f)
                            text(if (ctx.currentIndex < ctx.playlist.size) ctx.playlist[ctx.currentIndex].artist else "")
                            fontSize(14f)
                            color(Color(0xFF999999))
                        }
                    }
                    // 状态
                    Row {
                        attr {
                            marginTop(8f)
                            alignItemsCenter()
                        }
                        Text {
                            attr {
                                text("状态: ")
                                fontSize(13f)
                                color(Color(0xFF666666))
                            }
                        }
                        Text {
                            attr {
                                text(ctx.playState)
                                fontSize(13f)
                                fontWeightBold()
                                color(
                                    when (ctx.playState) {
                                        "playing" -> Color(0xFF4CAF50)
                                        "paused" -> Color(0xFFFFA000)
                                        "error" -> Color(0xFFFF0000)
                                        else -> Color(0xFF999999)
                                    }
                                )
                            }
                        }
                    }
                }

                // ===== 进度条 =====
                SectionTitle("播放进度")

                View {
                    attr {
                        backgroundColor(Color(0xFFF5F5F5))
                        borderRadius(12f)
                        padding(16f)
                        marginBottom(16f)
                    }
                    // 时间显示
                    Row {
                        attr {
                            justifyContentSpaceBetween()
                            marginBottom(8f)
                        }
                        Text {
                            attr {
                                text(ctx.formatTime(ctx.currentPosition))
                                fontSize(12f)
                                color(Color(0xFF666666))
                            }
                        }
                        Text {
                            attr {
                                text(ctx.formatTime(ctx.duration))
                                fontSize(12f)
                                color(Color(0xFF666666))
                            }
                        }
                    }
                    // Slider 进度条
                    Slider {
                        attr {
                            size(pagerData.pageViewWidth - 64f, 30f)
                            currentProgress(
                                if (ctx.duration > 0) {
                                    (ctx.currentPosition.toFloat() / ctx.duration.toFloat()).coerceIn(0f, 1f)
                                } else {
                                    0f
                                }
                            )
                            progressColor(Color(0xFF1E88E5))
                            trackColor(Color(0xFFE0E0E0))
                            thumbColor(Color(0xFF1E88E5))
                        }
                        event {
                            beginDragSlider {
                                ctx.isSeeking = true
                            }
                            endDragSlider {
                                ctx.isSeeking = false
                            }
                            progressDidChanged { progress ->
                                // 只在用户拖拽时才 seekTo，代码更新 progress 时不触发
                                if (ctx.isSeeking && ctx.duration > 0) {
                                    val targetPos = (progress * ctx.duration).toLong()
                                    ctx.audioModule.seekTo(targetPos)
                                }
                            }
                        }
                    }
                }

                // ===== 播放控制 =====
                SectionTitle("播放控制")

                Row {
                    attr {
                        justifyContentSpaceAround()
                        alignItemsCenter()
                        marginBottom(16f)
                    }
                    // 上一曲
                    ControlButton("⏮ 上一曲") {
                        ctx.audioModule.previous()
                    }
                    // 播放/暂停
                    ControlButton(if (ctx.playState == "playing") "⏸ 暂停" else "▶ 播放") {
                        when (ctx.playState) {
                            "playing" -> ctx.audioModule.pause()
                            "paused" -> ctx.audioModule.resume()
                            else -> {
                                // 首次播放，设置播放列表
                                ctx.audioModule.setPlaylist(ctx.playlist, ctx.currentIndex)
                            }
                        }
                    }
                    // 停止
                    ControlButton("⏹ 停止") {
                        ctx.audioModule.stop()
                    }
                    // 下一曲
                    ControlButton("⏭ 下一曲") {
                        ctx.audioModule.next()
                    }
                }

                // ===== 播放模式 =====
                SectionTitle("播放模式")

                Row {
                    attr {
                        justifyContentSpaceAround()
                        marginBottom(16f)
                    }
                    ModeButton("顺序", AudioPlayMode.SEQUENCE, ctx.currentPlayMode) {
                        ctx.currentPlayMode = AudioPlayMode.SEQUENCE
                        ctx.audioModule.setPlayMode(AudioPlayMode.SEQUENCE)
                    }
                    ModeButton("单曲循环", AudioPlayMode.SINGLE_LOOP, ctx.currentPlayMode) {
                        ctx.currentPlayMode = AudioPlayMode.SINGLE_LOOP
                        ctx.audioModule.setPlayMode(AudioPlayMode.SINGLE_LOOP)
                    }
                    ModeButton("列表循环", AudioPlayMode.LIST_LOOP, ctx.currentPlayMode) {
                        ctx.currentPlayMode = AudioPlayMode.LIST_LOOP
                        ctx.audioModule.setPlayMode(AudioPlayMode.LIST_LOOP)
                    }
                }

                // ===== 音量控制 =====
                SectionTitle("音量控制")

                View {
                    attr {
                        backgroundColor(Color(0xFFF5F5F5))
                        borderRadius(12f)
                        padding(16f)
                        marginBottom(16f)
                    }
                    Row {
                        attr {
                            alignItemsCenter()
                        }
                        Text {
                            attr {
                                text("音量: ${(ctx.volume * 100).toInt()}%")
                                fontSize(13f)
                                color(Color(0xFF666666))
                                width(80f)
                            }
                        }
                        Slider {
                            attr {
                                size(pagerData.pageViewWidth - 64f - 80f, 30f)
                                currentProgress(ctx.volume)
                                progressColor(Color(0xFF4CAF50))
                                trackColor(Color(0xFFE0E0E0))
                                thumbColor(Color(0xFF4CAF50))
                            }
                            event {
                                progressDidChanged { progress ->
                                    ctx.volume = progress
                                    ctx.audioModule.setVolume(progress)
                                }
                            }
                        }
                    }
                }

                // ===== 倍速控制 =====
                SectionTitle("倍速控制")

                Row {
                    attr {
                        justifyContentSpaceAround()
                        marginBottom(16f)
                    }
                    SpeedButton("0.5x", 0.5f, ctx.speed) {
                        ctx.speed = 0.5f
                        ctx.audioModule.setSpeed(0.5f)
                    }
                    SpeedButton("1.0x", 1.0f, ctx.speed) {
                        ctx.speed = 1.0f
                        ctx.audioModule.setSpeed(1.0f)
                    }
                    SpeedButton("1.5x", 1.5f, ctx.speed) {
                        ctx.speed = 1.5f
                        ctx.audioModule.setSpeed(1.5f)
                    }
                    SpeedButton("2.0x", 2.0f, ctx.speed) {
                        ctx.speed = 2.0f
                        ctx.audioModule.setSpeed(2.0f)
                    }
                }

                // ===== 后台播放 =====
                SectionTitle("后台播放")

                Row {
                    attr {
                        justifyContentSpaceAround()
                        marginBottom(16f)
                    }
                    ControlButton("开启后台播放") {
                        ctx.audioModule.enableBackgroundPlay(true)
                    }
                    ControlButton("关闭后台播放") {
                        ctx.audioModule.enableBackgroundPlay(false)
                    }
                }

                // ===== 播放列表 =====
                SectionTitle("播放列表")

                View {
                    attr {
                        backgroundColor(Color(0xFFF5F5F5))
                        borderRadius(12f)
                        padding(12f)
                        marginBottom(16f)
                    }
                    ctx.playlist.forEachIndexed { index, item ->
                        View {
                            attr {
                                flexDirectionRow()
                                alignItemsCenter()
                                padding(10f)
                                borderRadius(8f)
                                backgroundColor(
                                    if (index == ctx.currentIndex) Color(0xFFE3F2FD) else Color.TRANSPARENT
                                )
                            }
                            event {
                                click {
                                    ctx.currentIndex = index
                                    ctx.audioModule.setPlaylist(ctx.playlist, index)
                                }
                            }
                            // 序号
                            Text {
                                attr {
                                    text("${index + 1}")
                                    fontSize(14f)
                                    fontWeightBold()
                                    width(24f)
                                    color(
                                        if (index == ctx.currentIndex) Color(0xFF1E88E5) else Color(0xFF999999)
                                    )
                                }
                            }
                            // 曲目信息
                            View {
                                attr {
                                    flex(1f)
                                    marginLeft(8f)
                                }
                                Text {
                                    attr {
                                        text(item.title)
                                        fontSize(15f)
                                        color(
                                            if (index == ctx.currentIndex) Color(0xFF1E88E5) else Color(0xFF333333)
                                        )
                                    }
                                }
                                Text {
                                    attr {
                                        text(item.artist)
                                        fontSize(12f)
                                        color(Color(0xFF999999))
                                        marginTop(2f)
                                    }
                                }
                            }
                            // 播放指示
                            if (index == ctx.currentIndex && ctx.playState == "playing") {
                                Text {
                                    attr {
                                        text("♪")
                                        fontSize(16f)
                                        color(Color(0xFF1E88E5))
                                    }
                                }
                            }
                        }
                    }
                }

                // ===== 单曲播放测试 =====
                SectionTitle("单曲播放测试")

                ControlButton("播放单曲") {
                    ctx.audioModule.play(
                        url = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                        title = "测试单曲",
                        artist = "Test Artist"
                    )
                }

                // ===== 错误信息 =====
                if (ctx.errorMsg.isNotEmpty()) {
                    View {
                        attr {
                            marginTop(16f)
                            backgroundColor(Color(0xFFFFEBEE))
                            borderRadius(8f)
                            padding(12f)
                        }
                        Text {
                            attr {
                                text("错误: ${ctx.errorMsg}")
                                fontSize(13f)
                                color(Color(0xFFD32F2F))
                            }
                        }
                    }
                }

                // 底部间距
                View {
                    attr { height(40f) }
                }
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "${min.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    }

    companion object {
        const val PAGE_NAME = "audio_demo"
    }
}

// ===== 辅助组合组件 =====

private fun ViewContainer<*, *>.SectionTitle(title: String) {
    Text {
        attr {
            text(title)
            fontSize(15f)
            fontWeightBold()
            color(Color(0xFF333333))
            marginBottom(8f)
        }
    }
}

private fun ViewContainer<*, *>.ControlButton(label: String, onClick: () -> Unit) {
    Button {
        attr {
            height(40f)
            paddingLeft(16f)
            paddingRight(16f)
            borderRadius(20f)
            backgroundColor(Color(0xFF1E88E5))
            titleAttr {
                text(label)
                fontSize(14f)
                color(Color.WHITE)
            }
        }
        event {
            click {
                onClick()
            }
        }
    }
}

private fun ViewContainer<*, *>.ModeButton(
    label: String,
    mode: AudioPlayMode,
    currentMode: AudioPlayMode,
    onClick: () -> Unit,
) {
    Button {
        attr {
            height(36f)
            paddingLeft(14f)
            paddingRight(14f)
            borderRadius(18f)
            backgroundColor(
                if (mode == currentMode) Color(0xFF1E88E5) else Color(0xFFE0E0E0)
            )
            titleAttr {
                text(label)
                fontSize(13f)
                color(
                    if (mode == currentMode) Color.WHITE else Color(0xFF666666)
                )
            }
        }
        event {
            click { onClick() }
        }
    }
}

private fun ViewContainer<*, *>.SpeedButton(
    label: String,
    speedValue: Float,
    currentSpeed: Float,
    onClick: () -> Unit,
) {
    Button {
        attr {
            height(36f)
            paddingLeft(14f)
            paddingRight(14f)
            borderRadius(18f)
            backgroundColor(
                if (speedValue == currentSpeed) Color(0xFFFF9800) else Color(0xFFE0E0E0)
            )
            titleAttr {
                text(label)
                fontSize(13f)
                color(
                    if (speedValue == currentSpeed) Color.WHITE else Color(0xFF666666)
                )
            }
        }
        event {
            click { onClick() }
        }
    }
}
