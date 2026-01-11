package com.xxxx.emby_tv.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // Colors matching Flutter's Colors.blueAccent and Colors.purpleAccent
    // Flutter BlueAccent (primary value) is roughly 0xFF448AFF
    // Flutter PurpleAccent (primary value) is roughly 0xFFE040FB
    
    val blueAlpha = 0.5f + 0.2f * alphaAnim
    val purpleAlpha = 0.5f + 0.2f * (1f - alphaAnim)

    val color1 = Color(0xFF448AFF).copy(alpha = blueAlpha)
    val color2 = Color(0xFFE040FB).copy(alpha = purpleAlpha)

    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(color1, color2),
                    start = Offset.Zero,
                    end = Offset.Infinite, // Approximate TopLeft to BottomRight, but infinite depends on size. 
                    // Using standard linear gradient logic in Compose usually maps start/end to coords.
                    // For full container TopLeft to BottomRight, we need layout info or just use default linear gradient angle?
                    // Brush.linearGradient default creates coordinates based on size if we don't specify, 
                    // or we can specify Offset(0f, 0f) to Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY) 
                    // which Compose interprets as fill area. 
                    // Let's rely on standard linear gradient behavior.
                )
            )
    ) {
        content()
    }
}
