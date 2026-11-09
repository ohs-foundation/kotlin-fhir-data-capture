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

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.ohs.fhir.codelab.shared.resources.Res
import dev.ohs.fhir.codelab.shared.resources.app_name
import dev.ohs.fhir.datacapture.Questionnaire
import dev.ohs.fhir.datacapture.QuestionnaireConfig
import dev.ohs.fhir.datacapture.extraction.template.TemplateExtractionEngine
import dev.ohs.fhir.model.r4.Questionnaire as FhirQuestionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource

// App bar colors of the android-fhir codelab theme (Theme.MaterialComponents.DayNight.DarkActionBar)
private val Purple500 = Color(0xFF6200EE)
private val Purple700 = Color(0xFF3700B3)
private val DarkActionBar = Color(0xFF272727)

private val json = Json {
  explicitNulls = false
  encodeDefaults = false
}

@Composable
fun App() {
  val darkTheme = isSystemInDarkTheme()
  MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
    Scaffold(topBar = { AppBar(darkTheme) }) { innerPadding ->
      Box(modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
        var questionnaireJson by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
          questionnaireJson = Res.readBytes("files/questionnaire.json").decodeToString()
        }
        val scope = rememberCoroutineScope()

        // 4 Display the questionnaire
        questionnaireJson?.let { questionnaireJson ->
          Questionnaire(
            questionnaireJson = questionnaireJson,
            config = QuestionnaireConfig(showCancelButton = false),
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppBar(darkTheme: Boolean) {
  Column {
    Box(
      modifier =
        Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Purple700)
    )
    TopAppBar(
      title = { Text(stringResource(Res.string.app_name)) },
      windowInsets = WindowInsets(0),
      colors =
        TopAppBarDefaults.topAppBarColors(
          containerColor = if (darkTheme) DarkActionBar else Purple500,
          titleContentColor = Color.White,
        ),
    )
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
