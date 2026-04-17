package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.proto.ChatLog
import dev.kordex.core.extensions.Extension

class ChatMessagesExtension(val config: ServerConfig, val db: DB): Extension() {
    override val name: String = "batch-chat-messages"

    private lateinit var publicMessageWebhook: WebhookSender
    private lateinit var  privateMessageWebhook: WebhookSender

    suspend fun enqueueMessage(message: ChatLog){
        val userName = db.getLastPlayerName(message.senderSteamID.toULong())
        privateMessageWebhook.send(WebhookPayload("$userName in ${message.messageChannel} chat", message.message))
        if (message.messageChannel == "all"){
            publicMessageWebhook.send(WebhookPayload(userName, "```${message.message}```"))
        }
    }

    override suspend fun setup() {
        publicMessageWebhook = WebhookSender(config.publicChat, "Message forwarder")
        privateMessageWebhook = WebhookSender(config.privateChat, "Message forwarder")
    }
}
