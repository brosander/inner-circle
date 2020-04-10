package com.github.brosander.innercircle.services.files

interface FileResolver {
    fun resolve(location: String) : String
}