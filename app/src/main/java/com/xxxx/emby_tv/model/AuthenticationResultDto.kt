package com.xxxx.emby_tv.model

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject

/**
 * Emby 认证结果数据模型
 * 对应 Emby API 认证接口返回的结果
 */
data class AuthenticationResultDto(
    @SerializedName("AccessToken") val accessToken: String = "",
    @SerializedName("ServerId") val serverId: String = "",
    @SerializedName("User") val user: UserDto? = null, // 这里复用 UserDto
    @SerializedName("SessionInfo") val sessionInfo: JsonObject? = null
)