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

import dev.ohs.fhir.datacapture.extraction.DataExtractionException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val FHIRPATH_LANGUAGE = "text/fhirpath"

internal const val EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL: String =
  "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractContext"
internal const val EXTENSION_TEMPLATE_EXTRACT_VALUE_URL: String =
  "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue"
private val TEMPLATE_CONTROL_EXTENSION_URLS =
  setOf(EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL, EXTENSION_TEMPLATE_EXTRACT_VALUE_URL)

internal fun parseTemplateNodeExtensionState(
  extensionElement: JsonElement?,
  path: String,
): TemplateNodeExtensionState {
  val extensionArray = extensionElement as? JsonArray ?: return TemplateNodeExtensionState()
  val contextExtensions =
    extensionArray.filterExtensionsByUrl(EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL)
  val valueExtensions = extensionArray.filterExtensionsByUrl(EXTENSION_TEMPLATE_EXTRACT_VALUE_URL)
  val retainedExtensions =
    extensionArray.filterNot { extensionEntry ->
      (extensionEntry as? JsonObject)?.get("url")?.jsonPrimitive?.contentOrNull in
        TEMPLATE_CONTROL_EXTENSION_URLS
    }

  return TemplateNodeExtensionState(
    controls =
      TemplateNodeControls(
        contextExpression =
          contextExtensions.firstOrNull()?.let { parseTemplateExpression(it, path) },
        valueExpression = valueExtensions.firstOrNull()?.let { parseTemplateExpression(it, path) },
      ),
    remainingExtensions = retainedExtensions.takeIf { it.isNotEmpty() }?.let(::JsonArray),
  )
}

private fun JsonArray.filterExtensionsByUrl(url: String): List<JsonObject> =
  mapNotNull { extensionEntry ->
    (extensionEntry as? JsonObject)?.takeIf { it["url"]?.jsonPrimitive?.contentOrNull == url }
  }

private fun parseTemplateExpression(
  extensionObject: JsonObject,
  path: String,
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
        throw DataExtractionException(
          "Template extraction extensions must declare valueString or valueExpression for '$path'."
        )
      }

  val language = valueExpression["language"]?.jsonPrimitive?.contentOrNull
  if (language != null && language != FHIRPATH_LANGUAGE) {
    throw DataExtractionException(
      "Only FHIRPath expressions are supported for template extraction. Found language '$language' at '$path'."
    )
  }

  val expression = valueExpression["expression"]?.jsonPrimitive?.contentOrNull
  if (expression.isNullOrBlank()) {
    throw DataExtractionException(
      "Template extraction expressions must include valueExpression.expression for '$path'."
    )
  }

  return TemplateExtractExpression(
    expression = expression,
    variableName = valueExpression["name"]?.jsonPrimitive?.contentOrNull,
  )
}
