package com.github.brosander.innercircle.util

import io.ktor.util.hex
import java.security.SecureRandom

val secureRandom = SecureRandom()

val functions: Map<String, (List<String>) -> String> = mapOf(
        "randomHex" to { args ->
            hex(ByteArray(Integer.parseInt(args[0])).apply {
                secureRandom.nextBytes(this)
            })
        }
)

fun apply(invocation: String): String? {
    val startArgs = invocation.indexOf('(')
    val endArgs = invocation.indexOf(')', startArgs)

    return functions[invocation.substring(0, startArgs)]?.invoke(invocation.substring(startArgs + 1, endArgs).split(',').map { it.trim() })
}