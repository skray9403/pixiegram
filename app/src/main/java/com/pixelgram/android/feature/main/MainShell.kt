package com.pixelgram.android.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pixelgram.android.AppGraph
import com.pixelgram.android.R
import com.pixelgram.android.feature.chats.ChatsTab
import com.pixelgram.android.feature.contacts.ContactsTab
import com.pixelgram.android.feature.settings.SettingsTab
import com.pixelgram.android.nav.BackHandler
import com.pixelgram.android.nav.Navigator
import com.pixelgram.android.settings.NavBarStyle
import com.pixelgram.android.td.LIST_ARCHIVE
import com.pixelgram.android.td.LIST_MAIN
import com.pixelgram.android.ui.components.FloatingMorphingNavigationDock
import com.pixelgram.android.ui.components.LocalBottomBarPadding
import com.pixelgram.android.ui.components.MorphNavDestination
import com.pixelgram.android.ui.theme.LocalMotionSpec
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class MainTab { Chats, Contacts, Settings }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainShell(graph: AppGraph, navigator: Navigator) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Chats) }
    var list by rememberSaveable { mutableStateOf(LIST_MAIN) }
    val motion = LocalMotionSpec.current
    BackHandler(enabled = tab != MainTab.Chats || list == LIST_ARCHIVE) {
        if (list == LIST_ARCHIVE) list = LIST_MAIN else tab = MainTab.Chats
    }
    val appearance by graph.appearance.state.collectAsState()
    val unread by remember { graph.store.snapshot.map { it.unread[LIST_MAIN]?.chats ?: 0 }.distinctUntilChanged() }.collectAsState(0)
    val tabs = listOf(
        MainTabItem(MainTab.Chats, Icons.Rounded.Chat, Icons.Outlined.Chat, stringResource(R.string.tab_chats), unread),
        MainTabItem(MainTab.Contacts, Icons.Rounded.People, Icons.Outlined.People, stringResource(R.string.tab_contacts)),
        MainTabItem(MainTab.Settings, Icons.Rounded.Settings, Icons.Outlined.Settings, stringResource(R.string.tab_settings)),
    )
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    val floating = true
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CompositionLocalProvider(LocalBottomBarPadding provides if (floating) barHeight else 0.dp) {
            val order = listOf(MainTab.Chats, MainTab.Contacts, MainTab.Settings)
            val pager = androidx.compose.foundation.pager.rememberPagerState(order.indexOf(tab).coerceAtLeast(0)) { order.size }
            var jumping by remember { mutableStateOf(false) }
            LaunchedEffect(pager.settledPage, order) {
                if (!jumping) order.getOrNull(pager.settledPage)?.let { if (it != tab) tab = it }
            }
            LaunchedEffect(tab, order) {
                val index = order.indexOf(tab)
                if (index >= 0 && index != pager.targetPage) {
                    jumping = true
                    try { pager.animateScrollToPage(index, animationSpec = motion.spatial) }
                    finally { jumping = false }
                }
            }
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                key = { order[it].name },
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize().padding(bottom = if (floating) 0.dp else barHeight),
            ) { page ->
                when (order[page]) {
                    MainTab.Chats -> ChatsTab(graph, navigator, list, onOpenDrawer = null, onCloseArchive = { list = LIST_MAIN }, onOpenArchive = { list = LIST_ARCHIVE })
                    MainTab.Contacts -> ContactsTab(graph, navigator, onOpenDrawer = null)
                    MainTab.Settings -> SettingsTab(graph, navigator, onOpenDrawer = null)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { with(density) { barHeight = it.height.toDp() } }) {
            MainNavBar(NavBarStyle.Expressive, tabs, tab, { tab = it }, appearance.capsuleInset, appearance.capsuleSpacing, appearance.capsuleHeight)
        }
    }
}

internal data class MainTabItem(val tab: MainTab, val selectedIcon: ImageVector, val icon: ImageVector, val label: String, val badge: Int = 0)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MainNavBar(style: NavBarStyle, tabs: List<MainTabItem>, selected: MainTab, onSelect: (MainTab) -> Unit, inset: Int = 12, spacing: Int = 4, height: Int = 64) {
    val icon: @Composable (MainTabItem) -> Unit = { item ->
        BadgedBox(badge = {
            if (item.badge > 0) Badge {
                com.pixelgram.android.ui.components.AnimatedCounter(
                    item.badge,
                    androidx.compose.material3.LocalTextStyle.current,
                    format = { n -> com.pixelgram.android.ui.theme.countLabel(n, com.pixelgram.android.ui.theme.LocalAppearance.current.exactNumbers) },
                )
            }
        }) {
            com.pixelgram.android.ui.components.Icon(if (item.tab == selected) item.selectedIcon else item.icon, contentDescription = null)
        }
    }
    when (style) {
        NavBarStyle.Expressive -> FloatingMorphingNavigationDock(
            destinations = tabs.map { item ->
                MorphNavDestination(key = item.tab, label = item.label, icon = item.icon, selectedIcon = item.selectedIcon, badge = item.badge)
            },
            selected = selected,
            onSelect = onSelect,
            inset = inset.dp,
            spacing = spacing.dp,
            height = height.dp,
        )
        NavBarStyle.Classic -> NavigationBar {
            tabs.forEach { item ->
                NavigationBarItem(
                    selected = item.tab == selected,
                    onClick = { onSelect(item.tab) },
                    icon = { icon(item) },
                    label = { Text(item.label) },
                )
            }
        }
    }
}
