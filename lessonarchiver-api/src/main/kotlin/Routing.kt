package com.lessonarchiver

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.backblaze.b2.client.B2StorageClient
import com.backblaze.b2.client.contentHandlers.B2ContentSink
import com.backblaze.b2.client.contentSources.B2ContentSource
import com.backblaze.b2.client.contentSources.B2FileContentSource
import com.backblaze.b2.client.contentSources.B2Headers
import com.backblaze.b2.client.structures.B2UploadFileRequest
import com.lessonarchiver.db.FileDAO
import com.lessonarchiver.db.FileTable.fileId
import com.lessonarchiver.response.FileResponse
import com.lessonarchiver.response.toResponse
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.http.content.file
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory
import org.slf4j.event.*
import java.io.File
import java.io.InputStream
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
                "local" -> "http://localhost:8080/token"
                else -> "https://app.lessonarchiver.com/token"
            }

            call.respond(mapOf("url" to notary.authenticate(via = Notary.Provider.GOOGLE, callback)))
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
                                            this.ownerId = call.user().dao
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

            get("/files") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.takeIf { it in 0 .. 20 } ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid limit")
                val offset = call.request.queryParameters["offset"]?.toLongOrNull()?.takeIf { it >= 0 } ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid offset")

                val files = transaction {
                    call.user().dao.files.limit(limit).offset(offset).map { it.toResponse() }
                }

                call.respond(files)
            }

            get("/file/{fileId}") {
                val fileId = call.parameters["fileId"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing or invalid fileId")
                val b2documentId = transaction { FileDAO.findById(UUID.fromString(fileId))?.takeIf { it.ownerId.id == call.user().dao.id }?.fileId } ?: return@get call.respond(HttpStatusCode.NotFound, "File not found")

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