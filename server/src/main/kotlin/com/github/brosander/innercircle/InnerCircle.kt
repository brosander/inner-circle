package com.github.brosander.innercircle

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.github.brosander.innercircle.config.InnerCircleCliDefinition
import com.github.brosander.innercircle.config.InnerCircleCliEnvironmentDefinition
import com.github.brosander.innercircle.config.InnerCircleConfig
import com.github.brosander.innercircle.entrypoints.InnerCircleEntrypoint
import com.github.brosander.innercircle.services.security.AuthenticationProvider
import com.google.inject.*
import com.google.inject.util.Types
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.text.StringSubstitutor
import org.apache.commons.text.lookup.StringLookup
import java.io.File
import org.apache.commons.cli.HelpFormatter
import java.lang.RuntimeException

fun usage(messageText: String?, description: String, options: Options, environment: List<InnerCircleCliEnvironmentDefinition>) {
    val message = if (messageText == null) "" else messageText + System.lineSeparator() + System.lineSeparator()

    val environmentText = if (environment.isNotEmpty()) {
        System.lineSeparator() + "Environment Variables:" + System.lineSeparator() + environment
                .map {
                    val text = "${it.name}: ${it.description}"
                    " " + if (it.default == null) text else "$text (default: ${it.default})"
                }
                .joinToString(System.lineSeparator()) + System.lineSeparator() + System.lineSeparator()
    } else {
        ""
    }

    val formatter = HelpFormatter()
    formatter.width = 200
    formatter.printHelp("inner-circle", "$message$description${System.lineSeparator()}${environmentText}Arguments:", options, "")
}

fun main(args: Array<String>) {
    val configRawLines = File(args[0]).readLines()

    val documentIndices = ArrayList<Int>()

    for (i in configRawLines.indices) {
        val line = configRawLines[i]

        if (line.startsWith("---")) {
            documentIndices.add(i)
        }
    }

    val objectMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule())

    val cli = objectMapper.readValue(configRawLines.subList(documentIndices[0], documentIndices[1]).joinToString(System.lineSeparator()), InnerCircleCliDefinition::class.java)

    val options = Options()

    cli.arguments
            .map {
                val isRequired = it.default == null && (it.required == null || it.required)
                val description = if (isRequired) {
                    "${it.description} (required)"
                } else if (it.default != null) {
                    "${it.description} (default: ${it.default})"
                } else {
                    it.description
                }
                val option = Option(it.short, it.long, it.hasArg, description)
                option.isRequired = isRequired
                option
            }
            .forEach { options.addOption(it) }

    options.addOption("h", "help", false, "Display help text")

    val commandLine = try {
        DefaultParser().parse(options, args.copyOfRange(1, args.size))
    } catch (e: Exception) {
        e.printStackTrace()
        println()
        usage(e.message, cli.description, options, cli.environment)
        return
    }

    if (commandLine.hasOption('h')) {
        usage(null, cli.description, options, cli.environment)
        return
    }

    val cliMap = cli.arguments
            .map {
                val value = commandLine.getOptionValue(it.long)
                if (value == null) {
                    if (it.default == null) {
                        it.long to null
                    } else {
                        it.long to it.default
                    }
                } else {
                    it.long to value
                }
            }
            .filter { it.second != null }
            .toMap() as Map<String, String>

    val config = try {
        objectMapper.readValue(StringSubstitutor(object : StringLookup {
            override fun lookup(key: String?): String? {
                if (key == null) {
                    throw RuntimeException("Expected key, got null.")
                }

                val split = key.split(".")

                return when {
                    "args" == split[0] -> cliMap[split[1]]
                            ?: if (options.getOption(split[0]).isRequired) {
                                null
                            } else {
                                throw RuntimeException("Required argument ${split[1]} unspecified.")
                            }
                    "env" == split[0] -> System.getProperty(split[1]) ?: System.getenv()[split[1]]
                    "func" == split[0] -> com.github.brosander.innercircle.util.apply(split[1])
                    ?: throw RuntimeException("Environment value ${split[1]} missing from env, system properties.")
                    else -> throw RuntimeException("Malformed spec prefix ${split[0]}, expected args or env.")
                }
            }
        }).replace(configRawLines.subList(documentIndices[1], configRawLines.size).joinToString(System.lineSeparator())), InnerCircleConfig::class.java)
    } catch (e: Exception) {
        e.printStackTrace()
        println()
        usage(e.message, cli.description, options, cli.environment)
        return
    }

    val entrypoints = Guice.createInjector(config.modules).getInstance(Key.get(TypeLiteral.get(Types.setOf(InnerCircleEntrypoint::class.java)))) as Set<InnerCircleEntrypoint>
    entrypoints.map {
        val t = Thread(it)
        t.start()
        t
    }.forEach { it.join() }
}