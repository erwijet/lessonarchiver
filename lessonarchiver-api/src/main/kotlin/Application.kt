package com.lessonarchiver

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain
        .main(args)
}

@OptIn(ExperimentalUuidApi::class)
fun Application.module() {
    install(ContentNegotiation) {
        json(
            json =
                Json {
                    serializersModule =
                        SerializersModule {
                            contextual(Uuid.serializer())
                        }
                },
        )
    }

    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHost("localhost:3000", schemes = listOf("http"))
        allowHost("lessonarchiver.com", schemes = listOf("https"), subDomains = listOf("app"))
    }

    configureKoin()
    configureMonitoring()
    configureAuthentication()
    configureMigrations()
    configureElastic()
    configureRouting()
}
