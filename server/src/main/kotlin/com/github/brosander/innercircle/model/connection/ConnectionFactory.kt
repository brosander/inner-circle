package com.github.brosander.innercircle.model.connection

import java.sql.Connection

interface ConnectionFactory {
    fun getConnection(): Connection
}