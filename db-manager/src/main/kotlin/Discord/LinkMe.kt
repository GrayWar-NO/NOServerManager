package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.proto.LinkUser
import dev.kord.common.entity.Snowflake
import dev.kord.core.exception.EntityNotFoundException
import dev.kordex.core.checks.userFor
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.int
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.i18n.Key
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class LinkMeExtension(val db: DB, val linkedRole: Snowflake, val linkedGuild: Snowflake): Extension() {
    override val name: String = "linkMe"
    val codesToSteamIDs = mutableMapOf<Int, ULong>()

    inner class LinkMeArguments: Arguments() {
        val code by int {
            name = Key("code")
            description = Key("code you got in the game")
        }

        override fun validate(locale: Locale) {
            super.validate(locale)
            if (!codesToSteamIDs.containsKey(code)){
                error("the code $code you have given was not registered. Use /linkme in-game first!")
            }
        }
    }

    override suspend fun setup() {
        ephemeralSlashCommand(::LinkMeArguments) {
            name = Key("linkme")
            description = Key("Links your discord to your in-game stats. Use /linkme in-game first!")

            check {
                val user = userFor(event)
                if (user == null) {
                    fail(Key("User not found."))
                    return@check
                }
                if (db.isUserInDb(user.id.toString())) {
                    fail(Key("Your discord was already linked. You cannot do it again."))
                }
            }

            action {
                val steamID = codesToSteamIDs[arguments.code]
                if (steamID == null) {
                    respond {
                        content = "The code you entered is invalid."
                    }
                    return@action
                }
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
        try {
            val guild = kord.getGuild(linkedGuild)
            val member = guild.getMember(userId)
            member.addRole(linkedRole)
        }catch (e: EntityNotFoundException){
            println("Could not add linked role to user ${userId.value}: ${e.message}")
        }
        catch (_: Exception) { }
    }

    suspend fun initLinkedRoles(){
        db.getLinkedUsers().chunked(5).forEach { batch ->
            batch.forEach { user -> addLinkedRole(Snowflake(user)) }
            delay(1.seconds)
        }
    }

}
