package com.xxxx.emby_tv.ui.theme

import androidx.compose.ui.graphics.Color
import android.content.Context
import androidx.compose.runtime.Composable

data class ThemeColor(
    val id: String,
    val name: String,
    val primary: Color,
    val primaryLight: Color,
    val primaryDark: Color,
    val secondary: Color,
    val secondaryLight: Color,
    val secondaryDark: Color
)



object ThemeColorManager {
    
    @Composable
    fun getThemeColors(context: Context) = listOf(
        ThemeColor(
            id = "rose",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_rose),
            primary = Color(0xFFe91e63), // 玫瑰红
            primaryLight = Color(0xFFf06292),
            primaryDark = Color(0xFFc2185b),
            // 模仿 purple 逻辑：配深靛蓝 (Rose -> Indigo) 产生红蓝冷暖对撞
            secondary = Color(0xFF3f51b5),
            secondaryLight = Color(0xFF7986cb),
            secondaryDark = Color(0xFF303f9f)
        ),
        ThemeColor(
            id = "blue",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_blue),
            primary = Color(0xFF2196f3), // 蓝色
            primaryLight = Color(0xFF64b5f6),
            primaryDark = Color(0xFF1976d2),
            // 模仿 purple 逻辑：配紫色 (Blue -> Purple)
            secondary = Color(0xFF9c27b0),
            secondaryLight = Color(0xFFba68c8),
            secondaryDark = Color(0xFF7b1fa2)
        ),
        ThemeColor(
            id = "purple", // 参照标准
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_purple),
            primary = Color(0xFF9c27b0),
            primaryLight = Color(0xFFba68c8),
            primaryDark = Color(0xFF7b1fa2),
            secondary = Color(0xFFe91e63),
            secondaryLight = Color(0xFFf06292),
            secondaryDark = Color(0xFFc2185b)
        ),
        ThemeColor(
            id = "teal",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_teal),
            primary = Color(0xFF009688), // 青色
            primaryLight = Color(0xFF4db6ac),
            primaryDark = Color(0xFF00796b),
            // 模仿 purple 逻辑：配翠绿色 (Teal -> Green)
            secondary = Color(0xFF4caf50),
            secondaryLight = Color(0xFF81c784),
            secondaryDark = Color(0xFF388e3c)
        ),

        ThemeColor(
            id = "indigo",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_indigo),
            primary = Color(0xFF3f51b5), // 靛蓝
            primaryLight = Color(0xFF7986cb),
            primaryDark = Color(0xFF303f9f),
            // 模仿 purple 逻辑：配青色 (Indigo -> Teal)
            secondary = Color(0xFF009688),
            secondaryLight = Color(0xFF4db6ac),
            secondaryDark = Color(0xFF00796b)
        ),
        ThemeColor(
            id = "green",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_green),
            primary = Color(0xFF4caf50), // 绿色
            primaryLight = Color(0xFF81c784),
            primaryDark = Color(0xFF388e3c),
            // 模仿 purple 逻辑：配琥珀黄 (Green -> Amber) 像热带水果
            secondary = Color(0xFFffc107),
            secondaryLight = Color(0xFFEABB2A),
            secondaryDark = Color(0xFFff8f00)
        ),
        ThemeColor(
            id = "orange",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_orange),
            primary = Color(0xFFff9800), // 橙色
            primaryLight = Color(0xFFffb74d),
            primaryDark = Color(0xFFf57c00),
            // 【重改】配 亮青/薄荷绿 (Orange -> Bright Teal)
            // 这种组合像冰淇淋，清新且非常有视觉张力，完全不闷
            secondary = Color(0xFF00BFA5),
            secondaryLight = Color(0xFF5DF2D6),
            secondaryDark = Color(0xFF008E76)
        ),
        ThemeColor(
            id = "amber",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_amber),
            primary = Color(0xFFffc107), // 琥珀金
            primaryLight = Color(0xFFF3BD1B),
            primaryDark = Color(0xFFCC7607),
            // 【重改】配 亮紫色 (Amber -> Bright Purple)
            // 就像紫配红一样，黄配紫是极高饱和度的时尚撞色，非常显眼
            secondary = Color(0xFFD500F9),
            secondaryLight = Color(0xFFE040FB),
            secondaryDark = Color(0xFFAA00FF)
        ),

    )


    @Composable
    fun getThemeColorById(context: Context, id: String): ThemeColor {
        return getThemeColors(context).find { it.id == id } ?: getThemeColors(context)[0]
    }
}