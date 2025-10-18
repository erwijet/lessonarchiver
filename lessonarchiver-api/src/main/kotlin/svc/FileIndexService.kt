package com.lessonarchiver.svc

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.IndexRequest
import com.lessonarchiver.db.FileDAO
import org.koin.core.annotation.Single
import java.util.UUID

data class IndexedFileDoc(
    val id: String,
    val ownerId: String,
    val fileName: String,
)

fun FileDAO.toIndexed() = IndexedFileDoc(id.value.toString(), owner.id.value.toString(), fileName)

@Single
class FileIndexService(
    private val es: ElasticsearchClient,
    override val index: String = "files",
) : IndexService<IndexedFileDoc> {
    override fun upsert(doc: IndexedFileDoc) {
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

    override fun delete(
        id: UUID,
        ownerId: UUID,
    ) {
        es.delete { it.index(index).id(id.toString()).routing(rk(ownerId.toString())) }
    }

    override fun search(
        q: String,
        ownerId: UUID,
    ): List<Scored<IndexedFileDoc>> =
        es
            .search<IndexedFileDoc> { request ->
                request
                    .index(index)
                    .routing(rk(ownerId.toString()))
                    .query { qb -> qb.multiMatch { mm -> mm.query(q).fields("fileName^3") } }
            }.toScored() as List<Scored<IndexedFileDoc>>
}
