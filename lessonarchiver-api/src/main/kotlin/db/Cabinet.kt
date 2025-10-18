package com.lessonarchiver.db

import com.lessonarchiver.UUIDEntityClassWithOwner
import com.lessonarchiver.UUIDEntityWithOwner
import com.lessonarchiver.UUIDTableWithOwner
import com.lessonarchiver.createdAt
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import java.util.UUID

@ManagedTable
object CabinetTable : UUIDTableWithOwner("lessonarchiver.cabinet") {
    val name = text("name")
    val description = text("description").nullable()

    val parentId = reference("parent_id", id, onDelete = ReferenceOption.CASCADE).nullable()

    val createdAt = createdAt("created_at")

    init {
        uniqueIndex(ownerId, name, parentId)
    }
}

class CabinetDAO(
    id: EntityID<UUID>,
) : UUIDEntityWithOwner(id, CabinetTable) {
    companion object : UUIDEntityClassWithOwner<CabinetDAO>(CabinetTable)

    var name by CabinetTable.name
    var description by CabinetTable.description

    var parent by CabinetDAO optionalReferencedOn CabinetTable.parentId
    val children by CabinetDAO optionalReferrersOn CabinetTable.parentId

    val materials by CabinetMaterialDAO referrersOn CabinetMaterialTable.cabinetId orderBy CabinetMaterialTable.order
}

@ManagedTable
object CabinetMaterialTable : UUIDTable("lessonarchiver.cabinet_materials") {
    val cabinetId = reference("cabinet_id", CabinetTable.id, onDelete = ReferenceOption.CASCADE)
    val order = integer("order")
    val fileId = reference("file_id", FileTable.id).nullable()
    val noteId = reference("note_id", NoteTable.id).nullable()
}

class CabinetMaterialDAO(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CabinetMaterialDAO>(CabinetMaterialTable) {
        fun new(
            cabinet: CabinetDAO,
            order: Int,
            file: FileDAO,
        ) = new {
            this.cabinet = cabinet
            this.order = order
            this.file = file
        }

        fun new(
            cabinet: CabinetDAO,
            order: Int,
            note: NoteDAO,
        ) = new {
            this.cabinet = cabinet
            this.order = order
            this.note = note
        }
    }

    var cabinet by CabinetDAO referencedOn CabinetMaterialTable.cabinetId
    var order by CabinetMaterialTable.order
    var file by FileDAO optionalReferencedOn CabinetMaterialTable.fileId
    var note by NoteDAO optionalReferencedOn CabinetMaterialTable.noteId
}
