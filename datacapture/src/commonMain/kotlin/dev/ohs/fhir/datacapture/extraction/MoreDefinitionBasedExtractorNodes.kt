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
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathQuantity
import dev.ohs.fhir.fhirpath.types.FhirPathTime
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Element
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Uri
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder

// ********************************************************************************************** //
//                                                                                                //
// Mutable resource tree construction.                                                            //
//                                                                                                //
// These helpers navigate serializer descriptors, create missing object/array nodes, and write    //
// concrete values to the generated beta05 field names expected by the model.                     //
//                                                                                                //
// ********************************************************************************************** //

internal fun DefinitionBasedExtractorSession.setPathValues(
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
    rawValues
      .map { encodeValueForField(it, elementDescriptor) }
      .forEach { targetArray.values.add(MutableJsonLiteral(it)) }
    return
  }

  require(rawValues.size == 1) {
    "Multiple values cannot be assigned to singular field '$leafName'."
  }
  currentNode.values[leafName] =
    MutableJsonLiteral(encodeValueForField(rawValues.single(), leafFieldInfo.descriptor))
}

internal fun ensureAnchor(parentAnchor: AnchorContext, anchorPath: List<String>): AnchorContext {
  require(anchorPath.startsWithPath(parentAnchor.path)) {
    "Anchor path ${anchorPath.joinToString(".")} must extend parent anchor ${
      parentAnchor.path.joinToString(
        "."
      )
    }"
  }

  var currentNode = parentAnchor.node
  var currentDescriptor = parentAnchor.descriptor

  anchorPath.drop(parentAnchor.path.size).forEach { segment ->
    val fieldInfo = findFieldInfo(currentDescriptor, segment)
    currentNode = ensureObjectChild(currentNode, fieldInfo, appendToList = true)
    currentDescriptor = currentNode.descriptor
  }

  return AnchorContext(anchorPath, currentNode, currentDescriptor)
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

// ********************************************************************************************** //
//                                                                                                //
// Polymorphic descriptor resolution.                                                             //
//                                                                                                //
// Beta05 flattens many FHIR choice fields into concrete generated names such as `valueBoolean`   //
// or `valueCodeableConcept`. These helpers choose the best matching field for each raw answer.   //
//                                                                                                //
// ********************************************************************************************** //

private fun findFieldInfo(
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

  if (rawValues.isNotEmpty()) {
    val flatChoiceCandidates = findFlatChoiceCandidates(descriptor, requestedName)
    if (flatChoiceCandidates.isNotEmpty()) {
      return resolveFlatChoiceFieldInfo(requestedName, flatChoiceCandidates, rawValues)
    }
  }

  error("Field '$requestedName' was not found in descriptor ${descriptor.serialName}")
}

private fun findFlatChoiceCandidates(
  descriptor: SerialDescriptor,
  fieldName: String,
): List<ChoiceCandidate> =
  (0 until descriptor.elementsCount).mapNotNull { index ->
    val candidateName = descriptor.getElementName(index)
    val suffix = candidateName.choiceSuffixFor(fieldName) ?: return@mapNotNull null
    ChoiceCandidate(
      jsonName = candidateName,
      suffix = suffix,
      descriptor = descriptor.getElementDescriptor(index),
    )
  }

private fun String.choiceSuffixFor(fieldName: String): String? {
  if (!startsWith(fieldName) || length == fieldName.length) {
    return null
  }
  val suffix = removePrefix(fieldName)
  return suffix.takeIf { it.firstOrNull()?.isUpperCase() == true }
}

