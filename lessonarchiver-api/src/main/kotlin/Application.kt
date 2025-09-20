package com.lessonarchiver

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
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
    configureRouting()
}
