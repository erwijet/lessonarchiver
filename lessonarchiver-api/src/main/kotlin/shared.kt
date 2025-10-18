package com.lessonarchiver

import com.lessonarchiver.db.UserDAO
import com.lessonarchiver.db.UserTable
import com.lessonarchiver.db.findById
import com.lessonarchiver.db.toUUID
import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.dao.EntityChangeType
import org.jetbrains.exposed.dao.EntityHook
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

fun <T : Entity<*>> T.onUpdate(block: T.() -> Unit) {
    EntityHook.subscribe { action ->
        if (action.changeType == EntityChangeType.Updated) {
            block()
        }
    }
}

open class UUIDTableWithOwner(
    name: String,
) : UUIDTable(name) {
    val ownerId = reference("owner_id", UserTable.id)
}

abstract class UUIDEntityWithOwner(
    id: EntityID<UUID>,
    tbl: UUIDTableWithOwner,
) : UUIDEntity(id) {
    var owner by UserDAO referencedOn tbl.ownerId
}

open class UUIDEntityClassWithOwner<TEntity : UUIDEntityWithOwner>(
    protected val tbl: UUIDTableWithOwner,
) : UUIDEntityClass<TEntity>(tbl) {
    class Impl<TEntity : UUIDEntityWithOwner>(
        private val owner: UserDAO,
        private val ec: UUIDEntityClassWithOwner<TEntity>,
    ) {
        fun new(init: TEntity.() -> Unit): TEntity =
            ec.new {
                init()
                this.owner = this@Impl.owner
            }

        fun find(op: SqlExpressionBuilder.() -> Op<Boolean>): SizedIterable<TEntity> = ec.find { op() and (ec.tbl.ownerId eq owner.id) }

        @OptIn(ExperimentalUuidApi::class)
        fun findById(id: Uuid) = findById(id.toJavaUuid())

        fun findById(id: String) = id.toUUID()?.let { findById(it) }

        fun findById(id: UUID) = ec.findById(id)?.takeIf { it.owner.id.value == owner.id.value }

        fun all() = find { Op.TRUE }
    }

    fun withOwner(u: UserPrincipal) = Impl(u.dao, this)
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
