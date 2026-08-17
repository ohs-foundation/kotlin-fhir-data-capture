/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.compose.ui.test

@OptIn(ExperimentalTestApi::class)
fun runComposeUiTest(
    effectContext: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,
    runTestContext: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,
    testTimeout: kotlin.time.Duration = kotlin.time.Duration.INFINITE,
    block: suspend ComposeUiTest.() -> Unit
) {
    dev.ohs.fhir.datacapture.runComposeUiTestWrapper {
        try {
            block()
        } finally {
            try {
                setContent { }
            } catch (e: Throwable) {
                // Ignore
            }
            try {
                kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
            } catch (e: Throwable) {
                // Ignore if coroutineContext or Job is not available
            }
            try {
                val testDispatcher = kotlinx.coroutines.Dispatchers.Main as? kotlinx.coroutines.test.TestDispatcher
                testDispatcher?.scheduler?.runCurrent()
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.waitUntil(timeoutMillis: Long = 5000, condition: () -> Boolean) {
    val start = System.currentTimeMillis()
    val wasAutoAdvance = mainClock.autoAdvance
    mainClock.autoAdvance = false
    try {
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMillis) {
                throw ComposeTimeoutException("Condition still not satisfied after $timeoutMillis ms")
            }
            dev.ohs.fhir.datacapture.runLooperTasks()
            try {
                mainClock.advanceTimeByFrame()
            } catch (e: Throwable) {
                // Ignore
            }
            try {
                Thread.sleep(10)
            } catch (e: InterruptedException) {
                break
            }
        }
    } finally {
        mainClock.autoAdvance = wasAutoAdvance
    }
}
