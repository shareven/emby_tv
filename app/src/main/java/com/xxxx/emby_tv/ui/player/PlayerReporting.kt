package com.xxxx.emby_tv.ui.player

import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.util.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 播放器报告管理
 * 
 * 负责向 Emby 服务器报告播放状态
 */
object PlayerReporting {
    private const val TAG = "PlayerReporting"

    /**
     * 报告播放进度
     */
    suspend fun reportProgress(
        repository: EmbyRepository,
        mediaId: String,
        media: MediaDto,
        position: Long,
        selectedSubtitleIndex: Int,
        selectedAudioIndex: Int,
        isPaused: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            try {
                val ticks = position * 10000
                val playMethod = Utils.determinePlayMethod(media)
                val mediaSources = media.mediaSources
                val firstSource = mediaSources?.firstOrNull()
                val runTimeTicks = firstSource?.runTimeTicks ?: 0L
                val mediaSourceId = firstSource?.id ?: ""

                val body = mapOf(
                    "VolumeLevel" to 100,
                    "IsMuted" to false,
                    "IsPaused" to isPaused,
                    "RepeatMode" to "RepeatNone",
                    "PositionTicks" to ticks,
                    "PlaybackStartTimeTicks" to System.currentTimeMillis() * 10000,
                    "SubtitleStreamIndex" to selectedSubtitleIndex,
                    "AudioStreamIndex" to selectedAudioIndex,
                    "BufferedRanges" to emptyList<Any>(),
                    "SeekableRanges" to listOf(
                        mapOf("start" to 0, "end" to runTimeTicks)
                    ),
                    "PlayMethod" to playMethod,
                    "PlaySessionId" to (media.playSessionId ?: ""),
                    "MediaSourceId" to mediaSourceId,
                    "CanSeek" to true,
                    "ItemId" to mediaId,
                    "EventName" to "timeupdate"
                )

                repository.reportPlaybackProgress(body)
            } catch (e: Exception) {
                ErrorHandler.logError(TAG, "报告播放进度失败", e)
            }
        }
    }

    /**
     * 报告播放开始
     */
    suspend fun reportPlaying(
        repository: EmbyRepository,
        mediaId: String,
        media: MediaDto,
        position: Long,
        selectedSubtitleIndex: Int,
        selectedAudioIndex: Int
    ) {
        withContext(Dispatchers.IO) {
            try {
                val ticks = position * 10000
                val playMethod = Utils.determinePlayMethod(media)
                val mediaSources = media.mediaSources
                val firstSource = mediaSources?.firstOrNull()
                val runTimeTicks = firstSource?.runTimeTicks ?: 0L
                val mediaSourceId = firstSource?.id ?: ""

                val body = mapOf(
                    "VolumeLevel" to 100,
                    "IsMuted" to false,
                    "IsPaused" to false,
                    "RepeatMode" to "RepeatNone",
                    "Shuffle" to false,
                    "SubtitleOffset" to 0,
                    "PlaybackRate" to 1,
                    "MaxStreamingBitrate" to 200000000,
                    "PositionTicks" to ticks,
                    "PlaybackStartTimeTicks" to System.currentTimeMillis() * 10000,
                    "SubtitleStreamIndex" to selectedSubtitleIndex,
                    "AudioStreamIndex" to selectedAudioIndex,
                    "BufferedRanges" to emptyList<Any>(),
                    "SeekableRanges" to listOf(
                        mapOf("start" to 0, "end" to runTimeTicks)
                    ),
                    "PlayMethod" to playMethod,
                    "PlaySessionId" to (media.playSessionId ?: ""),
                    "MediaSourceId" to mediaSourceId,
                    "CanSeek" to true,
                    "ItemId" to mediaId
                )

                repository.playing(body)
                ErrorHandler.logDebug(TAG, "播放开始报告成功")
            } catch (e: Exception) {
                ErrorHandler.logError(TAG, "报告播放开始失败", e)
            }
        }
    }

    /**
     * 报告播放停止
     */
    suspend fun reportStopped(
        repository: EmbyRepository,
        mediaId: String,
        media: MediaDto,
        position: Long,
        selectedSubtitleIndex: Int,
        selectedAudioIndex: Int
    ) {
        withContext(Dispatchers.IO) {
            try {
                val ticks = position * 10000
                val mediaSources = media.mediaSources
                val firstSource = mediaSources?.firstOrNull()
                val runTimeTicks = firstSource?.runTimeTicks ?: 0L
                val mediaSourceId = firstSource?.id ?: ""

                val body = mapOf(
                    "VolumeLevel" to 100,
                    "IsMuted" to false,
                    "IsPaused" to true,
                    "RepeatMode" to "RepeatNone",
                    "Shuffle" to false,
                    "SubtitleOffset" to 0,
                    "PlaybackRate" to 1,
                    "MaxStreamingBitrate" to 200000000,
                    "PositionTicks" to ticks,
                    "PlaybackStartTimeTicks" to System.currentTimeMillis() * 10000,
                    "SubtitleStreamIndex" to selectedSubtitleIndex,
                    "AudioStreamIndex" to selectedAudioIndex,
                    "BufferedRanges" to emptyList<Any>(),
                    "SeekableRanges" to listOf(
                        mapOf("start" to 0, "end" to runTimeTicks)
                    ),
                    "PlayMethod" to Utils.determinePlayMethod(media),
                    "PlaySessionId" to (media.playSessionId ?: ""),
                    "MediaSourceId" to mediaSourceId,
                    "CanSeek" to true,
                    "ItemId" to mediaId,
                    "EventName" to "Stopped"
                )

                repository.stopped(body)
                ErrorHandler.logDebug(TAG, "播放停止报告成功")
            } catch (e: Exception) {
                ErrorHandler.logError(TAG, "报告播放停止失败", e)
            }
        }
    }
}
