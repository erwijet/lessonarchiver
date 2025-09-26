package com.lessonarchiver

import com.backblaze.b2.client.B2StorageClient
import com.backblaze.b2.client.contentSources.B2FileContentSource
import com.backblaze.b2.client.structures.B2UploadFileRequest
import com.lessonarchiver.db.FileDAO
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
import com.lessonarchiver.response.toResponse
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.fullJoin
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.unionAll
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.util.UUID

fun Application.configureRouting() {
    val b2: B2StorageClient by inject()
    val notary: Notary by inject()
    val bucketId: String by inject(named("b2.bucketId"))

    routing {
        get("/") {
            call.respondText("ok")
        }

        get("/auth/google") {
            val callback = when(call.parameters["env"]) {
                "local" -> "http://localhost:3000/token"
                else -> "https://app.lessonarchiver.com/token"
            }

            call.respond(notary.authenticate(via = Notary.Provider.GOOGLE, callback))
        }

        get("/auth/renew") {
            val token = call.request.headers["Authorization"]?.removePrefix("Bearer ") ?: return@get call.respond(
                HttpStatusCode.Unauthorized)

            notary.renew(token).let {
                when(it) {
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
                                    val meta = mapOf("originalName" to originalName, "owner" to call.user().dao.id.value.toString())

                                    val uploaded = withContext(Dispatchers.IO) {
                                        B2UploadFileRequest
                                            .builder(bucketId, remoteName, contentType, source)
                                            .setCustomFields(meta)
                                            .build()
                                            .let { b2.uploadSmallFile(it) }
                                    }

                                    logger.info("Uploaded file $remoteName to B2: $uploaded")
                                    results += transaction {
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

                if (results.isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, "No files uploaded")
                }

                call.respond(HttpStatusCode.OK, results.map { it.toResponse() }.toList())
            }

            @Serializable
            data class CreateNoteParams(val title: String, val body: String, val tags: List<String>)

            post("/note") {
                val params = call.receive<CreateNoteParams>()
                val tags = params.tags.mapOrNull(TagDAO::findById) ?: return@post call.respond(HttpStatusCode.BadRequest)

                val dao = transaction {
                    NoteDAO.new {
                        this.title = params.title
                        this.body = params.body
                        this.tags = SizedCollection(tags)
                    }
                }

                return@post call.respond(dao.toResponse())
            }

            @Serializable
            data class CreateTagParams(val name: String, val parent: String? = null)

            post("/tag") {
                val params = call.receive<CreateTagParams>()
                val parent = params.parent?.let { transaction { TagDAO.findById(it) } ?: return@post call.respond(HttpStatusCode.BadRequest) }

                val dao = runCatching { transaction {
                    TagDAO.new {
                        this.name = params.name
                        this.parent = parent
                        this.owner = call.user().dao
                    }
                } }.getOrNull() ?: return@post call.respond(HttpStatusCode.Conflict)

                call.respond(dao.toResponse())
            }

            delete("/tag/{tagId}") {
                val tag = transaction { TagDAO.find { TagTable.id eq call.parameters["tagId"]?.toUUID() and (TagTable.owner eq call.user().dao.id) } }.singleOrNull() ?: return@delete call.respond(HttpStatusCode.NotFound)
                call.respond(transaction { tag.toResponse().also { tag.delete() } })
            }

            get("/documents") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.takeIf { it in 0 .. 20 } ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid limit")
                val offset = call.request.queryParameters["offset"]?.toLongOrNull()?.takeIf { it >= 0 } ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid offset")
                val pinned = call.request.queryParameters["pinned"]?.toBooleanStrictOrNull()

                val documents = transaction {
                    FileTable.fullJoin(FilePinTable).select(FileTable.id, FileTable.uploadedAt, FileTable.ownerId, FilePinTable.id).where(FileTable.ownerId eq call.user().dao.id and whereNotNull(pinned) { if (it) FilePinTable.id neq null else FilePinTable.id eq null })
                        .unionAll(NoteTable.fullJoin(NotePinTable).select(NoteTable.id, NoteTable.updatedAt, NoteTable.ownerId).where(FileTable.ownerId eq call.user().dao.id and whereNotNull(pinned) { if (it) NotePinTable.id neq null else NotePinTable.id eq null }))
                        .orderBy(FileTable.uploadedAt to SortOrder.DESC)
                        .offset(offset)
                        .limit(limit)
                        .mapNotNull {
                            runCatching { FileDAO.findById(it[FileTable.id].value)?.toResponse() }.getOrNull() ?: NoteDAO.findById(it[FileTable.id].value)?.toResponse()
                        }
                }

                call.respond(documents)
            }

            get("/tags") {
                call.respond(transaction { TagDAO.find { TagTable.owner eq call.user().dao.id }.map { it.toResponse() } })
            }

            get("/file/{fileId}") {
                val fileId = call.parameters["fileId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid fileId")
                val b2documentId = transaction { FileDAO.findById(UUID.fromString(fileId))?.takeIf { it.owner.id == call.user().dao.id }?.fileId } ?: return@get call.respond(HttpStatusCode.NotFound, "File not found")

                val info = b2.getFileInfo(b2documentId)

                call.respondOutputStream(ContentType.parse(info.contentType ?: "application/octet-stream"), HttpStatusCode.OK) {
                    b2.downloadById(b2documentId) { _, stream ->
                        stream.copyTo(this@respondOutputStream)
                    }
                }
            }
        }
    }
}

fun RoutingCall.logger() = LoggerFactory.getLogger(this.request.path())