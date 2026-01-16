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
 * 跳过片头按钮组件，带圆环倒计时动画
 *
 * @param introEndMs 片头结束时间（毫秒）
 * @param onSkip 点击跳过回调
 * @param onTimeout 倒计时结束回调
 * @param countdownSeconds 倒计时秒数，默认 5 秒
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SkipIntroButton(
    introEndMs: Long,
    onSkip: () -> Unit,
    onTimeout: () -> Unit,
    countdownSeconds: Int = 5
) {
    val skipButtonFocusRequester = remember { FocusRequester() }
    
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
            // 跳过片头按钮
            Surface(
                onClick = onSkip,
                modifier = Modifier
                    .height(48.dp)
                    .focusRequester(skipButtonFocusRequester),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
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
                        text = stringResource(R.string.skip_intro),
                        fontSize = 16.sp,
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
    
    // 自动聚焦到跳过按钮
    LaunchedEffect(Unit) {
        delay(100)
        skipButtonFocusRequester.requestFocus()
    }
}
