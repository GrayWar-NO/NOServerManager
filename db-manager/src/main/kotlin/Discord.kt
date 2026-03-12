package com.graywar.noServerManager.dbManager

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
import kotlinx.coroutines.launch

data class Channel(val guild: ULong, val channel: ULong)
data class DiscordConfig(val token: String, val defaultChannel: Channel)

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
            name = Key("get servers")
            description = Key("Gets all servers")

            action {
                var contentStr = ""
                for (row in db.getAllServers()){
                    contentStr += "${row.key}: ${row.value}\n"
                }
                respond {
                    content = contentStr
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

@OptIn(DelicateCoroutinesApi::class)
class Discord(
    val config: DiscordConfig,
    val databaseConfig: DataBaseConfig,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    private val scope: CoroutineScope = GlobalScope
) {

    private var botJob: Job? = null

    // start asynchronously
    suspend fun start() {
        val bot = ExtensibleBotBuilder().apply {
            extensions {
                add { CentralServerExtension(databaseConfig, cbEdgeAgent) }
            }
        }.build(config.token)

        // Launch bot in the provided scope
        botJob = scope.launch {
            bot.start() // suspend, but runs in coroutine
        }
    }

    fun stop() {
        botJob?.cancel()
    }
}
//fun main() {
//    val config = ConfigLoaderBuilder.default()
//        .addFileSource("central.conf")
//        .build()
//        .loadConfigOrThrow<HostConfig>()
//
//    val discord = Discord(config.discord, config.db)
//    discord.main()
//}