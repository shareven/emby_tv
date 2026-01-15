package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.BaseItemDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 媒体库 ViewModel
 * 
 * 职责：
 * - 加载媒体库列表
 * - 支持分类筛选
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)

    // === 媒体库数据 ===
    var libraryItems by mutableStateOf<List<BaseItemDto>?>(null)
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
     * 加载媒体库列表
     */
    fun loadItems(parentId: String, type: String) {
        // 如果参数没变且已有数据，不重复加载
        if (parentId == currentParentId && type == currentType && libraryItems != null) {
            return
        }

        currentParentId = parentId
        currentType = type

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            libraryItems = null // 显示 loading

            try {
                val items = withContext(Dispatchers.IO) {
                    repository.getLibraryList(parentId, type)
                }
                libraryItems = items
            } catch (e: Exception) {
                errorMessage = e.message
                libraryItems = emptyList()
            } finally {
                isLoading = false
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
    }
}
