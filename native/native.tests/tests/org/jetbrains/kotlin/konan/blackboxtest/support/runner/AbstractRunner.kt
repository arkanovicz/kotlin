/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.konan.blackboxtest.support.runner

import org.jetbrains.kotlin.konan.blackboxtest.support.LoggedData
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertEquals
import org.jetbrains.kotlin.test.services.JUnit5Assertions.assertTrue
import org.jetbrains.kotlin.test.services.JUnit5Assertions.fail
import kotlin.time.Duration

internal abstract class AbstractRunner<R> {
    protected abstract fun buildRun(): AbstractRun
    protected abstract fun buildResultHandler(runResult: RunResult.Completed): ResultHandler
    protected abstract fun getLoggedParameters(): LoggedData.TestRunParameters
    protected abstract fun handleUnexpectedFailure(t: Throwable): Nothing

    fun run(): R = try {
        val run = buildRun()

        val resultHandler = when (val runResult = run.run()) {
            is RunResult.TimeoutExceeded -> fail {
                LoggedData.TestRunTimeoutExceeded(getLoggedParameters(), runResult.timeout)
                    .withErrorMessageHeader("Timeout exceeded during test execution.")
            }
            is RunResult.Completed -> buildResultHandler(runResult)
        }

        resultHandler.handle()
    } catch (t: Throwable) {
        if (t is AssertionError)
            throw t
        else {
            // An unexpected failure.
            handleUnexpectedFailure(t)
        }
    }

    fun interface AbstractRun {
        fun run(): RunResult
    }

    sealed interface RunResult {
        data class Completed(val exitCode: Int, val duration: Duration, val stdOut: String, val stdErr: String) : RunResult
        data class TimeoutExceeded(val timeout: Duration) : RunResult
    }

    abstract inner class ResultHandler(protected val runResult: RunResult.Completed) {
        abstract fun getLoggedRun(): LoggedData
        abstract fun handle(): R

        protected inline fun <T> verifyExpectation(expected: T, actual: T, crossinline errorMessageHeader: () -> String) {
            assertEquals(expected, actual) { getLoggedRun().withErrorMessageHeader(errorMessageHeader()) }
        }

        protected inline fun verifyExpectation(shouldBeTrue: Boolean, crossinline errorMessageHeader: () -> String) {
            assertTrue(shouldBeTrue) { getLoggedRun().withErrorMessageHeader(errorMessageHeader()) }
        }
    }
}
