package com.pixelgram.android.nav


import com.pixelgram.android.ui.components.PredictiveBackFadeOverlay
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.pixelgram.android.AppGraph
import com.pixelgram.android.feature.chat.ChatScreen
import com.pixelgram.android.feature.main.MainShell
import com.pixelgram.android.feature.pixel.PixelSettingsScreen
import com.pixelgram.android.feature.profile.ProfileScreen
import com.pixelgram.android.feature.settings.SessionsScreen
import kotlinx.serialization.Serializable

@Serializable data object MainKey : NavKey
@Serializable data class ChatKey(val chatId: Long, val topicId: Int = 0) : NavKey
@Serializable data class ForumKey(val chatId: Long) : NavKey
@Serializable data class ManageChatKey(val chatId: Long, val page: String = "") : NavKey
@Serializable data class ProfileKey(val userId: Long, val chatId: Long = 0) : NavKey
@Serializable data class PixelSettingsKey(val page: String = "") : NavKey
@Serializable data object SessionsKey : NavKey
@Serializable data object SearchKey : NavKey
@Serializable data class ProfilePhotosKey(val userId: Long) : NavKey
@Serializable data class SettingsSectionKey(val section: String) : NavKey
@Serializable data object PremiumKey : NavKey
@Serializable data object StarsKey : NavKey
@Serializable data object BusinessKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object SavedKey : NavKey
@Serializable data object UsernameKey : NavKey
@Serializable data object StickersKey : NavKey
@Serializable data object ContactsKey : NavKey
@Serializable data object QuickReactionKey : NavKey
@Serializable data class SavedTopicKey(val topicId: Long) : NavKey

object PendingNav {
    var chat by androidx.compose.runtime.mutableStateOf<Long?>(null)
    var link by androidx.compose.runtime.mutableStateOf<String?>(null)
    var jump by androidx.compose.runtime.mutableStateOf<Pair<Long, Long>?>(null)
    var profile by androidx.compose.runtime.mutableStateOf<Long?>(null)
    var external by androidx.compose.runtime.mutableStateOf<String?>(null)
}

object PixelLinks {
    val Pattern = Regex("pg://(settings|premium|stars|business|pixel)(/[A-Za-z_]+)?", RegexOption.IGNORE_CASE)

    fun key(link: String): NavKey? {
        val match = Pattern.matchEntire(link.trim()) ?: return null
        val sub = match.groupValues[2].removePrefix("/")
        return when (match.groupValues[1].lowercase()) {
            "settings" -> if (sub.isEmpty()) SettingsKey else com.pixelgram.android.feature.settings.SettingsSection.entries.firstOrNull { it.name.equals(sub, true) }?.let { SettingsSectionKey(it.name) } ?: SettingsKey
            "premium" -> PremiumKey
            "stars" -> StarsKey
            "business" -> BusinessKey
            "pixel" -> PixelSettingsKey(sub.lowercase())
            else -> null
        }
    }
}

