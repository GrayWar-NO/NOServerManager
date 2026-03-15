package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.embed
import dev.kordex.core.commands.Arguments
import dev.kordex.core.commands.application.slash.EphemeralSlashCommandContext
import dev.kordex.core.commands.application.slash.ephemeralSubCommand
import dev.kordex.core.commands.application.slash.group
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.core.components.forms.ModalForm
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

            group(Key("kills")) {
                description = Key("get your kill history")
                ephemeralSubCommand {
                    name = Key("all")
                    description = Key("Get all instances of you killing something")
                    action {
                        getUserKills(true)
                    }
                }
                ephemeralSubCommand {
                    name = Key("players")
                    description = Key("Get all instances of you killing a player")
                    action {
                        getUserKills(false)
                    }
                }

            }

            ephemeralSubCommand {
                name = Key("deaths")
                description = Key("Get all the times you died")

                action {
                    val (steamID, linked) = checkUserLinked()
                    if (!linked) return@action

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

    suspend fun <A, M> EphemeralSlashCommandContext<A, M>.checkUserLinked(): Pair<ULong, Boolean>
    where
    A : Arguments,
    M : ModalForm
    {
        val steamID = db.getSteamIDForDiscord(user.id.toString())
        if (steamID == null) {
            respond {
                content = "You are not in the database! Please use /linkme in-game, then here first!"
            }
            return Pair(0UL, false)
        }
        return Pair(steamID, true)
    }

    suspend fun <A, M> EphemeralSlashCommandContext<A, M>.getUserKills(all: Boolean)
    where
    A: Arguments,
    M : ModalForm
    {
        val (steamID, linked) = checkUserLinked()
        if (!linked) return

        var pageNumber = 0
        var result = db.getKillsForUser(steamID, pageNumber, playerOnly = !all)
        respond {
            embed { killsList(result.first, pageNumber) }
            components {
                ephemeralButton {
                    label = Key("Previous page")
                    check { if (pageNumber > 0) pass() else fail(Key("No previous page available")) }
                    action {
                        pageNumber--
                        result = db.getKillsForUser(steamID, pageNumber, playerOnly = !all)
                        edit { embed { killsList(result.first, pageNumber) } }
                    }
                }
                ephemeralButton {
                    label = Key("Next page")
                    check { if (result.second) pass() else fail(Key("No next page available")) }
                    action {
                        pageNumber++
                        result = db.getKillsForUser(steamID, pageNumber, playerOnly = !all)
                        edit { embed { killsList(result.first, pageNumber) } }
                    }
                }
            }
        }
    }

}