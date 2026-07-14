package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.proto.permissionBreakdown
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Guild
import dev.kordex.core.extensions.Extension


class ModListExtension(val guildId: Snowflake, val adminRole: Snowflake, val moderatorRole: Snowflake, val db: DB): Extension() {
    override val name = "ModList"
    lateinit var guild: Guild

    override suspend fun setup() {
        guild = kord.getGuild(guildId)
    }

    suspend fun get(): permissionBreakdown {
        val result = permissionBreakdown.newBuilder()
        guild.members.collect { member ->
            val steamID = db.getSteamIDForDiscord(member.id.toString()) ?: return@collect
            if (member.roleIds.contains(adminRole)) result.addAdmins(steamID.toLong())
            if (member.roleIds.contains(moderatorRole)) result.addMods(steamID.toLong())
        }
        return result.build()
    }
}