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

import co.touchlab.kermit.Logger
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.datacapture.fhirpath.FhirPathService
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathQuantity
import dev.ohs.fhir.fhirpath.types.FhirPathTime
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.Period
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Time
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Writes extracted answer/fixed/calculated values into the StructureDefinition path named by
 * `Questionnaire.item.definition` or `definitionExtractValue.definition`.
 *
 * This implements the spec rule that intermediate resource elements do not need matching
 * Questionnaire items; the extractor creates the missing backbone/data type objects as needed and
 * only enforces cardinality when the target leaf is singular.
 */
internal fun writeValuesToDefinitionPath(
  rootAnchor: AnchorContext,
  anchor: AnchorContext,
  fullPath: List<String>,
  rawValues: List<Any>,
) {
  val relativePath = fullPath.drop(anchor.path.size)
  if (relativePath.isEmpty()) {
    return
  }

  var currentNode = anchor.node
  var currentDescriptor = anchor.descriptor

  relativePath.dropLast(1).forEach { segment ->
    val fieldInfo = findFieldInfo(currentDescriptor, segment)
    currentNode = ensureObjectChild(currentNode, fieldInfo)
    currentDescriptor = currentNode.descriptor
  }

  val leafFieldInfo = findFieldInfo(currentDescriptor, relativePath.last(), rawValues)
  val leafName = leafFieldInfo.jsonName

  if (leafFieldInfo.isList) {
    val elementDescriptor = leafFieldInfo.descriptor.getElementDescriptor(0)
    val existingArray = currentNode.values[leafName] as? MutableJsonArray
    val targetArray = existingArray ?: MutableJsonArray().also { currentNode.values[leafName] = it }
    for (rawValue in rawValues) {
      targetArray.values.add(MutableJsonLiteral(encodeValueForField(rawValue, elementDescriptor)))
    }
    return
  }

  /**
   * Cardinality 0..1/1..1 elements can only hold one value. If the QuestionnaireResponse supplied
   * more than one answer for a singular element, keep the first-in-document-order value (consistent
   * with how repeated groups/answers are otherwise ordered) and warn instead of failing extraction
   * of the whole resource.
   */
  val singularElementValue =
    if (rawValues.size > 1) {
      Logger.w(
        "Element '$leafName' at path '${fullPath.joinToString(".")}' has cardinality 0..1/1..1 " +
          "but received ${rawValues.size} answers. Using the first answer and discarding the rest."
      )
      rawValues.first()
    } else {
      rawValues.single()
    }
  currentNode.values[leafName] =
    MutableJsonLiteral(encodeValueForField(singularElementValue, leafFieldInfo.descriptor))
}

private fun encodeValueForField(rawValue: Any, fieldDescriptor: SerialDescriptor): JsonElement {
  // Promotions: bare value -> a structurally different but compatible target field.
  // These are shape decisions, not encodings, so they still need to live here.
  if (
    fieldDescriptor.kind == StructureKind.CLASS &&
      isCodeableConceptShaped(fieldDescriptor) &&
      rawValue is Coding
  ) {
    return buildJsonObject {
      put(
        "coding",
        buildJsonArray {
          add(FhirPathService.toJsonElement(rawValue, path = fieldDescriptor.serialName))
        },
      )
    }
  }

  if (
    fieldDescriptor.kind == StructureKind.CLASS &&
      isReferenceShaped(fieldDescriptor) &&
      rawValue is String
  ) {
    return buildJsonObject { put("reference", JsonPrimitive(rawValue)) }
  }

  // Encode once via the shared serializer, then flatten to a sub-field if the target is
  // primitive. No per-type re-implementation of what toJsonElement already knows how to do.
  val encoded = FhirPathService.toJsonElement(rawValue, path = fieldDescriptor.serialName)

  if (fieldDescriptor.kind is PrimitiveKind && encoded is JsonObject) {
    val flattenedKey =
      when (rawValue) {
        is Coding -> "code"
        is Reference -> "reference"
        is Quantity -> "value".takeIf { fieldDescriptor.serialName.endsWith(".value") }
        else -> null
      }
    flattenedKey?.let { key ->
      return encoded[key] ?: JsonPrimitive("")
    }
  }

  return encoded
}

