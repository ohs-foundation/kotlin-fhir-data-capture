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
import dev.ohs.fhir.datacapture.extensions.generateAllocatedFullUrl
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val json = Json {
  explicitNulls = false
  encodeDefaults = false
}

/**
 * Executes one `definitionExtract` scope and returns the `Bundle.entry` JSON for that scope.
 *
 * This is the spec's "initiate resource extraction" step. It creates the stub resource identified
 * by the `definitionExtract.definition`, applies any root/item `definitionExtractValue` directives
 * in scope, walks descendant items whose `Questionnaire.item.definition` canonical matches the
 * scoped canonical, and then assembles request metadata such as `fullUrl`, `method`, and
 * conditional headers.
 */
internal fun extractBundleEntryForDefinitionScope(
  definitionExtract: DefinitionExtractConfig,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  scopeBase: Any,
  scopeQuestionnaireItem: Questionnaire.Item?,
  scopePairs: List<QuestionnaireItemResponsePair>,
  inheritedAllocateIds: Map<String, String>,
  resolveProfileResourceType: ((String) -> String?)?,
): JsonObject {
  val resourceType =
    inferResourceType(
      definitionCanonical = definitionExtract.definition,
      questionnaire = questionnaire,
      scopeQuestionnaireItem = scopeQuestionnaireItem,
      scopePairs = scopePairs,
      resolveProfileResourceType = resolveProfileResourceType,
    )
  val rootDescriptor = resourceDescriptor(resourceType)
  val resourceNode = MutableJsonObject(rootDescriptor)
  val rootAnchor =
    AnchorContext(path = emptyList(), node = resourceNode, descriptor = rootDescriptor)
  val scopeCanonical = definitionExtract.definition

  if (scopeQuestionnaireItem == null) {
    applyDefinitionExtractValueDirectives(
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
    applyQuestionnairePairToDefinitionScope(
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
  val requestMethod = if (resourceId.isNullOrBlank()) "POST" else "PUT"

  return buildJsonObject {
    val fullUrl =
      definitionExtract.fullUrlExpression
        ?.let {
          evaluateDefinitionExtractExpressionToString(
            expression = it,
            base = scopeBase,
            questionnaire = questionnaire,
            questionnaireResponse = questionnaireResponse,
            questionnaireItem = scopeQuestionnaireItem,
            responseItem = scopeBase as? QuestionnaireResponse.Item,
            allocateIds = inheritedAllocateIds,
          )
        }
        ?.takeIf { it.isNotBlank() } ?: generateAllocatedFullUrl()
    put("fullUrl", JsonPrimitive(fullUrl))

    put("resource", resourceJson)

    put(
      "request",
      buildRequestJson(
        definitionExtract = definitionExtract,
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        scopeBase = scopeBase,
        scopeQuestionnaireItem = scopeQuestionnaireItem,
        resourceType = resourceType,
        resourceId = resourceId,
        requestMethod = requestMethod,
        inheritedAllocateIds = inheritedAllocateIds,
      ),
    )
  }
}

/**
 * Builds `Bundle.entry.request`, including any conditional headers whose expressions evaluate to a
 * non-blank string.
 */
private fun buildRequestJson(
  definitionExtract: DefinitionExtractConfig,
  questionnaire: Questionnaire,
  questionnaireResponse: QuestionnaireResponse,
  scopeBase: Any,
  scopeQuestionnaireItem: Questionnaire.Item?,
  resourceType: String,
  resourceId: String?,
  requestMethod: String,
  inheritedAllocateIds: Map<String, String>,
): JsonObject = buildJsonObject {
  put("method", JsonPrimitive(requestMethod))
  put(
    "url",
    JsonPrimitive(if (resourceId.isNullOrBlank()) resourceType else "$resourceType/$resourceId"),
  )

  fun putConditionalHeader(jsonKey: String, expression: String?) {
    expression
      ?.let {
        evaluateDefinitionExtractExpressionToString(
          expression = it,
          base = scopeBase,
          questionnaire = questionnaire,
          questionnaireResponse = questionnaireResponse,
          questionnaireItem = scopeQuestionnaireItem,
          responseItem = scopeBase as? QuestionnaireResponse.Item,
          allocateIds = inheritedAllocateIds,
        )
      }
      ?.takeIf { it.isNotBlank() }
      ?.let { put(jsonKey, JsonPrimitive(it)) }
  }

  putConditionalHeader("ifNoneMatch", definitionExtract.ifNoneMatchExpression)
  putConditionalHeader("ifModifiedSince", definitionExtract.ifModifiedSinceExpression)
  putConditionalHeader("ifMatch", definitionExtract.ifMatchExpression)
  putConditionalHeader("ifNoneExist", definitionExtract.ifNoneExistExpression)
}

/**
 * Adds an extracted entry only if it fully materializes as a valid `Bundle.entry`.
 *
 * The SDC guide says extraction errors should be logged and processing should continue as though
 * the failed query or directive produced no data. This helper keeps that behavior localized to one
 * resource scope instead of failing the whole extraction transaction.
 */
internal inline fun materializeValidEntryOrNull(
  scopeDescription: String,
  block: () -> JsonObject,
): JsonObject? =
  try {
    val entryJson = block()
    json.decodeFromJsonElement(Bundle.Entry.serializer(), entryJson)
    entryJson
  } catch (throwable: Throwable) {
    Logger.w(
      "Skipping definition-based extraction for $scopeDescription because it could not be fully materialized.",
      throwable,
    )
    null
  }

private fun JsonElement.asString(): String? =
  (this as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
