package com.github.brosander.innercircle.model

import com.github.brosander.innercircle.AuthorizationException
import java.io.File

class Media(val store: DataStore, val baseDir: File) {
    val thumbnailSuffix = ".thumbnail.jpg"

    fun getMedia(userId: Int, path:String): File {
        val rawName: String
        if (path.endsWith(thumbnailSuffix)) {
            rawName = path.substring(0, path.length - thumbnailSuffix.length)
        } else {
            rawName = path
        }

        if (store.checkMediaAccess(userId, rawName)) {
            return File(baseDir, path)
        }
        throw AuthorizationException("User $userId not allowed to access $path")
    }
}