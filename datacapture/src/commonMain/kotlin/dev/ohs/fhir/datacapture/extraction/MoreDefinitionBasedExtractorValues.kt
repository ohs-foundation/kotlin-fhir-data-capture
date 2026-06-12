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

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.datacapture.extensions.CORE_STRUCTURE_DEFINITION_PREFIX
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathQuantity
import dev.ohs.fhir.fhirpath.types.FhirPathTime
import dev.ohs.fhir.model.r4.Attachment
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Canonical
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.ContactPoint
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Identifier
import dev.ohs.fhir.model.r4.Instant as FhirInstant
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.Period
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Time
import dev.ohs.fhir.model.r4.Uri
import kotlin.random.Random
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

// ********************************************************************************************** //
//                                                                                                //
// Fixed values and primitive formatting.                                                         //
//                                                                                                //
// Definition-based extraction accepts both explicit fixed values and dynamically evaluated
// //
// expressions. These helpers normalize that data into plain values ready for descriptor-driven
// //
// encoding.                                                                                      //
//                                                                                                //
// ********************************************************************************************** //

internal fun fixedValueToRawValue(fixedValue: Extension.Value): Any =
  when (fixedValue) {
    is Extension.Value.Attachment -> fixedValue.value

    is Extension.Value.Boolean -> fixedValue.value

    is Extension.Value.Canonical -> fixedValue.value

    is Extension.Value.Code -> fixedValue.value

    is Extension.Value.CodeableConcept -> fixedValue.value

    is Extension.Value.Coding -> fixedValue.value

    is Extension.Value.ContactPoint -> fixedValue.value

    is Extension.Value.Date -> fixedValue.value

    is Extension.Value.DateTime -> fixedValue.value

    is Extension.Value.Decimal -> fixedValue.value

    is Extension.Value.HumanName -> fixedValue.value

    is Extension.Value.Identifier -> fixedValue.value

    is Extension.Value.Integer -> fixedValue.value

    is Extension.Value.Meta -> fixedValue.value

    is Extension.Value.Period -> fixedValue.value

    is Extension.Value.Quantity -> fixedValue.value

    is Extension.Value.Reference -> fixedValue.value

    is Extension.Value.String -> fixedValue.value

    is Extension.Value.Time -> fixedValue.value

    is Extension.Value.Uri -> fixedValue.value

    else ->
      error(
        "Unsupported fixed value type ${fixedValue::class.simpleName} in definition-based extraction"
      )
  }

internal fun generateAllocatedFullUrl(): String {
  val bytes = ByteArray(16)
  Random.nextBytes(bytes)
  bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
  bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
  val hex =
    bytes.joinToString(separator = "") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
  return "urn:uuid:${hex.substring(0, 8)}-${hex.substring(8, 12)}-${
    hex.substring(
      12,
      16,
    )
  }-${hex.substring(16, 20)}-${hex.substring(20)}"
}

internal fun stringifyValue(value: Any): String =
  when (value) {
    is Boolean -> value.toString()
    is Int -> value.toString()
    is Long -> value.toString()
    is BigDecimal -> value.toString()
    is FhirString -> value.value.orEmpty()
    is dev.ohs.fhir.model.r4.Boolean -> value.value?.toString().orEmpty()
    is Integer -> value.value?.toString().orEmpty()
    is Decimal -> value.value?.toString().orEmpty()
    is Date -> value.value?.toString().orEmpty()
    is DateTime -> value.value?.toString().orEmpty()
    is Time -> value.value?.toString().orEmpty()
    is Uri -> value.value.orEmpty()
    is Canonical -> value.value.orEmpty()
    is Code -> value.value.orEmpty()
    is Coding -> value.code?.value ?: value.display?.value.orEmpty()
    is Reference -> value.reference?.value.orEmpty()
    is FhirPathDate -> value.toString()
    is FhirPathDateTime -> formatFhirPathDateTime(value)
    is FhirPathTime -> formatFhirPathTime(value)
    is FhirPathQuantity -> listOfNotNull(value.value?.toString(), value.unit).joinToString(" ")
    else -> value.toString()
  }

