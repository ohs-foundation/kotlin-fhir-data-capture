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

import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.Questionnaire
import kotlinx.serialization.descriptors.SerialDescriptor

// ********************************************************************************************** //
//                                                                                                //
// Definition extract extension parsing.                                                          //
//                                                                                                //
// These helpers translate raw SDC extensions into strongly typed extraction instructions that    //
// the engine can work with predictably.                                                          //
//                                                                                                //
// ********************************************************************************************** //

internal fun parseDefinitionExtract(extension: Extension): DefinitionExtractConfig {
  val definition =
    extension.extension.firstOrNull { it.url == "definition" }?.value?.asCanonical()?.value?.value
      ?: error("definitionExtract extension is missing its definition canonical")
  return DefinitionExtractConfig(
    definition = definition,
    fullUrlExpression = extension.extension.findStringValue("fullUrl"),
    ifNoneMatchExpression = extension.extension.findStringValue("ifNoneMatch"),
    ifModifiedSinceExpression = extension.extension.findStringValue("ifModifiedSince"),
    ifMatchExpression = extension.extension.findStringValue("ifMatch"),
    ifNoneExistExpression = extension.extension.findStringValue("ifNoneExist"),
  )
}

internal fun parseDefinitionExtractValue(extension: Extension): DefinitionExtractValueConfig {
  val definition =
    extension.extension
      .firstOrNull { it.url == "definition" }
      ?.value
      ?.let { value -> value.asUri()?.value?.value ?: value.asCanonical()?.value?.value }
      ?.let(::parseDefinitionPath)
      ?: error("definitionExtractValue extension is missing its definition uri")
  return DefinitionExtractValueConfig(
    definition = definition,
    expression =
      extension.extension.firstOrNull { it.url == "expression" }?.value?.asExpression()?.value,
    fixedValue = extension.extension.firstOrNull { it.url == "fixed-value" }?.value,
  )
}

private fun List<Extension>.findStringValue(url: String): String? =
  firstOrNull { it.url == url }
    ?.value
    ?.let { value ->
      value.asString()?.value?.value
        ?: value.asUri()?.value?.value
        ?: value.asCanonical()?.value?.value
        ?: value.asCode()?.value?.value
        ?: value.asMarkdown()?.value?.value
    }

// ********************************************************************************************** //
//                                                                                                //
// Definition path normalization.                                                                 //
//                                                                                                //
// StructureDefinition references may include sliced or polymorphic segments. These utilities     //
// normalize them into the concrete path names produced by the generated beta05 model.            //
//                                                                                                //
// ********************************************************************************************** //

internal fun parseDefinitionPath(rawDefinition: String): DefinitionPath {
  val canonical = rawDefinition.substringBefore("#")
  val elementId = rawDefinition.substringAfter("#")
  val resourceType = elementId.substringBefore(".")
  val pathSegments =
    elementId
      .substringAfter(".", missingDelimiterValue = "")
      .split('.')
      .filter { it.isNotBlank() }
      .map(::normalizeDefinitionSegment)
  return DefinitionPath(
    canonical = canonical,
    resourceType = resourceType,
    pathSegments = pathSegments,
  )
}

private fun normalizeDefinitionSegment(segment: String): String =
  when {
    segment.contains("[x]:") -> {
      val baseName = segment.substringBefore("[x]")
      val typeSlice = segment.substringAfter(':')
      if (typeSlice.startsWith(baseName)) {
        typeSlice
      } else {
        baseName + typeSlice.replaceFirstChar { it.uppercase() }
      }
    }

    segment.contains(":") -> segment.substringBefore(":")

    else -> segment.replace("[x]", "")
  }

internal fun computeItemAnchorPath(
  questionnaireItem: Questionnaire.Item,
  fullPath: List<String>,
): List<String> =
  when {
    fullPath.isEmpty() -> emptyList()
    questionnaireItem.isGroup() -> fullPath
    fullPath.size == 1 -> emptyList()
    else -> fullPath.dropLast(1)
  }

internal fun computeValueAnchorPath(fullPath: List<String>): List<String> =
  if (fullPath.size <= 1) emptyList() else fullPath.dropLast(1)

// ********************************************************************************************** //
//                                                                                                //
// Resource type resolution.                                                                      //
//                                                                                                //
// Root definition extracts may point at profiles rather than core StructureDefinition urls.      //
// These helpers resolve the concrete resource type and its descriptor from the generated model.  //
//                                                                                                //
// ********************************************************************************************** //

internal fun inferResourceType(definitionCanonical: String, scopePairs: List<ItemPair>): String {
  val canonicalWithoutVersion = definitionCanonical.substringBefore("|")
  val coreCandidate = canonicalWithoutVersion.substringAfterLast("/")
  if (GeneratedR4ResourceDescriptorRegistry.descriptorFor(coreCandidate) != null) {
    return coreCandidate
  }

  scopePairs
    .asSequence()
    .mapNotNull { pair -> pair.questionnaireItem.definition?.value?.let(::parseDefinitionPath) }
    .firstOrNull { it.canonical == definitionCanonical }
    ?.let {
      return it.resourceType
    }

  scopePairs
    .asSequence()
    .flatMap { pair -> pair.children.asSequence() }
    .mapNotNull { pair -> pair.questionnaireItem.definition?.value?.let(::parseDefinitionPath) }
    .firstOrNull { it.canonical == definitionCanonical }
    ?.let {
      return it.resourceType
    }

  error("Unable to infer resource type from definition '$definitionCanonical'.")
}

internal fun resourceDescriptor(resourceType: String): SerialDescriptor =
  GeneratedR4ResourceDescriptorRegistry.descriptorFor(resourceType)
    ?: error(
      "Definition-based extraction could not resolve a kotlin-fhir R4 serializer for resource type '$resourceType'."
    )
