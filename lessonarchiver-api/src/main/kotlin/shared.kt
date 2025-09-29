package com.lessonarchiver

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityChangeType
import org.jetbrains.exposed.dao.EntityHook
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.slf4j.LoggerFactory

fun <T : Entity<*>> T.onUpdate(block: T.() -> Unit) {
    EntityHook.subscribe { action ->
        if (action.changeType == EntityChangeType.Updated) {
            block()
        }
    }
}

infix fun Op<Boolean>.and(block: OpAndDsl.() -> Op<Boolean>) = this and with(OpAndDsl, block)

object OpAndDsl {
    infix fun <T> T?.whenNotNull(block: (T) -> Op<Boolean>) = this?.let { block(this) } ?: Op.TRUE
}

fun <T : Table> T.createdAt(name: String) = timestamp(name).clientDefault { Clock.System.now() }

inline fun <reified T : Any> T.logger() =
    lazy {
        LoggerFactory.getLogger(T::class.java.simpleName)
    }
