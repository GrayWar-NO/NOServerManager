package com.graywar.noServerManager.edge

import com.google.protobuf.Timestamp
import kotlinx.serialization.*
import com.graywar.noServerManager.proto.ChatLog
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.Instant

@Serializable
@SerialName("chatlog")
data class ChatLogPacket(
    val logText: String,
    val chatName: String
) : GamePacket()

suspend fun emitChatLog(flow: MutableSharedFlow<ChatLog>, chatLog: ChatLogPacket){
    val log = ChatLog
        .newBuilder()
        .setMessage(chatLog.logText)
        .setMessageChannel(chatLog.chatName)
        .setMessageSendTime(Timestamp.newBuilder().setSeconds(Instant.now().epochSecond).build())
        .build()
    flow.emit(log)
}