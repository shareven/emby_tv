package com.xxxx.emby_tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * 媒体信息数据模型
 * 对应 Emby API 返回的媒体信息
 */
data class MediaDto(
    @SerializedName("MediaSources") val mediaSources: List<MediaSourceInfoDto>? = null,
    @SerializedName("PlaySessionId") val playSessionId: String? = null
)

