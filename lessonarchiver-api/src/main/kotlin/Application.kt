package com.lessonarchiver

import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    install(ContentNegotiation) {
        json()
    }

    configureKoin()
    configureMonitoring()
    configureAuthentication()
    configureMigrations()
    configureRouting()
}
