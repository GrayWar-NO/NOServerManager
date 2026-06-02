package com.graywar.noServerManager.dbManager

import com.google.protobuf.Empty
import com.graywar.noServerManager.proto.Ban
import com.graywar.noServerManager.proto.BanList
import com.graywar.noServerManager.proto.BanListGeneratorGrpcKt

class BanListGeneratorServiceImpl(private val db: DB): BanListGeneratorGrpcKt.BanListGeneratorCoroutineImplBase() {
    override suspend fun getBanList(request: Empty): BanList {
        val bans = db.getBans()
        val requestBuilder = BanList.newBuilder()
        for (ban in bans) {
            requestBuilder.addBans(Ban.newBuilder().setSteamID(ban.first.toLong()).setReason(ban.second))
        }
        return requestBuilder.build()
    }
}