package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.dbManager.EdgeAgentServiceImpl
import dev.kord.common.entity.Snowflake
import dev.kordex.core.checks.guildFor
import dev.kordex.core.checks.types.CheckContext
import dev.kordex.core.checks.userFor
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.boolean
import dev.kordex.core.commands.converters.impl.int
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

class CreateServerCommandArgs : Arguments() {
    val name by string {
        name = Key("name")
        description = Key("Name of the server")
    }
    val maxPlayers by int {
        name = Key("max_players")
        description = Key("Maximum number of players")
    }
}

class CreateMissionCommandArgs : Arguments() {
    val name by string {
        name = Key("name")
        description = Key("Name of the mission")
    }

    val pvp by boolean {
        name = Key("pvp")
        description = Key("Set to true if the mission you're adding is PVP.")
    }

}


internal suspend fun CheckContext<*>.requireAnyRole(vararg roles: Snowflake) {
    val member = userFor(event)?.asMemberOrNull(guildFor(event)?.id ?: return fail(Key("Guild only command")))

    val memberRoleIds = member?.roleIds ?: emptyList()

    val hasRole = roles.any { it in memberRoleIds }

    if (!hasRole) {
        return fail(Key("You don't have permission to use this command."))
    }
    return pass()
}

class CentralServerExtension(
    val db: DB,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    val adminRoles: List<Snowflake>) : Extension() {
    override val name = "ping"

    override suspend fun setup() {
        publicSlashCommand {
            name = Key("servers")
            description = Key("Gets all servers")

            check {
                requireAnyRole(*adminRoles.toTypedArray())
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
                requireAnyRole(*adminRoles.toTypedArray())
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

        ephemeralSlashCommand(::CreateServerCommandArgs) {
            name = Key("newServer")
            description = Key("Creates a new server from name IN THE DATABASE (doesnt actually run any new server)")

            check {
                requireAnyRole(*adminRoles.toTypedArray())
            }

            action {
                db.newServer(arguments.name, arguments.maxPlayers)
                respond { content = "Created server ${arguments.name} in the database!" }
            }
        }

        ephemeralSlashCommand(::CreateMissionCommandArgs) {
            name = Key("newMission")
            description = Key("Creates a new mission from name IN THE DATABASE (doesnt actually add the mission)")

            check {
                requireAnyRole(*adminRoles.toTypedArray())
            }

            action {
                db.newMission(arguments.name, arguments.pvp)
                respond { content = "Created mission ${arguments.name} in the database!" }
            }
        }
    }
}
