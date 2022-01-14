/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.gradle.internal

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptionsImpl
import org.jetbrains.kotlin.gradle.tasks.FilteringSourceRootsContainer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.SourceRoots
import org.jetbrains.kotlin.gradle.utils.getValue
import org.jetbrains.kotlin.gradle.utils.isParentOf
import org.jetbrains.kotlin.incremental.classpathAsList
import org.jetbrains.kotlin.incremental.destinationAsFile
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class KaptGenerateStubsTask @Inject constructor(
    workerExecutor: WorkerExecutor
) : KotlinCompile(
    KotlinJvmOptionsImpl(),
    workerExecutor
) {

    @field:Transient
    override val sourceRootsContainer = FilteringSourceRootsContainer(objects, { isSourceRootAllowed(it) })

    @get:OutputDirectory
    abstract val stubsDir: DirectoryProperty

    @get:Internal
    abstract val excludedSourceDirs: ListProperty<File>

    @get:Internal
    abstract val javaSourceRoots: ConfigurableFileCollection

    @get:Internal
    abstract val kotlinSources: ConfigurableFileCollection

    @get:Internal("Not an input, just passed as kapt args. ")
    abstract val kaptClasspath: ConfigurableFileCollection

    /* Used as input as empty kapt classpath should not trigger stub generation, but a non-empty one should. */
    @Input
    fun getIfKaptClasspathIsPresent() = !kaptClasspath.isEmpty

    @get:Input
    abstract val verbose: Property<Boolean>

    /**
     * Changes in this additional sources will trigger stubs regeneration,
     * but the sources themselves will not be used to find kapt annotations and generate stubs.
     */
    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val additionalSources: ConfigurableFileCollection

    override fun source(vararg sources: Any): SourceTask {
        return super.source(sourceRootsContainer.add(sources))
    }

    override fun setSource(sources: Any) {
        super.setSource(sourceRootsContainer.set(sources))
    }

    internal fun isSourceRootAllowed(source: File): Boolean =
        !destinationDir.isParentOf(source) &&
                !stubsDir.asFile.get().isParentOf(source) &&
                excludedSourceDirs.get().none { it.isParentOf(source) }

    @get:Internal
    internal abstract val compileKotlinArgumentsContributor: Property<CompilerArgumentsContributor<K2JVMCompilerArguments>>

    override fun setupCompilerArgs(args: K2JVMCompilerArguments, defaultsOnly: Boolean, ignoreClasspathResolutionErrors: Boolean) {
        compileKotlinArgumentsContributor.get().contributeArguments(
            args, compilerArgumentsConfigurationFlags(
                defaultsOnly,
                ignoreClasspathResolutionErrors
            )
        )

        val pluginOptionsWithKapt = pluginOptions.withWrappedKaptOptions(withApClasspath = kaptClasspath)
        args.pluginOptions = (pluginOptionsWithKapt.arguments).toTypedArray()

        args.verbose = verbose.get()
        args.classpathAsList = this.classpath.filter { it.exists() }.toList()
        args.destinationAsFile = this.destinationDir
    }

    private val jvmSourceRoots by project.provider {
        SourceRoots.ForJvm(this.kotlinSources, javaSourceRoots)
    }

    override fun getSourceRoots(): SourceRoots.ForJvm = jvmSourceRoots
}
