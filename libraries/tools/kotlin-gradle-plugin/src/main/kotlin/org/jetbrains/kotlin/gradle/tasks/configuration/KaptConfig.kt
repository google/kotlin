/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.tasks.configuration

import com.intellij.openapi.util.SystemInfo
import com.intellij.util.lang.JavaVersion
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.*
import org.gradle.api.tasks.TaskProvider
import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension
import org.jetbrains.kotlin.gradle.dsl.topLevelExtension
import org.jetbrains.kotlin.gradle.internal.*
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin.Companion.KAPT_SUBPLUGIN_ID
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin.Companion.classLoadersCacheSize
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin.Companion.disableClassloaderCacheForProcessors
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin.Companion.isIncludeCompileClasspath
import org.jetbrains.kotlin.gradle.internal.Kapt3GradleSubplugin.Companion.isIncrementalKapt
import org.jetbrains.kotlin.gradle.internal.kapt.incremental.CLASS_STRUCTURE_ARTIFACT_TYPE
import org.jetbrains.kotlin.gradle.internal.kapt.incremental.StructureTransformAction
import org.jetbrains.kotlin.gradle.internal.kapt.incremental.StructureTransformLegacyAction
import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.plugin.PropertiesProvider
import org.jetbrains.kotlin.gradle.report.ReportingSettings
import org.jetbrains.kotlin.gradle.tasks.CompilerPluginOptions
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilerExecutionStrategy
import org.jetbrains.kotlin.gradle.utils.isConfigurationCacheAvailable
import org.jetbrains.kotlin.gradle.utils.property
import java.util.concurrent.Callable

