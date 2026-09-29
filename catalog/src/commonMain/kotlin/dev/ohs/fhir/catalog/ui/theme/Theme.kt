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
package dev.ohs.fhir.catalog.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import dev.ohs.fhir.datacapture.theme.QuestionnaireCustomStyle
import dev.ohs.fhir.datacapture.theme.QuestionnaireTheme

private val DarkColorScheme =
  darkColorScheme(
    primary = PrimaryBlue80,
    onPrimary = OnPrimaryBlue20,
    primaryContainer = PrimaryContainerBlue30,
    onPrimaryContainer = OnPrimaryContainerBlue90,
    secondary = SecondaryBlue80,
    onSecondary = OnSecondaryBlue20,
    secondaryContainer = SecondaryContainerBlue30,
    onSecondaryContainer = OnSecondaryContainerBlue90,
    tertiary = TertiaryGreen80,
    onTertiary = OnTertiaryGreen20,
    tertiaryContainer = TertiaryContainerGreen30,
    onTertiaryContainer = OnTertiaryContainerGreen90,
    error = ErrorRed80,
    errorContainer = ErrorContainerRed20,
    onError = OnErrorRed30,
    onErrorContainer = OnErrorContainerRed90,
    background = BackgroundNeutral10,
    onBackground = OnBackgroundNeutral90,
    surface = SurfaceNeutral10,
    onSurface = OnSurfaceNeutral90,
    surfaceVariant = SurfaceVariantNeutralVariant30,
    onSurfaceVariant = OnSurfaceVariantNeutralVariant80,
    outline = OutlineNeutralVariant60,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PrimaryBlue40,
    onPrimary = OnPrimaryBlue100,
    primaryContainer = PrimaryContainerBlue90,
    onPrimaryContainer = OnPrimaryContainerBlue10,
    secondary = SecondaryBlue40,
    onSecondary = OnSecondaryBlue100,
    secondaryContainer = SecondaryContainerBlue90,
    onSecondaryContainer = OnSecondaryContainerBlue10,
    tertiary = TertiaryGreen40,
    onTertiary = OnTertiaryGreen100,
    tertiaryContainer = TertiaryContainerGreen90,
    onTertiaryContainer = OnTertiaryContainerGreen10,
    error = ErrorRed40,
    errorContainer = ErrorContainerRed100,
    onError = OnErrorRed90,
    onErrorContainer = OnErrorContainerRed10,
    background = BackgroundNeutral100,
    onBackground = OnBackgroundNeutral10,
    surface = SurfaceNeutral100,
    onSurface = OnSurfaceNeutral10,
    surfaceVariant = SurfaceVariantNeutralVariant90,
    onSurfaceVariant = OnSurfaceVariantNeutralVariant30,
    outline = OutlineNeutralVariant50,
  )

/**
 * Named styles referenced by `component_per_question_custom_style.json` through the android-style
 * extension. Mirrors android-fhir's `CustomStyle_1` .. `CustomStyle_9` (text appearance from
 * Display Large down to Label Small, on a progressively lighter blue background).
 */
private fun customStyles(typography: Typography): Map<String, QuestionnaireCustomStyle> =
  listOf(
      typography.displayLarge to CustomStylePrimary100,
      typography.displayMedium to CustomStylePrimary200,
      typography.displaySmall to CustomStylePrimary300,
      typography.headlineLarge to CustomStylePrimary400,
      typography.headlineMedium to CustomStylePrimary500,
      typography.headlineSmall to CustomStylePrimary600,
      typography.labelLarge to CustomStylePrimary700,
      typography.labelMedium to CustomStylePrimary800,
      typography.labelSmall to CustomStylePrimary900,
    )
    .mapIndexed { index, (textStyle, background) ->
      "CustomStyle_${index + 1}" to
        QuestionnaireCustomStyle(
          textStyle = textStyle.copy(color = CustomStyleOnPrimary),
          background = background,
        )
    }
    .toMap()

/**
 * Text-color-only styles (no background) for status messages. Only the color is set, so the text
 * keeps the default question typography.
 */
private fun statusTextStyles(darkTheme: Boolean): Map<String, QuestionnaireCustomStyle> =
  mapOf(
      "SuccessText" to if (darkTheme) StatusSuccessDark else StatusSuccessLight,
      "WarningText" to if (darkTheme) StatusWarningDark else StatusWarningLight,
      "ErrorText" to if (darkTheme) StatusErrorDark else StatusErrorLight,
      "InfoText" to if (darkTheme) StatusInfoDark else StatusInfoLight,
    )
    .mapValues { (_, color) -> QuestionnaireCustomStyle(textStyle = TextStyle(color = color)) }

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val colorScheme =
    if (darkTheme) {
      DarkColorScheme
    } else {
      LightColorScheme
    }

  val typography = Typography()
  QuestionnaireTheme(
    colorScheme = colorScheme,
    typography = typography,
    customStyles = customStyles(typography) + statusTextStyles(darkTheme),
    content = content,
  )
}
