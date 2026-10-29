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

import dev.ohs.fhir.datacapture.extensions.allocateIdVariableNames
import dev.ohs.fhir.datacapture.extensions.elementValue
import dev.ohs.fhir.datacapture.extensions.generateAllocatedFullUrl
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlinx.serialization.json.JsonObject

/**
 * Recursively finds nested `definitionExtract` scopes below the Questionnaire root.
 *
 * The SDC definition-based rules say that item-level `definitionExtract` directives only create a
 * resource when the corresponding QuestionnaireResponse item has content, and that repeating items
 * create a distinct resource per repetition. This traversal enforces both rules while also carrying
 * inherited `extractAllocateId` variables down the tree.
 */
internal fun extractNestedDefinitionScopeEntries(
  pairs: List<QuestionnaireItemResponsePair>,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  inheritedAllocateIds: Map<String, String>,
  resolveProfileResourceType: ((String) -> String?)?,
): List<JsonObject> =
  pairs.flatMap { pair ->
    val pairAllocateIds =
      inheritedAllocateIds +
        pair.questionnaireItem.allocateIdVariableNames.associateWith { generateAllocatedFullUrl() }

    extractEntriesForDefinitionScopePair(
      pair = pair,
      questionnaire = questionnaire,
      questionnaireResponse = questionnaireResponse,
      inheritedAllocateIds = pairAllocateIds,
      resolveProfileResourceType = resolveProfileResourceType,
    ) +
      extractNestedDefinitionScopeEntries(
        pairs = pair.children,
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        inheritedAllocateIds = pairAllocateIds,
        resolveProfileResourceType = resolveProfileResourceType,
      )
  }

private fun extractEntriesForDefinitionScopePair(
  pair: QuestionnaireItemResponsePair,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  inheritedAllocateIds: Map<String, String>,
  resolveProfileResourceType: ((String) -> String?)?,
): List<JsonObject> =
  pair.questionnaireItem.definitionExtractExtensions.flatMap { definitionExtract ->
    val scopeDescription =
      "item '${pair.questionnaireItem.linkId.value.orEmpty()}' definition '${definitionExtract.definition}'"

    when {
      pair.questionnaireItem.repeats?.value == true && !pair.questionnaireItem.isGroup() ->
        pair.responseItem.answer
          .map { answer ->
            val syntheticResponseItem =
              pair.responseItem
                .toBuilder()
                .apply {
                  this.answer = mutableListOf(answer.toBuilder())
                  this.item = mutableListOf()
                }
                .build()
            val syntheticPair =
              QuestionnaireItemResponsePair(
                questionnaireItem = pair.questionnaireItem,
                responseItem = syntheticResponseItem,
                children =
                  alignQuestionnaireItemsWithResponseItems(pair.questionnaireItem.item, answer.item),
              )
            syntheticResponseItem to syntheticPair
          }
          .filter { (syntheticResponseItem, _) -> hasResponseContent(syntheticResponseItem) }
          .mapNotNull { (syntheticResponseItem, syntheticPair) ->
            materializeValidEntryOrNull(scopeDescription = scopeDescription) {
              extractBundleEntryForDefinitionScope(
                definitionExtract = definitionExtract,
                questionnaire = questionnaire,
                questionnaireResponse = questionnaireResponse,
                scopeBase = syntheticResponseItem,
                scopeQuestionnaireItem = pair.questionnaireItem,
                scopePairs = listOf(syntheticPair),
                inheritedAllocateIds = inheritedAllocateIds,
                resolveProfileResourceType = resolveProfileResourceType,
              )
            }
          }

      hasResponseContent(pair.responseItem) ->
        listOfNotNull(
          materializeValidEntryOrNull(scopeDescription = scopeDescription) {
            extractBundleEntryForDefinitionScope(
              definitionExtract = definitionExtract,
              questionnaire = questionnaire,
              questionnaireResponse = questionnaireResponse,
              scopeBase = pair.responseItem,
              scopeQuestionnaireItem = pair.questionnaireItem,
              scopePairs = listOf(pair),
              inheritedAllocateIds = inheritedAllocateIds,
              resolveProfileResourceType = resolveProfileResourceType,
            )
          }
        )

      else -> emptyList()
    }
  }

/**
 * Applies one aligned Questionnaire/QuestionnaireResponse item pair to the current resource scope.
 *
 * If the item's `Questionnaire.item.definition` belongs to the current `definitionExtract`
 * canonical, its answers populate the matching StructureDefinition path. Group items act as anchors
 * for repeated backbone/collection elements so descendant answers land in the same extracted object
 * instance, as described by the SDC parent/child matching rules.
 */
internal fun applyQuestionnairePairToDefinitionScope(
  pair: QuestionnaireItemResponsePair,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  scopeCanonical: String,
  inheritedAllocateIds: Map<String, String>,
  rootAnchor: AnchorContext,
  parentAnchor: AnchorContext,
) {
  if (!hasResponseContent(pair.responseItem)) {
    return
  }

  val pairAllocateIds =
    inheritedAllocateIds +
      pair.questionnaireItem.allocateIdVariableNames.associateWith { generateAllocatedFullUrl() }

  val definitionPath =
    pair.questionnaireItem.definition?.value?.let(::parseDefinitionPath)?.takeIf {
      canonicalMatches(it.canonical, scopeCanonical)
    }
  val directAnchor =
    definitionPath?.let { path ->
      val anchorPath =
        computeItemAnchorPath(
          questionnaireItem = pair.questionnaireItem,
          fullPath = path.pathSegments,
        )

      if (anchorPath == parentAnchor.path) {
        parentAnchor
      } else {
        ensureDefinitionPathAnchor(
          parentAnchor = parentAnchor,
          anchorPath = anchorPath,
          appendFinalListElement = pair.questionnaireItem.isGroup(),
        )
      }
    }

  if (!pair.questionnaireItem.isGroup() && definitionPath != null) {
    val answerValues = pair.responseItem.answer.mapNotNull { it.elementValue }
    if (answerValues.isNotEmpty()) {
      writeValuesToDefinitionPath(
        rootAnchor = rootAnchor,
        anchor = directAnchor ?: rootAnchor,
        fullPath = definitionPath.pathSegments,
        rawValues = answerValues,
      )
    }
  }

  applyDefinitionExtractValueDirectives(
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
    applyQuestionnairePairToDefinitionScope(
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