class Navigator(private val stack: MutableList<NavKey>) {
    fun open(key: NavKey) {
        if (EtgBack.matches(key.toString(), EtgBack.hidden)) EtgBack.hidden = null
        stack.add(key)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun toChat(chatId: Long) {
        if (stack.size >= 2 && stack[stack.lastIndex - 1] == ChatKey(chatId)) back() else open(ChatKey(chatId))
    }
}

@Composable
fun AppNavigation(graph: AppGraph, start: List<NavKey> = listOf(MainKey)) {
    val stack = rememberNavBackStack(*start.toTypedArray())
    val navigator = Navigator(stack)
    val backScope = androidx.compose.runtime.rememberCoroutineScope()
    val dispatcher = androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner.current?.navigationEventDispatcher
    val currentStack = androidx.compose.runtime.rememberUpdatedState(stack.toList())
    androidx.compose.runtime.LaunchedEffect(dispatcher) {
        dispatcher?.transitionState?.collect { state ->
            if (state is androidx.navigationevent.NavigationEventTransitionState.InProgress) {
                EtgBack.progress(state.latestEvent, currentStack.value)
            } else {
                EtgBack.released(backScope)
            }
        }
    }
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLowest)) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize().androidx_swipeBack(currentStack, backScope) { navigator.back() },
    ) {
    NavDisplay(
        backStack = stack,
        onBack = {
            if (EtgBack.phase == EtgBack.Drag || EtgBack.phase == EtgBack.Cancel) EtgBack.commit(backScope)
            navigator.back()
        },
        transitionSpec = {
            if (EtgBack.phase != EtgBack.Idle) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            else NavMotion.forward()
        },
        popTransitionSpec = {
            if (EtgBack.phase != EtgBack.Idle) androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            else NavMotion.backward()
        },
        predictivePopTransitionSpec = {
            fadeIn(tween(HoldMs), initialAlpha = 0.999f) togetherWith fadeOut(tween(HoldMs), targetAlpha = 0.999f)
        },
        entryDecorators = listOf(
            androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator(),
            remember { EtgBackDecorator() },
        ),
        entryProvider = entryProvider {
            entry<MainKey> { MainShell(graph, navigator) }
            entry<ChatKey> { key -> ChatScreen(graph, key.chatId, navigator, key.topicId) }
            entry<ForumKey> { key -> com.pixelgram.android.feature.chat.ForumScreen(graph, key.chatId, navigator) }
            entry<ManageChatKey> { key -> com.pixelgram.android.feature.admin.ManageChatScreen(graph, key.chatId, key.page, navigator) }
            entry<ProfileKey> { key -> ProfileScreen(graph, key.userId, key.chatId, navigator) }
            entry<ContactsKey> { com.pixelgram.android.feature.contacts.ContactsTab(graph, navigator, onOpenDrawer = null, onBack = { navigator.back() }) }
            entry<StickersKey> { com.pixelgram.android.feature.settings.StickersSettingsScreen(graph, navigator) }
            entry<QuickReactionKey> { com.pixelgram.android.feature.settings.QuickReactionScreen(graph, navigator) }
            entry<SavedKey> { com.pixelgram.android.feature.profile.SavedScreen(graph, navigator) }
            entry<SavedTopicKey> { key -> com.pixelgram.android.feature.profile.SavedTopicScreen(graph, navigator, key.topicId) }
            entry<PixelSettingsKey> { key -> PixelSettingsScreen(graph, key.page, navigator) }
            entry<SessionsKey> { SessionsScreen(graph, navigator) }
            entry<UsernameKey> { com.pixelgram.android.feature.settings.UsernameScreen(graph, navigator) }
            entry<SearchKey> { com.pixelgram.android.feature.search.SearchScreen(graph, navigator) }
            entry<ProfilePhotosKey> { key -> com.pixelgram.android.feature.profile.ProfilePhotosScreen(graph, key.userId, navigator) }
            entry<SettingsSectionKey> { key -> com.pixelgram.android.feature.settings.SettingsSectionScreen(graph, key.section, navigator) }
            entry<PremiumKey> { com.pixelgram.android.feature.premium.PremiumScreen(graph, navigator) }
            entry<SettingsKey> { com.pixelgram.android.feature.settings.SettingsTab(graph, navigator, onOpenDrawer = null, onBack = { navigator.back() }) }
            entry<BusinessKey> { com.pixelgram.android.feature.premium.PremiumScreen(graph, navigator, business = true) }
            entry<StarsKey> { com.pixelgram.android.feature.premium.StarsScreen(graph, navigator) }
        },
    )
    }
    com.pixelgram.android.feature.player.FullPlayerOverlay(graph)
    com.pixelgram.android.feature.stories.StoryOverlays(graph)
    com.pixelgram.android.feature.chat.GiftSheetHost()
    com.pixelgram.android.feature.chat.TagSheetHost()
    InviteJoinHost(graph, navigator)
    com.pixelgram.android.feature.security.SensitiveRequestHost()
    com.pixelgram.android.feature.premium.PaidMessageHost(graph)
    com.pixelgram.android.feature.security.PasscodeLockHost()
    com.pixelgram.android.feature.pixel.SupportSheet()
    com.pixelgram.android.feature.chat.WebAppHost()
    com.pixelgram.android.feature.chat.EmojiPreviewHost()
    com.pixelgram.android.feature.chat.DocumentViewerHost()
    com.pixelgram.android.feature.premium.PaymentHost()
    com.pixelgram.android.feature.premium.EmojiStatusHost { navigator.open(PremiumKey) }
    val linkContext = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(PendingNav.link) {
        PendingNav.link?.let { link ->
            PendingNav.link = null
            if (com.pixelgram.android.feature.pixel.SupportProject.LinkPattern.matches(link.trim())) {
                com.pixelgram.android.feature.pixel.SupportProject.open = true
                return@let
            }
            (if (SettingsLinks.matches(link)) SettingsLinks.resolve(linkContext, link) else PixelLinks.key(link))?.let { navigator.open(it) }
        }
    }
    val externalOpener = rememberLinkOpener(graph, navigator)
    LinkConfirmHost { externalOpener.url(it) }
    androidx.compose.runtime.LaunchedEffect(PendingNav.external) {
        PendingNav.external?.let { url ->
            PendingNav.external = null
            externalOpener.url(url)
        }
    }
    androidx.compose.runtime.LaunchedEffect(PendingNav.profile) {
        PendingNav.profile?.let { id ->
            PendingNav.profile = null
            navigator.open(ProfileKey(id))
        }
    }
    androidx.compose.runtime.LaunchedEffect(PendingNav.chat) {
        PendingNav.chat?.let { id ->
            PendingNav.chat = null
            navigator.open(ChatKey(id))
        }
    }
    com.pixelgram.android.feature.pixel.CreditsOverlay()
    com.pixelgram.android.ui.components.NoticeHost(
        androidx.compose.ui.Modifier
            .align(androidx.compose.ui.Alignment.BottomCenter)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.navigationBars)
            .padding(bottom = 96.dp, start = 24.dp, end = 24.dp)
            .zIndex(30f),
    )
    }
}

