package com.lessonarchiver.response

import com.lessonarchiver.db.FileDAO
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.LongAsStringSerializer
import java.util.UUID

@Serializable
class FileResponse(
    val id: String,
    val fileName: String,
    @Serializable(with = LongAsStringSerializer::class)
    val contentLength: Long,
    val sha1: String?,
    val uploadedAt: Instant
)

fun FileDAO.toResponse() = FileResponse(
    id.value.toString(),
    fileName,
    contentLength,
    sha1,
    uploadedAt
)