package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.proto.LinkUser
import dev.kord.common.entity.Snowflake
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.int
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.i18n.Key
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class LinkMeArguments: Arguments() {
    val code by int {
        name = Key("code")
        description = Key("code you got in the game")
    }
}

class LinkMeExtension(val db: DB, val linkedRole: Snowflake, val linkedGuild: Snowflake): Extension() {
    override val name: String = "linkMe"
    val codesToSteamIDs = mutableMapOf<Int, ULong>()

    override suspend fun setup() {
        ephemeralSlashCommand(::LinkMeArguments) {
            name = Key("linkme")
            description = Key("Links your discord to your in-game stats. Use /linkme in-game first!")

            action {
                if (db.isUserInDb(user.id.toString())){
                    respond { content = "Your discord was already linked. You cannot do it again." }
                    return@action
                }
                if (!codesToSteamIDs.keys.contains(arguments.code)) {
                    respond {
                        content = "The code ${arguments.code} you have given was not registered. Use /linkme in-game first!"
                    }
                    return@action
                }
                val steamID = codesToSteamIDs[arguments.code]!!
                db.addLink(steamID, user.id.toString())
                addLinkedRole(user.id)
                codesToSteamIDs.remove(arguments.code)
                respond {
                    content = "Your in-game stats have been linked sucessfully!"
                }
            }
        }
    }

    fun newLink(link: LinkUser){
        codesToSteamIDs[link.oneTimeCode] = link.senderSteamID.toULong()
        kord.launch {
            delay(10.minutes)
            if (codesToSteamIDs.keys.contains(link.oneTimeCode)){
                codesToSteamIDs.remove(link.oneTimeCode)
            }
        }
    }

    suspend fun addLinkedRole(userId: Snowflake){
        val guild = kord.getGuild(linkedGuild)
        val member = guild.getMember(userId)
        member.addRole(linkedRole)
    }

    suspend fun initLinkedRoles(){
        db.getLinkedUsers().chunked(5).forEach { batch ->
            batch.forEach { user -> addLinkedRole(Snowflake(user)) }
            delay(1.seconds)
        }
    }

}
