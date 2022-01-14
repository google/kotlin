/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks.configuration

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.internal.CompilerArgumentsContributor
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin.Companion.KAPT_SUBPLUGIN_ID
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin.Companion.isIncludeCompileClasspath
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.internal.KaptTask
import org.jetbrains.kotlin.gradle.internal.buildKaptSubpluginOptions
import org.jetbrains.kotlin.gradle.plugin.KaptExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinCompilationData
import org.jetbrains.kotlin.gradle.tasks.CompilerPluginOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.utils.property
import java.io.File
import java.util.concurrent.Callable

@Suppress("MemberVisibilityCanBePrivate")
internal class KaptGenerateStubsConfig(taskName: String, compilation: KotlinCompilationData<*>, taskProvider: TaskProvider<KotlinCompile>) :
    BaseKotlinCompileConfig<KaptGenerateStubsTask>(compilation, taskName) {

    private val kaptExtension: KaptExtension = project.extensions.getByType(KaptExtension::class.java)

    val allKotlinSources: ConfigurableFileCollection = objectFactory.fileCollection()

    val allJavaSourceRoot: ConfigurableFileCollection = objectFactory.fileCollection()

    val verbose: Property<Boolean> = objectFactory.property(KaptTask.queryKaptVerboseProperty(project))

    val compileKotlinArgumentsContributor: Property<CompilerArgumentsContributor<K2JVMCompilerArguments>> = objectFactory.property()

    val stubsDir: DirectoryProperty = objectFactory.directoryProperty()

    val excludedSourceDirs: ListProperty<File> = objectFactory.listProperty(File::class.java)

    val kaptClasspath: ConfigurableFileCollection = objectFactory.fileCollection()

    init {
        val kotlinCompileTask = taskProvider.get()
        useModuleDetection.value(kotlinCompileTask.useModuleDetection).disallowChanges()
        moduleName.value(kotlinCompileTask.moduleName).disallowChanges()
        classpath.from(Callable { kotlinCompileTask.classpath })
        compileKotlinArgumentsContributor.set(providers.provider { kotlinCompileTask.compilerArgumentsContributor })
        allKotlinSources.from(providers.provider { kotlinCompileTask.getSourceRoots().kotlinSourceFiles })
        allJavaSourceRoot.from(providers.provider { kotlinCompileTask.getSourceRoots().javaSourceRoots })

        additionalPluginOptions.add(buildOptions())
    }

    private fun isIncludeCompileClasspath() = kaptExtension.includeCompileClasspath ?: project.isIncludeCompileClasspath()

    private fun buildOptions(): Provider<CompilerPluginOptions> {
        val javacOptions = project.provider { kaptExtension.getJavacOptions() }
        return project.provider {
            val compilerPluginOptions = CompilerPluginOptions()
            buildKaptSubpluginOptions(
                kaptExtension,
                project,
                javacOptions.get(),
                aptMode = "stubs",
                generatedSourcesDir = objectFactory.fileCollection().from(destinationDir),
                generatedClassesDir = objectFactory.fileCollection().from(destinationDir),
                incrementalDataDir = objectFactory.fileCollection().from(destinationDir),
                includeCompileClasspath = isIncludeCompileClasspath(),
                kaptStubsDir = objectFactory.fileCollection().from(stubsDir)
            ).forEach {
                compilerPluginOptions.addPluginArgument(KAPT_SUBPLUGIN_ID, it)
            }
            return@provider compilerPluginOptions
        }
    }

    override fun configureTask(taskProvider: TaskProvider<KaptGenerateStubsTask>) {
        super.configureTask(taskProvider)

        taskProvider.configure { task ->
            task.kotlinSources.from(allKotlinSources).disallowChanges()
            task.javaSourceRoots.from(allJavaSourceRoot).disallowChanges()
            task.verbose.set(verbose)
            task.compileKotlinArgumentsContributor.set(compileKotlinArgumentsContributor)

            task.excludedSourceDirs.set(excludedSourceDirs)
            task.kaptClasspath.from(kaptClasspath)
            task.stubsDir.value(stubsDir).disallowChanges()

            if (!isIncludeCompileClasspath()) {
                task.onlyIf {
                    !(it as KaptGenerateStubsTask).kaptClasspath.isEmpty
                }
            }
        }
    }
}