private fun formatFhirPathDateTime(value: FhirPathDateTime): String {
  val year = value.year.toString().padStart(4, '0')
  val month = value.month?.toString()?.padStart(2, '0')
  val day = value.day?.toString()?.padStart(2, '0')
  val hour = value.hour.toString().padStart(2, '0')
  val minute = value.minute?.toString()?.padStart(2, '0')
  val second =
    value.second?.let {
      if (it.rem(1.0) == 0.0) {
        it.toInt().toString().padStart(2, '0')
      } else {
        it.toString().padStart(2, '0')
      }
    }
  return buildString {
    append(year)
    month?.let {
      append("-")
      append(it)
    }
    day?.let {
      append("-")
      append(it)
    }
    append("T")
    append(hour)
    minute?.let {
      append(":")
      append(it)
    }
    second?.let {
      append(":")
      append(it)
    }
    value.utcOffset?.let { append(it.toString()) }
  }
}

private fun formatFhirPathTime(value: FhirPathTime): String {
  val hour = value.hour.toString().padStart(2, '0')
  val minute = value.minute?.toString()?.padStart(2, '0')
  val second =
    value.second?.let {
      if (it.rem(1.0) == 0.0) {
        it.toInt().toString().padStart(2, '0')
      } else {
        it.toString().padStart(2, '0')
      }
    }
  return buildString {
    append(hour)
    minute?.let {
      append(":")
      append(it)
    }
    second?.let {
      append(":")
      append(it)
    }
  }
}

// ********************************************************************************************** //
//                                                                                                //
// Descriptor-aware value encoding and bundle entry conversion.                                   //
//                                                                                                //
// These helpers bridge the mutable JSON tree and the generated beta05 model so extracted values  //
// become strongly typed resources inside the final transaction bundle.                           //
//                                                                                                //
// ********************************************************************************************** //

internal fun DefinitionBasedExtractorSession.encodeValueForField(
  rawValue: Any,
  fieldDescriptor: SerialDescriptor,
): JsonElement {
  if (
    fieldDescriptor.kind == StructureKind.CLASS &&
      looksLikeCodeableConcept(fieldDescriptor) &&
      rawValue is Coding
  ) {
    return buildJsonObject {
      put("coding", buildJsonArray { add(json.encodeToJsonElement(Coding.serializer(), rawValue)) })
    }
  }

  if (
    fieldDescriptor.kind == StructureKind.CLASS &&
      looksLikeReference(fieldDescriptor) &&
      rawValue is kotlin.String
  ) {
    return buildJsonObject { put("reference", JsonPrimitive(rawValue)) }
  }

  return when (rawValue) {
    is kotlin.String -> JsonPrimitive(rawValue)

    is Boolean -> JsonPrimitive(rawValue)

    is Int -> JsonPrimitive(rawValue)

    is Long -> JsonPrimitive(rawValue)

    is BigDecimal -> JsonPrimitive(rawValue.toString())

    is FhirString -> JsonPrimitive(rawValue.value ?: "")

    is dev.ohs.fhir.model.r4.Boolean -> JsonPrimitive(rawValue.value ?: false)

    is Integer -> JsonPrimitive(rawValue.value ?: 0)

    is Decimal -> JsonPrimitive(rawValue.value?.toString() ?: "0")

    is Date -> JsonPrimitive(rawValue.value?.toString() ?: "")

    is DateTime -> JsonPrimitive(rawValue.value?.toString() ?: "")

    is Time -> JsonPrimitive(rawValue.value?.toString() ?: "")

    is Uri -> JsonPrimitive(rawValue.value ?: "")

    is Canonical -> JsonPrimitive(rawValue.value ?: "")

    is Code -> JsonPrimitive(rawValue.value ?: "")

    is Coding ->
      if (fieldDescriptor.kind is PrimitiveKind) {
        JsonPrimitive(rawValue.code?.value ?: "")
      } else {
        json.encodeToJsonElement(Coding.serializer(), rawValue)
      }

    is Reference ->
      if (fieldDescriptor.kind is PrimitiveKind) {
        JsonPrimitive(rawValue.reference?.value ?: "")
      } else {
        json.encodeToJsonElement(Reference.serializer(), rawValue)
      }

    is Quantity ->
      if (fieldDescriptor.kind is PrimitiveKind && fieldDescriptor.serialName.endsWith(".value")) {
        JsonPrimitive(rawValue.value?.value?.toString() ?: "")
      } else {
        json.encodeToJsonElement(Quantity.serializer(), rawValue)
      }

    is CodeableConcept -> json.encodeToJsonElement(CodeableConcept.serializer(), rawValue)

    is Identifier -> json.encodeToJsonElement(Identifier.serializer(), rawValue)

    is HumanName -> json.encodeToJsonElement(HumanName.serializer(), rawValue)

    is ContactPoint -> json.encodeToJsonElement(ContactPoint.serializer(), rawValue)

    is Meta -> json.encodeToJsonElement(Meta.serializer(), rawValue)

    is Period -> json.encodeToJsonElement(Period.serializer(), rawValue)

    is Attachment -> json.encodeToJsonElement(Attachment.serializer(), rawValue)

    is FhirPathDate -> JsonPrimitive(rawValue.toString())

    is FhirPathDateTime -> JsonPrimitive(formatFhirPathDateTime(rawValue))

    is FhirPathTime -> JsonPrimitive(formatFhirPathTime(rawValue))

    is FhirPathQuantity ->
      buildJsonObject {
        rawValue.value?.let { put("value", JsonPrimitive(it.toString())) }
        rawValue.unit?.let { put("unit", JsonPrimitive(it)) }
      }

    else ->
      error(
        "Unsupported value type ${rawValue::class.simpleName} for descriptor ${fieldDescriptor.serialName}"
      )
  }
}

