package com.lessonarchiver

import com.backblaze.b2.client.B2StorageClient
import com.lessonarchiver.db.CabinetDAO
import com.lessonarchiver.db.FileDAO
import com.lessonarchiver.db.FileTable
import com.lessonarchiver.db.NoteDAO
import com.lessonarchiver.db.NoteTable
import com.lessonarchiver.db.TagDAO
import com.lessonarchiver.db.mapOrNull
import com.lessonarchiver.response.MaterialsSearchResponse
import com.lessonarchiver.response.toDAO
import com.lessonarchiver.response.toResponse
import com.lessonarchiver.routing.cabinetRouter
import com.lessonarchiver.routing.fileRouter
import com.lessonarchiver.routing.noteRouter
import com.lessonarchiver.routing.tagRouter
import com.lessonarchiver.svc.FileIndexService
import com.lessonarchiver.svc.NoteIndexService
import com.lessonarchiver.svc.toIndexed
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.unionAll
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Application.configureRouting() {
    val notary: Notary by inject()
    val fileIdx: FileIndexService by inject()
    val noteIdx: NoteIndexService by inject()

    routing {
        get("/") {
            call.respondText("ok")
        }

        get("/auth/google") {
            val callback =
                when (call.parameters["env"]) {
                    "local" -> "http://localhost:3000/token"
                    else -> "https://app.lessonarchiver.com/token"
                }

            call.respond(notary.authenticate(via = Notary.Provider.GOOGLE, callback))
        }

        get("/auth/renew") {
            val token =
                call.request.headers["Authorization"]?.removePrefix("Bearer ") ?: return@get call.respond(HttpStatusCode.Unauthorized)

            notary.renew(token).let {
                when (it) {
                    is Notary.Renewal.Success -> call.respond(mapOf("token" to it.token))
                    is Notary.Renewal.Failure -> call.respond(HttpStatusCode.BadRequest, it.reason)
                }
            }
        }

        route("/file") { fileRouter() }

        authenticate {
            route("/tag") { tagRouter() }
            route("/note") { noteRouter() }
            route("/cabinet") { cabinetRouter() }

            get("/materials/search") {
                val query = queryParam("q")

                val files =
                    async(Dispatchers.IO) {
                        transaction {
                            fileIdx
                                .search(
                                    query,
                                    call
                                        .user()
                                        .dao.id.value,
                                ).mapNotNull { it.doc.toDAO()?.toResponse() }
                        }
                    }

                val notes =
                    async(Dispatchers.IO) {
                        transaction {
                            noteIdx
                                .search(
                                    query,
                                    call
                                        .user()
                                        .dao.id.value,
                                ).mapNotNull { it.doc.toDAO()?.toResponse() }
                        }
                    }

                call.respond(
                    MaterialsSearchResponse(
                        q = query,
                        files = files.await(),
                        notes = notes.await(),
                    ),
                )
            }

            get("/materials") {
                val limit = queryParam("limit") { toIntOrNull()?.takeIf { it in 0..20 } }
                val offset = queryParam("offset") { toLongOrNull()?.takeIf { it >= 0 } }
                val pinned = optionalQueryParam("pinned")?.toBooleanStrictOrNull() ?: false

                val materials =
                    transaction {
                        FileTable
                            .select(FileTable.id, FileTable.uploadedAt.alias("uploadedAt"), FileTable.ownerId, FileTable.pinned)
                            .where(
                                FileTable.ownerId eq call.user().dao.id and (FileTable.pinned eq pinned),
                            ).unionAll(
                                NoteTable
                                    .select(NoteTable.id, NoteTable.updatedAt.alias("uploadedAt"), NoteTable.ownerId, NoteTable.pinned)
                                    .where(
                                        NoteTable.ownerId eq call.user().dao.id and (NoteTable.pinned eq pinned),
                                    ),
                            ).orderBy(FileTable.uploadedAt.alias("uploadedAt") to SortOrder.DESC)
                            .offset(offset)
                            .limit(limit)
                            .mapNotNull {
                                runCatching { FileDAO.findById(it[FileTable.id].value)?.toResponse() }.getOrNull()
                                    ?: NoteDAO.findById(it[FileTable.id].value)?.toResponse()
                            }
                    }

                call.respond(materials)
            }
        }
    }
}

fun RoutingCall.logger() = LoggerFactory.getLogger(this.request.path())

suspend fun RoutingContext.badRequest(message: String? = null) = this.call.respond(HttpStatusCode.BadRequest, message ?: "Bad Request")

suspend fun RoutingContext.notFound(resource: String? = null) =
    this.call.respond(
        HttpStatusCode.NotFound,
        resource?.let { "$it not found" } ?: "Not Found",
    )

suspend fun RoutingContext.conflict() = this.call.respond(HttpStatusCode.Conflict, "Conflict")

class ErrDsl {
    fun notFound(resource: String? = null) = NotFoundException(resource?.let { "$it not found" } ?: "Not Found")
}

fun RoutingContext.err(block: ErrDsl.() -> Throwable) = with(ErrDsl()) { block() }

fun RoutingContext.param(k: String): String = this.call.parameters[k] ?: throw BadRequestException("Missing parameter: '$k'")

fun RoutingContext.queryParam(k: String): String =
    this.call.queryParameters[k] ?: throw BadRequestException("Missing or invalid query parameter: '$k'")

fun <T> RoutingContext.queryParam(
    k: String,
    map: (String.() -> T?),
) = this.call.queryParameters[k]?.let { param -> with(param) { map() } }
    ?: throw BadRequestException("Missing or invalid query parameter: '$k'")

fun RoutingContext.optionalQueryParam(k: String) = this.call.parameters[k]
