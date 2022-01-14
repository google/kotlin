/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks.configuration

import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinCompilationData
import org.jetbrains.kotlin.gradle.targets.js.ir.isProduceUnzippedKlib
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.utils.property
import java.io.File

internal typealias Kotlin2JsCompileConfig = BaseKotlin2JsCompileConfig<Kotlin2JsCompile>

@Suppress("MemberVisibilityCanBePrivate")
internal open class BaseKotlin2JsCompileConfig<TASK : Kotlin2JsCompile>(
    compilation: KotlinCompilationData<*>,
    taskName: String = compilation.compileKotlinTaskName,
) : AbstractKotlinCompileConfig<TASK>(compilation, taskName) {

    init {
        incremental.set(propertiesProvider.incrementalJs ?: true)
    }

    val incrementalJsKlib: Property<Boolean> = objectFactory.property(propertiesProvider.incrementalJsKlib ?: true)

    @Deprecated("Avoid using this, instead add new properties to this class and use those to configure the task.")
    protected val kotlinCompilation = compilation

    override fun configureTask(taskProvider: TaskProvider<TASK>) {
        super.configureTask(taskProvider)

        taskProvider.configure { task ->
            task.incrementalJsKlib = incrementalJsKlib.get()
            task.outputFileProperty.value(task.project.provider {
                task.kotlinOptions.outputFile?.let(::File)
                    ?: task.destinationDirectory.locationOnly.get().asFile.resolve("${kotlinCompilation.ownModuleName}.js")
            }).disallowChanges()

            task.optionalOutputFile.fileProvider(task.outputFileProperty.flatMap { outputFile ->
                task.project.provider {
                    outputFile.takeUnless { task.kotlinOptions.isProduceUnzippedKlib() }
                }
            }).disallowChanges()

            val libraryCacheService = task.project.rootProject.gradle.sharedServices.registerIfAbsent(
                "${Kotlin2JsCompile.LibraryFilterCachingService::class.java.canonicalName}_${Kotlin2JsCompile.LibraryFilterCachingService::class.java.classLoader.hashCode()}",
                Kotlin2JsCompile.LibraryFilterCachingService::class.java
            ) {}
            task.libraryCache.set(libraryCacheService).also { task.libraryCache.disallowChanges() }
        }
    }
}