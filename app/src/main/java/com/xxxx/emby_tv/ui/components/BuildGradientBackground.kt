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
    // 使用主题色定义渐变背景
    val color1 = themeColor.primaryDark
    val color2 = themeColor.secondaryLight


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(color1, color2),
                    // 对应 Flutter 的 stops: [0.3, 0.7]
                    start = Offset.Zero,
                    end = Offset.Infinite // 自动适配任何屏幕分辨率 (1080p 或 4K)
                )
            )
    ) {
        content()
    }
}