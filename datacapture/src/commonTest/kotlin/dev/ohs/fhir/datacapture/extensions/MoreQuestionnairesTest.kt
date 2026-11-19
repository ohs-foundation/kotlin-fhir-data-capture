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
package dev.ohs.fhir.datacapture.extensions

import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class MoreQuestionnairesTest {

  private val fhirJson = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  private suspend fun visitedLinkIds(groupRepeats: String): List<String> {
    val questionnaire =
      fhirJson.decodeFromString<Questionnaire>(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "item": [
            {
              "linkId": "group",
              "type": "group",
              $groupRepeats
              "item": [{ "linkId": "child", "type": "integer" }]
            }
          ]
        }
        """
      )
    val questionnaireResponse =
      fhirJson.decodeFromString<QuestionnaireResponse>(
        """
        {
          "resourceType": "QuestionnaireResponse",
          "status": "in-progress",
          "item": [{ "linkId": "group", "item": [{ "linkId": "child" }] }]
        }
        """
      )
    val visited = mutableListOf<String>()
    questionnaire.forEachItemPair(questionnaireResponse) { questionnaireItem, _ ->
      visited.add(questionnaireItem.linkId.value!!)
    }
    return visited
  }

  @Test
  fun forEachItemPair_visitsChildrenOfGroupWithoutRepeats() = runTest {
    // `repeats` is optional: a group without it does not repeat.
    assertEquals(listOf("group", "child"), visitedLinkIds(""))
  }

  @Test
  fun forEachItemPair_visitsChildrenOfGroupWithRepeatsFalse() = runTest {
    assertEquals(listOf("group", "child"), visitedLinkIds("\"repeats\": false,"))
  }
}
