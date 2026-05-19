/*
 * Copyright 2022-2026 Open Health Stack Foundation
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

import dev.ohs.fhir.datacapture.views.QuestionnaireViewItem
import dev.ohs.fhir.model.r4.QuestionnaireResponse

/** Various types of rows that can be used in a Questionnaire RecyclerView. */
internal sealed interface QuestionnaireAdapterItem {
  /** A row for a question in a Questionnaire RecyclerView. */
  data class Question(val item: QuestionnaireViewItem) :
    QuestionnaireAdapterItem, QuestionnaireReviewItem {
    var id: String? = item.questionnaireItem.linkId.value
  }

  /**
   * A row for a repeated group response instance's header.
   *
   * @property id Unique identifier for this header row.
   * @property index The response index. 0-indexed, but should be rendered as 1-indexed in the UI.
   * @property onDeleteClicked Callback invoked when the user clicks the delete button.
   * @property responses Responses nested under this header.
   * @property title Display title shown in the header.
   */
  data class RepeatedGroupHeader(
    val id: String,
    val index: Int,
    val onDeleteClicked: () -> Unit,
    val responses: List<QuestionnaireResponse.Item>,
    val title: String,
  ) : QuestionnaireAdapterItem

  data class RepeatedGroupAddButton(var id: String?, val item: QuestionnaireViewItem) :
    QuestionnaireAdapterItem

  data class Navigation(val questionnaireNavigationUIState: QuestionnaireNavigationUIState) :
    QuestionnaireAdapterItem, QuestionnaireReviewItem
}

internal sealed interface QuestionnaireReviewItem
