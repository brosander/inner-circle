package com.github.brosander.innercircle.config

import com.google.inject.AbstractModule
import com.google.inject.Provides
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.inject.Singleton

class LocalAssetResolver(val baseDir: Path) {
    fun resolvePath(path: String): Path = baseDir.resolve(path).toAbsolutePath()

    fun resolveFile(path: String): File = resolvePath(path).toFile()
}

class LocalAssetResolverModule(val baseDir: String): AbstractModule() {

    @Singleton
    @Provides
    fun getLocalAssetResolver(): LocalAssetResolver = LocalAssetResolver(Paths.get(baseDir))
}