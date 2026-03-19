package com.graywar.noServerManager.dbManager.API

import io.ktor.client.HttpClient
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.response.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.http.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json


data class ApiConfig(
    val baseUrl: String,
    val steam: SteamConfig,
    val jwt: JwtConfig
)

data class SteamConfig(
    val callbackPath: String // e.g. /auth/steam/callback
) {
    fun returnUrl(baseUrl: String): String = baseUrl.trimEnd('/') + callbackPath
    fun realm(baseUrl: String): String = baseUrl.trimEnd('/')
}
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val validityMs: Long
)

fun createModule(gwApi: GwApi): Application.() -> Unit ={
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }
        )
    }

    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                com.auth0.jwt.JWT
                    .require(com.auth0.jwt.algorithms.Algorithm.HMAC256(gwApi.config.jwt.secret))
                    .withIssuer(gwApi.config.jwt.issuer)
                    .withAudience(gwApi.config.jwt.audience)
                    .build()
            )
            validate { credential ->
                val steamId = credential.payload.getClaim("steamId").asString()
                if (steamId != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
    routing {
        gwApi.registerRoutes(this)
    }
}

class GwApi(val config: ApiConfig) {
    val httpClient = HttpClient(CIO) // Ktor client

    fun registerRoutes(routing: Routing) {
        val returnUrl = config.steam.returnUrl(config.baseUrl)
        val realm = config.steam.realm(config.baseUrl)
        val callbackPath = config.steam.callbackPath

        routing.get("/auth/steam/login") {
            @Suppress("HttpUrlsUsage") val params = mapOf(
                "openid.ns" to "http://specs.openid.net/auth/2.0",
                "openid.mode" to "checkid_setup",
                "openid.return_to" to returnUrl,
                "openid.realm" to realm,
                "openid.identity" to "http://specs.openid.net/auth/2.0/identifier_select",
                "openid.claimed_id" to "http://specs.openid.net/auth/2.0/identifier_select"
            )

            val query = params.entries.joinToString("&") {
                "${it.key}=${java.net.URLEncoder.encode(it.value, java.nio.charset.StandardCharsets.UTF_8)}"
            }

            call.respondRedirect("https://steamcommunity.com/openid/login?$query")
        }
        routing.get(callbackPath) {

            val queryParams = call.request.queryParameters.entries()
                .associate { it.key to it.value.first() }

            val verificationParams = queryParams.toMutableMap()
            verificationParams["openid.mode"] = "check_authentication"

            val formBody = verificationParams.entries.joinToString("&") {
                "${it.key}=${java.net.URLEncoder.encode(it.value, java.nio.charset.StandardCharsets.UTF_8)}"
            }

            val response: HttpResponse = httpClient.post("https://steamcommunity.com/openid/login") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(formBody)
            }

            val body = response.bodyAsText()
            if (!body.contains("is_valid:true")) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid Steam login")
                return@get
            }

            val identity = queryParams["openid.identity"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)

            val steamId = identity.substringAfterLast("/")

            val token = generateJwt(steamId)

            call.respond(mapOf(
                "token" to token,
                "steamId" to steamId
            ))
        }

        routing.authenticate("auth-jwt") {
            get("/me") {
                get("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val steamId = principal.payload.getClaim("steamId").asString()

                    call.respond(mapOf("steamId" to steamId))
                }
            }
        }
    }

    private fun generateJwt(steamId: String): String {
        val now = System.currentTimeMillis()

        return com.auth0.jwt.JWT.create()
            .withIssuer(config.jwt.issuer)
            .withAudience(config.jwt.audience)
            .withClaim("steamId", steamId)
            .withExpiresAt(java.util.Date(now + config.jwt.validityMs))
            .sign(com.auth0.jwt.algorithms.Algorithm.HMAC256(config.jwt.secret))
    }
}



