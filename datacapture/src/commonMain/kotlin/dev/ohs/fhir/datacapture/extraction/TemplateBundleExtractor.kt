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

import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.Resource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * High-level adapter that runs the template engine over a QuestionnaireResponse and turns the
 * resolved JSON-like output back into a FHIR Bundle.
 */
class TemplateBundleExtractor(
  private val resolveTemplateUseCase: ResolveTemplateUseCase =
    ResolveTemplateUseCase(DefaultTemplateRepository()),
  private val fhirJson: FhirR4Json = FhirR4Json(),
  private val templateJson: Json = Json { ignoreUnknownKeys = true },
) {
  /**
   * Resolves one or more mapping templates, executes them against the response payload, and
   * packages the resolved resources into a Bundle for logging or persistence.
   *
   * Implementers have two ways to supply templates:
   * - pass `templateJsons` explicitly, which is ideal when templates live in separate files
   * - omit `templateJsons` and let the extractor read them from questionnaire extensions
   */
  fun extract(
    questionnaireResponse: QuestionnaireResponse,
    questionnaire: Questionnaire? = null,
    templateJsons: List<String>? = null,
  ): Bundle {
    val templates =
      parseTemplates(
        templateJsons?.takeUnless { it.isEmpty() }
          ?: questionnaire?.mappingTemplatePayloads()?.takeUnless { it.isEmpty() }
          ?: error(
            "No extraction templates were provided and none were found on the questionnaire."
          )
      )

    val resourceNode =
      templateJson
        .parseToJsonElement(fhirJson.encodeToString(questionnaireResponse))
        .toDynamicNode()

    val resolved =
      resolveTemplateUseCase(
        TemplateExecutionRequest(
          resource = resourceNode,
          templates = templates,
          options = FpOptions(modelProfile = ModelProfile.FHIR_R4),
        )
      )

    return Bundle.Builder(type = Enumeration(value = Bundle.BundleType.Collection))
      .apply {
        entry =
          flattenCollection(resolved.values)
            .filterNotNull()
            .mapNotNull { value ->
              val resource =
                parseResource(value).getOrElse { error ->
                  println("Skipping invalid resource: ${error.message}")
                  return@mapNotNull null
                }

              Bundle.Entry.Builder().apply { this.resource = resource.toBuilder() }
            }
            .toMutableList()
      }
      .build()
  }

  /** Normalizes one or more template payloads into the engine's list contract. */
  private fun parseTemplates(templateJsons: List<String>): List<DynamicNode> =
    templateJsons.flatMap { templatePayload ->
      normalizeToCollection(templateJson.parseToJsonElement(templatePayload).toDynamicNode())
    }

  /** Parses one resolved template object back into a strongly typed FHIR resource. */
  private fun parseResource(value: DynamicNode): Result<Resource> {
    if (value !is Map<*, *>) {
      return Result.failure(
        IllegalArgumentException(
          "Template engine must return FHIR resource objects, but got ${value.describeDynamicType()}."
        )
      )
    }

    return runCatching { fhirJson.decodeFromString(value.toJsonElement().toString()) }
  }

  /** Reads every extraction-template payload declared on the questionnaire extension. */
  private fun Questionnaire.mappingTemplatePayloads(): List<String> =
    extension
      .filter { it.url == MAPPING_TEMPLATE_EXTENSION_URL }
      .mapNotNull { it.templatePayload() }

  private fun Extension.templatePayload(): String? =
    value?.asString()?.value?.value
      ?: value?.asMarkdown()?.value?.value
      ?: value?.asUri()?.value?.value
      ?: value?.asCanonical()?.value?.value
      ?: value?.asCode()?.value?.value

  private fun JsonElement.toDynamicNode(): DynamicNode =
    when (this) {
      JsonNull -> null

      is JsonArray -> map { it.toDynamicNode() }

      is JsonObject -> entries.associate { (key, value) -> key to value.toDynamicNode() }

      is JsonPrimitive -> {
        when {
          isString -> content
          booleanOrNull != null -> booleanOrNull
          longOrNull != null -> longOrNull
          doubleOrNull != null -> doubleOrNull
          else -> content
        }
      }
    }

  private fun DynamicNode.toJsonElement(): JsonElement =
    when (this) {
      null -> JsonNull

      is Map<*, *> ->
        JsonObject(
          entries.associate { (key, value) ->
            require(key is String) {
              "Resolved FHIR resource objects must use string keys, but found key '$key'."
            }
            key to value.toJsonElement()
          }
        )

      is List<*> -> JsonArray(map { it.toJsonElement() })

      is String -> JsonPrimitive(this)

      is Boolean -> JsonPrimitive(this)

      is Number -> templateJson.parseToJsonElement(toString())

      else -> error("Cannot convert ${describeDynamicType()} to JSON.")
    }

  private fun DynamicNode.describeDynamicType(): String =
    when (this) {
      null -> "null"
      is Map<*, *> -> "object"
      is List<*> -> "array"
      is String -> "string"
      is Boolean -> "boolean"
      is Number -> "number"
      else -> "unsupported value"
    }

  companion object {
    const val MAPPING_TEMPLATE_EXTENSION_URL: String =
      "http://dev.ohs.fhir/fhir-extensions/fhir-path-mapping-language"
  }
}
