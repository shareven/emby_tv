package com.xxxx.emby_tv

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.view.WindowManager
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.net.cronet.okhttptransport.CronetInterceptor
import com.xxxx.emby_tv.model.AuthenticationResultDto
import com.xxxx.emby_tv.model.BaseItemDto
import com.xxxx.emby_tv.model.BaseExternalUrlDto
import com.xxxx.emby_tv.model.EmbyResponseDto
import com.xxxx.emby_tv.model.MediaDto
import com.xxxx.emby_tv.model.SessionDto
import com.xxxx.emby_tv.model.UserDto
import com.xxxx.emby_tv.model.UserDataDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.chromium.net.CronetEngine
import java.util.concurrent.TimeUnit
import android.graphics.Point
import android.media.MediaCodecInfo

/**
 * 2025 全局 Cronet + OkHttp 配置
 */
object NetworkClient {
    private var client: OkHttpClient? = null

    fun getClient(context: Context): OkHttpClient {
        if (client == null) {
            // 1. 初始化 Cronet 引擎
            val cronetEngine = CronetEngine.Builder(context)
                .enableQuic(true)
                .enableHttp2(true)
                .enableBrotli(true)
                .setStoragePath(context.cacheDir.absolutePath) // 启用缓存，允许持久化 QUIC 状态
                .enableHttpCache(CronetEngine.Builder.HTTP_CACHE_DISK, 10 * 1024 * 1024) // 10MB 缓存
                .build()

            // 2. 构建 OkHttpClient 并植入 Cronet 拦截器
            client = OkHttpClient.Builder()
                .addInterceptor(CronetInterceptor.newBuilder(cronetEngine).build()) // 核心替换
                .dispatcher(Dispatcher().apply {
                    maxRequests = 64
                    maxRequestsPerHost = 20
                })
                .connectionPool(ConnectionPool(10, 2, TimeUnit.MINUTES))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
        return client!!
    }
}

val gson = Gson()


class EmbyService(
    private val context: Context, // 需要 Context 初始化 Cronet
    private val serverUrl: String,
    private val apiKey: String,
    private val deviceId: String
) {
    private val TAG = "EmbyService"
    private val client = NetworkClient.getClient(context)

    companion object {
        private const val CLIENT = "shareven/emby_tv"
        private val CLIENT_VERSION: String = BuildConfig.VERSION_NAME
        private const val DEVICE_NAME = "Android TV"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun mapAvcLevel(bitmask: Int): Int {
            return when {
                bitmask >= 0x10000 -> 52 // Level 5.2
                bitmask >= 0x8000 -> 51 // Level 5.1
                bitmask >= 0x4000 -> 50 // Level 5.0
                bitmask >= 0x2000 -> 42 // Level 4.2
                bitmask >= 0x1000 -> 41 // Level 4.1
                bitmask >= 0x800 -> 40 // Level 4.0
                bitmask >= 0x400 -> 32 // Level 4.2
                bitmask >= 0x200 -> 31 // Level 3.1
                bitmask >= 0x100 -> 30 // Level 3.0
                else -> 41 // Default safe
            }
        }

        private fun mapHevcLevel(bitmask: Int): Int {
            return when {
                bitmask >= 0x100000 -> 180 // Level 6.0
                bitmask >= 0x40000 -> 156 // Level 5.2 (approx)
                bitmask >= 0x10000 -> 153 // Level 5.1
                bitmask >= 0x4000 -> 150 // Level 5.0
                bitmask >= 0x1000 -> 123 // Level 4.1
                bitmask >= 0x400 -> 120 // Level 4.0
                bitmask >= 0x100 -> 93 // Level 3.1
                bitmask >= 0x40 -> 90 // Level 3.0
                else -> 120 // Default safe 4.0
            }
        }

        /**
         * 获取最新版本信息 - 对应 Flutter 的 getNewVersion
         * 使用 suspend 关键字支持协程异步调用
         */
        suspend fun getNewVersion(context: Context): JsonObject = withContext(Dispatchers.IO) {
            val client = NetworkClient.getClient(context)
            val url = "https://api.github.com/repos/shareven/emby_tv/releases/latest"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/json")
                .get()
                .build()

            try {
                // 使用 .use 确保 ResponseBody 和 Stream 正确关闭
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext JsonObject()

                    val body = response.body ?: return@withContext JsonObject()

                    // 同样使用 charStream() 配合 JsonReader，保持流式解析的一致性
                    val reader = JsonReader(body.charStream())
                    gson.fromJson<JsonObject>(reader, JsonObject::class.java) ?: JsonObject()
                }
            } catch (e: Exception) {
                Log.e("EmbyService", "检查更新失败: ${e.message}")
                JsonObject()
            }
        }

    }

