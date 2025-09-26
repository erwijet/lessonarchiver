package com.lessonarchiver.response

import com.lessonarchiver.db.TagDAO
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class TagResponse(
    val id: String,
    val name: String,
    val path: List<TagResponsePathPart>,
)

@Serializable
data class TagResponsePathPart(
    val id: String,
    val name: String,
)

fun TagDAO.toResponse() =
    transaction {
        val path = mutableListOf<TagResponsePathPart>()
        var parent: TagDAO? = this@toResponse

        while (parent != null) {
            path += TagResponsePathPart(parent.id.value.toString(), parent.name)
            parent = parent.parent
        }

        TagResponse(this@toResponse.id.value.toString(), name, path.reversed())
    }
