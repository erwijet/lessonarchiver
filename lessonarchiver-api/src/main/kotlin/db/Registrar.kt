package com.lessonarchiver.db

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import java.util.UUID

object Registrar {
    val tables = listOf(UserTable, FileTable, NoteTable, TagTable, TagToFileTable, TagToNoteTable)
}

fun <T, R> Iterable<T>.mapOrNull(f: T.() -> R?): List<R>? {
    val mapped = mapNotNull(f)
    return if (mapped.count() == this.count()) mapped else null
}

fun <T, R> Iterable<T>.mapAborting(f: T.(() -> Unit) -> R) =
    {
        var aborted = false
        val mapped =
            map {
                if (aborted) return@map null
                with(it) { f { aborted = true } }
            }

        if (aborted) null else mapped
    }

fun <T : UUIDEntity> UUIDEntityClass<T>.findById(uuidLike: String) = uuidLike.toUUID()?.let { this.findById(it) }

fun String.toUUID() = runCatching { UUID.fromString(this) }.getOrNull()
