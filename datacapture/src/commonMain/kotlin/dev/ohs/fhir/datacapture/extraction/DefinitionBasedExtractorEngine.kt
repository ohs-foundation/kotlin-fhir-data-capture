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

import dev.ohs.fhir.datacapture.extensions.EXTENSION_DEFINITION_EXTRACT_VALUE_URL
import dev.ohs.fhir.datacapture.extensions.allocateIdVariableNames
import dev.ohs.fhir.datacapture.extensions.definitionExtractExtensions
import dev.ohs.fhir.datacapture.extensions.elementValue
import dev.ohs.fhir.datacapture.extensions.packRepeatedGroups
import dev.ohs.fhir.fhirpath.FhirPathEngine
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlinx.serialization.json.Json

internal object DefinitionBasedExtractorEngine {
  fun extract(questionnaire: Questionnaire, questionnaireResponse: QuestionnaireResponse): Bundle =
    DefinitionBasedExtractorSession().extract(questionnaire, questionnaireResponse)
}

/**
 * Holds runtime collaborators shared across the split extractor helpers.
 *
 * Keeping these services on a lightweight session object lets the implementation span multiple
 * focused files without repeatedly threading the same dependencies through every helper call.
 */
internal class DefinitionBasedExtractorSession {
  internal val json = Json {
    explicitNulls = false
    encodeDefaults = false
  }
  internal val fhirPathEngine = FhirPathEngine.forR4()
}

// ********************************************************************************************** //
//                                                                                                //
// Bundle extraction orchestration.                                                               //
//                                                                                                //
// The engine entrypoint expands repeated groups, evaluates definition extract directives, and
// //
// gathers all generated resources into a single transaction bundle.                              //
//                                                                                                //
// ********************************************************************************************** //

private fun DefinitionBasedExtractorSession.extract(
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
): Bundle {
  val packedResponse =
    questionnaireResponse.toBuilder().apply { packRepeatedGroups(questionnaire) }.build()
  val rootPairs = buildItemPairs(questionnaire.item, packedResponse.item)
  val rootAllocateIds =
    questionnaire.allocateIdVariableNames.associateWith { generateAllocatedFullUrl() }
  val extractedEntries = mutableListOf<DefinitionExtractedEntry>()

  questionnaire.definitionExtractExtensions.map(::parseDefinitionExtract).forEach {
    definitionExtract ->
    extractedEntries.add(
      extractScope(
        definitionExtract = definitionExtract,
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        scopeBase = packedResponse,
        scopeQuestionnaireItem = null,
        scopePairs = rootPairs,
        inheritedAllocateIds = rootAllocateIds,
      )
    )
  }

  walkPairsForDefinitionExtracts(
    pairs = rootPairs,
    questionnaire = questionnaire,
    questionnaireResponse = questionnaireResponse,
    inheritedAllocateIds = rootAllocateIds,
    outputEntries = extractedEntries,
  )

  require(extractedEntries.isNotEmpty()) {
    "No definition-based extraction instructions were found in the questionnaire."
  }

  return Bundle(
    type = Enumeration(value = Bundle.BundleType.Transaction),
    entry = extractedEntries.map(::toBundleEntry),
  )
}

// ********************************************************************************************** //
//                                                                                                //
// Scope extraction and recursive traversal.                                                      //
//                                                                                                //
// Each definition extract scope builds a single mutable resource tree. The traversal helpers
// //
// decide where questionnaire answers and explicit definitionExtractValue extensions land.
// //
//                                                                                                //
// ********************************************************************************************** //

