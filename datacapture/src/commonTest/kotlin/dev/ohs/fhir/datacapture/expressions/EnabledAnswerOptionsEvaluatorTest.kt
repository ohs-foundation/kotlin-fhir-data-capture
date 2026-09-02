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
package dev.ohs.fhir.datacapture.expressions

import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class EnabledAnswerOptionsEvaluatorTest {
  private val json = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  private fun questionnaireWithContainedValueSet(containedId: String): Questionnaire =
    json.decodeFromString(
      Questionnaire.serializer(),
      """
      {
        "resourceType": "Questionnaire",
        "status": "active",
        "contained": [
          {
            "resourceType": "ValueSet",
            "id": "$containedId",
            "status": "active",
            "expansion": {
              "timestamp": "2026-01-01T00:00:00Z",
              "contains": [
                { "system": "http://example.org/cs", "code": "a", "display": "Option A" },
                { "system": "http://example.org/cs", "code": "b", "display": "Option B" }
              ]
            }
          }
        ],
        "item": [
          {
            "linkId": "q1",
            "type": "choice",
            "answerValueSet": "#vs1"
          }
        ]
      }
      """
        .trimIndent(),
    )

  private val questionnaireResponse =
    QuestionnaireResponse.Builder(
        status =
          dev.ohs.fhir.model.r4.Enumeration(
            value = QuestionnaireResponse.QuestionnaireResponseStatus.Completed
          )
      )
      .apply {
        item =
          mutableListOf(
            QuestionnaireResponse.Item.Builder(
              linkId = dev.ohs.fhir.model.r4.String.Builder().apply { value = "q1" }
            )
          )
      }
      .build()

  private suspend fun evaluateOptions(
    questionnaire: Questionnaire
  ): List<Questionnaire.Item.AnswerOption> {
    val evaluator = EnabledAnswerOptionsEvaluator(questionnaire, questionnaireResponse)
    val (options, _) =
      evaluator.evaluate(questionnaire.item.single(), questionnaireResponse.item.single())
    return options
  }

  @Test
  fun answerValueSet_containedIdWithoutHashPrefix_returnsExpandedOptions() = runTest {
    // kotlin-fhir keeps contained resource ids verbatim from the JSON (no '#'), while the
    // fragment reference in answerValueSet carries one.
    val options = evaluateOptions(questionnaireWithContainedValueSet(containedId = "vs1"))

    assertEquals(2, options.size)
    assertEquals(
      listOf("a", "b"),
      options.map { (it.value as Questionnaire.Item.AnswerOption.Value.Coding).value.code?.value },
    )
  }

  @Test
  fun answerValueSet_containedIdWithHashPrefix_returnsExpandedOptions() = runTest {
    // HAPI-style content where the contained id itself carries the '#'.
    val options = evaluateOptions(questionnaireWithContainedValueSet(containedId = "#vs1"))

    assertEquals(2, options.size)
  }

  @Test
  fun answerValueSet_unknownContainedReference_returnsNoOptions() = runTest {
    val options = evaluateOptions(questionnaireWithContainedValueSet(containedId = "other-vs"))

    assertTrue(options.isEmpty())
  }
}
