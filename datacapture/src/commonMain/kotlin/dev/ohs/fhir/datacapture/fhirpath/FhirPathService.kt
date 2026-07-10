/*
 * Copyright 2025-2026 Open Health Stack Foundation
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

import co.touchlab.kermit.Logger
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.datacapture.extraction.DataExtractionException
import dev.ohs.fhir.fhirpath.FhirPathEngine
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime as KotlinLocalTime
import kotlinx.datetime.YearMonth
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Centralized service for FHIRPath evaluation and normalization of raw engine results. */
internal object FhirPathService {
  private val r4FhirPathEngine = FhirPathEngine.forR4()
  private val json = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  /**
   * Evaluates the [expression] on the [resource] with optional [variables].
   *
   * @param expression The FHIRPath expression to evaluate.
   * @param resource The FHIR base value to evaluate the expression against.
   * @param variables Optional map of variables to use during evaluation.
   * @return The list of evaluation results.
   */
  fun evaluate(
    expression: String,
    resource: Any,
    variables: Map<String, Any?> = emptyMap(),
  ): List<Any> =
    runCatching { r4FhirPathEngine.evaluateExpression(expression, resource, variables).toList() }
      .onFailure { throwable ->
        Logger.e(
          "Failed to evaluate FHIRPath expression '$expression': ${throwable.message ?: "Unknown error"}",
          throwable,
        )
      }
      .getOrElse { emptyList() }

  /** Converts the FHIRPath evaluation [result] to a boolean. */
  fun convertToBoolean(result: List<Any>): Boolean {
    if (result.isEmpty()) return false
    if (result.size == 1) return result.first() as Boolean
    return result.isNotEmpty()
  }

  /** Converts the FHIRPath evaluation [results] to a string. */
  fun convertToString(results: List<Any>): String =
    when {
      results.isEmpty() -> ""
      results.size == 1 -> convertSingleResultToString(results.first())
      else -> results.joinToString(", ") { convertSingleResultToString(it) }
    }

  private fun convertSingleResultToString(value: Any): String =
    when (value) {
      is String -> value

      is Boolean,
      is Int,
      is Long,
      is Float,
      is Double -> value.toString()

      is BigDecimal -> value.toPlainString()

      is FhirPathDate -> value.toString()

      is FhirPathDateTime ->
        run {
          val month = value.month
          val day = value.day
          val hour = value.hour
          val minute = value.minute
          val second = value.second
          val utcOffset = value.utcOffset

          when {
            month == null -> FhirDateTime.Year(value.year).toString()

            day == null -> FhirDateTime.YearMonth(YearMonth(value.year, month)).toString()

            hour == null -> FhirDateTime.Date(LocalDate(value.year, month, day)).toString()

            minute != null && second != null && utcOffset != null -> {
              val wholeSeconds = second.toInt()
              val nanoseconds = second.rem(1).times(1_000_000_000.0).toInt()
              FhirDateTime.DateTime(
                  dateTime =
                    LocalDateTime(value.year, month, day, hour, minute, wholeSeconds, nanoseconds),
                  utcOffset = utcOffset,
                )
                .toString()
            }

            else ->
              buildString {
                // FHIRPath can retain partial time precision that the R4 dateTime primitive does
                // not model directly, so preserve the original shape for those intermediate
                // values.
                append(value.year.toString().padStart(4, '0'))
                append('-')
                append(month.toString().padStart(2, '0'))
                append('-')
                append(day.toString().padStart(2, '0'))
                append('T')
                append(hour.toString().padStart(2, '0'))
                minute?.let { minuteValue ->
                  append(':')
                  append(minuteValue.toString().padStart(2, '0'))
                  second?.let { secondValue ->
                    append(':')
                    val normalized =
                      if (secondValue % 1.0 == 0.0) {
                        secondValue.toInt().toString().padStart(2, '0')
                      } else {
                        secondValue.toString().padStart(2, '0')
                      }
                    append(normalized)
                  }
                }
                utcOffset?.let { append(it.toString()) }
              }
          }
        }

      is FhirPathTime ->
        run {
          val minute = value.minute
          val second = value.second

          when {
            minute == null -> value.hour.toString().padStart(2, '0')

            second == null -> KotlinLocalTime(value.hour, minute).toString()

            else -> {
              val wholeSeconds = second.toInt()
              val nanoseconds = second.rem(1).times(1_000_000_000.0).toInt()
              KotlinLocalTime(value.hour, minute, wholeSeconds, nanoseconds).toString()
            }
          }
        }

      is FhirPathQuantity -> value.value?.toString() ?: ""

      is FhirString -> value.value ?: ""

      is FhirBoolean -> value.value?.toString() ?: ""

      is Integer -> value.value?.toString() ?: ""

      is PositiveInt -> value.value?.toString() ?: ""

      is Decimal -> value.value?.toString() ?: ""

      is Date -> value.value?.toString() ?: ""

      is DateTime -> value.value?.toString() ?: ""

      is Time -> value.value?.toString() ?: ""

      is Uri -> value.value ?: ""

      is Url -> value.value ?: ""

      is Canonical -> value.value ?: ""

      is Code -> value.value ?: ""

      is Markdown -> value.value ?: ""

      is Id -> value.value ?: ""

      is Oid -> value.value ?: ""

      is Uuid -> value.value ?: ""

      is Coding -> value.display?.value ?: value.code?.value ?: ""

      is Quantity -> value.value?.value?.toString() ?: ""

      else -> value.toString()
    }