private const val HoldMs = 600

private fun androidx.compose.ui.Modifier.androidx_swipeBack(
    stack: androidx.compose.runtime.State<List<NavKey>>,
    scope: kotlinx.coroutines.CoroutineScope,
    onBack: () -> Unit,
): androidx.compose.ui.Modifier = this.then(
    androidx.compose.ui.Modifier.pointerInput(Unit) {
        val velocity = androidx.compose.ui.input.pointer.util.VelocityTracker()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (stack.value.size < 2 || EtgBack.phase != EtgBack.Idle) return@awaitEachGesture
            var dragged = false
            var total = androidx.compose.ui.geometry.Offset.Zero
            velocity.resetTracking()
            velocity.addPosition(down.uptimeMillis, down.position)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (!dragged) {
                    if (change.isConsumed) return@awaitEachGesture
                    total += change.positionChange()
                    if (total.getDistance() < viewConfiguration.touchSlop) continue
                    if (total.x <= 0f || kotlin.math.abs(total.x) < kotlin.math.abs(total.y) * 1.5f) return@awaitEachGesture
                    dragged = true
                }
                change.consume()
                velocity.addPosition(change.uptimeMillis, change.position)
                val dx = (change.position.x - down.position.x).coerceAtLeast(0f)
                EtgBack.manual(dx / size.width, change.position.y, stack.value)
            }
            if (!dragged) return@awaitEachGesture
            val speed = velocity.calculateVelocity().x
            if (EtgBack.progress > 0.35f || speed > 1200f) {
                EtgBack.commit(scope)
                onBack()
            } else EtgBack.released(scope, speed / size.width)
        }
    },
)

object EtgBack {
    const val Idle = 0
    const val Drag = 1
    const val Commit = 2
    const val Cancel = 3

