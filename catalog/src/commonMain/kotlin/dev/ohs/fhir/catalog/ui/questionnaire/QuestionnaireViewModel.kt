/*
 * Copyright 2022-2026 Google LLC
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

package dev.ohs.fhir.catalog.ui.questionnaire

import androidx.lifecycle.ViewModel
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin_fhir_data_capture.catalog.generated.resources.Res

class QuestionnaireViewModel : ViewModel() {
  private val fhirJson = FhirR4Json()

  fun getQuestionnaireResponseJson(response: QuestionnaireResponse): String =
    fhirJson.encodeToString(response)

  suspend fun getQuestionnaire(fileName: String) = Res.readBytes("files/$fileName").decodeToString()

  private fun findItemText(items: List<Questionnaire.Item>, linkId: String): String? {
    for (item in items) {
      if (item.linkId.value == linkId) {
        return item.text?.value
      }
      val nested = findItemText(item.item, linkId)
      if (nested != null) return nested
    }
    return null
  }
}
