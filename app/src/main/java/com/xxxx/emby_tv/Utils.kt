package com.xxxx.emby_tv

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaDto

object Utils {
    val gson = Gson()

    fun getImageUrl(serverUrl: String, item: BaseItemDto, isShowImg17: Boolean): String {
        // 1. 获取图片标签（确保你的 DTO 中 imageTags 是 Map<String, String>?）
        val imageTags = item.imageTags
        val parentBackdropImageTags = item.parentBackdropImageTags
        val itemId = item.id

        if (isShowImg17) {
            // 优先使用父级的背景图 (Backdrop)
            if (item.parentBackdropItemId != null && !parentBackdropImageTags.isNullOrEmpty()) {
                val tag = parentBackdropImageTags[0]
                return "$serverUrl/emby/Items/${item.parentBackdropItemId}/Images/Backdrop?maxWidth=500&tag=$tag&quality=80"
            }
            // 其次使用父级的缩略图 (Thumb)
            else if (item.parentThumbItemId != null) {
                return "$serverUrl/emby/Items/${item.parentThumbItemId}/Images/Thumb?maxWidth=500&tag=${item.parentThumbImageTag}&quality=80"
            }
            // 再次使用自己的缩略图 (Thumb)
            else if (imageTags?.containsKey("Thumb") == true) {
                val tag = imageTags["Thumb"]
                return "$serverUrl/emby/Items/$itemId/Images/Thumb?maxWidth=500&tag=$tag&quality=80"
            }
        }

        // 默认返回主封面图 (Primary)
        val primaryTag = imageTags?.get("Primary") ?: item.parentPrimaryImageTag
        val primaryId = if (imageTags?.containsKey("Primary") == true) itemId else item.parentPrimaryImageItemId

        if (!primaryTag.isNullOrEmpty() && !primaryId.isNullOrEmpty()) {
            return "$serverUrl/emby/Items/$primaryId/Images/Primary?maxWidth=500&tag=$primaryTag&quality=80"
        }

        return ""
    }

    fun formatDate(dateStr: Any?): String {
        if (dateStr == null) return ""
        val s = dateStr.toString()
        try {
            // Simple ISO 8601 substring (YYYY-MM-DD)
            if (s.length >= 10) {
                return s.substring(0, 10)
            }
        } catch (e: Exception) {
            return s
        }
        return s
    }

    fun formatRuntimeFromTicks(ticks: Long): String {
        val totalSeconds = ticks / 10000000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        if (hours > 0) {
            return "${hours}h ${minutes}m"
        }
        return "${minutes}m"
    }

    /**
     * 根据 PlaybackInfo 响应判定播放方式
     * 判定优先级: DirectPlay > DirectStream > Transcode
     *
     * @param media 包含 MediaSources 列表的媒体信息
     * @return PlayMethod: "DirectPlay" | "DirectStream" | "Transcode"
     */
    fun determinePlayMethod(media: MediaDto): String {
        val source = media.mediaSources?.firstOrNull() ?: return "Transcode"
        
        return when {
            source.supportsDirectPlay == true -> "DirectPlay"
            source.supportsDirectStream == true -> "DirectStream"
            !source.transcodingUrl.isNullOrEmpty() -> "Transcode"
            else -> "Transcode"
        }
    }

    fun formatFileSize(bytes: Long?): String {
        val b = bytes ?: 0L
        if (b <= 0) return ""
        val mb = b / 1024.0 / 1024.0
        if (mb > 1024) {
            return "%.2f GB".format(mb / 1024.0)
        }
        return "%.0f MB".format(mb)
    }

    fun formatDuration(ms: Long): String {
        // 再次保险：如果传入的是负数（如 C.TIME_UNSET 直接透传），返回 0
        if (ms <= 0) return "00:00"

        val seconds = ms / 1000
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60

        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    fun formatMbps(bps: Int?): String {
        val b = bps ?: 0
        if (b <= 0) return ""
        val mbps = b / 1000000.0
        return "%.1f Mbps".format(mbps)
    }

    fun formatKbps(bps:Int?): String {
        val b = bps ?: 0
        if (b <= 0) return ""
        val kbps = b / 1000
        return "$kbps kbps"
    }

    fun formatBandwidth(bps: Long): String {
        if (bps <= 0) return ""

        // bps (bits/s) → bytes/s
        val bytesPerSecond = bps / 8.0

        // bytes/s → KB/s
        val kbs = bytesPerSecond / 1024.0

        // bytes/s → MB/s
        val mbs = kbs / 1024.0

        return if (mbs >= 1.0) {
            "%.1f MB/s".format(mbs)
        } else {
            "%.0f KB/s".format(kbs)
        }
    }

    /**
     * 将 Emby ticks 转换为毫秒
     * Emby 使用 10000 ticks = 1ms 的转换比例
     * 
     * @param ticks Emby ticks 值
     * @return 毫秒值
     */
    fun ticksToMs(ticks: Long): Long {
        return ticks / 10000
    }


    fun getTranscodeReasonText(context: Context, reasons: List<String>): String {
        if (reasons.isEmpty()) return ""

        return reasons.joinToString(", ") { reason ->
            // 动态根据字符串名称获取资源 ID
            val resId = context.resources.getIdentifier(
                reason, "string", context.packageName
            )
            if (resId != 0) context.getString(resId) else reason
        }
    }
}

// Extension functions for JsonObject to safely get values
fun JsonObject.safeGetString(key: String): String? {
    return if (has(key) && !get(key).isJsonNull) {
        get(key).asString
    } else {
        null
    }
}

