package com.lessonarchiver.db

import com.lessonarchiver.UUIDEntityClassWithOwner
import com.lessonarchiver.UUIDEntityWithOwner
import com.lessonarchiver.UUIDTableWithOwner
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.UUID

@ManagedTable
object TagTable : UUIDTableWithOwner("lessonarchiver.tags") {
    val name = varchar("name", 255)

    init {
        uniqueIndex(ownerId, name)
    }
}

class TagDAO(
    id: EntityID<UUID>,
) : UUIDEntityWithOwner(id, TagTable) {
    companion object : UUIDEntityClassWithOwner<TagDAO>(TagTable)

    var name by TagTable.name
    var files by FileDAO via TagToFileTable
    var notes by NoteDAO via TagToNoteTable
}

@ManagedTable
object TagToFileTable : UUIDTable("lessonarchiver.tag_to_file") {
    val tag = reference("tag_id", TagTable.id)
    val file = reference("file_id", FileTable.id)
}

@ManagedTable
object TagToNoteTable : UUIDTable("lessonarchiver.tag_to_note") {
    val tag = reference("tag_id", TagTable.id)
    val note = reference("note_id", NoteTable.id)
}
