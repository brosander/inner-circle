package com.github.brosander.innercircle.services.security

interface AuthenticationProvider {
    val name: String

    val path: String
}