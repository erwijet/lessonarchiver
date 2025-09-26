package com.lessonarchiver.db

import com.lessonarchiver.onUpdate
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.EntityChangeType
import org.jetbrains.exposed.dao.EntityHook
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import java.util.UUID

object NoteTable : UUIDTable("lessonarchiver.notes") {
    val title = text("title")
    val body = text("body")
    val ownerId = reference("owner_id", UserTable.id)
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}

class NoteDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<NoteDAO>(NoteTable)

    var title by NoteTable.title
    var body by NoteTable.body
    var updatedAt by NoteTable.updatedAt
    var owner by UserDAO referencedOn NoteTable.ownerId

    var tags by TagDAO via TagToNoteTable
    val pin by NotePinDAO optionalBackReferencedOn NotePinTable.noteId

    init {
        onUpdate {
            this.updatedAt = Clock.System.now()
        }
    }
}
