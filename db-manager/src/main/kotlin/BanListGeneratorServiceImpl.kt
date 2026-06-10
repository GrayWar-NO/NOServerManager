package com.graywar.noServerManager.dbManager

import com.google.protobuf.Empty
import com.graywar.noServerManager.proto.Ban
import com.graywar.noServerManager.proto.BanList
import com.graywar.noServerManager.proto.BanListGeneratorGrpcKt

class BanListGeneratorServiceImpl(private val db: DB): BanListGeneratorGrpcKt.BanListGeneratorCoroutineImplBase() {
    override suspend fun getBanList(request: Empty): BanList {
        val bans = db.getAllBans()
        val requestBuilder = BanList.newBuilder()
        for (ban in bans) {
            requestBuilder.addBans(Ban.newBuilder().setSteamID(ban.user.toLong()).setReason(ban.reason))
        }
        return requestBuilder.build()
    }
}