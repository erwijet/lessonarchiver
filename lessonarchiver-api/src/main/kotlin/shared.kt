package com.lessonarchiver

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityChangeType
import org.jetbrains.exposed.dao.EntityHook
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.and

fun <T  : Entity<*>> T.onUpdate(block: T.() -> Unit) {
    EntityHook.subscribe { action ->
        if (action.changeType == EntityChangeType.Updated) {
            block()
        }
    }
}

fun <T> whereNotNull(t: T?, block: (T) -> Op<Boolean>): Op<Boolean> = t?.let { block(it) } ?: Op.TRUE