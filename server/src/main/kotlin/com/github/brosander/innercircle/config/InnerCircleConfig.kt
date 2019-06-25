package com.github.brosander.innercircle.config

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.google.inject.Module

data class InnerCircleCliDefinition(val description: String, val environment: List<InnerCircleCliEnvironmentDefinition>, val arguments: List<InnerCircleCliArgumentDefinition>)
data class InnerCircleCliEnvironmentDefinition(val name: String, val description: String, val default: String?)
data class InnerCircleCliArgumentDefinition(val short: String?, val long: String, val description: String, val default: String?, val hasArg: Boolean = true)

data class InnerCircleConfig(@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "class") val modules: List<Module>)