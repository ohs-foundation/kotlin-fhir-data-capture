/*
 * Copyright 2026 Google LLC
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

package dev.ohs.fhir.catalog.views.locationwidget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.datacapture.QuestionnaireItemViewFactoryMatcher
import dev.ohs.fhir.datacapture.theme.QuestionnaireTheme
import dev.ohs.fhir.datacapture.validation.Invalid
import dev.ohs.fhir.datacapture.views.QuestionnaireViewItem
import dev.ohs.fhir.datacapture.views.components.Header
import dev.ohs.fhir.datacapture.views.factories.QuestionnaireItemViewFactory
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin_fhir_data_capture.catalog.generated.resources.Res
import kotlin_fhir_data_capture.catalog.generated.resources.required_text_and_new_line
import org.jetbrains.compose.resources.stringResource

internal const val PRIMARY_GPS_COORDINATE_EXTENSION_URL =
  "https://github.com/google/android-fhir/StructureDefinition/gps-coordinate"
internal const val GPS_COORDINATE_EXTENSION_URL = "gps-coordinate"
internal const val GPS_COORDINATE_EXTENSION_VALUE_LATITUDE = "latitude"
internal const val GPS_COORDINATE_EXTENSION_VALUE_LONGITUDE = "longitude"
internal const val GPS_COORDINATE_EXTENSION_VALUE_ALTITUDE = "altitude"

/**
 * Renders a read-only coordinate field for a questionnaire item carrying a GPS coordinate
 * extension (`latitude`, `longitude`, or `altitude`). Subscribes to [LocationEventBus] and
 * writes the relevant coordinate value to the questionnaire answer when a location is captured.
 *
 * @see LocationCaptureItemViewFactory for the button that triggers the location fetch.
 */
object LocationCoordinateItemViewFactory : QuestionnaireItemViewFactory {

  @Composable
  override fun Content(questionnaireViewItem: QuestionnaireViewItem) {
    val answer =
      remember(questionnaireViewItem.answers.toString()) {
        questionnaireViewItem.answers
          .singleOrNull()
          ?.value
          ?.asDecimal()
          ?.value
          ?.value
          ?.toStringExpanded() ?: ""
      }
    val requiredTextAndNewLineText = stringResource(Res.string.required_text_and_new_line)
    val validationResultMessage =
      remember(questionnaireViewItem.validationResult) {
        when (val validationResult = questionnaireViewItem.validationResult) {
          is Invalid -> {
            if (
              questionnaireViewItem.questionnaireItem.required?.value == true &&
                questionnaireViewItem.questionViewTextConfiguration.showRequiredText
            ) {
              requiredTextAndNewLineText + validationResult.singleStringValidationMessage
            } else {
              validationResult.singleStringValidationMessage
            }
          }

          else -> null
        }
      }
    val questionnaireItemLabel =
      remember(questionnaireViewItem.questionnaireItem) { questionnaireViewItem.questionText }

    var textState by remember(answer) { mutableStateOf(answer) }

    val currentViewItem by rememberUpdatedState(questionnaireViewItem)

    LaunchedEffect(Unit) {
      LocationEventBus.locationUpdates.collect { locationData ->
        val gpsCoordinateExtensionValue =
          currentViewItem.questionnaireItem.gpsCoordinateValueString

        val questionnaireResponseAnswerValue =
          when (gpsCoordinateExtensionValue) {
            GPS_COORDINATE_EXTENSION_VALUE_LATITUDE ->
              QuestionnaireResponse.Item.Answer.Value.Decimal(
                value = Decimal(value = BigDecimal.fromDouble(locationData.latitude))
              )

            GPS_COORDINATE_EXTENSION_VALUE_LONGITUDE ->
              QuestionnaireResponse.Item.Answer.Value.Decimal(
                value = Decimal(value = BigDecimal.fromDouble(locationData.longitude))
              )

            GPS_COORDINATE_EXTENSION_VALUE_ALTITUDE ->
              locationData.altitude?.let { alt ->
                QuestionnaireResponse.Item.Answer.Value.Decimal(
                  value = Decimal(value = BigDecimal.fromDouble(alt))
                )
              }

            else -> null
          }

        if (questionnaireResponseAnswerValue != null) {
          currentViewItem.setAnswer(
            QuestionnaireResponse.Item.Answer(value = questionnaireResponseAnswerValue)
          )
        }
      }
    }

    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(
            horizontal = QuestionnaireTheme.dimensions.itemMarginHorizontal,
            vertical = QuestionnaireTheme.dimensions.itemMarginVertical,
          )
    ) {
      Header(questionnaireViewItem)

      OutlinedTextField(
        value = textState,
        onValueChange = { /* View shouldn't directly edit coordinate */ },
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
        isError = !validationResultMessage.isNullOrBlank(),
        supportingText = { validationResultMessage?.takeIf { it.isNotBlank() }?.let { Text(it) } },
        label = { questionnaireItemLabel?.let { Text(questionnaireItemLabel) } },
      )
    }
  }
}

internal val Questionnaire.Item.gpsCoordinateValueString: String?
  get() =
    extension
      .find { extension ->
        extension.url == PRIMARY_GPS_COORDINATE_EXTENSION_URL ||
          extension.url == GPS_COORDINATE_EXTENSION_URL
      }
      ?.value
      ?.asString()
      ?.value
      ?.value

val LocationCoordinateItemViewFactoryMatcher =
  QuestionnaireItemViewFactoryMatcher(LocationCoordinateItemViewFactory) {
    it.extension.any { extension ->
      extension.url == PRIMARY_GPS_COORDINATE_EXTENSION_URL ||
        extension.url == GPS_COORDINATE_EXTENSION_URL
    }
  }
