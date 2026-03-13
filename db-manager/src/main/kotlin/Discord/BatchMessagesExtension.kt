package com.graywar.noServerManager.dbManager.Discord

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

class BatchMessagesExtension(val config: ServerConfig): Extension() {
    override val name: String = "batch-messages"

    private val publicMessageQueue = mutableListOf<String>()
    private val publicQueueMutex = Mutex()

    private val privateMessageQueue = mutableListOf<String>()
    private val privateQueueMutex = Mutex()

    suspend fun enqueueMessage(message: ChatLog){
        privateQueueMutex.withLock {
            privateMessageQueue.add("`${message.senderSteamID} sent message in ${message.messageChannel} chat: ${message.message}`")
        }
        if (message.messageChannel == "all"){
            publicQueueMutex.withLock {
                publicMessageQueue.add("`${message.senderSteamID} sent message: ${message.message}`")
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun startPeriodicSender(scope: CoroutineScope = GlobalScope) {
        scope.launch {
            while (isActive) {
                delay(60.seconds)
                flushQueues()
            }
        }
    }

    private suspend fun flushQueues() {
        val publicChannel = kord.getChannel(Snowflake(config.publicChat)) as MessageChannelBehavior
        val privateChannel = kord.getChannel(Snowflake(config.privateChat)) as MessageChannelBehavior
        sendMessageFromQueue(publicMessageQueue, publicQueueMutex, publicChannel)
        sendMessageFromQueue(privateMessageQueue, privateQueueMutex, privateChannel)
    }

    private suspend fun sendMessageFromQueue(queue: MutableList<String>, mutex: Mutex, channel: MessageChannelBehavior) {
        var consolidated: String? = null

        mutex.withLock {
            while (queue.isNotEmpty()) {
                if (consolidated == null) {
                    consolidated = queue.removeFirst() + "\n"
                } else consolidated += queue.removeFirst() + "\n"
            }
        }
        if (consolidated != null)  {
            channel.createMessage(consolidated)
        }
    }

    override suspend fun setup() {
    }
}
