package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.dbManager.EdgeAgentServiceImpl
import com.graywar.noServerManager.proto.BanRequest
import com.graywar.noServerManager.proto.ChatBack
import com.graywar.noServerManager.proto.ChatLog
import com.graywar.noServerManager.proto.JoinLeaveLog
import com.graywar.noServerManager.proto.KillLog
import com.graywar.noServerManager.proto.LinkUser
import com.graywar.noServerManager.proto.missionStatus
import com.graywar.noServerManager.proto.serverReport
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
data class LogFormat(val format: String, val sanitizeForCode: Boolean, val sanitizeForDiscord: Boolean)
data class OnOffFormat(
    val onFormat: String,
    val offFormat: String,
    val sanitizeForCode: Boolean,
    val sanitizeForDiscord: Boolean
)

data class OnOffEventFormat(val publicFormat: OnOffFormat, val privateFormat: OnOffFormat)
data class LogEventFormat(val publicFormat: LogFormat, val privateFormat: LogFormat)
data class ChatMessageFormatConfig(
    val chatMessageFormat: LogEventFormat,
    val missionChangeFormat: OnOffEventFormat,
    val joinLeaveFormat: OnOffEventFormat,
)

data class StatusConfig(
    val channel: ULong,
    val statusTimeoutSeconds: Int = 300,
    val updateRateSeconds: Int = 60,
    val excludedServers: List<Int>
)


data class DiscordConfig(
    val enable: Boolean,
    val token: String,
    val guildID: ULong,
    val status: StatusConfig,
    val serverWebhooks: List<ServerConfig>,
    val chatMessages: ChatMessageFormatConfig,
    val teamKillWebhook: String,
    val teamKillFormat: LogFormat,
    val reportFormat: LogFormat,
    val banWebhook: String,
    val moderatorRole: ULong,
    val adminRole: ULong,
    val linkedRole: ULong
)

@OptIn(PrivilegedIntent::class)
class Discord(
    val config: DiscordConfig,
    val db: DB,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    private var botJob: Job? = null
    private lateinit var serverMessageExtensions: List<ChatMessagesExtension>
    internal lateinit var teamKillExt: TeamKillExtension
    internal lateinit var linkExt: LinkMeExtension
    internal lateinit var modListExt: ModListExtension
    internal lateinit var banWebhookExt: BanLogExt

    private val adminRole = Snowflake(config.adminRole)
    private val moderatorRole = Snowflake(config.moderatorRole)


    suspend fun start() {
        serverMessageExtensions = config.serverWebhooks.mapIndexed { index, serverConfig ->
            val ext = ChatMessagesExtension(index, serverConfig, config.chatMessages, db) { username, content ->
                cbEdgeAgent.discordMessageFlows[index + 1]?.trySend(
                    ChatBack.newBuilder().setSenderName(username).setMessage(content).build()
                )
            }
            ext
        }

        val guildID = Snowflake(config.guildID)

        val statusExt = Status(config.status, guildID, cbEdgeAgent, db.getAllServers())
        teamKillExt =
            TeamKillExtension(config.teamKillWebhook, config.teamKillFormat, config.reportFormat, moderatorRole)
        linkExt = LinkMeExtension(
            db,
            linkedRole = Snowflake(config.linkedRole),
            linkedGuild = guildID
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
                serverMessageExtensions.forEach { ext -> add { ext } }
                add { teamKillExt }
                add { CentralServerExtension(db, cbEdgeAgent, adminRole, moderatorRole) }
                add { ModQueriesExtension(db, adminRole, moderatorRole) }
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
                @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
                println("[Discord] Bot stopped. Restarting in 5s...")
                delay(5.seconds)
            }
        }

        scope.launch {
            linkExt.initLinkedRoles()
            println("Finished initializing discord roles")
        }
    }

    fun stop() {
        botJob?.cancel()
    }

    suspend fun sendCallbackEvent(message: CallbackEvent) {
        when (message) {
            is CallbackEvent.BanEvent -> banWebhookExt.log(message.event, message.source)
            is CallbackEvent.LinkEvent -> linkExt.newLink(message.event)
            is CallbackEvent.ReportEvent -> teamKillExt.sendReport(message.event, message.source)
            is CallbackEvent.ServerEvent -> sendLoggedEvent(message.event, message.serverID)
            is CallbackEvent.TeamKillEvent -> teamKillExt.sendTeamKill(
                message.event.first,
                message.event.second,
                message.event.third,
                message.source
            )
        }
    }

    suspend fun sendLoggedEvent(message: LoggedServerEvent, server: Int) {
        serverMessageExtensions[server - 1].sendEvent(message)
    }

}

sealed interface CallbackEvent {
    data class ServerEvent(val event: LoggedServerEvent, val serverID: Int) : CallbackEvent
    data class TeamKillEvent(val event: Triple<KillLog, String, String>, val source: String) : CallbackEvent
    data class LinkEvent(val event: LinkUser) : CallbackEvent
    data class ReportEvent(val event: serverReport, val source: String) : CallbackEvent
    data class BanEvent(val event: BanRequest, val source: String) : CallbackEvent
}

sealed interface LoggedServerEvent {
    data class ChatEvent(val event: ChatLog) : LoggedServerEvent
    data class MissionEvent(val event: missionStatus) : LoggedServerEvent
    data class PlayerEvent(val event: JoinLeaveLog) : LoggedServerEvent
}


fun escapeDiscordMarkdown(text: String): String =
    text
        .replace(Regex("""([\\*_~`|#\[])"""), """\\$1""")
        .replace(Regex(""":?//"""), "$0\u200B")

fun escapeForCodeBlock(text: String) =
    text.replace("```", "``\u200B`")
