package com.lessonarchiver.svc

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.SearchResponse
import co.elastic.clients.elasticsearch.core.search.Hit
import java.util.UUID

data class Scored<T>(
    val doc: T,
    val score: Double,
)

fun <T : Any> Hit<T>.toScored() = this.source()?.let { Scored(it, this.score() ?: 0.0) }

fun <T : Any> SearchResponse<T>.toScored() = this.hits().hits().mapNotNull { it.toScored() }.sortedBy { it.score }

inline fun <reified T : Any> ElasticsearchClient.search(block: (SearchRequest.Builder) -> SearchRequest.Builder): SearchResponse<T> =
    this.search(block(SearchRequest.Builder()).build(), T::class.java)

interface IndexService {
    val index: String

    fun createIndex()

    fun rk(ownerId: UUID) = ownerId.toString()
}
