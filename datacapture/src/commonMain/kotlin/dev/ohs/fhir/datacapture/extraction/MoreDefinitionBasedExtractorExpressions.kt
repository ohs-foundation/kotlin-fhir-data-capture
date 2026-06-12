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
package dev.ohs.fhir.datacapture.extraction

import co.touchlab.kermit.Logger
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.String as FhirString

// ********************************************************************************************** //
//                                                                                                //
// FHIRPath and request expression evaluation.                                                    //
//                                                                                                //
// Expressions are used both for populating extracted fields and for computing conditional bundle
// //
// request headers such as `ifNoneExist`.                                                         //
//                                                                                                //
// ********************************************************************************************** //

internal fun DefinitionBasedExtractorSession.evaluateExpression(
  expression: Expression,
  base: Any,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  questionnaireItem: Questionnaire.Item?,
  responseItem: QuestionnaireResponse.Item?,
  allocateIds: Map<String, String>,
): List<Any> =
  expression.expression
    ?.value
    ?.let { evaluateResourceExpressionFallback(it, questionnaireResponse) }
    ?.takeIf { it.isNotEmpty() }
    ?: try {
      fhirPathEngine
        .evaluateExpression(
          expression = expression.expression?.value ?: "",
          base = base,
          variables =
            buildMap {
              put("resource", questionnaireResponse)
              put("context", responseItem ?: base)
              put("questionnaire", questionnaire)
              questionnaireItem?.let { put("qItem", it) }
              putAll(allocateIds)
            },
        )
        .toList()
    } catch (throwable: Throwable) {
      Logger.e(
        "Error evaluating definition extract expression ${expression.expression?.value}",
        throwable,
      )
      emptyList()
    }

internal fun DefinitionBasedExtractorSession.evaluateExpressionToString(
  expression: String,
  base: Any,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  questionnaireItem: Questionnaire.Item?,
  responseItem: QuestionnaireResponse.Item?,
  allocateIds: Map<String, String>,
): String =
  evaluateExpression(
      expression =
        Expression(
          language = Enumeration(value = Expression.ExpressionLanguage.Text_Fhirpath),
          expression = FhirString(value = expression),
        ),
      base = base,
      questionnaire = questionnaire,
      questionnaireResponse = questionnaireResponse,
      questionnaireItem = questionnaireItem,
      responseItem = responseItem,
      allocateIds = allocateIds,
    )
    .firstOrNull()
    ?.let(::stringifyValue) ?: ""

internal fun DefinitionBasedExtractorSession.evaluateOptionalExpressionToString(
  expression: String?,
  base: Any,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  questionnaireItem: Questionnaire.Item?,
  responseItem: QuestionnaireResponse.Item?,
  allocateIds: Map<String, String>,
): String? =
  expression
    ?.let {
      evaluateExpressionToString(
        expression = it,
        base = base,
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        questionnaireItem = questionnaireItem,
        responseItem = responseItem,
        allocateIds = allocateIds,
      )
    }
    ?.takeIf { it.isNotBlank() }

private fun evaluateResourceExpressionFallback(
  expression: String,
  questionnaireResponse: QuestionnaireResponse,
): List<Any> =
  when (expression.trim()) {
    "%resource.author" -> listOfNotNull(questionnaireResponse.author)

    "%resource.id" -> questionnaireResponse.id?.let(::listOf) ?: emptyList()

    "'QuestionnaireResponse/' + %resource.id" ->
      questionnaireResponse.id?.let { listOf("QuestionnaireResponse/$it") } ?: emptyList()

    "(%resource.authored | %resource.meta.lastUpdated | now()).first()" ->
      listOfNotNull(questionnaireResponse.authored ?: questionnaireResponse.meta?.lastUpdated)

    else -> emptyList()
  }
