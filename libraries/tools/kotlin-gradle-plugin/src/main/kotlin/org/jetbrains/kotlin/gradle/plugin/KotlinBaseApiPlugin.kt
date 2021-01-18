/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptionsImpl
import org.jetbrains.kotlin.gradle.internal.KaptGenerateStubsTask
import org.jetbrains.kotlin.gradle.internal.KaptWithoutKotlincTask
import org.jetbrains.kotlin.gradle.internal.KotlinJvmCompilerArgumentsContributor
import org.jetbrains.kotlin.gradle.tasks.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinTasksProvider
import org.jetbrains.kotlin.gradle.tasks.configuration.KaptGenerateStubsConfig
import org.jetbrains.kotlin.gradle.tasks.configuration.KaptWithoutKotlincConfig
import org.jetbrains.kotlin.gradle.tasks.configuration.KotlinCompileConfig
import javax.inject.Inject

/** Plugin that can be used by third-party plugins to create Kotlin-specific DSL and tasks (compilation and KAPT). */
abstract class KotlinBaseApiPlugin : KotlinBasePlugin(), KotlinJvmFactory {

    override val pluginVersion = getKotlinPluginVersion(log)

    private lateinit var myProject: Project
    private val taskCreator = KotlinTasksProvider()

    override fun apply(project: Project) {
        super.apply(project)
        myProject = project
        setupAttributeMatchingStrategy(project, isKotlinGranularMetadata = false)
    }

    override fun addCompilerPluginDependency(project: Project, dependency: Any) {
        project.dependencies.add(PLUGIN_CLASSPATH_CONFIGURATION_NAME, dependency)
    }

    override fun createKotlinJvmDsl(factory: (Class<out KotlinJvmOptions>) -> KotlinJvmOptions): KotlinJvmOptions {
        return KotlinJvmOptionsImpl()
    }

    private val kotlinProjectExtension by lazy {
        myProject.objects.newInstance(KotlinProjectExtension::class.java, myProject)
    }

    private val kaptExtension by lazy {
        myProject.objects.newInstance(KaptExtension::class.java)
    }

    override fun createKotlinProjectExtension(factory: (Class<out KotlinTopLevelExtensionConfig>) -> KotlinTopLevelExtensionConfig): KotlinTopLevelExtensionConfig {
        return kotlinProjectExtension
    }

    override fun createKaptExtension(factory: (Class<out KaptExtensionApi>) -> KaptExtensionApi): KaptExtensionApi {
        return kaptExtension
    }

    override fun createKotlinCompileTask(taskName: String, action: (TaskProvider<out Task>, KotlinCompileOptions) -> Unit) {

        taskCreator.registerKotlinJVMTask(myProject, KotlinJvmOptionsImpl(), taskName) { taskProvider ->
            val taskConfiguration = myProject.objects.newInstance(KotlinCompileOptionsImpl::class.java, taskProvider)
            val internalTaskConfiguration = KotlinCompileConfig(myProject, taskConfiguration, kotlinProjectExtension)

            action(taskProvider, taskConfiguration)

            internalTaskConfiguration
        }
    }

    override fun createKaptGenerateStubsTask(taskName: String, action: (TaskProvider<out Task>, KaptGenerateStubsOptions) -> Unit) {
        val taskProvider = myProject.registerTask(taskName, KaptGenerateStubsTask::class.java, emptyList())

        val taskConfiguration = myProject.objects.newInstance(KaptGenerateStubsOptionsImpl::class.java, taskProvider)
        KaptGenerateStubsConfig(
            myProject,
            taskConfiguration,
            kotlinProjectExtension,
            kaptExtension
        ).configureTask(taskProvider)
        action(taskProvider, taskConfiguration)
    }

    override fun createKaptTask(taskName: String, action: (TaskProvider<out Task>, KaptOptions) -> Unit) {
        val taskProvider = myProject.registerTask(taskName, KaptWithoutKotlincTask::class.java, emptyList())

        val taskConfiguration = myProject.objects.newInstance(KaptOptionsImpl::class.java, taskProvider)
        KaptWithoutKotlincConfig(myProject, taskConfiguration, kaptExtension, kotlinProjectExtension).configureTask(taskProvider)
        action(taskProvider, taskConfiguration)
    }
}

internal abstract class KotlinCompileOptionsImpl @Inject constructor(
    val taskProvider: TaskProvider<out KotlinCompile>
) : KotlinCompileOptions {

    override val taskName: String = taskProvider.name

    override val taskBuildDirectory: DirectoryProperty by lazy {
        // use task property directly so that this property carries dependency information
        taskProvider.get().taskBuildDirectory
    }

    val taskBuildDirectoryLazy by lazy {
        // use task property directly so that this property carries dependency information
        taskProvider.flatMap  { it.taskBuildDirectory }
    }

    override val destinationDir: DirectoryProperty by lazy {
        // use task property directly so that this property carries dependency information
        taskProvider.get().destinationDirectory
    }

    val destinationDirLazy by lazy {
        // use task property directly so that this property carries dependency information
        taskProvider.flatMap { it.destinationDirectory }
    }
}

internal abstract class KaptGenerateStubsOptionsImpl @Inject constructor(
    taskProvider: TaskProvider<out KaptGenerateStubsTask>
) : KotlinCompileOptionsImpl(taskProvider), KaptGenerateStubsOptions {

    override val stubsDir: DirectoryProperty by lazy {
        // use task property directly so that this property carries dependency information
        taskProvider.get().stubsDir
    }

    val stubsDirLazy by lazy {
        // use task property directly so that this property carries dependency information
        taskProvider.flatMap { it.stubsDir }
    }

    val compileKotlinArgumentsContributor by lazy {
        taskProvider.map {
            KotlinJvmCompilerArgumentsContributor(KotlinJvmCompilerArgumentsProvider(it))
        }
    }
}

internal abstract class KaptOptionsImpl @Inject constructor(
    val taskProvider: TaskProvider<out KaptWithoutKotlincTask>
) : KaptOptions {

    override val taskName: String = taskProvider.name

    override val incAptCache: DirectoryProperty by lazy {
        taskProvider.get().incAptCache
    }

    val incAptCacheLazy by lazy {
        taskProvider.flatMap { it.incAptCache }
    }

    override val classesDir: DirectoryProperty by lazy {
        taskProvider.get().classesDir
    }

    val classesDirLazy by lazy {
        taskProvider.flatMap { it.classesDir }
    }


    override val destinationDir: DirectoryProperty by lazy {
        taskProvider.get().destinationDir
    }

    val destinationDirLazy by lazy {
        taskProvider.flatMap { it.destinationDir }
    }


    override val kotlinSourcesDestinationDir: DirectoryProperty by lazy {
        taskProvider.get().kotlinSourcesDestinationDir
    }

    val kotlinSourcesDestinationDirLazy by lazy {
        taskProvider.flatMap { it.kotlinSourcesDestinationDir }
    }


    override val stubsDir: DirectoryProperty by lazy {
        taskProvider.get().stubsDir
    }

    val stubsDirLazy by lazy {
        taskProvider.flatMap { it.stubsDir }
    }


    override val annotationProcessorOptionProviders: MutableList<Any> = mutableListOf()
}