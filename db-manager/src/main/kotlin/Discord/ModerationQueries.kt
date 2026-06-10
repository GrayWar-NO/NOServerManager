package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import dev.kord.common.entity.Snowflake
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.i18n.Key
import kotlin.math.min

class ModQueriesExtension(val db: DB, val adminRoles: List<Snowflake>): Extension() {
    override val name: String = "Mod queries"
    override suspend fun setup() {
        ephemeralSlashCommand {
            name = Key("bans")
            description = Key("Get ban history")

            check {
                requireAnyRole(*adminRoles.toTypedArray())
            }

            action {
                val bans = db.getBans()
                var pageNumber = 0
                var currentBans = bans.subList(0, min(10, bans.size))
                respond {
                    embed {
                        banList(currentBans, db, pageNumber)
                    }
                    components {
                        ephemeralButton {
                            label = Key("Previous page")
                            check { if (pageNumber > 0) pass() else fail(Key("No previous page available")) }
                            action {
                                pageNumber--
                                currentBans = bans.subList(pageNumber*10, pageNumber*10 + 10)
                                edit { embed { banList(currentBans, db, pageNumber) } }
                            }
                        }
                        ephemeralButton {
                            label = Key("Next page")
                            check { if (pageNumber*10 + 10 < bans.size) pass() else fail(Key("No next page available")) }
                            action {
                                pageNumber++
                                currentBans = bans.subList(pageNumber*10, min(pageNumber*10 + 10, bans.size))
                                edit { embed { banList(currentBans, db, pageNumber) } }
                            }
                        }
                    }
                }
            }
        }
        /*      TODO
                 Mission history (optional user)
                 TK history (optional user)
                 Kick history (optional user)
                 Warn history (optional user)
        */

    }

    fun EmbedBuilder.banList(data: List<Pair<ULong, String>>, db:DB, pageNumber: Int) {
        title = "Ban history:"
        var content = ""
        for ((i, ban) in data.withIndex()) {
            content += "${i + (pageNumber * 10)}: ${db.getLastPlayerName(ban.first)}(${ban.first}) banned for ${ban.second}.\n"
        }
        description = content
    }

}