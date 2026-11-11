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
package dev.ohs.fhir.datacapture.fhirpath

import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

class ExpressionEvaluatorTest {
  private val json = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  private val questionnaire: Questionnaire =
    json.decodeFromString(
      Questionnaire.serializer(),
      """
      {
        "resourceType": "Questionnaire",
        "status": "active",
        "extension": [
          {
            "url": "http://hl7.org/fhir/StructureDefinition/variable",
            "valueExpression": {
              "name": "weightKg",
              "language": "text/fhirpath",
              "expression": "%resource.repeat(item).where(linkId='weight').answer.value"
            }
          }
        ],
        "item": [
          { "linkId": "weight", "type": "decimal" },
          { "linkId": "height", "type": "decimal" },
          { "linkId": "notes", "type": "string" },
          {
            "linkId": "double-weight",
            "type": "decimal",
            "extension": [
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-calculatedExpression",
                "valueExpression": {
                  "language": "text/fhirpath",
                  "expression": "%resource.repeat(item).where(linkId='weight').answer.value * 2"
                }
              }
            ]
          },
          {
            "linkId": "bmi",
            "type": "decimal",
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/variable",
                "valueExpression": {
                  "name": "heightM",
                  "language": "text/fhirpath",
                  "expression": "%resource.repeat(item).where(linkId='height').answer.value / 100"
                }
              },
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-calculatedExpression",
                "valueExpression": {
                  "language": "text/fhirpath",
                  "expression": "%weightKg / (%heightM * %heightM)"
                }
              }
            ]
          }
        ]
      }
      """
        .trimIndent(),
    )

  private val questionnaireResponse: QuestionnaireResponse =
    json.decodeFromString(
      QuestionnaireResponse.serializer(),
      """
      {
        "resourceType": "QuestionnaireResponse",
        "status": "in-progress",
        "item": [
          { "linkId": "weight", "answer": [{ "valueDecimal": 80 }] },
          { "linkId": "height", "answer": [{ "valueDecimal": 200 }] },
          { "linkId": "notes", "answer": [{ "valueString": "unrelated" }] },
          { "linkId": "double-weight" },
          { "linkId": "bmi" }
        ]
      }
      """
        .trimIndent(),
    )

  private val expressionEvaluator = ExpressionEvaluator(questionnaire, questionnaireResponse)

  private suspend fun affectedLinkIdsWhenUpdating(linkId: String): List<String?> =
    expressionEvaluator
      .evaluateAllAffectedCalculatedExpressions(
        questionnaire.item.single { it.linkId.value == linkId }
      )
      .map { (item, _) -> item.linkId.value }

  @Test
  fun evaluateAllAffectedCalculatedExpressions_updatedItemReferencedDirectlyAndThroughVariable() =
    runTest {
      assertEquals(listOf("double-weight", "bmi"), affectedLinkIdsWhenUpdating("weight"))
    }

  @Test
  fun evaluateAllAffectedCalculatedExpressions_updatedItemReferencedThroughItemVariable() =
    runTest {
      assertEquals(listOf("bmi"), affectedLinkIdsWhenUpdating("height"))
    }

  @Test
  fun evaluateAllAffectedCalculatedExpressions_updatedItemNotReferenced_shouldNotRecalculate() =
    runTest {
      assertEquals(emptyList(), affectedLinkIdsWhenUpdating("notes"))
    }
}
