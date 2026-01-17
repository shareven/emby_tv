package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.remote.EmbyApi
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.session.AccountInfo
import com.xxxx.emby_tv.util.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 搜索结果包装类，包含所属账号信息
 */
data class SearchResultModel(
    val item: BaseItemDto,
    val account: AccountInfo
)

/**
 * 搜索 ViewModel
 *
 * 职责：
 * - 管理搜索关键词
 * - 并发搜索所有已登录账号 (Max 4 requests)
 * - 聚合搜索结果
 * - 支持按账号过滤
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)
    private val context = application

    companion object {
        private const val TAG = "SearchViewModel"
        const val PAGE_SIZE = 20
        private const val MAX_CONCURRENT_REQUESTS = 4
    }

    // === 搜索关键词 ===
    var currentQuery by mutableStateOf("")
        private set

    // === 搜索结果 (聚合后) ===
    private var _allSearchResults by mutableStateOf<List<SearchResultModel>?>(null)

    // === 过滤条件 (当前选中的账号) ===
    var filterAccount by mutableStateOf<AccountInfo?>(null)

    // === UI 展示用的结果 (经过过滤) ===
    val searchResults: List<SearchResultModel>?
        get() {
            val all = _allSearchResults ?: return null
            return if (filterAccount != null) {
                all.filter { it.account.id == filterAccount?.id }
            } else {
                all
            }
        }
    
    // === 状态 ===
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    // === 本次搜索涉及的总条数 (统计用) ===
    var totalCount by mutableStateOf(0)
        private set

    /**
     * 执行多账号并发搜索
     */
    fun search(query: String) {
        if (query.isBlank()) {
            clearResults()
            return
        }

        // 如果搜索词没变且已有数据，不重复搜索 (除非强制刷新，这里简单处理)
        if (query == currentQuery && _allSearchResults != null) {
            return
        }

        currentQuery = query
        filterAccount = null // 重置过滤

        viewModelScope.launch {
            isLoading = true
            _allSearchResults = null
            errorMessage = null
            totalCount = 0

            try {
                val accounts = repository.savedAccounts
                if (accounts.isEmpty()) {
                    // 没有账号，可能是异常状态
                    isLoading = false
                    return@launch
                }

                // 按当前账号排序：当前账号排第一，其他不变 (或者按名称排，这里只保证当前优先)
                val currentId = repository.currentAccountId
                val sortedAccounts = accounts.sortedByDescending { it.id == currentId }

                // 分批并发请求，每次最多 MAX_CONCURRENT_REQUESTS 个
                val allResults = mutableListOf<SearchResultModel>()
                var total = 0

                // 使用 chunked 分批处理
                val chunks = sortedAccounts.chunked(MAX_CONCURRENT_REQUESTS)
                
                withContext(Dispatchers.IO) {
                    chunks.forEach { batchAccounts ->
                        val deferreds = batchAccounts.map { account ->
                            async {
                                searchAccount(account, query)
                            }
                        }
                        // 等待这一批完成
                        val batchResults = deferreds.awaitAll()
                        batchResults.forEach { (items, count) ->
                            allResults.addAll(items)
                            total += count
                        }
                    }
                }

                _allSearchResults = allResults
                totalCount = total
                
                ErrorHandler.logDebug(TAG, "多账号搜索完成: ${allResults.size} 条结果, 总计 $total")

            } catch (e: Exception) {
                ErrorHandler.logError(TAG, "搜索失败", e)
                _allSearchResults = emptyList()
                errorMessage = e.message ?: "搜索失败"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 单个账号搜索
     */
    private suspend fun searchAccount(account: AccountInfo, query: String): Pair<List<SearchResultModel>, Int> {
        return try {
            // 直接调用 EmbyApi，不通过 Repository 的 session 状态
            // 因为我们需要使用非当前登录账号的凭证
            val (items, total) = EmbyApi.searchItems(
                context = context,
                serverUrl = account.serverUrl,
                apiKey = account.apiKey,
                deviceId = account.deviceId, // 使用账号绑定的 deviceId (或者 repository.deviceId)
                userId = account.userId,
                query = query,
                startIndex = 0,
                limit = PAGE_SIZE // 搜索每个服务器前20条
            )
            
            val models = items.map { item ->
                SearchResultModel(item, account)
            }
            Pair(models, total)
        } catch (e: Exception) {
            ErrorHandler.logError(TAG, "账号 [${account.username}] 搜索失败: ${e.message}")
            // 单个账号失败不影响整体，返回空
            Pair(emptyList(), 0)
        }
    }

    /**
     * 刷新搜索（例如账号列表变化后，或者用户手动刷新）
     * 这里的逻辑改为简单的重新搜索
     */
    fun refreshSearch() {
        if (currentQuery.isNotBlank()) {
            val query = currentQuery
            currentQuery = "" // 强制触发
            search(query)
        }
    }

    /**
     * 设置账号过滤
     */
    fun setAccountFilter(account: AccountInfo?) {
        filterAccount = account
    }

    /**
     * 清空
     */
    fun clearResults() {
        currentQuery = ""
        _allSearchResults = null
        filterAccount = null
        totalCount = 0
        errorMessage = null
    }
}
