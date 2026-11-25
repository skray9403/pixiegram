package com.pixelgram.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun PredictiveBackFadeOverlay(
    progress: () -> Float,
    swipeFromLeft: () -> Boolean,
    scrim: () -> Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val fromLeft = swipeFromLeft()
    val edgeBrush = remember(fromLeft) {
        if (fromLeft) {
            Brush.horizontalGradient(
                0.00f to Color.Black,
                0.22f to Color.Black.copy(alpha = 0.35f),
                0.55f to Color.Transparent,
            )
        } else {
            Brush.horizontalGradient(
                0.45f to Color.Transparent,
                0.78f to Color.Black.copy(alpha = 0.35f),
                1.00f to Color.Black,
            )
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        content()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = (0.36f * progress().coerceIn(0f, 1f) * scrim().coerceIn(0f, 1f)).coerceIn(0f, 0.45f)
                }
                .background(edgeBrush),
        )
    }
}
