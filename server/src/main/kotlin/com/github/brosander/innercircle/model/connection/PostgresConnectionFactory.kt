package com.github.brosander.innercircle.model.connection

import com.google.inject.AbstractModule
import com.google.inject.Provides
import java.sql.Connection
import java.sql.DriverManager
import javax.inject.Singleton

class PostgresConnectionFactory(private val postgresUrl: String, private val user: String, private val password: String) : ConnectionFactory {
    constructor(hostname: String, port: Int, db: String, user: String, password: String): this("jdbc:postgresql://$hostname:$port/$db", user, password)

    override fun getConnection(): Connection = DriverManager.getConnection(postgresUrl, user, password)
}

class PostgresConnectionFactoryModule(hostname: String, port: Int, db: String,
                                      user: String, password: String) : AbstractModule() {
    private val connectionFactory = PostgresConnectionFactory(hostname, port, db, user, password)

    @Singleton
    @Provides
    fun getConnectionFactory(): ConnectionFactory = connectionFactory
}