package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.util.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 搜索 ViewModel
 * 
 * 职责：
 * - 管理搜索关键词和结果
 * - 支持分页加载
 * - 支持账号切换后重新搜索
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)

    companion object {
        private const val TAG = "SearchViewModel"
        const val PAGE_SIZE = 36
    }

    // === 搜索关键词 ===
    var currentQuery by mutableStateOf("")
        private set

    // === 搜索结果 ===
    var searchResults by mutableStateOf<List<BaseItemDto>?>(null)
        private set

    // === 分页状态 ===
    var totalCount by mutableIntStateOf(0)
        private set
    var currentPage by mutableIntStateOf(0)
        private set
    var hasMoreData by mutableStateOf(false)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set

    // === 加载状态 ===
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)

    // === 当前账号ID（用于检测账号切换）===
    var currentAccountId by mutableStateOf<String?>(null)
        private set

    init {
        // 初始化时获取当前账号ID
        currentAccountId = repository.currentAccountId
    }

    /**
     * 执行搜索
     */
    fun search(query: String) {
        if (query.isBlank()) {
            clearResults()
            return
        }

        // 如果搜索词没变且已有数据，不重复搜索
        if (query == currentQuery && searchResults != null) {
            return
        }

        currentQuery = query
        currentPage = 0
        hasMoreData = true
        currentAccountId = repository.currentAccountId

        viewModelScope.launch {
            isLoading = true
            searchResults = null // 显示 loading

            try {
                val (items, total) = withContext(Dispatchers.IO) {
                    repository.searchItems(query, 0, PAGE_SIZE)
                }
                searchResults = items
                totalCount = total
                currentPage = 1
                hasMoreData = items.size < total
                
                ErrorHandler.logDebug(TAG, "搜索完成: ${items.size}/$total 条结果")
            } catch (e: Exception) {
                ErrorHandler.logError(TAG, "搜索失败", e)
                searchResults = emptyList()
                hasMoreData = false
                errorMessage = e.message ?: "搜索失败"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 加载更多结果
     */
    fun loadMore() {
        // 防止重复加载
        if (isLoadingMore || !hasMoreData || isLoading || currentQuery.isBlank()) {
            return
        }

        viewModelScope.launch {
            isLoadingMore = true

            try {
                val startIndex = currentPage * PAGE_SIZE
                val (newItems, total) = withContext(Dispatchers.IO) {
                    repository.searchItems(currentQuery, startIndex, PAGE_SIZE)
                }
                
                if (newItems.isNotEmpty()) {
                    searchResults = (searchResults ?: emptyList()) + newItems
                    totalCount = total
                    currentPage++
                    hasMoreData = (searchResults?.size ?: 0) < total
                    
                    ErrorHandler.logDebug(TAG, "加载更多完成: ${searchResults?.size}/$total 条结果")
                } else {
                    hasMoreData = false
                }
            } catch (e: Exception) {
                ErrorHandler.logError(TAG, "加载更多失败", e)
                // 加载更多失败不显示错误，保留已有数据
            } finally {
                isLoadingMore = false
            }
        }
    }

    /**
     * 使用当前搜索词重新搜索（用于账号切换后）
     */
    fun refreshSearch() {
        if (currentQuery.isBlank()) {
            return
        }

        // 检查账号是否切换
        val newAccountId = repository.currentAccountId
        if (newAccountId != currentAccountId) {
            currentAccountId = newAccountId
            // 重置状态，重新搜索
            val query = currentQuery
            currentQuery = ""
            search(query)
        }
    }

    /**
     * 清空搜索结果
     */
    fun clearResults() {
        currentQuery = ""
        searchResults = null
        currentPage = 0
        totalCount = 0
        hasMoreData = false
        errorMessage = null
    }
}
