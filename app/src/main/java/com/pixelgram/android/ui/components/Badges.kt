@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pixelgram.android.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import com.pixelgram.android.ui.components.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import coil.compose.AsyncImage
import com.pixelgram.android.LocalGraph
import com.pixelgram.android.R
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

@Composable
fun NameBadges(userId: Long, verified: Boolean, size: Dp = 18.dp, scam: Boolean = false, fake: Boolean = false, name: String = "", label: Boolean = false, official: Boolean = false) {
    val statusGraph = LocalGraph.current
    val status = statusGraph.store.snapshot.collectAsState().value.users[userId]?.emojiStatus ?: 0L
    if (status != 0L) {
        Box(
            Modifier
                .padding(start = 4.dp)
                .clickable(interactionSource = null, indication = null) {
                    com.pixelgram.android.feature.premium.EmojiStatusSheet.userId = userId
                    com.pixelgram.android.feature.premium.EmojiStatusSheet.emojiId = status
                },
        ) { CustomEmojiView(status, "⭐", size = size, tint = MaterialTheme.colorScheme.primary) }
    }
    if (official) OfficialBadge(size)
    if (verified) VerifiedBadge(size)
    if (scam || fake) {
        Box(
            Modifier.padding(start = 4.dp).border(1.dp, MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
        ) {
            Text(
                stringResource(if (scam) R.string.badge_scam else R.string.badge_fake),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            )
        }
    }
}

@Composable
fun OfficialBadge(size: Dp = 18.dp) {
    val scheme = MaterialTheme.colorScheme
    val notice = stringResource(R.string.badge_official)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(size)
            .clip(MaterialShapes.Cookie12Sided.toShape())
            .background(scheme.primary)
            .clickable(interactionSource = null, indication = null) { Notice.show(notice) },
    ) {
        Icon(painterResource(R.drawable.ic_pixelgram), contentDescription = notice, tint = scheme.onPrimary, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
fun VerifiedBadge(size: Dp = 18.dp) {
    val scheme = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(start = 4.dp).size(size).clip(MaterialShapes.Cookie12Sided.toShape()).background(scheme.primary),
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(size * 0.62f))
    }
}

object Notice {
    var text by androidx.compose.runtime.mutableStateOf<String?>(null)
    var stamp by androidx.compose.runtime.mutableLongStateOf(0L)

    fun show(value: String) {
        text = value
        stamp = System.currentTimeMillis()
    }
}

@Composable
fun NoticeHost(modifier: Modifier = Modifier) {
    val shown = Notice.text
    androidx.compose.runtime.LaunchedEffect(Notice.stamp) {
        if (shown != null) {
            kotlinx.coroutines.delay(NoticeMs)
            Notice.text = null
        }
    }
    var last by remember { androidx.compose.runtime.mutableStateOf("") }
    if (shown != null) last = shown
    androidx.compose.animation.AnimatedVisibility(
        visible = shown != null,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.85f) + androidx.compose.animation.slideInVertically { it / 2 },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.9f),
        modifier = modifier,
    ) {
        androidx.compose.material3.Surface(
            onClick = { Notice.text = null },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shadowElevation = 6.dp,
        ) {
            Text(last, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
        }
    }
}

private const val NoticeMs = 3_000L

