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
 * 详情页 ViewModel
 * 
 * 职责：
 * - 加载媒体详情
 * - 加载季/集列表
 * - 切换收藏状态
 */
class DetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)

    // === 媒体详情 ===
    var mediaInfo by mutableStateOf<BaseItemDto?>(null)
        private set

    // === 剧集数据 ===
    var seasons by mutableStateOf<List<BaseItemDto>?>(null)
        private set
    var episodes by mutableStateOf<Map<String, List<BaseItemDto>>>(emptyMap())
        private set

    // === 继续观看（剧集）===
    var resumeItem by mutableStateOf<BaseItemDto?>(null)
        private set

    // === 加载状态 ===
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    /**
     * 加载媒体详情
     */
    fun loadMediaInfo(mediaId: String) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            mediaInfo = null

            try {
                val info = withContext(Dispatchers.IO) {
                    repository.getMediaInfo(mediaId)
                }
                mediaInfo = info
            } catch (e: Exception) {
                errorMessage = e.message
                mediaInfo = null
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * 加载季列表
     */
    fun loadSeasons(seriesId: String) {
        viewModelScope.launch {
            try {
                seasons = withContext(Dispatchers.IO) {
                    repository.getSeasonList(seriesId)
                }
            } catch (e: Exception) {
                seasons = emptyList()
            }
        }
    }

    /**
     * 加载剧集列表
     */
    fun loadEpisodes(seasonId: String) {
        // 如果已加载，不重复请求
        if (episodes.containsKey(seasonId)) return

        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    repository.getSeriesList(seasonId)
                }
                episodes = episodes + (seasonId to list)
            } catch (e: Exception) {
                episodes = episodes + (seasonId to emptyList())
            }
        }
    }

    /**
     * 加载继续观看项（用于剧集）
     */
    fun loadResumeItem(seriesId: String) {
        viewModelScope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    repository.getResumeItems(seriesId)
                }
                resumeItem = items.firstOrNull()
            } catch (e: Exception) {
                resumeItem = null
            }
        }
    }

    /**
     * 获取下一集
     */
    fun getNextEpisode(seriesId: String, onResult: (BaseItemDto?) -> Unit) {
        viewModelScope.launch {
            try {
                val nextUp = withContext(Dispatchers.IO) {
                    repository.getShowsNextUp(seriesId)
                }
                if (nextUp.isNotEmpty()) {
                    onResult(nextUp.first())
                } else {
                    val all = withContext(Dispatchers.IO) {
                        repository.getSeriesList(seriesId)
                    }
                    onResult(all.firstOrNull())
                }
            } catch (e: Exception) {
                onResult(null)
            }
        }
    }

    /**
     * 切换收藏状态
     */
    fun toggleFavorite(itemId: String, isFavorite: Boolean, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            try {
                val success = if (isFavorite) {
                    repository.addToFavorites(itemId)
                } else {
                    repository.removeFromFavorites(itemId)
                }

                // 更新本地状态
                if (success && mediaInfo?.id == itemId) {
                    val currentUserData = mediaInfo?.userData
                    mediaInfo = mediaInfo?.copy(
                        userData = currentUserData?.copy(isFavorite = isFavorite)
                    )
                }

                onResult(success)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    /**
     * 清空数据（用于切换详情时）
     */
    fun clear() {
        mediaInfo = null
        seasons = null
        episodes = emptyMap()
        resumeItem = null
    }

    // === Suspend 版本的方法（供 Screen 直接调用）===

    /**
     * 获取季列表
     */
    suspend fun getSeasonList(seriesId: String): List<BaseItemDto> {
        return withContext(Dispatchers.IO) {
            repository.getSeasonList(seriesId)
        }
    }

    /**
     * 获取剧集列表
     */
    suspend fun getSeriesList(seriesId: String): List<BaseItemDto> {
        return withContext(Dispatchers.IO) {
            repository.getSeriesList(seriesId)
        }
    }

    /**
     * 获取继续观看项
     */
    suspend fun getResumeItem(seriesId: String): BaseItemDto? {
        return withContext(Dispatchers.IO) {
            repository.getResumeItems(seriesId).firstOrNull()
        }
    }
}
