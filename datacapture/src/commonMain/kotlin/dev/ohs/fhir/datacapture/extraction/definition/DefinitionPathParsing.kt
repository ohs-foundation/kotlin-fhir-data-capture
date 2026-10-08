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

import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.Questionnaire

internal val Questionnaire.definitionExtractExtensions: List<DefinitionExtractConfig>
  get() =
    extension.filter { it.url == EXTENSION_DEFINITION_EXTRACT_URL }.map(::parseDefinitionExtract)

internal val Questionnaire.Item.definitionExtractExtensions: List<DefinitionExtractConfig>
  get() =
    extension.filter { it.url == EXTENSION_DEFINITION_EXTRACT_URL }.map(::parseDefinitionExtract)

internal val List<Extension>.definitionExtractValueDefinitions: List<DefinitionExtractValueConfig>
  get() =
    filter { it.url == EXTENSION_DEFINITION_EXTRACT_VALUE_URL }.map(::parseDefinitionExtractValue)

private fun parseDefinitionExtract(extension: Extension): DefinitionExtractConfig {
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
      ?.asUri()
      ?.value
      ?.value
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
  firstOrNull { it.url == url }?.value?.asString()?.value?.value

/**
 * Parses a `Questionnaire.item.definition` or `definitionExtractValue.definition` value.
 *
 * The part before `#` is the scoped StructureDefinition/profile canonical and the part after `#` is
 * the element id/path that should be populated in the extracted resource.
 */
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

/** True when two `definitionExtract`/`item.definition` canonicals refer to the same profile. */
internal fun canonicalMatches(left: String, right: String): Boolean =
  left.substringBefore("|") == right.substringBefore("|")

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

internal fun List<String>.startsWithPath(prefix: List<String>): Boolean =
  size >= prefix.size && take(prefix.size) == prefix
