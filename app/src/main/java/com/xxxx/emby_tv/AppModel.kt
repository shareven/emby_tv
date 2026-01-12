package com.xxxx.emby_tv

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.model.BaseItemDto
import com.xxxx.emby_tv.model.MediaDto
import com.xxxx.emby_tv.model.SessionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.ifEmpty

/**
 * 应用状态管理类
关于 ViewModelScope 的位置（核心逻辑）
appModel 在最上层，：
UI 触发的异步任务（如：点击按钮开始登录）：写在 viewModelScope.launch 里。
被动的数据同步（如：播放器上报进度）：写在 suspend 里
 */
class AppModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("emby_tv", Context.MODE_PRIVATE)

    private var embyService: EmbyService? = null


    // 状态变量

    var serverUrl by mutableStateOf<String?>(null)
        private set
    var apiKey by mutableStateOf<String?>(null)
        private set
    var userId by mutableStateOf<String?>(null)
        private set
    var deviceId by mutableStateOf("")
        private set
    var isLoaded by mutableStateOf(false)
        private set

    // State for UI
    var isLoading by mutableStateOf(false)
        private set
    var currentMediaInfo by mutableStateOf<BaseItemDto?>(null)
        private set

    var resumeItems by mutableStateOf<List<BaseItemDto>?>(null)
        private set
    var libraryLatestItems by mutableStateOf<List<BaseItemDto>?>(null)
        private set
    var favoriteItems by mutableStateOf<List<BaseItemDto>?>(null)
        private set


    var libraryItems by mutableStateOf<List<BaseItemDto>?>(null)
        private set

    var currentVersion by mutableStateOf<String>("")
        private set
    var newVersion by mutableStateOf<String>("")
        private set
    var downloadUrl by mutableStateOf<String>("")
        private set

    // 主题色相关
    var currentThemeId by mutableStateOf("purple") // 默认主题ID
        private set

    // 登录状态判断
    val isLoggedIn: Boolean
        get() = !apiKey.isNullOrEmpty() && !userId.isNullOrEmpty()

    val needUpdate: Boolean
        get() {
            if (newVersion.isEmpty() || currentVersion.isEmpty()) return false
            return newVersion != currentVersion
        }

    // Saved Credentials Accessors
    val savedServerUrl: String?
        get() = prefs.getString("serverUrl", null)
    val savedUsername: String
        get() = prefs.getString("username", "") ?: ""
    val savedPassword: String
        get() = prefs.getString("password", "") ?: ""

    init {
        loadCredentials()
        loadThemeId() // 加载保存的主题色
    }


    // 内部使用的辅助函数，确保 service 已初始化
    private fun getService(): EmbyService {
        return embyService ?: EmbyService(getApplication(), serverUrl!!, apiKey!!, deviceId).also {
            embyService = it
        }
    }

    // 对应 Flutter 的 checkUpdate
    fun checkUpdate() {
        // 如果已经获取过新版本，直接返回
        if (newVersion.isNotEmpty()) return

        viewModelScope.launch {
            // 1. 获取当前版本号 (类似 PackageInfo.fromPlatform)
            val context = getApplication<Application>().applicationContext
            val version = try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                "v${packageInfo.versionName}"
            } catch (e: Exception) {
                ""
            }

            currentVersion = version

            // 2. 网络请求获取新版本 (切换到 IO 线程)
            try {
                val res = withContext(Dispatchers.IO) {
                    EmbyService.getNewVersion(context)
                }

                val tagName = res.safeGetString("tag_name") ?: ""
                val assets = res["assets"]?.asJsonArray

                if (assets != null && assets.size() > 0) {
                    downloadUrl = assets[0].asJsonObject.safeGetString("browser_download_url") ?: ""
                }

                newVersion = tagName

            } catch (e: Exception) {
                // 处理网络异常
                e.printStackTrace()
            }
        }
    }

    /**
     * 从SharedPreferences加载保存的凭证
     */
    fun loadCredentials() {
        viewModelScope.launch {
            val savedServerUrl = prefs.getString("serverUrl", null)
            val savedApiKey = prefs.getString("apiKey", null)
            val savedUserId = prefs.getString("userId", null)

            getDeviceId()
            // 验证本地凭证有效性
            if (savedServerUrl != null && savedApiKey != null && savedUserId != null) {
                try {
                    val service = EmbyService(getApplication(), savedServerUrl, "", deviceId)
                    val isValid = service.testKey(savedUserId, savedApiKey)
                    Log.e("testKey isValid",isValid.toString())
                    if ( isValid) {
                        serverUrl = savedServerUrl
                        apiKey = savedApiKey
                        userId = savedUserId

                        // 加载用户数据
                        loadData()
                    } else {
                        // 清除无效凭证
                        clearCredentials()
                        isLoaded = true
                    }
                } catch (e: Exception) {
                    // 凭证验证失败，但不清除，可能是网络问题
                    serverUrl = savedServerUrl
                    apiKey = savedApiKey
                    userId = savedUserId
                    isLoaded = true
                }
            }else {
                isLoaded = true
            }

        }
    }

    /**
     * 加载数据
     */
    fun loadData() {
        viewModelScope.launch {
            try {
                // 检查必要的参数是否存在
                if (serverUrl == null || apiKey == null || userId == null) {
                    resumeItems = emptyList()
                    libraryLatestItems = emptyList()
                    
                    return@launch
                }

                // Parallel execution for better performance
                val deferredResume = async(Dispatchers.IO) {
                    getService().getResumeItems(userId!!)
                }
                val deferredLatest = async(Dispatchers.IO) {
                    getService().getLatestItems(userId!!)
                }
                val deferredFavorites = async(Dispatchers.IO) {
                    getService().getFavoriteItems(userId!!)
                }

                resumeItems = deferredResume.await()
                libraryLatestItems = deferredLatest.await()
                favoriteItems = deferredFavorites.await()
            } catch (e: Exception) {
                // 处理数据加载错误，设置默认值防止闪退
                resumeItems = emptyList()
                libraryLatestItems = emptyList()
                favoriteItems = emptyList()
                favoriteItems = emptyList()
                // 可以在这里添加日志记录
                e.printStackTrace()
            } finally {
                isLoaded = true
            }
        }
    }


    /**
     * 加载媒体库项目 - 与Flutter版本保持一致，必须传入type参数
     */
    fun loadLibraryItems(parentId: String, type: String) {
        viewModelScope.launch {
            try {

                libraryItems = null // If they are same

                val items = withContext(Dispatchers.IO) {
                    getService().getLibraryList(userId!!,parentId, type)
                }
                libraryItems = items
            } catch (e: Exception) {
                // 处理错误
                libraryItems = emptyList()
            }
        }
    }



    /**
     * 切换收藏状态 - 简化版，只发送请求，不关心结果
     */
    fun toggleFavorite(itemId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                if (isFavorite) {
                    getService().addToFavorites(userId!!, itemId)
                } else {
                    getService().removeFromFavorites(userId!!,itemId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getDeviceId() {
        var id = prefs.getString("deviceId", null)
        if (id == null) {
            id = "kotlin_tv_${System.currentTimeMillis()}"
            prefs.edit().putString("deviceId", id).apply()
        }
        deviceId = id ?: ""
    }

    private fun clearCredentials() {
        prefs.edit().apply {
            remove("apiKey")
            remove("userId")
            apply()
        }
        apiKey = null
        userId = null
        serverUrl = null
        embyService = null
    }

    /**
     * 退出登录
     */
    fun logout() {
        clearCredentials()
        resumeItems = emptyList()
        libraryLatestItems = emptyList()
        favoriteItems = emptyList()
    }

    /**
     * 登录方法
     */
    fun login(serverUrl: String, username: String, password: String, onResult: (Boolean) -> Unit) {
        isLoading = true
        viewModelScope.launch {
            try {
                val service = EmbyService(getApplication(), serverUrl, "", deviceId)
                val authResult = withContext(Dispatchers.IO) {
                    service.authenticate(username, password)
                }

                this@AppModel.serverUrl = serverUrl
                apiKey = authResult.accessToken  // 使用accessToken作为API密钥
                userId = authResult.user?.id ?: ""  // 使用用户ID

                // 保存凭证
                prefs.edit().apply {
                    putString("serverUrl", serverUrl)
                    putString("apiKey", apiKey)
                    putString("userId", userId)
                    putString("username", username)
                    putString("password", password)
                    apply()
                }
                embyService = EmbyService(getApplication(), serverUrl, apiKey!!, deviceId)

                loadData()
                onResult(true)
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false)
            } finally {
                withContext(Dispatchers.Main) {
                   isLoading = false
                }
            }
        }
    }

    // --- Helper methods to call EmbyService ---

    suspend fun getSeasonList(seriesId: String): List<BaseItemDto> {
        return getService().getSeasonList(userId!!, seriesId)
    }

    suspend fun getSeriesList(parentId: String): List<BaseItemDto> {
        return getService().getSeriesList(userId!!, parentId)
    }

    suspend fun getResumeItem(seriesId: String): BaseItemDto? {
        return getService().getResumeItems(userId!!, seriesId).firstOrNull()
    }

    suspend fun getShowsNextUp(seriesId: String): List<BaseItemDto> {
        return getService().getShowsNextUp(userId!!, seriesId)
    }


    fun playNextUp(seriesId: String,onBack:(item: BaseItemDto)-> Unit) {
        viewModelScope.launch {
            val itemsArray = getShowsNextUp(seriesId)
            // 2. 逻辑判断：如果 Items 为空，则去请求 SeriesList (对应 Flutter 的 items.isEmpty)
            val items = itemsArray.ifEmpty {
                getSeriesList(seriesId)
            }
            onBack(items.first())
        }
    }

    fun loadMediaInfo(id: String) {
        viewModelScope.launch {
            try {
                currentMediaInfo = null
                val info = withContext(Dispatchers.IO) {
                    getService().getMediaInfo(userId!!, id)
                }
                currentMediaInfo = info
            } catch (e: Exception) {
                e.printStackTrace()
                currentMediaInfo = null
            }
        }
    }

    suspend fun getMediaInfo(id: String): BaseItemDto {
        return getService().getMediaInfo(userId!!, id)
    }

    suspend fun getPlaybackInfo(
        id: String,
        playbackPositionTicks: Long,
        selectedAudioIndex: Int?=null,
        selectedSubtitleIndex: Int?=null,
        disableHevc: Boolean = false
    ): MediaDto {
        return getService().getPlaybackInfo(userId!!, id, playbackPositionTicks,selectedAudioIndex, selectedSubtitleIndex, disableHevc)
    }
    
     fun reportPlaybackProgress(body: Any) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                 getService().reportPlaybackProgress(body)
            }
        }
    }

     fun playing(body: Any) {
      viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                 getService().playing(body)
            }
        }
    }

      fun stopped(body: Any) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                  getService().stopped(body)
            }
        }
    }

    fun stopActiveEncodings(playSessionId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            getService().stopActiveEncodings(playSessionId)
        }
    }

    suspend fun getPlayingSessions(): List<SessionDto> {
      return  getService().getPlayingSessions()
    }

    /**
     * 保存主题色ID到SharedPreferences
     */
    fun saveThemeId(themeId: String) {
        prefs.edit().putString("selected_theme_id", themeId).apply()
        currentThemeId = themeId
    }

    /**
     * 从SharedPreferences加载主题色ID
     */
    private fun loadThemeId() {
        val savedThemeId = prefs.getString("selected_theme_id", "purple") ?: "purple"
        currentThemeId = savedThemeId
    }


}
