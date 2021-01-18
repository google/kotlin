/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks.configuration

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinJsBinaryMode
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrCompilation
import org.jetbrains.kotlin.gradle.targets.js.ir.KotlinJsIrLink
import org.jetbrains.kotlin.gradle.utils.property


internal open class KotlinJsIrLinkConfig(
    taskName: String,
    compilation: KotlinJsIrCompilation
) : BaseKotlin2JsCompileConfig<KotlinJsIrLink>(compilation, taskName) {

    val entryModule: DirectoryProperty = objectFactory.directoryProperty().fileProvider(
        compilation.output.classesDirs.elements.map { it.single().asFile }
    ).also {
        it.disallowChanges()
    }

    val mode: Property<KotlinJsBinaryMode> = objectFactory.property()

    override fun configureTask(taskProvider: TaskProvider<KotlinJsIrLink>) {
        super.configureTask(taskProvider)
        taskProvider.configure { task ->
            task.entryModule.set(entryModule)
            task.compilation = kotlinCompilation
            task.modeProperty.value(mode).disallowChanges()
            task.destinationDirectory.fileProvider(task.outputFileProperty.map { it.parentFile }).disallowChanges()
        }
    }
}
