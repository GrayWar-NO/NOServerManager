package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.proto.ChatLog
import com.graywar.noServerManager.proto.JoinLeaveLog
import com.graywar.noServerManager.proto.missionStatus
import dev.kord.core.entity.effectiveName
import dev.kord.core.event.message.MessageCreateEvent
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import kotlin.collections.emptyMap
import kotlin.collections.mapOf

class ChatMessagesExtension(
    index: Int,
    val serverConfig: ServerConfig,
    val formatConfig: ChatMessageFormatConfig,
    val db: DB,
    val cb: suspend (username: String, content: String) -> Unit
) : Extension() {
    override val name: String = "batch-chat-messages-$index"

    private lateinit var publicMessageWebhook: WebhookSender
    private lateinit var privateMessageWebhook: WebhookSender

    suspend fun sendEvent(message: LoggedServerEvent) {
        when (message) {
            is LoggedServerEvent.ChatEvent -> sendChatMessage(message.event)
            is LoggedServerEvent.MissionEvent -> sendMissionChange(message.event)
            is LoggedServerEvent.PlayerEvent -> sendPlayerJoinLeave(message.event)
        }
    }

    suspend fun sendChatMessage(message: ChatLog) {
        val config = formatConfig.chatMessageFormat
        val steamID = message.senderSteamID.toULong()
        val userName = db.getLastPlayerName(steamID)

        val private = TemplateString(config.privateFormat)
        val public = TemplateString(config.publicFormat)
        val argMap = mapOf(
            "message" to message.message,
            "channel" to message.messageChannel,
            "userName" to userName,
            "steamID" to steamID.toString(),
        )

        privateMessageWebhook.send(
            username = "$userName in ${message.messageChannel} chat",
            content = private.format(argMap)
        )
        if (message.messageChannel == "all") {
            publicMessageWebhook.send {
                username = userName
                content = public.format(argMap)
            }
        }
    }

    suspend fun sendPlayerJoinLeave(message: JoinLeaveLog) {
        val config = formatConfig.joinLeaveFormat
        val public = TemplateString(
            LogFormat(
                if (message.isOn) config.publicFormat.onFormat else config.publicFormat.offFormat,
                config.publicFormat.sanitizeForCode,
                config.publicFormat.sanitizeForDiscord
            )
        )

        val private = TemplateString(
            LogFormat(
                if (message.isOn) config.privateFormat.onFormat else config.privateFormat.offFormat,
                config.privateFormat.sanitizeForCode,
                config.privateFormat.sanitizeForDiscord
            )
        )

        val argMap = mutableMapOf(
            "name" to message.name,
            "steamID" to message.steamID.toString(),
        )

        if (message.hasScore()) {
            argMap["score"] = message.score.toString()
        }

        privateMessageWebhook.send(private.format(argMap))
        publicMessageWebhook.send(public.format(argMap))
    }

    suspend fun sendMissionChange(message: missionStatus) {
        val config = formatConfig.missionChangeFormat
        val public = TemplateString(
            LogFormat(
                if (message.hasMissionName()) config.publicFormat.onFormat else config.publicFormat.offFormat,
                config.publicFormat.sanitizeForCode,
                config.publicFormat.sanitizeForDiscord
            )
        )

        val private = TemplateString(
            LogFormat(
                if (message.hasMissionName()) config.privateFormat.onFormat else config.privateFormat.offFormat,
                config.privateFormat.sanitizeForCode,
                config.privateFormat.sanitizeForDiscord
            )
        )

        val argMap = if (message.hasMissionName()) mapOf("missionName" to message.missionName) else emptyMap()

        privateMessageWebhook.send(private.format(argMap))
        publicMessageWebhook.send(public.format(argMap))
    }

    override suspend fun setup() {
        publicMessageWebhook = WebhookSender(kord, serverConfig.publicChat, "Message forwarder")
        privateMessageWebhook = WebhookSender(kord, serverConfig.privateChat, "Message forwarder")

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
