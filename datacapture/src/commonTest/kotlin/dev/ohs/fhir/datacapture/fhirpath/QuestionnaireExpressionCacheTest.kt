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

import dev.ohs.fhir.datacapture.extensions.EXTENSION_VARIABLE_URL
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.ExtensibleEnumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.Id
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class QuestionnaireExpressionCacheTest {

  private val questionnaire = Questionnaire(status = Enumeration(value = PublicationStatus.Active))

  private val questionnaireResponse =
    QuestionnaireResponse(
      id = "qr-1",
      status = Enumeration(value = QuestionnaireResponse.QuestionnaireResponseStatus.In_Progress),
    )

  @Test
  fun sharesResultOfExpressionThatOnlyReadsResource() = runTest {
    val cache = QuestionnaireExpressionCache().apply { activate() }
    val evaluator =
      ExpressionEvaluator(questionnaire, questionnaireResponse, expressionCache = cache)

    val result = evaluator.evaluateExpression(item("a"), null, fhirPath("%resource.id"))

    assertEquals(listOf("qr-1"), result.map { it.toString() })
    assertEquals(result, cache.cachedResult("%resource.id"))
  }

  @Test
  fun doesNotShareResultOfExpressionReadingItemVariable() = runTest {
    val cache = QuestionnaireExpressionCache().apply { activate() }
    val evaluator =
      ExpressionEvaluator(questionnaire, questionnaireResponse, expressionCache = cache)
    val expression = fhirPath("%resource.exists() and %x = 'a'")

    val first = evaluator.evaluateExpression(item("a", variableX = "'a'"), null, expression)
    val second = evaluator.evaluateExpression(item("b", variableX = "'b'"), null, expression)

    assertEquals(listOf<Any>(true), first)
    assertEquals(listOf<Any>(false), second)
  }

  @Test
  fun doesNotShareResultOfExpressionReadingDelimitedContext() = runTest {
    val cache = QuestionnaireExpressionCache().apply { activate() }
    val evaluator =
      ExpressionEvaluator(questionnaire, questionnaireResponse, expressionCache = cache)
    val expression = fhirPath("%resource.exists() and %'context'.linkId = 'a'")

    val first =
      evaluator.evaluateExpression(
        item("a"),
        QuestionnaireResponse.Item(linkId = fhirString("a")),
        expression,
      )
    val second =
      evaluator.evaluateExpression(
        item("b"),
        QuestionnaireResponse.Item(linkId = fhirString("b")),
        expression,
      )

    assertEquals(listOf<Any>(true), first)
    assertEquals(listOf<Any>(false), second)
  }

  private fun fhirString(value: String) = FhirString(value = value)

  private fun fhirPath(expression: String, name: String? = null) =
    Expression(
      name = name?.let { Id(value = it) },
      language = ExtensibleEnumeration.of(Expression.ExpressionLanguage.Text_Fhirpath),
      expression = fhirString(expression),
    )

  private fun item(linkId: String, variableX: String? = null) =
    Questionnaire.Item(
      linkId = fhirString(linkId),
      type = Enumeration(value = Questionnaire.QuestionnaireItemType.String),
      extension =
        listOfNotNull(
          variableX?.let {
            Extension(
              url = EXTENSION_VARIABLE_URL,
              value = Extension.Value.Expression(value = fhirPath(it, name = "x")),
            )
          }
        ),
    )
}
