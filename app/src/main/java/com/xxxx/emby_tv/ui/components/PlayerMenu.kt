package com.xxxx.emby_tv.ui.components


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Surface
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.xxxx.emby_tv.AppModel
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.Utils.formatFileSize
import com.xxxx.emby_tv.model.BaseItemDto
import com.xxxx.emby_tv.model.MediaDto
import com.xxxx.emby_tv.model.MediaStreamDto

@Composable
fun PlayerMenu(
    onDismiss: () -> Unit,
    media: MediaDto,
    mediaInfo: BaseItemDto,
    subtitleTracks: List<MediaStreamDto>,
    selectedSubtitleIndex: Int,
    onSubtitleSelect: (Int) -> Unit,
    audioTracks: List<MediaStreamDto>,
    selectedAudioIndex: Int,
    onAudioSelect: (Int) -> Unit,
    playbackCorrection: Int,
    onPlaybackCorrectionChange: (Int) -> Unit,
    playMode: Int,
    onPlayModeChange: (Int) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    appModel: AppModel,
    onNavigateToPlayer: (BaseItemDto) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // Determine if Episodes tab should be shown
    val isSeries = mediaInfo.type == "Episode" || mediaInfo.seriesId != null

    // Build tabs dynamically
    val tabs = remember(isSeries) {
        val list = mutableListOf<String>()
        if (isSeries) list.add("Episodes") // 0 (if present)
        list.add("Info") // 0 or 1
        list.add("Subtitles") // 2 or 1
        list.add("Audio") // 3 or 2
        if (isSeries) list.add("Mode")
        list.add("Correction") // ...
        list
    }

    // Helper to map UI index to content type
    fun getTabContent(index: Int): String {
        return tabs.getOrNull(index) ?: ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false // 必须设置，否则无法撑满左右边缘
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,

            ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.65f),
//                    .background(Color.Black.copy(alpha = 0.7f)), // 背景遮罩
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))),
                colors = SurfaceDefaults.colors(
                    containerColor = Color(0xFF161616).copy(alpha = 0.5f),
                    contentColor = Color.White
                )

            ) {

                Column {
                    // Tabs
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        itemsIndexed(tabs) { index, title ->
                            val displayTitle = when (title) {
                                "Info" -> stringResource(R.string.info)
                                "Episodes" -> stringResource(R.string.episodes)
                                "Subtitles" -> stringResource(R.string.subtitles)
                                "Audio" -> stringResource(R.string.audio_label)
                                "Mode" -> stringResource(R.string.play_mode)
                                "Correction" -> stringResource(R.string.playback_correction)
                                else -> title
                            }

                            val isSelected = selectedTab == index
                            Surface(
                                onClick = { selectedTab = index },
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .onFocusChanged {
                                        if (it.isFocused) selectedTab = index
                                    },
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(50)),
                                colors = ClickableSurfaceDefaults.colors(
                                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.5f
                                    ) else Color.Transparent,
                                    contentColor = if (isSelected) TvMaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                                    focusedContainerColor = TvMaterialTheme.colorScheme.primary,
                                    focusedContentColor = TvMaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = displayTitle,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    ),
                                    style = TvMaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content
                    Box(modifier = Modifier.weight(1f)) {
                        val currentTabName = getTabContent(selectedTab)
                        when (currentTabName) {
                            "Info" -> InfoTab(mediaInfo, media, isFavorite, onToggleFavorite)
                            "Episodes" -> EpisodesTab(
                                mediaInfo,
                                appModel,
                                onDismiss,
                                onNavigateToPlayer
                            )

                            "Subtitles" -> SubtitlesTab(
                                subtitleTracks,
                                selectedSubtitleIndex,
                                onSubtitleSelect
                            )

                            "Audio" -> AudioTab(audioTracks, selectedAudioIndex, onAudioSelect)
                            "Mode" -> PlayModeTab(playMode, onPlayModeChange)
                            "Correction" -> PlaybackCorrectionTab(
                                playbackCorrection,
                                onPlaybackCorrectionChange
                            )

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoTab(
    mediaInfo: BaseItemDto,
    media: MediaDto,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .padding(horizontal = 50.dp)
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        // 1. 标题栏
        Text(
            text = mediaInfo.seriesName ?: mediaInfo.name ?: "",
            style = TvMaterialTheme.typography.headlineMedium, // 加大标题
            color = Color.White,
            fontWeight = FontWeight.Bold
        )

        // 2. 季集详细信息 (如果是电视剧)
        val parentIndexNumber = mediaInfo.parentIndexNumber
        val indexNumber = mediaInfo.indexNumber
        if (parentIndexNumber != null && indexNumber != null) {
            Text(
                text = "S${parentIndexNumber} E${indexNumber} · ${mediaInfo.name ?: ""}",
                style = TvMaterialTheme.typography.titleMedium,
                color = TvMaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. 媒体元数据标签组 (年份、分辨率、视频编码、大小)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 年份标签
            val year = mediaInfo.productionYear?.toString() ?: mediaInfo.premiereDate?.take(4) ?: ""
            if (year.isNotEmpty()) MetaBadge(text = year)

            // 视频信息解析
            val source = media.mediaSources?.firstOrNull()
            val videoStream = source?.mediaStreams?.find { it.type == "Video" }

            // 分辨率 (如 4K, 1080P)
            val resolution = when {
                (videoStream?.width ?: 0) >= 3840 -> "4K"
                (videoStream?.width ?: 0) >= 1920 -> "1080P"
                (videoStream?.width ?: 0) >= 1280 -> "720P"
                else -> videoStream?.displayTitle?.split(" ")?.firstOrNull() ?: ""
            }
            if (resolution.isNotEmpty()) MetaBadge(text = resolution, containerColor = Color(0xFFE50914)) // 红色醒目

            // HDR
            val videoRange = videoStream?.videoRange?:""
            if(videoRange.isNotEmpty())MetaBadge(text = videoRange)
            // 视频编码 (如 HEVC, H264)
            val codec = videoStream?.codec?.uppercase() ?: ""
            if (codec.isNotEmpty()) MetaBadge(text = codec)

            // 文件大小
            val fileSize = formatFileSize(mediaInfo.size)
            MetaBadge(text = fileSize)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. 操作按钮与转码信息区
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 收藏按钮
            Button(
                onClick = onToggleFavorite,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                scale = ButtonDefaults.scale(focusedScale = 1.1f),
                colors = ButtonDefaults.colors(
                    containerColor = TvMaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f),
                    contentColor = TvMaterialTheme.colorScheme.onPrimary,
                    focusedContainerColor = TvMaterialTheme.colorScheme.primary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onPrimary,
                )
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isFavorite) stringResource(R.string.remove_from_favorite)
                    else stringResource(R.string.add_to_favorite)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Surface(
            onClick = { /* 可以在这里实现展开全屏简介的逻辑 */ },
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Gray.copy(alpha = 0.4f),
            ),
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = 1.03f
            ),
        ) {
            Text(
                text = mediaInfo.overview ?: "",
                style = TvMaterialTheme.typography.bodyMedium,
                lineHeight = 28.sp,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(50.dp)) // 底部留白
    }
}

/**
 * 媒体信息小标签组件
 */
@Composable
fun MetaBadge(
    text: String,
    containerColor: Color = Color.White.copy(alpha = 0.2f)
) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Text(
            text = text,
            style = TvMaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}


@Composable
fun EpisodesTab(
    mediaInfo: BaseItemDto,
    appModel: AppModel,
    onDismiss: () -> Unit,
    onNavigateToPlayer: (BaseItemDto) -> Unit
) {
    val seriesId = mediaInfo.seriesId
    if (seriesId == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.not_series_content), color = Color.White)
        }
        return
    }

    var episodes by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(seriesId) {
        try {
            val list = appModel.getSeriesList(seriesId)
            @Suppress("UNCHECKED_CAST")
            episodes = list
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    if (!isLoading && episodes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.no_episodes_found), color = Color.White)
        }
        return
    }

    // 1. 根据比例计算动态的宽高
    val maxLength = 220.dp
    val maxAspectRatio = episodes.mapNotNull {
        val ratio = it.primaryImageAspectRatio?.toFloat()
        // 排除掉 null、1.0 以及明显不是正常比例的数值
        if (ratio == null || ratio == 1f) null else ratio
    }.maxOrNull() ?: 0.666f
    val imgWidth = if (maxAspectRatio >= 1) {
        // 横图：宽是长边
        maxLength
    } else {
        // 竖图：高是长边
        (maxLength.value * maxAspectRatio).dp
    }

    // Auto scroll to current item
    val listState = rememberLazyListState()
    val currentItemId = mediaInfo.id

    LaunchedEffect(episodes) {
        val index = episodes.indexOfFirst { it.id == currentItemId }
        if (index >= 0) {
            listState.scrollToItem(index)
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        items(episodes) { episode ->
            val isCurrent = episode.id == currentItemId

            BuildItem(
                item = episode,
                imgWidth = imgWidth,
                aspectRatio = maxAspectRatio,
                modifier = Modifier,
                isMyLibrary = false,
                isShowOverview = false, // Less space in Player Menu
                appModel = appModel,
                onItemClick = {
                    if (!isCurrent) {
                        onDismiss()
                        onNavigateToPlayer(episode)
                    }
                }
            )
        }
    }
}

