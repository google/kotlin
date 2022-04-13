/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle

import org.gradle.api.internal.project.ProjectInternal
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.mpp.buildProject
import org.jetbrains.kotlin.gradle.plugin.KotlinBaseApiPlugin
import org.jetbrains.kotlin.gradle.plugin.KotlinJvmFactory
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinCompileApiTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private lateinit var project: ProjectInternal
    private lateinit var plugin: KotlinJvmFactory
    private lateinit var kotlinJvmCompileTask: KotlinJvmCompile
    private lateinit var kotlinCompileTask: KotlinCompile

    companion object {
        private const val TASK_NAME = "kotlinCompile"
    }

    @Before
    fun setUpProject() {
        project = buildProject {}
        plugin = project.plugins.apply(KotlinBaseApiPlugin::class.java)
        plugin.registerKotlinJvmCompileTask(TASK_NAME).configure { task ->
            kotlinJvmCompileTask = task
        }
        kotlinCompileTask = project.tasks.getByName(TASK_NAME) as KotlinCompile
    }

    @Test
    fun testTaskName() {
        assertEquals(TASK_NAME, kotlinCompileTask.name)
    }

    @Test
    fun testSources() {
        val sourcePath = tmpDir.newFolder().resolve("foo.kt").also {
            it.createNewFile()
        }
        kotlinJvmCompileTask.setSource(sourcePath)
        assertEquals(setOf(sourcePath), kotlinCompileTask.sources.files)

        val sourcesDir = tmpDir.newFolder().also {
            it.resolve("a.kt").createNewFile()
            it.resolve("b.kt").createNewFile()
        }
        kotlinJvmCompileTask.setSource(sourcePath, sourcesDir)
        assertEquals(
            setOf(sourcePath, sourcesDir.resolve("a.kt"), sourcesDir.resolve("b.kt")),
            kotlinCompileTask.sources.files
        )
    }

    @Test
    fun testFriendPaths() {
        val friendPath = tmpDir.newFolder()
        kotlinJvmCompileTask.friendPaths.from(friendPath)
        assertEquals(setOf(friendPath), kotlinCompileTask.friendPaths.files)
    }

    @Test
    fun testClasspath() {
        val classpathEntries = setOf(tmpDir.newFolder(), tmpDir.newFolder())
        kotlinJvmCompileTask.libraries.from(classpathEntries)
        assertEquals(classpathEntries, kotlinCompileTask.libraries.files)
    }

    @Test
    fun testPluginClasspath() {
        val pluginDependency = tmpDir.newFile()
        plugin.addCompilerPluginDependency(project.provider { project.files(pluginDependency) })

        val anotherCompilerPlugin = tmpDir.newFile()
        kotlinJvmCompileTask.pluginClasspath.from(plugin.getCompilerPlugins(), anotherCompilerPlugin)
        assertEquals(setOf(pluginDependency, anotherCompilerPlugin), kotlinCompileTask.pluginClasspath.files)
    }

    @Test
    fun testModuleName() {
        kotlinJvmCompileTask.moduleName.set("foo")
        assertEquals("foo", kotlinCompileTask.moduleName.get())
    }

    @Test
    fun testSourceSetName() {
        kotlinJvmCompileTask.sourceSetName.set("sourceSetFoo")
        assertEquals("sourceSetFoo", kotlinCompileTask.sourceSetName.get())
    }

    @Test
    fun testTaskBuildDirectory() {
        val taskBuildDir = tmpDir.newFolder()
        kotlinJvmCompileTask.destinationDirectory.fileValue(taskBuildDir)
        assertEquals(taskBuildDir, kotlinCompileTask.destinationDirectory.get().asFile)
    }

    @Test
    fun testDestinationDirectory() {
        val destinationDir = tmpDir.newFolder()
        kotlinJvmCompileTask.destinationDirectory.fileValue(destinationDir)
        assertEquals(destinationDir, kotlinCompileTask.destinationDirectory.get().asFile)
    }

    @Test
    fun testMultiplatform() {
        kotlinJvmCompileTask.multiPlatformEnabled.set(true)
        assertEquals(true, kotlinCompileTask.multiPlatformEnabled.get())

        kotlinJvmCompileTask.multiPlatformEnabled.set(false)
        assertEquals(false, kotlinCompileTask.multiPlatformEnabled.get())
    }

    @Test
    fun testModuleDetection() {
        kotlinJvmCompileTask.useModuleDetection.set(true)
        assertEquals(true, kotlinCompileTask.useModuleDetection.get())

        kotlinJvmCompileTask.useModuleDetection.set(false)
        assertEquals(false, kotlinCompileTask.useModuleDetection.get())
    }


    @Test
    fun testParentKotlinOptions() {
        val parentOptions = plugin.createKotlinJvmOptions()
        parentOptions.moduleName = "foo"
        parentOptions.javaParameters = true
        parentOptions.languageVersion = "lang_version"
        kotlinJvmCompileTask.parentKotlinOptions.set(parentOptions)

        kotlinCompileTask.parentKotlinOptions.get().let {
            assertEquals(parentOptions.moduleName, it.moduleName)
            assertEquals(parentOptions.javaParameters, it.javaParameters)
            assertEquals(parentOptions.languageVersion, it.languageVersion)
        }
    }

    @Test
    fun testTopLevelExtension() {
        plugin.kotlinExtension.explicitApi = ExplicitApiMode.Strict
        project.evaluate()
        assertTrue(ExplicitApiMode.Strict.toCompilerArg() in kotlinCompileTask.kotlinOptions.freeCompilerArgs)
    }
}