package com.xxxx.emby_tv.model

import com.google.gson.annotations.SerializedName

/**
 * Emby 通用响应包装类
 * 用于处理Emby API返回的标准响应格式
 */
data class EmbyResponseDto<T>(
    @SerializedName("Items") val items: List<T> = emptyList(),
    @SerializedName("TotalRecordCount") val totalRecordCount: Int = 0
)