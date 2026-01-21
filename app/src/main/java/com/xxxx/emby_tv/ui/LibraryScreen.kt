package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.components.BuildItem
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.BaseItemDto
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.viewmodel.LibraryViewModel

/**
 * 媒体库界面（支持分页加载）
 */
@Composable
fun LibraryScreen(
    parentId: String,
    title: String,
    type: String,
    libraryViewModel: LibraryViewModel,
    onNavigateToSeries: (String) -> Unit,
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    // 获取 serverUrl
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""

    // 从 ViewModel 获取数据
    val libraryItems = libraryViewModel.libraryItems
    val isLoadingMore = libraryViewModel.isLoadingMore
    val hasMoreData = libraryViewModel.hasMoreData
    val totalCount = libraryViewModel.totalCount

    // 加载数据
    LaunchedEffect(parentId, type) {
        libraryViewModel.loadItems(parentId, type)
    }

    // 数据加载完成后聚焦到第一个项目
    LaunchedEffect(libraryItems) {
        if (libraryItems != null && libraryItems.isNotEmpty()) {
            firstItemFocusRequester.requestFocus()
        }
    }

    // 监听滚动位置，接近底部时加载更多
    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItemsCount = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0

            // 当最后可见项距离末尾小于 5 个时触发加载
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 5
        }.collect { shouldLoadMore ->
            if (shouldLoadMore && hasMoreData && !isLoadingMore) {
                libraryViewModel.loadMore()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题和数量统计
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (title.isNotEmpty()) title else stringResource(R.string.my_libraries),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )

            // 显示加载数量
            if (libraryItems != null && totalCount > 0) {
                Text(
                    text = "${libraryItems.size}/$totalCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        if (libraryItems == null) {
            Loading()
        } else {
            val items = libraryItems

            // 计算宽高
            // 1. 定义基准长度
            val maxLength = 220.dp

            // 2. 提取并过滤有效比例，同时拆分
            val (aspectRatioOver1List, aspectRatioUnder1List) = items
                .mapNotNull { it.primaryImageAspectRatio?.toFloat() } // 确保转为 Float 且过滤 null
                .partition { it > 1.0f }

            // 3. 计算目标比例 (增加对空列表的防御性保护)
            val maxAspectRatio = if (aspectRatioOver1List.size >= aspectRatioUnder1List.size) {
                // 横版（如 16:9）居多，取横版中的最大比例（最宽的那张）
                aspectRatioOver1List.maxOrNull() ?: 1.777f
            } else {
                // 竖版（如 2:3）居多，取竖版中的最大比例（最接近正方形的那张）
                // 注意：如果是竖版，max() 实际上是让画面不那么“窄”
                aspectRatioUnder1List.maxOrNull() ?: 0.666f
            }

            // 4. 计算图片宽度 (使用 Compose 的 Dp 乘法)
            // 逻辑：如果是横版，宽度固定为 maxLength；如果是竖版，按比例缩减宽度
            val imgWidth = if (maxAspectRatio >= 1.0f) {
                maxLength
            } else {
                // 修正：直接用 Dp 乘 Float，比取 .value 更安全
                maxLength * maxAspectRatio
            }

            // 5. 计算单行显示数量
            val num = if (maxAspectRatio > 1.0f) 4 else 6


            LazyVerticalGrid(
                columns = GridCells.Fixed(num),
                state = gridState,
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // 数据项
                items(items.size, key = { items[it].id ?: it.hashCode() }) { index ->
                    val item = items[index]
                    val id = item.id ?: ""
                    if (id.isNotEmpty()) {
                        val itemModifier = if (index == 0) {
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(firstItemFocusRequester)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            BuildItem(
                                item = item,
                                imgWidth = imgWidth,
                                aspectRatio = maxAspectRatio,
                                modifier = itemModifier,
                                isMyLibrary = false,
                                serverUrl = serverUrl,
                                onItemClick = {
                                    onNavigateToSeries(id)
                                }
                            )
                        }
                    }
                }

                // 加载更多指示器
                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
