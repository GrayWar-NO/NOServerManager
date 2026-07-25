package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.dbManager.EdgeAgentServiceImpl
import com.graywar.noServerManager.proto.PermissionLevel
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Member
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

suspend fun getPermissionLevel(user: Member, moderatorRole: Snowflake, adminRole: Snowflake): PermissionLevel {
    if (user.isOwner()) return PermissionLevel.Owner
    if (user.roleIds.contains(adminRole)) return PermissionLevel.Admin
    if (user.roleIds.contains(moderatorRole)) return PermissionLevel.Moderator
    return PermissionLevel.Everyone
}


internal suspend fun CheckContext<*>.requireAnyRoleOrOwner(vararg roles: Snowflake) {
    val member = userFor(event)?.asMemberOrNull(guildFor(event)?.id ?: return fail(Key("Guild only command")))
    if (member?.isOwner() ?: false) return

    val memberRoleIds = member?.roleIds ?: emptyList()

    val hasRole = roles.any { it in memberRoleIds }

    if (!hasRole) {
        return fail(Key("You don't have permission to use this command."))
    }
}

class CentralServerExtension(
    val db: DB,
    val cbEdgeAgent: EdgeAgentServiceImpl,
    val adminRole: Snowflake,
    val moderatorRole: Snowflake) : Extension() {
    override val name = "ping"

    override suspend fun setup() {
        publicSlashCommand {
            name = Key("servers")
            description = Key("Gets all servers")

            check {
                requireAnyRoleOrOwner(adminRole, moderatorRole)
                pass()
            }

            action {
                try {
                    var contentStr = ""
                    for ((key, value) in db.getAllServers()) {
                        contentStr += "$key: $value\n"
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
                requireAnyRoleOrOwner(adminRole, moderatorRole)
                pass()
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
                    arguments.result ?: true,
                    getPermissionLevel(user.asMember(guildFor(event)!!.id), moderatorRole, adminRole),)
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
                requireAnyRoleOrOwner(moderatorRole, adminRole)
                pass()
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
                requireAnyRoleOrOwner(moderatorRole, adminRole)
                pass()
            }

            action {
                db.newMission(arguments.name, arguments.pvp)
                respond { content = "Created mission ${arguments.name} in the database!" }
            }
        }
    }
}
