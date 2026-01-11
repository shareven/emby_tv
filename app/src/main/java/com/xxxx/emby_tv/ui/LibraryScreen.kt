package com.xxxx.emby_tv.ui

import android.util.Log
import com.xxxx.emby_tv.ui.components.BuildItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import com.xxxx.emby_tv.AppModel
import com.xxxx.emby_tv.model.BaseItemDto
import androidx.compose.ui.res.stringResource
import com.xxxx.emby_tv.ui.theme.GradientBackground
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.ui.components.Loading

/**
 * 媒体库界面 - 对应Flutter中的library_screen.dart
 * Dart转换Kotlin说明：
 * 1. 使用Jetpack Compose替代Flutter的Widget系统
 * 2. 使用State和MutableState替代Dart中的State类
 * 3. 使用androidx.tv.material3组件替代Flutter的Material组件，避免与标准Material组件冲突
 * 4. 使用TvLazyVerticalGrid实现网格布局，适配TV端
 */
@Composable
fun LibraryScreen(
    parentId: String,
    title: String,
    type: String,
    appModel: AppModel,
    onNavigateBack: () -> Unit,
    onNavigateToSeries: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Use AppModel state
    val libraryItems = appModel.libraryItems

    LaunchedEffect(parentId, type) {
        appModel.loadLibraryItems(parentId, type)
    }

    GradientBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = if (title.isNotEmpty()) title else stringResource(R.string.my_libraries),
                style = androidx.tv.material3.MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
                color = androidx.compose.ui.graphics.Color.White
            )

            if (libraryItems == null) {
                Loading()
            } else {
                val items = libraryItems

                // 1. 根据比例计算动态的宽高
                val maxLength = 220.dp
                val maxAspectRatio = libraryItems.mapNotNull {
                    val ratio = it.primaryImageAspectRatio?.toFloat()
                    if (ratio == null || ratio == 1.0f) null else ratio
                }.maxOrNull() ?: 0.666f
                val aspectRatios = items.map { it.primaryImageAspectRatio?.toFloat() ?: 1f }

                var imgWidth = 0.dp
                if (maxAspectRatio >= 1f) {
                    // 横图：宽是长边
                    imgWidth = maxLength
                } else {
                    // 竖图：高是长边
                    imgWidth = (maxLength.value * maxAspectRatio).dp
                }
                val num = if(maxAspectRatio>1) 4 else 6
                LazyVerticalGrid(
                    columns = GridCells.Fixed(num),
                    contentPadding = PaddingValues(24.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    modifier = Modifier.fillMaxSize()
                ) {


                    items(items, key = { it.id ?: it.hashCode() }) { item ->
                        val id = item.id ?: ""
                        if (id.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                BuildItem(
                                    item = item,
                                    imgWidth = imgWidth,
                                    aspectRatio = maxAspectRatio,
                                    modifier = Modifier.fillMaxWidth(),
                                    isMyLibrary = false,
                                    appModel = appModel,
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
}