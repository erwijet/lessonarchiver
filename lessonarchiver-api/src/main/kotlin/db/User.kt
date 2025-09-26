package com.lessonarchiver.db

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

object UserTable : UUIDTable("lessonarchiver.users") {
    val notaryId = varchar("notary_id", 50)
}

class UserDAO(
    id: EntityID<UUID>,
) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserDAO>(UserTable) {
        fun findOrCreateByNotaryId(notaryId: String): UserDAO =
            transaction {
                find { UserTable.notaryId eq notaryId }.firstOrNull() ?: new { this.notaryId = notaryId }
            }
    }

    var notaryId by UserTable.notaryId
    val files by FileDAO referrersOn FileTable.ownerId orderBy FileTable.uploadedAt
}
