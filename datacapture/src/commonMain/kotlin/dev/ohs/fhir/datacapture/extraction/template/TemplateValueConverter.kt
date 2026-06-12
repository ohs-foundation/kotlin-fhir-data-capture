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

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.datacapture.fhirpath.FhirPathService
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathQuantity
import dev.ohs.fhir.fhirpath.types.FhirPathTime
import dev.ohs.fhir.model.r4.Address
import dev.ohs.fhir.model.r4.Annotation
import dev.ohs.fhir.model.r4.Attachment
import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Canonical
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.ContactPoint
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Id
import dev.ohs.fhir.model.r4.Identifier
import dev.ohs.fhir.model.r4.Instant
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.Markdown
import dev.ohs.fhir.model.r4.Oid
import dev.ohs.fhir.model.r4.Period
import dev.ohs.fhir.model.r4.PositiveInt
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Time
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.Url
import dev.ohs.fhir.model.r4.Uuid
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Converts evaluated FHIRPath results into JSON and typed FHIR request metadata. */
internal class TemplateValueConverter {
  private val json = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  /**
   * Converts one evaluated FHIRPath result into the JSON form expected by the template tree.
   *
   * This method accepts the runtime types produced by the FHIRPath engine as well as common Kotlin
   * primitives and Kotlin FHIR model types. Unsupported values are reported as extraction issues so
   * the caller can continue processing other template nodes.
   */
  fun toJsonElement(
    value: Any,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): JsonElement? =
    when (value) {
      is String -> JsonPrimitive(value)

      is Boolean -> JsonPrimitive(value)

      is Int -> JsonPrimitive(value)

      is Long -> JsonPrimitive(value)

      is Float -> JsonPrimitive(value)

      is Double -> JsonPrimitive(value)

      is BigDecimal -> JsonPrimitive(value.toString())

      is FhirPathDate -> JsonPrimitive(FhirPathService.convertToString(listOf(value)))

      is FhirPathDateTime -> JsonPrimitive(FhirPathService.convertToString(listOf(value)))

      is FhirPathTime -> JsonPrimitive(FhirPathService.convertToString(listOf(value)))

      is FhirPathQuantity ->
        JsonObject(
          buildMap {
            value.value?.let { put("value", JsonPrimitive(it.toString())) }
            value.unit?.let {
              put("code", JsonPrimitive(it))
              put("unit", JsonPrimitive(it))
            }
          }
        )

      is FhirString -> JsonPrimitive(value.value)

      is FhirBoolean -> JsonPrimitive(value.value)

      is Integer -> JsonPrimitive(value.value)

      is PositiveInt -> JsonPrimitive(value.value)

      is Decimal -> JsonPrimitive(value.value?.toString())

      is Date -> JsonPrimitive(value.value?.toString())

      is DateTime -> JsonPrimitive(value.value?.toString())

      is Time -> JsonPrimitive(value.value?.toString())

      is Uri -> JsonPrimitive(value.value)

      is Url -> JsonPrimitive(value.value)

      is Canonical -> JsonPrimitive(value.value)

      is Code -> JsonPrimitive(value.value)

      is Markdown -> JsonPrimitive(value.value)

      is Id -> JsonPrimitive(value.value)

      is Oid -> JsonPrimitive(value.value)

      is Uuid -> JsonPrimitive(value.value)

      is Quantity -> json.encodeToJsonElement(Quantity.serializer(), value)

      is Coding -> json.encodeToJsonElement(Coding.serializer(), value)

      is CodeableConcept -> json.encodeToJsonElement(CodeableConcept.serializer(), value)

      is Reference -> json.encodeToJsonElement(Reference.serializer(), value)

      is Attachment -> json.encodeToJsonElement(Attachment.serializer(), value)

      is Identifier -> json.encodeToJsonElement(Identifier.serializer(), value)

      is HumanName -> json.encodeToJsonElement(HumanName.serializer(), value)

      is Address -> json.encodeToJsonElement(Address.serializer(), value)

      is ContactPoint -> json.encodeToJsonElement(ContactPoint.serializer(), value)

      is Period -> json.encodeToJsonElement(Period.serializer(), value)

      is Annotation -> json.encodeToJsonElement(Annotation.serializer(), value)

      is Resource -> json.encodeToJsonElement(Resource.serializer(), value)

      else -> {
        issues +=
          TemplateExtractionIssue(
            severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Error,
            code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Invalid,
            diagnostics =
              "Unsupported extraction result type ${value::class.simpleName} for '$path'.",
            expressionPath = path,
          )
        null
      }
    }