internal fun ensureDefinitionPathAnchor(
  parentAnchor: AnchorContext,
  anchorPath: List<String>,
  appendFinalListElement: Boolean = false,
): AnchorContext {
  require(anchorPath.startsWithPath(parentAnchor.path)) {
    "Anchor path '${anchorPath.joinToString(".")}' must extend parent anchor " +
      "'${parentAnchor.path.joinToString(".")}'."
  }

  val relativePath = anchorPath.drop(parentAnchor.path.size)

  var currentNode = parentAnchor.node
  var currentDescriptor = parentAnchor.descriptor

  relativePath.forEachIndexed { index, segment ->
    val fieldInfo = findFieldInfo(currentDescriptor, segment)
    val isFinalSegment = index == relativePath.lastIndex

    currentNode =
      ensureObjectChild(
        currentNode = currentNode,
        fieldInfo = fieldInfo,
        appendToList = appendFinalListElement && isFinalSegment,
      )

    currentDescriptor = currentNode.descriptor
  }

  return AnchorContext(path = anchorPath, node = currentNode, descriptor = currentDescriptor)
}

private fun ensureObjectChild(
  currentNode: MutableJsonObject,
  fieldInfo: FieldInfo,
  appendToList: Boolean = false,
): MutableJsonObject {
  if (fieldInfo.isList) {
    val array =
      (currentNode.values[fieldInfo.jsonName] as? MutableJsonArray)
        ?: MutableJsonArray().also { currentNode.values[fieldInfo.jsonName] = it }
    if (!appendToList && array.values.lastOrNull() is MutableJsonObject) {
      return array.values.last() as MutableJsonObject
    }
    val objectValue = MutableJsonObject(fieldInfo.descriptor.getElementDescriptor(0))
    array.values.add(objectValue)
    return objectValue
  }

  val existing = currentNode.values[fieldInfo.jsonName] as? MutableJsonObject
  if (existing != null) {
    return existing
  }
  require(fieldInfo.descriptor.kind == StructureKind.CLASS) {
    "Cannot descend into primitive field ${fieldInfo.jsonName}"
  }
  return MutableJsonObject(fieldInfo.descriptor).also {
    currentNode.values[fieldInfo.jsonName] = it
  }
}

/**
 * Resolves one StructureDefinition path segment to the field it maps to in the generated FHIR JSON
 * shape, handling choice-typed elements (e.g. `value[x]`) by picking the concrete variant matching
 * [rawValues].
 */
internal fun findFieldInfo(
  descriptor: SerialDescriptor,
  requestedName: String,
  rawValues: List<Any> = emptyList(),
): FieldInfo {
  val directIndex = descriptor.getElementIndex(requestedName)
  if (directIndex != CompositeDecoder.UNKNOWN_NAME) {
    val directDescriptor = descriptor.getElementDescriptor(directIndex)
    if (isChoiceContainer(requestedName, directDescriptor)) {
      val childName = resolveChoiceChildName(requestedName, directDescriptor, rawValues)
      val childIndex = directDescriptor.getElementIndex(childName)
      return FieldInfo(
        jsonName = childName,
        descriptor = directDescriptor.getElementDescriptor(childIndex),
        isList = false,
      )
    }
    return FieldInfo(
      jsonName = requestedName,
      descriptor = directDescriptor,
      isList = directDescriptor.kind == StructureKind.LIST,
    )
  }

  val flatChoiceCandidateNames =
    (0 until descriptor.elementsCount).map(descriptor::getElementName).filter { candidateName ->
      candidateName.startsWith(requestedName) &&
        candidateName.length > requestedName.length &&
        candidateName[requestedName.length].isUpperCase()
    }
  if (flatChoiceCandidateNames.isNotEmpty()) {
    val childName = resolveFlatChoiceChildName(requestedName, flatChoiceCandidateNames, rawValues)
    val childIndex = descriptor.getElementIndex(childName)
    return FieldInfo(
      jsonName = childName,
      descriptor = descriptor.getElementDescriptor(childIndex),
      isList = descriptor.getElementDescriptor(childIndex).kind == StructureKind.LIST,
    )
  }

  repeat(descriptor.elementsCount) { index ->
    val candidateName = descriptor.getElementName(index)
    val candidateDescriptor = descriptor.getElementDescriptor(index)
    if (!isChoiceContainer(candidateName, candidateDescriptor)) {
      return@repeat
    }
    val childIndex = candidateDescriptor.getElementIndex(requestedName)
    if (childIndex != CompositeDecoder.UNKNOWN_NAME) {
      return FieldInfo(
        jsonName = requestedName,
        descriptor = candidateDescriptor.getElementDescriptor(childIndex),
        isList = false,
      )
    }
  }

  error("Field '$requestedName' was not found in descriptor ${descriptor.serialName}")
}

