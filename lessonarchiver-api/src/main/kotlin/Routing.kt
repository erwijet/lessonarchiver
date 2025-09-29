package com.lessonarchiver

import com.backblaze.b2.client.B2StorageClient
import com.backblaze.b2.client.contentSources.B2FileContentSource
import com.backblaze.b2.client.structures.B2UploadFileRequest
import com.lessonarchiver.db.FileDAO
import com.lessonarchiver.db.FileGrantDAO
import com.lessonarchiver.db.FilePinTable
import com.lessonarchiver.db.FileTable
import com.lessonarchiver.db.NoteDAO
import com.lessonarchiver.db.NotePinTable
import com.lessonarchiver.db.NoteTable
import com.lessonarchiver.db.TagDAO
import com.lessonarchiver.db.TagTable
import com.lessonarchiver.db.findById
import com.lessonarchiver.db.mapOrNull
import com.lessonarchiver.db.toUUID
import com.lessonarchiver.response.MaterialsSearchResponse
import com.lessonarchiver.response.toDAO
import com.lessonarchiver.response.toResponse
import com.lessonarchiver.svc.FileIndexService
import com.lessonarchiver.svc.NoteIndexService
import com.lessonarchiver.svc.Scored
import com.lessonarchiver.svc.toIndexed
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.files
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.unionAll
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

