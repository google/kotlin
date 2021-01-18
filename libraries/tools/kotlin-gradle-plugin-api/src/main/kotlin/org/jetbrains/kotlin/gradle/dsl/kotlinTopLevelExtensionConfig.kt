/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl

interface KotlinTopLevelExtensionConfig {
    val experimental: ExperimentalExtensionConfig

    var coreLibrariesVersion: String

    var explicitApi: ExplicitApiMode?

    fun explicitApi()

    fun explicitApiWarning()
}

interface ExperimentalExtensionConfig {
    var coroutines: Coroutines?
}


enum class ExplicitApiMode(private val cliOption: String) {
    Strict("strict"),
    Warning("warning"),
    Disabled("disable");

    fun toCompilerArg() = "-Xexplicit-api=$cliOption"
}

enum class Coroutines {
    ENABLE,
    WARN,
    ERROR,
    DEFAULT;

    companion object {
        fun byCompilerArgument(argument: String): Coroutines? =
            values().firstOrNull { it.name.equals(argument, ignoreCase = true) }
    }
}
