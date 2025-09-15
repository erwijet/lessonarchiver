package com.lessonarchiver

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import kotlinx.serialization.*
import io.ktor.client.request.request
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.parameters
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

class Notary(
    private val client: String,
    private val url: String,
    private val callback: String,
    private val key: String
) {
    private val http = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                explicitNulls = false
                allowStructuredMapKeys = true
                prettyPrint = false
                allowStructuredMapKeys = true
            })
        }

        expectSuccess = true
    }

    enum class Provider(val value: String) {
        GOOGLE("google")
    }

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    @JsonIgnoreUnknownKeys
    data class UserInfo(
        @SerialName("user_id") val userId: String,
        @SerialName("given_name") val givenName: String,
        @SerialName("family_name") val familyName: String,
        @SerialName("fullname") val fullName: String,
        val picture: String,
        val sub: String,
        val aud: String
    )

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    @JsonClassDiscriminator("valid")
    sealed class Inspection {
        abstract val valid: Boolean;

        @Serializable
        @SerialName("true")
        data class Pass(override val valid: Boolean, val claims: UserInfo) : Inspection()

        @Serializable
        @SerialName("false")
        data class Fail(override val valid: Boolean) : Inspection()
    }

    @Serializable
    data class Authentication(
        val url: String
    )

    @Serializable
    @OptIn(ExperimentalSerializationApi::class)
    @JsonClassDiscriminator("ok")
    sealed class Renewal {
        @Serializable
        @SerialName("true")
        data class Success(val token: String, val ok: Boolean) : Renewal()
        @Serializable
        @SerialName("false")
        data class Failure(val reason: String, val ok: Boolean) : Renewal()
    }

    suspend fun authenticate(via: Provider, callback: String? = null): Authentication =
        http.request(Url(url)) {
            url {
                appendPathSegments("authorize", client)
                parameters {
                    set("via", via.value)
                    set("key", key)
                    set("callback", callback ?: this@Notary.callback)
                }
            }
        }.body()

    suspend fun inspect(token: String): Inspection = http.request(Url(url)) {
        url {
            appendPathSegments("inspect", token)
        }
    }.body()

    suspend fun renew(token: String): Renewal = http.request(Url(url)) {
        url {
            appendPathSegments("renew", token)
        }
    }.body()
}