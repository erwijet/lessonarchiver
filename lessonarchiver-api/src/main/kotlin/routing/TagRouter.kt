package com.lessonarchiver.routing

import com.lessonarchiver.OpAndDsl.whenNotNull
import com.lessonarchiver.conflict
import com.lessonarchiver.db.TagDAO
import com.lessonarchiver.db.TagTable
import com.lessonarchiver.db.fn.ilike
import com.lessonarchiver.notFound
import com.lessonarchiver.optionalQueryParam
import com.lessonarchiver.param
import com.lessonarchiver.response.toResponse
import com.lessonarchiver.user
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class CreateTagParams(
    val name: String,
)

fun Route.tagRouter() {
    get("/") {
        val q = optionalQueryParam("q")

        val tags =
            transaction {
                TagDAO.withOwner(call.user()).find { q whenNotNull { TagTable.name ilike "%$it%" } }.map { it.toResponse() }
            }

        call.respond(tags)
    }

    post("/") {
        val params = call.receive<CreateTagParams>()

        val tag =
            runCatching {
                transaction {
                    TagDAO.withOwner(call.user()).new {
                        this.name = params.name
                    }
                }.toResponse()
            }.getOrNull() ?: return@post conflict()

        call.respond(tag)
    }

    delete("/{tagId}") {
        val id = param("tagId")

        val tag =
            transaction {
                TagDAO.withOwner(call.user()).findById(id)
            } ?: return@delete notFound()

        call.respond(transaction { tag.toResponse().also { tag.delete() } })
    }
}
