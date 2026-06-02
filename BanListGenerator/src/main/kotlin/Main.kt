package org.graywar.noServerManager.banListGenerator

import com.google.protobuf.Empty
import com.graywar.noServerManager.edge.EdgeConfig
import com.graywar.noServerManager.proto.BanListGeneratorGrpcKt
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import kotlinx.coroutines.*
import java.io.File

fun main() = runBlocking {
    val config = ConfigLoaderBuilder.default()
        .addFileSource("edge-agent.conf")
        .build()
        .loadConfigOrThrow<EdgeConfig>()

    val sslContext = GrpcSslContexts.forClient()
        .trustManager(File("CA/ca.crt")) // trust server
        .keyManager(
            File("CA/${config.name}.crt"), // client certificate
            File("CA/${config.name}.key")  // client private key
        )
        .build()

    val channel = NettyChannelBuilder
        .forAddress(config.central.host, config.central.port)
        .sslContext(sslContext)
        .build()

    val grpcStub = BanListGeneratorGrpcKt.BanListGeneratorCoroutineStub(channel)
    val banlist = grpcStub.getBanList(Empty.getDefaultInstance())
    File("ban_list.txt").printWriter().use { out -> banlist.bansList.forEach { out.println("${it.steamID.toULong()} // ${it.reason}") } }
}
