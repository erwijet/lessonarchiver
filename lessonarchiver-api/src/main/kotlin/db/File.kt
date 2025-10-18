package com.lessonarchiver.db

import com.lessonarchiver.UUIDEntityClassWithOwner
import com.lessonarchiver.UUIDEntityWithOwner
import com.lessonarchiver.UUIDTableWithOwner
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID

@ManagedTable
object FileTable : UUIDTableWithOwner("lessonarchiver.files") {
    val fileId = text("file_id")
    val fileName = text("file_name")
    val contentLength = long("content_length")
    val sha1 = varchar("sha1", 40).nullable()
    val uploadedAt = timestamp("uploaded_at").clientDefault { Clock.System.now() }
    val pinned = bool("pinned").default(false)
}

class FileDAO(
    id: EntityID<UUID>,
) : UUIDEntityWithOwner(id, FileTable) {
    companion object : UUIDEntityClassWithOwner<FileDAO>(FileTable)

    var fileId by FileTable.fileId
    var fileName by FileTable.fileName
    var contentLength by FileTable.contentLength
    var sha1 by FileTable.sha1
    val uploadedAt by FileTable.uploadedAt

    var tags by TagDAO via TagToFileTable
    var pinned by FileTable.pinned
}
