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
 * 媒体库 ViewModel
 * 
 * 职责：
 * - 加载媒体库列表
 * - 支持分页加载
 * - 支持分类筛选
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)

    companion object {
        private const val TAG = "LibraryViewModel"
        const val PAGE_SIZE = 36
    }

    // === 媒体库数据 ===
    var libraryItems by mutableStateOf<List<BaseItemDto>?>(null)
        private set

    // === 分页状态 ===
    var totalCount by mutableIntStateOf(0)
        private set
    var currentPage by mutableIntStateOf(0)
        private set
    var hasMoreData by mutableStateOf(true)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set

    // === 加载状态 ===
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // === 当前筛选条件 ===
    var currentParentId by mutableStateOf("")
        private set
    var currentType by mutableStateOf("")
        private set

    /**
     * 加载媒体库列表（首页）
     */
    fun loadItems(parentId: String, type: String) {
        // 如果参数没变且已有数据，不重复加载
        if (parentId == currentParentId && type == currentType && libraryItems != null) {
            return
        }

        currentParentId = parentId
        currentType = type
        currentPage = 0
        hasMoreData = true

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            libraryItems = null // 显示 loading

            try {
                val (items, total) = withContext(Dispatchers.IO) {
                    repository.getLibraryList(parentId, type, 0, PAGE_SIZE)
                }
                libraryItems = items
                totalCount = total
                currentPage = 1
                hasMoreData = items.size < total
                
                ErrorHandler.logDebug(TAG, "加载完成: ${items.size}/$total 条数据")
            } catch (e: Exception) {
                ErrorHandler.logError(TAG, "加载媒体库失败", e)
                errorMessage = ErrorHandler.getFriendlyMessage(e)
                libraryItems = emptyList()
                hasMoreData = false
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 加载更多数据
     */
    fun loadMore() {
        // 防止重复加载
        if (isLoadingMore || !hasMoreData || isLoading) {
            return
        }

        viewModelScope.launch {
            isLoadingMore = true

            try {
                val startIndex = currentPage * PAGE_SIZE
                val (newItems, total) = withContext(Dispatchers.IO) {
                    repository.getLibraryList(currentParentId, currentType, startIndex, PAGE_SIZE)
                }
                
                if (newItems.isNotEmpty()) {
                    libraryItems = (libraryItems ?: emptyList()) + newItems
                    totalCount = total
                    currentPage++
                    hasMoreData = (libraryItems?.size ?: 0) < total
                    
                    ErrorHandler.logDebug(TAG, "加载更多完成: ${libraryItems?.size}/$total 条数据")
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
     * 强制刷新（忽略缓存）
     */
    fun refresh() {
        val parentId = currentParentId
        val type = currentType
        currentParentId = ""  // 重置以强制刷新
        currentType = ""
        loadItems(parentId, type)
    }

    /**
     * 清空数据
     */
    fun clear() {
        libraryItems = null
        currentParentId = ""
        currentType = ""
        currentPage = 0
        totalCount = 0
        hasMoreData = true
    }
}
