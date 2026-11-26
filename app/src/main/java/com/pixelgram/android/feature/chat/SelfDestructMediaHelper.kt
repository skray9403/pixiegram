package com.pixelgram.android.feature.chat

import android.content.Context
import com.pixelgram.android.td.TdClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.UUID

enum class ExpiryOption(val label: String, val shortLabel: String, val seconds: Int?) {
    None("Off", "Off", null),
    ViewOnce("View once", "1x", 0),
    Seconds5("5 seconds", "5s", 5),
    Seconds10("10 seconds", "10s", 10),
    Seconds15("15 seconds", "15s", 15),
    Seconds30("30 seconds", "30s", 30),
    Minute1("1 minute", "1m", 60),
    Minutes5("5 minutes", "5m", 300),
}

object SelfDestructMediaHelper {
    fun toTdSelfDestruct(option: ExpiryOption): TdApi.MessageSelfDestructType? = when (option) {
        ExpiryOption.None -> null
        ExpiryOption.ViewOnce -> TdApi.MessageSelfDestructTypeImmediately()
        else -> option.seconds?.takeIf { it > 0 }?.let { TdApi.MessageSelfDestructTypeTimer(it) }
    }

    /**
     * Opens/downloads self-destruct or view-once media via TDLib and copies the cached file
     * into app-private storage (`filesDir/captured-ephemeral`) before/as it expires.
     */
    suspend fun downloadAndSaveLocally(
        context: Context,
        td: TdClient,
        chatId: Long,
        messageId: Long,
        sourcePath: String?,
        extension: String = "jpg",
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching { td.send(TdApi.OpenMessageContent(chatId, messageId)) }
            val resolvedPath = sourcePath?.takeIf { File(it).isFile }
                ?: run {
                    val msg = td.send(TdApi.GetMessage(chatId, messageId))
                    extractLocalFilePath(msg.content)
                }
                ?: error("Media file is not available locally")

            val outDir = File(context.filesDir, "captured-ephemeral").apply { mkdirs() }
            val outFile = File(outDir, "saved_${chatId}_${messageId}_${UUID.randomUUID()}.$extension")
            File(resolvedPath).copyTo(outFile, overwrite = true)
            outFile
        }
    }

    private fun extractLocalFilePath(content: TdApi.MessageContent): String? = when (content) {
        is TdApi.MessagePhoto -> {
            val best = content.photo.sizes.maxByOrNull { it.width * it.height }?.photo
            best?.local?.path?.takeIf { it.isNotBlank() && File(it).isFile }
        }
        is TdApi.MessageVideo -> {
            content.video.video.local.path.takeIf { it.isNotBlank() && File(it).isFile }
        }
        else -> null
    }
}
