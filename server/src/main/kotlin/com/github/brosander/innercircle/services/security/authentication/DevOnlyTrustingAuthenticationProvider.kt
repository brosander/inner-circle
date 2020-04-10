package com.github.brosander.innercircle.services.security.authentication

import com.github.brosander.innercircle.model.DataStore
import com.github.brosander.innercircle.services.InnerCircleService
import com.github.brosander.innercircle.services.ServiceContext
import com.github.brosander.innercircle.services.security.AuthenticationProvider
import com.google.inject.AbstractModule
import com.google.inject.multibindings.ProvidesIntoSet
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.sessions.sessions
import io.ktor.sessions.set
import io.ktor.velocity.VelocityContent
import javax.inject.Inject
import javax.inject.Singleton

class DevOnlyTrustingAuthenticationProvider @Inject @Singleton constructor(dataStore: DataStore, serviceContext: ServiceContext): AuthenticationProvider, InnerCircleService {
    override val sortOrder: Int = 2_000

    override val name: String = "Trusting (DEV ONLY, NOT PROD)"

    override val path: String = "/login/trust"

    override val config: Application.() -> Unit = {
        routing {
            get(path) {
                call.respond(VelocityContent("templates/security/authentication/devOnlyTrust.vl", HashMap(mapOf("users" to dataStore.listUsers(), "path" to path)), "e"))
            }
            get("$path/{id}") {
                val id = call.parameters["id"]
                val session = dataStore.getSessionForId(Integer.parseInt(id))
                if (session != null) {
                    call.sessions.set(session)
                    call.respondRedirect(serviceContext.baseUrlString)
                } else {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }
    }
}

class DevOnlyTrustingAuthenticationProviderModule: AbstractModule() {

    @Singleton
    @ProvidesIntoSet
    fun getAuthenticationProvider(devOnlyTrustingAuthenticationProvider: DevOnlyTrustingAuthenticationProvider): AuthenticationProvider = devOnlyTrustingAuthenticationProvider


    @Singleton
    @ProvidesIntoSet
    fun getInnerCircleService(devOnlyTrustingAuthenticationProvider: DevOnlyTrustingAuthenticationProvider): InnerCircleService = devOnlyTrustingAuthenticationProvider
}