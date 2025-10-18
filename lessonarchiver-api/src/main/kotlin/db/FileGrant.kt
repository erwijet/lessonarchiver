package com.lessonarchiver.db

import com.lessonarchiver.UUIDEntityClassWithOwner
import com.lessonarchiver.UUIDEntityWithOwner
import com.lessonarchiver.UUIDTableWithOwner
import com.lessonarchiver.createdAt
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

@ManagedTable
object FileGrantTable : UUIDTableWithOwner("lessonarchiver.file_grants") {
    val fileId = reference("file_id", FileTable.id)
    val createdAt = createdAt("created_at")
    val expiresAt = timestamp("expires_at").clientDefault { Clock.System.now() + 30.minutes }
}

class FileGrantDAO(
    id: EntityID<UUID>,
) : UUIDEntityWithOwner(id, FileGrantTable) {
    companion object : UUIDEntityClassWithOwner<FileGrantDAO>(FileGrantTable)

    var file by FileDAO referencedOn FileGrantTable.fileId

    val createdAt by FileGrantTable.createdAt
    val expiresAt by FileGrantTable.expiresAt

    val isExpired: Boolean
        get() = Clock.System.now() > expiresAt
}
