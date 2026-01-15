package com.xxxx.emby_tv.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.xxxx.emby_tv.data.model.BaseItemDto
import com.xxxx.emby_tv.data.model.PersonInfo
import androidx.compose.ui.res.stringResource
import com.xxxx.emby_tv.R
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.xxxx.emby_tv.ui.components.BuildItem
import com.xxxx.emby_tv.Utils
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import coil3.compose.SubcomposeAsyncImage
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.ui.viewmodel.DetailViewModel
import kotlinx.coroutines.delay

@Composable
fun MediaDetailScreen(
    seriesId: String,
    detailViewModel: DetailViewModel,
    onNavigateToSeries: (String) -> Unit,
    onNavigateToPlayer: (BaseItemDto) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""
    
    val mediaInfo = detailViewModel.mediaInfo
    val playButtonFocusRequester = remember { FocusRequester() }

    // Series Data State
    var seasons by remember { mutableStateOf<List<BaseItemDto>?>(null) }
    var episodes by remember { mutableStateOf<List<BaseItemDto>?>(null) }
    var resume by remember { mutableStateOf<BaseItemDto?>(null) }
    var selectedSeasonIndex by remember { mutableIntStateOf(0) }
    var isLoadingSeriesData by remember { mutableStateOf(false) }

    LaunchedEffect(seriesId) {
        detailViewModel.loadMediaInfo(seriesId)
    }

    // Auto scroll to current item
    val listState = rememberLazyListState()
    val currentItemId = resume?.id

    LaunchedEffect(episodes) {
        if (currentItemId != null && episodes != null) {
            val index = episodes?.indexOfFirst { it.id == currentItemId } ?: -1
            if (index >= 0) {
                listState.scrollToItem(index)
            }
        }
    }

    LaunchedEffect(mediaInfo) {
        seasons = null
        episodes = null
        resume = null
        if (mediaInfo != null && mediaInfo.isSeries) {
            isLoadingSeriesData = true
            try {
                val seasonsList = detailViewModel.getSeasonList(seriesId)
                val episodesList = detailViewModel.getSeriesList(seriesId)
                val x = detailViewModel.getResumeItem(seriesId)

                seasons = seasonsList
                episodes = episodesList
                resume = x
                selectedSeasonIndex = 0
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingSeriesData = false
            }
        }
    }

    // Default focus logic
    LaunchedEffect(mediaInfo, isLoadingSeriesData) {
        if (mediaInfo != null && !isLoadingSeriesData) {
            delay(200)
            try {
                playButtonFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore if not attached
            }
        }
    }

    fun getSeasonEpisodes(seasonName: String): List<BaseItemDto> {
        return episodes?.filter { it.seasonName == seasonName } ?: emptyList()
    }

    if (mediaInfo == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else {
        // Construct Backdrop URL
        val backdropTags = mediaInfo.backdropImageTags
        val parentBackdropTags = mediaInfo.parentBackdropImageTags
        val parentBackdropItemId = mediaInfo.parentBackdropItemId

        var finalBackdropUrl = ""
        if (!backdropTags.isNullOrEmpty()) {
            finalBackdropUrl =
                "$serverUrl/emby/Items/${mediaInfo.id}/Images/Backdrop?maxWidth=1920&tag=${backdropTags[0]}&quality=80"
        } else if (!parentBackdropTags.isNullOrEmpty() && parentBackdropItemId != null) {
            finalBackdropUrl =
                "$serverUrl/emby/Items/$parentBackdropItemId/Images/Backdrop?maxWidth=1920&tag=${parentBackdropTags[0]}&quality=80"
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1. Backdrop Layer
            if (finalBackdropUrl.isNotEmpty()) {
                SubcomposeAsyncImage(
                    model = finalBackdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f
                )
            }

            // 2. Gradient Overlay (Scrim)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.9f),
                                Color.Black
                            )
                        )
                    )
            )

            // 3. Main Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                // Header Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // Poster
                    Box(
                        modifier = Modifier
                            .width(180.dp)
                            .aspectRatio(
                                mediaInfo.primaryImageAspectRatio?.toFloat()
                                    ?: 0.67f
                            )
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val imageUrl = Utils.getImageUrl(serverUrl, mediaInfo, false)
                        if (imageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = mediaInfo.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Info Column
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = mediaInfo.name ?: "",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )

                        // Meta Pills Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val year = mediaInfo.productionYear?.toString()
                            if (!year.isNullOrEmpty()) MetaPill(year)

                            val runtimeTicks = mediaInfo.runTimeTicks
                            if (runtimeTicks != null) {
                                val runtime = Utils.formatRuntimeFromTicks(runtimeTicks)
                                if (runtime.isNotEmpty()) MetaPill(runtime)
                            }

                            val officialRating = mediaInfo.officialRating
                            if (!officialRating.isNullOrEmpty()) MetaPill(officialRating)

                            val communityRating = mediaInfo.communityRating?.toString()
                            if (!communityRating.isNullOrEmpty()) MetaPill("★ $communityRating")

                            val type = mediaInfo.type
                            if (!type.isNullOrEmpty()) MetaPill(type)
                        }

                        // Play Button
                        Button(
                            onClick = {
                                if (mediaInfo.isSeries) {
                                    val currentResume = resume
                                    if (currentResume != null) {
                                        onNavigateToPlayer(currentResume)
                                    } else if (!seasons.isNullOrEmpty()) {
                                        val firstSeason = seasons?.first()
                                        val seasonName = firstSeason?.name ?: ""
                                        val eps = getSeasonEpisodes(seasonName)
                                        if (eps.isNotEmpty()) {
                                            onNavigateToPlayer(eps.first())
                                        }
                                    }
                                } else {
                                    onNavigateToPlayer(mediaInfo)
                                }
                            },
                            modifier = Modifier
                                .focusRequester(playButtonFocusRequester)
                                .padding(top = 8.dp),
                            colors = ButtonDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.primary,
                                focusedContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            val isResume =
                                resume != null || ((mediaInfo.userData?.playbackPositionTicks
                                    ?: 0L) > 0)

                            Text(
                                text = if (isResume) stringResource(R.string.resume) else stringResource(
                                    R.string.play
                                ),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        if (resume != null) Box(
                            modifier = Modifier
                                .width(150.dp)
                                .height(3.dp)
                                .background(Color.Gray.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = resume?.playbackProgress ?: 0f)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        if (((mediaInfo.userData?.playbackPositionTicks
                                ?: 0L) > 0)
                        ) Box(
                            modifier = Modifier
                                .width(150.dp)
                                .height(3.dp)
                                .background(Color.Gray.copy(alpha = 0.3f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction = mediaInfo.playbackProgress)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                        if (resume != null) Text(
                            text = "S${resume?.parentIndexNumber}:E${resume?.indexNumber} ${resume?.name}",
                            color = MaterialTheme.colorScheme.onPrimary
                        )

                        // Overview
                        mediaInfo.overview?.let { overview ->
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    color = Color.White.copy(alpha = 0.9f),
                                    lineHeight = TextUnit(
                                        1.5f,
                                        TextUnitType.Em
                                    )
                                ),
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Seasons & Episodes Section
                if (mediaInfo.isSeries && !seasons.isNullOrEmpty()) {

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        itemsIndexed(seasons ?: emptyList()) { index, season ->
                            val name = season.name
                                ?: "${stringResource(R.string.season)} ${index + 1}"
                            val isSelected = index == selectedSeasonIndex

                            Surface(
                                onClick = { selectedSeasonIndex = index },
                                shape = ClickableSurfaceDefaults.shape(
                                    shape = RoundedCornerShape(
                                        50
                                    )
                                ),
                                colors = ClickableSurfaceDefaults.colors(
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(
                                        alpha = 0.15f
                                    ),
                                    focusedContainerColor = MaterialTheme.colorScheme.primary,
                                    focusedContentColor = MaterialTheme.colorScheme.onPrimary

                                )
                            ) {
                                Text(
                                    text = name,
                                    modifier = Modifier.padding(
                                        horizontal = 20.dp,
                                        vertical = 10.dp
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }

                    val currentSeason = seasons?.getOrNull(selectedSeasonIndex)
                    val seasonName = currentSeason?.name ?: ""
                    val currentEpisodes = getSeasonEpisodes(seasonName)

                    if (currentEpisodes.isNotEmpty()) {
                        val maxLength = 220.dp
                        val aspectRatios =
                            currentEpisodes.mapNotNull { it.primaryImageAspectRatio?.toFloat() }
                        val maxAspectRatio = aspectRatios.maxOrNull() ?: 1.77f

                        val imgWidth = if (maxAspectRatio >= 1f) {
                            maxLength
                        } else {
                            (maxLength.value * maxAspectRatio).dp
                        }

                        LazyRow(
                            state = listState,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(horizontal = 32.dp),

                            ) {
                            items(currentEpisodes, key = { it.id ?: it.hashCode() }) { episode ->
                                BuildItem(
                                    item = episode,
                                    imgWidth = imgWidth,
                                    aspectRatio = maxAspectRatio,
                                    modifier = Modifier,
                                    isMyLibrary = false,
                                    isShowOverview = true,
                                    serverUrl = serverUrl,
                                    onItemClick = { onNavigateToPlayer(episode) }
                                )
                            }
                        }
                    } else {
                        Text(text = stringResource(R.string.no_episodes_found), color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // People List (Below Episodes)
                mediaInfo.people?.takeIf { it.isNotEmpty() }?.let { people ->
                    if (people.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.cast),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        val castMaxLength = 160.dp
                        val maxAspectRatio = 0.66f

                        val castImgWidth = if (maxAspectRatio >= 1f) {
                            castMaxLength
                        } else {
                            (castMaxLength.value * maxAspectRatio).dp
                        }

                        LazyRow(
                            contentPadding = PaddingValues(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                        ) {
                            items(people, key = { it.id ?: it.hashCode() }) { person ->
                                PersonCard(
                                    person = person,
                                    imgWidth = castImgWidth,
                                    aspectRatio = maxAspectRatio,
                                    serverUrl = serverUrl
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }

                // Details Box (Bottom)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.details),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )

                        // Meta Rows
                        MetaRow(
                            stringResource(R.string.genres),
                            mediaInfo.genres?.joinToString(", ") ?: ""
                        )

                        val studios =
                            mediaInfo.studios?.mapNotNull { it.name }?.joinToString(", ")
                        MetaRow(stringResource(R.string.studios), studios ?: "")

                        MetaRow(
                            stringResource(R.string.premiere),
                            Utils.formatDate(mediaInfo.premiereDate)
                        )
                        MetaRow(
                            stringResource(R.string.end),
                            Utils.formatDate(mediaInfo.dateCreated)
                        )
                        MetaRow(
                            stringResource(R.string.status),
                            mediaInfo.status ?: ""
                        )
                        MetaRow(
                            stringResource(R.string.tagline),
                            mediaInfo.taglines?.joinToString(", ") ?: ""
                        )

                        val providerIds = mediaInfo.providerIds
                        val providersStr =
                            providerIds?.entries?.joinToString(" · ") { "${it.key}: ${it.value}" }
                        MetaRow(stringResource(R.string.provider_ids), providersStr ?: "")

                        MetaRow(stringResource(R.string.path), mediaInfo.path ?: "")

                        if (mediaInfo.isSeries) {
                            val seasonCount = seasons?.size ?: 0
                            val episodeCount = episodes?.size ?: 0
                            MetaRow(
                                stringResource(R.string.counts),
                                "$seasonCount ${stringResource(R.string.seasons)} · $episodeCount ${
                                    stringResource(R.string.episodes)
                                }"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))
            }
        }
    }
}

@Composable
fun MetaPill(text: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(color = Color.White),
        )
    }
}

@Composable
fun MetaRow(label: String, value: String) {
    if (value.isNotEmpty()) {
        Surface(
            onClick = {},
            enabled = true,
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                containerColor = Color.Transparent,
                contentColor = Color.White
            ),
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = 1.02f
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.width(140.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = Color.White.copy(alpha = 0.7f)
                    )
                )
                Text(
                    text = value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                )
            }
        }
    }
}

@Composable
fun PersonCard(
    person: PersonInfo,
    imgWidth: Dp,
    aspectRatio: Float,
    serverUrl: String,
) {

    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.onSurface
                )
            )
        ),
        scale = ClickableSurfaceDefaults
            .scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Black.copy(alpha = 0.2f),
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            pressedContentColor = MaterialTheme.colorScheme.surface,
            focusedContentColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.width(imgWidth)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .background(
                        Color(0xFF2D2D2D),
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = if (person.primaryImageTag != null) {
                    "$serverUrl/emby/Items/${person.id}/Images/Primary?maxWidth=300&tag=${person.primaryImageTag}&quality=80"
                } else null

                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = person.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Text(
                text = person.name ?: "",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )

            Text(
                text = person.role ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
            )
        }
    }
}
