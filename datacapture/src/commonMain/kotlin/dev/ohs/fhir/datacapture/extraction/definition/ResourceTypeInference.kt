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

import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.Questionnaire
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonPrimitive

private const val CORE_STRUCTURE_DEFINITION_BASE = "http://hl7.org/fhir/StructureDefinition/"

internal fun inferResourceType(
  definitionCanonical: String,
  questionnaire: Questionnaire,
  scopeQuestionnaireItem: Questionnaire.Item?,
  scopePairs: List<QuestionnaireItemResponsePair>,
  resolveProfileResourceType: ((String) -> String?)?,
): String {
  val canonicalWithoutVersion = definitionCanonical.substringBefore("|")
  val coreCandidate = canonicalWithoutVersion.substringAfterLast("/")
  if (isSupportedResourceType(coreCandidate)) {
    return coreCandidate
  }

  val hintedResourceTypes =
    collectResourceTypeHints(questionnaire, scopeQuestionnaireItem, scopePairs)
      .filter { definitionPath -> canonicalMatches(definitionPath.canonical, definitionCanonical) }
      .map(DefinitionPath::resourceType)
      .distinct()
      .toList()

  when (hintedResourceTypes.size) {
    1 -> return hintedResourceTypes.single()

    0 -> Unit

    else ->
      error(
        "Conflicting resource type hints ${hintedResourceTypes.joinToString()} were found for definition '$definitionCanonical'."
      )
  }

  resolveProfileResourceType?.invoke(definitionCanonical)?.let { resolvedResourceType ->
    require(isSupportedResourceType(resolvedResourceType)) {
      "Profile resource type resolver returned unsupported resource type '$resolvedResourceType' for definition '$definitionCanonical'."
    }
    return resolvedResourceType
  }

  error(
    "Unable to infer resource type from definition '$definitionCanonical'. Add a matching item.definition or definitionExtractValue.definition hint, or provide a profile resource type resolver."
  )
}

private fun collectResourceTypeHints(
  questionnaire: Questionnaire,
  scopeQuestionnaireItem: Questionnaire.Item?,
  scopePairs: List<QuestionnaireItemResponsePair>,
): Sequence<DefinitionPath> = sequence {
  if (scopeQuestionnaireItem == null) {
    yieldAll(
      questionnaire.extension.definitionExtractValueDefinitions.asSequence().map { it.definition }
    )
  }
  scopePairs.forEach { pair -> yieldAll(pair.resourceTypeHintsRecursively()) }
}

private fun QuestionnaireItemResponsePair.resourceTypeHintsRecursively(): Sequence<DefinitionPath> =
  sequence {
    questionnaireItem.definition?.value?.let(::parseDefinitionPath)?.let { definitionPath ->
      yield(definitionPath)
    }
    yieldAll(
      questionnaireItem.extension.definitionExtractValueDefinitions.asSequence().map {
        it.definition
      }
    )
    children.forEach { child -> yieldAll(child.resourceTypeHintsRecursively()) }
  }

internal fun resourceDescriptor(resourceType: String): SerialDescriptor =
  DefinitionExtractResourceRegistry.descriptorFor(resourceType)
    ?: error(
      "Definition-based extraction could not resolve a descriptor for resource type: $resourceType."
    )

private fun isSupportedResourceType(resourceType: String): Boolean =
  resourceType in DefinitionExtractResourceRegistry.supportedResourceTypes

internal fun addProfileIfNeeded(
  resourceNode: MutableJsonObject,
  definitionCanonical: String,
  resourceType: String,
) {
  val canonicalWithoutVersion = definitionCanonical.substringBefore("|")
  val coreCanonical = "$CORE_STRUCTURE_DEFINITION_BASE$resourceType"
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
