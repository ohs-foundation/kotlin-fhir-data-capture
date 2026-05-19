/*
 * Copyright 2023-2026 Open Health Stack Foundation
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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ohs.fhir.datacapture.extensions.flattened
import dev.ohs.fhir.datacapture.views.components.ValidationErrorDialog
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.coroutines.cancellation.CancellationException

/** Display configuration for [Questionnaire]. All fields have defaults matching prior behaviour. */
data class QuestionnaireConfig(
  val showSubmitButton: Boolean = true,
  val showCancelButton: Boolean = true,
  val showReviewPage: Boolean = false,
  val showReviewPageFirst: Boolean = false,
  val isReadOnly: Boolean = false,
  val showAsterisk: Boolean = false,
  val showRequiredText: Boolean = true,
  val showOptionalText: Boolean = false,
  val showNavigationLongScroll: Boolean = false,
  val submitButtonText: String? = null,
  val showSubmitAnywayWhenValidationFails: Boolean = true,
)

/**
 * Public composable function for displaying a FHIR Questionnaire in KMP applications.
 *
 * @param questionnaireJson JSON string of the FHIR Questionnaire to display
 * @param questionnaireResponseJson Optional JSON string of a pre-filled QuestionnaireResponse
 * @param questionnaireLaunchContextMap Optional launch context map
 * @param config Display configuration; see [QuestionnaireConfig] for defaults
 * @param matchersProvider Custom matchers provider for custom question types (optional)
 * @param onSubmit Callback invoked when user submits the questionnaire with the response
 * @param onCancel Callback invoked when user cancels the questionnaire
 */
@Composable
fun Questionnaire(
  questionnaireJson: String,
  questionnaireResponseJson: String? = null,
  questionnaireLaunchContextMap: Map<String, String>? = null,
  config: QuestionnaireConfig = QuestionnaireConfig(),
  matchersProvider: QuestionnaireItemViewFactoryMatchersProvider? = null,
  onSubmit: (suspend () -> QuestionnaireResponse) -> Unit,
  onCancel: () -> Unit,
) {
  val stateMap =
    remember(questionnaireJson, questionnaireResponseJson, questionnaireLaunchContextMap, config) {
      buildMap<String, Any> {
        put(EXTRA_QUESTIONNAIRE_JSON_STRING, questionnaireJson)
        questionnaireResponseJson?.let { put(EXTRA_QUESTIONNAIRE_RESPONSE_JSON_STRING, it) }
        questionnaireLaunchContextMap?.let { put(EXTRA_QUESTIONNAIRE_LAUNCH_CONTEXT_MAP, it) }
        put(EXTRA_SHOW_SUBMIT_BUTTON, config.showSubmitButton)
        put(EXTRA_SHOW_CANCEL_BUTTON, config.showCancelButton)
        put(EXTRA_ENABLE_REVIEW_PAGE, config.showReviewPage)
        put(EXTRA_SHOW_REVIEW_PAGE_FIRST, config.showReviewPageFirst)
        put(EXTRA_READ_ONLY, config.isReadOnly)
        put(EXTRA_SHOW_ASTERISK_TEXT, config.showAsterisk)
        put(EXTRA_SHOW_REQUIRED_TEXT, config.showRequiredText)
        put(EXTRA_SHOW_OPTIONAL_TEXT, config.showOptionalText)
        put(EXTRA_SHOW_NAVIGATION_IN_DEFAULT_LONG_SCROLL, config.showNavigationLongScroll)
        config.submitButtonText?.let { put(EXTRA_SUBMIT_BUTTON_TEXT, it) }
        put(EXTRA_SHOW_SUBMIT_ANYWAY_BUTTON, config.showSubmitAnywayWhenValidationFails)
      }
    }
  val effectiveMatchersProvider =
    remember(matchersProvider) {
      matchersProvider ?: EmptyQuestionnaireItemViewFactoryMatchersProvider
    }

  val viewModel: QuestionnaireViewModel =
    viewModel(key = questionnaireJson) { QuestionnaireViewModel(stateMap) }
  val flattenedQuestionnaireItems =
    remember(viewModel.questionnaire) { viewModel.questionnaire.item.flattened() }

  var showValidationDialog by remember { mutableStateOf(false) }
  var invalidFields by remember { mutableStateOf<List<AnnotatedString>>(emptyList()) }

  LaunchedEffect(Unit) {
    viewModel.setOnSubmitButtonClickListener {
      onSubmit {
        val invalidValidationLinkIds =
          viewModel.validateQuestionnaireUpdateUIAndGetErrorFields(flattenedQuestionnaireItems)
        if (invalidValidationLinkIds.isNotEmpty()) {
          invalidFields = invalidValidationLinkIds
          showValidationDialog = true
          throw CancellationException()
        } else {
          return@onSubmit viewModel.getQuestionnaireResponse()
        }
      }
    }
  }

  LaunchedEffect(Unit) { viewModel.setOnCancelButtonClickListener { onCancel() } }

  Box(modifier = Modifier.fillMaxSize()) {
    QuestionnaireScreen(viewModel = viewModel, matchersProvider = effectiveMatchersProvider)

    if (showValidationDialog) {
      ValidationErrorDialog(
        invalidFields = invalidFields,
        onDismiss = { showValidationDialog = false },
        onFixQuestions = { showValidationDialog = false },
        showSubmitAnyway = config.showSubmitAnywayWhenValidationFails,
        onSubmitAnyway = {
          showValidationDialog = false
          onSubmit { viewModel.getQuestionnaireResponse() }
        },
      )
    }
  }
}

/**
 * Default empty implementation of QuestionnaireItemViewHolderFactoryMatchersProvider that provides
 * no custom matchers.
 */
private object EmptyQuestionnaireItemViewFactoryMatchersProvider :
  QuestionnaireItemViewFactoryMatchersProvider {
  override fun get() = emptyList<QuestionnaireItemViewFactoryMatcher>()
}
