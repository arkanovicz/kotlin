/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest.support

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.konan.blackboxtest.support.runner.AbstractRunner
import java.io.File
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * A piece of information that makes sense for the user, and that can be logged to a file or
 * displayed inside an error message in user-friendly way.
 *
 * Handles all the necessary formatting right inside of [computeText]. Caches the resulting text to avoid re-computation.
 */
internal abstract class LoggedData {
    private val text: String by lazy { computeText() }
    protected abstract fun computeText(): String
    final override fun toString() = text

    fun withErrorMessageHeader(errorMessageHeader: String): String = buildString {
        appendLine(errorMessageHeader)
        appendLine()
        appendLine(this@LoggedData)
    }

    class CompilerParameters(
        private val compilerArgs: Array<String>,
        private val sourceModules: Collection<TestModule>
    ) : LoggedData() {
        private val testDataFiles: List<File>
            get() = sourceModules.asSequence()
                .filterIsInstance<TestModule.Exclusive>()
                .map { it.testCase.origin.testDataFile }
                .toMutableList()
                .apply { sort() }

        override fun computeText() = buildString {
            appendArguments("COMPILER ARGUMENTS:", listOf("\$\$kotlinc-native\$\$") + compilerArgs)
            appendLine()
            appendList("TEST DATA FILES (COMPILED TOGETHER):", testDataFiles)
        }
    }

    class CompilerCall(
        private val parameters: CompilerParameters,
        private val exitCode: ExitCode,
        private val compilerOutput: String,
        private val compilerOutputHasErrors: Boolean,
        private val duration: Duration
    ) : LoggedData() {
        override fun computeText(): String {
            val problems = listOfNotNull(
                "- Non-zero exit code".takeIf { exitCode != ExitCode.OK },
                "- Errors reported by the compiler".takeIf { compilerOutputHasErrors }
            )

            return buildString {
                if (problems.isNotEmpty()) {
                    appendLine("COMPILATION PROBLEMS:")
                    problems.forEach(::appendLine)
                    appendLine()
                }

                appendLine("COMPILER CALL:")
                appendLine("- Exit code: ${exitCode.code} (${exitCode.name})")
                appendDuration(duration)
                appendLine()
                appendLine("========== BEGIN: RAW COMPILER OUTPUT ==========")
                if (compilerOutput.isNotEmpty()) appendLine(compilerOutput.trimEnd())
                appendLine("========== END: RAW COMPILER OUTPUT ==========")
                appendLine()
                appendLine(parameters)
            }
        }
    }

    class CompilerCallUnexpectedFailure(parameters: CompilerParameters, throwable: Throwable) : UnexpectedFailure(parameters, throwable)

    class TestRunParameters(
        private val compilerCall: CompilerCall,
        private val origin: TestOrigin.SingleTestDataFile,
        private val runArgs: Iterable<String>,
        private val runParameters: List<TestRunParameter>
    ) : LoggedData() {
        override fun computeText() = buildString {
            appendLine("TEST DATA FILE:")
            appendLine(origin.testDataFile)
            appendLine()
            appendArguments("TEST RUN ARGUMENTS:", runArgs)
            appendLine()
            runParameters.get<TestRunParameter.WithInputData> {
                appendLine("INPUT DATA FILE:")
                appendLine(inputDataFile)
                appendLine()
            }
            runParameters.get<TestRunParameter.WithExpectedOutputData> {
                appendLine("EXPECTED OUTPUT DATA FILE:")
                appendLine(expectedOutputDataFile)
                appendLine()
            }
            appendLine(compilerCall)
        }
    }

    class TestRun(
        private val parameters: TestRunParameters,
        private val runResult: AbstractRunner.RunResult.Completed
    ) : LoggedData() {
        override fun computeText() = buildString {
            appendLine("TEST RUN:")
            appendLine("- Exit code: ${runResult.exitCode}")
            appendDuration(runResult.duration)
            appendLine()
            appendLine("========== BEGIN: TEST STDOUT ==========")
            if (runResult.stdOut.isNotEmpty()) appendLine(runResult.stdOut.trimEnd())
            appendLine("========== END: TEST STDOUT ==========")
            appendLine()
            appendLine("========== BEGIN: TEST STDERR ==========")
            if (runResult.stdErr.isNotEmpty()) appendLine(runResult.stdErr.trimEnd())
            appendLine("========== END: TEST STDERR ==========")
            appendLine()
            appendLine(parameters)
        }
    }

    class TestRunUnexpectedFailure(parameters: TestRunParameters, throwable: Throwable) : UnexpectedFailure(parameters, throwable)

    abstract class UnexpectedFailure(
        private val parameters: LoggedData,
        private val throwable: Throwable
    ) : LoggedData() {
        override fun computeText() = buildString {
            appendLine("ERROR MESSAGE:")
            appendLine("${throwable.message}")
            appendLine()
            appendLine("STACK TRACE:")
            appendLine(throwable.stackTraceToString().trimEnd())
            appendLine()
            appendLine(parameters)
        }
    }

    class TestRunTimeoutExceeded(parameters: TestRunParameters, timeout: Duration) : TimeoutExceeded(parameters, timeout)

    abstract class TimeoutExceeded(
        private val parameters: LoggedData,
        private val timeout: Duration
    ) : LoggedData() {
        override fun computeText() = buildString {
            appendLine("TIMED OUT:")
            appendLine("- Max permitted duration: $timeout")
            appendLine()
            appendLine(parameters)
        }
    }

    companion object {
        protected fun StringBuilder.appendList(header: String, list: Iterable<Any?>): StringBuilder {
            appendLine(header)
            list.forEach(::appendLine)
            return this
        }

        protected fun StringBuilder.appendArguments(header: String, args: Iterable<String>): StringBuilder {
            appendLine(header)

            fun String.sanitize() = if (startsWith("--ktest_") && substringBefore('=').endsWith("_filter")) "'$this'" else this

            var lastArgIsOptionWithoutEqualsSign = false
            args.forEachIndexed { index, arg ->
                val isOption = arg[0] == '-'
                val isSourceFile = !isOption && arg.substringAfterLast('.') == "kt"
                if (index > 0) {
                    if (isOption || isSourceFile || !lastArgIsOptionWithoutEqualsSign)
                        append(" \\\n")
                    else
                        append(' ')
                }
                lastArgIsOptionWithoutEqualsSign = isOption && '=' !in arg
                append(arg.sanitize())
            }

            appendLine()
            return this
        }

        protected fun StringBuilder.appendDuration(duration: Duration): StringBuilder =
            append("- Duration: ").appendLine(duration.toString(DurationUnit.SECONDS, 2))
    }
}
