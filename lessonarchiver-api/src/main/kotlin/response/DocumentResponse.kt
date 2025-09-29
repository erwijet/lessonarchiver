package com.lessonarchiver.response

import co.elastic.clients.elasticsearch._types.SortOptionsBuilders.doc
import com.lessonarchiver.db.FileDAO
import com.lessonarchiver.db.NoteDAO
import com.lessonarchiver.db.findById
import com.lessonarchiver.svc.IndexedFileDoc
import com.lessonarchiver.svc.IndexedNoteDoc
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.LongAsStringSerializer
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
abstract class DocumentResponse(
    val type: String,
)

@Serializable
class FileResponse(
    val id: String,
    val fileName: String,
    @Serializable(with = LongAsStringSerializer::class)
    val contentLength: Long,
    val sha1: String?,
    val uploadedAt: Instant,
    val isPinned: Boolean,
) : DocumentResponse("file")

fun FileDAO.toResponse() =
    FileResponse(
        id = id.value.toString(),
        fileName,
        contentLength,
        sha1,
        uploadedAt,
        isPinned = pin != null,
    )

fun IndexedFileDoc.toDAO() = let { doc ->
    transaction {
        FileDAO.findById(doc.id)
    }
}

@Serializable
class NoteResponse(
    val id: String,
    val title: String,
    val body: String,
    val updatedAt: Instant,
    val isPinned: Boolean,
) : DocumentResponse("note")

fun NoteDAO.toResponse() =
    let { dao ->
        transaction {
            NoteResponse(
                id = dao.id.value.toString(),
                title = dao.title,
                body = dao.body,
                updatedAt = dao.updatedAt,
                isPinned = dao.pin != null,
            )
        }
    }

fun IndexedNoteDoc.toDAO() = let { doc ->
    transaction {
        NoteDAO.findById(doc.id)
    }
}