// finish migration for KAPT tasks, and try wiring up with AGP
open class KaptConfig<TASK : KaptTask>(
    protected val project: Project,
    protected val ext: KaptExtension,
    val taskName: String,
    /**
     * Task outputs are exposed with external API directly, and in that scenario setting them from the config object introduces
     * cyclic dependency. When Config objects are created internally, this is not the case.
     */
    protected val configureTaskOutputs: Boolean
) {
    protected val objectFactory: ObjectFactory = project.objects
    protected val providerFactory: ProviderFactory = project.providers

    internal constructor(taskName: String, kotlinCompileTask: KotlinCompile, ext: KaptExtension) : this(
        kotlinCompileTask.project, ext, taskName, true
    ) {
        classpath.from(kotlinCompileTask.classpath)
        compiledSources.from(kotlinCompileTask.destinationDirectory, Callable { kotlinCompileTask.javaOutputDir.takeIf { it.isPresent } })
            .disallowChanges()
        sourceSetName.value(kotlinCompileTask.sourceSetName).disallowChanges()
        val kotlinSourceRoots = providerFactory.provider { kotlinCompileTask.sourceRootsContainer.sourceRoots }
        source.from(objectFactory.fileCollection().from(kotlinSourceRoots, stubsDir).asFileTree.matching { it.include("**/*.java") })
            .disallowChanges()
        verbose.set(KaptTask.queryKaptVerboseProperty(project))
        compilerClasspath.from(providerFactory.provider { kotlinCompileTask.defaultCompilerClasspath })
    }

    internal constructor(project: Project, configuration: KaptOptionsImpl, ext: KaptExtension) : this(project, ext, configuration.taskName, false) {
        this.kaptClasspath.from(configuration.kaptClasspath)
        this.kaptExternalClasspath.from(configuration.kaptExternalClasspath)
        this.kaptClasspathConfigurationNames.set(configuration.kaptClasspathConfigurationNames)
        this.defaultJavaSourceCompatibility.set(configuration.defaultJavaSourceCompatibility)

        this.annotationProcessorOptionProviders.clear()
        this.annotationProcessorOptionProviders.addAll(configuration.annotationProcessorOptionProviders)

        this.compiledSources.from(configuration.compiledSources)
        this.classpath.from(configuration.classpath)
        this.source.from(configuration.source)
        this.sourceSetName.value(configuration.sourceSetName).disallowChanges()
        this.includeCompileClasspath.value(configuration.includeCompileClasspath).disallowChanges()

        // task properties are exposed directly via API object, make sure we do not set them to avoid cyclic dependency
        this.incAptCache.value(configuration.incAptCacheLazy).disallowChanges()
        this.classesDir.value(configuration.classesDirLazy).disallowChanges()
        this.stubsDir.value(configuration.stubsDirLazy).disallowChanges()
        this.destinationDir.value(configuration.destinationDirLazy).disallowChanges()
        this.kotlinSourcesDestinationDir.value(configuration.kotlinSourcesDestinationDirLazy).disallowChanges()
    }

    val classpathStructure: ConfigurableFileCollection = objectFactory.fileCollection()

    init {
        if (project.isIncrementalKapt()) {
            maybeRegisterTransform(project)

            val classStructureConfiguration = project.configurations.detachedConfiguration()

            // Wrap the `kotlinCompile.classpath` into a file collection, so that, if the classpath is represented by a configuration,
            // the configuration is not extended (via extendsFrom, which normally happens when one configuration is _added_ into another)
            // but is instead included as the (lazily) resolved files. This is needed because the class structure configuration doesn't have
            // the attributes that are potentially needed to resolve dependencies on MPP modules, and the classpath configuration does.
            classStructureConfiguration.dependencies.add(project.dependencies.create(project.files(project.provider { this.classpath })))
            classpathStructure.from(classStructureConfiguration.incoming.artifactView { viewConfig ->
                viewConfig.attributes.attribute(artifactType, CLASS_STRUCTURE_ARTIFACT_TYPE)
            }.files)
        }
    }

    private fun maybeRegisterTransform(project: Project) {
        if (!project.extensions.extraProperties.has("KaptStructureTransformAdded")) {
            val transformActionClass =
                if (GradleVersion.current() >= GradleVersion.version("5.4"))
                    StructureTransformAction::class.java
                else
                    StructureTransformLegacyAction::class.java
            project.dependencies.registerTransform(transformActionClass) { transformSpec ->
                transformSpec.from.attribute(artifactType, "jar")
                transformSpec.to.attribute(artifactType, CLASS_STRUCTURE_ARTIFACT_TYPE)
            }

            project.dependencies.registerTransform(transformActionClass) { transformSpec ->
                transformSpec.from.attribute(artifactType, "directory")
                transformSpec.to.attribute(artifactType, CLASS_STRUCTURE_ARTIFACT_TYPE)
            }

            project.extensions.extraProperties["KaptStructureTransformAdded"] = true
        }
    }

    val kaptClasspath: ConfigurableFileCollection = objectFactory.fileCollection()
    val kaptExternalClasspath: ConfigurableFileCollection = objectFactory.fileCollection()
    val kaptClasspathConfigurationNames: ListProperty<String> = objectFactory.listProperty(String::class.java)

    val incAptCache: DirectoryProperty = objectFactory.directoryProperty()

    val classesDir: DirectoryProperty = objectFactory.directoryProperty()

    val destinationDir: DirectoryProperty = objectFactory.directoryProperty()

    val kotlinSourcesDestinationDir: DirectoryProperty = objectFactory.directoryProperty()

    val annotationProcessorOptionProviders: MutableList<Any> = mutableListOf()

    val stubsDir: DirectoryProperty = objectFactory.directoryProperty()

    val compiledSources: ConfigurableFileCollection = objectFactory.fileCollection()

    val classpath: ConfigurableFileCollection = objectFactory.fileCollection()

    val sourceSetName: Property<String> = objectFactory.property()

    val source: ConfigurableFileCollection = objectFactory.fileCollection()

    val verbose: Property<Boolean> = objectFactory.property<Boolean>().value(KaptTask.queryKaptVerboseProperty(project))

    val incremental: Property<Boolean> = objectFactory.property(project.isIncrementalKapt())

    val useBuildCache: Property<Boolean> = objectFactory.property(ext.useBuildCache)

    val compilerClasspath: ConfigurableFileCollection = objectFactory.fileCollection()

    val includeCompileClasspath: Property<Boolean> =
        objectFactory.property(ext.includeCompileClasspath ?: project.isIncludeCompileClasspath())

    val javacOptions: MapProperty<String, String> = objectFactory.mapProperty(String::class.java, String::class.java)
        .value(providerFactory.provider { getJavaOptions() })

    val defaultJavaSourceCompatibility: Property<String> = objectFactory.property()

    internal val pluginOptions: ListProperty<CompilerPluginOptions> = objectFactory.listProperty(CompilerPluginOptions::class.java)

    // Adds the specified options to this configuration while keeping everything lazy.
    fun addSubpluginOptions(pluginId: String, options: Provider<List<SubpluginOption>>) {
        addSubpluginOptions(options.map { mapOf(pluginId to it) })
    }

    // Adds the specified options to this configuration while keeping everythign lazy.
    fun addSubpluginOptions(options: Provider<Map<String, List<SubpluginOption>>>) {
        pluginOptions.add(options.map { subpluginOptions ->
            val compilerPluginOptions = CompilerPluginOptions()
            subpluginOptions.forEach { (id, pluginOptions) ->
                compilerPluginOptions.subpluginOptionsByPluginId.put(id, pluginOptions.toMutableList())
            }
            compilerPluginOptions
        })
    }

    private fun getJavaOptions(): Map<String, String> {
        return ext.getJavacOptions().toMutableMap().also { result ->
            if ("-source" in result || "--source" in result || "--release" in result) return@also

            if (defaultJavaSourceCompatibility.isPresent) {
                val atLeast12Java =
                    if (isConfigurationCacheAvailable(project.gradle)) {
                        val currentJavaVersion =
                            JavaVersion.parse(project.providers.systemProperty("java.version").forUseAtConfigurationTime().get())
                        currentJavaVersion.feature >= 12
                    } else {
                        SystemInfo.isJavaVersionAtLeast(12, 0, 0)
                    }
                val sourceOptionKey = if (atLeast12Java) {
                    "--source"
                } else {
                    "-source"
                }
                result[sourceOptionKey] = defaultJavaSourceCompatibility.get()
            }
        }
    }

    open fun configureTask(taskProvider: TaskProvider<TASK>) {
        taskProvider.configure { task ->
            registerSubpluginOptions(pluginOptions, task.pluginOptions, task)

            task.classpath.from(classpath).disallowChanges()
            task.compiledSources.from(compiledSources).disallowChanges()
            task.sourceSetName.value(sourceSetName).disallowChanges()
            task.source.from(source.filter { task.isRootAllowed(it) }).disallowChanges()
            task.verbose.set(verbose)

            task.isIncremental = incremental.get()
            if (task.isIncremental && configureTaskOutputs) {
                task.incAptCache.set(incAptCache)
            }
            if (configureTaskOutputs) {
                task.classesDir.set(classesDir)
                task.kotlinSourcesDestinationDir.set(kotlinSourcesDestinationDir)
                task.stubsDir.set(stubsDir)
                task.destinationDir.set(destinationDir)
            }

            task.annotationProcessorOptionProviders.addAll(annotationProcessorOptionProviders)
            task.useBuildCache = useBuildCache.get()

            task.kaptClasspath.setFrom(kaptClasspath)
            task.kaptExternalClasspath.setFrom(kaptExternalClasspath)
            task.kaptClasspathConfigurationNames.value(kaptClasspathConfigurationNames)
            task.includeCompileClasspath.set(includeCompileClasspath)
            task.classpathStructure.from(classpathStructure)

            task.compilerClasspath.from(compilerClasspath)

            task.localStateDirectories.from(Callable { task.incAptCache.orNull })
            task.onlyIf {
                it as KaptTask
                it.includeCompileClasspath.get() || !it.kaptClasspath.isEmpty
            }

            pluginOptions.get().forEach {
                it.subpluginOptionsByPluginId.forEach { (id, options) ->
                    options.forEach { option ->
                        task.pluginOptions.addPluginArgument(id, option)
                    }
                }
            }
        }
    }
}

