package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.dbManager.EdgeAgentServiceImpl
import dev.kord.common.entity.Snowflake
import dev.kordex.core.checks.channelFor
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.optionalBoolean
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.commands.converters.impl.string
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.core.extensions.publicSlashCommand
import dev.kordex.i18n.Key

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


class CentralServerExtension(
    val db: DB,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    commandChannelID: ULong) : Extension() {
    val commandChannel = Snowflake(commandChannelID)
    override val name = "ping"

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = Key("ping")
            description = Key("Ping command")

            action {
                respond {
                    content = "Pong!"
                }
            }
        }

        publicSlashCommand {
            name = Key("servers")
            description = Key("Gets all servers")

            check {
                if (channelFor(event)?.id == commandChannel) pass()  else fail(
                    Key("You cannot execute this command in this channel. Go to ${kord.getChannel(commandChannel)?.mention}.")
                )
            }

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

            check {
                if (channelFor(event)?.id == commandChannel) pass()  else fail(
                    Key("You cannot execute this command in this channel. Go to ${kord.getChannel(commandChannel)?.mention}.")
                )
            }

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
