/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest

import org.jetbrains.kotlin.konan.blackboxtest.support.lldb.*

import java.nio.file.Files
import java.nio.file.Paths

abstract class AbstractNativeShellTest {
    val directiveRegex = Regex("// (FILE|KONANC|SHELL):.*")
    fun runTest(testFileName: String) {
        val testFilePath = Paths.get(testFileName)
        var content = mutableListOf<String>()
        val directives = mutableListOf(Pair("// FILE: main.kt", content))

        val tmpdir = Files.createTempDirectory("debugger_test")
        tmpdir.toFile().deleteOnExit()

        Files.lines(testFilePath).forEach { line ->
            when {
                directiveRegex.matches(line) -> {
                    content = mutableListOf()
                    directives.add(Pair(line, content))
                }
                else -> content.add(line)
            }
        }

        directives.forEach { (directive, lines) ->
            when {
                directive.startsWith("// FILE:") -> {
                    val fileName = directive.split(':', limit = 2).last().trim()
                    val path = tmpdir.resolve(fileName)
                    Files.write(path, lines.joinToString(System.lineSeparator()).toByteArray())
                }
                directive.startsWith("// KONANC:") -> {
                    subprocess(
                        DistProperties.konanc,
                        *directive.split(':', limit = 2).last().trim().split(' ').toTypedArray()
                    ).thrownIfFailed()
                }
                directive.startsWith("// SHELL:") -> {
                    val parts = directive.split(':', limit = 2).last().trim().split(' ')
                    val output = subprocess(Paths.get(parts.first()), *parts.drop(1).toTypedArray(), workingDirectory = tmpdir.toFile())
                    val expected = Regex(".*" + lines.joinToString("\n") + ".*")
                    check(expected.matches(output.stdout))
                }
            }
        }
    }
}
