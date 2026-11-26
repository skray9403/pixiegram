package com.pixelgram.android.feature.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.pixelgram.android.nav.SearchKey
import com.pixelgram.android.td.LIST_ARCHIVE
import com.pixelgram.android.td.LIST_MAIN
import com.pixelgram.android.ui.components.FloatingMorphingNavigationDock
import com.pixelgram.android.ui.components.LocalBottomBarPadding
import com.pixelgram.android.ui.components.MorphNavDestination
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class MainTab { Chats, Contacts, Settings }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MainShell(graph: AppGraph, navigator: Navigator) {
    val tabsOrder = remember { listOf(MainTab.Chats, MainTab.Contacts, MainTab.Settings) }
    var tab by rememberSaveable { mutableStateOf(MainTab.Chats) }
    var list by rememberSaveable { mutableStateOf(LIST_MAIN) }

    BackHandler(enabled = tab != MainTab.Chats || list == LIST_ARCHIVE) {
        if (list == LIST_ARCHIVE) list = LIST_MAIN else tab = MainTab.Chats
    }

    val unread by remember {
        graph.store.snapshot.map { it.unread[LIST_MAIN]?.chats ?: 0 }.distinctUntilChanged()
    }.collectAsState(0)

    val destinations = listOf(
        MorphNavDestination(
            key = MainTab.Chats,
            label = stringResource(R.string.tab_chats),
            icon = Icons.Outlined.Chat,
            selectedIcon = Icons.Rounded.Chat,
            badge = unread,
        ),
        MorphNavDestination(
            key = MainTab.Contacts,
            label = stringResource(R.string.tab_contacts),
            icon = Icons.Outlined.People,
            selectedIcon = Icons.Rounded.People,
        ),
        MorphNavDestination(
            key = MainTab.Settings,
            label = stringResource(R.string.tab_settings),
            icon = Icons.Outlined.Settings,
            selectedIcon = Icons.Rounded.Settings,
        ),
    )

    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0.dp) }
    val pager = rememberPagerState(tabsOrder.indexOf(tab).coerceAtLeast(0)) { tabsOrder.size }
    var jumping by remember { mutableStateOf(false) }

    LaunchedEffect(pager.settledPage) {
        if (!jumping) tabsOrder.getOrNull(pager.settledPage)?.let { if (it != tab) tab = it }
    }
    LaunchedEffect(tab) {
        val index = tabsOrder.indexOf(tab)
        if (index >= 0 && index != pager.targetPage) {
            jumping = true
            try {
                pager.scrollToPage(index)
            } finally {
                jumping = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CompositionLocalProvider(LocalBottomBarPadding provides barHeight) {
            HorizontalPager(
                state = pager,
                key = { tabsOrder[it].name },
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (tabsOrder[page]) {
                    MainTab.Chats -> ChatsTab(
                        graph = graph,
                        navigator = navigator,
                        baseList = list,
                        onOpenDrawer = null,
                        onCloseArchive = { list = LIST_MAIN },
                        onOpenArchive = { list = LIST_ARCHIVE },
                    )
                    MainTab.Contacts -> ContactsTab(
                        graph = graph,
                        navigator = navigator,
                        onOpenDrawer = null,
                    )
                    MainTab.Settings -> SettingsTab(
                        graph = graph,
                        navigator = navigator,
                        onOpenDrawer = null,
                    )
                }
            }
        }

        FloatingMorphingNavigationDock(
            destinations = destinations,
            selected = tab,
            onSelect = { tab = it },
            isFabCollapsed = true,
            fabLabel = stringResource(R.string.action_search),
            fabIcon = Icons.Rounded.Search,
            onFabClick = { navigator.open(SearchKey) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { with(density) { barHeight = it.height.toDp() } },
        )
    }
}
