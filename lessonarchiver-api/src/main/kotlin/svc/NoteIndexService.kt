package com.lessonarchiver.svc

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch._types.analysis.Analyzer
import co.elastic.clients.elasticsearch.core.IndexRequest
import com.lessonarchiver.db.NoteDAO
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.annotation.Single
import java.util.UUID

@Serializable
data class IndexedNoteDoc(
    @Contextual val id: UUID,
    @Contextual val ownerId: UUID,
    val title: String,
    val body: String,
)

fun NoteDAO.toIndexed() = let { dao -> transaction { IndexedNoteDoc(dao.id.value, dao.owner.id.value, dao.title, dao.body) } }

@Single
class NoteIndexService(
    private val es: ElasticsearchClient,
    override val index: String = "notes",
) : IndexService {
    fun upsert(doc: IndexedNoteDoc) {
        es.index(
            IndexRequest
                .Builder<IndexedNoteDoc>()
                .index(index)
                .id(doc.id.toString())
                .routing(rk(doc.ownerId))
                .document(doc)
                .build(),
        )
    }

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

    fun delete(
        id: UUID,
        ownerId: UUID,
    ) {
        es.delete { it.index(index).id(id.toString()).routing(rk(ownerId)) }
    }

    fun search(
        userId: UUID,
        q: String,
    ): List<Scored<IndexedNoteDoc>> =
        es
            .search<IndexedNoteDoc> { request ->
                request
                    .index(index)
                    .routing(rk(userId))
                    .query { qb -> qb.multiMatch { mm -> mm.query(q).fields("title^3", "body") } }
            }.toScored()
}
