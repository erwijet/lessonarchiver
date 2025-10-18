package com.lessonarchiver.db

import com.lessonarchiver.UUIDEntityClassWithOwner
import com.lessonarchiver.UUIDEntityWithOwner
import com.lessonarchiver.UUIDTableWithOwner
import com.lessonarchiver.onUpdate
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID

@ManagedTable
object NoteTable : UUIDTableWithOwner("lessonarchiver.notes") {
    val title = text("title")
    val body = text("body")
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
    val pinned = bool("pinned").default(false)
}

class NoteDAO(
    id: EntityID<UUID>,
) : UUIDEntityWithOwner(id, NoteTable) {
    companion object : UUIDEntityClassWithOwner<NoteDAO>(NoteTable)

    var title by NoteTable.title
    var body by NoteTable.body
    var updatedAt by NoteTable.updatedAt

    var tags by TagDAO via TagToNoteTable
    var pinned by NoteTable.pinned

    init {
        onUpdate {
            this.updatedAt = Clock.System.now()
        }
    }
}
