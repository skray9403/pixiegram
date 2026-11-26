package com.pixelgram.android.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Immutable
data class MotionSpec(
    val micro: SpringSpec<Float> = spring(dampingRatio = 0.88f, stiffness = 1100f),
    val spatial: SpringSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 780f),
    val expressive: SpringSpec<Float> = spring(dampingRatio = 0.78f, stiffness = 680f),
    val dismiss: SpringSpec<Float> = spring(dampingRatio = 0.90f, stiffness = 900f),
    val microDp: SpringSpec<Dp> = spring(dampingRatio = 0.88f, stiffness = 1100f),
    val spatialDp: SpringSpec<Dp> = spring(dampingRatio = 0.82f, stiffness = 780f),
    val expressiveDp: SpringSpec<Dp> = spring(dampingRatio = 0.78f, stiffness = 680f),
    val dismissDp: SpringSpec<Dp> = spring(dampingRatio = 0.90f, stiffness = 900f),
    val spatialSize: SpringSpec<IntSize> = spring(dampingRatio = 0.82f, stiffness = 780f),
    val dismissSize: SpringSpec<IntSize> = spring(dampingRatio = 0.90f, stiffness = 900f),
)

object MotionTokens {
    val Default = MotionSpec()
    const val FrameBudgetMs: Double = 8.33
    const val MaxTouchToVisualMs: Long = 16L
    const val PredictiveBackMinScale: Float = 0.90f
    val PredictiveBackCornerRadius: Dp = 24.dp
    val FabCollapseScrollThreshold: Dp = 16.dp
    const val NoBounceDamping: Float = Spring.DampingRatioNoBouncy
}
