package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.proto.statusResponse
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.FollowupMessageCreateBuilder
import dev.kord.rest.builder.message.embed
import dev.kordex.core.components.components
import dev.kordex.core.components.ephemeralButton
import dev.kordex.i18n.Key
import kotlin.math.min

suspend fun <T> FollowupMessageCreateBuilder.pagedList(
    data: List<T>,
    pageSize: Int = 10,
    embedBuilder: suspend EmbedBuilder.(pageData: List<T>, page: Int) -> Unit
) {
    var pageNumber = 0
    fun pageData(page: Int): List<T> {
        val start = page * pageSize
        val end = min(start + pageSize, data.size)
        return data.subList(start, end)
    }
    statusResponse {
        embed {
            embedBuilder(pageData(pageNumber), pageNumber)
        }

        components {
            ephemeralButton {
                label = Key("Previous page")
                check { if (pageNumber > 0) pass() else fail(Key("No previous page available")) }
                action {
                    pageNumber--
                    edit {
                        embed {
                            embedBuilder(pageData(pageNumber), pageNumber)
                        }
                    }
                }
            }
            ephemeralButton {
                label = Key("Next page")
                check { if ((pageNumber + 1) * pageSize < data.size) pass() else fail(Key("No next page available"))
                }
                action {
                    pageNumber++
                    edit {
                        embed {
                            embedBuilder(pageData(pageNumber), pageNumber)
                        }
                    }
                }
            }
        }
    }
}