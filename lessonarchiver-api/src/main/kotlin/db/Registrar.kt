package com.lessonarchiver.db

import io.github.classgraph.ClassGraph
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.sql.Table
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

annotation class ManagedTable {
    companion object {
        fun getAll() =
            ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan()
                .getClassesWithAnnotation(
                    ManagedTable::class.qualifiedName,
                ).filter {
                    it.isStandardClass || it.isAnnotation
                }.loadClasses()
                .mapNotNull { runCatching { it.kotlin.objectInstance as? Table }.getOrNull() }
    }
}

fun <T, R> Iterable<T>.mapOrNull(f: (T) -> R?): List<R>? {
    val mapped = mapNotNull(f)
    return if (mapped.count() == this.count()) mapped else null
}

fun <T : UUIDEntity> UUIDEntityClass<T>.findById(uuidStr: String) = uuidStr.toUUID()?.let { this.findById(it) }

@OptIn(ExperimentalUuidApi::class)
fun <T : UUIDEntity> UUIDEntityClass<T>.findById(id: Uuid) = findById(id.toJavaUuid())

fun String.toUUID() = runCatching { UUID.fromString(this) }.getOrNull()
