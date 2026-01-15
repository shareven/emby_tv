package com.xxxx.emby_tv.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.MimeTypes
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.compose.ui.res.stringResource
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils
import com.xxxx.emby_tv.Utils.formatDuration
import com.xxxx.emby_tv.Utils.formatKbps
import com.xxxx.emby_tv.Utils.formatMbps
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.MediaDto
import com.xxxx.emby_tv.data.model.MediaSourceInfoDto
import com.xxxx.emby_tv.data.model.MediaStreamDto
import com.xxxx.emby_tv.data.model.SessionDto
import com.xxxx.emby_tv.ui.components.PlayerMenu
import com.xxxx.emby_tv.ui.components.PlayerOverlay
import com.xxxx.emby_tv.ui.components.getAudioTrack
import com.xxxx.emby_tv.ui.components.getVideoTrack
import com.xxxx.emby_tv.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 播放器界面（Screen）- 使用 PlayerViewModel
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    mediaId: String,
    playbackPositionTicks: Long = 0L,
    onPlaybackStateChanged: (isPlaying: Boolean) -> Unit = {},
    onBack: () -> Unit,
    onNavigateToPlayer: (BaseItemDto) -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    
    // 获取 Repository 用于直接访问
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""
    val apiKey = repository.apiKey ?: ""

    // 使用 rememberCoroutineScope() 替代 GlobalScope，确保协程可取消
    val scope = rememberCoroutineScope()

    // 状态变量
    var isPlaying by remember { mutableStateOf(false) }
    var isShowInfo by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<MediaDto>(MediaDto()) }
    var mediaInfo by remember { mutableStateOf<BaseItemDto>(BaseItemDto()) }
    var session by remember { mutableStateOf<SessionDto?>(null) }
    var hasReportedPlaying by remember { mutableStateOf(false) }

    // 收藏状态 - 在播放页层面管理，菜单打开/关闭时保持状态
    var isFavorite by remember { mutableStateOf(false) }

    var subtitleTracks by remember { mutableStateOf<List<MediaStreamDto>>(emptyList()) }
    var selectedSubtitleIndex by remember { mutableStateOf(-99) }
    var audioTracks by remember { mutableStateOf<List<MediaStreamDto>>(emptyList()) }
    var selectedAudioIndex by remember { mutableStateOf(-1) }
    var videoUrl by remember { mutableStateOf<String?>(null) }

    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var buffered by remember { mutableStateOf(0L) }

    var playbackCorrection by remember { mutableStateOf(0) } // 0: off, 1: server transcode
    var playMode by remember { mutableStateOf(0) } // 0: list loop, 1: single loop, 2: no loop
    var endedHandled by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    
    // 继续播放/从头开始 按钮状态
    var showResumeButtons by remember { mutableStateOf(playbackPositionTicks > 0) }
    var resumeButtonsShownOnce by remember { mutableStateOf(false) }

    // 用于跟踪是否已经尝试过转码回退
    var hasTriedTranscodeFallback by remember { mutableStateOf(false) }
    var currentTracks by remember { mutableStateOf<Tracks?>(null) }


    val playbackInfoFailText = stringResource(R.string.failed_get_playback_info)
    var playbackTrigger by remember { mutableStateOf(0) }


    // TrackSelector
    val trackSelector = remember {
        DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredTextLanguage("zh")
            )
        }
    }

    val renderersFactory = DefaultRenderersFactory(context).apply {
        // 在 1.5.1 中，这确保了渲染器能够处理复杂的字幕样式
        setEnableDecoderFallback(true)
    }

    // ExoPlayer
    val player = remember {
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build().apply {

                playWhenReady = true // Ensure it tries to play immediately
            }
    }

    var isBuffering by remember { mutableStateOf(true) } // Start true assuming we wait for load

    // Long press state
    var leftKeyDownTime by remember { mutableStateOf(0L) }
    var rightKeyDownTime by remember { mutableStateOf(0L) }

    fun reportStopped() {
        // 播放结束，发送停止报告
        val ticks = position * 10000

        // 提取 MediaSource 逻辑，避免在 mapOf 内部写过于复杂的嵌套
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
       
        // 2. 执行停止报告
        scope.launch(Dispatchers.IO) {
            try {
                repository.stopped(body)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    // 即使暂停播放也不会熄屏
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previous
        }
    }

    LaunchedEffect(leftKeyDownTime) {
        if (leftKeyDownTime > 0) {
            delay(500)
            while (isActive) {
                val newPos = (player.currentPosition - 30000).coerceAtLeast(0)
                player.seekTo(newPos)
                position = newPos // Update progress bar
                if (!player.isPlaying) {
                    player.play() // Auto-play if paused
                }
                delay(200)
            }
        }
    }

    LaunchedEffect(rightKeyDownTime) {
        if (rightKeyDownTime > 0) {
            delay(500)
            while (isActive) {
                val newPos = (player.currentPosition + 30000).coerceAtMost(duration)
                player.seekTo(newPos)
                position = newPos // Update progress bar
                if (!player.isPlaying) {
                    player.play() // Auto-play if paused
                }
                delay(200)
            }
        }
    }


    // 实现转码回退逻辑
    fun fallbackToServerTranscode() {
        if (hasTriedTranscodeFallback) {

            return
        }

        hasTriedTranscodeFallback = true
        Log.d("PlayerScreen", "开始执行转码回退逻辑")

        // 使用 rememberCoroutineScope() 替代 GlobalScope，确保协程可取消
        scope.launch(Dispatchers.IO) {
            val requestAudioIndex = if (selectedAudioIndex <= -1) null else selectedAudioIndex
            val requestSubtitleIndex =
                if (selectedSubtitleIndex <= -1) null else selectedSubtitleIndex

            try {
                if (media.mediaSources?.firstOrNull()?.transcodingUrl != null) {
                    repository.stopActiveEncodings(
                        media.playSessionId
                    )
                }
                val mediaResult = repository.getPlaybackInfo(
                    mediaId,
                    if (position > 0) position * 10000 else playbackPositionTicks,
                    requestAudioIndex,
                    requestSubtitleIndex,
                    true
                )

                if (mediaResult.mediaSources.isNullOrEmpty()) {
                    Log.e("PlayerScreen", "转码回退失败：无法获取播放信息")
                    return@launch
                }

                // 直接访问mediaSources属性
                val source = mediaResult.mediaSources.firstOrNull()

                // 获取转码URL - 优先使用直链
                val path = source?.directStreamUrl ?: source?.transcodingUrl

                if (path != null) {
                    val newVideoUrl = "${serverUrl}/emby$path"

                    // 切换到主线程更新UI
                    withContext(Dispatchers.Main) {
                        Log.d("PlayerScreen", "转码回退成功，更新视频URL")
                        videoUrl = newVideoUrl

                        media = mediaResult

                        //重要步骤
                        player.stop()

                        // 重新设置播放源
                        val mediaItem = MediaItem.Builder()
                            .setUri(newVideoUrl)
                            .build()

                        player.setMediaItem(mediaItem, position)
                        player.prepare()
                        player.playWhenReady = true
                    }
                } else {
                    Log.e("PlayerScreen", "转码回退失败：没有找到转码URL")
                }
            } catch (e: Exception) {
                Log.e("PlayerScreen", "转码回退过程中出现异常: ${e.message}", e)
            }
        }
    }


    // 加载设置
    LaunchedEffect(Unit) {
        try {
            val prefs = context.getSharedPreferences("emby_tv_prefs", Context.MODE_PRIVATE)
            playbackCorrection = prefs.getInt("playback_correction", 0)
            playMode = prefs.getInt("play_mode", 0)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 提供一个函数供 UI 调用切换（例如点击列表时调用）
    fun changeTrack(audioIndex: Int, subIndex: Int) {
        val needChange =
            (selectedAudioIndex != audioIndex && audioIndex > -1) || (selectedSubtitleIndex != subIndex && subIndex > -1)
        selectedAudioIndex = audioIndex
        selectedSubtitleIndex = subIndex
        hasTriedTranscodeFallback = false
        if (needChange&&!(media.mediaSources?.firstOrNull()?.supportsDirectPlay?:false)) playbackTrigger++ // 只有手动修改时，才递增触发器，重启协程
    }

    // 数据加载逻辑
    LaunchedEffect(mediaId, playbackCorrection, playbackTrigger) {
        //  这里的逻辑只会运行一次（初始化时）或者在手动递增 trigger 时运行
        val requestAudioIndex = if (selectedAudioIndex <= -1) null else selectedAudioIndex
        val requestSubtitleIndex = if (selectedSubtitleIndex <= -1) null else selectedSubtitleIndex

        try {
            if (media.mediaSources?.firstOrNull()?.transcodingUrl != null) {
                repository.stopActiveEncodings(
                    media.playSessionId
                )
            }
            val mediaResult = repository.getPlaybackInfo(
                mediaId,
                if (position > 0) position * 10000 else playbackPositionTicks,
                requestAudioIndex,
                requestSubtitleIndex,
                hasTriedTranscodeFallback || playbackCorrection == 1
            )

            if (mediaResult.mediaSources.isNullOrEmpty()) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        playbackInfoFailText,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                return@LaunchedEffect
            }

            // 直接使用 mediaResult 对象，赋值给状态
            media = mediaResult
            val mediaInfoResult = repository.getMediaInfo(mediaId)
            mediaInfo = mediaInfoResult

            // 更新收藏状态
            isFavorite = mediaInfoResult.userData?.isFavorite == true

            val source = mediaResult.mediaSources.firstOrNull()
            val streams = source?.mediaStreams ?: emptyList()

            subtitleTracks = streams.filter { s ->
                s.type == "Subtitle"
            }

            audioTracks = streams.filter { s ->
                s.type == "Audio"
            }

            if (selectedAudioIndex == -1) {
                selectedAudioIndex = source?.defaultAudioStreamIndex ?: -1
            }
            if (selectedSubtitleIndex == -99) {
                selectedSubtitleIndex = source?.defaultSubtitleStreamIndex ?: -1
            }

            // 构建 URL (参考 Flutter 逻辑)
            var path = source?.directStreamUrl

            // 如果强制转码(correction=1) 或者 没有直链(path=null)，则尝试使用转码链接
            if (playbackCorrection == 1 || path == null) {
                val transcodeUrl = source?.transcodingUrl
                if (transcodeUrl != null) {
                    path = transcodeUrl
                }
            }

            if (path != null) {
                // 音轨和字幕修正逻辑
                // 1. 判断音轨是否为物理默认
                var isPhysicalDefaultAudio = false
                try {
                    val currentAudio = audioTracks.firstOrNull { s ->
                        s.index == selectedAudioIndex
                    }
                    isPhysicalDefaultAudio = currentAudio?.isDefault == true
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // 2. 判断字幕是否为内置且非物理默认
                var isInternalNonDefaultSubtitle = false
                try {
                    val currentSub = subtitleTracks.firstOrNull { s ->
                        s.index == selectedSubtitleIndex
                    }
                    val isInternal = currentSub?.isExternal != true
                    val isDefaultSub = currentSub?.isDefault == true
                    if (isInternal == true && isDefaultSub != true) {
                        isInternalNonDefaultSubtitle = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                if (!isPhysicalDefaultAudio || isInternalNonDefaultSubtitle) {
                    if (path.contains("original.")) {
                        val regex = Regex("""original\.(\w+)""")
                        path = regex.replace(path) { matchResult ->
                            val extension = matchResult.groupValues[1].ifEmpty { "mkv" }
                            "stream.$extension"
                        }
                    }
                    path = path.replace(Regex("""[&?]AudioStreamIndex=\d+"""), "")
                    path =
                        if (path.contains("?")) "${path}&AudioStreamIndex=$selectedAudioIndex" else "${path}?AudioStreamIndex=$selectedAudioIndex"

                    path = path.replace(Regex("""[&?]SubtitleStreamIndex=\d+"""), "")
                    path = "${path}&SubtitleStreamIndex=$selectedSubtitleIndex"
                }
            }

            videoUrl = if (path != null) "${serverUrl}/emby$path" else null
            hasReportedPlaying = false
        } catch (e: Exception) {
            e.printStackTrace()

        }
    }

    // 设置 MediaItem 和 字幕
    LaunchedEffect(videoUrl) {
        if (videoUrl != null) {
            val source = media.mediaSources?.firstOrNull()
            val mediaSourceId = source?.id ?: ""

            val subtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()

            // 添加所有字幕 (External + Internal extracted)
            // Emby 允许通过 API 提取内置字幕，这能避免转码流丢失字幕的问题
            subtitleTracks.forEach { track ->
                val index = track.index ?: 0
                val codec = track.codec?.lowercase() ?: "srt"
                val lang = track.language ?: "und"
                val label = track.displayTitle ?: "Subtitle"

                // 关键修改：基于Emby返回的数据，支持所有可外部访问的字幕
                val isLoadableSubtitle = track.supportsExternalStream == true || track.isExternal == true
               
                if (isLoadableSubtitle) {
                    // 构建字幕 URL
                    // 即使是内置字幕，也可以通过此 API 提取 (Stream.srt, Stream.vtt 等)
                    val subUrl =
                        "${serverUrl}/emby/Videos/$mediaId/$mediaSourceId/Subtitles/$index/Stream.$codec?api_key=${apiKey}"

                    val mimeType = when {
                        codec.contains("ass") || codec.contains("ssa") -> MimeTypes.TEXT_SSA
                        codec.contains("vtt") -> MimeTypes.TEXT_VTT
                        else -> MimeTypes.APPLICATION_SUBRIP
                    }

                    val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                        .setId(index.toString()) // 关键：设置 ID 与 Emby Index 一致，方便匹配
                        .setMimeType(mimeType)
                        .setLanguage(lang)
                        .setLabel(label)
                        .setSelectionFlags(if (index == selectedSubtitleIndex) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                    subtitleConfigs.add(config)

                 } 
            }

           
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(videoUrl)
                .setSubtitleConfigurations(subtitleConfigs)

            // 计算起始位置：优先级为播放进度 > 参数传入位置 > 0
            val startPositionMs = if (position > 0) {
                position
            } else if (playbackPositionTicks > 0) {
                playbackPositionTicks / 10000
            } else {
                0L
            }

            val mediaItem = mediaItemBuilder.build()

            // 直接在setMediaItem中设置跳转位置（Media3 API支持）
            player.setMediaItem(mediaItem, startPositionMs)

            if (startPositionMs > 0) {
                Log.d("Player", "Setting initial position via setMediaItem: $startPositionMs ms")
            }

            player.prepare()
            player.playWhenReady = true
        }
    }

    // 更新选中字幕
    // LaunchedEffect(selectedSubtitleIndex, subtitleTracks, currentTracks) {
    //     if (subtitleTracks.isEmpty()) return@LaunchedEffect

    //     // 如果是 -1，关闭字幕逻辑
    //     if (selectedSubtitleIndex == -1) {
    //         player.trackSelectionParameters = player.trackSelectionParameters
    //             .buildUpon()
    //             .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
    //             .build()
    //         return@LaunchedEffect
    //     }

    //     // Fix: Use find to match by Index, not list index
    //     val targetTrack = subtitleTracks.find { it.index == selectedSubtitleIndex }
    //         ?: run {
    //             Log.w(
    //                 "Player",
    //                 "Target subtitle index $selectedSubtitleIndex not found in metadata"
    //             )
    //             return@LaunchedEffect
    //         }

    //     val targetOrdinalIndex = subtitleTracks.indexOf(targetTrack)
    //     val targetLabel = targetTrack.displayTitle
    //     val targetIndex = targetTrack.index?.toString() ?: ""
    //     val targetLanguage = targetTrack.language?.lowercase()

    //     // 开启文本轨道
    //     var parametersBuilder = player.trackSelectionParameters.buildUpon()
    //         .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)

    //     // 1.5.1 推荐做法：先尝试通过首选语言/标签匹配，增加成功率
    //     if (targetLanguage != null) {
    //         parametersBuilder =
    //             parametersBuilder.setPreferredTextLanguage(targetLanguage)
    //     }

    //     // 执行强制覆盖逻辑
    //     // 使用 currentTracks 进行匹配
    //     val groups = currentTracks?.groups ?: player.currentTracks.groups
    //     val trackGroups = groups.filter { it.type == C.TRACK_TYPE_TEXT }
    //     var isMatched = false

    //     Log.d(
    //         "Player",
    //         "Subtitle Selection: Target [Index:$targetIndex, Label:$targetLabel, Lang:$targetLanguage, Ordinal:$targetOrdinalIndex]"
    //     )

    //     // // 策略1：遍历所有 Text Track Group 进行多重匹配
    //     // outer@ for (group in trackGroups) {
    //     //     for (i in 0 until group.length) {
    //     //         val format = group.getTrackFormat(i)
    //     //         val formatId = format.id
    //     //         val formatLabel = format.label
    //     //         val formatLang = format.language?.lowercase()

    //     //         // 1. ID 精确匹配 (Emby Index vs ExoPlayer ID)
    //     //         if (formatId == targetIndex) {
    //     //             parametersBuilder.setOverrideForType(
    //     //                 TrackSelectionOverride(
    //     //                     group.mediaTrackGroup,
    //     //                     i
    //     //                 )
    //     //             )
    //     //             isMatched = true
    //     //             Log.d("Player", "Matched subtitle by ID: $formatId")
    //     //             break@outer
    //     //         }

    //     //         // 2. Label 精确匹配
    //     //         if (formatLabel != null && targetLabel != null && formatLabel.equals(
    //     //                 targetLabel,
    //     //                 ignoreCase = true
    //     //             )
    //     //         ) {
    //     //             parametersBuilder.setOverrideForType(
    //     //                 TrackSelectionOverride(
    //     //                     group.mediaTrackGroup,
    //     //                     i
    //     //                 )
    //     //             )
    //     //             isMatched = true
    //     //             Log.d("Player", "Matched subtitle by Label (Exact): $formatLabel")
    //     //             break@outer
    //     //         }

    //     //         // 3. Label 模糊匹配 (包含关系)
    //     //         if (formatLabel != null && targetLabel != null && (formatLabel.contains(
    //     //                 targetLabel,
    //     //                 true
    //     //             ) || targetLabel.contains(formatLabel, true))
    //     //         ) {
    //     //             parametersBuilder.setOverrideForType(
    //     //                 TrackSelectionOverride(
    //     //                     group.mediaTrackGroup,
    //     //                     i
    //     //                 )
    //     //             )
    //     //             isMatched = true
    //     //             Log.d(
    //     //                 "Player",
    //     //                 "Matched subtitle by Label (Fuzzy): Player=$formatLabel / Target=$targetLabel"
    //     //             )
    //     //             break@outer
    //     //         }

    //     //         // 4. 语言匹配 (Language Code) - 作为较弱的匹配条件
    //     //         // 注意：如果有多个同语言轨道，这里可能会匹配到第一个，所以要在 Label 匹配之后
    //     //         if (targetLanguage != null && formatLang == targetLanguage) {
    //     //             // 此时不立即 break，而是先标记，继续寻找是否有 Label 匹配的更优解
    //     //             // 但为了简单，如果上面都没匹配到，我们暂时信任语言匹配
    //     //             // 这里做一个简单的优化：如果还没找到 Match，记录这个作为备选
    //     //             // 实际实现：这通常由 exoPlayer 的 setPreferredTextLanguage 处理了，但为了强制覆盖，我们也可以做。
    //     //             // 风险：可能有多个 "English"，选错了一个 Forced 轨道。
    //     //             // 暂时跳过纯语言强制匹配，交给 setPreferredTextLanguage，或者仅 Log
    //     //             Log.d("Player", "Potential match by Language: $formatLang")
    //     //         }
    //     //     }
    //     // }

    //     // 策略2：顺序匹配 (应对 Emby ID 偏移问题，如 Index:3 -> ID:4)
    //     if (!isMatched && targetOrdinalIndex != -1) {
    //         var trackCounter = 0
    //         outerOrdinal@ for (group in trackGroups) {
    //             for (i in 0 until group.length) {
    //                 val format = group.getTrackFormat(i)
    //                 // 假设 ExoPlayer 解析出的文本轨道顺序与 Emby 元数据顺序一致
    //                 // 注意：这假设 trackGroups 中只包含我们需要对应的字幕轨道
    //                 if (trackCounter == targetOrdinalIndex) {
    //                     parametersBuilder.setOverrideForType(
    //                         TrackSelectionOverride(group.mediaTrackGroup, i)
    //                     )
    //                     isMatched = true
    //                     Log.d(
    //                         "Player",
    //                         "Matched subtitle by Ordinal: MetadataIndex=$targetIndex -> ExoOrdinal=$trackCounter (Label: ${format.label})"
    //                     )
    //                     break@outerOrdinal
    //                 }
    //                 trackCounter++
    //             }

    //         }
    //     }

    //     player.trackSelectionParameters = parametersBuilder.build()

    //     // 调试输出
    //     if (!isMatched) {
    //         Log.w("Player", "Unable to match subtitle target: $targetLabel")
    //         // 打印现有轨道以供调试
    //         Log.d("Player", "--- Dump Available Text Tracks ---")
    //         trackGroups.forEachIndexed { gi, group ->
    //             for (i in 0 until group.length) {
    //                 val f = group.getTrackFormat(i)
    //                 Log.d(
    //                     "Player",
    //                     "Group[$gi] Track[$i]: ID=${f.id}, Label=${f.label}, Lang=${f.language}"
    //                 )
    //             }
    //         }
    //         Log.d("Player", "----------------------------------")
    //     }
    // }

    // 更新选中音频
    LaunchedEffect(selectedAudioIndex, audioTracks, currentTracks) {
        if (audioTracks.isEmpty()) return@LaunchedEffect
        if (selectedAudioIndex <= -1) return@LaunchedEffect

        val targetTrack = audioTracks.find { it.index == selectedAudioIndex }
            ?: run {
                Log.w(
                    "Player",
                    "Target audio index $selectedAudioIndex not found in metadata"
                )
                return@LaunchedEffect
            }

        val targetOrdinalIndex = audioTracks.indexOf(targetTrack)
        val targetLabel = targetTrack.displayTitle
        val targetIndex = targetTrack.index?.toString() ?: ""
        val targetLanguage = targetTrack.language?.lowercase()

        var parametersBuilder = player.trackSelectionParameters.buildUpon()

        // 优先语言（如果有）
        if (targetLanguage != null) {
            parametersBuilder = parametersBuilder.setPreferredAudioLanguage(targetLanguage)
        }

        val groups = currentTracks?.groups ?: player.currentTracks.groups
        val trackGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        var isMatched = false

        Log.d(
            "Player",
            "Audio Selection: Target [Index:$targetIndex, Label:$targetLabel, Lang:$targetLanguage, Ordinal:$targetOrdinalIndex]"
        )

        // 策略1：遍历所有 Audio Track Group 进行多重匹配
        outer@ for (group in trackGroups) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                val formatId = format.id
                val formatLabel = format.label
                val formatLang = format.language?.lowercase()

                // 1. ID 精确匹配
                if (formatId == targetIndex) {
                    parametersBuilder.setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, i)
                    )
                    isMatched = true
                    Log.d("Player", "Matched audio by ID: $formatId")
                    break@outer
                }

                // 2. Label 精确匹配
                if (formatLabel != null && targetLabel != null && formatLabel.equals(
                        targetLabel,
                        ignoreCase = true
                    )
                ) {
                    parametersBuilder.setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, i)
                    )
                    isMatched = true
                    Log.d("Player", "Matched audio by Label (Exact): $formatLabel")
                    break@outer
                }

                // 3. Label 模糊匹配
                if (formatLabel != null && targetLabel != null && (formatLabel.contains(
                        targetLabel,
                        true
                    ) || targetLabel.contains(formatLabel, true))
                ) {
                    parametersBuilder.setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, i)
                    )
                    isMatched = true
                    Log.d(
                        "Player",
                        "Matched audio by Label (Fuzzy): Player=$formatLabel / Target=$targetLabel"
                    )
                    break@outer
                }

                // 4. 语言匹配（弱匹配）
                if (!isMatched && targetLanguage != null && formatLang == targetLanguage) {
                    parametersBuilder.setOverrideForType(
                        TrackSelectionOverride(group.mediaTrackGroup, i)
                    )
                    isMatched = true
                    Log.d("Player", "Matched audio by Language: $formatLang")
                    break@outer
                }
            }
        }

        // 策略2：顺序匹配
        if (!isMatched && targetOrdinalIndex != -1) {
            var trackCounter = 0
            outerOrdinal@ for (group in trackGroups) {
                for (i in 0 until group.length) {
                    if (trackCounter == targetOrdinalIndex) {
                        parametersBuilder.setOverrideForType(
                            TrackSelectionOverride(group.mediaTrackGroup, i)
                        )
                        isMatched = true
                        val f = group.getTrackFormat(i)
                        Log.d(
                            "Player",
                            "Matched audio by Ordinal: MetadataIndex=$targetIndex -> ExoOrdinal=$trackCounter (Label: ${f.label})"
                        )
                        break@outerOrdinal
                    }
                    trackCounter++
                }
            }
        }

        player.trackSelectionParameters = parametersBuilder.build()

        if (!isMatched) {
            Log.w("Player", "Unable to match audio target: $targetLabel")
            Log.d("Player", "--- Dump Available Audio Tracks ---")
            trackGroups.forEachIndexed { gi, group ->
                for (i in 0 until group.length) {
                    val f = group.getTrackFormat(i)
                    Log.d(
                        "Player",
                        "Group[$gi] Track[$i]: ID=${f.id}, Label=${f.label}, Lang=${f.language}"
                    )
                }
            }
            Log.d("Player", "-----------------------------------")
        }
    }


    // Handle Ended Logic for List Loop
    LaunchedEffect(endedHandled) {
        if (endedHandled && playMode == 0) {
            val seriesId = mediaInfo.seriesId
            if (seriesId != null) {
                try {
                    val list = repository.getSeriesList(seriesId)

                    @Suppress("UNCHECKED_CAST")
                    val episodes = list

                    val currentId = mediaId
                    val currentIndex = episodes.indexOfFirst { it.id == currentId }

                    if (currentIndex >= 0 && currentIndex < episodes.size - 1) {
                        val nextEpisode = episodes[currentIndex + 1]
                        onNavigateToPlayer(nextEpisode)
                    } else {
                        onBack()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    onBack()
                }
            } else {
                onBack()
            }
        }
    }

    // Session Reporting & Updates
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var tickCount = 0
            while (isActive) {
                try {


                    // 2. Report Progress (Every 5s approx, tickCount % 5 == 0)
                    if (tickCount % 5 == 0) {
                        val ticks = position * 10000
                        val playMethod = Utils.determinePlayMethod(media)

                        val body = mapOf(
                            "VolumeLevel" to 100,
                            "IsMuted" to false,
                            "IsPaused" to false,
                            "RepeatMode" to "RepeatNone",
                            "PositionTicks" to ticks,
                            "PlaybackStartTimeTicks" to 0,
                            "SubtitleStreamIndex" to selectedSubtitleIndex,
                            "AudioStreamIndex" to selectedAudioIndex,
                            "PlayMethod" to playMethod,
                            "PlaySessionId" to (media.playSessionId ?: ""),
                            "MediaSourceId" to ((media.mediaSources as? List<*>)?.firstOrNull() as? Map<*, *>)?.get(
                                "Id"
                            ),
                            "ItemId" to mediaId,
                            "CanSeek" to true,
                            "EventName" to "TimeUpdate"
                        )

                        repository.reportPlaybackProgress(body)
                    }

                    tickCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(1000)
            }
        }
    }

    // Initial Playing Report - Call when playback starts to register session with Emby server
    LaunchedEffect(isPlaying, videoUrl) {
        if (isPlaying && videoUrl != null && !hasReportedPlaying) {
            try {
                val source = media.mediaSources?.firstOrNull()
                val ticks = position * 10000
                val playMethod = Utils.determinePlayMethod(media)
                val body = mapOf(
                    "VolumeLevel" to 100,
                    "IsMuted" to false,
                    "IsPaused" to false,
                    "RepeatMode" to "RepeatNone",
                    "Shuffle" to false,
                    "SubtitleOffset" to 0,
                    "PlaybackRate" to 1,
                    "MaxStreamingBitrate" to 60000000,
                    "PositionTicks" to ticks,
                    "PlaybackStartTimeTicks" to System.currentTimeMillis() * 10000,
                    "SubtitleStreamIndex" to selectedSubtitleIndex,
                    "AudioStreamIndex" to selectedAudioIndex,
                    "BufferedRanges" to emptyList<Any>(),
                    "SeekableRanges" to listOf(
                        mapOf("start" to 0, "end" to (source?.runTimeTicks ?: 0))
                    ),
                    "PlayMethod" to playMethod,
                    "PlaySessionId" to (media.playSessionId ?: ""),
                    "MediaSourceId" to (source?.id ?: ""),
                    "CanSeek" to true,
                    "ItemId" to (source?.itemId ?: mediaId)
                )
                repository.playing(body)
                hasReportedPlaying = true
            } catch (e: Exception) {
                Log.e("PlayerSession", "Failed to report playing", e)
            }
        }
    }

    // 自动隐藏继续播放按钮（3秒后）
    LaunchedEffect(showResumeButtons) {
        if (showResumeButtons && !resumeButtonsShownOnce) {
            resumeButtonsShownOnce = true
            delay(3000)
            showResumeButtons = false
        }
    }

    // Load session once after reporting playing
    // Load session once after reporting playing (with retry)
    LaunchedEffect(hasReportedPlaying, media.playSessionId) {
        if (!hasReportedPlaying) return@LaunchedEffect

        val currentId = media.playSessionId ?: return@LaunchedEffect
        val retries = 4

        repeat(retries) { attempt ->
            try {
                // Delay waiting for server to process playing report (500ms initial + retry interval)
                delay(1200)

                val sessions = repository.getPlayingSessions()
                val source =
                    media.mediaSources?.firstOrNull()
                val mediaSourceId = source?.id

                val found = sessions
                    .find { s ->
                        // Match by NowPlayingItem.Id
                        val nowPlayingId = s.nowPlayingItem?.id
                        if (nowPlayingId == mediaId || (mediaSourceId != null && nowPlayingId == mediaSourceId)) {
                            return@find true
                        }
                        false
                    }

                if (found != null) {
                    // 检查是否需要继续等待转码信息
                    val playMethod = found.playState?.playMethod
                    val transcodingInfo = found.transcodingInfo

                    if (playMethod == "Transcode" && transcodingInfo == null) {
                        android.util.Log.d(
                            "PlayerSession",
                            "Found session but transcodingInfo is null for Transcode mode, retrying..."
                        )
                        session = null  // 清空，继续重试
                    } else {

                        session = found
                        android.util.Log.d(
                            "PlayerSession",
                            "Matched session on attempt ${5 - retries}, playMethod=$playMethod"
                        )

                        return@LaunchedEffect
                    }
                }


            } catch (e: Exception) {
                e.printStackTrace()

            }
            // 如果运行到这里，说明本次没找到，repeat 会继续下一次
            if (attempt == retries - 1) {
                Log.w("Session", "达到最大重试次数，未能找到 Session: $currentId")
            }
        }
    }


    // 使用 LaunchedEffect 监听 player 实例
    // 当 player 变化或 Composable 销毁时，这个协程会自动重启或取消
    LaunchedEffect(player) {
        while (true) {
            if (player.isPlaying || player.playbackState == Player.STATE_BUFFERING) {
                // 1. 更新当前播放位置
                val rawPosition = player.currentPosition
                position = if (rawPosition > 0) rawPosition else 0L

                // 2. 更新缓存进度
                buffered = player.bufferedPosition.coerceAtLeast(0L)

                // 3. 时长更新由onTimelineChanged处理，这里只需要fallback
                // if (duration <= 0) {
                //     val rawDuration = player.duration
                //     if (rawDuration > 0) {
                //         duration = rawDuration
                //     } else {
                //         // Fallback to metadata duration
                //         val source = media.mediaSources?.firstOrNull()
                //         val runTimeTicks = source?.runTimeTicks ?: 0
                //         if (runTimeTicks > 0) {
                //             duration = runTimeTicks / 10000
                //         }
                //     }
                // }
            }

            // 每 800 毫秒更新一次进度
            delay(800)
        }
    }

    // 播放器监听
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                onPlaybackStateChanged(playing)
            }

            override fun onTracksChanged(tracks: Tracks) {
                currentTracks = tracks
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                    val durationMs = player.duration // 此时时长已可用
                    if (durationMs != C.TIME_UNSET) {
                        // 执行逻辑
                        duration = durationMs
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_BUFFERING) {
                    isBuffering = true
                } else if (state == Player.STATE_READY) {
                    isBuffering = false
                    
                   // 在STATE_READY时获取准确时长
                   val rawDuration = player.duration
                   if (rawDuration > 0) {
                       duration = rawDuration
                       Log.d("Player", "Duration updated from READY state: $duration ms")
                   }
                }


                if (state == Player.STATE_ENDED) {
                    isBuffering = false
                    onPlaybackStateChanged(false)
                    // Handle loop logic
                    if (playMode == 1) { // Single Loop
                        player.seekTo(0)
                        player.play()
                    } else if (playMode == 0) { // List Loop

                        val seriesId = mediaInfo.seriesId

                        if (seriesId != null) {
                            // Trigger next episode logic
                            // ...
                            if (!endedHandled) {
                                endedHandled = true
                                // Trigger logic handled in LaunchedEffect
                            }
                        } else {
                            // 播放结束，发送停止报告
                            reportStopped()
                            onBack()
                        }
                    } else {
                        // 播放结束，发送停止报告
                        reportStopped()
                        onBack()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("PlayerScreen", "播放器错误: ${error.message}", error)
                fallbackToServerTranscode()
            }
        }
        player.addListener(listener)

        onDispose {
            // 发送停止报告
            reportStopped()
            player.removeListener(listener)
            player.setVideoSurface(null)
            player.release()
        }
    }

    // 监听按键显示菜单
    val focusRequester = remember { FocusRequester() }

    // UI 结构 - 最外层纯黑背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                // 如果 Resume 按钮正在显示，让按钮处理焦点，不拦截按键
                if (showResumeButtons && playbackPositionTicks > 0) {
                    return@onKeyEvent false
                }
                
                if (event.key == Key.DirectionLeft) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (leftKeyDownTime == 0L) {
                            leftKeyDownTime = System.currentTimeMillis()
                            isShowInfo = true
                        }
                    } else if (event.type == KeyEventType.KeyUp) {
                        if (leftKeyDownTime > 0) {
                            if (System.currentTimeMillis() - leftKeyDownTime < 500) {
                                player.seekBack()
                            }
                            leftKeyDownTime = 0L
                        }
                    }
                    return@onKeyEvent true
                }
                if (event.key == Key.DirectionRight) {
                    if (event.type == KeyEventType.KeyDown) {
                        if (rightKeyDownTime == 0L) {
                            rightKeyDownTime = System.currentTimeMillis()
                            isShowInfo = true
                        }
                    } else if (event.type == KeyEventType.KeyUp) {
                        if (rightKeyDownTime > 0) {
                            if (System.currentTimeMillis() - rightKeyDownTime < 500) {
                                player.seekForward()
                            }
                            rightKeyDownTime = 0L
                        }
                    }
                    return@onKeyEvent true
                }

                if (event.type == KeyEventType.KeyDown) {
                    if (event.key == Key.DirectionDown || event.key == Key.Menu) {
                        showMenu = true
                        return@onKeyEvent true
                    }
                    if (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                        if (showMenu) return@onKeyEvent false
                        if (isPlaying) {
                            player.pause()
                            isShowInfo = true
                        } else {
                            player.play()
                            isShowInfo = false
                        }
                        return@onKeyEvent true
                    }
                    // Show info on any key
                    isShowInfo = true
                    // Hide info after delay?
                }
                false
            }
            .focusRequester(focusRequester)
            .focusable()
    ) {
        // 1. Video Layer
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false // Use custom overlay
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // --- 核心配置：还原作者原定样式 ---
                    subtitleView?.apply {
                        // 1. 允许应用字幕文件内置的样式（颜色、字体、定位等）
                        setApplyEmbeddedStyles(true)

                        // 2. 允许应用字幕文件内置的字体大小
                        setApplyEmbeddedFontSizes(true)

                        // 3. 关键：将渲染模式设为 BITMAP（位图模式）
                        // 只有在这种模式下，复杂的 ASS/SSA 特效和 PGS 图形字幕才能精准还原
                        // 默认的层次模式（VIEW_TYPE_TEXT）会丢失很多高级特效
                        // VIEW_TYPE_CANVAS = 2
                        setViewType(SubtitleView.VIEW_TYPE_CANVAS)

                        // 4. 强制设置透明背景，避免默认样式的黑色背景遮挡
                        val transparentStyle = CaptionStyleCompat(
                            android.graphics.Color.WHITE,
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_NONE,
                            android.graphics.Color.WHITE,
                            null
                        )
                        setStyle(transparentStyle)
                    }
                }
            },
            update = { view ->
                view.player = player
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Full Info Overlay Layer (only when isShowInfo)
        if (isShowInfo && !isPlaying) {
            PlayerOverlay(
                mediaInfo = mediaInfo,
                mediaSource = media.mediaSources?.firstOrNull(),
                session = session,
                videoStream = getVideoTrack(media),
                audioStream = getAudioTrack(media, selectedAudioIndex),
                position = position,
                duration = duration,
                buffered = buffered,
                isPlaying = isPlaying,
                player = player,
                isBuffering = isBuffering
            )

        }

        // 3. Simple Pause/Loading Overlay (no info)
        if ((!isPlaying || isBuffering) && !isShowInfo) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    // Play Icon
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Paused",
                        modifier = Modifier.size(100.dp),
                        tint = Color.White
                    )
                }
            }

            if (!isBuffering) {
                // Menu Hint at bottom
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(bottom = 48.dp)
                    ) {


                        Spacer(modifier = Modifier.width(16.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.press_menu_down_to_show_menu),
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // 4. Resume Buttons (从头开始 / 继续播放)
        if (showResumeButtons && playbackPositionTicks > 0) {
            val continueButtonFocusRequester = remember { FocusRequester() }
            
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 80.dp, end = 48.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 从头开始按钮
                    Surface(
                        onClick = {
                            showResumeButtons = false
                            player.seekTo(0)
                            player.play()
                        },
                        modifier = Modifier.height(48.dp),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))),
                            focusedBorder = Border(BorderStroke(2.dp, Color.White))
                        ),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White.copy(alpha = 0.7f),
                            contentColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.play_from_start),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 继续播放按钮
                    Surface(
                        onClick = {
                            showResumeButtons = false
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .focusRequester(continueButtonFocusRequester),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))),
                            focusedBorder = Border(BorderStroke(2.dp, Color.White))
                        ),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.1f),
                            focusedContainerColor = Color.White.copy(alpha = 0.9f),
                            contentColor = Color.White,
                            focusedContentColor = Color.Black
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 24.dp)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.continue_playback),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // 自动聚焦到继续播放按钮
            LaunchedEffect(Unit) {
                delay(100)
                continueButtonFocusRequester.requestFocus()
            }
        }

        // 5. Menu Dialog
        if (showMenu) {
            PlayerMenu(
                onDismiss = { showMenu = false },
                media = media,
                mediaInfo = mediaInfo,
                subtitleTracks = subtitleTracks,
                selectedSubtitleIndex = selectedSubtitleIndex,
                onSubtitleSelect = { index ->
                    changeTrack(selectedAudioIndex, index)
                },
                audioTracks = audioTracks,
                selectedAudioIndex = selectedAudioIndex,
                onAudioSelect = { index -> changeTrack(index, selectedSubtitleIndex) },
                playbackCorrection = playbackCorrection,
                onPlaybackCorrectionChange = {
                    playbackCorrection = it
                    val prefs = context.getSharedPreferences("emby_tv_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("playback_correction", it).apply()
                },
                playMode = playMode,
                onPlayModeChange = {
                    playMode = it
                    val prefs = context.getSharedPreferences("emby_tv_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("play_mode", it).apply()
                },
                isFavorite = isFavorite,
                onToggleFavorite = {
                    isFavorite = !isFavorite
                    if (mediaInfo.id != null) {
                        scope.launch(Dispatchers.IO) {
                            try {
                                if (isFavorite) {
                                    repository.addToFavorites(mediaInfo.id!!)
                                } else {
                                    repository.removeFromFavorites(mediaInfo.id!!)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                },
                serverUrl = serverUrl,
                repository = repository,
                onNavigateToPlayer = onNavigateToPlayer
            )
        }

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
    } // 最外层纯黑背景 Box 闭合
}

