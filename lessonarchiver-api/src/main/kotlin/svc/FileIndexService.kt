package com.lessonarchiver.svc

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.analysis.Analyzer
import co.elastic.clients.elasticsearch.core.IndexRequest
import com.lessonarchiver.db.FileDAO
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.koin.core.annotation.Single
import java.util.UUID

@Serializable
data class IndexedFileDoc(
    @Contextual val id: UUID,
    @Contextual val ownerId: UUID,
    val fileName: String,
)

fun FileDAO.toIndexed() = IndexedFileDoc(id.value, owner.id.value, fileName)

@Single
class FileIndexService(
    private val es: ElasticsearchClient,
    override val index: String = "files",
) : IndexService {
    override fun createIndex() {
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

    fun upsert(doc: IndexedFileDoc) {
        es.index(
            IndexRequest
                .Builder<IndexedFileDoc>()
                .index(index)
                .id(doc.id.toString())
                .routing(rk(doc.ownerId))
                .document(doc)
                .build(),
        )
    }

    fun delete(
        id: UUID,
        ownerId: UUID,
    ) {
        es.delete { it.index(index).id(id.toString()).routing(rk(ownerId)) }
    }

    fun search(
        ownerId: UUID,
        q: String,
    ): List<Scored<IndexedFileDoc>> =
        es
            .search<IndexedFileDoc> { request ->
                request
                    .index(index)
                    .routing(rk(ownerId))
                    .query { qb -> qb.multiMatch { mm -> mm.query(q).fields("fileName^3") } }
            }.toScored()
}
