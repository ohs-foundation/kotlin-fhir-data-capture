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
package dev.ohs.fhir.catalog.views.barcode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ohs.fhir.datacapture.QuestionnaireItemViewFactoryMatcher
import dev.ohs.fhir.datacapture.extensions.itemControlCode
import dev.ohs.fhir.datacapture.theme.QuestionnaireTheme
import dev.ohs.fhir.datacapture.validation.Valid
import dev.ohs.fhir.datacapture.views.QuestionnaireViewItem
import dev.ohs.fhir.datacapture.views.components.Header
import dev.ohs.fhir.datacapture.views.factories.QuestionnaireItemViewFactory
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.String
import kotlin_fhir_data_capture.catalog.generated.resources.Res
import kotlin_fhir_data_capture.catalog.generated.resources.camera_permission_message
import kotlin_fhir_data_capture.catalog.generated.resources.camera_permission_required
import kotlin_fhir_data_capture.catalog.generated.resources.cancel
import kotlin_fhir_data_capture.catalog.generated.resources.ic_barcode
import kotlin_fhir_data_capture.catalog.generated.resources.open_settings
import kotlin_fhir_data_capture.catalog.generated.resources.rescan
import kotlin_fhir_data_capture.catalog.generated.resources.scan_barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.ncgroup.kscan.BarcodeFormat
import org.ncgroup.kscan.BarcodeResult
import org.ncgroup.kscan.ScannerView

internal object BarcodeItemViewFactory : QuestionnaireItemViewFactory {

  @Composable
  override fun Content(questionnaireViewItem: QuestionnaireViewItem) {
    val cameraPermissionProvider = rememberCameraPermissionProvider()
    val coroutineScope = rememberCoroutineScope { Dispatchers.Main }
    val scanBarcodeText = stringResource(Res.string.scan_barcode)

    val scannedAnswer =
      remember(questionnaireViewItem.answers.toString()) {
        questionnaireViewItem.answers.singleOrNull()?.value?.asString()?.value?.value
      }
    val barcodeText =
      remember(scannedAnswer) {
        if (scannedAnswer.isNullOrBlank()) scanBarcodeText else scannedAnswer
      }
    val showRescanBarcode = remember(scannedAnswer) { !scannedAnswer.isNullOrBlank() }

    var showScanner by remember { mutableStateOf(false) }
    var showPermissionError by remember { mutableStateOf(false) }

    Box {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .padding(
              horizontal = QuestionnaireTheme.dimensions.itemMarginHorizontal,
              vertical = QuestionnaireTheme.dimensions.itemMarginVertical,
            )
      ) {
        Header(questionnaireViewItem)

        Row(
          modifier =
            Modifier.clickable {
              coroutineScope.launch {
                try {
                  cameraPermissionProvider.providePermission()
                  showScanner = true
                } catch (_: Exception) {
                  showPermissionError = true
                }
              }
            },
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Icon(
            painter = painterResource(Res.drawable.ic_barcode),
            contentDescription = "Barcode icon",
            modifier = Modifier.size(24.dp),
          )
          Text(
            barcodeText,
            fontSize = 21.sp,
            fontWeight = if (showRescanBarcode) FontWeight.Normal else FontWeight.Bold,
            modifier = Modifier.weight(1f),
          )
          if (showRescanBarcode) {
            Text(
              stringResource(Res.string.rescan),
              fontSize = 21.sp,
              fontWeight = FontWeight.Bold,
              color = Color(0x00, 0x6C, 0xBB),
            )
          }
        }
      }

      if (showPermissionError) {
        AlertDialog(
          onDismissRequest = { showPermissionError = false },
          title = { Text(stringResource(Res.string.camera_permission_required)) },
          text = { Text(stringResource(Res.string.camera_permission_message)) },
          confirmButton = {
            Button(
              onClick = {
                showPermissionError = false
                coroutineScope.launch { cameraPermissionProvider.openSettings() }
              }
            ) {
              Text(stringResource(Res.string.open_settings))
            }
          },
          dismissButton = {
            TextButton(onClick = { showPermissionError = false }) {
              Text(stringResource(Res.string.cancel))
            }
          },
        )
      }

      if (showScanner) {
        ScannerViewDialog(onDismiss = { showScanner = false }) { result ->
          coroutineScope.launch {
            when (result) {
              is BarcodeResult.OnSuccess -> {
                val barcode = result.barcode.data
                if (barcode.isBlank()) {
                  questionnaireViewItem.clearAnswer()
                } else {
                  questionnaireViewItem.setAnswer(
                    QuestionnaireResponse.Item.Answer(
                      value =
                        QuestionnaireResponse.Item.Answer.Value.String(
                          value = String(value = barcode)
                        )
                    )
                  )
                }
              }

              is BarcodeResult.OnFailed -> {
                result.exception.printStackTrace()
              }

              is BarcodeResult.OnCanceled -> {}
            }
          }
        }
      }
    }
  }

  @Composable
  fun ScannerViewDialog(onDismiss: () -> Unit, onBarcodeResult: (BarcodeResult) -> Unit) {
    Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
      Surface(modifier = Modifier.fillMaxSize()) {
        ScannerView(codeTypes = listOf(BarcodeFormat.FORMAT_ALL_FORMATS)) { result ->
          onBarcodeResult(result)
          onDismiss()
        }
      }
    }
  }

  @Preview
  @Composable
  fun PreviewContent() {
    Content(
      QuestionnaireViewItem(
        Questionnaire.Item(
          linkId = String(value = "preview"),
          type = Enumeration(value = Questionnaire.QuestionnaireItemType.String),
          text = String(value = "Test Barcode text"),
        ),
        QuestionnaireResponse.Item(linkId = String(value = "preview")),
        validationResult = Valid,
        answersChangedCallback = { _, _, _, _ -> },
      )
    )
  }
}

val BarcodeItemViewFactoryMatcher =
  QuestionnaireItemViewFactoryMatcher(BarcodeItemViewFactory) { it.itemControlCode == "barcode" }
