package com.lessonarchiver.db

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

object NotePinTable : UUIDTable("lessonarchiver.note_pins") {
    val noteId = reference("note_id", NoteTable.id)
}

class NotePinDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<NotePinDAO>(NotePinTable)
}

object FilePinTable : UUIDTable("lessonarchiver.file_pins") {
    val fileId = reference("file_id", FileTable.id)
}

class FilePinDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FilePinDAO>(FilePinTable)
}