class KaptWithoutKotlincConfig : KaptConfig<KaptWithoutKotlincTask> {

    constructor(taskName: String, kotlinCompileTask: KotlinCompile, ext: KaptExtension) : super(taskName, kotlinCompileTask, ext) {
        initKaptWorkersConfiguration(project.topLevelExtension)

        addJdkClassesToClasspath.set(project.providers.provider { project.plugins.none { it is KotlinAndroidPluginWrapper } })
        kaptJars.from(project.configurations.getByName(Kapt3GradleSubplugin.KAPT_WORKER_DEPENDENCIES_CONFIGURATION_NAME))
    }


    internal constructor(project: Project, configuration: KaptOptionsImpl, ext: KaptExtension, topLevelExtension: KotlinTopLevelExtension) : super(
        project,
        configuration,
        ext
    ) {
        addJdkClassesToClasspath.set(configuration.addJdkClassesToClasspath)
        kaptJars.from(configuration.kaptJars)

        val dslApOptions = project.provider {
            ext.getAdditionalArguments(project, null, null).toList()
                .map { SubpluginOption(it.first, it.second) } +
                    FilesSubpluginOption(
                        Kapt3GradleSubplugin.KAPT_KOTLIN_GENERATED,
                        project.files(configuration.kotlinSourcesDestinationDir)
                    )
        }

        addSubpluginOptions(KAPT_SUBPLUGIN_ID, dslApOptions)
    }

