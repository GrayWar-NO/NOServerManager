package com.graywar.noServerManager.dbManager

import com.graywar.noServerManager.proto.ChatLog
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.builders.ExtensibleBotBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.*
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.i18n.Key
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

data class ServerConfig(val publicChat: ULong, val privateChat: ULong)
data class DiscordConfig(val token: String, val serverChannels: List<ServerConfig>)

class ServerCommandArgs : Arguments() {
    val target by string {
        name = Key("target")
        description = Key("Server name or ID for running the command")
    }
    val command by string {
        name = Key("command")
        description = Key("The command to run")
    }
    val arguments by optionalString {
        name = Key("arguments")
        description = Key("The arguments for this command, separated by whitespace.")
    }

    val result by optionalBoolean {
        name = Key("result")
        description = Key("Do you need the result from this command? (default: true)")
    }

}


class CentralServerExtension(config: DataBaseConfig, val cbEdgeAgent: EdgeAgentServiceImpl) : Extension() {
    override val name = "ping"
    private val db = DB(config)


    override suspend fun setup() {
        db.connect()
        ephemeralSlashCommand {
            name = Key("ping")
            description = Key("Ping command")

            action {
                respond {
                    content = "Pong! ${user.mention}"
                }
            }
        }

        publicSlashCommand {
            name = Key("servers")
            description = Key("Gets all servers")

            action {
                try {
                    var contentStr = ""
                    for (row in db.getAllServers()) {
                        contentStr += "${row.key}: ${row.value}\n"
                    }
                    respond {
                        content = contentStr
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    respond {
                        content = "Failed to get servers"
                    }
                }
            }
        }

        publicSlashCommand(::ServerCommandArgs) {
            name = Key("command")
            description = Key("Send a command to a server")

            action {
                val serverID = arguments.target.toIntOrNull()
                val serverName: String?
                if (serverID != null) {
                    serverName = db.getServerNameFromId(serverID)
                    if (serverName == null) {
                        respond {
                            content = "Server ${arguments.target} not found!"
                        }
                        return@action
                    }
                } else serverName = arguments.target
                val result = arguments.result ?: true
                val deferredResult = cbEdgeAgent.sendCommand(
                    serverName,
                    arguments.command,
                    arguments.arguments?.split(' ') ?: listOf(),
                    arguments.result ?: true)
                if (!result){
                    respond {
                        content = "Command ${arguments.command} executed on server (you asked for no result)"
                    }
                    return@action
                }

                respond {
                    content = deferredResult.await()
                }
            }
        }
    }
}

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

@OptIn(DelicateCoroutinesApi::class)
class Discord(
    val config: DiscordConfig,
    val databaseConfig: DataBaseConfig,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    private val scope: CoroutineScope = GlobalScope
) {

    private var botJob: Job? = null
    private lateinit var serverMessageExtensions: List<BatchMessagesExtension>

    suspend fun start() {
        serverMessageExtensions = config.serverChannels.map { config ->
            BatchMessagesExtension(config)
        }

        val bot = ExtensibleBotBuilder().apply {
            applicationCommands { // TODO Remove; for testing only.
                defaultGuild = Snowflake(989821370747719731)
            }

            extensions {
                serverMessageExtensions.forEach { ext -> add {ext} }
                add { CentralServerExtension(databaseConfig, cbEdgeAgent) }
            }
        }.build(config.token)

        // Launch bot in the provided scope
        botJob = scope.launch {
            bot.start()
        }
    }

    fun stop() {
        botJob?.cancel()
    }

    suspend fun queueMessage(message: ChatLog, server: Int){
        serverMessageExtensions[server-1].enqueueMessage(message)
    }

}
