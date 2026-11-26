package com.pixelgram.android.feature.chat

import com.pixelgram.android.td.TdClient
import org.drinkless.tdlib.TdApi

object SecretChatHelper {
    suspend fun startSecretChat(td: TdClient, userId: Long): Result<Long> = runCatching {
        val chat = td.send(TdApi.CreateNewSecretChat(userId))
        chat.id
    }

    fun isSecretChat(chat: TdApi.Chat?): Boolean = chat?.type is TdApi.ChatTypeSecret
}
