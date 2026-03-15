package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.dbManager.DataBaseConfig
import com.graywar.noServerManager.dbManager.EdgeAgentServiceImpl
import com.graywar.noServerManager.proto.ChatLog
import dev.kordex.core.builders.ExtensibleBotBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class ServerConfig(val publicChat: ULong, val privateChat: ULong)
data class DiscordConfig(
    val enable: Boolean,
    val token: String,
    val serverChannels: List<ServerConfig>,
    val teamKillChannel: ULong,
    val adminRoles: List<ULong>
)

@OptIn(DelicateCoroutinesApi::class)
class Discord(
    val config: DiscordConfig,
    databaseConfig: DataBaseConfig,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    private val scope: CoroutineScope = GlobalScope
) {
    private var botJob: Job? = null
    private lateinit var serverMessageExtensions: List<BatchMessagesExtension>
    lateinit var teamKillExt: TeamKillExtension
    lateinit var linkExt: LinkMeExtension
    private val db = DB(databaseConfig)

    suspend fun start() {
        serverMessageExtensions = config.serverChannels.map { config ->
            BatchMessagesExtension(config)
        }
        teamKillExt = TeamKillExtension(config.teamKillChannel)
        linkExt = LinkMeExtension(db)

        val bot = ExtensibleBotBuilder().apply {
            extensions {
                serverMessageExtensions.forEach { ext -> add {ext} }
                add { teamKillExt }
                add { CentralServerExtension(db, cbEdgeAgent, config.adminRoles) }
                add { linkExt }
                add { StatsExtension(db) }
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
