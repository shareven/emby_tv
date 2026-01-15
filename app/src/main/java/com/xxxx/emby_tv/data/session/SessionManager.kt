package com.xxxx.emby_tv.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 账号信息数据类
 */
data class AccountInfo(
    val id: String,                    // 唯一标识（使用 serverUrl + userId 组合）
    val serverUrl: String,
    val apiKey: String,
    val userId: String,
    val username: String,
    val password: String,              // 用于重新验证
    val deviceId: String,
    val displayName: String = "",      // 显示名称（用户名@服务器）
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 单例管理登录状态和认证信息
 * 支持多账号切换
 */
class SessionManager private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("emby_tv", Context.MODE_PRIVATE)
    private val gson = Gson()

    // === 当前会话状态（响应式）===
    var serverUrl: String? by mutableStateOf(null)
        private set
    var apiKey: String? by mutableStateOf(null)
        private set
    var userId: String? by mutableStateOf(null)
        private set
    var deviceId: String = ""
        private set

    // 加载状态（响应式）
    var isLoaded by mutableStateOf(false)
        private set

    // 登录状态（响应式，依赖于 apiKey 和 userId）
    val isLoggedIn: Boolean
        get() = !apiKey.isNullOrEmpty() && !userId.isNullOrEmpty()

    // === 多账号支持 ===
    private var _savedAccounts by mutableStateOf<List<AccountInfo>>(emptyList())
    val savedAccounts: List<AccountInfo> get() = _savedAccounts

    var currentAccountId: String? by mutableStateOf(null)
        private set

    init {
        loadDeviceId()
        loadSavedAccounts()
    }

    // === 设备 ID 管理 ===
    private fun loadDeviceId() {
        var id = prefs.getString("deviceId", null)
        if (id == null) {
            id = "kotlin_tv_${System.currentTimeMillis()}"
            prefs.edit().putString("deviceId", id).apply()
        }
        deviceId = id ?: ""
    }

    // === 凭证验证回调接口 ===
    interface CredentialValidator {
        suspend fun validate(serverUrl: String, userId: String, apiKey: String): Boolean
    }

    // === 凭证加载 ===
    suspend fun loadCredentials(validator: CredentialValidator): Boolean {
        val savedServerUrl = prefs.getString("serverUrl", null)
        val savedApiKey = prefs.getString("apiKey", null)
        val savedUserId = prefs.getString("userId", null)

        if (savedServerUrl != null && savedApiKey != null && savedUserId != null) {
            try {
                val isValid = validator.validate(savedServerUrl, savedUserId, savedApiKey)
                if (isValid) {
                    serverUrl = savedServerUrl
                    apiKey = savedApiKey
                    userId = savedUserId
                    currentAccountId = generateAccountId(savedServerUrl, savedUserId)
                    isLoaded = true
                    return true
                } else {
                    // 凭证无效，清除
                    clearCurrentCredentials()
                    isLoaded = true
                    return false
                }
            } catch (e: Exception) {
                // 网络错误，保留凭证（可能只是临时网络问题）
                serverUrl = savedServerUrl
                apiKey = savedApiKey
                userId = savedUserId
                currentAccountId = generateAccountId(savedServerUrl, savedUserId)
                isLoaded = true
                return true
            }
        }

        isLoaded = true
        return false
    }

    // === 保存凭证 ===
    fun saveCredentials(
        server: String,
        key: String,
        user: String,
        username: String,
        password: String
    ) {
        // 保存当前凭证
        prefs.edit().apply {
            putString("serverUrl", server)
            putString("apiKey", key)
            putString("userId", user)
            putString("username", username)
            putString("password", password)
            apply()
        }

        // 更新内存状态
        serverUrl = server
        apiKey = key
        userId = user
        currentAccountId = generateAccountId(server, user)

        // 保存到账号列表
        val accountId = generateAccountId(server, user)
        val displayName = "$username@${extractServerName(server)}"
        val newAccount = AccountInfo(
            id = accountId,
            serverUrl = server,
            apiKey = key,
            userId = user,
            username = username,
            password = password,
            deviceId = deviceId,
            displayName = displayName
        )
        addOrUpdateAccount(newAccount)
    }

    // === 清除当前凭证 ===
    fun clearCurrentCredentials() {
        prefs.edit().apply {
            remove("apiKey")
            remove("userId")
            apply()
        }
        apiKey = null
        userId = null
        serverUrl = null
        currentAccountId = null
    }

    // === 完全登出（清除当前凭证但保留账号列表）===
    fun logout() {
        clearCurrentCredentials()
    }

    // === 多账号管理 ===

    /**
     * 生成账号唯一 ID
     */
    private fun generateAccountId(serverUrl: String, userId: String): String {
        return "${serverUrl.hashCode()}_$userId"
    }

    /**
     * 提取服务器名称用于显示
     */
    private fun extractServerName(serverUrl: String): String {
        return try {
            val url = java.net.URL(serverUrl)
            url.host
        } catch (e: Exception) {
            serverUrl.take(20)
        }
    }

    /**
     * 加载保存的账号列表
     */
    private fun loadSavedAccounts() {
        val json = prefs.getString("saved_accounts", null)
        if (json != null) {
            try {
                val type = object : TypeToken<List<AccountInfo>>() {}.type
                _savedAccounts = gson.fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                _savedAccounts = emptyList()
            }
        }
    }

    /**
     * 保存账号列表
     */
    private fun persistAccounts() {
        val json = gson.toJson(_savedAccounts)
        prefs.edit().putString("saved_accounts", json).apply()
    }

    /**
     * 添加或更新账号
     */
    private fun addOrUpdateAccount(account: AccountInfo) {
        val existingIndex = _savedAccounts.indexOfFirst { it.id == account.id }
        _savedAccounts = if (existingIndex >= 0) {
            _savedAccounts.toMutableList().apply {
                set(existingIndex, account)
            }
        } else {
            _savedAccounts + account
        }
        persistAccounts()
    }

    /**
     * 切换账号
     * @return true 如果切换成功
     */
    fun switchAccount(accountId: String): Boolean {
        val account = _savedAccounts.find { it.id == accountId } ?: return false

        // 更新当前凭证
        prefs.edit().apply {
            putString("serverUrl", account.serverUrl)
            putString("apiKey", account.apiKey)
            putString("userId", account.userId)
            putString("username", account.username)
            putString("password", account.password)
            apply()
        }

        serverUrl = account.serverUrl
        apiKey = account.apiKey
        userId = account.userId
        currentAccountId = account.id

        return true
    }

    /**
     * 删除账号
     */
    fun removeAccount(accountId: String) {
        _savedAccounts = _savedAccounts.filter { it.id != accountId }
        persistAccounts()

        // 如果删除的是当前账号，清除凭证
        if (currentAccountId == accountId) {
            clearCurrentCredentials()
        }
    }

    /**
     * 获取当前账号信息
     */
    fun getCurrentAccount(): AccountInfo? {
        return currentAccountId?.let { id ->
            _savedAccounts.find { it.id == id }
        }
    }

    // === 回显用（兼容旧代码）===
    val savedServerUrl: String?
        get() = prefs.getString("serverUrl", null)
    val savedUsername: String
        get() = prefs.getString("username", "") ?: ""
    val savedPassword: String
        get() = prefs.getString("password", "") ?: ""

    companion object {
        @Volatile
        private var instance: SessionManager? = null

        fun getInstance(context: Context): SessionManager {
            return instance ?: synchronized(this) {
                instance ?: SessionManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
