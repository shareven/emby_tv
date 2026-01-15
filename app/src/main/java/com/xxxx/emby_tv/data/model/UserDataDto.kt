package com.xxxx.emby_tv.data.model

import com.google.gson.annotations.SerializedName

/**
 * Emby UserDataDto 数据模型
 * 对应 Emby API 返回的用户数据信息
 */
data class UserDataDto(
    @SerializedName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerializedName("PlayCount") val playCount: Int = 0,
    @SerializedName("IsFavorite") val isFavorite: Boolean = false,
    @SerializedName("Likes") val likes: Boolean? = null,
    @SerializedName("LastPlayedDate") val lastPlayedDate: String? = null,
    @SerializedName("Played") val played: Boolean = false,
    @SerializedName("Key") val key: String? = null,
    @SerializedName("ItemId") val itemId: String? = null,
    @SerializedName("UnplayedItemCount") val unplayedItemCount: Int? = null
)