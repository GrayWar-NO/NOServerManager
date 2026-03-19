package com.graywar.noServerManager.edge

import kotlinx.serialization.*


@Serializable
sealed class GamePacket

@Serializable
@SerialName("response")
data class ResponsePacket(
    val responseText: String
) : GamePacket()
