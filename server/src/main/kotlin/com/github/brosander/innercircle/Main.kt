package com.github.brosander.innercircle

import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.github.brosander.innercircle.model.DataStore
import com.github.brosander.innercircle.model.InnerCircleSession
import com.github.brosander.innercircle.model.Media
import io.ktor.application.ApplicationCall
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.features.*
import io.ktor.html.respondHtml
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.default
import io.ktor.http.content.files
import io.ktor.http.content.resources
import io.ktor.http.content.static
import io.ktor.jackson.jackson
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.get
import io.ktor.routing.param
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.sessions.*
import io.ktor.util.hex
import kotlinx.html.*
import java.io.File
import java.lang.Exception
import java.util.*
import java.util.concurrent.TimeUnit


fun ApplicationCall.getInnerCircleSession(): InnerCircleSession? {
    val session = sessions.get<InnerCircleSession>() ?: return null
    if (System.currentTimeMillis() > session.expiration) {
        return null
    }
    return session
}

data class AuthorizationException(override val message: String): Exception(message)

@Location("/login/{type?}") class login(val type: String = "")

val loginProviders = listOf(
    OAuthServerSettings.OAuth2ServerSettings(
        name = "google",
        authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
        accessTokenUrl = "https://www.googleapis.com/oauth2/v3/token",
        requestMethod = HttpMethod.Post,

        clientId = System.getenv("OAUTH2_CLIENT_ID"),
        clientSecret = System.getenv("OAUTH2_CLIENT_SECRET"),
        defaultScopes = listOf("openid", "email", "profile")
    )
).associateBy { it.name }

val httpClient = HttpClient(Apache)

fun main(args: Array<String>) {
    val s3Client = AmazonS3ClientBuilder.standard()
        .withRegion(System.getenv("ASSET_S3_BUCKET_REGION"))
        .build()
    val assetS3Bucket = System.getenv("ASSET_S3_BUCKET")
    val dataStore = DataStore { s3Client.generatePresignedUrl(assetS3Bucket, it, Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))).toExternalForm() }
//    val media = Media(dataStore)

    val server = embeddedServer(Netty, port = 8081) {
        install(CORS) {
            host(Url(System.getenv("OAUTH2_SUCCESS_URL")).host, listOf("https"))

            allowCredentials = true
        }
        install(Sessions) {
            cookie<InnerCircleSession>("SESSION_FEATURE_SESSION") {
                transform(SessionTransportTransformerMessageAuthentication(hex(System.getenv("SESSION_HASH_KEY")), "HmacSHA256"))
                cookie.path = "/"
            }
        }
        install(CallLogging)
        install(Locations)
        install(Authentication) {
            oauth("oauth") {
                client = httpClient
                providerLookup = { loginProviders[application.locations.resolve<login>(login::class, this).type] }
                urlProvider = { "${System.getenv("OAUTH2_REDIRECT_URL")}/${it.name}" }
            }
        }

        install(AutoHeadResponse)
        install(ContentNegotiation) {
            jackson {
                configure(SerializationFeature.INDENT_OUTPUT, true)
                setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
                    indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                    indentObjectsWith(DefaultIndenter("  ", "\n"))
                })
            }
        }
        install(StatusPages) {
            exception<AuthorizationException> {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
        routing {
            trace { application.log.debug(it.buildText()) }

            get("/api/v1/posts") {
                val session = call.getInnerCircleSession()
                if (session == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    call.respond(dataStore.listPosts(session.userId, call.request.queryParameters["beforeId"]?.toLong()))
                }
            }
            /*get("/assets/{path...}") {
                val session = call.getInnerCircleSession()
                if (session == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    val path = call.parameters.getAll("path")?.joinToString("/")
                    if (path != null) {
                        call.respondFile(media.getMedia(session.userId, path))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }*/
            authenticate("oauth") {
                location<login>() {
                    param("error") {
                        handle {
                            call.loginFailedPage(call.parameters.getAll("error").orEmpty())
                        }
                    }

                    handle {
                        val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                        if (principal != null) {
                            val email = getUserEmail(principal)
                            val userId = dataStore.getUserId(email)
                            if (userId != null) {
                                call.sessions.set(userId)
                                call.respondRedirect(System.getenv("OAUTH2_SUCCESS_URL"))
                            } else {
                                call.loginFailedPage(listOf("Unknown email address: $email, please contact administrator."))
                            }
                        } else {
                            call.respondRedirect("/login/google")
                        }
                    }
                }
            }
            static {
                val staticFolder = File(System.getenv("APP_HOME"), "static")
                files(staticFolder)
                default(File(staticFolder, "index.html"))
            }
        }
    }
    server.start(wait = true)
}

private suspend fun ApplicationCall.loginFailedPage(errors: List<String>) {
    respondHtml {
        head {
            title { +"Problem Logging In" }
        }
        body {
            h1 {
                +"Login error"
            }

            for (e in errors) {
                p {
                    +e
                }
            }
        }
    }
}

val objectMapper = ObjectMapper()

private suspend fun getUserEmail(callback: OAuthAccessTokenResponse.OAuth2): String {
    val getUserEmailUrl = objectMapper.readTree(httpClient.get<String>("https://accounts.google.com/.well-known/openid-configuration")
        .toString()).get("userinfo_endpoint").asText()
    return objectMapper.readTree(httpClient.get<String>(getUserEmailUrl) {
        headers.append("Authorization", "Bearer ${callback.accessToken}")
    }).get("email").asText()
}