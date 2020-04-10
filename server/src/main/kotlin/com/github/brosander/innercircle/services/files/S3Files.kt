package com.github.brosander.innercircle.services.files

import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.inject.AbstractModule
import com.google.inject.Provides
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

class S3FileResolver(private val s3Client: AmazonS3, private val bucket: String) : FileResolver {
    override fun resolve(location: String): String =
            s3Client.generatePresignedUrl(bucket, location, Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))).toExternalForm()
}

class S3FilesModule(@JsonProperty("region") region: String, private val bucket: String) : AbstractModule() {
    private val s3Client: AmazonS3 = AmazonS3Client.builder()
            .withRegion(region)
            .build()

    @Singleton
    @Provides
    fun getFileResolver() : FileResolver = S3FileResolver(s3Client, bucket)
}