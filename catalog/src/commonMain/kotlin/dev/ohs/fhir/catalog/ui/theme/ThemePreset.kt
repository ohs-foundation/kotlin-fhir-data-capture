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
package dev.ohs.fhir.catalog.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin_fhir_data_capture.catalog.generated.resources.Res
import kotlin_fhir_data_capture.catalog.generated.resources.theme_preset_default
import org.jetbrains.compose.resources.StringResource

/**
 * Questionnaire theme presets shown in the catalog. Each preset exercises a part of the theming
 * API, and the screenshot tests render every component questionnaire under every preset.
 */
enum class ThemePreset(val label: StringResource) {
  DEFAULT(Res.string.theme_preset_default)
}

/** Holds the theme preset selected in the catalog. */
class ThemePresetSelection(initial: ThemePreset = ThemePreset.DEFAULT) {
  var selected by mutableStateOf(initial)
}

val LocalThemePresetSelection = staticCompositionLocalOf { ThemePresetSelection() }
