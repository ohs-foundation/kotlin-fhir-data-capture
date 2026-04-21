/*
 * Copyright 2025-2026 Google LLC
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import dev.ohs.fhir.datacapture.theme.QuestionnaireTheme

private val DarkColorScheme =
  darkColorScheme(
    primary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.PrimaryBlue80,
    onPrimary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnPrimaryBlue20,
    primaryContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.PrimaryContainerBlue30,
    onPrimaryContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnPrimaryContainerBlue90,
    secondary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.SecondaryBlue80,
    onSecondary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnSecondaryBlue20,
    secondaryContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.SecondaryContainerBlue30,
    onSecondaryContainer =
      _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnSecondaryContainerBlue90,
    tertiary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.TertiaryGreen80,
    onTertiary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnTertiaryGreen20,
    tertiaryContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.TertiaryContainerGreen30,
    onTertiaryContainer =
      _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnTertiaryContainerGreen90,
    error = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.ErrorRed80,
    errorContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.ErrorContainerRed20,
    onError = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnErrorRed30,
    onErrorContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnErrorContainerRed90,
    background = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.BackgroundNeutral10,
    onBackground = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnBackgroundNeutral90,
    surface = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.SurfaceNeutral10,
    onSurface = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnSurfaceNeutral90,
    surfaceVariant =
      _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.SurfaceVariantNeutralVariant30,
    onSurfaceVariant =
      _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnSurfaceVariantNeutralVariant80,
    outline = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OutlineNeutralVariant60,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.PrimaryBlue40,
    onPrimary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnPrimaryBlue100,
    primaryContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.PrimaryContainerBlue90,
    onPrimaryContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnPrimaryContainerBlue10,
    secondary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.SecondaryBlue40,
    onSecondary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnSecondaryBlue100,
    secondaryContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.SecondaryContainerBlue90,
    onSecondaryContainer =
      _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnSecondaryContainerBlue10,
    tertiary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.TertiaryGreen40,
    onTertiary = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnTertiaryGreen100,
    tertiaryContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.TertiaryContainerGreen90,
    onTertiaryContainer =
      _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnTertiaryContainerGreen10,
    error = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.ErrorRed40,
    errorContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.ErrorContainerRed100,
    onError = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnErrorRed90,
    onErrorContainer = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnErrorContainerRed10,
    background = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.BackgroundNeutral100,
    onBackground = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnBackgroundNeutral10,
    surface = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.SurfaceNeutral100,
    onSurface = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnSurfaceNeutral10,
    surfaceVariant =
      _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.SurfaceVariantNeutralVariant90,
    onSurfaceVariant =
      _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OnSurfaceVariantNeutralVariant30,
    outline = _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.OutlineNeutralVariant50,
  )

@Composable
fun AppTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme =
    if (darkTheme) _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.DarkColorScheme
    else _root_ide_package_.dev.ohs.fhir.catalog.ui.theme.LightColorScheme

  QuestionnaireTheme(
    colorScheme = colorScheme,
    content = content,
  )
}
