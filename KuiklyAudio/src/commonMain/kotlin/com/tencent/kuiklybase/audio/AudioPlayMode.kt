package com.tencent.kuiklybase.audio

/**
 * 音频播放模式
 */
enum class AudioPlayMode(
    val value: String,
) {
    /** 顺序播放 */
    SEQUENCE("sequence"),

    /** 单曲循环 */
    SINGLE_LOOP("single"),

    /** 列表循环 */
    LIST_LOOP("loop"),
    ;

    companion object {
        fun fromValue(value: String): AudioPlayMode = entries.firstOrNull { it.value == value } ?: SEQUENCE
    }
}
