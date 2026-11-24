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

import dev.ohs.fhir.datacapture.validation.QuestionnaireResponseValidator
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

  /** Prepares an application-provided questionnaire response as QuestionnaireViewModel does. */
  private fun loadResponse(
    questionnaireJson: String,
    responseJson: String,
  ): Pair<Questionnaire, QuestionnaireResponse> {
    val questionnaire = fhirJson.decodeFromString<Questionnaire>(questionnaireJson)
    val questionnaireResponse = fhirJson.decodeFromString<QuestionnaireResponse>(responseJson)
    QuestionnaireResponseValidator.checkQuestionnaireResponse(questionnaire, questionnaireResponse)
    return questionnaire to
      questionnaireResponse
        .toBuilder()
        .apply {
          addMissingResponseItems(questionnaire)
          packRepeatedGroups(questionnaire)
        }
        .build()
  }

  private suspend fun visitedLinkIds(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): List<String> {
    val visited = mutableListOf<String>()
    questionnaire.forEachItemPair(questionnaireResponse) { questionnaireItem, _ ->
      visited.add(questionnaireItem.linkId.value!!)
    }
    return visited
  }

  private suspend fun visitedLinkIdsForSparseResponse(groupRepeats: String): List<String> {
    val (questionnaire, questionnaireResponse) =
      loadResponse(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "item": [
            {
              "linkId": "group",
              "type": "group",
              $groupRepeats
              "item": [
                { "linkId": "a", "type": "integer" },
                { "linkId": "b", "type": "integer" },
                { "linkId": "c", "type": "integer" }
              ]
            }
          ]
        }
        """,
        // Unanswered children b and c are left out, which FHIR allows.
        """
        {
          "resourceType": "QuestionnaireResponse",
          "status": "in-progress",
          "item": [
            {
              "linkId": "group",
              "item": [{ "linkId": "a", "answer": [{ "valueInteger": 1 }] }]
            }
          ]
        }
        """,
      )
    return visitedLinkIds(questionnaire, questionnaireResponse)
  }

  @Test
  fun forEachItemPair_sparseResponseUnderGroupWithoutRepeats() = runTest {
    assertEquals(listOf("group", "a", "b", "c"), visitedLinkIdsForSparseResponse(""))
  }

  @Test
  fun forEachItemPair_sparseResponseUnderGroupWithRepeatsFalse() = runTest {
    assertEquals(
      listOf("group", "a", "b", "c"),
      visitedLinkIdsForSparseResponse("\"repeats\": false,"),
    )
  }

  private val questionnaireWithRepeatedGroup =
    """
    {
      "resourceType": "Questionnaire",
      "status": "active",
      "item": [
        {
          "linkId": "repeated-group",
          "type": "group",
          "repeats": true,
          "item": [
            { "linkId": "a", "type": "integer" },
            { "linkId": "b", "type": "integer" }
          ]
        }
      ]
    }
    """

  @Test
  fun addMissingResponseItems_doesNotAddInstanceOfRepeatedGroup() = runTest {
    val (questionnaire, questionnaireResponse) =
      loadResponse(
        questionnaireWithRepeatedGroup,
        """{ "resourceType": "QuestionnaireResponse", "status": "in-progress" }""",
      )

    assertEquals(0, questionnaireResponse.item.single().answer.size)
    assertEquals(listOf("repeated-group"), visitedLinkIds(questionnaire, questionnaireResponse))
  }

  @Test
  fun addMissingResponseItems_addsMissingItemsToEachInstanceOfRepeatedGroup() = runTest {
    val (questionnaire, questionnaireResponse) =
      loadResponse(
        questionnaireWithRepeatedGroup,
        """
        {
          "resourceType": "QuestionnaireResponse",
          "status": "in-progress",
          "item": [
            {
              "linkId": "repeated-group",
              "item": [{ "linkId": "a", "answer": [{ "valueInteger": 1 }] }]
            },
            {
              "linkId": "repeated-group",
              "item": [{ "linkId": "b", "answer": [{ "valueInteger": 2 }] }]
            }
          ]
        }
        """,
      )

    assertEquals(
      listOf("repeated-group", "a", "b", "a", "b"),
      visitedLinkIds(questionnaire, questionnaireResponse),
    )
  }

  @Test
  fun forEachItemPair_visitsItemsNestedUnderAnswerOfQuestionWithoutRepeats() = runTest {
    val (questionnaire, questionnaireResponse) =
      loadResponse(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "item": [
            {
              "linkId": "question",
              "type": "boolean",
              "item": [
                { "linkId": "a", "type": "integer" },
                { "linkId": "b", "type": "integer" }
              ]
            }
          ]
        }
        """,
        """
        {
          "resourceType": "QuestionnaireResponse",
          "status": "in-progress",
          "item": [
            {
              "linkId": "question",
              "answer": [
                {
                  "valueBoolean": true,
                  "item": [{ "linkId": "b", "answer": [{ "valueInteger": 2 }] }]
                }
              ]
            }
          ]
        }
        """,
      )

    assertEquals(listOf("question", "a", "b"), visitedLinkIds(questionnaire, questionnaireResponse))
  }
}
