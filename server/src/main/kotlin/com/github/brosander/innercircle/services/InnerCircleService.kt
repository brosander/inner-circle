package com.github.brosander.innercircle.services

import io.ktor.application.Application

interface InnerCircleService {
    val sortOrder: Int

    val config: Application.() -> Unit
}