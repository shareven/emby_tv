package com.xxxx.emby_tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Emby BaseExternalUrl 数据模型
 * 对应 Emby API 返回的外部链接信息
 */
data class BaseExternalUrlDto(
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Url") val url: String? = null
)