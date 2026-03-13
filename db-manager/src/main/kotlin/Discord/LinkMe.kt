package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.proto.LinkUser
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.converters.impl.int
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.i18n.Key
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class LinkMeArguments: Arguments() {
    val code by int {
        name = Key("code")
        description = Key("code you got in the game")
    }
}

class LinkMeExtension(val db: DB): Extension() {
    override val name: String = "linkMe"
    val codesToSteamIDs = mutableMapOf<Int, ULong>()

    override suspend fun setup() {
        ephemeralSlashCommand(::LinkMeArguments) {
            name = Key("linkme")
            description = Key("Links your discord to your in-game stats. Use /linkme in-game first!")

            action {
                if (!codesToSteamIDs.keys.contains(arguments.code)) {
                    respond {
                        content = "The code ${arguments.code} you have given was not registered. Use /linkme in-game first!"
                    }
                    return@action
                }
                val steamID = codesToSteamIDs[arguments.code]!!
                db.addLink(steamID, user.id.toString())
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
}
