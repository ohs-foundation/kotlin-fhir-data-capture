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

import dev.ohs.fhir.datacapture.extraction.EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL
import dev.ohs.fhir.datacapture.extraction.EXTENSION_TEMPLATE_EXTRACT_VALUE_URL
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val FHIRPATH_LANGUAGE = "text/fhirpath"

internal fun parseTemplateNodeExtensionState(
  extensionElement: JsonElement?,
  path: String,
  issues: MutableList<TemplateExtractionIssue>,
): TemplateNodeExtensionState {
  val extensionArray = extensionElement as? JsonArray ?: return TemplateNodeExtensionState()
  var contextExpression: TemplateExtractExpression? = null
  var valueExpression: TemplateExtractExpression? = null
  val retainedExtensions = mutableListOf<JsonElement>()

  extensionArray.forEach { extensionEntry ->
    val extensionObject = extensionEntry as? JsonObject
    val url = extensionObject?.get("url")?.jsonPrimitive?.contentOrNull
    when (url) {
      EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL -> {
        if (contextExpression != null) {
          issues +=
            TemplateExtractionIssue(
              severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Warning,
              code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Invalid,
              diagnostics =
                "Multiple templateExtractContext extensions were found. Only the first one will be used.",
              expressionPath = path,
            )
        } else {
          contextExpression = parseTemplateExpression(extensionObject, path, issues)
        }
      }

      EXTENSION_TEMPLATE_EXTRACT_VALUE_URL -> {
        if (valueExpression != null) {
          issues +=
            TemplateExtractionIssue(
              severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Warning,
              code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Invalid,
              diagnostics =
                "Multiple templateExtractValue extensions were found. Only the first one will be used.",
              expressionPath = path,
            )
        } else {
          valueExpression = parseTemplateExpression(extensionObject, path, issues)
        }
      }

      else -> retainedExtensions += extensionEntry
    }
  }

  return TemplateNodeExtensionState(
    controls =
      TemplateNodeControls(
        contextExpression = contextExpression,
        valueExpression = valueExpression,
      ),
    remainingExtensions = retainedExtensions.takeIf { it.isNotEmpty() }?.let(::JsonArray),
  )
}

private fun parseTemplateExpression(
  extensionObject: JsonObject,
  path: String,
  issues: MutableList<TemplateExtractionIssue>,
): TemplateExtractExpression? {
  extensionObject["valueString"]
    ?.jsonPrimitive
    ?.contentOrNull
    ?.takeIf { it.isNotBlank() }
    ?.let {
      return TemplateExtractExpression(expression = it)
    }

  val valueExpression =
    extensionObject["valueExpression"]?.jsonObject
      ?: run {
        issues +=
          TemplateExtractionIssue(
            severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Error,
            code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Invalid,
            diagnostics =
              "Template extraction extensions must declare valueString or valueExpression.",
            expressionPath = path,
          )
        return null
      }

  val language = valueExpression["language"]?.jsonPrimitive?.contentOrNull
  if (language != null && language != FHIRPATH_LANGUAGE) {
    issues +=
      TemplateExtractionIssue(
        severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Error,
        code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Invalid,
        diagnostics =
          "Only FHIRPath expressions are supported for template extraction. Found language '$language'.",
        expressionPath = path,
      )
    return null
  }

  val expression = valueExpression["expression"]?.jsonPrimitive?.contentOrNull
  if (expression.isNullOrBlank()) {
    issues +=
      TemplateExtractionIssue(
        severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Error,
        code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Required,
        diagnostics = "Template extraction expressions must include valueExpression.expression.",
        expressionPath = path,
      )
    return null
  }

  return TemplateExtractExpression(
    expression = expression,
    variableName = valueExpression["name"]?.jsonPrimitive?.contentOrNull,
  )
}
