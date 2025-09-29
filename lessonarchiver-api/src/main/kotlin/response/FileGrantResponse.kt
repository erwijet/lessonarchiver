package com.lessonarchiver.response

import com.lessonarchiver.db.FileGrantDAO
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
class FileGrantResponse(
    val token: String,
    val expiresAt: Instant,
)

fun FileGrantDAO.toResponse() {
    FileGrantResponse(
        token = this.id.value.toString(),
        expiresAt = this.expiresAt,
    )
}
