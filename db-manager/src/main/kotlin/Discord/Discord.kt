package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.dbManager.DataBaseConfig
import com.graywar.noServerManager.dbManager.EdgeAgentServiceImpl
import com.graywar.noServerManager.proto.ChatLog
import dev.kord.common.entity.Snowflake
import dev.kordex.core.builders.ExtensibleBotBuilder
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.ktor.client.plugins.HttpRequestRetry

data class ServerConfig(val publicChat: ULong, val privateChat: ULong)
data class ServerWebhookConfig(val publicChat: String, val privateChat: String)
data class DiscordConfig(
    val enable: Boolean,
    val token: String,
    val guildID: ULong,
    val serverWebhooks: List<ServerWebhookConfig>,
    val teamKillWebhook: String,
    val adminRoles: List<ULong>,
    val linkedRole: ULong
)

@OptIn(DelicateCoroutinesApi::class)
class Discord(
    val config: DiscordConfig,
    databaseConfig: DataBaseConfig,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    private val scope: CoroutineScope = GlobalScope
) {
    private var botJob: Job? = null
    private lateinit var serverMessageExtensions: List<ChatMessagesWebhookExtension>
    lateinit var teamKillExt: TeamKillWebhookExtension
    lateinit var linkExt: LinkMeExtension
    private val db = DB(databaseConfig)

    suspend fun start() {
        serverMessageExtensions = config.serverWebhooks.map { config ->
            ChatMessagesWebhookExtension(config, db)
        }
        teamKillExt = TeamKillWebhookExtension(config.teamKillWebhook)
        linkExt = LinkMeExtension(
            db,
            linkedRole = Snowflake(config.linkedRole),
            linkedGuild =  Snowflake(config.guildID)
        )

        val bot = ExtensibleBotBuilder().apply {
            kord {
                httpClient = HttpClient {
                    install(HttpRequestRetry) {
                        maxRetries = 5
                        retryOnServerErrors(maxRetries = 5)
                        exponentialDelay()
                        retryIf { _, response ->
                            response.status.value == 503
                        }
                    }
                }
            }
            extensions {
                serverMessageExtensions.forEach { ext -> add {ext} }
                add { teamKillExt }
                add { CentralServerExtension(db, cbEdgeAgent, config.adminRoles) }
                add { linkExt }
                add { StatsExtension(db) }
            }
        }.build(config.token)

        botJob = scope.launch {
            bot.start()
        }
        linkExt.initLinkedRoles()
    }

    fun stop() {
        botJob?.cancel()
    }

    suspend fun queueMessage(message: ChatLog, server: Int){
        serverMessageExtensions[server-1].enqueueMessage(message)
    }

}
