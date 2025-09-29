package com.lessonarchiver.db

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

object Registrar {
    val tables = listOf(UserTable, FileTable, NoteTable, TagTable, TagToFileTable, TagToNoteTable, NotePinTable, FilePinTable)
}

fun <T, R> Iterable<T>.mapOrNull(f: (T) -> R?): List<R>? {
    val mapped = mapNotNull(f)
    return if (mapped.count() == this.count()) mapped else null
}

fun <T : UUIDEntity> UUIDEntityClass<T>.findById(uuidStr: String) = uuidStr.toUUID()?.let { this.findById(it) }

@OptIn(ExperimentalUuidApi::class)
fun <T : UUIDEntity> UUIDEntityClass<T>.findById(id: Uuid) = findById(id.toJavaUuid())

fun String.toUUID() = runCatching { UUID.fromString(this) }.getOrNull()
