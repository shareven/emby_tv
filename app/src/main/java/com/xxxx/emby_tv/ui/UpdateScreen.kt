package com.xxxx.emby_tv.ui

import android.app.Activity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.xxxx.emby_tv.AppUpdateManager
import com.xxxx.emby_tv.R
import com.xxxx.emby_tv.ui.components.Loading
import com.xxxx.emby_tv.ui.viewmodel.MainViewModel

@Composable
fun UpdateScreen(
    mainViewModel: MainViewModel
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val updateManager = remember { AppUpdateManager(activity) }
    var downloadProgress by remember { mutableStateOf(0) }
    var isDownloading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    LaunchedEffect(mainViewModel.downloadUrl) {
        if (mainViewModel.downloadUrl.isNotEmpty() && !isDownloading) {
            isDownloading = true

            updateManager.startUpdate(
                downloadUrl = mainViewModel.downloadUrl,
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
                drawRect(color = trackColor, size = size)
                drawRect(
                    color = color,
                    size = size.copy(width = width * downloadProgress / 100)
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

        if (mainViewModel.newVersion.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(horizontal = 16.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.current_version, mainViewModel.currentVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.latest_version, mainViewModel.newVersion),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (mainViewModel.updateLog.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.update_log),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = mainViewModel.updateLog ,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
