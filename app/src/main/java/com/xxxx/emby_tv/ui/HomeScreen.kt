package com.xxxx.emby_tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.tv.material3.*
import com.xxxx.emby_tv.AppModel
import com.xxxx.emby_tv.model.BaseItemDto
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.res.stringResource
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.ui.components.BuildItem
import com.xxxx.emby_tv.ui.components.MenuDialog
import com.xxxx.emby_tv.ui.components.NoData
import com.xxxx.emby_tv.ui.components.TopStatusBar
import com.xxxx.emby_tv.ui.theme.GradientBackground

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    appModel: AppModel,
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        appModel.checkUpdate()
    }

    if (showMenu) {
        MenuDialog(
            needUpdate = appModel.needUpdate,
            onDismiss = { showMenu = false },
            onLogout = {
                appModel.logout()
                showMenu = false
            },
            onUpdate = {
                showMenu = false
                navController.navigate("update")
            },
            onThemeChange = { themeColor ->
                appModel.saveThemeId(themeColor.id)
            }
        )
    }

    fun goPlay(item: BaseItemDto) {
        val id = item.id ?: ""
        val userData = item.userData
        val position = userData?.playbackPositionTicks ?: 0L

        navController.navigate("player/$id?position=$position")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部状态栏
        TopStatusBar(
            currentVersion = appModel.currentVersion,
            newVersion = appModel.newVersion,
            needUpdate = appModel.needUpdate
        )

        Spacer(modifier = Modifier.height(8.dp))


        if (appModel.isLoaded) LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // 我的媒体库
            item {
                MediaSection(
                    title = stringResource(R.string.my_libraries),
                    items = appModel.libraryLatestItems ?: emptyList(),
                    isMyLibrary = true,
                    onItemSelected = { item ->
                        val firstItem = item.latestItems?.firstOrNull()
                        val type = firstItem?.type ?: ""

                        val id = item.id ?: ""
                        val title = item.name ?: ""
                        navController.navigate("library/$id?libraryName=$title&type=$type")
                    },
                    onMenuPressed = { showMenu = true },
                    appModel = appModel
                )
            }

            // 继续观看
            item {
                MediaSection(
                    title = stringResource(R.string.continue_watching),
                    items = appModel.resumeItems ?: emptyList(),
                    isShowImg17 = true,
                    isContinueWatching = true,
                    onItemSelected = { item -> goPlay(item) },
                    onMenuPressed = { showMenu = true },
                    appModel = appModel
                )
            }

            // 收藏
            if (appModel.favoriteItems != null && appModel.favoriteItems!!.isNotEmpty()) item {
                MediaSection(
                    title = stringResource(R.string.favorite),
                    items = appModel.favoriteItems ?: emptyList(),
                    isShowImg17 = true,
                    onItemSelected = { item -> goPlay(item) },
                    onMenuPressed = { showMenu = true },
                    appModel = appModel
                )
            }

            // 各库最新内容
            itemsIndexed(
                appModel.libraryLatestItems ?: emptyList(),
                key = { _, library -> library.id ?: library.hashCode() }) { index, library ->
                MediaSection(
                    title = library.name ?: "",
                    items = library.latestItems ?: emptyList(),
                    onItemSelected = { item ->
                        if (item.isSeries) {
                            val seriesId = item.id
                            if (!seriesId.isNullOrEmpty()) {
                                appModel.playNextUp(seriesId) { nextItem: BaseItemDto ->
                                    goPlay(nextItem)
                                }
                            } else {
                                goPlay(item)
                            }
                        } else {
                            goPlay(item)
                        }

                    },
                    onMenuPressed = { showMenu = true },
                    appModel = appModel
                )
            }
        }
    }

}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MediaSection(
    title: String,
    items: List<BaseItemDto>,
    isMyLibrary: Boolean = false,
    isShowImg17: Boolean = false,
    isContinueWatching: Boolean = false,
    onItemSelected: (BaseItemDto) -> Unit,
    onMenuPressed: () -> Unit,
    appModel: AppModel,
) {
    val maxLength = when {
        isMyLibrary -> 194.dp
        else -> 214.dp
    }

    val maxAspectRatio = items.mapNotNull {
        val ratio = it.primaryImageAspectRatio?.toFloat()
        if (ratio == null || ratio == 1.0f) null else ratio
    }.maxOrNull() ?: 0.666f

    // 1. 根据比例计算动态的宽高
    var imgWidth = 0.dp

    if (maxAspectRatio >= 1f) {
        // 横图：宽是长边
        imgWidth = maxLength
    } else {
        // 竖图：高是长边
        imgWidth = (maxLength.value * maxAspectRatio).dp
    }
    Column {
        Text(
            text = title,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 16.dp)
        )

        if (items.isEmpty()) {
            NoData(modifier = Modifier.height(maxLength))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                itemsIndexed(
                    items,
                    key = { _, item -> item.id ?: item.hashCode() }) { index, item ->
                    val focusRequester = remember { FocusRequester() }
                    val modifier = if (isContinueWatching && index == 0) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    }

                    if (isContinueWatching && index == 0) {
                        LaunchedEffect(Unit) {
                            focusRequester.requestFocus()
                        }
                    }

                    BuildItem(
                        modifier = modifier,
                        item = item,
                        aspectRatio = maxAspectRatio, // BuildItem expects Int width
                        imgWidth = imgWidth, // BuildItem expects Int width
                        isShowImg17 = isShowImg17,
                        isMyLibrary = isMyLibrary,
                        appModel = appModel,
                        onItemClick = { onItemSelected(item) },
                        onMenuClick = { onMenuPressed() },
                    )
                }

            }
        }
    }

    Spacer(modifier = Modifier.height(32.dp))

}
