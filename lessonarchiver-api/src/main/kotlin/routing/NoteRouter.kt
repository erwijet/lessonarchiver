package com.lessonarchiver.routing

import com.lessonarchiver.badRequest
import com.lessonarchiver.db.NoteDAO
import com.lessonarchiver.db.TagDAO
import com.lessonarchiver.db.mapOrNull
import com.lessonarchiver.notFound
import com.lessonarchiver.param
import com.lessonarchiver.response.toResponse
import com.lessonarchiver.svc.NoteIndexService
import com.lessonarchiver.svc.toIndexed
import com.lessonarchiver.user
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.ktor.ext.inject
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Route.noteRouter() {
    @Serializable
    data class CreateNoteParams(
        val title: String,
        val body: String,
        val tags: List<@Contextual Uuid>,
    )

    @Serializable
    data class UpdateNoteParams(
        val title: String,
        val body: String,
        val tags: List<@Contextual Uuid>,
    )

    val noteIdx: NoteIndexService by inject()

    post("/") {
        val params = call.receive<CreateNoteParams>()
        val tags =
            transaction { params.tags.mapOrNull { TagDAO.withOwner(call.user()).findById(it) } }
                ?: return@post badRequest()

        val dao =
            transaction {
                NoteDAO.new {
                    this.title = params.title
                    this.body = params.body
                    this.owner = call.user().dao
                    this.tags = SizedCollection(tags)
                }
            }

        noteIdx.upsert(dao.toIndexed())
        return@post call.respond(dao.toResponse())
    }

    put("/{noteId}") {
        val id = param("noteId")
        val params = call.receive<UpdateNoteParams>()
        val tags =
            params.tags.mapOrNull { transaction { TagDAO.withOwner(call.user()).findById(it) } }
                ?: return@put call.respond(HttpStatusCode.BadRequest)

        val dao = transaction { NoteDAO.withOwner(call.user()).findById(id) } ?: return@put notFound()

        transaction {
            dao.title = params.title
            dao.body = params.body
            dao.tags = SizedCollection(tags)
        }

        noteIdx.upsert(dao.toIndexed())
        return@put call.respond(dao.toResponse())
    }

    delete("/{noteId}") {
        val noteId = param("noteId")
        val dao =
            transaction { NoteDAO.withOwner(call.user()).findById(noteId) }
                ?: return@delete notFound()

        transaction {
            noteIdx.delete(dao.id.value, dao.owner.id.value)
            dao.delete()
        }

        call.respond(HttpStatusCode.OK)
    }
}
