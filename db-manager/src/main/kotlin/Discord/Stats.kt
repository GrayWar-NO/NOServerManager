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
import io.ktor.client.request.forms.ChannelProvider
import io.ktor.utils.io.ByteReadChannel
import org.knowm.xchart.*
import org.knowm.xchart.style.Styler
import java.awt.Color
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

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

        ephemeralSlashCommand {
            name = Key("weapons")
            description = Key("get stats about your weapon use")

            ephemeralSubCommand {
                name = Key("all")
                description = Key("Find out what's your favourite weapon")

                action {
                    val (steamID, linked) = checkUserLinked()
                    if (!linked) return@action
                    val bytes = generatePieChart(db.getWeaponsToKillsForUser(steamID))
                    respond {
                        addFile(
                            "chart.png",
                            ChannelProvider { ByteReadChannel(bytes) }
                        )
                        embed {
                            title = "Your weapons"
                            image = "attachment://chart.png"
                        }
                    }
                }
            }
            ephemeralSubCommand {
                name = Key("aircraft")
                description = Key("weapons for your aircraft kills")

                action {
                    val (steamID, linked) = checkUserLinked()
                    if (!linked) return@action
                    val bytes = generatePieChart(db.getWeaponsToKillsForUser(steamID, aircraftOnly = true))
                    respond {
                        addFile(
                            "chart.png",
                            ChannelProvider { ByteReadChannel(bytes) }
                        )
                        embed {
                            title = "Weapons for your aircraft kills"
                            image = "attachment://chart.png"
                        }
                    }
                }
            }
            ephemeralSubCommand {
                name = Key("players")
                description = Key("weapons for your player kills")
                action {
                    val (steamID, linked) = checkUserLinked()
                    if (!linked) return@action
                    val bytes = generatePieChart(db.getWeaponsToKillsForUser(steamID, playerOnly = true))
                    respond {
                        addFile(
                            "chart.png",
                            ChannelProvider { ByteReadChannel(bytes) }
                        )
                        embed {
                            title = "Weapons for your player kills"
                            image = "attachment://chart.png"
                        }
                    }
                }
            }
        }
        ephemeralSlashCommand {
            name = Key("kills")
            description = Key("get stats about what units you kill")

            ephemeralSubCommand {
                name = Key("all")
                description = Key("Find out what units you kill most")

                action {
                    val (steamID, linked) = checkUserLinked()
                    if (!linked) return@action
                    val bytes = generatePieChart(db.getTargetsToKillsForUser(steamID))
                    respond {
                        addFile(
                            "chart.png",
                            ChannelProvider { ByteReadChannel(bytes) }
                        )

                        embed {
                            title = "Your kills"
                            image = "attachment://chart.png"
                        }
                    }
                }
            }
            ephemeralSubCommand {
                name = Key("aircraft")
                description = Key("types of your aircraft kills")

                action {
                    val (steamID, linked) = checkUserLinked()
                    if (!linked) return@action
                    val bytes = generatePieChart(db.getTargetsToKillsForUser(steamID, aircraftOnly = true))

                    respond {
                        addFile(
                            "chart.png",
                            ChannelProvider { ByteReadChannel(bytes) }
                        )
                        embed {
                            title = "Find out what aircraft you kill most"
                            image = "attachment://chart.png"
                        }
                    }
                }
            }
            ephemeralSubCommand {
                name = Key("players")
                description = Key("find out what units you kill players in the most.")

                action {
                    val (steamID, linked) = checkUserLinked()
                    if (!linked) return@action
                    val bytes = generatePieChart(db.getTargetsToKillsForUser(steamID, playerOnly = true))
                    respond {
                        addFile(
                            "chart.png",
                            ChannelProvider { ByteReadChannel(bytes) }
                        )
                        embed {
                            title = "units of your player kills"
                            image = "attachment://chart.png"
                        }
                    }
                }
            }
            ephemeralSubCommand {
                name = Key("KD")
                description = Key("Your kill/death ratio")
                action {
                    val (steamID, linked) = checkUserLinked()
                    if (!linked) return@action
                    val kd = db.getKDForPlayer(steamID)
                    respond{
                        embed{
                            title = "Your K/D ratio is $kd."
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
                "${i + (pageNumber * 10)}:${if (result[i].isAircraft) " AI" else ""} ${result[i].unit} with ${result[i].weapon}"
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

fun generatePieChart(input: Map<String, Long>): ByteArray {
    val data = groupSmallSlices(input)
    val chart = PieChartBuilder()
        .width(700)
        .height(400)
        .build()

    chart.styler.apply {
        legendPosition = Styler.LegendPosition.OutsideE

        chartBackgroundColor = Color.decode("#2B2D31")
        plotBackgroundColor = Color.decode("#2B2D31")

        legendBackgroundColor = Color.decode("#313338")
        legendBorderColor = Color.decode("#1E1F22")

        chartFontColor = Color.decode("#F2F3F5")

    }

    data.forEach { (label, value) ->
        chart.addSeries(label, value)
    }
    val out = ByteArrayOutputStream()
    ImageIO.write(BitmapEncoder.getBufferedImage(chart), "png", out)

    return out.toByteArray()
}

fun groupSmallSlices(
    data: Map<String, Long>,
    maxSlices: Int = 13,
): Map<String, Long> {
    val sorted = data.entries
        .sortedByDescending { it.value }

    val result = linkedMapOf<String, Long>()
    var other = 0L

    sorted.forEachIndexed { index, entry ->
        if (index < maxSlices) {
            result[entry.key] = entry.value
        } else {
            other += entry.value
        }
    }
    if (other > 0) {
        result["Other"] = other
    }
    return result
}
