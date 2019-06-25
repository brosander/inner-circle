package com.github.brosander.innercircle.services.security.session

import com.fasterxml.jackson.annotation.JsonProperty
import com.github.brosander.innercircle.services.InnerCircleService
import com.github.brosander.innercircle.model.InnerCircleSession
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.multibindings.ProvidesIntoSet
import io.ktor.application.Application
import io.ktor.application.ApplicationCall
import io.ktor.application.install
import io.ktor.sessions.*
import io.ktor.util.hex
import javax.inject.Singleton

class CookieSessionProvider(auth: SessionTransportTransformerMessageAuthentication) : InnerCircleService, SessionProvider {
    override val sortOrder: Int = 0

    override val name: String = "SESSION_FEATURE_SESSION"

    override fun get(call: ApplicationCall): InnerCircleSession? {
        val session = call.sessions.get<InnerCircleSession>() ?: return null
        if (System.currentTimeMillis() > session.expiration) {
            return null
        }
        return session
    }

    override val config: Application.() -> Unit = {
        install(Sessions) {
            cookie<InnerCircleSession>(name) {
                transform(auth)
                cookie.path = "/"
            }
        }
    }
}

class CookieSessionModule(@JsonProperty("sessionHashKey") sessionHashKey: String) : AbstractModule() {
    private val cookieSessionProvider = CookieSessionProvider(SessionTransportTransformerMessageAuthentication(hex(sessionHashKey), "HmacSHA256"))

    @Singleton
    @ProvidesIntoSet
    fun getInnerCircleModule(): InnerCircleService = cookieSessionProvider

    @Singleton
    @Provides
    fun getSessionProvider(): SessionProvider = cookieSessionProvider
}