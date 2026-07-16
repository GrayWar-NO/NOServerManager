package com.graywar.noServerManager.dbManager.Discord

import com.graywar.noServerManager.dbManager.DB
import com.graywar.noServerManager.proto.BanRequest
import dev.kordex.core.extensions.Extension

class BanLogExt(url: String, private val db: DB): Extension() {
    override val name: String = "BanLogger"

    private val sender = WebhookSender(kord, url, "Bans logger")
    override suspend fun setup() {}

    suspend fun log(data: BanRequest, server: String){
        val name = db.getLastPlayerName(data.steamID.toULong())
        if (data.shouldBeBanned)
            sender.send("- name when banned: $name\n- steamID: ${data.steamID}\n- reason: ${data.reason}\n- server: $server")
        else
            sender.send("$name (${data.steamID}) was unbanned.")
    }
}