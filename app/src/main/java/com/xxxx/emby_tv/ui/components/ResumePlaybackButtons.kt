package com.xxxx.emby_tv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.xxxx.emby_tv.R
import kotlinx.coroutines.delay

/**
 * 继续播放/从头开始 按钮组件，带圆环倒计时动画
 *
 * @param countdownSeconds 倒计时秒数
 * @param onPlayFromStart 点击"从头开始"回调
 * @param onContinue 点击"继续播放"回调
 * @param onTimeout 倒计时结束回调
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ResumePlaybackButtons(
    countdownSeconds: Int = 3,
    onPlayFromStart: () -> Unit,
    onContinue: () -> Unit,
    onTimeout: () -> Unit
) {
    val continueButtonFocusRequester = remember { FocusRequester() }
    
    // 倒计时动画状态
    var currentSeconds by remember { mutableIntStateOf(countdownSeconds) }
    val animatedProgress = remember { Animatable(1f) }
    var hasStarted by remember { mutableStateOf(false) }
    
    // 倒计时动画
    LaunchedEffect(Unit) {
        if (!hasStarted) {
            hasStarted = true
            // 启动圆环进度动画
            animatedProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = countdownSeconds * 1000, easing = LinearEasing)
            )
            onTimeout()
        }
    }
    
    // 倒计时数字更新
    LaunchedEffect(Unit) {
        currentSeconds = countdownSeconds
        repeat(countdownSeconds - 1) {
            delay(1000)
            currentSeconds--
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 80.dp, end = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 从头开始按钮
            Surface(
                onClick = onPlayFromStart,
                modifier = Modifier.height(48.dp),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))),
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                ),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.White.copy(alpha = 0.7f),
                    contentColor = Color.White,
                    focusedContentColor = Color.Black
                )
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.play_from_start),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }


            // 继续播放按钮
            Surface(
                onClick = onContinue,
                modifier = Modifier
                    .height(48.dp)
                    .focusRequester(continueButtonFocusRequester),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(28.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))),
                    focusedBorder = Border(BorderStroke(2.dp, Color.White))
                ),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.1f),
                    focusedContainerColor = Color.White.copy(alpha = 0.9f),
                    contentColor = Color.White,
                    focusedContentColor = Color.Black
                )
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.continue_playback),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }


            // 圆环倒计时
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                // 背景圆环（灰色）
                Canvas(modifier = Modifier.size(44.dp)) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.2f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                // 进度圆环（白色）
                Canvas(modifier = Modifier.size(44.dp)) {
                    drawArc(
                        color = Color.White,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress.value,
                        useCenter = false,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                // 倒计时数字
                Text(
                    text = "${currentSeconds}s",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    
    // 自动聚焦到继续播放按钮
    LaunchedEffect(Unit) {
        delay(100)
        continueButtonFocusRequester.requestFocus()
    }
}
