package com.xxxx.emby_tv.ui

import com.xxxx.emby_tv.ui.components.BuildItem
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.xxxx.emby_tv.data.repository.EmbyRepository
import com.xxxx.emby_tv.data.model.BaseItemDto
import androidx.compose.ui.res.stringResource
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.viewmodel.LibraryViewModel

/**
 * 媒体库界面
 */
@Composable
fun LibraryScreen(
    parentId: String,
    title: String,
    type: String,
    libraryViewModel: LibraryViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSeries: (String) -> Unit
) {
    val context = LocalContext.current
    val firstItemFocusRequester = remember { FocusRequester() }
    
    // 获取 serverUrl
    val repository = remember { EmbyRepository.getInstance(context) }
    val serverUrl = repository.serverUrl ?: ""

    // 从 ViewModel 获取数据
    val libraryItems = libraryViewModel.libraryItems

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = if (title.isNotEmpty()) title else stringResource(R.string.my_libraries),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
            color = androidx.compose.ui.graphics.Color.White
        )

        if (libraryItems == null) {
            Loading()
        } else {
            val items = libraryItems

            // 计算宽高
            val maxLength = 220.dp
            val maxAspectRatio = libraryItems.mapNotNull {
                val ratio = it.primaryImageAspectRatio?.toFloat()
                if (ratio == null || ratio == 1.0f) null else ratio
            }.maxOrNull() ?: 0.666f

            val imgWidth = if (maxAspectRatio >= 1f) {
                maxLength
            } else {
                (maxLength.value * maxAspectRatio).dp
            }
            
            val num = if (maxAspectRatio > 1) 4 else 6
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(num),
                contentPadding = PaddingValues(24.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(items.size, key = { items[it].id ?: it.hashCode() }) { index ->
                    val item = items[index]
                    val id = item.id ?: ""
                    if (id.isNotEmpty()) {
                        val itemModifier = if (index == 0) {
                            Modifier.fillMaxWidth().focusRequester(firstItemFocusRequester)
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
            }
        }
    }
}
