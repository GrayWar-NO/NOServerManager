package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.proto.ChatLog
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.message.MessageCreateEvent
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event

class ChatMessagesExtension(val config: ServerConfig, val db: DB,val cb: suspend (username: String, content: String) -> Unit): Extension() {
    override val name: String = "batch-chat-messages"

    private lateinit var publicMessageWebhook: WebhookSender
    private lateinit var  privateMessageWebhook: WebhookSender

    suspend fun enqueueMessage(message: ChatLog){
        val steamID = message.senderSteamID.toULong()
        val userName = db.getLastPlayerName(steamID)
        privateMessageWebhook.send{
            username = "$userName in ${message.messageChannel} chat"
            content = "$userName: ${message.message}" }
        if (message.messageChannel == "all"){
            publicMessageWebhook.send {
                username = userName
                content = "```$userName($steamID): ${message.message}```"
            }
        }
    }

    override suspend fun setup() {
        publicMessageWebhook = WebhookSender(kord, config.publicChat, "Message forwarder")
        privateMessageWebhook = WebhookSender(kord, config.privateChat, "Message forwarder")

        event<MessageCreateEvent> {
            check {
                if (event.message.channelId == publicMessageWebhook.webhook.channelId && !(event.message.author?.isBot ?: true)) pass() else fail()
            }
            action {
                cb(event.message.author!!.effectiveName, event.message.content)
            }
        }
    }
}