@Composable
fun SubtitlesTab(tracks: List<MediaStreamDto>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {
        item {
            val isSelected = selectedIndex == -1
            Surface(
                onClick = { onSelect(-1) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.primary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onPrimary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.disable_subtitles))
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
        items(tracks) { track ->
            val index = (track.index) ?: -1
            val title =
                (track.displayTitle) ?: (track.language) ?: "Unknown"

            val isSelected = selectedIndex == index

            Surface(
                onClick = { onSelect(index) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.primary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onPrimary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title)
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}

@Composable
fun AudioTab(tracks: List<MediaStreamDto>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 150.dp)) {
        item {
            // Optional: Header or "Audio" title using audio_label if needed, but tabs already have title.
            // Just listing tracks here.
        }
        items(tracks) { track ->
            val index = (track.index) ?: -1
            val title =
                (track.displayTitle) ?: (track.language) ?: "Unknown"
            val isSelected = selectedIndex == index

            Surface(
                onClick = { onSelect(index) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.primary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onPrimary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title)
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}

@Composable
fun PlaybackCorrectionTab(current: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 150.dp)) {
        val options = listOf(
            0 to stringResource(R.string.off_default),
            1 to stringResource(R.string.playback_correction_server)
        )
        options.forEach { (value, label) ->
            val isSelected = current == value
            Surface(
                onClick = { onChange(value) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.primary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onPrimary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, style = TvMaterialTheme.typography.bodyLarge)
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}

@Composable
fun PlayModeTab(current: Int, onChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 150.dp)) {
        val modes = listOf(
            stringResource(R.string.loop_list),
            stringResource(R.string.loop_single),
            stringResource(R.string.loop_off)
        )
        modes.forEachIndexed { index, title ->
            val isSelected = current == index
            Surface(
                onClick = { onChange(index) },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    focusedContainerColor = TvMaterialTheme.colorScheme.primary,
                    focusedContentColor = TvMaterialTheme.colorScheme.onPrimary,
                    containerColor = if (isSelected) TvMaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.5f
                    ) else Color.Transparent,
                    contentColor = TvMaterialTheme.colorScheme.onSurface
                ),
                scale = ClickableSurfaceDefaults.scale(
                    focusedScale = 1.03f,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, style = TvMaterialTheme.typography.bodyLarge)
                    if (isSelected) Icon(
                        Icons.Default.Check,
                        null,
                        tint = LocalContentColor.current
                    )
                }
            }
        }
    }
}