private fun resolveFlatChoiceFieldInfo(
  fieldName: String,
  candidates: List<ChoiceCandidate>,
  rawValues: List<Any>,
): FieldInfo {
  val rankedCandidates =
    candidates.mapNotNull { candidate ->
      val totalRank =
        rawValues.fold(0) { rank, rawValue ->
          val matchRank = choiceMatchRank(candidate.suffix, rawValue) ?: return@mapNotNull null
          rank + matchRank
        }
      candidate to totalRank
    }

  require(rankedCandidates.isNotEmpty()) {
    "Choice field '$fieldName' does not support values ${
      rawValues.joinToString { it::class.simpleName ?: "unknown" }
    }. Available fields: ${candidates.joinToString { it.jsonName }}."
  }

  val bestRank = rankedCandidates.minOf { it.second }
  val bestCandidates = rankedCandidates.filter { it.second == bestRank }.map { it.first }
  require(bestCandidates.size == 1) {
    "Choice field '$fieldName' is ambiguous for values ${
      rawValues.joinToString { it::class.simpleName ?: "unknown" }
    }. Matching fields: ${bestCandidates.joinToString { it.jsonName }}."
  }

  return FieldInfo(
    jsonName = bestCandidates.single().jsonName,
    descriptor = bestCandidates.single().descriptor,
    isList = false,
  )
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
  require(rawValues.isNotEmpty()) { "Cannot resolve choice for '$fieldName' without a value." }
  val candidates = findFlatChoiceCandidates(descriptor, fieldName)
  require(candidates.isNotEmpty()) {
    "Choice field '$fieldName' does not expose any typed fields in ${descriptor.serialName}."
  }
  return resolveFlatChoiceFieldInfo(fieldName, candidates, rawValues).jsonName
}

private fun choiceMatchRank(choiceSuffix: String, rawValue: Any): Int? =
  when (rawValue) {
    is Element -> elementChoiceMatchRank(choiceSuffix, rawValue)

    is Boolean -> if (choiceSuffix == "Boolean") 0 else null

    is Int ->
      when {
        choiceSuffix == "Integer" -> 0
        choiceSuffix == "Decimal" -> 1
        rawValue >= 0 && choiceSuffix in numericChoiceSuffixes -> 1
        else -> null
      }

    is BigDecimal -> if (choiceSuffix == "Decimal") 0 else null

    is kotlin.String ->
      when {
        choiceSuffix == "String" -> 0
        choiceSuffix in stringLikeChoiceSuffixes -> 1
        choiceSuffix == "Reference" -> 2
        else -> null
      }

    is FhirPathDate -> if (choiceSuffix == "Date") 0 else null

    is FhirPathDateTime ->
      when (choiceSuffix) {
        "DateTime" -> 0
        "Instant" -> 1
        else -> null
      }

    is FhirPathTime -> if (choiceSuffix == "Time") 0 else null

    is FhirPathQuantity -> if (choiceSuffix == "Quantity") 0 else null

    else -> null
  }

private fun elementChoiceMatchRank(choiceSuffix: String, rawValue: Element): Int? {
  val exactName = rawValue::class.simpleName
  if (exactName == choiceSuffix) {
    return 0
  }

  return when (rawValue) {
    is Coding ->
      when {
        choiceSuffix == "CodeableConcept" -> 1
        else -> null
      }

    is FhirString ->
      when {
        choiceSuffix in stringLikeChoiceSuffixes -> 1
        choiceSuffix == "Reference" -> 2
        else -> null
      }

    is Uri ->
      when {
        choiceSuffix in stringLikeChoiceSuffixes -> 1
        else -> null
      }

    is Integer ->
      rawValue.value?.let { intValue ->
        when {
          choiceSuffix == "Decimal" -> 1
          intValue >= 0 && choiceSuffix in numericChoiceSuffixes -> 1
          else -> null
        }
      }

    else -> null
  }
}

private val numericChoiceSuffixes = setOf("PositiveInt", "UnsignedInt")

private val stringLikeChoiceSuffixes =
  setOf("String", "Uri", "Url", "Canonical", "Oid", "Uuid", "Code", "Id", "Markdown")

internal fun looksLikeCodeableConcept(descriptor: SerialDescriptor): Boolean =
  descriptor.kind == StructureKind.CLASS &&
    descriptor.getElementIndex("coding") != CompositeDecoder.UNKNOWN_NAME

internal fun looksLikeReference(descriptor: SerialDescriptor): Boolean =
  descriptor.kind == StructureKind.CLASS &&
    descriptor.getElementIndex("reference") != CompositeDecoder.UNKNOWN_NAME

internal fun List<String>.startsWithPath(prefix: List<String>): Boolean =
  size >= prefix.size && take(prefix.size) == prefix
