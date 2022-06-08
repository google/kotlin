/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest

import org.jetbrains.kotlin.konan.blackboxtest.support.lldb.*

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.absolute

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
                        DistProperties.konanc.absolute(),
                        *directive.split(':', limit = 2).last().trim().split(' ').toTypedArray(),
                        workingDirectory = tmpdir.toFile()
                    ).thrownIfFailed()
                }
                directive.startsWith("// SHELL:") -> {
                    val parts = directive.split(':', limit = 2).last().trim().split(' ')
                    val command = parts.first()
                    var args = parts.drop(1).toTypedArray()
                    if (command == "lldb")
                        args = arrayOf("-o", "command script import \"${DistProperties.lldbPrettyPrinters.absolute()}\"") + args
                    val output = subprocess(
                        Paths.get(command),
                        *args,
                        workingDirectory = tmpdir.toFile()
                    )
                    checkMatch(lines.joinToString("\n"), output.stdout)
//                    val expected = Regex(".*" + lines.joinToString("\n") + ".*", RegexOption.DOT_MATCHES_ALL)
//                    check(expected.matches(output.stdout)) {
//                        """Output of $command:
//                             |${output.stdout}
//                             |did not match pattern:
//                             |$expected
//                             """.trimMargin("|")
//                    }
                }
            }
        }
    }

    companion object {
        private val wildcards = Regex("""\[\.\.]""")
        private val whitespace = Regex("""\s+""")

        // TODO: worst case (M * N), but this should never be a bottleneck here
        fun <T> List<T>.indexOf(pattern: List<T>, from: Int = 0) =
            (from..size - pattern.size).firstOrNull { i ->
                pattern.withIndex().all { (j, elm) -> elm == get(i + j) }
            } ?: -1

        fun String.tokens() = split(whitespace).filter { it.isNotEmpty() }

        fun checkMatch(_pattern: String, _actual: String) {
            val pattern = _pattern.trim()
            val actual = _actual.trim()
            val patternBlocks = pattern.split(wildcards)
            if (patternBlocks.size == 1) {
                check(pattern == actual)
            } else {
                val first = patternBlocks.first()
                val last = patternBlocks.last()
                check(first.length + last.length <= actual.length) {
                    """Output too short
                        |Expected:
                        |$pattern
                        |
                        |Actual:
                        |$actual
                    """.trimMargin()
                }
                check(actual.startsWith(first)) {
                    """Pattern mismatch
                        |Expected:
                        |$first [..]
                        |
                        |Actual:
                        |${actual.substring(0, first.length)} [..]
                    """.trimMargin()
                }
                check(actual.endsWith(last)) {
                    """Pattern mismatch
                        |Expected:
                        |[..] $last
                        |
                        |Actual:
                        |[..] ${actual.substring(actual.length - last.length, actual.length)}
                    """.trimMargin()
                }
                var from = first.length
                for (pat in patternBlocks.subList(1, patternBlocks.size - 1)) {
                    from = actual.indexOf(pat).also {
                        check(it >= 0) {
                            """Pattern mismatch
                            |Expected:
                            |$pattern
                            |
                            |Actual:
                            |$actual
                            """.trimMargin()
                        }
                    } + pat.length
                }
                check(from + last.length <= actual.length) {
                    """Pattern mismatch
                        |Expected:
                        |$pattern
                        |
                        |Actual:
                        |$actual
                    """.trimMargin()
                }
            }
        }
    }
}
