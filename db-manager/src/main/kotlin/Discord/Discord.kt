package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.dbManager.DataBaseConfig
import com.graywar.noServerManager.dbManager.EdgeAgentServiceImpl
import com.graywar.noServerManager.proto.ChatBack
import com.graywar.noServerManager.proto.ChatLog
import dev.kord.common.entity.Snowflake
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import dev.kordex.core.builders.ExtensibleBotBuilder
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.ktor.client.plugins.HttpRequestRetry
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

data class ServerConfig(val publicChat: String, val privateChat: String)
data class DiscordConfig(
    val enable: Boolean,
    val token: String,
    val guildID: ULong,
    val statusChannel: ULong,
    val serverWebhooks: List<ServerConfig>,
    val teamKillWebhook: String,
    val adminRoles: List<ULong>,
    val linkedRole: ULong
)

@OptIn(DelicateCoroutinesApi::class, PrivilegedIntent::class)
class Discord(
    val config: DiscordConfig,
    databaseConfig: DataBaseConfig,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    private val scope: CoroutineScope = GlobalScope
) {
    private var botJob: Job? = null
    private lateinit var serverMessageExtensions: List<ChatMessagesExtension>
    internal lateinit var teamKillExt: TeamKillExtension
    internal lateinit var linkExt: LinkMeExtension
    private val db = DB(databaseConfig)
    private val adminRoles = config.adminRoles.map { id -> Snowflake(id) }


    suspend fun start() {
        serverMessageExtensions = config.serverWebhooks.mapIndexed { index, config ->
            val ext = ChatMessagesExtension(config, db) { username, content ->
                cbEdgeAgent.discordMessageFlows[index]?.trySend(
                    ChatBack.newBuilder().setSenderName(username).setMessage(content).build()
                )
            }
            ext
        }

        val guildID = Snowflake(config.guildID)

        val statusExt = Status(db, Snowflake(config.statusChannel), guildID, cbEdgeAgent)
        teamKillExt = TeamKillExtension(config.teamKillWebhook)
        linkExt = LinkMeExtension(
            db,
            linkedRole = Snowflake(config.linkedRole),
            linkedGuild =  guildID
        )

        val bot = ExtensibleBotBuilder().apply {
            kord {
                intents {
                    +Intent.GuildMessages
                    +Intent.MessageContent
                }
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
                add { CentralServerExtension(db, cbEdgeAgent, adminRoles) }
                add { ModQueriesExtension(db, adminRoles)}
                add { linkExt }
                add { StatsExtension(db) }
                add { statusExt }
            }
        }.build(config.token)

        botJob = scope.launch {
            while (isActive) {
                try {
                    bot.start()
                } catch (e: Exception) {
                    println("[Discord] Bot crashed: ${e.message}")
                }
                println("[Discord] Bot stopped. Restarting in 5s...")
                delay(5.seconds)
            }
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
