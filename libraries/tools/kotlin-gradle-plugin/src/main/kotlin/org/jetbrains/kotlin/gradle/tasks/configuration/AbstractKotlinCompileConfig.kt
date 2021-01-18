/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks.configuration

import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.Coroutines
import org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension
import org.jetbrains.kotlin.gradle.dsl.topLevelExtension
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider
import org.jetbrains.kotlin.gradle.plugin.mpp.associateWithTransitiveClosure
import org.jetbrains.kotlin.gradle.plugin.mpp.pm20.KotlinCompilationData
import org.jetbrains.kotlin.gradle.plugin.sources.applyLanguageSettingsToKotlinOptions
import org.jetbrains.kotlin.gradle.report.BuildMetricsReporterService
import org.jetbrains.kotlin.gradle.tasks.*
import org.jetbrains.kotlin.gradle.utils.property
import org.jetbrains.kotlin.project.model.LanguageSettings
import java.util.concurrent.Callable

/**
 * Configuration for the base compile task, [org.jetbrains.kotlin.gradle.tasks.AbstractKotlinCompile].
 *
 * This contains all data necessary to configure the tasks, and should avoid exposing global state (project, extensions, other tasks)
 * as much as possible.
 */
internal abstract class AbstractKotlinCompileConfig<TASK : AbstractKotlinCompile<*>>(
    protected val project: Project,
    open val taskName: String,
    /**
     * Task outputs are exposed with external API directly, and in that scenario setting them from the config object introduces
     * cyclic dependency. When Config objects are created internally, this is not the case.
     */
    protected val configureTaskOutputs: Boolean
) {

    constructor(compilation: KotlinCompilationData<*>, taskName: String = compilation.compileKotlinTaskName) : this(compilation.project, taskName, true) {
        val ext = compilation.project.topLevelExtension
        languageSettings.set(providers.provider { compilation.languageSettings })

        friendPaths.from(Callable { compilation.friendPaths }).disallowChanges()
        if (compilation is KotlinCompilation<*>) {
            friendSourceSets.value(providers.provider { compilation.associateWithTransitiveClosure.map { it.name } }).disallowChanges()
            pluginClasspath.from(compilation.project.configurations.getByName(compilation.pluginConfigurationName)).disallowChanges()
        }
        moduleName.value(providers.provider { compilation.moduleName })
        sourceSetName.value(providers.provider { compilation.compilationPurpose }).disallowChanges()
        multiPlatformEnabled.value(providers.provider {
            compilation.project.plugins.any { it is KotlinPlatformPluginBase || it is KotlinMultiplatformPluginWrapper || it is KotlinPm20PluginWrapper }
        }).disallowChanges()
        coroutines.value(
            providers.provider { ext.experimental.coroutines ?: propertiesProvider.coroutines ?: Coroutines.DEFAULT }
        ).disallowChanges()
        taskBuildDirectory.value(project.layout.buildDirectory.dir("$KOTLIN_BUILD_DIR_NAME/$taskName"))
    }

    constructor(project: Project, configuration: KotlinCompileOptionsImpl, ext: KotlinTopLevelExtension) : this(
        project,
        configuration.taskName,
        false
    ) {
        useModuleDetection.value(configuration.useModuleDetection).disallowChanges()
        source.from(configuration.source).disallowChanges()
        pluginClasspath.from(configuration.pluginClasspath).disallowChanges()
        classpath.from(configuration.classpath).disallowChanges()
        friendPaths.from(configuration.friendPaths).disallowChanges()
        moduleName.value(configuration.moduleName).disallowChanges()
        sourceSetName.value(configuration.sourceSetName).disallowChanges()
        multiPlatformEnabled.value(configuration.multiPlatformEnabled).disallowChanges()
        coroutines.value(
            providers.provider { ext.experimental.coroutines ?: propertiesProvider.coroutines ?: Coroutines.DEFAULT }
        ).disallowChanges()
        destinationDir.value(configuration.destinationDirLazy).disallowChanges()
        taskBuildDirectory.value(configuration.taskBuildDirectoryLazy).disallowChanges()
    }

    init {
        val buildMetricReporter =
            BuildMetricsReporterService.registerIfAbsent(project, BuildMetricsReporterService.getStartParameters(project))

        buildMetricReporter?.also {
            BuildEventsListenerRegistryHolder.getInstance(project).listenerRegistry.onTaskCompletion(it)
            this.buildMetricsReporterService.set(buildMetricReporter)
        }
    }

    protected val objectFactory: ObjectFactory = project.objects
    protected val providers: ProviderFactory = project.providers
    protected val propertiesProvider: PropertiesProvider = PropertiesProvider(project)

    val languageSettings: Property<LanguageSettings> = objectFactory.property()

    //region Properties for task
    val source: ConfigurableFileCollection = objectFactory.fileCollection()

    val friendPaths: ConfigurableFileCollection = objectFactory.fileCollection()

    val friendSourceSets: ListProperty<String> = objectFactory.listProperty(String::class.java)

    val pluginClasspath: ConfigurableFileCollection = objectFactory.fileCollection()

    val classpath: ConfigurableFileCollection = objectFactory.fileCollection()

    val moduleName: Property<String> = objectFactory.property<String>()

    val sourceSetName: Property<String> = objectFactory.property<String>()

    val multiPlatformEnabled: Property<Boolean> = objectFactory.property<Boolean>()

    val taskBuildDirectory: DirectoryProperty = objectFactory.directoryProperty()

    val destinationDir: DirectoryProperty = objectFactory.directoryProperty()

    open val incremental: Property<Boolean> = objectFactory.property(false)

    val useModuleDetection: Property<Boolean> = objectFactory.property(false)

    val taskDescription: Property<String> = objectFactory.property()

    val coroutines: Property<Coroutines> = objectFactory.property()

    val kotlinDaemonJvmArguments: ListProperty<String>
        get() {
            val result = objectFactory.listProperty(String::class.java)
            propertiesProvider.kotlinDaemonJvmArgs?.let {
                result.value(it.split("\\s+".toRegex()))
            }
            return result
        }

    val compilerExecutionStrategy: Property<KotlinCompilerExecutionStrategy> =
        objectFactory.property<KotlinCompilerExecutionStrategy>().value(propertiesProvider.kotlinCompilerExecutionStrategy).also {
            it.disallowChanges()
        }


    internal val buildMetricsReporterService: Property<BuildMetricsReporterService?> = objectFactory.property()

    open fun configureTask(taskProvider: TaskProvider<TASK>) {
        project.runOnceAfterEvaluated("apply properties and language settings to ${taskProvider.name}") {
            taskProvider.configure {
                languageSettings.orNull?.let { langSettings ->
                    applyLanguageSettingsToKotlinOptions(
                        langSettings, (it as org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>).kotlinOptions
                    )
                }
            }
        }
        taskProvider.configure { task ->
            task.source(source)
            task.classpath = classpath
            task.friendPaths.from(friendPaths)
            task.friendSourceSets.set(friendSourceSets)
            task.pluginClasspath.from(pluginClasspath)
            task.moduleName.set(moduleName)
            task.sourceSetName.set(sourceSetName)
            task.multiPlatformEnabled.value(multiPlatformEnabled).disallowChanges()

            if (configureTaskOutputs) {
                task.taskBuildDirectory.value(taskBuildDirectory).disallowChanges()
                task.destinationDirectory.value(destinationDir).disallowChanges()
            }
            task.coroutines.set(coroutines)
            task.useModuleDetection.set(useModuleDetection)
            task.description = taskDescription.orNull
            task.incremental = incremental.get()
            task.kotlinDaemonJvmArguments.set(kotlinDaemonJvmArguments)
            task.compilerExecutionStrategy.set(compilerExecutionStrategy)

            task.localStateDirectories.from(task.taskBuildDirectory).disallowChanges()
            task.buildMetricsReporterService.value(buildMetricsReporterService)
        }
    }
}