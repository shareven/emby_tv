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
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.remote.EmbyApi
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.session.SessionManager
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.SessionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.ifEmpty

/**
 * 应用状态管理类
 * 
 * 注意：此类现在是过渡层，内部委托给新的 Repository 和 SessionManager
 * 在完成迁移后，各 Screen 应该直接使用各自的 ViewModel
 */
class AppModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("emby_tv", Context.MODE_PRIVATE)
    
    // 新的数据层
    private val repository = EmbyRepository.getInstance(application)
    private val session = SessionManager.getInstance(application)
    private val prefsManager = PreferencesManager(application)

    // === 状态变量（从 SessionManager 代理）===

    val serverUrl: String? get() = session.serverUrl
    val apiKey: String? get() = session.apiKey
    val userId: String? get() = session.userId
    val deviceId: String get() = session.deviceId
    
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
    var currentThemeId by mutableStateOf("purple")
        private set

    // 登录状态判断
    val isLoggedIn: Boolean
        get() = repository.isLoggedIn

    val needUpdate: Boolean
        get() {
            if (newVersion.isEmpty() || currentVersion.isEmpty()) return false
            return newVersion != currentVersion
        }

    // Saved Credentials Accessors
    val savedServerUrl: String? get() = repository.savedServerUrl
    val savedUsername: String get() = repository.savedUsername
    val savedPassword: String get() = repository.savedPassword

    init {
        loadCredentials()
        loadThemeId()
    }

    // 对应 Flutter 的 checkUpdate
    fun checkUpdate() {
        if (newVersion.isNotEmpty()) return

        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val version = try {
                val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                "v${packageInfo.versionName}"
            } catch (e: Exception) {
                ""
            }

            currentVersion = version

            try {
                val res = withContext(Dispatchers.IO) {
                    EmbyApi.getNewVersion(context)
                }

                val tagName = res.safeGetString("tag_name") ?: ""
                val assets = res["assets"]?.asJsonArray

                if (assets != null && assets.size() > 0) {
                    downloadUrl = assets[0].asJsonObject.safeGetString("browser_download_url") ?: ""
                }

                newVersion = tagName

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 从SharedPreferences加载保存的凭证
     */
    fun loadCredentials() {
        viewModelScope.launch {
            try {
                val loaded = repository.loadCredentials()
                if (loaded && repository.isLoggedIn) {
                    loadData()
                } else {
                    isLoaded = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                if (!repository.isLoggedIn) {
                    resumeItems = emptyList()
                    libraryLatestItems = emptyList()
                    return@launch
                }

                val deferredResume = async(Dispatchers.IO) {
                    repository.getResumeItems()
                }
                val deferredLatest = async(Dispatchers.IO) {
                    repository.getLatestItems()
                }
                val deferredFavorites = async(Dispatchers.IO) {
                    repository.getFavoriteItems()
                }

                resumeItems = deferredResume.await()
                libraryLatestItems = deferredLatest.await()
                favoriteItems = deferredFavorites.await()
            } catch (e: Exception) {
                resumeItems = emptyList()
                libraryLatestItems = emptyList()
                favoriteItems = emptyList()
                e.printStackTrace()
            } finally {
                isLoaded = true
            }
        }
    }

    /**
     * 加载媒体库项目
     */
    fun loadLibraryItems(parentId: String, type: String) {
        viewModelScope.launch {
            try {
                libraryItems = null

                val items = withContext(Dispatchers.IO) {
                    repository.getLibraryList(parentId, type)
                }
                libraryItems = items
            } catch (e: Exception) {
                libraryItems = emptyList()
            }
        }
    }

    /**
     * 切换收藏状态
     */
    fun toggleFavorite(itemId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            try {
                if (isFavorite) {
                    repository.addToFavorites(itemId)
                } else {
                    repository.removeFromFavorites(itemId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 退出登录
     */
    fun logout() {
        repository.logout()
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
                repository.login(serverUrl, username, password)
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

    // === Helper methods ===

    suspend fun getSeasonList(seriesId: String): List<BaseItemDto> {
        return repository.getSeasonList(seriesId)
    }

    suspend fun getSeriesList(parentId: String): List<BaseItemDto> {
        return repository.getSeriesList(parentId)
    }

    suspend fun getResumeItem(seriesId: String): BaseItemDto? {
        return repository.getResumeItems(seriesId).firstOrNull()
    }

    suspend fun getShowsNextUp(seriesId: String): List<BaseItemDto> {
        return repository.getShowsNextUp(seriesId)
    }

    fun playNextUp(seriesId: String, onBack: (item: BaseItemDto) -> Unit) {
        viewModelScope.launch {
            val itemsArray = getShowsNextUp(seriesId)
            val items = itemsArray.ifEmpty {
                getSeriesList(seriesId)
            }
            if (items.isNotEmpty()) {
                onBack(items.first())
            }
        }
    }

    fun loadMediaInfo(id: String) {
        viewModelScope.launch {
            try {
                currentMediaInfo = null
                val info = withContext(Dispatchers.IO) {
                    repository.getMediaInfo(id)
                }
                currentMediaInfo = info
            } catch (e: Exception) {
                e.printStackTrace()
                currentMediaInfo = null
            }
        }
    }

    suspend fun getMediaInfo(id: String): BaseItemDto {
        return repository.getMediaInfo(id)
    }

    suspend fun getPlaybackInfo(
        id: String,
        playbackPositionTicks: Long,
        selectedAudioIndex: Int? = null,
        selectedSubtitleIndex: Int? = null,
        disableHevc: Boolean = false
    ): MediaDto {
        return repository.getPlaybackInfo(
            id,
            playbackPositionTicks,
            selectedAudioIndex,
            selectedSubtitleIndex,
            disableHevc
        )
    }

    fun reportPlaybackProgress(body: Any) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                repository.reportPlaybackProgress(body)
            }
        }
    }

    fun playing(body: Any) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                repository.playing(body)
            }
        }
    }

    fun stopped(body: Any) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                repository.stopped(body)
            }
        }
    }

    fun stopActiveEncodings(playSessionId: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.stopActiveEncodings(playSessionId)
        }
    }

    suspend fun getPlayingSessions(): List<SessionDto> {
        return repository.getPlayingSessions()
    }

    /**
     * 保存主题色ID
     */
    fun saveThemeId(themeId: String) {
        prefsManager.themeId = themeId
        currentThemeId = themeId
    }

    /**
     * 加载主题色ID
     */
    private fun loadThemeId() {
        currentThemeId = prefsManager.themeId
    }
}
