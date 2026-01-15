package com.xxxx.emby_tv.data.remote

import android.content.Context
import android.graphics.Point
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.xxxx.emby_tv.BuildConfig
import com.xxxx.emby_tv.data.model.AuthenticationResultDto
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.EmbyResponseDto
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.SessionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Emby API 接口定义
 * 无状态设计，所有参数显式传入
 */
object EmbyApi {
    private const val TAG = "EmbyApi"
    private const val CLIENT = "shareven/emby_tv"
    private val CLIENT_VERSION: String = BuildConfig.VERSION_NAME
    private const val DEVICE_NAME = "Android TV"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val gson = Gson()

    // ==================== 认证相关 ====================

    /**
     * 用户认证
     */
    suspend fun authenticate(
        context: Context,
        serverUrl: String,
        deviceId: String,
        username: String,
        password: String
    ): AuthenticationResultDto = withContext(Dispatchers.IO) {
        val body = mapOf("Username" to username, "Pw" to password)
        val result = httpAsJsonObject(
            context = context,
            serverUrl = serverUrl,
            apiKey = "",
            deviceId = deviceId,
            url = "/Users/authenticatebyname",
            method = "POST",
            body = body
        )
        gson.fromJson(result, AuthenticationResultDto::class.java)
    }

    /**
     * 验证 API Key 有效性
     */
    suspend fun testKey(
        context: Context,
        serverUrl: String,
        userId: String,
        apiKey: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$serverUrl/Users/$userId?X-Emby-Token=$apiKey"
            val request = Request.Builder().url(url).get().build()
            HttpClient.getClient(context).newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 媒体库相关 ====================

    /**
     * 获取视图列表
     */
    suspend fun getViews(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String
    ): List<BaseItemDto> {
        return httpAsBaseItemDtoList(
            context, serverUrl, apiKey, deviceId,
            "/Users/$userId/Views?X-Emby-Token=$apiKey"
        )
    }

    /**
     * 获取媒体库列表
     */
    suspend fun getLibraryList(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String,
        parentId: String,
        type: String
    ): List<BaseItemDto> {
        val url = "/Users/$userId/Items?IncludeItemTypes=$type" +
                "&Fields=BasicSyncInfo,PrimaryImageAspectRatio,ProductionYear,Status,EndDate" +
                "&StartIndex=0&SortBy=SortName&SortOrder=Ascending&ParentId=$parentId" +
                "&EnableImageTypes=Primary,Backdrop,Thumb&ImageTypeLimit=1&Recursive=true&Limit=2000" +
                "&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(context, serverUrl, apiKey, deviceId, url)
    }

    /**
     * 获取继续观看列表
     */
    suspend fun getResumeItems(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String,
        seriesId: String = ""
    ): List<BaseItemDto> {
        val limit = if (seriesId.isEmpty()) 15 else 1
        val url = "/Users/$userId/Items/Resume?Limit=$limit&MediaTypes=Video&ParentId=$seriesId" +
                "&Fields=PrimaryImageAspectRatio,ProductionYear&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(context, serverUrl, apiKey, deviceId, url)
    }

    /**
     * 获取视图下的最新项目
     */
    suspend fun getLatestItemsByViews(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String,
        parentId: String
    ): List<BaseItemDto> {
        val url = "/Users/$userId/Items/Latest?Limit=20&ParentId=$parentId" +
                "&Fields=PrimaryImageAspectRatio,ProductionYear&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoListDirect(context, serverUrl, apiKey, deviceId, url)
    }

    /**
     * 获取所有视图的最新项目
     */
    suspend fun getLatestItems(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String
    ): List<BaseItemDto> {
        val views = getViews(context, serverUrl, apiKey, deviceId, userId)
        return views.map { view ->
            val id = view.id ?: ""
            if (id.isNotEmpty()) {
                val items = getLatestItemsByViews(context, serverUrl, apiKey, deviceId, userId, id)
                view.copy(latestItems = items)
            } else {
                view
            }
        }
    }

    // ==================== 详情与剧集 ====================

    /**
     * 获取媒体详情
     */
    suspend fun getMediaInfo(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String,
        mediaId: String
    ): BaseItemDto {
        return httpAsBaseItemDto(
            context, serverUrl, apiKey, deviceId,
            "/Users/$userId/Items/$mediaId?fields=ShareLevel&ExcludeFields=VideoChapters,VideoMediaSources,MediaStreams&X-Emby-Token=$apiKey"
        )
    }

    /**
     * 获取剧集列表
     */
    suspend fun getSeriesList(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String,
        parentId: String
    ): List<BaseItemDto> {
        val url = "/Users/$userId/Items?UserId=$userId" +
                "&Fields=BasicSyncInfo%2CCanDelete%2CPrimaryImageAspectRatio%2COverview%2CPremiereDate%2CProductionYear%2CRunTimeTicks%2CSpecialEpisodeNumbers" +
                "&Recursive=true&IsFolder=false&ParentId=$parentId&Limit=1000&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(context, serverUrl, apiKey, deviceId, url)
    }

    /**
     * 获取下一集
     */
    suspend fun getShowsNextUp(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String,
        seriesId: String
    ): List<BaseItemDto> {
        val url = "/Shows/NextUp?SeriesId=$seriesId&UserId=$userId" +
                "&EnableTotalRecordCount=false&ExcludeLocationTypes=Virtual" +
                "&Fields=ProductionYear,PremiereDate,Container,PrimaryImageAspectRatio&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(context, serverUrl, apiKey, deviceId, url)
    }

    /**
     * 获取季列表
     */
    suspend fun getSeasonList(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String,
        seriesId: String
    ): List<BaseItemDto> {
        val url = "/Shows/$seriesId/Seasons?UserId=$userId" +
                "&Fields=PrimaryImageAspectRatio&Limit=100&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(context, serverUrl, apiKey, deviceId, url)
    }

    // ==================== 播放相关 ====================

    /**
     * 获取播放信息
     */
    suspend fun getPlaybackInfo(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String,
        mediaId: String,
        startTimeTicks: Long,
        selectedAudioIndex: Int? = null,
        selectedSubtitleIndex: Int? = null,
        disableHevc: Boolean = false
    ): MediaDto = withContext(Dispatchers.IO) {
        try {
            val body = buildPlaybackInfoBody(context, disableHevc)

            val url = "/Items/$mediaId/PlaybackInfo?UserId=$userId" +
                    "&StartTimeTicks=$startTimeTicks" +
                    "&IsPlayback=true" +
                    "&AutoOpenLiveStream=true" +
                    "&MaxStreamingBitrate=200000000" +
                    "&X-Emby-Token=$apiKey" +
                    "&X-Emby-Language=zh-cn" +
                    "&reqformat=json" +
                    (selectedAudioIndex?.let { "&AudioStreamIndex=$it" } ?: "") +
                    (selectedSubtitleIndex?.let { "&SubtitleStreamIndex=$it" } ?: "")

            val result = httpAsJsonObject(context, serverUrl, apiKey, deviceId, url, "POST", body)
            gson.fromJson(result, MediaDto::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get playback info: ${e.message}")
            MediaDto()
        }
    }

    /**
     * 上报播放进度
     */
    suspend fun reportPlaybackProgress(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        body: Any
    ) {
        try {
            httpAsJsonObject(
                context, serverUrl, apiKey, deviceId,
                "/Sessions/Playing/Progress?X-Emby-Token=$apiKey", "POST", body
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 上报开始播放
     */
    suspend fun playing(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        body: Any
    ) {
        try {
            httpAsJsonObject(
                context, serverUrl, apiKey, deviceId,
                "/Sessions/Playing?reqformat=json&X-Emby-Token=$apiKey", "POST", body
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 上报停止播放
     */
    suspend fun stopped(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        body: Any
    ) {
        try {
            httpAsJsonObject(
                context, serverUrl, apiKey, deviceId,
                "/Sessions/Playing/Stopped?reqformat=json&X-Emby-Token=$apiKey", "POST", body
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 停止活动编码
     */
    suspend fun stopActiveEncodings(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        playSessionId: String?
    ) = withContext(Dispatchers.IO) {
        try {
            httpAsJsonObject(
                context, serverUrl, apiKey, deviceId,
                "/Videos/ActiveEncodings/Delete?PlaySessionId=$playSessionId&X-Emby-Token=$apiKey",
                "POST", null
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取播放会话列表
     */
    suspend fun getPlayingSessions(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String
    ): List<SessionDto> {
        return httpAsSessionDtoList(
            context, serverUrl, apiKey, deviceId,
            "/Sessions?X-Emby-Token=$apiKey"
        )
    }

    // ==================== 收藏相关 ====================

    /**
     * 添加收藏
     */
    suspend fun addToFavorites(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String,
        itemId: String
    ): Boolean {
        return try {
            httpAsJsonObject(
                context, serverUrl, apiKey, deviceId,
                "/Users/$userId/FavoriteItems/$itemId?X-Emby-Token=$apiKey", "POST", null
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 取消收藏
     */
    suspend fun removeFromFavorites(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String,
        itemId: String
    ): Boolean {
        return try {
            httpAsJsonObject(
                context, serverUrl, apiKey, deviceId,
                "/Users/$userId/FavoriteItems/$itemId/Delete?X-Emby-Token=$apiKey", "POST", null
            )
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 获取收藏列表
     */
    suspend fun getFavoriteItems(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        userId: String
    ): List<BaseItemDto> {
        val url = "/Users/$userId/Items?SortBy=SeriesSortName,ParentIndexNumber,IndexNumber,SortName" +
                "&SortOrder=Ascending&Filters=IsFavorite" +
                "&Fields=BasicSyncInfo,CanDelete,CanDownload,PrimaryImageAspectRatio,ProductionYear" +
                "&ImageTypeLimit=1&EnableImageTypes=Primary,Backdrop,Thumb&Recursive=true&Limit=20" +
                "&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(context, serverUrl, apiKey, deviceId, url)
    }

    // ==================== 版本更新 ====================

    /**
     * 获取最新版本信息
     */
    suspend fun getNewVersion(context: Context): JsonObject = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/shareven/emby_tv/releases/latest"
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .get()
            .build()

        try {
            HttpClient.getClient(context).newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext JsonObject()
                val body = response.body ?: return@withContext JsonObject()
                val reader = JsonReader(body.charStream())
                gson.fromJson<JsonObject>(reader, JsonObject::class.java) ?: JsonObject()
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败: ${e.message}")
            JsonObject()
        }
    }

    // ==================== HTTP 辅助方法 ====================

    private suspend fun <T> httpStream(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        url: String,
        method: String = "GET",
        body: Any? = null,
        parser: (JsonReader) -> T
    ): T = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val connector = if (url.contains("?")) "&" else "?"
        val params = "${connector}deviceId=$deviceId&X-Emby-Client=$CLIENT" +
                "&X-Emby-Client-Version=$CLIENT_VERSION&X-Emby-Device-Name=$DEVICE_NAME" +
                "&X-Emby-Device-Id=$deviceId"
        val fullUrl = "$serverUrl/emby$url$params"

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .addHeader("Accept", "application/json")

        if (method == "POST") {
            val jsonBody = if (body != null) gson.toJson(body) else "{}"
            requestBuilder.post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
        } else {
            requestBuilder.get()
        }

        HttpClient.getClient(context).newCall(requestBuilder.build()).execute().use { response ->
            val responseTime = System.currentTimeMillis()
            val networkDuration = responseTime - startTime

            if (!response.isSuccessful) {
                throw Exception("HTTP Error: ${response.code}")
            }

            val bodySource = response.body ?: throw Exception("Empty response body")
            val result = parser(JsonReader(bodySource.charStream()))

            val endTime = System.currentTimeMillis()
            Log.i(TAG, """
                🏁 请求完成: $url
                ├─ 网络协议: ${response.protocol}
                ├─ RTT: ${networkDuration}ms
                ├─ JSON解析: ${endTime - responseTime}ms
                └─ 总耗时: ${endTime - startTime}ms
            """.trimIndent())

            result
        }
    }

    private suspend fun httpAsJsonObject(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        url: String,
        method: String = "GET",
        body: Any? = null
    ): JsonObject {
        return httpStream(context, serverUrl, apiKey, deviceId, url, method, body) { reader ->
            gson.fromJson(reader, JsonObject::class.java) ?: JsonObject()
        }
    }

    private suspend fun httpAsBaseItemDto(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        url: String,
        method: String = "GET",
        body: Any? = null
    ): BaseItemDto {
        return httpStream(context, serverUrl, apiKey, deviceId, url, method, body) { reader ->
            gson.fromJson(reader, BaseItemDto::class.java) ?: BaseItemDto()
        }
    }

    private suspend fun httpAsBaseItemDtoList(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        url: String
    ): List<BaseItemDto> {
        return httpStream(context, serverUrl, apiKey, deviceId, url) { reader ->
            val type = object : TypeToken<EmbyResponseDto<BaseItemDto>>() {}.type
            val response = gson.fromJson<EmbyResponseDto<BaseItemDto>>(reader, type)
            response?.items ?: emptyList()
        }
    }

    private suspend fun httpAsBaseItemDtoListDirect(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        url: String
    ): List<BaseItemDto> {
        return httpStream(context, serverUrl, apiKey, deviceId, url) { reader ->
            val type = object : TypeToken<List<BaseItemDto>>() {}.type
            gson.fromJson<List<BaseItemDto>>(reader, type) ?: emptyList()
        }
    }

    private suspend fun httpAsSessionDtoList(
        context: Context,
        serverUrl: String,
        apiKey: String,
        deviceId: String,
        url: String
    ): List<SessionDto> {
        return httpStream(context, serverUrl, apiKey, deviceId, url) { reader ->
            val type = object : TypeToken<List<SessionDto>>() {}.type
            gson.fromJson<List<SessionDto>>(reader, type) ?: emptyList()
        }
    }

    // ==================== 设备能力探测 ====================

    /**
     * 构建播放信息请求体
     */
    private fun buildPlaybackInfoBody(
        context: Context,
        disableHevc: Boolean = false,
        maxStreamingBitrate: Int = 200_000_000
    ): JsonObject {
        try {
            val capabilities = getDeviceCapabilities(context)

            val videoCodecs = capabilities.videoCodecs.toMutableList()
            val audioCodecs = capabilities.audioCodecs.toMutableList()
            val videoProfiles = capabilities.videoProfiles

            val hardwareSupportsHevc = videoCodecs.any { codec ->
                listOf("hevc", "h265", "hevc10").any { it.equals(codec, ignoreCase = true) }
            }

            var actualDisableHevc = disableHevc
            if (!hardwareSupportsHevc) {
                actualDisableHevc = true
            }

            val rawLevel = findMaxLevel(videoProfiles, "h264", 51)
            val finalLevel = if (actualDisableHevc) 51 else if (rawLevel > 62) 62 else rawLevel

            val supportedVideo = videoCodecs.joinToString(",")
            val supportedAudio = audioCodecs.joinToString(",")

            val deviceProfile = JsonObject().apply {
                addProperty("MaxStaticBitrate", maxStreamingBitrate)
                addProperty("MaxStreamingBitrate", maxStreamingBitrate)
                addProperty("MusicStreamingTranscodingBitrate", 192000)
                addProperty("MaxCanvasWidth", capabilities.maxCanvasWidth)
                addProperty("MaxCanvasHeight", capabilities.maxCanvasHeight)

                add("DirectPlayProfiles", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("Type", "Video")
                        addProperty("VideoCodec", if (actualDisableHevc) "h264" else supportedVideo)
                        addProperty("Container", "mp4,m4v,mkv,mov")
                        addProperty("AudioCodec", supportedAudio)
                    })
                    add(JsonObject().apply {
                        addProperty("Type", "Audio")
                        addProperty("Container", null as String?)
                        addProperty("AudioCodec", null as String?)
                    })
                })

                add("TranscodingProfiles", JsonArray().apply {
                    add(createTranscodingProfile("aac", "Audio", "aac", "hls", "8"))
                    add(createTranscodingProfile("aac", "Audio", "aac", "http", "8"))
                    add(createTranscodingProfile("mp3", "Audio", "mp3", "http", "8"))
                    add(createTranscodingProfile("opus", "Audio", "opus", "http", "8"))
                    add(createTranscodingProfile("wav", "Audio", "wav", "http", "8"))
                    add(createTranscodingProfile("opus", "Audio", "opus", "http", "8", "Static"))
                    add(createTranscodingProfile("mp3", "Audio", "mp3", "http", "8", "Static"))
                    add(createTranscodingProfile("aac", "Audio", "aac", "http", "8", "Static"))
                    add(createTranscodingProfile("wav", "Audio", "wav", "http", "8", "Static"))

                    add(JsonObject().apply {
                        addProperty("Container", "mkv")
                        addProperty("Type", "Video")
                        addProperty("AudioCodec", supportedAudio)
                        addProperty("VideoCodec", if (actualDisableHevc) "h264" else supportedVideo)
                        addProperty("Context", "Static")
                        addProperty("MaxAudioChannels", "8")
                        addProperty("CopyTimestamps", true)
                    })

                    add(JsonObject().apply {
                        addProperty("Container", "ts")
                        addProperty("Type", "Video")
                        addProperty("AudioCodec", supportedAudio)
                        addProperty("VideoCodec", if (actualDisableHevc) "h264" else supportedVideo)
                        addProperty("Context", "Streaming")
                        addProperty("Protocol", "hls")
                        addProperty("MaxAudioChannels", "8")
                        addProperty("MinSegments", "1")
                        addProperty("BreakOnNonKeyFrames", false)
                        addProperty("ManifestSubtitles", "vtt")
                    })

                    add(JsonObject().apply {
                        addProperty("Container", "webm")
                        addProperty("Type", "Video")
                        addProperty("AudioCodec", "vorbis")
                        addProperty("VideoCodec", "vpx")
                        addProperty("Context", "Streaming")
                        addProperty("Protocol", "http")
                        addProperty("MaxAudioChannels", "8")
                    })

                    add(JsonObject().apply {
                        addProperty("Container", "mp4")
                        addProperty("Type", "Video")
                        addProperty("AudioCodec", supportedAudio)
                        addProperty("VideoCodec", "h264")
                        addProperty("Context", "Static")
                        addProperty("Protocol", "http")
                    })
                })

                add("ContainerProfiles", JsonArray())

                add("CodecProfiles", JsonArray().apply {
                    add(createCodecProfileAudio("aac"))
                    add(createCodecProfileAudio("flac"))
                    add(createCodecProfileAudio("vorbis"))
                    add(createCodecProfileAudio(null))

                    add(JsonObject().apply {
                        addProperty("Type", "Video")
                        addProperty("Codec", "h264")
                        add("Conditions", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("Condition", "EqualsAny")
                                addProperty("Property", "VideoProfile")
                                addProperty("Value", "high|main|baseline|constrained baseline|high 10")
                                addProperty("IsRequired", false)
                            })
                            add(JsonObject().apply {
                                addProperty("Condition", "LessThanEqual")
                                addProperty("Property", "VideoLevel")
                                addProperty("Value", finalLevel)
                                addProperty("IsRequired", false)
                            })
                        })
                    })

                    if (!actualDisableHevc && (videoCodecs.contains("hevc") || videoCodecs.contains("h265"))) {
                        add(JsonObject().apply {
                            addProperty("Type", "Video")
                            addProperty("Codec", "hevc")
                            add("Conditions", JsonArray().apply {
                                add(JsonObject().apply {
                                    addProperty("Condition", "EqualsAny")
                                    addProperty("Property", "VideoCodecTag")
                                    addProperty("Value", "hvc1|hev1|hevc|hdmv")
                                    addProperty("IsRequired", false)
                                })
                            })
                        })
                    }
                })

                add("SubtitleProfiles", JsonArray().apply {
                    add(createSubtitleProfile("vtt", "Hls"))
                    add(createSubtitleProfile("eia_608", "VideoSideData", "hls"))
                    add(createSubtitleProfile("eia_708", "VideoSideData", "hls"))
                    add(createSubtitleProfile("vtt", "External"))
                    add(createSubtitleProfile("ass", "External"))
                    add(createSubtitleProfile("ssa", "External"))
                    add(createSubtitleProfile("srt", "External"))
                    add(createSubtitleProfile("subrip", "Embed"))
                })

                add("ResponseProfiles", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("Type", "Video")
                        addProperty("Container", "m4v")
                        addProperty("MimeType", "video/mp4")
                    })
                })
            }

            return JsonObject().apply {
                add("DeviceProfile", deviceProfile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return JsonObject()
        }
    }

    private fun createTranscodingProfile(
        container: String,
        type: String,
        audioCodec: String,
        protocol: String,
        maxAudioChannels: String,
        context: String = "Streaming"
    ): JsonObject {
        return JsonObject().apply {
            addProperty("Container", container)
            addProperty("Type", type)
            addProperty("AudioCodec", audioCodec)
            addProperty("Context", context)
            addProperty("Protocol", protocol)
            addProperty("MaxAudioChannels", maxAudioChannels)
            if (context == "Streaming") {
                addProperty("MinSegments", "1")
                addProperty("BreakOnNonKeyFrames", false)
                if (protocol == "hls") {
                    addProperty("ManifestSubtitles", "vtt")
                }
            }
        }
    }

    private fun createCodecProfileAudio(codec: String?): JsonObject {
        return JsonObject().apply {
            addProperty("Type", "VideoAudio")
            if (codec != null) {
                addProperty("Codec", codec)
            }
            add("Conditions", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("Condition", "Equals")
                    addProperty("Property", "IsSecondaryAudio")
                    addProperty("Value", "false")
                    addProperty("IsRequired", "false")
                })
            })
        }
    }

    private fun createSubtitleProfile(
        format: String,
        method: String,
        protocol: String? = null
    ): JsonObject {
        return JsonObject().apply {
            addProperty("Format", format)
            addProperty("Method", method)
            if (protocol != null) {
                addProperty("Protocol", protocol)
            }
        }
    }

    private fun findMaxLevel(profiles: List<VideoProfile>, codec: String, defaultValue: Int): Int {
        return try {
            val profile = profiles.find { it.codec.equals(codec, ignoreCase = true) }
            profile?.maxLevel ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    private fun getDeviceCapabilities(context: Context): DeviceCapabilities {
        val videoCodecs = mutableSetOf<String>()
        val audioCodecs = mutableSetOf<String>()
        val videoProfiles = mutableListOf<VideoProfile>()

        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue

            for (type in info.supportedTypes) {
                try {
                    val caps = info.getCapabilitiesForType(type)

                    when {
                        type.equals("video/avc", ignoreCase = true) -> {
                            videoCodecs.add("h264")
                            val maxLevel = caps.profileLevels?.maxOfOrNull { mapAvcLevel(it.level) } ?: 41
                            videoProfiles.add(VideoProfile("h264", maxLevel))
                        }
                        type.equals("video/hevc", ignoreCase = true) -> {
                            videoCodecs.add("hevc")
                            videoCodecs.add("h265")
                        }
                        type.equals("video/av01", ignoreCase = true) -> videoCodecs.add("av1")
                        type.equals("video/x-vnd.on2.vp9", ignoreCase = true) -> videoCodecs.add("vp9")
                        type.equals("audio/mp4a-latm", ignoreCase = true) -> audioCodecs.add("aac")
                        type.equals("audio/ac3", ignoreCase = true) -> audioCodecs.add("ac3")
                        type.equals("audio/eac3", ignoreCase = true) -> audioCodecs.add("eac3")
                        type.equals("audio/mpeg", ignoreCase = true) -> audioCodecs.add("mp3")
                        type.equals("audio/flac", ignoreCase = true) -> audioCodecs.add("flac")
                        type.equals("audio/opus", ignoreCase = true) -> audioCodecs.add("opus")
                        type.equals("audio/vnd.dts", true) -> audioCodecs.add("dts")
                        type.equals("audio/vnd.dts.hd", true) -> {
                            audioCodecs.add("dts")
                            audioCodecs.add("dtshd")
                        }
                        type.equals("audio/true-hd", true) -> audioCodecs.add("truehd")
                        type.equals("audio/eac3-joc", true) -> audioCodecs.add("eac3")
                        type.equals("audio/ac4", true) -> audioCodecs.add("ac4")
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (screenWidth, screenHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val rect = metrics.bounds
            rect.width() to rect.height()
        } else {
            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            val size = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(size)
            size.x to size.y
        }

        val canHandle4K = videoCodecs.contains("hevc") || videoCodecs.contains("av1")
        val maxDecodeWidth = if (canHandle4K) maxOf(screenWidth, 3840) else screenWidth
        val maxDecodeHeight = if (canHandle4K) maxOf(screenHeight, 2160) else screenHeight

        return DeviceCapabilities(
            videoCodecs = videoCodecs.toList(),
            audioCodecs = audioCodecs.toList(),
            videoProfiles = videoProfiles,
            maxCanvasWidth = maxDecodeWidth,
            maxCanvasHeight = maxDecodeHeight
        )
    }

    private fun mapAvcLevel(androidLevel: Int): Int {
        return when (androidLevel) {
            MediaCodecInfo.CodecProfileLevel.AVCLevel1 -> 10
            MediaCodecInfo.CodecProfileLevel.AVCLevel11 -> 11
            MediaCodecInfo.CodecProfileLevel.AVCLevel12 -> 12
            MediaCodecInfo.CodecProfileLevel.AVCLevel13 -> 13
            MediaCodecInfo.CodecProfileLevel.AVCLevel2 -> 20
            MediaCodecInfo.CodecProfileLevel.AVCLevel21 -> 21
            MediaCodecInfo.CodecProfileLevel.AVCLevel22 -> 22
            MediaCodecInfo.CodecProfileLevel.AVCLevel3 -> 30
            MediaCodecInfo.CodecProfileLevel.AVCLevel31 -> 31
            MediaCodecInfo.CodecProfileLevel.AVCLevel32 -> 32
            MediaCodecInfo.CodecProfileLevel.AVCLevel4 -> 40
            MediaCodecInfo.CodecProfileLevel.AVCLevel41 -> 41
            MediaCodecInfo.CodecProfileLevel.AVCLevel42 -> 42
            MediaCodecInfo.CodecProfileLevel.AVCLevel5 -> 50
            MediaCodecInfo.CodecProfileLevel.AVCLevel51 -> 51
            MediaCodecInfo.CodecProfileLevel.AVCLevel52 -> 52
            MediaCodecInfo.CodecProfileLevel.AVCLevel6 -> 60
            MediaCodecInfo.CodecProfileLevel.AVCLevel61 -> 61
            MediaCodecInfo.CodecProfileLevel.AVCLevel62 -> 62
            else -> 41
        }
    }
}

// ==================== 数据类 ====================

data class VideoProfile(
    val codec: String,
    val maxLevel: Int,
    val profiles: List<String> = emptyList()
)

data class DeviceCapabilities(
    val videoCodecs: List<String>,
    val audioCodecs: List<String>,
    val videoProfiles: List<VideoProfile>,
    val maxCanvasWidth: Int = 3840,
    val maxCanvasHeight: Int = 2160
)
