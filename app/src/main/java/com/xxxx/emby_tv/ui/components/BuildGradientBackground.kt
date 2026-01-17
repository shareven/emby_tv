package com.xxxx.emby_tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import android.content.Context
import com.xxxx.emby_tv.ui.theme.ThemeColor
import com.xxxx.emby_tv.ui.theme.ThemeColorManager

@Composable
fun BuildGradientBackground(
    context: Context,
    themeColor: ThemeColor = ThemeColorManager.getThemeColorById(context, "purple"), // 默认使用紫罗兰色
    content: @Composable () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    0.0f to themeColor.primaryDark,
                    1.0f to themeColor.secondaryLight,
                    start = Offset.Zero,
                    end = Offset.Infinite
                )
            )
    ) {
        content()
    }
}