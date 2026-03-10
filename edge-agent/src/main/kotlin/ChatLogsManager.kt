package com.graywar.noServerManager.edge

import com.google.protobuf.Timestamp
import kotlinx.serialization.*
import com.graywar.noServerManager.proto.ChatLog
import kotlinx.coroutines.channels.Channel
import java.time.Instant

@Serializable
@SerialName("chatlog")
data class ChatLogPacket(
    val logText: String,
    val chatName: String,
    val steamID: ULong
) : GamePacket()

suspend fun emitChatLog(flow: Channel<ChatLog>, chatLog: ChatLogPacket){
    val log = ChatLog
        .newBuilder()
        .setMessage(chatLog.logText)
        .setSenderSteamID(chatLog.steamID.toLong())
        .setMessageChannel(chatLog.chatName)
        .setMessageSendTime(Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).build())
        .build()
    flow.send(log)
}