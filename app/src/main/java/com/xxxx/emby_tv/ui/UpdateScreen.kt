package com.xxxx.emby_tv.ui

import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.tv.material3.*
import com.xxxx.emby_tv.AppModel
import com.xxxx.emby_tv.AppUpdateManager
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.ui.components.Loading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun UpdateScreen(
    appModel: AppModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val updateManager = remember { AppUpdateManager(activity) }
    var downloadProgress by remember { mutableStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(appModel.downloadUrl) {
        if (appModel.downloadUrl.isNotEmpty() && !isDownloading) {
            isDownloading = true

            updateManager.startUpdate(
                downloadUrl = appModel.downloadUrl,
                onProgress = { progress ->
                    if (progress > 0) {
                        downloadProgress = progress
                    }
                },
                onSuccess = {
                    isDownloading = false
                },
                onError = { error ->
                    isDownloading = false
                    errorMessage = error
                }
            )

        }

    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.update_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (isDownloading) {
            Text(
                text = "$downloadProgress%",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            val color = MaterialTheme.colorScheme.onSurface
            val trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(8.dp)
            ) {
                val width = size.width
                val height = size.height
                // 绘制背景
                drawRect(color = trackColor, size = size)
                // 绘制进度（无任何额外偏移或点）
                drawRect(
                    color = color,
                    size = size.copy(width = width * downloadProgress/100)
                )
            }
            if (errorMessage.isNotEmpty()) Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Red,
                modifier = Modifier.padding(bottom = 16.dp)
            )

        } else {
            Loading()
        }

        Spacer(modifier = Modifier.height(32.dp))


    }

}
