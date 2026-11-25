package com.pixelgram.android.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import com.pixelgram.android.td.TdClient
import java.io.File

data class ChatAudioTrack(val fileId: Int, val title: String, val performer: String)
data class ChatAudioState(
    val track: ChatAudioTrack? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
)

/** Minimal playback for audio attachments in conversations. */
class ChatAudioPlayer(context: Context, private val td: TdClient) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateFlow = MutableStateFlow(ChatAudioState())
    val state: StateFlow<ChatAudioState> = stateFlow.asStateFlow()

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { exo ->
                exo.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        stateFlow.value = stateFlow.value.copy(playing = isPlaying)
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        stateFlow.value = stateFlow.value.copy(
                            loading = playbackState == Player.STATE_BUFFERING,
                            duration = exo.duration.takeIf { it > 0 } ?: stateFlow.value.duration,
                            playing = exo.isPlaying,
                        )
                    }
                })
            }
    }

    fun play(audios: List<TdApi.Audio>, start: Int, source: String? = null) {
        val audio = audios.getOrNull(start) ?: return
        scope.launch {
            stateFlow.value = ChatAudioState(
                track = ChatAudioTrack(audio.audio.id, audio.title.ifBlank { audio.fileName }, audio.performer),
                loading = true,
                duration = audio.duration * 1000L,
            )
            val file = runCatching { td.send(TdApi.DownloadFile(audio.audio.id, 32, 0, 0, true)) }.getOrNull()
            val path = file?.local?.path?.takeIf { it.isNotBlank() && file.local.isDownloadingCompleted }
            if (path == null) {
                stateFlow.value = stateFlow.value.copy(loading = false)
                return@launch
            }
            val metadata = MediaMetadata.Builder()
                .setTitle(audio.title.ifBlank { audio.fileName })
                .setArtist(audio.performer)
                .build()
            player.setMediaItem(MediaItem.Builder().setUri(Uri.fromFile(File(path))).setMediaMetadata(metadata).build())
            player.prepare()
            player.play()
        }
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        stateFlow.value = ChatAudioState()
    }
}
