/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

@file:Suppress("PackageDirectoryMismatch") // Old package for compatibility
package org.jetbrains.kotlin.gradle.internal

import com.android.build.api.dsl.AndroidSourceDirectorySet
import com.android.build.api.dsl.AndroidSourceFile
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.CompilerPluginConfig
import java.io.File
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

class Agp72SourceSetHandler(project: Project) : AgpSourceSets {

    private val ext: CommonExtension<*, *, *, *> = project.extensions.getByType(CommonExtension::class.java)
    private val componentExt = project.extensions.getByType(AndroidComponentsExtension::class.java)

    override fun onEachVariant(callable: (String) -> Provider<CompilerPluginConfig>) {
        componentExt.onVariants {
            val options = callable(it.name)
            it.kotlinCompilerOptions.add(options)
        }
    }

    override fun getMainManifest(): File {
        val sourceFile = ext.sourceSets.getByName("main").manifest
        @Suppress("UNCHECKED_CAST") val property =
            sourceFile::class.memberProperties.find { it.name == "srcFile" }!! as KProperty1<AndroidSourceFile, File>

        return property.get(sourceFile)
    }

    override fun getResDirsForSourceSet(name: String): Set<File>? {
        val sourceset = ext.sourceSets.findByName(name) ?: return null

        @Suppress("UNCHECKED_CAST") val propery: KProperty1<AndroidSourceDirectorySet, Set<File>> =
            sourceset.res::class.memberProperties.find { it.name == "srcDirs" }!! as KProperty1<AndroidSourceDirectorySet, Set<File>>
        return propery.get(sourceset.res)
    }
}
