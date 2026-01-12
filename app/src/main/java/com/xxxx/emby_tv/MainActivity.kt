package com.xxxx.emby_tv

import Emby_tvTheme
import com.xxxx.emby_tv.ui.LibraryScreen
import com.xxxx.emby_tv.ui.LoginScreen
import com.xxxx.emby_tv.ui.PlayerScreen


import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.gson.Gson
import com.xxxx.emby_tv.ui.MediaDetailScreen
import com.xxxx.emby_tv.ui.HomeScreen
import com.xxxx.emby_tv.ui.UpdateScreen
import com.xxxx.emby_tv.ui.components.BuildGradientBackground
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.theme.ThemeColorManager


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 强制设置为横屏模式
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        enableEdgeToEdge()
        setContent {


                    EmbyTvApp()


        }
    }
}

@Composable
fun EmbyTvApp() {
    val navController = rememberNavController()
    val appModel: AppModel = viewModel()
    val gson = Gson()

    // 响应式监听AppModel的状态变化
    val isLoaded = appModel.isLoaded
    val isLoggedIn = appModel.isLoggedIn

   

    // 监听状态变化并自动导航
    LaunchedEffect(isLoaded, isLoggedIn) {
        when {
            !isLoaded -> {
                // 如果当前不在加载页面，导航到加载页面
                if (navController.currentDestination?.route != "loading") {
                    navController.navigate("loading") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            isLoggedIn -> {
                // 如果已登录且不在主页，导航到主页
                if (navController.currentDestination?.route != "home") {
                    navController.navigate("home") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            else -> {
                // 如果未登录且不在登录页，导航到登录页
                if (navController.currentDestination?.route != "login") {
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }
        }
    }

    // 获取当前主题色
    val context = LocalContext.current
    val currentThemeColor = ThemeColorManager.getThemeColorById(context, appModel.currentThemeId)

    Emby_tvTheme(themeColor = currentThemeColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BuildGradientBackground(context = context, themeColor = currentThemeColor) {
                NavHost(
                    navController = navController,
                    startDestination = "loading" // 总是从加载页面开始
                ) {
                    // 加载页面
                    composable("loading") {
                        Loading()
                    }
                    // 登录页面
                    composable("login") {
                        LoginScreen(
                            appModel = appModel,
                            navController = navController
                        )
                    }

                    // 主页面
                    composable("home") {
                        HomeScreen(
                            appModel = appModel,
                            navController = navController
                        )
                    }

                    // 更新页面
                    composable("update") {
                        UpdateScreen(
                            appModel = appModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    // 媒体库页面
                    composable(
                        "library/{libraryId}?libraryName={libraryName}&type={type}",
                        arguments = listOf(
                            navArgument("libraryId") { type = NavType.StringType },
                            navArgument("libraryName") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("type") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStackEntry ->
                        val libraryId = backStackEntry.arguments?.getString("libraryId") ?: ""
                        val libraryName = backStackEntry.arguments?.getString("libraryName") ?: ""
                        val type = backStackEntry.arguments?.getString("type") ?: ""
                        LibraryScreen(
                            parentId = libraryId,
                            title = libraryName,
                            type = type,
                            appModel = appModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            },
                            onNavigateToSeries = { seriesId ->
                                navController.navigate("series/$seriesId")
                            }
                        )
                    }

                    // 剧集详情页面
                    composable(
                        "series/{seriesId}",
                        arguments = listOf(navArgument("seriesId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val seriesId = backStackEntry.arguments?.getString("seriesId") ?: ""
                        MediaDetailScreen(
                            seriesId = seriesId,
                            onNavigateToSeries = { nestedSeriesId ->
                                navController.navigate("series/$nestedSeriesId")
                            },
                            onNavigateToPlayer = { mediaItem ->
                                val id = mediaItem.id ?: ""
                                val position = mediaItem.userData?.playbackPositionTicks ?: 0L
                               
                                navController.navigate("player/$id?position=$position")
                            },
                            appModel = appModel
                        )
                    }

                    // 通用媒体详情页面 (修复 media/xxx 路由崩溃问题)
                    composable(
                        "media/{mediaId}",
                        arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                        MediaDetailScreen(
                            seriesId = mediaId, // 复用 MediaDetailScreen，seriesId 参数实际就是 generic mediaId
                            onNavigateToSeries = { nestedSeriesId ->
                                navController.navigate("series/$nestedSeriesId")
                            },
                            onNavigateToPlayer = { mediaItem ->
                                val id = mediaItem.id ?: ""
                                val position = mediaItem.userData?.playbackPositionTicks ?: 0L

                                navController.navigate("player/$id?position=$position")
                            },
                            appModel = appModel
                        )
                    }

                    // 播放器页面 - 使用参数传递避免由巨大JSON导致的崩溃
                    composable(
                        "player/{mediaId}?position={position}",
                        arguments = listOf(
                            navArgument("mediaId") { type = NavType.StringType },
                            navArgument("position") {
                                type = NavType.LongType
                                defaultValue = 0L
                            }
                        )
                    ) { backStackEntry ->
                        val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
                        val playbackPositionTicks =
                            backStackEntry.arguments?.getLong("position") ?: 0L

                        PlayerScreen(
                            mediaId = mediaId,
                            playbackPositionTicks = playbackPositionTicks,
                            appModel = appModel,
                            onBack = {

                                appModel.loadData()
                                navController.popBackStack()
                            },
                            onNavigateToPlayer = { nextItem ->
                                val nextId = nextItem.id ?: ""
                                val nextPos = nextItem.userData?.playbackPositionTicks ?: 0L
                                navController.popBackStack()
                                navController.navigate("player/$nextId?position=$nextPos")
                            }
                        )
                    }
                }
            }
        }
    }
}