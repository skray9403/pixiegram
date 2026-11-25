package com.pixelgram.android

import android.content.Context
import com.pixelgram.android.settings.AppearanceStore
import com.pixelgram.android.td.TdClient
import com.pixelgram.android.td.TdStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppGraph(context: Context, offline: Boolean = false) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val td = TdClient(context.applicationContext, scope, offline)
    val hiddenChats = com.pixelgram.android.feature.security.TitanHiddenChats(context.applicationContext)
    val store = TdStore(td, scope, context.applicationContext, hiddenChats)
    val audio = com.pixelgram.android.audio.ChatAudioPlayer(context.applicationContext, td)
    val appearance = AppearanceStore(context.applicationContext, scope)
    val auth = com.pixelgram.android.feature.auth.AuthController(td, scope)
    val notifier = com.pixelgram.android.notify.Notifier(context.applicationContext, td, store, scope)
}

val LocalGraph = androidx.compose.runtime.staticCompositionLocalOf<AppGraph> { error("No AppGraph") }
