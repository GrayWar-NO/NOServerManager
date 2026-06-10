package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.Ban
import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.dbManager.Mission
import dev.kord.common.entity.Snowflake
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.optionalString
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.i18n.Key


class UserForModCommandArg : Arguments() {
    val user by optionalString {
        name = Key("user")
        description = Key("SteamID of the user")
        validate {
            if (value == null) pass()
            else value!!.toULongOrNull() != null
        }
    }
}

class ModQueriesExtension(val db: DB, val adminRoles: List<Snowflake>) : Extension() {
    override val name: String = "Mod queries"
    override suspend fun setup() {
        ephemeralSlashCommand {
            name = Key("bans")
            description = Key("Get ban history")

            check {
                requireAnyRole(*adminRoles.toTypedArray())
            }

            action {
                val bans = db.getAllBans()
                respond { pagedList(bans) { pd, p -> banList(pd, p) } }
            }
        }
        ephemeralSlashCommand(::UserForModCommandArg) {
            name = Key("missions")
            description = Key("Get history of missions")

            check {
                requireAnyRole(*adminRoles.toTypedArray())
            }

            action {
                val steamID = arguments.user?.toULongOrNull()
                val userName: String? = if (steamID == null) null else db.getLastPlayerName(steamID)
                val data = db.getMissions(steamID)
                respond { pagedList(data) { pd, p -> missionList(pd, userName, p) } }
            }
        }

        /*      TODO
                 Kick history (optional user)
                 Warn history (optional user)
        */

    }

    fun EmbedBuilder.banList(data: List<Ban>, pageNumber: Int) {
        title = "Ban history:"
        var content = ""
        for ((i, ban) in data.withIndex()) {
            content += "${i + (pageNumber * 10)}: ${ban.username}(${ban.user}) was banned for ${ban.reason} on <t:${ban.time.epochSeconds}:f>.\n"
        }
        description = content
    }

    fun EmbedBuilder.missionList(data: List<Mission>, user: String?, pageNumber: Int) {
        title = "Mission History${if (user == null) "" else " for $user"}:"
        var content = ""
        for ((i, m) in data.withIndex()) {
            content += "${i + (pageNumber * 10)}: ${m.name} on server ${m.server} on <t:${m.start.epochSeconds}:f>.\n"
        }
        description = content
    }
}