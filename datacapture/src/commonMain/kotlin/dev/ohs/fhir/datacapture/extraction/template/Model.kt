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
package dev.ohs.fhir.datacapture.extraction.template

import dev.ohs.fhir.datacapture.extensions.isRepeatedGroup
import dev.ohs.fhir.datacapture.extensions.normalizedVariableName
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.OperationOutcome
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.String as FhirString

/**
 * Parsed view of the `sdc-questionnaire-templateExtract` complex extension.
 *
 * In SDC this extension points to a contained resource template and can optionally supply
 * expressions for `Bundle.entry.fullUrl`, the extracted resource id, and conditional
 * `Bundle.entry.request` metadata that should be populated when the output transaction Bundle is
 * assembled:
 * https://build.fhir.org/ig/HL7/sdc/en/StructureDefinition-sdc-questionnaire-templateExtract.html
 */
internal data class TemplateExtractDefinition(
  val templateReference: String,
  val fullUrlExpression: String? = null,
  val resourceIdExpression: String? = null,
  val ifNoneMatchExpression: String? = null,
  val ifModifiedSinceExpression: String? = null,
  val ifMatchExpression: String? = null,
  val ifNoneExistExpression: String? = null,
)

/** Parsed expression attached to a template node. */
internal data class TemplateExtractExpression(
  val expression: String,
  val variableName: String? = null,
)

/**
 * Template control extensions found on one JSON node inside a contained extraction template.
 *
 * `templateExtractContext` shifts the evaluation context for the subtree, while
 * `templateExtractValue` replaces the current node with one or more evaluated values:
 * https://build.fhir.org/ig/HL7/sdc/en/StructureDefinition-sdc-questionnaire-templateExtractContext.html
 * https://build.fhir.org/ig/HL7/sdc/en/StructureDefinition-sdc-questionnaire-templateExtractValue.html
 */
internal data class TemplateNodeControls(
  val contextExpression: TemplateExtractExpression? = null,
  val valueExpression: TemplateExtractExpression? = null,
)

/** Control extensions split away from any non-extraction extensions that must remain on output. */
internal data class TemplateNodeExtensionState(
  val controls: TemplateNodeControls = TemplateNodeControls(),
  val remainingExtensions: kotlinx.serialization.json.JsonArray? = null,
)

/** Evaluation scope for a single template expansion pass. */
internal data class TemplateEvaluationScope(
  val questionnaire: Questionnaire,
  val questionnaireResponse: QuestionnaireResponse,
  val questionnaireItem: Questionnaire.Item?,
  val context: Any?,
  val variables: Map<String, Any?>,
) {
  /**
   * Returns a derived scope after a `templateExtractContext` or `templateExtractValue` evaluation.
   *
   * When the expression defines a named variable, the variable is normalized and added to the scope
   * so later template expressions can reference it with the same behavior used elsewhere in the
   * data capture stack.
   */
  fun withContext(
    nextContext: Any?,
    namedVariable: String? = null,
    namedValue: Any? = null,
  ): TemplateEvaluationScope {
    val updatedVariables =
      if (namedVariable != null) {
        variables + (namedVariable.normalizedVariableName() to namedValue)
      } else {
        variables
      }
    return copy(context = nextContext, variables = updatedVariables)
  }
}

/** A single logical occurrence of a questionnaire item for extraction purposes. */
internal data class ItemExtractionContext(
  val baseContext: QuestionnaireResponse.Item,
  val childResponseItems: List<QuestionnaireResponse.Item>,
)

/** Internal issue model that is later converted into an [OperationOutcome]. */
internal data class TemplateExtractionIssue(
  val severity: OperationOutcome.IssueSeverity,
  val code: OperationOutcome.IssueType,
  val diagnostics: String,
  val expressionPath: String? = null,
) {
  /** Converts one internal extraction issue into the FHIR `OperationOutcome.issue` shape. */
  fun toOperationOutcomeIssue(): OperationOutcome.Issue =
    OperationOutcome.Issue(
      severity = Enumeration(value = severity),
      code = Enumeration(value = code),
      diagnostics = FhirString(value = diagnostics),
      expression = expressionPath?.let { listOf(FhirString(value = it)) } ?: emptyList(),
    )
}

/** Converts the collected extraction issues into a single FHIR-native diagnostic resource. */
internal fun List<TemplateExtractionIssue>.toOperationOutcome(): OperationOutcome =
  OperationOutcome(issue = map { it.toOperationOutcomeIssue() })

/**
 * Normalizes a questionnaire item into the logical extraction units defined by SDC.
 *
 * Repeating groups produce one context per repeated group instance, while repeating non-group
 * questions produce one synthetic context per answer so item-level templates behave like "extract
 * once per answer" instead of "extract once per response item".
 */
internal fun Questionnaire.Item.toExtractionContexts(
  matchingResponseItems: List<QuestionnaireResponse.Item>
): List<ItemExtractionContext> {
  if (matchingResponseItems.isEmpty()) return emptyList()

  if (isRepeatedGroup) {
    return matchingResponseItems.map { responseItem ->
      ItemExtractionContext(baseContext = responseItem, childResponseItems = responseItem.item)
    }
  }

  val responseItem = matchingResponseItems.first()
  if (repeats?.value == true) {
    return responseItem.answer.map { responseAnswer ->
      val syntheticItem =
        responseItem
          .toBuilder()
          .apply {
            answer = mutableListOf(responseAnswer.toBuilder())
            item = responseAnswer.item.map { child -> child.toBuilder() }.toMutableList()
          }
          .build()

      ItemExtractionContext(baseContext = syntheticItem, childResponseItems = responseAnswer.item)
    }
  }

  return listOf(
    ItemExtractionContext(baseContext = responseItem, childResponseItems = responseItem.item)
  )
}

/**
 * Result of SDC template-based extraction.
 *
 * @property bundle The extracted transaction bundle. This is always returned, even when warnings or
 *   recoverable errors were recorded during extraction.
 * @property operationOutcome Structured issues captured while processing the template. `null`
 *   indicates a clean extraction with no warnings or errors.
 */
data class TemplateExtractionResult(
  val bundle: Bundle,
  val operationOutcome: OperationOutcome? = null,
)