private fun DefinitionBasedExtractorSession.extractScope(
  definitionExtract: DefinitionExtractConfig,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  scopeBase: Any,
  scopeQuestionnaireItem: Questionnaire.Item?,
  scopePairs: List<ItemPair>,
  inheritedAllocateIds: Map<String, String>,
): DefinitionExtractedEntry {
  val resourceType = inferResourceType(definitionExtract.definition, scopePairs)
  val rootDescriptor = resourceDescriptor(resourceType)
  val resourceNode = MutableJsonObject(rootDescriptor)
  val rootAnchor =
    AnchorContext(path = emptyList(), node = resourceNode, descriptor = rootDescriptor)
  val scopeCanonical = definitionExtract.definition
  val scopeResponseItem = scopeBase as? QuestionnaireResponse.Item

  if (scopeQuestionnaireItem == null) {
    applyDefinitionExtractValues(
      sourceExtensions = questionnaire.extension,
      scopeCanonical = scopeCanonical,
      questionnaire = questionnaire,
      questionnaireResponse = questionnaireResponse,
      base = questionnaireResponse,
      questionnaireItem = null,
      responseItem = null,
      allocateIds = inheritedAllocateIds,
      rootAnchor = rootAnchor,
      parentAnchor = rootAnchor,
      directAnchor = null,
    )
  }

  scopePairs.forEach {
    processPairInScope(
      pair = it,
      questionnaire = questionnaire,
      questionnaireResponse = questionnaireResponse,
      scopeCanonical = scopeCanonical,
      inheritedAllocateIds = inheritedAllocateIds,
      rootAnchor = rootAnchor,
      parentAnchor = rootAnchor,
    )
  }

  addProfileIfNeeded(resourceNode, definitionExtract.definition, resourceType)

  val resourceJson = resourceNode.toJsonObject(resourceType)
  val resourceId = resourceNode.values["id"]?.toJsonElement()?.asString()
  val requestMethod = if (resourceId.isNullOrBlank()) Bundle.HTTPVerb.Post else Bundle.HTTPVerb.Put
  val fullUrl =
    definitionExtract.fullUrlExpression
      ?.let {
        evaluateExpressionToString(
          expression = it,
          base = scopeBase,
          questionnaire = questionnaire,
          questionnaireResponse = questionnaireResponse,
          questionnaireItem = scopeQuestionnaireItem,
          responseItem = scopeResponseItem,
          allocateIds = inheritedAllocateIds,
        )
      }
      ?.takeIf { it.isNotBlank() } ?: generateAllocatedFullUrl()

  return DefinitionExtractedEntry(
    fullUrl = fullUrl,
    resourceJson = resourceJson,
    requestMethod = requestMethod,
    requestUrl = if (resourceId.isNullOrBlank()) resourceType else "$resourceType/$resourceId",
    ifNoneMatch =
      evaluateOptionalExpressionToString(
        expression = definitionExtract.ifNoneMatchExpression,
        base = scopeBase,
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        questionnaireItem = scopeQuestionnaireItem,
        responseItem = scopeResponseItem,
        allocateIds = inheritedAllocateIds,
      ),
    ifModifiedSince =
      evaluateOptionalExpressionToString(
        expression = definitionExtract.ifModifiedSinceExpression,
        base = scopeBase,
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        questionnaireItem = scopeQuestionnaireItem,
        responseItem = scopeResponseItem,
        allocateIds = inheritedAllocateIds,
      ),
    ifMatch =
      evaluateOptionalExpressionToString(
        expression = definitionExtract.ifMatchExpression,
        base = scopeBase,
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        questionnaireItem = scopeQuestionnaireItem,
        responseItem = scopeResponseItem,
        allocateIds = inheritedAllocateIds,
      ),
    ifNoneExist =
      evaluateOptionalExpressionToString(
        expression = definitionExtract.ifNoneExistExpression,
        base = scopeBase,
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        questionnaireItem = scopeQuestionnaireItem,
        responseItem = scopeResponseItem,
        allocateIds = inheritedAllocateIds,
      ),
  )
}

private fun DefinitionBasedExtractorSession.walkPairsForDefinitionExtracts(
  pairs: List<ItemPair>,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  inheritedAllocateIds: Map<String, String>,
  outputEntries: MutableList<DefinitionExtractedEntry>,
) {
  pairs.forEach { pair ->
    val pairAllocateIds =
      inheritedAllocateIds +
        pair.questionnaireItem.allocateIdVariableNames.associateWith { generateAllocatedFullUrl() }

    pair.questionnaireItem.definitionExtractExtensions.map(::parseDefinitionExtract).forEach {
      definitionExtract ->
      if (pair.questionnaireItem.repeats?.value == true && !pair.questionnaireItem.isGroup()) {
        pair.responseItem.answer.forEach { answer ->
          val syntheticResponseItem =
            pair.responseItem
              .toBuilder()
              .apply {
                this.answer = mutableListOf(answer.toBuilder())
                this.item = mutableListOf()
              }
              .build()
          val syntheticPair =
            ItemPair(
              questionnaireItem = pair.questionnaireItem,
              responseItem = syntheticResponseItem,
              children = buildItemPairs(pair.questionnaireItem.item, answer.item),
            )
          if (hasAnyContent(syntheticResponseItem)) {
            outputEntries.add(
              extractScope(
                definitionExtract = definitionExtract,
                questionnaire = questionnaire,
                questionnaireResponse = questionnaireResponse,
                scopeBase = syntheticResponseItem,
                scopeQuestionnaireItem = pair.questionnaireItem,
                scopePairs = listOf(syntheticPair),
                inheritedAllocateIds = pairAllocateIds,
              )
            )
          }
        }
      } else if (hasAnyContent(pair.responseItem)) {
        outputEntries.add(
          extractScope(
            definitionExtract = definitionExtract,
            questionnaire = questionnaire,
            questionnaireResponse = questionnaireResponse,
            scopeBase = pair.responseItem,
            scopeQuestionnaireItem = pair.questionnaireItem,
            scopePairs = listOf(pair),
            inheritedAllocateIds = pairAllocateIds,
          )
        )
      }
    }

    walkPairsForDefinitionExtracts(
      pairs = pair.children,
      questionnaire = questionnaire,
      questionnaireResponse = questionnaireResponse,
      inheritedAllocateIds = pairAllocateIds,
      outputEntries = outputEntries,
    )
  }
}

