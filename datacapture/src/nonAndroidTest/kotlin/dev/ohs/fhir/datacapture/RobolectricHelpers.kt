/*
 * Copyright 2025-2026 Open Health Stack Foundation
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
package dev.ohs.fhir.datacapture

import kotlin.reflect.KClass

actual abstract class Runner
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
actual annotation class RunWith(actual val value: KClass<out Runner>)
actual class AndroidJUnit4 : Runner()

actual fun printApplicationPackageName() {}

actual fun setupRobolectricActivity() {}

actual fun runLooperTasks() {}

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
actual fun runComposeUiTestWrapper(block: suspend androidx.compose.ui.test.ComposeUiTest.() -> Unit) {
  androidx.compose.ui.test.runComposeUiTest(block = block)
}

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
actual fun androidx.compose.ui.test.ComposeUiTest.waitUntilSynchronized(
  timeoutMillis: Long,
  condition: () -> Boolean
) {
  waitUntil(timeoutMillis = timeoutMillis, condition = condition)
}

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
actual fun androidx.compose.ui.test.ComposeUiTest.advanceTimeAndIdleLooper(millis: Long) {
  mainClock.advanceTimeBy(millis)
}