internal fun addProfileIfNeeded(
  resourceNode: MutableJsonObject,
  definitionCanonical: String,
  resourceType: String,
) {
  val canonicalWithoutVersion = definitionCanonical.substringBefore("|")
  val coreCanonical = "$CORE_STRUCTURE_DEFINITION_PREFIX$resourceType"
  if (canonicalWithoutVersion == coreCanonical) {
    return
  }
  val metaNode =
    (resourceNode.values["meta"] as? MutableJsonObject)
      ?: MutableJsonObject(Meta.serializer().descriptor).also { resourceNode.values["meta"] = it }
  val profiles =
    (metaNode.values["profile"] as? MutableJsonArray)
      ?: MutableJsonArray().also { metaNode.values["profile"] = it }
  profiles.values.add(MutableJsonLiteral(JsonPrimitive(definitionCanonical)))
}

internal fun DefinitionBasedExtractorSession.toBundleEntry(
  extractedEntry: DefinitionExtractedEntry
): Bundle.Entry {
  val resource = json.decodeFromString<Resource>(extractedEntry.resourceJson.toString())
  return Bundle.Entry(
    fullUrl = Uri(value = extractedEntry.fullUrl),
    resource = resource,
    request =
      Bundle.Entry.Request(
        method = Enumeration(value = extractedEntry.requestMethod),
        url = Uri(value = extractedEntry.requestUrl),
        ifNoneMatch = extractedEntry.ifNoneMatch?.let(::FhirString),
        ifModifiedSince = extractedEntry.ifModifiedSince?.let(::toFhirInstant),
        ifMatch = extractedEntry.ifMatch?.let(::FhirString),
        ifNoneExist = extractedEntry.ifNoneExist?.let(::FhirString),
      ),
  )
}

private fun toFhirInstant(value: kotlin.String): FhirInstant? =
  FhirDateTime.fromString(value)?.let { FhirInstant(value = it) }

internal fun JsonElement.asString(): kotlin.String? =
  (this as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
