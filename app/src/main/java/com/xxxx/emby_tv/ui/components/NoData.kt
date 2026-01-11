package com.xxxx.emby_tv.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.tv.material3.*
import androidx.compose.ui.res.stringResource
import com.xxxx.emby_tv.R

/**
 * 无数据组件 - 对应Flutter中的no_data.dart
 * Dart转换Kotlin说明：
 * 1. 使用Jetpack Compose的@Composable函数替代Flutter的Widget
 * 2. 使用androidx.tv.material3组件替代Flutter的Material组件，避免与标准Material组件冲突
 */
@Composable
fun NoData(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.no_data),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}