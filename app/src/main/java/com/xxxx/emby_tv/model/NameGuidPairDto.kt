package com.xxxx.emby_tv.model

import com.google.gson.annotations.SerializedName

/**
 * 名称GUID对数据模型
 */
data class NameGuidPairDto(
    @SerializedName("Name") val name: String? = null,
    @SerializedName("Id") val id: String? = null
)