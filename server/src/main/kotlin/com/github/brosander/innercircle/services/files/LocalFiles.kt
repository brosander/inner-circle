package com.github.brosander.innercircle.services.files

import com.github.brosander.innercircle.entrypoints.AuthorizationException
import com.github.brosander.innercircle.services.InnerCircleService
import com.github.brosander.innercircle.model.DataStore
import com.github.brosander.innercircle.services.security.session.SessionProvider
import com.google.inject.AbstractModule
import com.google.inject.Provides
import com.google.inject.multibindings.ProvidesIntoSet
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondFile
import io.ktor.routing.get
import io.ktor.routing.routing
import java.io.File
import javax.inject.Singleton

class LocalFiles(private val store: DataStore, private val sessionProvider: SessionProvider, private val baseDir: File): InnerCircleService {
    override val sortOrder: Int = 5_000

    private val thumbnailSuffix = ".thumbnail.jpg"

    override val config: Application.() -> Unit = {
        routing {
            get("/assets/{path...}") {
                val session = sessionProvider.get(call)
                if (session == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    val path = call.parameters.getAll("path")?.joinToString("/")
                    if (path != null) {
                        call.respondFile(getFile(session.userId, path))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }
    }

    fun getFile(userId: Int, path:String): File {
        val rawName = if (path.endsWith(thumbnailSuffix)) {
            path.substring(0, path.length - thumbnailSuffix.length)
        } else {
            path
        }

        if (store.checkFileAccess(userId, rawName)) {
            return File(baseDir, path)
        }
        throw AuthorizationException("User $userId not allowed to access $path")
    }
}

class LocalFilesModule(val baseDir: File) : AbstractModule() {

    @Singleton
    @Provides
    fun getFileResolver(): FileResolver = object : FileResolver {
        override fun resolve(location: String): String = "/assets/$location"
    }

    @Singleton
    @ProvidesIntoSet
    fun getInnerCircleModule(store: DataStore, sessionProvider: SessionProvider): InnerCircleService =
            LocalFiles(store, sessionProvider, baseDir)
}