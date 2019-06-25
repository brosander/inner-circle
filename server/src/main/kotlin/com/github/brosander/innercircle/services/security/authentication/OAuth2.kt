package com.github.brosander.innercircle.services.security.authentication

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.brosander.innercircle.services.InnerCircleService
import com.github.brosander.innercircle.model.DataStore
import com.github.brosander.innercircle.services.ServiceContext
import com.github.brosander.innercircle.services.security.AuthenticationProvider
import com.google.inject.AbstractModule
import com.google.inject.multibindings.ProvidesIntoSet
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import io.ktor.features.CORS
import io.ktor.html.respondHtml
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.locations.location
import io.ktor.locations.locations
import io.ktor.response.respondRedirect
import io.ktor.routing.param
import io.ktor.routing.routing
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import kotlinx.html.*
import javax.inject.Singleton

class OAuth2(private val dataStore: DataStore, val serviceContext: ServiceContext, val loginProviders: Map<String, OAuthServerSettings.OAuth2ServerSettings>, oauth2RedirectPath: String): InnerCircleService, AuthenticationProvider {
    override val sortOrder: Int = 4000

    override val name: String = "OAuth2"
    override val path: String = "/login/google"

    val oauth2RedirectUrl = "${serviceContext.baseUrl}$oauth2RedirectPath"

    val httpClient = HttpClient(Apache)

    private val objectMapper = ObjectMapper()

    @Location("/login/{type?}") class login(val type: String = "")

    override val config: Application.() -> Unit = {
        install(CORS) {
            host(serviceContext.baseUrl.host, listOf("https"))

            allowCredentials = true
        }

        install(Locations)

        install(Authentication) {
            oauth("oauth") {
                client = httpClient
                providerLookup = { loginProviders[application.locations.resolve<login>(login::class, this).type] }
                urlProvider = { "$oauth2RedirectUrl/${it.name}" }
            }
        }

        routing {
            authenticate("oauth") {
                location<login> {
                    param("error") {
                        handle {
                            call.loginFailedPage(call.parameters.getAll("error").orEmpty())
                        }
                    }

                    handle {
                        val principal = call.authentication.principal<OAuthAccessTokenResponse.OAuth2>()
                        if (principal != null) {
                            val email = getUserEmail(principal)
                            val session = dataStore.getSessionForEmail(email)
                            if (session != null) {
                                call.sessions.set(session)
                                call.respondRedirect(serviceContext.baseUrlString)
                            } else {
                                call.loginFailedPage(listOf("Unknown email address: $email, please contact administrator."))
                            }
                        } else {
                            call.respondRedirect("/login/google")
                        }
                    }
                }
            }
        }
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

    private suspend fun getUserEmail(callback: OAuthAccessTokenResponse.OAuth2): String  {
        val getUserEmailUrl = objectMapper.readTree(httpClient.get<String>("https://accounts.google.com/.well-known/openid-configuration")
            .toString()).get("userinfo_endpoint").asText()
        return objectMapper.readTree(httpClient.get<String>(getUserEmailUrl) {
            headers.append("Authorization", "Bearer ${callback.accessToken}")
        }).get("email").asText()
    }
}

class OAuth2Module(@JsonProperty("clientId") clientId: String, @JsonProperty("clientSecret") clientSecret: String,
                   @JsonProperty("oauth2RedirectPath") val oauth2RedirectPath: String) : AbstractModule() {
    val loginProviders = listOf(
            OAuthServerSettings.OAuth2ServerSettings(
                    name = "google",
                    authorizeUrl = "https://accounts.google.com/o/oauth2/auth",
                    accessTokenUrl = "https://www.googleapis.com/oauth2/v3/token",
                    requestMethod = HttpMethod.Post,

                    clientId = clientId,
                    clientSecret = clientSecret,
                    defaultScopes = listOf("openid", "email", "profile")
            )
    ).associateBy { it.name }

    @Singleton
    @ProvidesIntoSet
    fun getInnerCircleModule(dataStore: DataStore, serviceContext: ServiceContext): InnerCircleService = OAuth2(dataStore, serviceContext, loginProviders, oauth2RedirectPath)
}