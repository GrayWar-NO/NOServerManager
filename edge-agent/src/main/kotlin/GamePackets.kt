package com.graywar.noServerManager.edge

import kotlinx.serialization.*


@Serializable
sealed class GamePacket

@Serializable
@SerialName("Response")
data class ResponsePacket(
    val ResponseText: String
) : GamePacket()
