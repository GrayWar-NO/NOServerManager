package com.graywar.noServerManager.dbManager

import com.graywar.noServerManager.dbManager.API.ApiConfig
import com.graywar.noServerManager.dbManager.API.GwApi
import com.graywar.noServerManager.dbManager.API.createModule
import com.graywar.noServerManager.dbManager.Discord.Discord
import com.graywar.noServerManager.dbManager.Discord.DiscordConfig
import io.grpc.ServerInterceptors
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addFileSource
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.time.Duration.Companion.hours

data class HostConfig(val port: Int, val db: DataBaseConfig, val discord: DiscordConfig, val api: ApiConfig)


class CentralServer {
    private val config = ConfigLoaderBuilder.default()
        .addFileSource("central.conf")
        .build()
        .loadConfigOrThrow<HostConfig>()

    private val db = DB(config.db)

    private val edgeAgent = EdgeAgentServiceImpl(db)

    private val discord = Discord(config.discord, config.db, edgeAgent)

    private val server = NettyServerBuilder
        .forPort(config.port)
        .sslContext(
            GrpcSslContexts.forServer(
                File("CA/central.crt"),
                File("CA/central.key"),
            )
                .trustManager(File("CA/ca.crt"))
                .clientAuth(ClientAuth.REQUIRE)
                .build()
        )
        .addService(
            ServerInterceptors.intercept(
                edgeAgent,
                AgentIdInterceptor()
            )
        )
        .build()

    private val api = GwApi(config.api)

    fun start() = runBlocking {
        server.start()
        if (config.discord.enable){
            discord.start()
            edgeAgent.setLoggedEventCallback(discord::sendLoggedEvent)
            edgeAgent.setTKCallback(discord.teamKillExt::sendTeamKill)
            edgeAgent.setLinkCallback(discord.linkExt::newLink)
            edgeAgent.setReportCallback(discord.teamKillExt::sendReport)
            edgeAgent.setPermissionBreakdownGetter(discord.modListExt::get)
            edgeAgent.setBanLoggerCallback(discord.banWebhookExt::log)
        }
        manageEndingBans()
        embeddedServer(Netty, config.api.port, module = createModule(api)).start()
        @Suppress("KotlinPrintToLogpoint", "RedundantSuppression")
        println("[Central] gRPC server started on ${config.port}")
        server.awaitTermination()
    }

    @Suppress("unused")
    fun stop() {
        server.shutdownNow()!!
        discord.stop()
    }

    private fun manageEndingBans() = CoroutineScope(Dispatchers.Default).launch {
        while (isActive) {
            delay(12.hours)
            for (ban in db.getAllEndedBansInLast(12.hours)) {
                edgeAgent.sendBanBack(ban, emptyList())
            }
        }
        println("cancelled")
    }


}

fun main() = CentralServer().start()
