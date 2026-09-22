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
package dev.ohs.fhir.datacapture.views.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.snapshots.Snapshot
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/**
 * Tests the debounce that [EditTextFieldState] applies to text input.
 *
 * The debounce is covered here rather than in the text input factory UI tests because
 * `runComposeUiTest`'s virtual clock does not resume a real `delay()` on non-Android targets
 * (https://github.com/JetBrains/compose-multiplatform/issues/4805). [EditTextFieldState] takes its
 * own [kotlinx.coroutines.CoroutineScope], so the debounce can be driven by
 * `kotlinx-coroutines-test` virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditTextFieldStateTest {

  private val debounce = 500.milliseconds

  @Test
  fun shouldHandleOnlyTheLatestValueWhenInputArrivesWithinTheDebounceWindow() = runTest {
    val handled = mutableListOf<String>()
    val state = editTextFieldState(debounce, handled)

    state.type("1")
    advanceTimeBy(100.milliseconds)
    state.type("12")
    advanceTimeBy(100.milliseconds)
    state.type("123")
    advanceTimeBy(debounce + 100.milliseconds)
    runCurrent()

    handled shouldContainExactly listOf("123")
  }

  @Test
  fun shouldNotHandleInputUntilTheDebounceWindowElapses() = runTest {
    val handled = mutableListOf<String>()
    val state = editTextFieldState(debounce, handled)

    state.type("1")
    advanceTimeBy(debounce - 100.milliseconds)
    runCurrent()

    handled.shouldBeEmpty()

    advanceTimeBy(200.milliseconds)
    runCurrent()

    handled shouldContainExactly listOf("1")
  }

  @Test
  fun shouldHandleEveryValueWhenInputIsSeparatedByMoreThanTheDebounceWindow() = runTest {
    val handled = mutableListOf<String>()
    val state = editTextFieldState(debounce, handled)

    state.type("1")
    advanceTimeBy(debounce + 100.milliseconds)
    state.type("2")
    advanceTimeBy(debounce + 100.milliseconds)
    runCurrent()

    handled shouldContainExactly listOf("1", "2")
  }

  @Test
  fun shouldNotHandleTheInitialInputText() = runTest {
    val handled = mutableListOf<String>()
    editTextFieldState(debounce, handled, initialInputText = "initial")

    advanceTimeBy(debounce + 100.milliseconds)
    runCurrent()

    handled.shouldBeEmpty()
  }

  /** The text input factory tests configure a zero debounce via `DataCaptureConfig`. */
  @Test
  fun shouldHandleEveryValueWhenTheDebounceIsZero() = runTest {
    val handled = mutableListOf<String>()
    val state = editTextFieldState(Duration.ZERO, handled)

    state.type("1")
    runCurrent()
    state.type("12")
    runCurrent()

    handled shouldContainExactly listOf("1", "12")
  }

  /**
   * Creates the state under test and runs its `init` block, so the first emission it drops is the
   * initial value rather than a value typed afterwards.
   */
  private fun TestScope.editTextFieldState(
    debounce: Duration,
    handled: MutableList<String>,
    initialInputText: String = "",
  ): EditTextFieldState =
    EditTextFieldState(
        hint = null,
        helperText = null,
        isError = false,
        isReadOnly = false,
        keyboardOptions = KeyboardOptions(),
        isMultiLine = false,
        initialInputText = initialInputText,
        handleTextInputChange = { handled += it },
        coroutineScope = backgroundScope,
        debounce = debounce,
      )
      .also { runCurrent() }

  /**
   * Applies a text change. Outside composition the global snapshot is not applied automatically, so
   * `snapshotFlow` observes the write only after apply notifications are sent.
   */
  private fun EditTextFieldState.type(text: String) {
    onInputTextChange(text)
    Snapshot.sendApplyNotifications()
  }
}
