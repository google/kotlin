/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig

interface KotlinCompileOptions {
    val taskName: String

    val source: ConfigurableFileCollection

    val friendPaths: ConfigurableFileCollection

    val classpath: ConfigurableFileCollection

    val pluginClasspath: ConfigurableFileCollection

    val moduleName: Property<String>

    val sourceSetName: Property<String>

    val multiPlatformEnabled: Property<Boolean>

    val taskBuildDirectory: DirectoryProperty

    val destinationDir: DirectoryProperty

    val useModuleDetection: Property<Boolean>

    val parentKotlinOptions: Property<KotlinJvmOptions>

    val additionalPluginOptions: ListProperty<CompilerPluginConfig>

    val explicitApiMode: Property<ExplicitApiMode>
}

interface KaptGenerateStubsOptions : KotlinCompileOptions {
    val stubsDir: DirectoryProperty
    val javaSourceRoots: ConfigurableFileCollection
    val allKotlinSources: ConfigurableFileCollection
    val kaptClasspath: ConfigurableFileCollection
}

interface KaptOptions {
    val taskName: String
    val addJdkClassesToClasspath: Property<Boolean>
    val kaptJars: ConfigurableFileCollection

    val kaptClasspath: ConfigurableFileCollection
    val kaptExternalClasspath: ConfigurableFileCollection
    val kaptClasspathConfigurationNames: ListProperty<String>

    val incAptCache: DirectoryProperty

    val classesDir: DirectoryProperty

    val destinationDir: DirectoryProperty

    val kotlinSourcesDestinationDir: DirectoryProperty

    val annotationProcessorOptionProviders: MutableList<Any>

    val stubsDir: DirectoryProperty

    val compiledSources: ConfigurableFileCollection

    val classpath: ConfigurableFileCollection

    val sourceSetName: Property<String>

    val source: ConfigurableFileCollection

    val includeCompileClasspath: Property<Boolean>

    val defaultJavaSourceCompatibility: Property<String>
}