    private fun initKaptWorkersConfiguration(kotlinExt: KotlinTopLevelExtension) {
        project.configurations.findByName(Kapt3GradleSubplugin.KAPT_WORKER_DEPENDENCIES_CONFIGURATION_NAME)
            ?: project.configurations.create(Kapt3GradleSubplugin.KAPT_WORKER_DEPENDENCIES_CONFIGURATION_NAME).apply {
                val kaptDependency = "org.jetbrains.kotlin:kotlin-annotation-processing-gradle:${project.getKotlinPluginVersion()}"
                dependencies.add(project.dependencies.create(kaptDependency))
                dependencies.add(
                    project.kotlinDependency(
                        "kotlin-stdlib",
                        kotlinExt.coreLibrariesVersion
                    )
                )
            }
    }

    val addJdkClassesToClasspath: Property<Boolean> = objectFactory.property()
    val kaptJars: ConfigurableFileCollection = objectFactory.fileCollection()

    val mapDiagnosticLocations: Property<Boolean> =
        objectFactory.property(providerFactory.provider { ext.mapDiagnosticLocations })

    val annotationProcessorFqNames: ListProperty<String> = objectFactory.listProperty(String::class.java)
        .value(providerFactory.provider { ext.processors.split(',').filter { it.isNotEmpty() } })

    val disableClassloaderCacheForProcessors: SetProperty<String> =
        objectFactory.setProperty(String::class.java).value(project.disableClassloaderCacheForProcessors())

    val classLoadersCacheSize: Property<Int> = objectFactory.property(project.classLoadersCacheSize())

    override fun configureTask(taskProvider: TaskProvider<KaptWithoutKotlincTask>) {
        super.configureTask(taskProvider)

        taskProvider.configure { task ->
            task.addJdkClassesToClasspath.value(addJdkClassesToClasspath).disallowChanges()
            task.kaptJars.from(kaptJars).disallowChanges()

            task.mapDiagnosticLocations = mapDiagnosticLocations.get()
            task.javacOptions.set(javacOptions)
            task.annotationProcessorFqNames.set(annotationProcessorFqNames)
            task.disableClassloaderCacheForProcessors = disableClassloaderCacheForProcessors.get()
            task.classLoadersCacheSize = classLoadersCacheSize.get()
        }
    }
}

class KaptWithKotlincConfig(taskName: String, kotlinCompileTask: KotlinCompile, ext: KaptExtension) :
    KaptConfig<KaptWithKotlincTask>(taskName, kotlinCompileTask, ext) {

    init {
        if (project.isIncrementalKapt()) {
            addSubpluginOptions(KAPT_SUBPLUGIN_ID, incAptCache.map {
                listOf(SubpluginOption("incrementalCache", lazy { it.asFile.absolutePath }))
            })
        }
    }

    private val propertiesProvider = PropertiesProvider(project)

    val pluginClasspath: ConfigurableFileCollection = objectFactory.fileCollection().from(kotlinCompileTask.pluginClasspath)

    val javaPackagePrefix: Property<String> = objectFactory.property(providerFactory.provider { kotlinCompileTask.javaPackagePrefix })

    val reportingSettings: Property<ReportingSettings> =
        objectFactory.property(providerFactory.provider { kotlinCompileTask.reportingSettings() })

    internal val compileKotlinArgumentsContributor: Property<CompilerArgumentsContributor<K2JVMCompilerArguments>> =
        objectFactory.property(providerFactory.provider { kotlinCompileTask.compilerArgumentsContributor })

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

    override fun configureTask(taskProvider: TaskProvider<KaptWithKotlincTask>) {
        super.configureTask(taskProvider)
        taskProvider.configure { task ->
            task.pluginClasspath.from(pluginClasspath)
            task.compileKotlinArgumentsContributor.set(compileKotlinArgumentsContributor)
            task.javaPackagePrefix.set(javaPackagePrefix)
            task.reportingSettings.set(reportingSettings)
            task.kotlinDaemonJvmArguments.value(kotlinDaemonJvmArguments).disallowChanges()
            task.compilerExecutionStrategy.value(compilerExecutionStrategy).disallowChanges()
        }
    }
}

private val artifactType = Attribute.of("artifactType", String::class.java)