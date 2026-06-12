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

import dev.ohs.fhir.datacapture.extensions.isRepeatedGroup
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse

/**
 * Questionnaire and response alignment. Definition-based extraction walks questionnaire items
 * together with the matching response items. These helpers normalize repeated-group structure and
 * prune empty branches early.
 */
internal fun buildItemPairs(
  questionnaireItems: List<Questionnaire.Item>,
  responseItems: List<QuestionnaireResponse.Item>,
): List<ItemPair> {
  val responseItemsByLinkId = responseItems.groupBy { it.linkId.value.orEmpty() }
  return questionnaireItems.flatMap { questionnaireItem ->
    val itemLinkId = questionnaireItem.linkId.value.orEmpty()
    val matchingResponseItems = responseItemsByLinkId[itemLinkId].orEmpty()
    if (questionnaireItem.isRepeatedGroup) {
      matchingResponseItems.flatMap { responseItem ->
        responseItem.answer.map { answer ->
          val syntheticResponseItem =
            responseItem
              .toBuilder()
              .apply {
                this.answer = mutableListOf()
                this.item = answer.item.map { it.toBuilder() }.toMutableList()
              }
              .build()
          ItemPair(
            questionnaireItem = questionnaireItem,
            responseItem = syntheticResponseItem,
            children = buildItemPairs(questionnaireItem.item, answer.item),
          )
        }
      }
    } else {
      matchingResponseItems.map { responseItem ->
        val childResponseItems =
          when {
            questionnaireItem.isGroup() -> responseItem.item
            questionnaireItem.item.isNotEmpty() -> responseItem.answer.flatMap { it.item }
            else -> emptyList()
          }
        ItemPair(
          questionnaireItem = questionnaireItem,
          responseItem = responseItem,
          children = buildItemPairs(questionnaireItem.item, childResponseItems),
        )
      }
    }
  }
}

internal fun hasAnyContent(item: QuestionnaireResponse.Item): Boolean {
  if (item.answer.any { it.value != null }) {
    return true
  }
  if (item.item.any(::hasAnyContent)) {
    return true
  }
  return item.answer.any { answer -> answer.item.any(::hasAnyContent) }
}

internal fun Questionnaire.Item.isGroup(): Boolean =
  type.value == Questionnaire.QuestionnaireItemType.Group
