/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.dsl

import groovy.lang.Closure

/** KAPT extension used to configure stub generation and annotation processing. */
interface KaptExtensionApi {
    var includeCompileClasspath: Boolean?

    var useLightAnalysis: Boolean

    var correctErrorTypes: Boolean

    var dumpDefaultParameterValues: Boolean

    var mapDiagnosticLocations: Boolean

    var strictMode: Boolean

    var stripMetadata: Boolean

    var showProcessorTimings: Boolean

    var detectMemoryLeaks: String

    var useBuildCache: Boolean

    var keepJavacAnnotationProcessors: Boolean

    fun annotationProcessor(fqName: String)

    fun annotationProcessors(vararg fqName: String)

    fun arguments(action: KaptArguments.() -> Unit)

    fun javacOptions(action: KaptJavacOption.() -> Unit)

    fun getJavacOptions(): Map<String, String>

    fun getExplicitAnnotationProcessors(): String
}

interface KaptArguments {
    fun arg(name: Any, vararg values: Any)
}

interface KaptJavacOption {
    fun option(name: Any, value: Any)

    fun option(name: Any)
}