    private suspend fun <T> httpStream(
        url: String,
        method: String = "GET",
        body: Any? = null,
        parser: (JsonReader) -> T
    ): T = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val connector = if (url.contains("?")) "&" else "?"
        val params =
            "${connector}deviceId=$deviceId&X-Emby-Client=$CLIENT&X-Emby-Client-Version=$CLIENT_VERSION&X-Emby-Device-Name=$DEVICE_NAME&X-Emby-Device-Id=$deviceId"
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

        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseTime = System.currentTimeMillis()
            val networkDuration = responseTime - startTime

            if (!response.isSuccessful) {
                throw Exception("HTTP Error: ${response.code}")
            }

            val bodySource = response.body ?: throw Exception("Empty response body")
            val result = parser(JsonReader(bodySource.charStream()))

            val endTime = System.currentTimeMillis()
            Log.i(
                TAG, """
                🏁 Cronet 请求完成: $url
                ├─ 网络协议: ${response.protocol} (可能是 h3/h2/http1.1)
                ├─ RTT: ${networkDuration}ms
                ├─ JSON解析: ${endTime - responseTime}ms
                └─ 总耗时: ${endTime - startTime}ms
            """.trimIndent()
            )

            result
        }
    }


    /**
     * 辅助方法：将结果解析为单个 JsonObject
     */
    private suspend fun httpAsJsonObject(
        url: String,
        method: String = "GET",
        body: Any? = null
    ): JsonObject {
        return httpStream(url, method, body) { reader ->
            gson.fromJson(reader, JsonObject::class.java) ?: JsonObject()
        }
    }

    /**
     * 辅助方法：将结果解析为单个 BaseItemDto
     */
    private suspend fun httpAsBaseItemDto(
        url: String,
        method: String = "GET",
        body: Any? = null
    ): BaseItemDto {
        return httpStream(url, method, body) { reader ->
            gson.fromJson(reader, BaseItemDto::class.java) ?: BaseItemDto()
        }
    }

    /**
     * 辅助方法：将结果解析为 BaseItemDto 列表
     */
    private suspend fun httpAsBaseItemDtoList(
        url: String
    ): List<BaseItemDto> {
        return httpStream(url) { reader ->
            val type = object : TypeToken<EmbyResponseDto<BaseItemDto>>() {}.type
            val response = gson.fromJson<EmbyResponseDto<BaseItemDto>>(reader, type)
            response?.items ?: emptyList()
        }
    }

    /**
     * 辅助方法：直接解析为 BaseItemDto 列表（API直接返回数组格式）
     */
    private suspend fun httpAsBaseItemDtoListDirect(
        url: String
    ): List<BaseItemDto> {
        return httpStream(url) { reader ->
            val type = object : TypeToken<List<BaseItemDto>>() {}.type
            gson.fromJson<List<BaseItemDto>>(reader, type) ?: emptyList()
        }
    }

    /**
     * 辅助方法：将结果解析为 SessionDto 列表
     */
    private suspend fun httpAsSessionDtoList(
        url: String
    ): List<SessionDto> {
        return httpStream(url) { reader ->
            val type = object : TypeToken<List<SessionDto>>() {}.type
            gson.fromJson<List<SessionDto>>(reader, type) ?: emptyList()
        }
    }

    // --- 认证相关 ---

    suspend fun authenticate(username: String, password: String): AuthenticationResultDto {
        val body = mapOf("Username" to username, "Pw" to password)
        val response = httpAsJsonObject("/Users/authenticatebyname", "POST", body)
        return gson.fromJson(response, AuthenticationResultDto::class.java)
    }

    suspend fun testKey(savedUserId: String, savedApiKey: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    "$serverUrl/Users/$savedUserId?X-Emby-Token=$savedApiKey"
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
        }

    // --- 媒体库内容加载 ---
    suspend fun getViews(userId: String): List<BaseItemDto> {
        return httpAsBaseItemDtoList("/Users/$userId/Views?X-Emby-Token=$apiKey")
    }

    suspend fun getLibraryList(userId: String, parentId: String, type: String): List<BaseItemDto> {
        val url =
            "/Users/$userId/Items?IncludeItemTypes=$type&Fields=BasicSyncInfo,PrimaryImageAspectRatio,ProductionYear,Status,EndDate&StartIndex=0&SortBy=SortName&SortOrder=Ascending&ParentId=$parentId&EnableImageTypes=Primary,Backdrop,Thumb&ImageTypeLimit=1&Recursive=true&Limit=2000&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(url)
    }

    suspend fun getResumeItems(userId: String, seriesId: String = ""): List<BaseItemDto> {
        val limit = if (seriesId.isEmpty()) 15 else 1
        val url =
            "/Users/$userId/Items/Resume?Limit=$limit&MediaTypes=Video&ParentId=$seriesId&Fields=PrimaryImageAspectRatio,ProductionYear&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(url)
    }


    suspend fun getLatestItemsByViews(userId: String, parentId: String): List<BaseItemDto> {
        val url =
            "/Users/$userId/Items/Latest?Limit=20&ParentId=$parentId&Fields=PrimaryImageAspectRatio,ProductionYear&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoListDirect(url)
    }

    suspend fun getLatestItems(userId: String): List<BaseItemDto> {
        val views = getViews(userId)
        // 为每个视图获取最新的媒体项
        return views.map { view ->
            val id = view.id ?: ""
            if (id.isNotEmpty()) {
                val items = getLatestItemsByViews(userId, id)
                // 创建一个新的BaseItemDto实例，包含最新的项目
                view.copy(latestItems = items)
            } else {
                view
            }
        }
    }

    // --- 详情与剧集 ---

    suspend fun getMediaInfo(userId: String, mediaId: String): BaseItemDto {
        return httpAsBaseItemDto("/Users/$userId/Items/$mediaId?fields=ShareLevel&ExcludeFields=VideoChapters,VideoMediaSources,MediaStreams&X-Emby-Token=$apiKey")

    }

    suspend fun getSeriesList(userId: String, parentId: String): List<BaseItemDto> {
        val url =
            "/Users/$userId/Items?UserId=$userId&Fields=BasicSyncInfo%2CCanDelete%2CPrimaryImageAspectRatio%2COverview%2CPremiereDate%2CProductionYear%2CRunTimeTicks%2CSpecialEpisodeNumbers&Recursive=true&IsFolder=false&ParentId=$parentId&Limit=1000&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(url)
    }

    suspend fun getShowsNextUp(userId: String, seriesId: String): List<BaseItemDto> {
        val url =
            "/Shows/NextUp?SeriesId=$seriesId&UserId=$userId&EnableTotalRecordCount=false&ExcludeLocationTypes=Virtual&Fields=ProductionYear,PremiereDate,Container,PrimaryImageAspectRatio&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(url)
    }

    suspend fun getSeasonList(userId: String, parentId: String): List<BaseItemDto> {
        val url =
            "/Shows/$parentId/Seasons?UserId=$userId&Fields=PrimaryImageAspectRatio&Limit=100&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(url)
    }


    suspend fun getPlayingSessions(): List<SessionDto> {
        val url = "/Sessions?X-Emby-Token=$apiKey"
        return httpAsSessionDtoList(url)
    }

    // --- 播放相关 ---
    suspend fun getPlaybackInfo(
        userId: String,
        mediaId: String,
        startTimeTicks: Long,
        selectedAudioIndex: Int?=null,
        selectedSubtitleIndex: Int?=null,
        disableHevc: Boolean = false
    ): MediaDto = withContext(Dispatchers.IO) {
        try {

            // 非常重要：告诉服务器，设置的能力，可以处理哪些媒体数据
            val body = buildPlaybackInfoBody(disableHevc)

            // 构建 URL
            val url = "/Items/$mediaId/PlaybackInfo?UserId=$userId" +
                    "&StartTimeTicks=$startTimeTicks" +
                    "&IsPlayback=true" +
                    "&AutoOpenLiveStream=true" +
                    "&MaxStreamingBitrate=200000000" + // 200M 码率支持
                    "&X-Emby-Token=$apiKey" +
                    "&X-Emby-Language=zh-cn" +
                    "&reqformat=json" +
                     (selectedAudioIndex?.let { "&AudioStreamIndex=$it" } ?: "") +
                    (selectedSubtitleIndex?.let { "&SubtitleStreamIndex=$it" } ?: "")

           
            val result = httpAsJsonObject(url, "POST", body)

            return@withContext gson.fromJson(result, MediaDto::class.java)
        } catch (e: Exception) {
           
            withContext(Dispatchers.Main) {
               
                println("Failed to get playback info: ${e.message}")
            }
            return@withContext MediaDto() // 返回空对象，对应 Flutter 的 return {}
        }
    }

    suspend fun reportPlaybackProgress(body: Any) {
        try {
            httpAsJsonObject("/Sessions/Playing/Progress?X-Emby-Token=$apiKey", "POST", body)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    suspend fun playing(body: Any) {
        try {
            httpAsJsonObject(
                "/Sessions/Playing?reqformat=json&X-Emby-Token=$apiKey",
                "POST",
                body
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    suspend fun stopped(body: Any) {
        try {
            httpAsJsonObject(
                "/Sessions/Playing/Stopped?reqformat=json&X-Emby-Token=$apiKey",
                "POST",
                body
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    suspend fun stopActiveEncodings(playSessionId: String? = null) = withContext(Dispatchers.IO) {
        val url = "/Videos/ActiveEncodings/Delete?PlaySessionId=$playSessionId&X-Emby-Token=$apiKey"
        try {
            httpAsJsonObject(url, "POST", null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- 收藏相关 ---

    /**
     * 添加收藏
     */
    suspend fun addToFavorites(userId: String, itemId: String): Boolean {
        return try {
            val url = "/Users/$userId/FavoriteItems/$itemId?X-Emby-Token=$apiKey"
            httpAsJsonObject(url, "POST", null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    }

    /**
     * 取消收藏 - 使用官方 API 格式
     */
    suspend fun removeFromFavorites(userId: String, itemId: String): Boolean {
        return try {
            val url = "/Users/$userId/FavoriteItems/$itemId/Delete?X-Emby-Token=$apiKey"
            httpAsJsonObject(url, "POST", null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }

    }

    /**
     * 获取收藏列表 - 匹配官方实现，一个接口查询所有收藏项目
     */
    suspend fun getFavoriteItems(userId: String): List<BaseItemDto> {
        val url =
            "/Users/$userId/Items?SortBy=SeriesSortName,ParentIndexNumber,IndexNumber,SortName&SortOrder=Ascending&Filters=IsFavorite&Fields=BasicSyncInfo,CanDelete,CanDownload,PrimaryImageAspectRatio,ProductionYear&ImageTypeLimit=1&EnableImageTypes=Primary,Backdrop,Thumb&Recursive=true&Limit=20&X-Emby-Token=$apiKey"
        return httpAsBaseItemDtoList(url)
    }


    /**
     * 构建播放信息请求体 - 对应Dart端的buildPlaybackInfoBody函数
     *
     * Dart转换Kotlin说明：
     * 1. 使用suspend函数替代Dart的Future
     * 2. 使用kotlin原生集合替代Dart的Map/List
     * 3. 使用try-catch替代Dart的异常处理
     * 4. 保持相同的数据结构和逻辑处理
     */
    suspend fun buildPlaybackInfoBody(
        disableHevc: Boolean = false,
        maxStreamingBitrate: Int = 200_000_000
    ): JsonObject = withContext(Dispatchers.IO) {
        try {
            // 1. 获取硬件探测能力
            val capabilities = getDeviceCapabilities(context)
                ?: throw Exception("无法获取设备硬件信息")

            // 2. 提取数据
            val videoCodecs = capabilities.videoCodecs.toMutableList()
            val audioCodecs = capabilities.audioCodecs.toMutableList()
            val videoProfiles = capabilities.videoProfiles

            // 这样即使用户"关闭服务器转码"，但硬件不支持时，App 会强制回退到 H264
            val hardwareSupportsHevc = videoCodecs.any { codec ->
                listOf("hevc", "h265", "hevc10").any { it.equals(codec, ignoreCase = true) }
            }

            var actualDisableHevc = disableHevc
            if (!hardwareSupportsHevc) {
                actualDisableHevc = true
            }

            // --- 动态 Level 处理 ---
            val rawLevel = findMaxLevel(videoProfiles, "h264", 51)
            val finalLevel = if (actualDisableHevc) 51 else if (rawLevel > 62) 62 else rawLevel

            // 动态构建支持字符串
            val supportedVideo = videoCodecs.joinToString(",")
            val supportedAudio = audioCodecs.joinToString(",")

            // 构建返回的JsonObject
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
                    // 音频转码配置
                    add(createTranscodingProfile("aac", "Audio", "aac", "hls", "8"))
                    add(createTranscodingProfile("aac", "Audio", "aac", "http", "8"))
                    add(createTranscodingProfile("mp3", "Audio", "mp3", "http", "8"))
                    add(createTranscodingProfile("opus", "Audio", "opus", "http", "8"))
                    add(createTranscodingProfile("wav", "Audio", "wav", "http", "8"))
                    add(createTranscodingProfile("opus", "Audio", "opus", "http", "8", "Static"))
                    add(createTranscodingProfile("mp3", "Audio", "mp3", "http", "8", "Static"))
                    add(createTranscodingProfile("aac", "Audio", "aac", "http", "8", "Static"))
                    add(createTranscodingProfile("wav", "Audio", "wav", "http", "8", "Static"))

                    // 视频转码配置
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
                    // 音频编解码配置
                    add(createCodecProfileAudio("aac"))
                    add(createCodecProfileAudio("flac"))
                    add(createCodecProfileAudio("vorbis"))
                    add(createCodecProfileAudio(null))

                    // H264视频编解码配置
                    add(JsonObject().apply {
                        addProperty("Type", "Video")
                        addProperty("Codec", "h264")
                        add("Conditions", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("Condition", "EqualsAny")
                                addProperty("Property", "VideoProfile")
                                addProperty(
                                    "Value",
                                    "high|main|baseline|constrained baseline|high 10"
                                )
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

                    // HEVC动态配置
                    if (!actualDisableHevc && (videoCodecs.contains("hevc") || videoCodecs.contains(
                            "h265"
                        ))
                    ) {
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

            JsonObject().apply {
                add("DeviceProfile", deviceProfile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            JsonObject() // 返回空对象，避免崩溃
        }
    }

    /**
     * 创建转码配置对象
     */
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

    /**
     * 创建音频编解码配置
     */
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

    /**
     * 创建字幕配置对象
     */
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

    /**
     * 查找指定 codec 的最大 Level
     */
    private fun findMaxLevel(profiles: List<VideoProfile>, codec: String, defaultValue: Int): Int {
        return try {
            val profile = profiles.find {
                it.codec.equals(codec, ignoreCase = true)
            }
            profile?.maxLevel ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * 兼容 2015 (Android 6.0) - 2025 (Android 15+) 的设备能力探测
     */
    private fun getDeviceCapabilities(context: Context): DeviceCapabilities {
        val videoCodecs = mutableSetOf<String>()
        val audioCodecs = mutableSetOf<String>()
        val videoProfiles = mutableListOf<VideoProfile>()

        // 1. 获取编解码器列表 (使用 REGULAR_CODECS 过滤掉不稳定的软件插件)
        // 在 API 21+ 均可用
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (info.isEncoder) continue

            for (type in info.supportedTypes) {
                try {
                    val caps = info.getCapabilitiesForType(type)

                    when {
                        // --- H.264 / AVC ---
                        type.equals("video/avc", ignoreCase = true) -> {
                            videoCodecs.add("h264")
                            val maxLevel =
                                caps.profileLevels?.maxOfOrNull { mapAvcLevel(it.level) } ?: 41
                            videoProfiles.add(VideoProfile("h264", maxLevel))
                        }

                        // --- H.265 / HEVC ---
                        type.equals("video/hevc", ignoreCase = true) -> {
                            videoCodecs.add("hevc")
                            videoCodecs.add("h265")
                            // 探测 10bit 支持 (2017年后设备主流)
//                            val isMain10 = caps.profileLevels?.any {
//                                it.profile >= MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
//                            } ?: false
//                            videoProfiles.add(VideoProfile("hevc",if (isMain10) "Main10" else "Main"))
                        }

                        // --- AV1 (针对 2021-2025 年新设备) ---
                        type.equals("video/av01", ignoreCase = true) -> videoCodecs.add("av1")

                        // --- VP9 ---
                        type.equals(
                            "video/x-vnd.on2.vp9",
                            ignoreCase = true
                        ) -> videoCodecs.add("vp9")

                        // --- 音频兼容性 ---
                        type.equals("audio/mp4a-latm", ignoreCase = true) -> audioCodecs.add("aac")
                        type.equals("audio/ac3", ignoreCase = true) -> audioCodecs.add("ac3")
                        type.equals("audio/eac3", ignoreCase = true) -> audioCodecs.add("eac3")
                        type.equals("audio/mpeg", ignoreCase = true) -> audioCodecs.add("mp3")
                        type.equals("audio/flac", ignoreCase = true) -> audioCodecs.add("flac")
                        type.equals("audio/opus", ignoreCase = true) -> audioCodecs.add("opus")
                        type.equals("audio/vnd.dts", true) -> audioCodecs.add("dts")
                        type.equals("audio/vnd.dts.hd", true) -> {
                            audioCodecs.add("dts")
                            audioCodecs.add("dtshd") // 蓝光原盘常见
                        }

                        type.equals("audio/true-hd", true) -> audioCodecs.add("truehd") // 杜比全景声原盘核心
                        type.equals(
                            "audio/eac3-joc",
                            true
                        ) -> audioCodecs.add("eac3") // 杜比数字+ (带全景声)
                        type.equals("audio/ac4", true) -> audioCodecs.add("ac4")
                    }
                } catch (e: Exception) {
                    // 预防部分老旧系统在探测特定 Codec 时崩溃
                    continue
                }
            }
        }

        // 2. 屏幕分辨率探测 (处理 API 30+ 废弃方法)
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val (screenWidth, screenHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val rect = metrics.bounds
            rect.width() to rect.height()
        } else {
            val display = wm.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            size.x to size.y
        }

        // 策略：即使屏幕是1080p，只要支持HEVC，上报4K解码能力以避免不必要的转码
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

    /**
     * 级别映射：将 Android 系统常量转为 H.264 标准 Level 数值
     */
    private fun mapAvcLevel(androidLevel: Int): Int {
        return when (androidLevel) {
            // AVC/H264 levels
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