@OptIn(ExperimentalUuidApi::class)
fun Application.configureRouting() {
    val b2: B2StorageClient by inject()
    val notary: Notary by inject()
    val bucketId: String by inject(named("b2.bucketId"))
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

        authenticate {
            post("/upload") {
                val logger = call.logger()
                val multipart = runCatching { call.receiveMultipart() }.getOrNull() ?: return@post call.respond(HttpStatusCode.BadRequest)
                val results = mutableListOf<FileDAO>()
                try {
                    multipart.forEachPart { part ->
                        when (part) {
                            is PartData.FileItem -> {
                                val originalName = (part.originalFileName ?: "upload").substringAfterLast("/").substringAfterLast("\\")
                                val remoteName = "uploads/${UUID.randomUUID()}-$originalName"
                                val tmp = File.createTempFile("upload-", "-$originalName")

                                try {
                                    withContext(Dispatchers.IO) {
                                        part.provider().toInputStream().use { input ->
                                            Files.newOutputStream(tmp.toPath()).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    }

                                    val contentType = (part.contentType ?: ContentType.Application.OctetStream).toString()
                                    val source = B2FileContentSource.builder(tmp).build()
                                    val meta =
                                        mapOf(
                                            "originalName" to originalName,
                                            "owner" to
                                                call
                                                    .user()
                                                    .dao.id.value
                                                    .toString(),
                                        )

                                    val uploaded =
                                        withContext(Dispatchers.IO) {
                                            B2UploadFileRequest
                                                .builder(bucketId, remoteName, contentType, source)
                                                .setCustomFields(meta)
                                                .build()
                                                .let { b2.uploadSmallFile(it) }
                                        }

                                    logger.info("Uploaded file $remoteName to B2: $uploaded")
                                    results +=
                                        transaction {
                                            FileDAO.new {
                                                this.owner = call.user().dao
                                                this.fileId = uploaded.fileId
                                                this.fileName = uploaded.fileName
                                                this.contentLength = uploaded.contentLength
                                                this.sha1 = uploaded.contentSha1
                                            }
                                        }
                                } finally {
                                    runCatching { Files.deleteIfExists(tmp.toPath()) }
                                }
                            }
                            else -> {
                                logger.warn("Unsupported part type: ${part::class.simpleName}, ignoring")
                            }
                        }
                        part.dispose()
                    }
                } catch (e: Throwable) {
                    logger.error("Failed to upload file", e)
                    return@post call.respond(HttpStatusCode.InternalServerError, "Upload failed: ${e.message}")
                }

                results
                    .map { each ->
                        async(Dispatchers.IO) {
                            fileIdx.upsert(each.toIndexed())
                        }
                    }.awaitAll()

                if (results.isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, "No files uploaded")
                }

                call.respond(HttpStatusCode.OK, results.map { it.toResponse() }.toList())
            }

            @Serializable
            data class CreateNoteParams(
                val title: String,
                val body: String,
                val tags: List<@Contextual Uuid>,
            )

            post("/note") {
                val params = call.receive<CreateNoteParams>()
                val tags =
                    transaction { params.tags.mapOrNull { TagDAO.findById(it.toJavaUuid()) } }
                        ?: return@post call.respond(HttpStatusCode.BadRequest)

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

            @Serializable
            data class UpdateNoteParams(
                @Contextual val id: UUID,
                val title: String,
                val body: String,
                val tags: List<@Contextual UUID>,
            )

            put("/note") {
                val params = call.receive<UpdateNoteParams>()
                val tags =
                    params.tags.mapOrNull { transaction { TagDAO.findById(it) } } ?: return@put call.respond(HttpStatusCode.BadRequest)

                val dao =
                    transaction {
                        NoteDAO.findById(params.id)?.takeIf { it.owner == call.user().dao }
                    } ?: return@put call.respond(HttpStatusCode.NotFound)

                transaction {
                    dao.title = params.title
                    dao.body = params.body
                    dao.tags = SizedCollection(tags)
                }

                noteIdx.upsert(dao.toIndexed())
                return@put call.respond(dao.toResponse())
            }

            delete("/note/{noteId}") {
                val noteId = call.parameters["noteId"]?.toUUID() ?: return@delete call.respond(HttpStatusCode.BadRequest)
                val dao =
                    transaction { NoteDAO.findById(noteId)?.takeIf { it.owner == call.user().dao } }
                        ?: return@delete call.respond(HttpStatusCode.NotFound)

                transaction {
                    noteIdx.delete(dao.id.value, dao.owner.id.value)
                    dao.delete()
                }

                call.respond(HttpStatusCode.OK)
            }

            @Serializable
            data class CreateTagParams(
                val name: String,
                val parent: String? = null,
            )

            post("/tag") {
                val params = call.receive<CreateTagParams>()
                val parent =
                    params.parent?.let {
                        transaction {
                            TagDAO.findById(
                                it,
                            )
                        } ?: return@post call.respond(HttpStatusCode.BadRequest)
                    }

                val dao =
                    runCatching {
                        transaction {
                            TagDAO.new {
                                this.name = params.name
                                this.parent = parent
                                this.owner = call.user().dao
                            }
                        }
                    }.getOrNull() ?: return@post call.respond(HttpStatusCode.Conflict)

                call.respond(dao.toResponse())
            }

            delete("/tag/{tagId}") {
                val tag =
                    transaction {
                        TagDAO.find { TagTable.id eq call.parameters["tagId"]?.toUUID() and (TagTable.owner eq call.user().dao.id) }
                    }.singleOrNull()
                        ?: return@delete call.respond(HttpStatusCode.NotFound)
                call.respond(transaction { tag.toResponse().also { tag.delete() } })
            }

            post("/grant/{fileId}") {
                val fileId = call.parameters["fileId"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing or invalid fileId")
                val file =
                    transaction {
                        FileDAO.findById(fileId)?.takeIf { it.owner.id == call.user().dao.id }
                        FileDAO.findById(UUID.fromString(fileId))?.takeIf { it.owner.id == call.user().dao.id }
                    } ?: return@post call.respond(HttpStatusCode.NotFound, "File not found")

                val grant =
                    transaction {
                        FileGrantDAO.new {
                            this.file = file
                            this.user = call.user().dao
                        }
                    }

                call.respond(grant.toResponse())
            }

            get("/materials/search") {
                val query =
                    call.request.queryParameters["q"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid query")

                val files = async(Dispatchers.IO) { fileIdx.search(call.user().dao.id.value, query).mapNotNull { it.doc.toDAO()?.toResponse() } }
                val notes = async(Dispatchers.IO) { noteIdx.search(call.user().dao.id.value, query).mapNotNull { it.doc.toDAO()?.toResponse() } }

                call.respond(MaterialsSearchResponse(
                    q = query,
                    files = files.await(),
                    notes = notes.await(),
                ))
            }

            get("/materials") {
                val limit =
                    call.request.queryParameters["limit"]
                        ?.toIntOrNull()
                        ?.takeIf { it in 0..20 }
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid limit")
                val offset =
                    call.request.queryParameters["offset"]
                        ?.toLongOrNull()
                        ?.takeIf { it >= 0 }
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid offset")
                val pinned = call.request.queryParameters["pinned"]?.toBooleanStrictOrNull()

                val documents =
                    transaction {
                        FileTable
                            .fullJoin(FilePinTable)
                            .select(FileTable.id, FileTable.uploadedAt, FileTable.ownerId, FilePinTable.id)
                            .where(
                                FileTable.ownerId eq call.user().dao.id and
                                    { pinned whenNotNull { if (it) FilePinTable.id neq null else FilePinTable.id eq null } },
                            ).unionAll(
                                NoteTable.fullJoin(NotePinTable).select(NoteTable.id, NoteTable.updatedAt, NoteTable.ownerId).where(
                                    FileTable.ownerId eq call.user().dao.id and
                                        { pinned whenNotNull { if (it) NotePinTable.id neq null else FilePinTable.id eq null } },
                                ),
                            ).orderBy(FileTable.uploadedAt to SortOrder.DESC)
                            .offset(offset)
                            .limit(limit)
                            .mapNotNull {
                                runCatching { FileDAO.findById(it[FileTable.id].value)?.toResponse() }.getOrNull()
                                    ?: NoteDAO.findById(it[FileTable.id].value)?.toResponse()
                            }
                    }

                call.respond(documents)
            }

            get("/tags") {
                call.respond(transaction { TagDAO.find { TagTable.owner eq call.user().dao.id }.map { it.toResponse() } })
            }

            get("/file/{fileGrantId}") {
                val fileGrantId =
                    call.parameters["fileGrantId"]?.toUUID()
                        ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid grant")
                val grant =
                    transaction { FileGrantDAO.findById(fileGrantId)?.takeIf { it.user == call.user().dao } }
                        ?: return@get call.respond(HttpStatusCode.NotFound, "Grant not found")

                val b2file = b2.getFileInfo(grant.file.fileId)

                call.respondOutputStream(ContentType.parse(b2file.contentType ?: "application/octet-stream"), HttpStatusCode.OK) {
                    b2.downloadById(grant.file.fileId) { _, stream ->
                        stream.copyTo(this@respondOutputStream)
                    }
                }
            }
        }
    }
}

fun RoutingCall.logger() = LoggerFactory.getLogger(this.request.path())
