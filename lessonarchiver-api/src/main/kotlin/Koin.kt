package com.lessonarchiver

import com.backblaze.b2.client.B2StorageClient
import com.backblaze.b2.client.B2StorageClientFactory
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.Schema
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()

        modules(module {
            single<String>(named("mode")) { environment.config.property("mode").getString() }
            single<String>(named("db.url")) { environment.config.property("db.url").getString() }
            single<String>(named("db.username")) { environment.config.property("db.username").getString() }
            single<String>(named("db.password")) { environment.config.property("db.password").getString() }
            single<String>(named("b2.bucketId")) { environment.config.property("b2.bucketId").getString() }
            single<String>(named("b2.applicationKeyId")) { environment.config.property("b2.applicationKeyId").getString() }
            single<String>(named("b2.applicationKey")) { environment.config.property("b2.applicationKey").getString() }

            single {
                Notary(
                    client = "lessonarchiver",
                    url = environment.config.property("notary.url").getString(),
                    callback = environment.config.property("notary.callback").getString(),
                    key = environment.config.property("notary.key").getString(),
                )
            }

            single {
                HikariDataSource(HikariConfig().apply {
                    this.driverClassName = "org.postgresql.Driver"
                    this.jdbcUrl = get<String>(named("db.url"))
                    this.username = get<String>(named("db.username"))
                    this.password = get<String>(named("db.password"))
                    this.minimumIdle = 20
                    this.maximumPoolSize = 150
                })
            }

            single {
                Database.connect(
                    datasource = get<HikariDataSource>(),
                    databaseConfig = DatabaseConfig {
                        defaultSchema = Schema("lessonarchiver")
                    }
                )
            }

            single<B2StorageClient> { B2StorageClientFactory.createDefaultFactory().create(get<String>(named("b2.applicationKeyId")), get<String>(named("b2.applicationKey")), "lessonarchiver-api") }
        })
    }
}
