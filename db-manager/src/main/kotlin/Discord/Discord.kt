package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.proto.ChatLog
import dev.kord.common.entity.Snowflake
import dev.kordex.core.builders.ExtensibleBotBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ServerConfig(val publicChat: ULong, val privateChat: ULong)
data class DiscordConfig(val token: String, val serverChannels: List<ServerConfig>, val teamKillChannel: ULong)

@OptIn(DelicateCoroutinesApi::class)
class Discord(
    val config: DiscordConfig,
    val databaseConfig: DataBaseConfig,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    private val scope: CoroutineScope = GlobalScope
) {
    private var botJob: Job? = null
    private lateinit var serverMessageExtensions: List<BatchMessagesExtension>
    lateinit var teamKillExt: TeamKillExtension

    suspend fun start() {
        serverMessageExtensions = config.serverChannels.map { config ->
            BatchMessagesExtension(config)
        }
        teamKillExt = TeamKillExtension(config.teamKillChannel)

        val bot = ExtensibleBotBuilder().apply {
            applicationCommands { // TODO Remove; for testing only.
                defaultGuild = Snowflake(989821370747719731)
            }

            extensions {
                serverMessageExtensions.forEach { ext -> add {ext} }
                add { teamKillExt }
                add { CentralServerExtension(databaseConfig, cbEdgeAgent) }
            }
        }.build(config.token)

        serverMessageExtensions.forEach { ext -> ext.startPeriodicSender() }

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
