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
            primary = Color(0xFFe91e63),
            primaryLight = Color(0xFFf06292),
            primaryDark = Color(0xFFc2185b),
            secondary = Color(0xFFff4081),
            secondaryLight = Color(0xFFff79b0),
            secondaryDark = Color(0xFFc60055)
        ),
        ThemeColor(
            id = "blue",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_blue),
            primary = Color(0xFF2196f3),
            primaryLight = Color(0xFF64b5f6),
            primaryDark = Color(0xFF1976d2),
            secondary = Color(0xFF03a9f4),
            secondaryLight = Color(0xFF4fc3f7),
            secondaryDark = Color(0xFF0288d1)
        ),
        ThemeColor(
            id = "purple",
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
            primary = Color(0xFF009688),
            primaryLight = Color(0xFF4db6ac),
            primaryDark = Color(0xFF00796b),
            secondary = Color(0xFF26a69a),
            secondaryLight = Color(0xFF80cbc4),
            secondaryDark = Color(0xFF00695c)
        ),
        ThemeColor(
            id = "orange",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_orange),
            primary = Color(0xFFff9800),
            primaryLight = Color(0xFFffb74d),
            primaryDark = Color(0xFFf57c00),
            secondary = Color(0xFFff5722),
            secondaryLight = Color(0xFFff8a65),
            secondaryDark = Color(0xFFe64a19)
        ),
        ThemeColor(
            id = "indigo",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_indigo),
            primary = Color(0xFF3f51b5),
            primaryLight = Color(0xFF7986cb),
            primaryDark = Color(0xFF303f9f),
            secondary = Color(0xFF5c6bc0),
            secondaryLight = Color(0xFF9fa8da),
            secondaryDark = Color(0xFF3949ab)
        ),
        ThemeColor(
            id = "green",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_green),
            primary = Color(0xFF4caf50),
            primaryLight = Color(0xFF81c784),
            primaryDark = Color(0xFF388e3c),
            secondary = Color(0xFF8bc34a),
            secondaryLight = Color(0xFFaed581),
            secondaryDark = Color(0xFF689f38)
        ),
        ThemeColor(
            id = "amber",
            name = context.getString(com.xxxx.emby_tv.R.string.theme_color_amber),
            primary = Color(0xFFffc107),
            primaryLight = Color(0xFFffecb3),
            primaryDark = Color(0xFFff8f00),
            secondary = Color(0xFFffab00),
            secondaryLight = Color(0xFFffd54f),
            secondaryDark = Color(0xFFff6f00)
        )
    )

    @Composable
    fun getThemeColorById(context: Context, id: String): ThemeColor {
        return getThemeColors(context).find { it.id == id } ?: getThemeColors(context)[0]
    }
}