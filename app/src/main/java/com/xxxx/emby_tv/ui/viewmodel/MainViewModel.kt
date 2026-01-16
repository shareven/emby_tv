package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.data.local.PreferencesManager
import com.xxxx.emby_tv.data.remote.EmbyApi
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.util.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全局状态管理 ViewModel
 * 
 * 职责：
 * - 主题管理
 * - 全局登录状态观察
 * - 应用初始化
 * - 版本检查
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = PreferencesManager(application)
    private val repository = EmbyRepository.getInstance(application)

    // === 主题 ===
    var currentThemeId by mutableStateOf("purple")
        private set

    // === 登录状态（从 Repository 观察）===
    val isLoggedIn: Boolean get() = repository.isLoggedIn
    val isLoaded: Boolean get() = repository.isLoaded

    // === 版本信息 ===
    var currentVersion by mutableStateOf("")
        private set
    var newVersion by mutableStateOf("")
        private set
    var downloadUrl by mutableStateOf("")
        private set

    val needUpdate: Boolean
        get() {
            if (newVersion.isEmpty() || currentVersion.isEmpty()) return false
            return newVersion != currentVersion
        }

    init {
        loadThemeId()
        loadCurrentVersion()
    }

    /**
     * 初始化应用（加载凭证）
     */
    fun initialize(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.loadCredentials()
            onComplete()
        }
    }

    /**
     * 保存主题 ID
     */
    fun saveThemeId(themeId: String) {
        prefs.themeId = themeId
        currentThemeId = themeId
    }

    /**
     * 加载主题 ID
     */
    private fun loadThemeId() {
        currentThemeId = prefs.themeId
    }

    /**
     * 加载当前版本号
     */
    private fun loadCurrentVersion() {
        val context = getApplication<Application>().applicationContext
        currentVersion = try {
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
    }

    /**
     * 检查更新
     */
    fun checkUpdate() {
        if (newVersion.isNotEmpty()) return

        viewModelScope.launch {
            try {
                val context = getApplication<Application>().applicationContext
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
                ErrorHandler.logError("MainViewModel", "操作失败", e)
            }
        }
    }

    /**
     * 退出登录
     */
    fun logout() {
        repository.logout()
    }
}

// 扩展函数
private fun com.google.gson.JsonObject.safeGetString(key: String): String? {
    return try {
        this[key]?.asString
    } catch (e: Exception) {
        null
    }
}
