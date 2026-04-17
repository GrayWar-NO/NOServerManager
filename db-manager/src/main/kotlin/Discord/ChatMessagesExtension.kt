package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.proto.ChatLog
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kordex.core.extensions.Extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class ChatMessagesExtension(val config: ServerConfig, val db: DB): Extension() {
    override val name: String = "batch-chat-messages"

    private lateinit var publicMessageQueue: MessageQueue
    private lateinit var  privateMessageQueue: MessageQueue

    suspend fun enqueueMessage(message: ChatLog){
        val userName = db.getLastPlayerName(message.senderSteamID.toULong())
        privateMessageQueue.add("`${userName} sent message in ${message.messageChannel} chat: ${message.message}`")
        if (message.messageChannel == "all"){
        publicMessageQueue.add("```${userName} sent message: ${message.message}```")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startPeriodicSender(scope: CoroutineScope = GlobalScope) {
        scope.launch {
            while (isActive) {
                delay(1.minutes)
                flushQueues()
            }
        }
    }

    private suspend fun flushQueues() {
        publicMessageQueue.flush()
        privateMessageQueue.flush()
    }


    override suspend fun setup() {
        val publicChannel = kord.getChannel(Snowflake(config.publicChat)) as MessageChannelBehavior
        val privateChannel = kord.getChannel(Snowflake(config.privateChat)) as MessageChannelBehavior
        publicMessageQueue = MessageQueue(publicChannel)
        privateMessageQueue = MessageQueue(privateChannel)
    }
}

class ChatMessagesWebhookExtension(val config: ServerWebhookConfig, val db: DB): Extension() {
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
