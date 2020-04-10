package com.github.brosander.innercircle.services

import com.google.inject.AbstractModule
import com.google.inject.Provides
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import javax.inject.Singleton

class ServiceContext(val baseUrlString: String) {
    val baseUrl = Url(baseUrlString)

    val port = baseUrl.port

    fun resolve(path: String): String = URLBuilder(baseUrl)
            .path(if (path.startsWith('/')) path.substring(1) else path)
            .buildString()
}

class ServiceContextModule(val baseUrl: String?): AbstractModule() {

    @Singleton
    @Provides
    fun getServiceContext(): ServiceContext = ServiceContext(baseUrl ?: "/")
}