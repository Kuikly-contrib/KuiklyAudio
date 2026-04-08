package com.tencent.kuiklybase.audio

/**
 * 音频播放项数据类
 */
class AudioPlayItem(
    /** 音频 URL（支持网络 URL 和本地路径） */
    val url: String,
    /** 音频标题（用于锁屏信息展示） */
    val title: String = "",
    /** 艺术家名称（用于锁屏信息展示） */
    val artist: String = "",
    /** 封面图 URL（用于锁屏信息展示） */
    val coverUrl: String = "",
) {
    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"url\":\"").append(escapeJson(url)).append("\"")
        sb.append(",\"title\":\"").append(escapeJson(title)).append("\"")
        sb.append(",\"artist\":\"").append(escapeJson(artist)).append("\"")
        sb.append(",\"coverUrl\":\"").append(escapeJson(coverUrl)).append("\"")
        sb.append("}")
        return sb.toString()
    }

    companion object {
        fun listToJson(items: List<AudioPlayItem>): String {
            val sb = StringBuilder()
            sb.append("[")
            items.forEachIndexed { index, item ->
                if (index > 0) sb.append(",")
                sb.append(item.toJson())
            }
            sb.append("]")
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
}
