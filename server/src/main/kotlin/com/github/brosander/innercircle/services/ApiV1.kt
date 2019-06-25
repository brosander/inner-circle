package com.github.brosander.innercircle.services

import com.github.brosander.innercircle.model.DataStore
import com.github.brosander.innercircle.services.security.session.SessionProvider
import com.google.inject.AbstractModule
import com.google.inject.multibindings.ProvidesIntoSet
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import javax.inject.Singleton

class ApiV1(private val dataStore: DataStore, private val sessionProvider: SessionProvider): InnerCircleService {
    override val sortOrder: Int = 1_000

    override val config: Application.() -> Unit = {
        routing {
            get("/api/v1/posts") {
                val session = sessionProvider.get(call)
                if (session == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    call.respond(dataStore.listPosts(session.userId, call.request.queryParameters["beforeId"]?.toLong()))
                }
            }

            post("/api/v1/posts") {
                val session = sessionProvider.get(call)
                if (session == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
//                    call.rec()
                }
            }

            get("/api/v1/circles") {
                val session = sessionProvider.get(call)
                if (session == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    call.respond(dataStore.listCircles(session.userId))
                }
            }
        }
    }
}

class ApiV1Module : AbstractModule() {

    @Singleton
    @ProvidesIntoSet
    fun getInnerCircleModule(dataStore: DataStore, sessionProvider: SessionProvider): InnerCircleService = ApiV1(dataStore, sessionProvider)
}