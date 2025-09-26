package com.lessonarchiver

import com.lessonarchiver.Notary.UserInfo
import com.lessonarchiver.db.UserDAO
import com.lessonarchiver.db.UserTable
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import org.koin.ktor.ext.inject

fun Application.configureAuthentication() {
    install(Authentication) {
        bearer {
            realm = "lesson-archiver"
            authenticate { credential ->
                val notary: Notary by inject()
                val inspection = notary.inspect(credential.token)

                return@authenticate when (inspection) {
                    is Notary.Inspection.Pass -> UserPrincipal.from(inspection.claims)
                    is Notary.Inspection.Fail -> null
                }
            }
        }
    }
}

data class UserPrincipal(
    val dao: UserDAO,
    val userId: String,
    val givenName: String,
    val familyName: String,
    val fullName: String,
    val picture: String,
) {
    companion object {
        fun from(claims: UserInfo) =
            UserPrincipal(
                userId = claims.userId,
                givenName = claims.givenName,
                familyName = claims.familyName,
                fullName = claims.fullName,
                picture = claims.picture,
                dao = UserDAO.findOrCreateByNotaryId(claims.userId),
            )
    }
}

fun RoutingCall.user() = principal<UserPrincipal>()!!