  /**
   * Narrows [toJsonElement] to template positions that represent primitive FHIR JSON values.
   *
   * If an expression resolves to a complex object where the template expects a primitive, the
   * mismatch is recorded and `null` is returned so the caller can omit that assignment safely.
   */
  fun toPrimitiveJsonElement(
    value: Any,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): JsonElement? {
    val jsonValue = toJsonElement(value, path, issues) ?: return null
    if (jsonValue !is JsonPrimitive) {
      issues +=
        TemplateExtractionIssue(
          severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Error,
          code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Invalid,
          diagnostics =
            "Expression for '$path' resolved to a non-primitive value, but the template element is primitive.",
          expressionPath = path,
        )
      return null
    }
    return jsonValue
  }

  /**
   * Converts the first evaluated value into a string-compatible representation for request
   * metadata.
   *
   * SDC request directives such as `fullUrl`, `resourceId`, and conditional headers are singular,
   * so multiple results are downgraded to a warning and only the first value is used.
   */
  fun toStringValue(
    values: List<Any>,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): String? {
    if (values.isEmpty()) return null
    if (values.size > 1) {
      issues +=
        TemplateExtractionIssue(
          severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Warning,
          code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Invalid,
          diagnostics =
            "Expression for '$path' produced multiple values. Only the first value will be used.",
          expressionPath = path,
        )
    }
    return when (val value = values.first()) {
      is String -> value

      is Boolean,
      is Int,
      is Long,
      is Float,
      is Double,
      is BigDecimal,
      is FhirPathDate,
      is FhirPathDateTime,
      is FhirPathTime,
      is FhirPathQuantity,
      is FhirString,
      is FhirBoolean,
      is Integer,
      is Decimal,
      is Date,
      is DateTime,
      is Time,
      is Uri,
      is Url,
      is Canonical,
      is Code,
      is Markdown,
      is Id,
      is Oid,
      is Uuid -> FhirPathService.convertToString(listOf(value))

      else -> {
        issues +=
          TemplateExtractionIssue(
            severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Error,
            code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Invalid,
            diagnostics =
              "Expression for '$path' must resolve to a string-compatible value, but found ${value::class.simpleName}.",
            expressionPath = path,
          )
        null
      }
    }
  }

  /**
   * Parses a singular expression result into a FHIR `instant`.
   *
   * The extractor requires timezone-aware timestamps for bundle request metadata, so values without
   * a timezone offset are rejected and surfaced as extraction issues.
   */
  fun toInstantValue(
    values: List<Any>,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): Instant? {
    val value = toStringValue(values, path, issues)?.takeIf { it.isNotBlank() } ?: return null
    val instantRegex =
      Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})$")
    if (!instantRegex.matches(value)) {
      issues +=
        TemplateExtractionIssue(
          severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Error,
          code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Invalid,
          diagnostics =
            "Expression for '$path' must resolve to an instant with timezone information. Found '$value'.",
          expressionPath = path,
        )
      return null
    }
    return Instant(value = FhirDateTime.fromString(value))
  }

  /**
   * Rehydrates a processed JSON object back into a typed Kotlin FHIR [Resource].
   *
   * This is the final structural validation pass for one extracted resource. Any decoding failure
   * is treated as an extraction error for that template path.
   */
  fun jsonToResource(
    resourceJson: JsonObject,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): Resource? =
    try {
      json.decodeFromString<Resource>(resourceJson.toString())
    } catch (throwable: Throwable) {
      issues +=
        TemplateExtractionIssue(
          severity = dev.ohs.fhir.model.r4.OperationOutcome.IssueSeverity.Error,
          code = dev.ohs.fhir.model.r4.OperationOutcome.IssueType.Exception,
          diagnostics =
            "Extracted resource at '$path' could not be decoded back into a Kotlin FHIR model: ${throwable.message ?: throwable::class.simpleName}",
          expressionPath = path,
        )
      null
    }

  /** Serializes a typed Kotlin FHIR resource into a mutable JSON object for template processing. */
  fun resourceToJson(resource: Resource): JsonObject =
    json.encodeToJsonElement(Resource.serializer(), resource).jsonObject
}
