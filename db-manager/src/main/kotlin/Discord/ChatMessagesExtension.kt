package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.proto.ChatLog
import com.graywar.noServerManager.proto.JoinLeaveLog
import com.graywar.noServerManager.proto.missionStatus
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.message.MessageCreateEvent
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event

class ChatMessagesExtension(
    index: Int,
    val config: ServerConfig,
    val db: DB,
    val cb: suspend (username: String, content: String) -> Unit
) : Extension() {
    override val name: String = "batch-chat-messages-$index"

    private lateinit var publicMessageWebhook: WebhookSender
    private lateinit var privateMessageWebhook: WebhookSender

    suspend fun sendChatMessage(message: ChatLog) {
        val steamID = message.senderSteamID.toULong()
        val userName = db.getLastPlayerName(steamID)
        privateMessageWebhook.send(
            username = "$userName in ${message.messageChannel} chat",
            content = "${userName}($steamID): ${message.message}"
        )
        if (message.messageChannel == "all") {
            publicMessageWebhook.send {
                username = userName
                content = "```${escapeForCodeBlock(message.message)}```"
            }
        }
    }

    suspend fun sendEvent(message: LoggedServerEvent) {
        when (message) {
            is LoggedServerEvent.ChatEvent -> sendChatMessage(message.event)
            is LoggedServerEvent.MissionEvent -> sendMissionChange(message.event)
            is LoggedServerEvent.PlayerEvent -> sendPlayerJoinLeave(message.event)
        }
    }

    suspend fun sendPlayerJoinLeave(message: JoinLeaveLog) {
        val content = if (message.isOn)
            "${message.name} joined the server"
        else
            "${message.name} left the server, with a score of ${message.score}"
        privateMessageWebhook.send("(${message.steamID}) $content")
        publicMessageWebhook.send(content)
    }

    suspend fun sendMissionChange(message: missionStatus) {
        val content = if (message.missionName == null)
            "Mission stopped"
        else
            "Mission ${message.missionName} started!"
        privateMessageWebhook.send(content)
        publicMessageWebhook.send(content)
    }

    override suspend fun setup() {
        publicMessageWebhook = WebhookSender(kord, config.publicChat, "Message forwarder")
        privateMessageWebhook = WebhookSender(kord, config.privateChat, "Message forwarder")

        event<MessageCreateEvent> {
            check {
                if (event.message.channelId == publicMessageWebhook.webhook.channelId &&
                    !(event.message.author?.isBot ?: true)
                ) pass() else fail()
            }
            action {
                cb(event.message.author!!.effectiveName, event.message.content)
            }
        }
    }
}