    private val gesture = android.view.animation.PathInterpolator(0.1f, 0.1f, 0f, 1f)
    private val vertical = android.view.animation.DecelerateInterpolator()
    private val emphasized = android.view.animation.PathInterpolator(
        android.graphics.Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        },
    )
    private val easeOutQuint = android.view.animation.PathInterpolator(0.23f, 1f, 0.32f, 1f)

    var phase by androidx.compose.runtime.mutableIntStateOf(Idle)
    var progress by androidx.compose.runtime.mutableFloatStateOf(0f)
    var commitRaw by androidx.compose.runtime.mutableFloatStateOf(0f)
    var scrim by androidx.compose.runtime.mutableFloatStateOf(1f)
    var touchY by androidx.compose.runtime.mutableFloatStateOf(0f)
    var startY = 0f
    var fromLeft = true
    var closingKey: String? = null
    var enteringKey: String? = null
    private var job: kotlinx.coroutines.Job? = null
    private val cancelProgress = androidx.compose.animation.core.Animatable(0f)

    fun matches(key: String, target: String?) = target != null && (key == target || key.startsWith("$target:"))

    fun progress(event: androidx.navigationevent.NavigationEvent, stack: List<NavKey>) {
        if (phase == Commit) return
        if (phase != Drag) {
            if (stack.size < 2) return
            job?.cancel()
            hidden = null
            closingKey = stack[stack.lastIndex].toString()
            enteringKey = stack[stack.lastIndex - 1].toString()
            fromLeft = event.swipeEdge != androidx.navigationevent.NavigationEvent.EDGE_RIGHT
            startY = event.touchY
            scrim = 1f
            commitRaw = 0f
            phase = Drag
        }
        touchY = event.touchY
        progress = gesture.getInterpolation(event.progress.coerceIn(0f, 1f))
    }

    fun manual(fraction: Float, y: Float, stack: List<NavKey>) {
        if (phase == Commit) return
        if (phase != Drag) {
            if (stack.size < 2) return
            job?.cancel()
            hidden = null
            closingKey = stack[stack.lastIndex].toString()
            enteringKey = stack[stack.lastIndex - 1].toString()
            fromLeft = true
            startY = y
            scrim = 1f
            commitRaw = 0f
            phase = Drag
        }
        touchY = y
        progress = gesture.getInterpolation(fraction.coerceIn(0f, 1f))
    }

    fun released(
        scope: kotlinx.coroutines.CoroutineScope,
        initialVelocity: Float = 0f,
    ) {
        if (phase != Drag) return
        job = scope.launch {
            androidx.compose.runtime.withFrameNanos { }
            if (phase != Drag) return@launch
            phase = Cancel
            val from = progress
            cancelProgress.snapTo(from)
            cancelProgress.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.76f,
                    stiffness = 420f,
                ),
                initialVelocity = initialVelocity.coerceIn(-5f, 5f),
            ) {
                progress = value.coerceIn(0f, 1f)
                scrim = if (from > 0f) (value / from).coerceIn(0f, 1f) else 0f
            }
            reset()
        }
    }

    var hidden by androidx.compose.runtime.mutableStateOf<String?>(null)

    fun commit(scope: kotlinx.coroutines.CoroutineScope) {
        phase = Commit
        job?.cancel()
        job = scope.launch {
            val start = androidx.compose.runtime.withFrameNanos { it }
            while (true) {
                val now = androidx.compose.runtime.withFrameNanos { it }
                val f = (((now - start) / 1_000_000f) / CommitMs).coerceIn(0f, 1f)
                commitRaw = f
                scrim = 1f - f
                if (f >= 1f) break
            }
            val closed = closingKey
            hidden = closed
            reset()
            kotlinx.coroutines.delay(HiddenHoldMs)
            if (hidden == closed) hidden = null
        }
    }

    private fun reset() {
        phase = Idle
        progress = 0f
        commitRaw = 0f
        scrim = 1f
        closingKey = null
        enteringKey = null
    }

    fun commitCurve(raw: Float) = emphasized.getInterpolation(raw)
    fun verticalCurve(x: Float) = vertical.getInterpolation(x)

    private const val CommitMs = 375
    private const val HiddenHoldMs = 600L
}

