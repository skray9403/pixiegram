package com.pixelgram.android.ui.components

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos

import androidx.compose.ui.text.input.ImeAction

import androidx.compose.ui.platform.LocalSoftwareKeyboardController

import androidx.compose.ui.graphics.SolidColor

import androidx.compose.ui.focus.focusRequester

import androidx.compose.ui.focus.FocusRequester

import androidx.compose.material.icons.rounded.Search

import androidx.compose.material.icons.rounded.Close

import androidx.compose.material.icons.automirrored.rounded.ArrowBack

import androidx.compose.foundation.text.KeyboardOptions

import androidx.compose.foundation.text.KeyboardActions

import androidx.compose.foundation.text.BasicTextField

import androidx.compose.animation.togetherWith

import androidx.compose.animation.AnimatedContent

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
    inset: Dp = 5.dp,
    spacing: Dp = 2.dp,
    height: Dp = 50.dp,
    isFabCollapsed: Boolean = true,
    fabLabel: String = "Search",
    fabIcon: ImageVector = Icons.Outlined.Add,
    onFabClick: (() -> Unit)? = null,
    searchExpanded: Boolean = false,
    searchQuery: String = "",
    searchPlaceholder: String = "Search",
    onSearchOpen: (() -> Unit)? = null,
    onSearchQueryChange: (String) -> Unit = {},
    onSearchClose: (() -> Unit)? = null,
) {
    val cappedDestinations = remember(destinations) { destinations.take(3) }
    val motion = com.pixelgram.android.ui.theme.LocalMotionSpec.current
    val haptics = LocalHapticFeedback.current
    var dragPx by remember { mutableFloatStateOf(0f) }
    val activeIndex = cappedDestinations.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    val itemHeight = (height - inset * 2).coerceAtLeast(38.dp)
    val fastSpring = spring<Float>(dampingRatio = 0.80f, stiffness = 1000f)
    val keyboard = LocalSoftwareKeyboardController.current
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            withFrameNanos { }
            searchFocus.requestFocus()
            keyboard?.show()
        } else keyboard?.hide()
    }

    AnimatedContent(
        targetState = searchExpanded,
        transitionSpec = {
            (fadeIn() + expandHorizontally(spring(dampingRatio = 0.80f, stiffness = 1000f))) togetherWith
                (fadeOut() + shrinkHorizontally(spring(dampingRatio = 0.80f, stiffness = 1000f)))
        },
        label = "dock-search-morph",
        modifier = modifier.fillMaxWidth().navigationBarsPadding().then(if (searchExpanded) Modifier.imePadding() else Modifier).padding(horizontal = 16.dp, vertical = 6.dp),
    ) { searching ->
        if (searching) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(height).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
                    androidx.compose.material3.IconButton(
                        onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSearchClose?.invoke() },
                        modifier = Modifier.size(40.dp),
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close search") }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) Text(searchPlaceholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f).focusRequester(searchFocus).padding(horizontal = 4.dp),
                    )
                    if (searchQuery.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        } else {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cappedDestinations.isNotEmpty()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = CircleShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .weight(1f)
                .height(height)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .pointerInput(cappedDestinations.size, selected) {
                    val threshold = 40.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { dragPx = 0f },
                        onDragEnd = { dragPx = 0f },
                        onDragCancel = { dragPx = 0f },
                        onHorizontalDrag = { change, delta ->
                            change.consume()
                            dragPx += delta
                            if (dragPx <= -threshold && activeIndex < cappedDestinations.lastIndex) {
                                dragPx = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(cappedDestinations[activeIndex + 1].key)
                            } else if (dragPx >= threshold && activeIndex > 0) {
                                dragPx = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(cappedDestinations[activeIndex - 1].key)
                            }
                        },
                    )
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = inset),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                cappedDestinations.forEachIndexed { index, dest ->
                    val isSelected = index == activeIndex
                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 900f),
                        label = "dock-pill",
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = 900f),
                        label = "dock-fg",
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        animationSpec = fastSpring,
                        label = "dock-scale",
                    )
                    val padH by animateDpAsState(
                        targetValue = if (isSelected) 12.dp else 8.dp,
                        animationSpec = motion.spatialDp,
                        label = "dock-pad",
                    )

                    Row(
                        modifier = Modifier
                            .weight(if (isSelected) 1.2f else 1f)
                            .height(itemHeight)
                            .clip(CircleShape)
                            .background(pillColor)
                            .clickable(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(dest.key)
                                },
                            )
                            .padding(horizontal = padH),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale }) {
                            if (dest.badge > 0) {
                                BadgedBox(badge = { Badge { Text(if (dest.badge > 99) "99+" else dest.badge.toString()) } }) {
                                    Icon(
                                        imageVector = if (isSelected) dest.selectedIcon else dest.icon,
                                        contentDescription = dest.contentDescription,
                                        tint = contentColor,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (isSelected) dest.selectedIcon else dest.icon,
                                    contentDescription = dest.contentDescription,
                                    tint = contentColor,
                                    modifier = Modifier.size(19.dp),
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(motion.micro) + expandHorizontally(animationSpec = motion.spatialSize),
                            exit = fadeOut(motion.micro) + shrinkHorizontally(animationSpec = motion.spatialSize),
                        ) {
                            Row {
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = dest.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = contentColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        if (onFabClick != null || onSearchOpen != null) {
            Surface(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    (onSearchOpen ?: onFabClick)?.invoke()
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .height(height)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .animateContentSize(motion.spatialSize),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = if (isFabCollapsed) 14.dp else 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = fabIcon,
                        contentDescription = fabLabel,
                        modifier = Modifier.size(19.dp),
                    )
                    AnimatedVisibility(
                        visible = !isFabCollapsed,
                        enter = fadeIn(motion.micro) + expandHorizontally(animationSpec = motion.spatialSize),
                        exit = fadeOut(motion.micro) + shrinkHorizontally(animationSpec = motion.spatialSize),
                    ) {
                        Row {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = fabLabel,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}
    }