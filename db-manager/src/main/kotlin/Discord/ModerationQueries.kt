package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.dbManager.UserReasonTime
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
            when {
                value == null -> pass()
                value!!.toULongOrNull() != null -> pass()
                else -> fail(Key("User must be a valid SteamID"))
            }
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
        ephemeralSlashCommand(::UserForModCommandArg) {
            name = Key("Kicks")
            description = Key("Get kick history")

            check {
                requireAnyRole(*adminRoles.toTypedArray())
            }

            action {
                val steamID = arguments.user?.toULongOrNull()
                val userName: String? = if (steamID == null) null else db.getLastPlayerName(steamID)
                val data = db.getKicks(steamID)
                respond { pagedList(data) { pd, p -> kickList(pd, userName, p) } }
            }
        }
        ephemeralSlashCommand(::UserForModCommandArg) {
            name = Key("Warns")
            description = Key("Get warn history")

            check {
                requireAnyRole(*adminRoles.toTypedArray())
            }

            action {
                val steamID = arguments.user?.toULongOrNull()
                val userName: String? = if (steamID == null) null else db.getLastPlayerName(steamID)
                val data = db.getWarns(steamID)
                respond { pagedList(data) { pd, p -> warnList(pd, userName, p) } }
            }
        }
    }

    fun EmbedBuilder.banList(data: List<UserReasonTime>, pageNumber: Int) {
        title = "Ban history:"
        var content = ""
        for ((i, ban) in data.withIndex()) {
            content += "${i + (pageNumber * 10)}: ${ban.username}(${ban.steamID}) was banned for ${ban.reason} on <t:${ban.time.epochSeconds}:f>.\n"
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

    fun EmbedBuilder.kickList(data: List<UserReasonTime>, user: String?, pageNumber: Int) {
        title = "Kick History${if (user == null) "" else " for $user"}:"
        var content = ""
        for ((i, k) in data.withIndex()) {
            content += if (user == null)
                "${i + (pageNumber * 10)}: ${k.username}(${k.steamID}) got kicked on <t:${k.time.epochSeconds}:f> for ${k.reason}.\n"
            else
                "${i + (pageNumber * 10)}: kicked on <t:${k.time.epochSeconds}:f> for ${k.reason}.\n"
        }
        description = content
    }

    fun EmbedBuilder.warnList(data: List<UserReasonTime>, user: String?, pageNumber: Int) {
        title = "Warn History${if (user == null) "" else " for $user"}:"
        var content = ""
        for ((i, k) in data.withIndex()) {
            content += if (user == null)
                "${i + (pageNumber * 10)}: ${k.username}(${k.steamID}) got warned on <t:${k.time.epochSeconds}:f> for ${k.reason}.\n"
            else
                "${i + (pageNumber * 10)}: warned on <t:${k.time.epochSeconds}:f> for ${k.reason}.\n"
        }
        description = content
    }

}