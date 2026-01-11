package com.xxxx.emby_tv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局聚焦主题样式定义
 * 统一管理所有组件的聚焦背景样式
 */
object FocusTheme {
    
    /**
     * 主要聚焦背景 - 使用主题色
     */
    @Composable
    fun primaryFocusBackground(): Brush {
        return Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        )
    }
    
    /**
     * 次要聚焦背景 - 使用主题色的淡化版本
     */
    @Composable
    fun secondaryFocusBackground(): Brush {
        return Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )
        )
    }
    
    /**
     * 聚焦边框颜色
     */
    @Composable
    fun focusBorderColor(): Color {
        return MaterialTheme.colorScheme.primary
    }
    
    /**
     * 透明背景（未聚焦状态）
     */
    fun transparentBackground(): Brush {
        return Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.Transparent)
        )
    }
}

/**
 * Modifier扩展函数 - 应用聚焦背景样式
 */
@Composable
fun Modifier.focusBackground(
    isFocused: Boolean,
    shape: Shape = RoundedCornerShape(8.dp),
    focusType: FocusType = FocusType.PRIMARY
): Modifier {
    return this.background(
        brush = if (isFocused) {
            when (focusType) {
                FocusType.PRIMARY -> FocusTheme.primaryFocusBackground()
                FocusType.SECONDARY -> FocusTheme.secondaryFocusBackground()
            }
        } else {
            FocusTheme.transparentBackground()
        },
        shape = shape
    )
}

/**
 * Modifier扩展函数 - 应用聚焦边框样式
 */
@Composable
fun Modifier.focusBorder(
    isFocused: Boolean,
    borderWidth: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(8.dp)
): Modifier {
    return if (isFocused) {
        this.border(
            width = borderWidth,
            color = FocusTheme.focusBorderColor(),
            shape = shape
        )
    } else {
        this
    }
}

/**
 * Modifier扩展函数 - 组合聚焦样式（背景+边框）
 */
@Composable
fun Modifier.focusStyle(
    isFocused: Boolean,
    shape: Shape = RoundedCornerShape(8.dp),
    focusType: FocusType = FocusType.PRIMARY,
    borderWidth: Dp = 2.dp,
    useBackground: Boolean = true,
    useBorder: Boolean = false
): Modifier {
    var modifier = this
    
    if (useBackground) {
        modifier = modifier.focusBackground(isFocused, shape, focusType)
    }
    
    if (useBorder) {
        modifier = modifier.focusBorder(isFocused, borderWidth, shape)
    }
    
    return modifier
}

/**
 * 聚焦类型枚举
 */
enum class FocusType {
    PRIMARY,    // 主要聚焦样式 - 用于重要按钮、卡片等
    SECONDARY   // 次要聚焦样式 - 用于列表项、次要按钮等
}