package com.lessonarchiver.response

import com.lessonarchiver.db.CabinetMaterialDAO
import com.lessonarchiver.db.FileDAO
import com.lessonarchiver.db.NoteDAO
import com.lessonarchiver.db.toUUID
import com.lessonarchiver.svc.IndexedFileDoc
import com.lessonarchiver.svc.IndexedNoteDoc
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.LongAsStringSerializer
import kotlinx.serialization.json.JsonClassDiscriminator
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
sealed class MaterialResponse

@Serializable
@SerialName("file")
class FileResponse(
    val id: String,
    val fileName: String,
    @Serializable(with = LongAsStringSerializer::class)
    val contentLength: Long,
    val sha1: String?,
    val uploadedAt: Instant,
    val pinned: Boolean,
    val tags: List<TagResponse>,
) : MaterialResponse()

fun FileDAO.toResponse() =
    FileResponse(
        id = id.value.toString(),
        fileName,
        contentLength,
        sha1,
        uploadedAt,
        pinned,
        tags.map { it.toResponse() },
    )

fun IndexedFileDoc.toDAO() =
    let { doc ->
        transaction {
            FileDAO.findById(doc.id.toUUID()!!)
        }
    }

@Serializable
@SerialName("note")
class NoteResponse(
    val id: String,
    val title: String,
    val body: String,
    val updatedAt: Instant,
    val pinned: Boolean,
    val tags: List<TagResponse>,
) : MaterialResponse()

fun NoteDAO.toResponse() =
    let { dao ->
        transaction {
            NoteResponse(
                id = dao.id.value.toString(),
                title = dao.title,
                body = dao.body,
                updatedAt = dao.updatedAt,
                pinned = dao.pinned,
                tags = dao.tags.map { it.toResponse() },
            )
        }
    }

fun IndexedNoteDoc.toDAO() =
    let { doc ->
        transaction {
            NoteDAO.findById(doc.id)
        }
    }

fun CabinetMaterialDAO.toResponse() =
    let { dao ->
        dao.file?.toResponse() ?: dao.note?.toResponse() ?: error("No material found for id ${dao.id.value}")
    }
