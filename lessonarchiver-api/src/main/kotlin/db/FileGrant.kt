package com.lessonarchiver.db

import com.lessonarchiver.createdAt
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

object FileGrantTable : UUIDTable("lessonarchiver.file_grants") {
    val fileId = reference("file_id", FileTable.id)
    val userId = reference("user_id", UserTable.id)
    val createdAt = createdAt("created_at")
    val expiresAt = timestamp("expires_at").clientDefault { Clock.System.now() + 30.minutes }
}

class FileGrantDAO(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FileGrantDAO>(FileGrantTable)

    var file by FileDAO referencedOn FileGrantTable.fileId
    var user by UserDAO referencedOn FileGrantTable.userId

    val createdAt by FileGrantTable.createdAt
    val expiresAt by FileGrantTable.expiresAt
}
