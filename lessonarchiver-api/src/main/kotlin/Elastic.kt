package com.lessonarchiver

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.lessonarchiver.svc.IndexService
import io.ktor.server.application.Application
import io.ktor.server.application.log
import org.koin.ktor.ext.getKoin
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.koin

fun Application.configureElastic() {
    val es: ElasticsearchClient by inject()
    val services = getKoin().getAll<IndexService>()
    val log by logger()

    services.forEach { svc ->
        if (!es.indices().exists { it.index(svc.index) }.value()) {
            log.info("Creating index '${svc.index}' (${svc::class.simpleName})")
            svc.createIndex()
        }
    }
}
