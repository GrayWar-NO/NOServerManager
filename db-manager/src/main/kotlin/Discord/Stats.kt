package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.ephemeralSlashCommand
import dev.kordex.i18n.Key

data class Kill(val name: String?, val weapon: String, val unit: String, val isAircraft: Boolean)


class StatsExtension(val db: DB) : Extension() {
    override val name = "Stats"

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = Key("history")
            description = Key("get your server history")

            ephemeralSubCommand {
                name = Key("kills")
                description = Key("Get all instances of you killing something")

                action {
                    val steamID = db.getSteamIDForDiscord(user.id.toString())
                    if (steamID == null) {
                        respond {
                            content = "You are not in the database! Please use /linkme in-game, then here first!"
                        }
                        return@action
                    }
                    var pageNumber = 0
                    var result = db.getKillsForUser(steamID, pageNumber)
                    respond {
                        embed { killsList(result.first, pageNumber) }
                        components {
                            ephemeralButton {
                                label = Key("Previous page")
                                check { if (pageNumber > 0) pass() else fail(Key("No previous page available")) }
                                action {
                                    pageNumber--
                                    result = db.getKillsForUser(steamID, pageNumber)
                                    edit { embed { killsList(result.first, pageNumber) } }
                                }
                            }
                            ephemeralButton {
                                label = Key("Next page")
                                check { if (result.second) pass() else fail(Key("No next page available")) }
                                action {
                                    pageNumber++
                                    result = db.getKillsForUser(steamID, pageNumber)
                                    edit { embed { killsList(result.first, pageNumber) } }
                                }
                            }
                        }
                    }
                }
            }

            ephemeralSubCommand {
                name = Key("Deaths")
                description = Key("Get all the times you died")

                action {
                    val steamID = db.getSteamIDForDiscord(user.id.toString())
                    if (steamID == null) {
                        respond {
                            content = "You are not in the database! Please use /linkme in-game, then here first!"
                        }
                        return@action
                    }
                    var pageNumber = 0
                    var result = db.getDeathsForUser(steamID, pageNumber)
                    respond {
                        embed { deathsList(result.first, pageNumber) }
                        components {
                            ephemeralButton {
                                label = Key("Previous page")
                                check { if (pageNumber > 0) pass() else fail(Key("No previous page available")) }
                                action {
                                    pageNumber--
                                    result = db.getDeathsForUser(steamID, pageNumber)
                                    edit { embed { deathsList(result.first, pageNumber) } }
                                }
                            }
                            ephemeralButton {
                                label = Key("Next page")
                                check { if (result.second) pass() else fail(Key("No next page available")) }
                                action {
                                    pageNumber++
                                    result = db.getDeathsForUser(steamID, pageNumber)
                                    edit { embed { deathsList(result.first, pageNumber) } }
                                }
                            }
                        }
                    }
                }
            }

        }
    }


    fun EmbedBuilder.killsList(result: List<Kill>, pageNumber: Int) {
        title = "Your kills: "
        var content = ""
        for (i in result.indices) {
                content += if (result[i].name == null) {
                    "${i + (pageNumber * 10)}: ${result[i].unit} with ${result[i].weapon}"
                } else "${i + (pageNumber * 10)}: ${result[i].name} in ${result[i].unit} with ${result[i].weapon}"
                content += "\n"
        }
        description = content
    }

    fun EmbedBuilder.deathsList(result: List<Kill>, pageNumber: Int) {
        title = "Your deaths: "
        var content = ""
        for (i in result.indices) {
            content += if (result[i].name == null) {
                "${i + (pageNumber * 10)}: ${result[i].unit} with ${result[i].weapon}"
            } else "${i + (pageNumber * 10)}: ${result[i].name} in ${result[i].unit} with ${result[i].weapon}"
            content += "\n"
        }
        description = content
    }
}