private fun isChoiceContainer(fieldName: String, descriptor: SerialDescriptor): Boolean {
  if (descriptor.kind != StructureKind.CLASS || descriptor.elementsCount == 0) {
    return false
  }
  return (0 until descriptor.elementsCount).all { childIndex ->
    val childName = descriptor.getElementName(childIndex)
    childName.startsWith(fieldName) || childName.startsWith("_$fieldName")
  }
}

private fun resolveChoiceChildName(
  fieldName: String,
  descriptor: SerialDescriptor,
  rawValues: List<Any>,
): String {
  val rawValue =
    rawValues.firstOrNull() ?: error("Cannot resolve choice for '$fieldName' without a value.")
  val suffix = choiceTypeSuffix(rawValue)
  val candidate = "$fieldName$suffix"
  require(descriptor.getElementIndex(candidate) != CompositeDecoder.UNKNOWN_NAME) {
    "Choice field '$fieldName' does not support value type '$suffix'."
  }
  return candidate
}

private fun resolveFlatChoiceChildName(
  fieldName: String,
  candidateNames: List<String>,
  rawValues: List<Any>,
): String {
  val rawValue =
    rawValues.firstOrNull() ?: error("Cannot resolve choice for '$fieldName' without a value.")
  val suffix = choiceTypeSuffix(rawValue)
  val candidate = "$fieldName$suffix"
  require(candidate in candidateNames) {
    "Choice field '$fieldName' does not support value type '$suffix'."
  }
  return candidate
}

private fun choiceTypeSuffix(rawValue: Any): String =
  when (rawValue) {
    is Boolean,
    is dev.ohs.fhir.model.r4.Boolean -> "Boolean"

    is Int,
    is Integer -> "Integer"

    is BigDecimal,
    is Decimal -> "Decimal"

    is Date,
    is FhirPathDate -> "Date"

    is DateTime,
    is FhirPathDateTime -> "DateTime"

    is Time,
    is FhirPathTime -> "Time"

    is FhirString -> "String"

    is Quantity,
    is FhirPathQuantity -> "Quantity"

    is CodeableConcept -> "CodeableConcept"

    is Coding -> "Coding"

    is Reference -> "Reference"

    is Period -> "Period"

    else -> rawValue::class.simpleName ?: error("Unsupported choice type ${rawValue::class}")
  }

/**
 * Structural (not type-identity) checks used only for the bare-value promotions above.
 *
 * At this point the code only has the target field's [SerialDescriptor] to go on, not its concrete
 * FHIR class, so "does this descriptor declare the element a CodeableConcept/Reference would have"
 * is the best available signal. This is deliberately approximate: it can misfire if an unrelated
 * complex type ever declares an element literally named `coding` or `reference`.
 */
private fun isCodeableConceptShaped(descriptor: SerialDescriptor): Boolean =
  descriptor.kind == StructureKind.CLASS &&
    descriptor.getElementIndex("coding") != CompositeDecoder.UNKNOWN_NAME

private fun isReferenceShaped(descriptor: SerialDescriptor): Boolean =
  descriptor.kind == StructureKind.CLASS &&
    descriptor.getElementIndex("reference") != CompositeDecoder.UNKNOWN_NAME
