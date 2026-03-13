package com.graywar.noServerManager.edge

import com.graywar.noServerManager.proto.EdgeAgentServiceGrpcKt
import com.graywar.noServerManager.proto.LinkUser
import kotlinx.serialization.*


@Serializable
@SerialName("link")
data class LinkPacket(
    val steamID: ULong,
    val oneTimeCode: Int
): GamePacket()

suspend fun sendLink(link: LinkPacket, grpc: EdgeAgentServiceGrpcKt.EdgeAgentServiceCoroutineStub) {
    val outPacket = LinkUser.newBuilder()
        .setSenderSteamID(link.steamID.toLong())
        .setOneTimeCode(link.oneTimeCode)
        .build()
    grpc.sendLinkCode(outPacket)
}
