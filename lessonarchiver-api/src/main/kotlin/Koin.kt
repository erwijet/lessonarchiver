package com.lessonarchiver

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.json.jackson.JacksonJsonpMapper
import co.elastic.clients.transport.ElasticsearchTransport
import co.elastic.clients.transport.rest_client.RestClientTransport
import com.backblaze.b2.client.B2StorageClient
import com.backblaze.b2.client.B2StorageClientFactory
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.auth.AuthScheme
import io.ktor.server.application.*
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.nio.conn.SchemeIOSessionStrategy
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.Schema
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ksp.generated.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()

        modules(
            module {
                single<String>(named("mode")) { environment.config.property("mode").getString() }
                single<String>(named("db.url")) { environment.config.property("db.url").getString() }
                single<String>(named("db.username")) { environment.config.property("db.username").getString() }
                single<String>(named("db.password")) { environment.config.property("db.password").getString() }
                single<String>(named("b2.bucketId")) { environment.config.property("b2.bucketId").getString() }
                single<String>(named("b2.applicationKeyId")) { environment.config.property("b2.applicationKeyId").getString() }
                single<String>(named("b2.applicationKey")) { environment.config.property("b2.applicationKey").getString() }
                single<String>(named("es.host")) { environment.config.property("es.host").getString() }
                single<Int>(named("es.port")) {
                    environment.config
                        .property("es.port")
                        .getString()
                        .toInt()
                }
                single<String>(named("es.scheme")) { environment.config.property("es.scheme").getString() }
                single<String>(named("es.username")) { environment.config.property("es.username").getString() }
                single<String>(named("es.password")) { environment.config.property("es.password").getString() }

                single {
                    Notary(
                        client = "lessonarchiver",
                        url = environment.config.property("notary.url").getString(),
                        callback = environment.config.property("notary.callback").getString(),
                        key = environment.config.property("notary.key").getString(),
                    )
                }

                single {
                    HikariDataSource(
                        HikariConfig().apply {
                            this.driverClassName = "org.postgresql.Driver"
                            this.jdbcUrl = get<String>(named("db.url"))
                            this.username = get<String>(named("db.username"))
                            this.password = get<String>(named("db.password"))
                            this.minimumIdle = 20
                            this.maximumPoolSize = 150
                        },
                    )
                }

                single {
                    Database.connect(
                        datasource = get<HikariDataSource>(),
                        databaseConfig =
                            DatabaseConfig {
                                defaultSchema = Schema("lessonarchiver")
                            },
                    )
                }

                single<B2StorageClient> {
                    B2StorageClientFactory.createDefaultFactory().create(
                        get<String>(named("b2.applicationKeyId")),
                        get<String>(named("b2.applicationKey")),
                        "lessonarchiver-api",
                    )
                }

                single<BasicCredentialsProvider> {
                    BasicCredentialsProvider().also {
                        it.setCredentials(
                            AuthScope.ANY,
                            UsernamePasswordCredentials(get<String>(named("es.username")), get<String>(named("es.password"))),
                        )
                    }
                }

                single<RestClient> {
                    RestClient
                        .builder(
                            HttpHost(get<String>(named("es.host")), get<Int>(named("es.port")), get<String>(named("es.scheme"))),
                        ).setHttpClientConfigCallback {
                            it.setDefaultCredentialsProvider(get<BasicCredentialsProvider>())
                        }.build()
                }

                single<ObjectMapper> {
                    ObjectMapper().findAndRegisterModules().configure(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        false,
                    )
                }

                single<ElasticsearchTransport> {
                    RestClientTransport(get<RestClient>(), JacksonJsonpMapper(get()))
                }

                single<ElasticsearchClient> {
                    ElasticsearchClient(get<ElasticsearchTransport>())
                }
            },
            AppModule().module,
        )
    }
}

@Module
@ComponentScan("com.lessonarchiver")
class AppModule