  /**
   * Converts one raw FHIRPath result into the JSON form expected by template extraction.
   *
   * The FHIRPath engine returns `Any`, so extraction needs a single normalization point that knows
   * how to serialize scalar engine types, Kotlin primitives, and Kotlin FHIR model classes.
   * Unsupported values are surfaced as fatal extraction errors because the template cannot decide
   * how to materialize them safely.
   */
  internal fun toJsonElement(value: Any, path: String): JsonElement =
    primitiveJsonElementOrNull(value)
      ?: fhirPathQuantityJsonElementOrNull(value)
      ?: structuredJsonElementOrNull(json, value)
      ?: throw DataExtractionException(
        "Unsupported extraction result type ${value::class.simpleName} for '$path'."
      )

  /**
   * Narrows [toJsonElement] to template locations that represent primitive FHIR JSON values.
   *
   * This guards primitive slots such as `_id` or `_valueString` from being replaced with a complex
   * object that the downstream JSON tree cannot legally attach to that position.
   */
  internal fun toPrimitiveJsonElement(value: Any, path: String): JsonElement {
    val jsonValue = toJsonElement(value, path)
    if (jsonValue !is JsonPrimitive) {
      throw DataExtractionException(
        "Expression for '$path' resolved to a non-primitive value, but the template element is primitive."
      )
    }
    return jsonValue
  }

  /**
   * Converts evaluated FHIRPath results into the singular string form used by request metadata.
   *
   * Template directives such as `resourceId`, `fullUrl`, and conditional request headers are
   * singular by contract. If evaluation returns multiple values, extraction keeps only the first so
   * bundle assembly can continue deterministically.
   */
  internal fun toStringValue(values: List<Any>, path: String): String? {
    if (values.isEmpty()) return null

    val firstValue = values.first()
    return when (firstValue) {
      is String,
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
      is PositiveInt,
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
      is Uuid -> convertToString(listOf(firstValue))

      else ->
        throw DataExtractionException(
          "Expression for '$path' must resolve to a string-compatible value, but found ${firstValue::class.simpleName}."
        )
    }
  }

  /**
   * Parses a singular FHIRPath result into a FHIR `instant` for conditional request metadata.
   *
   * The extractor requires a timezone-aware timestamp so the generated transaction bundle carries
   * an unambiguous HTTP date value.
   */
  internal fun toInstantValue(values: List<Any>, path: String): Instant? {
    val value = toStringValue(values, path)?.takeIf { it.isNotBlank() } ?: return null
    val instantRegex =
      Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})$")
    if (!instantRegex.matches(value)) {
      throw DataExtractionException(
        "Expression for '$path' must resolve to an instant with timezone information. Found '$value'."
      )
    }
    return Instant(value = FhirDateTime.fromString(value))
  }

  /**
   * Rehydrates a processed JSON object back into a typed Kotlin FHIR [Resource].
   *
   * This is the final structural validation pass for one extracted resource before it is attached
   * to the output transaction bundle. It delegates to the shared generated `Resource` polymorphic
   * serializer from the model dependency, so any R4 resource type exposed by that model is
   * supported here.
   */
  internal fun jsonToResource(resourceJson: JsonObject, path: String): Resource =
    try {
      json.decodeFromString<Resource>(resourceJson.toString())
    } catch (throwable: Throwable) {
      throw DataExtractionException(
        "Extracted resource at '$path' could not be decoded back into a Kotlin FHIR model: ${throwable.message ?: throwable::class.simpleName}",
        throwable,
      )
    }

  /**
   * Serializes a typed FHIR resource into a mutable JSON object for template tree processing.
   *
   * Extraction works against JSON because SDC template directives are attached to primitive
   * companion nodes such as `_valueString`, which are easier to rewrite before decoding back into
   * the typed model layer.
   */
  internal fun resourceToJson(resource: Resource): JsonObject =
    json.encodeToJsonElement(Resource.serializer(), resource).jsonObject

  /** Extracts the resource type from the given FHIRPath. */
  fun extractResourceTypeFromPath(fhirPath: String): String? {
    val trimmedPath = fhirPath.trim()
    val firstToken = trimmedPath.split('.', '(', '[', ' ').firstOrNull() ?: return null
    return firstToken.takeIf { it.firstOrNull()?.isUpperCase() == true }
  }

  /** Evaluates the [expression] on the [resource] and returns the result as a string. */
  fun evaluateFhirPathToString(expression: String, resource: Resource?): String {
    if (resource == null) return ""
    return convertToString(evaluate(expression, resource))
  }

  /**
   * Evaluates the [expressions] over the [data] resource and joins them to a space-separated
   * string.
   */
  fun evaluateToDisplay(expressions: List<String>, data: Resource) =
    expressions.joinToString(" ") { evaluateFhirPathToString(it, data) }
}

private fun FhirPathService.primitiveJsonElementOrNull(value: Any): JsonElement? =
  when (value) {
    is String -> JsonPrimitive(value)

    is Boolean -> JsonPrimitive(value)

    is Int -> JsonPrimitive(value)

    is Long -> JsonPrimitive(value)

    is Float -> JsonPrimitive(value)

    is Double -> JsonPrimitive(value)

    is BigDecimal -> JsonPrimitive(value.toString())

    is FhirPathDate,
    is FhirPathDateTime,
    is FhirPathTime -> JsonPrimitive(convertToString(listOf(value)))

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

    else -> null
  }

private fun fhirPathQuantityJsonElementOrNull(value: Any): JsonElement? =
  when (value) {
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

    else -> null
  }

private fun structuredJsonElementOrNull(json: Json, value: Any): JsonElement? =
  when (value) {
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
    else -> null
  }
