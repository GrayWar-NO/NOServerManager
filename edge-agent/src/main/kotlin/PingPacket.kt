package com.graywar.noServerManager.edge

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

@Serializable
@SerialName("ping")
data class PingPacket(
    val data: String,
) : GamePacket()

class PingPacketProcessor
{
    var pingPacketOutbound: Boolean = false
    var pingOk = true
    private val json = Json { encodeDefaults = true }


    fun processPacket(packet: PingPacket): PingPacket?{
        if (pingPacketOutbound) {
            pingPacketOutbound = false
            return null
        }
        return packet
    }

    @Suppress("unused")
    fun sendNewPing(client: ManagedSocket): Boolean{
        if (pingPacketOutbound) {
            pingOk = false
            return false
        }
        pingPacketOutbound = true
        val packet: GamePacket = PingPacket(data="TEST")
        try {
            client.sendWithWriter(json.encodeToString(packet))
        } catch (_: NotConnectedException){
            return false
        }
        return true
    }
}



