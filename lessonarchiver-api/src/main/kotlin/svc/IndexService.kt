package com.lessonarchiver.svc

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.analysis.Analyzer
import co.elastic.clients.elasticsearch.core.SearchRequest
import co.elastic.clients.elasticsearch.core.SearchResponse
import co.elastic.clients.elasticsearch.core.search.Hit
import org.koin.java.KoinJavaComponent.inject
import java.util.UUID

data class Scored<T>(
    val doc: T,
    val score: Double,
)

fun <T : Any> Hit<T>.toScored() = this.source()?.let { Scored(it, this.score() ?: 0.0) }

fun <T : Any> SearchResponse<T>.toScored() = this.hits().hits().mapNotNull { it.toScored() }.sortedBy { it.score }

inline fun <reified T : Any> ElasticsearchClient.search(block: (SearchRequest.Builder) -> SearchRequest.Builder): SearchResponse<T> =
    this.search(block(SearchRequest.Builder()).build(), T::class.java)

interface IndexService<TDoc> {
    val index: String

    fun createIndex() {
        val es: ElasticsearchClient by inject(ElasticsearchClient::class.java);

        es.indices().create {
            it.index(index).settings { s ->
                s.analysis { a ->
                    a.analyzer(
                        "${index}_analyzer",
                        Analyzer.of { az ->
                            az.custom { c -> c.tokenizer("standard").filter("lowercase") }
                        },
                    )
                }
                s.numberOfShards("1").numberOfReplicas("0")
            }
        }
    }

    fun rk(ownerId: UUID) = ownerId.toString()

    fun upsert(doc: TDoc)
    fun delete(id: UUID, ownerId: UUID)
    fun search(q: String, ownerId: UUID): List<Scored<TDoc>>
}
