package com.github.brosander.innercircle.services.security

import com.github.brosander.innercircle.services.InnerCircleService
import com.github.brosander.innercircle.services.ServiceContext
import com.github.brosander.innercircle.services.security.session.SessionProvider
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.sessions.sessions
import io.ktor.velocity.VelocityContent
import java.util.*
import kotlin.collections.HashMap

class LoginHandler (providers: Set<AuthenticationProvider>, sessionProvider: SessionProvider, serviceContext: ServiceContext) : InnerCircleService {
    override val sortOrder: Int = 3_000

    override val config: Application.() -> Unit = {
        val sortedProviders = Collections.unmodifiableList(providers.toList().sortedBy { it.name })

        routing {
            get("/login") {
                if (providers.size == 1) {
                    call.respondRedirect(serviceContext.resolve(providers.iterator().next().path))
                } else {
                    call.respond(VelocityContent("templates/security/login.vl", HashMap(mapOf("providers" to sortedProviders)), "e"))
                }
            }
            get("/logout") {
                call.sessions.clear(sessionProvider.name)
                call.respondRedirect("/")
            }
        }
    }
}