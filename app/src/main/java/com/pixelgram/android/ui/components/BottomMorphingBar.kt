package com.pixelgram.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class MorphNavDestination<K>(
    val key: K,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
    val badge: Int = 0,
    val contentDescription: String = label,
)

@Composable
fun <K> FloatingMorphingNavigationDock(
    destinations: List<MorphNavDestination<K>>,
    selected: K,
    onSelect: (K) -> Unit,
    modifier: Modifier = Modifier,
    inset: Dp = 8.dp,
    spacing: Dp = 4.dp,
    height: Dp = 64.dp,
    isFabCollapsed: Boolean = false,
    fabLabel: String = "New",
    fabIcon: ImageVector = Icons.Outlined.Add,
    onFabClick: (() -> Unit)? = null,
) {
    val motion = com.pixelgram.android.ui.theme.LocalMotionSpec.current
    val haptics = LocalHapticFeedback.current
    var dragPx by remember { mutableFloatStateOf(0f) }
    val activeIndex = destinations.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    val itemHeight = (height - inset * 2).coerceAtLeast(44.dp)
    Row(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.weight(1f).height(height)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .pointerInput(destinations.size, selected) {
                    val threshold = 56.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { dragPx = 0f },
                        onDragEnd = { dragPx = 0f },
                        onDragCancel = { dragPx = 0f },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            dragPx += delta
                            if (dragPx <= -threshold && activeIndex < destinations.lastIndex) {
                                dragPx = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(destinations[activeIndex + 1].key)
                            } else if (dragPx >= threshold && activeIndex > 0) {
                                dragPx = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(destinations[activeIndex - 1].key)
                            }
                        },
                    )
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = inset),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                destinations.forEach { destination ->
                    val chosen = destination.key == selected
                    MorphNavTabItem(
                        destination = destination,
                        selected = chosen,
                        onClick = {
                            if (!chosen) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(destination.key)
                            }
                        },
                        modifier = Modifier.weight(if (chosen) 1.45f else 1f).height(itemHeight)
                            .animateContentSize(animationSpec = motion.spatialSize),
                    )
                }
            }
        }
        if (onFabClick != null) {
            val corner by animateDpAsState(if (isFabCollapsed) 28.dp else 20.dp, if (isFabCollapsed) motion.dismissDp else motion.spatialDp, label = "dock_fab_corner")
            val scale by animateFloatAsState(if (isFabCollapsed) 0.96f else 1f, motion.micro, label = "dock_fab_scale")
            Row(
                modifier = Modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(corner)).background(MaterialTheme.colorScheme.primary)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corner))
                    .animateContentSize(animationSpec = motion.spatialSize)
                    .clickable { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onFabClick() }
                    .padding(horizontal = if (isFabCollapsed) 18.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(fabIcon, fabLabel, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                AnimatedVisibility(
                    visible = !isFabCollapsed,
                    enter = expandHorizontally(animationSpec = motion.spatialSize) + fadeIn(animationSpec = motion.micro),
                    exit = shrinkHorizontally(animationSpec = motion.dismissSize) + fadeOut(animationSpec = motion.micro),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        Text(fabLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun <K> MorphNavTabItem(
    destination: MorphNavDestination<K>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = com.pixelgram.android.ui.theme.LocalMotionSpec.current
    val pill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        spring(dampingRatio = 0.64f, stiffness = 320f),
        label = "nav_pill_color",
    )
    val ink by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        motion.micro,
        label = "nav_content_color",
    )
    Box(
        modifier = modifier.clip(CircleShape).background(pill).clickable(onClick = onClick)
            .padding(horizontal = if (selected) 14.dp else 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            BadgedBox(
                badge = {
                    if (destination.badge > 0) Badge {
                        AnimatedCounter(
                            destination.badge,
                            androidx.compose.material3.LocalTextStyle.current,
                            format = { n -> com.pixelgram.android.ui.theme.countLabel(n, com.pixelgram.android.ui.theme.LocalAppearance.current.exactNumbers) },
                        )
                    }
                },
            ) {
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.icon,
                    contentDescription = destination.contentDescription,
                    tint = ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(animationSpec = motion.spatialSize) + fadeIn(animationSpec = motion.micro),
                exit = shrinkHorizontally(animationSpec = motion.dismissSize) + fadeOut(animationSpec = motion.micro),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(6.dp))
                    Text(destination.label, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
