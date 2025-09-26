package com.lessonarchiver.db

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.UUID

object TagTable : UUIDTable("lessonarchiver.tags") {
    val name = varchar("name", 255)
    val parent = reference("parent_id", id, onDelete = ReferenceOption.CASCADE).nullable()
    val owner = reference("owner_id", UserTable.id)

    init {
        uniqueIndex(owner, name, parent)
    }
}

class TagDAO(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TagDAO>(TagTable)

    var owner by UserDAO referencedOn TagTable.owner
    var name by TagTable.name
    var parent by TagDAO optionalReferencedOn TagTable.parent
    val children by TagDAO optionalReferrersOn TagTable.parent
    var files by FileDAO via TagToFileTable
    var notes by NoteDAO via TagToNoteTable
}

object TagToFileTable : UUIDTable("lessonarchiver.tag_to_file") {
    val tag = reference("tag_id", TagTable.id)
    val file = reference("file_id", FileTable.id)
}

object TagToNoteTable : UUIDTable("lessonarchiver.tag_to_note") {
    val tag = reference("tag_id", TagTable.id)
    val note = reference("note_id", NoteTable.id)
}