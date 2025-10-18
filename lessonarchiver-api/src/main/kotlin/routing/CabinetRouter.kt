package com.lessonarchiver.routing

import com.lessonarchiver.conflict
import com.lessonarchiver.db.CabinetDAO
import com.lessonarchiver.db.CabinetMaterialDAO
import com.lessonarchiver.db.CabinetTable
import com.lessonarchiver.db.FileDAO
import com.lessonarchiver.db.NoteDAO
import com.lessonarchiver.err
import com.lessonarchiver.notFound
import com.lessonarchiver.param
import com.lessonarchiver.response.toResponse
import com.lessonarchiver.user
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Route.cabinetRouter() {
    get("/") {
        val cabinets =
            transaction {
                CabinetDAO.withOwner(call.user()).find { CabinetTable.parentId eq null }.map { it.toResponse() }
            }

        call.respond(cabinets)
    }

    get("/{cabinetId}") {
        val cabinetId = param("cabinetId")

        val dao = transaction { CabinetDAO.withOwner(call.user()).findById(cabinetId) } ?: throw err { notFound("Cabinet") }

        call.respond(dao.toResponse())
    }

    get("/{cabinetId}/children") {
        val cabinetId = param("cabinetId")

        val children =
            transaction {
                CabinetDAO
                    .withOwner(call.user())
                    .findById(cabinetId)
                    ?.children
                    ?.map { it.toResponse() }
                    ?: throw err { notFound("Cabinet") }
            }

        call.respond(children)
    }

    get("/{cabinetId}/materials") {
        val cabinetId = param("cabinetId")

        val materials =
            transaction {
                CabinetDAO
                    .withOwner(call.user())
                    .findById(cabinetId)
                    ?.materials
                    ?.map { it.toResponse() }
                    ?: throw err { notFound("Cabinet") }
            }

        call.respond(materials)
    }

    @Serializable
    data class CreateCabinetParams(
        @Contextual val parentId: Uuid? = null,
        val name: String,
        val description: String,
    )

    post("/") {
        val params = call.receive<CreateCabinetParams>()

        val dao =
            runCatching {
                transaction {
                    val parent = params.parentId?.let { CabinetDAO.withOwner(call.user()).findById(it) }

                    CabinetDAO.withOwner(call.user()).new {
                        this.name = params.name
                        this.description = params.description
                        this.parent = parent
                    }
                }
            }.getOrNull() ?: return@post conflict()

        call.respond(dao.toResponse())
    }

    @Serializable
    data class UpdateCabinetParams(
        val name: String,
        val description: String,
        val materials: List<@Contextual Uuid>,
    )

    put("/{cabinetId}") {
        val cabinetId = param("cabinetId")
        val params = call.receive<UpdateCabinetParams>()

        val dao =
            transaction {
                CabinetDAO.withOwner(call.user()).findById(cabinetId)?.also {
                    it.name = params.name
                    it.description = params.description

                    it.materials.onEach { m -> m.delete() }
                    params.materials.forEachIndexed { i, m ->
                        FileDAO.withOwner(call.user()).findById(m)?.let { file ->
                            CabinetMaterialDAO.new(it, i, file)
                        }

                        NoteDAO.withOwner(call.user()).findById(m)?.let { note ->
                            CabinetMaterialDAO.new(it, i, note)
                        }
                    }
                }
            } ?: throw err { notFound("Cabinet") }

        call.respond(dao.toResponse())
    }

    delete("/{cabinetId}") {
        val cabinetId = param("cabinetId")

        val dao =
            transaction { CabinetDAO.withOwner(call.user()).findById(cabinetId)?.also { it.delete() } } ?: throw err { notFound("Cabinet") }

        call.respond(dao.toResponse())
    }
}
