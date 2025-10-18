package com.lessonarchiver.response

import com.lessonarchiver.db.TagDAO
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class TagResponse(
    val id: String,
    val name: String,
)

fun TagDAO.toResponse() =
    transaction {
        TagResponse(this@toResponse.id.value.toString(), name)
    }
