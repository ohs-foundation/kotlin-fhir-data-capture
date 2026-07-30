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
package dev.ohs.fhir.datacapture.extraction.definition

import dev.ohs.fhir.datacapture.extensions.referencesQuestionnaireResponseResource
import dev.ohs.fhir.datacapture.fhirpath.FhirPathService
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.String as FhirString

/**
 * Applies `definitionExtractValue` directives for the current scope.
 *
 * The SDC definition-based spec allows fixed values and calculated FHIRPath values to populate the
 * extracted resource even when the user did not directly answer that exact property. Only
 * directives whose canonical definition matches the active `definitionExtract` scope are applied.
 */
internal fun applyDefinitionExtractValueDirectives(
  sourceExtensions: List<Extension>,
  scopeCanonical: String,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  base: Any,
  questionnaireItem: Questionnaire.Item?,
  responseItem: QuestionnaireResponse.Item?,
  allocateIds: Map<String, String>,
  rootAnchor: AnchorContext,
  parentAnchor: AnchorContext,
  directAnchor: AnchorContext?,
) {
  sourceExtensions
    .asSequence()
    .filter { it.url == EXTENSION_DEFINITION_EXTRACT_VALUE_URL }
    .map(::parseDefinitionExtractValue)
    .filter { config ->
      canonicalMatches(left = config.definition.canonical, right = scopeCanonical)
    }
    .forEach { config ->
      val rawValues =
        config.expression?.let { expression ->
          evaluateDefinitionExtractExpression(
            expression = expression,
            base = base,
            questionnaire = questionnaire,
            questionnaireResponse = questionnaireResponse,
            questionnaireItem = questionnaireItem,
            responseItem = responseItem,
            allocateIds = allocateIds,
          )
        } ?: config.fixedValue?.let(::fixedValueToRawValue)?.let(::listOf) ?: emptyList()

      if (rawValues.isEmpty()) {
        return@forEach
      }

      val definitionPath = config.definition.pathSegments

      val targetAnchor =
        when {
          directAnchor != null && definitionPath.startsWithPath(directAnchor.path) -> {
            directAnchor
          }

          parentAnchor.path.isNotEmpty() && definitionPath.startsWithPath(parentAnchor.path) -> {
            parentAnchor
          }

          else -> {
            ensureDefinitionPathAnchor(
              parentAnchor = rootAnchor,
              anchorPath = computeValueAnchorPath(definitionPath),
            )
          }
        }

      writeValuesToDefinitionPath(
        rootAnchor = rootAnchor,
        anchor = targetAnchor,
        fullPath = definitionPath,
        rawValues = rawValues,
      )
    }
}

/**
 * Evaluates a FHIRPath expression in the SDC definition-extraction context.
 *
 * Per the spec, `$extract` expressions only have access to QuestionnaireResponse data,
 * Questionnaire data, and `extractAllocateId` variables. This wrapper builds that scoped variable
 * map and delegates raw FHIRPath execution to the shared [FhirPathService].
 *
 * Item-scoped directives still need `%resource` to resolve against the whole QuestionnaireResponse
 * even though relative paths such as `item.where(...)` should evaluate against the current
 * `QuestionnaireResponse.Item`. Choose the evaluation root up front based on whether the expression
 * references `%resource` instead of maintaining a brittle expression-by-expression fallback table.
 */
internal fun evaluateDefinitionExtractExpression(
  expression: Expression,
  base: Any,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  questionnaireItem: Questionnaire.Item?,
  responseItem: QuestionnaireResponse.Item?,
  allocateIds: Map<String, String>,
): List<Any> {
  val expressionString = expression.expression?.value ?: return emptyList()
  val variables = buildMap {
    put("resource", questionnaireResponse)
    put("context", responseItem ?: base)
    put("questionnaire", questionnaire)
    questionnaireItem?.let { put("qItem", it) }
    putAll(allocateIds)
  }

  val evaluationRoot =
    if (expressionString.referencesQuestionnaireResponseResource()) {
      questionnaireResponse
    } else {
      base
    }

  return FhirPathService.evaluate(
    expression = expressionString,
    resource = evaluationRoot,
    variables = variables,
  )
}

/**
 * Resolves a definition-extraction FHIRPath expression to the singular string form required by
 * `Bundle.entry` request metadata such as `fullUrl`, `ifMatch`, and `ifNoneExist`.
 */
internal fun evaluateDefinitionExtractExpressionToString(
  expression: String,
  base: Any,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  questionnaireItem: Questionnaire.Item?,
  responseItem: QuestionnaireResponse.Item?,
  allocateIds: Map<String, String>,
): String =
  FhirPathService.toStringValue(
    values =
      evaluateDefinitionExtractExpression(
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
      ),
    path = "definition extraction expression '$expression'",
  ) ?: ""

private fun fixedValueToRawValue(fixedValue: Extension.Value): Any =
  when (fixedValue) {
    is Extension.Value.Boolean -> fixedValue.value

    is Extension.Value.Code -> fixedValue.value

    is Extension.Value.CodeableConcept -> fixedValue.value

    is Extension.Value.Coding -> fixedValue.value

    is Extension.Value.Date -> fixedValue.value

    is Extension.Value.DateTime -> fixedValue.value

    is Extension.Value.Decimal -> fixedValue.value

    is Extension.Value.Identifier -> fixedValue.value

    is Extension.Value.Integer -> fixedValue.value

    is Extension.Value.Meta -> fixedValue.value

    is Extension.Value.Quantity -> fixedValue.value

    is Extension.Value.Reference -> fixedValue.value

    is Extension.Value.String -> fixedValue.value

    is Extension.Value.Time -> fixedValue.value

    is Extension.Value.Uri -> fixedValue.value

    is Extension.Value.Canonical -> fixedValue.value

    is Extension.Value.Attachment -> fixedValue.value

    is Extension.Value.HumanName -> fixedValue.value

    is Extension.Value.ContactPoint -> fixedValue.value

    is Extension.Value.Period -> fixedValue.value

    else ->
      error(
        "Unsupported fixed value type ${fixedValue::class.simpleName} in definition-based extraction"
      )
  }
