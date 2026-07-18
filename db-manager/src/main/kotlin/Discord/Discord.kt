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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.ktor.client.plugins.HttpRequestRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    val banWebhook: String,
    val moderatorRole: ULong,
    val adminRole: ULong,
    val linkedRole: ULong
)

@OptIn(PrivilegedIntent::class)
class Discord(
    val config: DiscordConfig,
    databaseConfig: DataBaseConfig,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var botJob: Job? = null
    private lateinit var serverMessageExtensions: List<ChatMessagesExtension>
    internal lateinit var teamKillExt: TeamKillExtension
    internal lateinit var linkExt: LinkMeExtension
    internal lateinit var modListExt: ModListExtension
    internal lateinit var banWebhookExt: BanLogExt

    private val db = DB(databaseConfig)
    private val adminRole = Snowflake(config.adminRole)
    private val moderatorRole = Snowflake(config.moderatorRole)



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
        teamKillExt = TeamKillExtension(config.teamKillWebhook, moderatorRole)
        linkExt = LinkMeExtension(
            db,
            linkedRole = Snowflake(config.linkedRole),
            linkedGuild =  guildID
        )
        modListExt = ModListExtension(guildID, moderatorRole, adminRole, db)
        banWebhookExt = BanLogExt(config.banWebhook, db)

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
                add { CentralServerExtension(db, cbEdgeAgent, adminRole, moderatorRole) }
                add { ModQueriesExtension(db, adminRole, moderatorRole)}
                add { linkExt }
                add { StatsExtension(db) }
                add { statusExt }
                add { modListExt }
                add { banWebhookExt }
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
