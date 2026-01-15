package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.data.remote.EmbyApi
import com.xxxx.emby_tv.safeGetString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 更新 ViewModel
 * 
 * 职责：
 * - 检查版本更新
 * - 管理更新状态
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    // === 版本信息 ===
    var currentVersion by mutableStateOf("")
        private set
    var newVersion by mutableStateOf("")
        private set
    var downloadUrl by mutableStateOf("")
        private set

    // === 状态 ===
    var isChecking by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // === 是否需要更新 ===
    val needUpdate: Boolean
        get() = newVersion.isNotEmpty() && newVersion != currentVersion

    init {
        loadCurrentVersion()
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
    fun checkUpdate(force: Boolean = false) {
        // 如果已经检查过且不是强制刷新，直接返回
        if (newVersion.isNotEmpty() && !force) return
        if (isChecking) return

        viewModelScope.launch {
            isChecking = true
            errorMessage = null

            try {
                val res = withContext(Dispatchers.IO) {
                    EmbyApi.getNewVersion(getApplication())
                }

                val tagName = res.safeGetString("tag_name") ?: ""
                val assets = res["assets"]?.asJsonArray

                if (assets != null && assets.size() > 0) {
                    downloadUrl = assets[0].asJsonObject.safeGetString("browser_download_url") ?: ""
                }

                newVersion = tagName
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isChecking = false
            }
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        errorMessage = null
    }
}
