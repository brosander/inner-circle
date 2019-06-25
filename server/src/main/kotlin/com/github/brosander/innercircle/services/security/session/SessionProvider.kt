package com.github.brosander.innercircle.services.security.session

import com.github.brosander.innercircle.model.InnerCircleSession
import io.ktor.application.ApplicationCall

interface SessionProvider {
    val name: String

    fun get(call: ApplicationCall) : InnerCircleSession?
}