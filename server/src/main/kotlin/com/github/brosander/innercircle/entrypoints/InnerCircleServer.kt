package com.github.brosander.innercircle.entrypoints

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.SerializationFeature
import com.github.brosander.innercircle.services.InnerCircleService
import com.github.brosander.innercircle.services.ServiceContext
import com.github.brosander.innercircle.services.security.AuthenticationProvider
import com.github.brosander.innercircle.services.security.LoginHandler
import com.github.brosander.innercircle.services.security.session.SessionProvider
import com.google.inject.*
import com.google.inject.multibindings.ProvidesIntoSet
import com.google.inject.util.Types
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.AutoHeadResponse
import io.ktor.features.CallLogging
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.velocity.Velocity
import java.lang.Exception
import javax.inject.Singleton
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import org.apache.velocity.runtime.RuntimeConstants



data class AuthorizationException(override val message: String): Exception(message)

class InnerCircleServer(private val serviceContext: ServiceContext, val services: Set<InnerCircleService>, private val traceRouting: Boolean): InnerCircleEntrypoint {
    override fun run() {
        val server = embeddedServer(Netty, port = serviceContext.port) {
            install(CallLogging)

            install(AutoHeadResponse)

            install(StatusPages) {
                exception<AuthorizationException> {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }

            install(Velocity) {
                setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath")
                setProperty("classpath.resource.loader.class", ClasspathResourceLoader::class.java.name)
                init()
            }

            install(ContentNegotiation) {
                jackson {
                    configure(SerializationFeature.INDENT_OUTPUT, true)
                    setDefaultPrettyPrinter(DefaultPrettyPrinter().apply {
                        indentArraysWith(DefaultPrettyPrinter.FixedSpaceIndenter.instance)
                        indentObjectsWith(DefaultIndenter("  ", "\n"))
                    })
                }
            }

            if (traceRouting) {
                routing {
                    trace { application.log.info(it.buildText()) }
                }
            }
            services.sortedBy { it.sortOrder }.forEach { it.config(this) }
        }
        server.start(wait = true)
    }
}

class InnerCircleServerModule(val traceRouting: Boolean?) : AbstractModule() {

    @Singleton
    @Provides
    fun getInnerCircleEntrypoint(serviceContext: ServiceContext, injector: Injector): InnerCircleEntrypoint = InnerCircleServer(serviceContext, injector.getInstance(Key.get(TypeLiteral.get(Types.setOf(InnerCircleService::class.java)))) as Set<InnerCircleService>,
            traceRouting ?: false)

    @Singleton
    @ProvidesIntoSet
    fun getLoginHandler(injector: Injector, sessionProvider: SessionProvider, serviceContext: ServiceContext): InnerCircleService {
        val authenticationProviders: Set<AuthenticationProvider> = try {
            injector.getInstance(Key.get(TypeLiteral.get(Types.setOf(AuthenticationProvider::class.java)))) as Set<AuthenticationProvider>
        } catch (e: Exception) {
            emptySet()
        }
        return LoginHandler(authenticationProviders, sessionProvider, serviceContext)
    }
}