private class EtgBackDecorator : androidx.navigation3.runtime.NavEntryDecorator<NavKey>(
    decorate = { entry ->
        val key = entry.contentKey.toString()
        val dark = com.pixelgram.android.ui.theme.LocalDarkTheme.current
        androidx.compose.runtime.DisposableEffect(key) {
            onDispose { if (EtgBack.matches(key, EtgBack.hidden)) EtgBack.hidden = null }
        }
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier
                .graphicsLayer {
                    if (EtgBack.matches(key, EtgBack.hidden)) {
                        alpha = 0f
                        return@graphicsLayer
                    }
                    val phase = EtgBack.phase
                    if (phase == EtgBack.Idle) return@graphicsLayer
                    val closing = EtgBack.matches(key, EtgBack.closingKey)
                    val entering = EtgBack.matches(key, EtgBack.enteringKey)
                    if (!closing && !entering) return@graphicsLayer
                    val w = size.width
                    val h = size.height
                    val p = EtgBack.progress
                    val margin = 8.dp.toPx()
                    val shift = 96.dp.toPx()
                    val dy = EtgBack.touchY - EtgBack.startY
                    fun yOffset(height: Float): Float {
                        val half = h / 2f
                        return (if (dy < 0) -1f else 1f) * EtgBack.verticalCurve(minOf(half, kotlin.math.abs(dy)) / half) * maxOf(0f, (h - height) / 2f - margin)
                    }
                    val direction = if (EtgBack.fromLeft) 1f else -1f
                    val minScale = com.pixelgram.android.ui.theme.MotionTokens.PredictiveBackMinScale
                    val closeScale = 1f + (minScale - 1f) * p
                    val closeOffsetX = direction * w * 0.14f * p
                    val closeTop = (h - h * closeScale) / 2f + yOffset(h * closeScale)
                    val corner = com.pixelgram.android.ui.theme.MotionTokens.PredictiveBackCornerRadius.toPx() * p
                    val enterBase = 0.95f
                    val enterOffset = direction * maxOf(w * enterBase * 0.15f, shift)
                    val enterScale = enterBase * (1f + (minScale - 1f) * p)
                    val enterCx = w / 2f - enterOffset
                    val enterCy = h / 2f + yOffset(h * closeScale)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                    if (phase == EtgBack.Commit) {
                        val raw = EtgBack.commitRaw
                        val c = EtgBack.commitCurve(raw)
                        if (closing) {
                            val scale = closeScale + (1f - closeScale) * c
                            scaleX = scale
                            scaleY = scale
                            translationX = closeOffsetX + direction * shift * c
                            translationY = closeTop * (1f - c)
                            alpha = maxOf(1f - 5f * raw, 0f)
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(corner * (1f - c))
                            clip = true
                        } else {
                            val scale = enterScale + (1f - enterScale) * c
                            scaleX = scale
                            scaleY = scale
                            translationX = (enterCx - w / 2f) * (1f - c)
                            translationY = (enterCy - h * enterScale / 2f) * (1f - c)
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(corner * (1f - c))
                            clip = c < 1f
                        }
                    } else if (closing) {
                        scaleX = closeScale
                        scaleY = closeScale
                        translationX = closeOffsetX
                        translationY = closeTop
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
                        clip = true
                    } else {
                        scaleX = enterScale
                        scaleY = enterScale
                        translationX = enterCx - w / 2f
                        translationY = enterCy - h * enterScale / 2f
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
                        clip = true
                    }
                }
                .drawWithContent {
                    drawContent()
                    if (EtgBack.phase != EtgBack.Idle && EtgBack.matches(key, EtgBack.enteringKey)) {
                        val a = (if (dark) 0.8f else 0.2f) * EtgBack.scrim
                        if (a > 0f) drawRect(androidx.compose.ui.graphics.Color.Black.copy(alpha = a))
                    }
                },
        ) {
            if (!EtgBack.matches(key, EtgBack.hidden)) {
                if (EtgBack.matches(key, EtgBack.closingKey)) {
                    PredictiveBackFadeOverlay(
                        progress = { EtgBack.progress },
                        swipeFromLeft = { EtgBack.fromLeft },
                        scrim = { EtgBack.scrim },
                    ) {
                        entry.Content()
                    }
                } else {
                    entry.Content()
                }
            }
        }
    },
)

object NavMotion {
    private val spatial = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.76f, stiffness = 420f)
    private val effects = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.82f, stiffness = 900f)
    private const val Shift = 0.12f

    fun forward(): androidx.compose.animation.ContentTransform =
        (androidx.compose.animation.slideInHorizontally(spatial) { (it * Shift).toInt() } + fadeIn(effects)) togetherWith
            (androidx.compose.animation.slideOutHorizontally(spatial) { -(it * Shift).toInt() } + fadeOut(effects))

    fun backward(): androidx.compose.animation.ContentTransform =
        (androidx.compose.animation.slideInHorizontally(spatial) { -(it * Shift).toInt() } + fadeIn(effects)) togetherWith
            (androidx.compose.animation.slideOutHorizontally(spatial) { (it * Shift).toInt() } + fadeOut(effects))
}
