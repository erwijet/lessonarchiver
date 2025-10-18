package com.lessonarchiver.routing

import com.backblaze.b2.client.B2StorageClient
import com.backblaze.b2.client.contentSources.B2FileContentSource
import com.backblaze.b2.client.structures.B2UploadFileRequest
import com.lessonarchiver.badRequest
import com.lessonarchiver.db.FileDAO
import com.lessonarchiver.db.FileGrantDAO
import com.lessonarchiver.db.TagDAO
import com.lessonarchiver.db.findById
import com.lessonarchiver.db.mapOrNull
import com.lessonarchiver.err
import com.lessonarchiver.logger
import com.lessonarchiver.notFound
import com.lessonarchiver.param
import com.lessonarchiver.response.toResponse
import com.lessonarchiver.svc.FileIndexService
import com.lessonarchiver.svc.toIndexed
import com.lessonarchiver.user
import io.ktor.http.*
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.jvm.javaio.toOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.transactions.experimental.suspendedTransactionAsync
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.qualifier.named
import org.koin.ktor.ext.inject
import java.io.File
import java.nio.file.Files
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun Route.fileRouter() {
    get("/grant/{fileGrantId}") {
        val fileGrantId = param("fileGrantId")

        val b2: B2StorageClient by inject()
        val grant = transaction { FileGrantDAO.findById(fileGrantId) } ?: return@get notFound("Grant")

        if (grant.isExpired) return@get badRequest("Grant is expired")
        val file = transaction { grant.file }
        val b2file = b2.getFileInfo(file.fileId)

        call.respondOutputStream(ContentType.parse(b2file.contentType ?: "application/octet-stream"), HttpStatusCode.OK) {
            b2.downloadById(file.fileId) { _, stream ->
                stream.copyTo(this@respondOutputStream)
            }
        }
    }

    authenticate {
        get("/{fileId}") {
            val id = param("fileId")
            val file =
                transaction {
                    FileDAO.withOwner(call.user()).findById(id)?.toResponse()
                } ?: throw err { notFound("File") }

            call.respond(file)
        }

        post("/upload") {
            val b2: B2StorageClient by inject()
            val bucketId: String by inject(named("b2.bucketId"))
            val fileIdx: FileIndexService by inject()

            val logger = call.logger()
            val multipart =
                runCatching { call.receiveMultipart(formFieldLimit = 1024 * 1024 * 100) }.getOrNull() ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                )
            val results = mutableListOf<FileDAO>()
            try {
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val originalName =
                                (part.originalFileName ?: "upload").substringAfterLast("/").substringAfterLast("\\")
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

            transaction {
                results.map { it.toIndexed() }
            }.let {
                it.map { each -> withContext(Dispatchers.IO) { fileIdx.upsert(each) } }
            }

            if (results.isEmpty()) {
                return@post badRequest("No files uploaded")
            }

            call.respond(
                transaction {
                    results.map { it.toResponse() }.toList()
                },
            )
        }

        @Serializable
        data class UpdateFileParams(
            val pinned: Boolean,
            val tags: List<@Contextual Uuid>,
        )

        put("/{fileId}") {
            val id = param("fileId")
            val params = call.receive<UpdateFileParams>()
            val file =
                transaction {
                    val tags =
                        params.tags.mapOrNull { transaction { TagDAO.withOwner(call.user()).findById(it) } }
                            ?: throw err { notFound("Tag") }

                    FileDAO
                        .withOwner(call.user())
                        .findById(id)
                        ?.also {
                            it.tags = SizedCollection(tags)
                            it.pinned = params.pinned
                        }?.toResponse()
                } ?: throw err { notFound("File") }

            call.respond(file)
        }

        post("/grant/{fileId}") {
            val fileId = param("fileId")

            val file =
                transaction {
                    FileDAO.withOwner(call.user()).findById(fileId)
                } ?: throw err { notFound("File") }

            val grant =
                transaction {
                    FileGrantDAO.withOwner(call.user()).new { this.file = file }
                }

            call.respond(grant.toResponse())
        }
    }
}