private fun DefinitionBasedExtractorSession.processPairInScope(
  pair: ItemPair,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  scopeCanonical: String,
  inheritedAllocateIds: Map<String, String>,
  rootAnchor: AnchorContext,
  parentAnchor: AnchorContext,
) {
  if (!hasAnyContent(pair.responseItem)) {
    return
  }

  val pairAllocateIds =
    inheritedAllocateIds +
      pair.questionnaireItem.allocateIdVariableNames.associateWith { generateAllocatedFullUrl() }

  val definitionPath =
    pair.questionnaireItem.definition?.value?.let(::parseDefinitionPath)?.takeIf {
      it.canonical == scopeCanonical
    }
  val directAnchor =
    definitionPath?.let { path ->
      val anchorPath = computeItemAnchorPath(pair.questionnaireItem, path.pathSegments)
      if (anchorPath == parentAnchor.path) {
        parentAnchor
      } else {
        ensureAnchor(parentAnchor = parentAnchor, anchorPath = anchorPath)
      }
    }

  if (!pair.questionnaireItem.isGroup() && definitionPath != null) {
    val answerValues = pair.responseItem.answer.mapNotNull { it.elementValue }
    if (answerValues.isNotEmpty()) {
      setPathValues(
        anchor = directAnchor ?: rootAnchor,
        fullPath = definitionPath.pathSegments,
        rawValues = answerValues,
      )
    }
  }

  applyDefinitionExtractValues(
    sourceExtensions = pair.questionnaireItem.extension,
    scopeCanonical = scopeCanonical,
    questionnaire = questionnaire,
    questionnaireResponse = questionnaireResponse,
    base = pair.responseItem,
    questionnaireItem = pair.questionnaireItem,
    responseItem = pair.responseItem,
    allocateIds = pairAllocateIds,
    rootAnchor = rootAnchor,
    parentAnchor = parentAnchor,
    directAnchor = directAnchor,
  )

  pair.children.forEach {
    processPairInScope(
      pair = it,
      questionnaire = questionnaire,
      questionnaireResponse = questionnaireResponse,
      scopeCanonical = scopeCanonical,
      inheritedAllocateIds = pairAllocateIds,
      rootAnchor = rootAnchor,
      parentAnchor = directAnchor ?: parentAnchor,
    )
  }
}

private fun DefinitionBasedExtractorSession.applyDefinitionExtractValues(
  sourceExtensions: List<Extension>,
  scopeCanonical: String,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  base: Any,
  questionnaireItem: Questionnaire.Item?,
  responseItem: QuestionnaireResponse.Item?,
  allocateIds: Map<String, String>,
  rootAnchor: AnchorContext,
  parentAnchor: AnchorContext,
  directAnchor: AnchorContext?,
) {
  sourceExtensions
    .filter { it.url == EXTENSION_DEFINITION_EXTRACT_VALUE_URL }
    .map(::parseDefinitionExtractValue)
    .filter { it.definition.canonical == scopeCanonical }
    .forEach { config ->
      val rawValues =
        config.expression?.let {
          evaluateExpression(
            expression = it,
            base = base,
            questionnaire = questionnaire,
            questionnaireResponse = questionnaireResponse,
            questionnaireItem = questionnaireItem,
            responseItem = responseItem,
            allocateIds = allocateIds,
          )
        } ?: config.fixedValue?.let(::fixedValueToRawValue)?.let(::listOf) ?: emptyList()
      if (rawValues.isEmpty()) {
        return@forEach
      }

      val targetAnchor =
        when {
          directAnchor != null &&
            config.definition.pathSegments.startsWithPath(directAnchor.path) -> directAnchor

          parentAnchor.path.isNotEmpty() &&
            config.definition.pathSegments.startsWithPath(parentAnchor.path) -> parentAnchor

          else ->
            ensureAnchor(
              parentAnchor = rootAnchor,
              anchorPath = computeValueAnchorPath(config.definition.pathSegments),
            )
        }

      setPathValues(
        anchor = targetAnchor,
        fullPath = config.definition.pathSegments,
        rawValues = rawValues,
      )
    }
}
