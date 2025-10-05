package com.lessonarchiver

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.lessonarchiver.svc.IndexService
import io.ktor.server.application.Application
import org.koin.ktor.ext.getKoin
import org.koin.ktor.ext.inject

fun Application.configureElastic() {
    val es: ElasticsearchClient by inject()
    val services = getKoin().getAll<IndexService<*>>()
    val log by logger()

    log.info("Registered ${services.count()} elasticsearch index services")

    services.forEach { svc ->
        if (!es.indices().exists { it.index(svc.index) }.value()) {
            log.info("Creating index '${svc.index}' (${svc::class.simpleName})")
            svc.createIndex()
        }
    }
}
