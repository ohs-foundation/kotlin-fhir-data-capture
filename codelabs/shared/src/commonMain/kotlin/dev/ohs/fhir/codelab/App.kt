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

package dev.ohs.fhir.codelab

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.ohs.fhir.codelab.shared.resources.Res
import dev.ohs.fhir.datacapture.Questionnaire
import dev.ohs.fhir.datacapture.extraction.template.TemplateExtractionEngine
import dev.ohs.fhir.model.r4.Questionnaire as FhirQuestionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val json = Json {
  explicitNulls = false
  encodeDefaults = false
}

@Composable
fun App() {
  MaterialTheme {
    Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
      var questionnaireJson by remember { mutableStateOf<String?>(null) }
      LaunchedEffect(Unit) {
        questionnaireJson = Res.readBytes("files/questionnaire.json").decodeToString()
      }
      val scope = rememberCoroutineScope()

      // 4 Display the questionnaire
      questionnaireJson?.let { questionnaireJson ->
        Questionnaire(
          questionnaireJson = questionnaireJson,
          onSubmit = { getQuestionnaireResponse ->
            // getQuestionnaireResponse() validates the answers first. If validation fails, the
            // library shows an error dialog and cancels this coroutine.
            scope.launch { submitQuestionnaire(questionnaireJson, getQuestionnaireResponse()) }
          },
          onCancel = {},
        )
      }
    }
  }
}

private fun submitQuestionnaire(
  questionnaireJson: String,
  questionnaireResponse: QuestionnaireResponse,
) {
  // 5 Print the questionnaire response
  println("response: ${json.encodeToString(questionnaireResponse)}")

  // 6 Extract FHIR resources from the questionnaire response
  val questionnaire = json.decodeFromString<FhirQuestionnaire>(questionnaireJson)
  val bundle = TemplateExtractionEngine.extract(questionnaire, questionnaireResponse)
  println("extraction result: ${json.encodeToString(bundle)}")
}
