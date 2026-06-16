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
    pageSize: Int = 10,
    initialPage: Int = 0,
    getPage: suspend (page: Int, pageSize: Int) -> Pair<List<T>, Boolean>,
    embedBuilder: suspend EmbedBuilder.(pageData: List<T>, page: Int) -> Unit
) {
    var pageNumber = initialPage
    var pageData = getPage(pageNumber, pageSize)

    statusResponse {
        embed {
            embedBuilder(pageData.first, pageNumber)
        }

        components {
            ephemeralButton {
                label = Key("Previous page")
                check {
                    if (pageNumber > 0) pass()
                    else fail(Key("No previous page available"))
                }
                action {
                    pageNumber--
                    pageData = getPage(pageNumber, pageSize)
                    edit {
                        embed {
                            embedBuilder(pageData.first, pageNumber)
                        }
                    }
                }
            }

            ephemeralButton {
                label = Key("Next page")
                check {
                    if (pageData.second) pass()
                    else fail(Key("No next page available"))
                }
                action {
                    pageNumber++
                    pageData = getPage(pageNumber, pageSize)
                    edit {
                        embed {
                            embedBuilder(pageData.first, pageNumber)
                        }
                    }
                }
            }
        }
    }
}

suspend fun <T> FollowupMessageCreateBuilder.pagedList(
    data: List<T>,
    pageSize: Int = 10,
    initialPage: Int = 0,
    embedBuilder: suspend EmbedBuilder.(pageData: List<T>, page: Int) -> Unit,
) {
   pagedList(
       pageSize = pageSize,
       initialPage = initialPage,
       getPage = {page, pageSize ->
           val start = page * pageSize
           val end = min(start + pageSize, data.size)
           val data = data.subList(start, end)
           Pair(data, end == data.size)
       },
       embedBuilder = embedBuilder,
   )
}