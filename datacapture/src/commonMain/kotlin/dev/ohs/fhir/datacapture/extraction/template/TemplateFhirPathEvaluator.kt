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

import dev.ohs.fhir.datacapture.fhirpath.FhirPathService
import dev.ohs.fhir.model.r4.OperationOutcome

/** FHIRPath adapter used by template extraction. */
internal class TemplateFhirPathEvaluator {
  fun evaluate(
    expression: TemplateExtractExpression,
    scope: TemplateEvaluationScope,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): List<Any> {
    val baseContext = scope.context ?: scope.questionnaireResponse
    val variables = buildMap {
      putAll(scope.variables)
      put("resource", scope.questionnaireResponse)
      put("context", baseContext)
      put("questionnaire", scope.questionnaire)
      scope.questionnaireItem?.let { put("qItem", it) }
    }

    return try {
      FhirPathService.evaluateUntypedOrThrow(
        expression = expression.expression,
        base = baseContext,
        variables = variables,
      )
    } catch (throwable: Throwable) {
      issues +=
        TemplateExtractionIssue(
          severity = OperationOutcome.IssueSeverity.Error,
          code = OperationOutcome.IssueType.Exception,
          diagnostics =
            "FHIRPath evaluation failed for '${expression.expression}': ${throwable.message ?: throwable::class.simpleName}",
          expressionPath = path,
        )
      emptyList()
    }
  }
}
