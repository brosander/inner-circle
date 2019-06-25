package com.github.brosander.innercircle.services

import com.google.inject.AbstractModule
import com.google.inject.multibindings.ProvidesIntoSet
import io.ktor.application.Application
import io.ktor.http.content.default
import io.ktor.http.content.files
import io.ktor.http.content.static
import io.ktor.routing.routing
import java.io.File
import javax.inject.Singleton

class Client(private val staticFolder: File) : InnerCircleService {
    override val sortOrder: Int = 1_000_000

    override val config: Application.() -> Unit = {
        routing {
            static {
                files(staticFolder)
                default(File(staticFolder, "index.html"))
            }
        }
    }
}

class ClientModule(val staticFolder: File) : AbstractModule() {

    @Singleton
    @ProvidesIntoSet
    fun getInnerCircleModule(): InnerCircleService = Client(staticFolder)
}