# 计划：验证并在 Kotlin 项目中实现缺失功能

目标是确保 Kotlin 项目 (`android/emby_tv`) 实现 Flutter 项目 (`flutter/emby_tv`) 中的所有功能，同时遵守用户关于保留 `implementation("androidx.tv:tv-material:1.0.1")` 和 `import androidx.tv.material3.*` 的指示。

根据详细对比，Kotlin 项目结构良好并实现了核心逻辑，但相比 Flutter 项目，仍缺少一些具体的功能和 UI 细节。

## 1. 缺失功能分析

### 1.1 EmbyService.kt

* **缺失方法**:

  * `getShowsNextBackInfo`: 用于播放器中的“接下来播放”逻辑。

  * `getSubtitle`: 用于获取字幕内容 (ASS/SRT)。

  * `getSessions`: 用于播放同步和会话管理。

  * `stoped` (Flutter 使用 `stoped`，Kotlin 使用 `stopped` - 命名差异，需确保用法一致)。

    <br />

### 1.2 AppModel.kt

* **缺失方法**:

  * 针对剧集的 `getPlaybackInfo` 重载（调用 `getShowsNextBackInfo`）。

  * `getSubtitle`: Service 调用的封装。

  * `getPlayingSessions`: Service 调用的封装。

  * `stoped` 与 `stopped` 的命名对齐。

### 1.3 PlayerScreen.kt

* **缺失特性**:

  * **字幕解析与渲染**: Flutter 项目有详细的 ASS 和 SRT 字幕解析逻辑 (`_parseAss`, `_parseSrt`, `_cleanSubtitleText`)。Kotlin 的 `PlayerScreen.kt` 目前这部分是占位符或不完整实现。

  * **播放校正 (Playback Correction)**: 如果直接播放失败，回退到服务器转码的逻辑 (`playbackCorrection` 状态和逻辑)。

  * **会话加载**: `loadSessionForCurrent` 逻辑，用于与服务器会话同步。

  * **UI 细节**: Kotlin 中的菜单对话框只是一个占位符。Flutter 有更完整的“播放选项”菜单（速度、字幕、音频、统计信息）。

  * **高级播放控制**: 快退/快进增量逻辑（20秒 vs 10秒），“下一集”自动播放逻辑。

### 1.4 MediaDetailScreen.kt

* **缺失特性**:

  * **季/集选择**: 电视剧获取和显示季/集的逻辑在 Flutter 中存在 (`_load` 配合 `Future.wait`, `_seasonEpisodes`, `_buildSeasonSelector`)，但在 Kotlin 中部分实现或为模拟数据。

  * **接下来播放逻辑**: 剧集的“播放”按钮逻辑（播放下一集 vs 第一集）。

  * **UI 润色**: 渐变背景、特定的布局约束、元数据的“胶囊”样式（年份、时长、评级）。

### 1.5 HomeScreen.kt & LoginScreen.kt

* **UI 细节**:

  * `LoginScreen`: 缺少“页脚通知”和“更新可用”提示细节。

  * `HomeScreen`: 顶部栏缺少“更新可用”提示。

  * **焦点处理**: Flutter 有特定的 `Focus` 组件和按键事件处理（例如 `LogicalKeyboardKey.contextMenu`），需要在 Compose 中完全验证（使用 `onKeyEvent`）。

### 1.6 AppUpdateManager.kt

* **实现**: Kotlin 项目使用 `com.azhon.appupdate` 库。这是 Flutter 更新逻辑的一个很好的原生替代方案。需要确保在 `UpdateScreen.kt` 中的集成是完整的。

## 2. 实施计划

我将逐个文件在 Kotlin 项目中实现缺失的功能。

### 第一步：更新 `EmbyService.kt`

使用httpAsJsonObject或httpStream修改所有请求的fun

改用 这个方式

```kotlin

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
        private const val CLIENT = "Android TV"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * 获取最新版本信息 - 对应 Flutter 的 getNewVersion
         * 使用 suspend 关键字支持协程异步调用
         */
        suspend fun getNewVersion(context: Context): JsonObject = withContext(Dispatchers.IO) {
            val client = NetworkClient.getClient(context)
            val url = "api.github.com"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github.v3+json")
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
            "${connector}X-Emby-Client=$CLIENT&X-Emby-Client-Version=1.0.0&X-Emby-Device-Name=Android%20TV&X-Emby-Device-Id=$deviceId"
        val fullUrl = "$serverUrl$url$params"

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
            gson.fromJson(reader, JsonObject::class.java)
        }
    }

    // --- 认证相关 ---

    suspend fun authenticate(username: String, password: String): JsonObject {
        val body = mapOf("Username" to username, "Pw" to password)
        return httpAsJsonObject("/Users/authenticatebyname", "POST", body)
    }

        suspend fun getLibraryList(userId: String, parentId: String, type: String): List<JsonObject> {
        val url =
            "/Users/$userId/Items?IncludeItemTypes=$type&Fields=BasicSyncInfo,PrimaryImageAspectRatio,ProductionYear,Status,EndDate&StartIndex=0&SortBy=SortName&SortOrder=Ascending&ParentId=$parentId&EnableImageTypes=Primary,Backdrop,Thumb&ImageTypeLimit=1&Recursive=true&Limit=2000&X-Emby-Token=$apiKey"
        return httpStream(url) { reader ->
            val root = gson.fromJson<JsonObject>(reader, JsonObject::class.java)
            root.getAsJsonArray("Items")?.map { it.asJsonObject } ?: emptyList()
        }
    }

```

*

  添加 `getShowsNextBackInfo`。

* 添加 `getSubtitle`。

* 添加 `getSessions`。

* 确保 `stopped` 与 Flutter 中使用的 API 端点 (`/Sessions/Playing/Stopped`) 匹配。

### 第二步：更新 `AppModel.kt`

* 为新的 Service 方法添加封装。

* 更新 `getPlaybackInfo` 以支持剧集的“接下来播放”逻辑。

### 第三步：增强 `PlayerScreen.kt`

* **字幕逻辑**: 在exoplayer中播放字幕

* **播放校正**: 实现“播放校正”开关和逻辑（切换到转码）。

* **会话同步**: 实现 `loadSessionForCurrent`。

* 实现flutter中相同的暂停页面显示内容

* **UI**: 改进菜单对话框，实现和flutter中一样tab选项和ui功能。

### 第四步：完善 `MediaDetailScreen.kt`

* 实现剧集的真实数据加载（季、集、接下来播放）。

* 移植剧集的“播放”按钮逻辑（自动播放下一集）。

* 改进 UI 以匹配 Flutter 的“胶囊”样式和布局。

### 第五步：验证 `LoginScreen.kt` 和 `HomeScreen.kt`

* 添加缺失的 UI 元素（页脚、更新提示）。

* 验证 TV 遥控器的焦点处理。

### 第六步：验证 `MainActivity.kt` & 导航

* 确保导航参数与数据流匹配（正确传递 `mediaJson` 或 ID）。

## 3. 执行顺序

1. **后端逻辑优先**: 修改 `EmbyService.kt` 和 `AppModel.kt`。
2. 验证各个components组件是否符合tv特点
3. **复杂 UI 逻辑**: 更新 `PlayerScreen.kt`。
4. **详情页逻辑**: 更新 `MediaDetailScreen.kt`。
5. **润色**: 更新 `LoginScreen.kt` 和 `HomeScreen.kt`。

此计划确保在保持原生 Kotlin/Compose 架构并遵守用户库约束的同时，与 Flutter 项目完全对齐。
