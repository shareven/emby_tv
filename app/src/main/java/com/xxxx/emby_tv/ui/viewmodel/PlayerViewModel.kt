package com.xxxx.emby_tv.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放器 ViewModel
 * 
 * 职责：
 * - 获取播放信息
 * - 管理播放状态
 * - 上报播放进度
 * - 管理音轨/字幕轨道
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = EmbyRepository.getInstance(application)

    // === 播放信息 ===
    var playbackInfo by mutableStateOf<MediaDto?>(null)
        private set
    var currentMediaSource by mutableStateOf<MediaSourceInfoDto?>(null)
        private set

    // === 轨道信息 ===
    var audioTracks by mutableStateOf<List<MediaStreamDto>>(emptyList())
        private set
    var subtitleTracks by mutableStateOf<List<MediaStreamDto>>(emptyList())
        private set
    var selectedAudioIndex by mutableStateOf<Int?>(null)
        private set
    var selectedSubtitleIndex by mutableStateOf<Int?>(null)
        private set

    // === 播放会话 ===
    var playSessionId: String? = null
        private set
    var mediaSourceId: String? = null
        private set

    // === 加载状态 ===
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // === 当前播放的媒体 ID ===
    var currentMediaId: String = ""
        private set

    /**
     * 加载播放信息
     */
    fun loadPlaybackInfo(
        mediaId: String,
        startPosition: Long,
        audioIndex: Int? = null,
        subtitleIndex: Int? = null,
        disableHevc: Boolean = false,
        onSuccess: (MediaSourceInfoDto?) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        currentMediaId = mediaId

        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                val info = withContext(Dispatchers.IO) {
                    repository.getPlaybackInfo(
                        mediaId,
                        startPosition,
                        audioIndex,
                        subtitleIndex,
                        disableHevc
                    )
                }

                playbackInfo = info
                currentMediaSource = info.mediaSources?.firstOrNull()

                // playSessionId 在 MediaDto 级别
                playSessionId = info.playSessionId
                
                currentMediaSource?.let { source ->
                    mediaSourceId = source.id

                    // 解析轨道信息
                    val streams = source.mediaStreams ?: emptyList()
                    audioTracks = streams.filter { it.type == "Audio" }
                    subtitleTracks = streams.filter { it.type == "Subtitle" }

                    // 设置默认选中的轨道
                    selectedAudioIndex = audioIndex ?: source.defaultAudioStreamIndex
                    selectedSubtitleIndex = subtitleIndex ?: source.defaultSubtitleStreamIndex
                }

                withContext(Dispatchers.Main) {
                    isLoading = false
                    onSuccess(currentMediaSource)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMessage = e.message
                    onError(e.message ?: "加载播放信息失败")
                }
            }
        }
    }

    /**
     * 上报开始播放
     */
    fun reportPlaying(
        mediaId: String,
        positionTicks: Long = 0
    ) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                val body = buildPlayingBody(mediaId, positionTicks)
                repository.playing(body)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 上报播放进度
     */
    fun reportProgress(
        mediaId: String,
        positionTicks: Long,
        isPaused: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                val body = buildProgressBody(mediaId, positionTicks, isPaused)
                repository.reportPlaybackProgress(body)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 上报停止播放
     */
    fun reportStopped(
        mediaId: String,
        positionTicks: Long
    ) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            try {
                val body = buildStoppedBody(mediaId, positionTicks)
                repository.stopped(body)

                // 停止活动编码
                playSessionId?.let { sessionId ->
                    repository.stopActiveEncodings(sessionId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 更新选中的轨道
     */
    fun updateSelectedTracks(audioIndex: Int?, subtitleIndex: Int?) {
        selectedAudioIndex = audioIndex
        selectedSubtitleIndex = subtitleIndex
    }

    /**
     * 重新加载播放信息（用于切换轨道后）
     */
    fun reloadWithTracks(
        startPosition: Long,
        audioIndex: Int?,
        subtitleIndex: Int?,
        disableHevc: Boolean = false,
        onSuccess: (MediaSourceInfoDto?) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        loadPlaybackInfo(
            currentMediaId,
            startPosition,
            audioIndex,
            subtitleIndex,
            disableHevc,
            onSuccess,
            onError
        )
    }

    /**
     * 清理播放状态
     */
    fun clear() {
        playbackInfo = null
        currentMediaSource = null
        audioTracks = emptyList()
        subtitleTracks = emptyList()
        selectedAudioIndex = null
        selectedSubtitleIndex = null
        playSessionId = null
        mediaSourceId = null
        currentMediaId = ""
    }

    // === 构建请求体 ===

    private fun buildPlayingBody(mediaId: String, positionTicks: Long): Map<String, Any?> {
        return mapOf(
            "ItemId" to mediaId,
            "MediaSourceId" to mediaSourceId,
            "PlaySessionId" to playSessionId,
            "PositionTicks" to positionTicks,
            "CanSeek" to true,
            "IsPaused" to false,
            "IsMuted" to false,
            "PlayMethod" to getPlayMethod(),
            "AudioStreamIndex" to selectedAudioIndex,
            "SubtitleStreamIndex" to selectedSubtitleIndex
        )
    }

    private fun buildProgressBody(
        mediaId: String,
        positionTicks: Long,
        isPaused: Boolean
    ): Map<String, Any?> {
        return mapOf(
            "ItemId" to mediaId,
            "MediaSourceId" to mediaSourceId,
            "PlaySessionId" to playSessionId,
            "PositionTicks" to positionTicks,
            "CanSeek" to true,
            "IsPaused" to isPaused,
            "IsMuted" to false,
            "PlayMethod" to getPlayMethod(),
            "AudioStreamIndex" to selectedAudioIndex,
            "SubtitleStreamIndex" to selectedSubtitleIndex
        )
    }

    private fun buildStoppedBody(mediaId: String, positionTicks: Long): Map<String, Any?> {
        return mapOf(
            "ItemId" to mediaId,
            "MediaSourceId" to mediaSourceId,
            "PlaySessionId" to playSessionId,
            "PositionTicks" to positionTicks,
            "PlayMethod" to getPlayMethod()
        )
    }

    private fun getPlayMethod(): String {
        return if (currentMediaSource?.transcodingUrl != null) {
            "Transcode"
        } else {
            "DirectPlay"
        }
    }
}
