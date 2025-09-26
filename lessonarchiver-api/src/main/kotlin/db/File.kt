package com.lessonarchiver.db

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID

object FileTable : UUIDTable("lessonarchiver.files") {
    val fileId = text("file_id")
    val fileName = text("file_name")
    val contentLength = long("content_length")
    val sha1 = varchar("sha1", 40).nullable()
    val ownerId = reference("owner_id", UserTable.id)
    val uploadedAt = timestamp("uploaded_at").clientDefault { Clock.System.now() }
}

class FileDAO(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FileDAO>(FileTable)

    var fileId by FileTable.fileId
    var fileName by FileTable.fileName
    var contentLength by FileTable.contentLength
    var sha1 by FileTable.sha1
    var owner by UserDAO referencedOn FileTable.ownerId
    val uploadedAt by FileTable.uploadedAt

    var tags by TagDAO via TagToFileTable
    val pin by FilePinDAO optionalBackReferencedOn FilePinTable.fileId
}
