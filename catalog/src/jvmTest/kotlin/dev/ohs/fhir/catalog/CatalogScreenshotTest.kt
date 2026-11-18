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
package dev.ohs.fhir.catalog

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.ohs.fhir.catalog.ui.components.ComponentListViewModel.Component
import dev.ohs.fhir.catalog.ui.questionnaire.QuestionnaireScreen
import dev.ohs.fhir.catalog.ui.questionnaire.QuestionnaireViewModel
import dev.ohs.fhir.catalog.ui.theme.AppTheme
import dev.ohs.fhir.catalog.ui.theme.ThemePreset
import dev.ohs.fhir.datacapture.QUESTIONNAIRE_EDIT_LIST
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Renders every catalog component questionnaire under every [ThemePreset] and compares the result
 * with the committed baseline in `catalog/screenshots`.
 *
 * Record baselines with `./gradlew :catalog:recordRoborazziJvm` and check them with `./gradlew
 * :catalog:verifyRoborazziJvm`. Baselines are recorded on Linux CI only.
 */
@RunWith(Parameterized::class)
@OptIn(ExperimentalTestApi::class)
class CatalogScreenshotTest(private val component: Component, private val preset: ThemePreset) {

  @get:Rule val timeout: Timeout = Timeout.seconds(TEST_TIMEOUT_SECONDS)

  @Test
  fun screenshot() =
    runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
      val testLifecycleOwner = TestLifecycleOwner(Lifecycle.State.RESUMED)
      setContent {
        val viewModelStoreOwner = remember {
          object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
          }
        }
        CompositionLocalProvider(
          LocalLifecycleOwner provides testLifecycleOwner,
          LocalViewModelStoreOwner provides viewModelStoreOwner,
        ) {
          AppTheme(preset = preset, darkTheme = false) {
            QuestionnaireScreen(
              viewModel = remember { QuestionnaireViewModel() },
              title = component.name,
              fileName = component.questionnaireFile,
              onBackClick = {},
              navigateToResponse = {},
            )
          }
        }
      }

      waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) {
        onAllNodes(hasTestTag(QUESTIONNAIRE_EDIT_LIST)).fetchSemanticsNodes().isNotEmpty()
      }
      waitForIdle()

      onRoot().captureRoboImage("${preset.name.lowercase()}/${component.name.lowercase()}.png")
    }

  companion object {
    private const val WIDTH = 412
    private const val HEIGHT = 1600
    private const val LOAD_TIMEOUT_MILLIS = 10_000L
    private const val TEST_TIMEOUT_SECONDS = 60L

    @JvmStatic
    @Parameterized.Parameters(name = "{1}_{0}")
    fun parameters(): List<Array<Any>> =
      ThemePreset.entries.flatMap { preset ->
        Component.entries
          // ITEM_MEDIA never becomes idle: MediaItem's LaunchedEffect is keyed on the bitmap it
          // writes, so it decodes the image in a loop. Include it once that is fixed.
          .filter { it.questionnaireFile.isNotEmpty() && it != Component.ITEM_MEDIA }
          .map { component -> arrayOf(component, preset) }
      }
  }
}
