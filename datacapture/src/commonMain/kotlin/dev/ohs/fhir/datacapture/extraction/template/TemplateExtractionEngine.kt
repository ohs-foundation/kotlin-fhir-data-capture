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
package dev.ohs.fhir.datacapture.extraction.template

import dev.ohs.fhir.datacapture.extensions.allocateIdVariableNames
import dev.ohs.fhir.datacapture.extensions.findContainedBundle
import dev.ohs.fhir.datacapture.extensions.findContainedResource
import dev.ohs.fhir.datacapture.extensions.generateAllocatedFullUrl
import dev.ohs.fhir.datacapture.extensions.templateExtractBundleReference
import dev.ohs.fhir.datacapture.extensions.templateExtractExtensions
import dev.ohs.fhir.datacapture.extraction.DataExtractionException
import dev.ohs.fhir.datacapture.fhirpath.FhirPathService
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Uri
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Template-based extraction is the Structured Data Capture (SDC) mechanism for deriving one
 * transaction Bundle from a completed [QuestionnaireResponse] by cloning contained resource
 * templates and replacing their templated values with data selected from the response:
 * https://build.fhir.org/ig/HL7/sdc/en/extraction.html
 * https://build.fhir.org/ig/HL7/sdc/en/StructureDefinition-sdc-questionnaire-templateExtract.html
 * https://build.fhir.org/ig/HL7/sdc/en/StructureDefinition-sdc-questionnaire-templateExtractBundle.html
 *
 * This implementation supports the `sdc-questionnaire-templateExtract`,
 * `sdc-questionnaire-templateExtractBundle`, `sdc-questionnaire-templateExtractContext`,
 * `sdc-questionnaire-templateExtractValue`, and `sdc-questionnaire-extractAllocateId` extensions
 * defined by SDC. The extractor is platform-independent and lives in `commonMain`, so callers can
 * use it from Android, iOS, JVM, JS, or Wasm after obtaining a completed questionnaire response
 * from the data capture workflow.
 */
object TemplateExtractionEngine {
  private val treeProcessor = TemplateTreeProcessor()
  private val json = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  /** Returns `true` when the questionnaire declares at least one template extraction definition. */
  fun canExtract(questionnaire: Questionnaire): Boolean =
    questionnaire.templateExtractBundleReference != null ||
      questionnaire.templateExtractExtensions.isNotEmpty() ||
      questionnaire.item.any { item -> item.hasTemplateExtractExtensionRecursively() }

  /**
   * Runs one template extraction pass for the provided questionnaire/response pair.
   *
   * @throws IllegalArgumentException if the questionnaire does not declare template extraction, if
   *   any declared template reference cannot be resolved to a contained resource, or if the
   *   questionnaire response points to a different questionnaire URL.
   * @throws DataExtractionException if template evaluation encounters a fatal extraction error
   *   after preflight validation succeeds.
   */
  fun extract(questionnaire: Questionnaire, questionnaireResponse: QuestionnaireResponse): Bundle {
    require(canExtract(questionnaire)) {
      "Template-based extraction requires sdc-questionnaire-templateExtractBundle, sdc-questionnaire-templateExtract, or one of the item-level template extraction declarations."
    }

    val questionnaireReference = questionnaireResponse.questionnaire?.value
    require(questionnaireReference == null || questionnaireReference == questionnaire.url?.value) {
      "Mismatching Questionnaire ${questionnaire.url?.value} and QuestionnaireResponse (for Questionnaire $questionnaireReference)."
    }

    val missingTemplateReferences = questionnaire.missingTemplateReferences()
    require(missingTemplateReferences.isEmpty()) {
      "Missing contained template resource(s): ${missingTemplateReferences.joinToString()}. Each template extraction reference must resolve before extraction starts."
    }

    try {
      val rootVariables = allocateIdVariables(questionnaire.allocateIdVariableNames)
      val rootScope =
        TemplateEvaluationScope(
          questionnaire = questionnaire,
          questionnaireResponse = questionnaireResponse,
          questionnaireItem = null,
          context = questionnaireResponse,
          variables = rootVariables,
        )

      val rootBundleEntries =
        questionnaire.templateExtractBundleReference
          ?.let { templateReference ->
            extractBundleTemplate(templateReference, rootScope, "Questionnaire")
          }
          .orEmpty()
      val entries =
        questionnaire.templateExtractExtensions.mapNotNull { definition ->
          extractTemplate(definition, rootScope, "Questionnaire")
        }
      val traversedEntries =
        traverseQuestionnaireItems(
          questionnaire = questionnaire,
          questionnaireResponse = questionnaireResponse,
          questionnaireItems = questionnaire.item,
          responseItems = questionnaireResponse.item,
          inheritedVariables = rootVariables,
        )

      return Bundle(
        type = questionnaire.extractedBundleType(),
        entry = rootBundleEntries + entries + traversedEntries,
      )
    } catch (exception: DataExtractionException) {
      throw exception
    }
  }

  /**
   * Walks the Questionnaire item tree in lockstep with the matching QuestionnaireResponse items.
   *
   * For the current sibling level, the response items are indexed by `linkId` so each Questionnaire
   * item can retrieve its matching response items in constant time instead of scanning the full
   * list repeatedly. This lookup is intentionally scoped to the current branch of the response
   * tree: when recursion moves into child items, the child response items from that branch become
   * the next input so nested extraction only sees the responses that belong to the current parent
   * context.
   *
   * Each matched item is normalized into one or more extraction contexts to support repeated groups
   * and repeating questions, item-level templates are evaluated for each logical occurrence, and
   * any allocated `%variables` are carried forward to descendants. If multiple response items are
   * found for a non-repeating Questionnaire item, extraction uses the first logical occurrence for
   * direct extraction.
   */
  private fun traverseQuestionnaireItems(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    questionnaireItems: List<Questionnaire.Item>,
    responseItems: List<QuestionnaireResponse.Item>,
    inheritedVariables: Map<String, Any?>,
  ): List<Bundle.Entry> {
    val responseItemsByLinkId = responseItems.groupBy { it.linkId.value }
    return questionnaireItems.flatMap { questionnaireItem ->
      val matchingResponseItems = responseItemsByLinkId[questionnaireItem.linkId.value].orEmpty()
      if (matchingResponseItems.isEmpty()) return@flatMap emptyList()

      questionnaireItem.toExtractionContexts(matchingResponseItems).flatMap { extractionContext ->
        val currentVariables =
          inheritedVariables + allocateIdVariables(questionnaireItem.allocateIdVariableNames)
        val scope =
          TemplateEvaluationScope(
            questionnaire = questionnaire,
            questionnaireResponse = questionnaireResponse,
            questionnaireItem = questionnaireItem,
            context = extractionContext.baseContext,
            variables = currentVariables,
          )

        val extractedEntries =
          questionnaireItem.templateExtractExtensions.mapNotNull { definition ->
            extractTemplate(
              definition = definition,
              scope = scope,
              path = questionnaireItem.linkId.value ?: "Questionnaire.item",
            )
          }

        val childEntries =
          if (questionnaireItem.item.isNotEmpty()) {
            traverseQuestionnaireItems(
              questionnaire = questionnaire,
              questionnaireResponse = questionnaireResponse,
              questionnaireItems = questionnaireItem.item,
              responseItems = extractionContext.childResponseItems,
              inheritedVariables = currentVariables,
            )
          } else {
            emptyList()
          }

        extractedEntries + childEntries
      }
    }
  }

  private fun extractTemplate(
    definition: TemplateExtractDefinition,
    scope: TemplateEvaluationScope,
    path: String,
  ): Bundle.Entry? {
    val templateResource =
      scope.questionnaire.findContainedResource(definition.templateReference)
        ?: throw DataExtractionException(
          "Contained template '${definition.templateReference}' was not found in the questionnaire."
        )

    // Keep the contained template id in the JSON tree while template directives are evaluated.
    // This lets `_id` participate in `templateExtractValue` the same way as any other primitive,
    // while still giving us the original anchor so we can remove it from the emitted resource if
    // no extraction logic replaces it.
    val templateJson = FhirPathService.resourceToJson(templateResource)
    val templateId = templateJson["id"]?.jsonPrimitive?.contentOrNull
    val resourceType =
      templateJson["resourceType"]?.jsonPrimitive?.contentOrNull
        ?: run {
          throw DataExtractionException(
            "Contained template '${definition.templateReference}' is missing resourceType."
          )
        }

    // The JSON tree pass applies templateExtractContext/templateExtractValue recursively before we
    // materialize the resource back into typed Kotlin FHIR models.
    val processedResources = treeProcessor.processResource(templateJson, scope)
    if (processedResources.isEmpty()) return null

    val resourceJson =
      processedResources.first().withResolvedResourceId(definition, templateId, scope, path)
    val extractedResource = FhirPathService.jsonToResource(resourceJson, path)
    return createBundleEntry(definition, scope, path, resourceType, extractedResource)
  }

  private fun extractBundleTemplate(
    templateReference: String,
    scope: TemplateEvaluationScope,
    path: String,
  ): List<Bundle.Entry> {
    val templateBundle =
      scope.questionnaire.findContainedBundle(templateReference)
        ?: throw DataExtractionException(
          "Contained template bundle '$templateReference' was not found in the questionnaire."
        )

    return templateBundle.entry.flatMapIndexed { index, entryTemplate ->
      val entryPath = "$path.templateBundle.entry[$index]"
      val entryTemplateJson =
        json.encodeToJsonElement(Bundle.Entry.serializer(), entryTemplate).jsonObject

      treeProcessor.processObject(entryTemplateJson, scope, entryPath).map { entryJson ->
        materializeBundleEntry(entryJson, entryPath)
      }
    }
  }

  private fun materializeBundleEntry(entryJson: JsonObject, path: String): Bundle.Entry =
    try {
      json.decodeFromJsonElement(Bundle.Entry.serializer(), entryJson)
    } catch (exception: Exception) {
      throw DataExtractionException(
        "Failed to materialize extracted bundle entry at '$path': ${exception.message ?: exception::class.simpleName}"
      )
    }

  /**
   * Finalizes `Resource.id` after template processing completes.
   *
   * Contained template ids such as `#patient-template` identify the source template inside the
   * Questionnaire; they are not valid emitted resource ids unless extraction logic explicitly turns
   * them into one. We therefore apply the evaluated `resourceId` override when present, and
   * otherwise strip the original template anchor only if it survived processing unchanged.
   */
  private fun JsonObject.withResolvedResourceId(
    definition: TemplateExtractDefinition,
    templateId: String?,
    scope: TemplateEvaluationScope,
    path: String,
  ): JsonObject {
    val mutable = toMutableMap()
    val evaluatedId =
      definition.resourceIdExpression
        ?.let { evaluateExpression(it, scope) }
        ?.let { values -> FhirPathService.toStringValue(values, "$path.resourceId") }
        ?.takeIf { it.isNotBlank() }

    if (evaluatedId != null) {
      mutable["id"] = JsonPrimitive(evaluatedId)
      mutable.remove("_id")
    } else if (mutable["id"]?.jsonPrimitive?.contentOrNull == templateId) {
      mutable.remove("id")
      mutable.remove("_id")
    }
    return JsonObject(mutable)
  }

  private fun createBundleEntry(
    definition: TemplateExtractDefinition,
    scope: TemplateEvaluationScope,
    path: String,
    resourceType: String,
    extractedResource: Resource,
  ): Bundle.Entry {
    val resourceId = extractedResource.id
    val fullUrl =
      definition.fullUrlExpression
        ?.let { evaluateExpression(it, scope) }
        ?.let { values -> FhirPathService.toStringValue(values, "$path.fullUrl") }
        ?.takeIf { it.isNotBlank() } ?: generateAllocatedFullUrl()

    val requestUrl =
      if (resourceId.isNullOrBlank()) {
        resourceType
      } else {
        "$resourceType/$resourceId"
      }

    val requestBuilder =
      Bundle.Entry.Request.Builder(
        method =
          Enumeration(
            value =
              if (resourceId.isNullOrBlank()) {
                Bundle.HTTPVerb.Post
              } else {
                Bundle.HTTPVerb.Put
              }
          ),
        url = Uri.Builder().apply { value = requestUrl },
      )

    definition.ifNoneMatchExpression
      ?.let { evaluateExpression(it, scope) }
      ?.let { values -> FhirPathService.toStringValue(values, "$path.ifNoneMatch") }
      ?.takeIf { it.isNotBlank() }
      ?.let { requestBuilder.ifNoneMatch = FhirString.Builder().apply { value = it } }

    definition.ifMatchExpression
      ?.let { evaluateExpression(it, scope) }
      ?.let { values -> FhirPathService.toStringValue(values, "$path.ifMatch") }
      ?.takeIf { it.isNotBlank() }
      ?.let { requestBuilder.ifMatch = FhirString.Builder().apply { value = it } }

    definition.ifNoneExistExpression
      ?.let { evaluateExpression(it, scope) }
      ?.let { values -> FhirPathService.toStringValue(values, "$path.ifNoneExist") }
      ?.takeIf { it.isNotBlank() }
      ?.let { requestBuilder.ifNoneExist = FhirString.Builder().apply { value = it } }

    definition.ifModifiedSinceExpression
      ?.let { evaluateExpression(it, scope) }
      ?.let { values -> FhirPathService.toInstantValue(values, "$path.ifModifiedSince") }
      ?.let { instant -> requestBuilder.ifModifiedSince = instant.toBuilder() }

    return Bundle.Entry(
      fullUrl = Uri(value = fullUrl),
      resource = extractedResource,
      request = requestBuilder.build(),
    )
  }

  private fun evaluateExpression(expression: String, scope: TemplateEvaluationScope): List<Any> =
    FhirPathService.evaluate(
      expression = expression,
      resource = scope.fhirPathEvaluationContext(),
      variables = scope.fhirPathVariables(),
    )

  @OptIn(ExperimentalUuidApi::class)
  private fun allocateIdVariables(variableNames: List<String>): Map<String, Any?> =
    variableNames.associateWith { generateAllocatedFullUrl() }
}

/** Returns `true` when this questionnaire item subtree declares template extraction anywhere. */
private fun Questionnaire.Item.hasTemplateExtractExtensionRecursively(): Boolean =
  templateExtractExtensions.isNotEmpty() ||
    item.any { child -> child.hasTemplateExtractExtensionRecursively() }

/** Collects unresolved contained resource references declared by template extraction extensions. */
private fun Questionnaire.missingTemplateReferences(): List<String> =
  buildList {
      templateExtractBundleReference?.let(::add)
      addAll(allTemplateExtractDefinitions().map { it.templateReference })
    }
    .distinct()
    .filter { findContainedResource(it) == null }

private fun Questionnaire.extractedBundleType(): Enumeration<Bundle.BundleType> =
  templateExtractBundleReference?.let(::findContainedBundle)?.type
    ?: Enumeration(value = Bundle.BundleType.Transaction)

/** Flattens questionnaire-level and item-level template extraction declarations into one list. */
private fun Questionnaire.allTemplateExtractDefinitions(): List<TemplateExtractDefinition> =
  templateExtractExtensions +
    item.flatMap { questionnaireItem -> questionnaireItem.allTemplateExtractDefinitions() }

/** Recursively gathers template extraction declarations for one questionnaire item subtree. */
private fun Questionnaire.Item.allTemplateExtractDefinitions(): List<TemplateExtractDefinition> =
  templateExtractExtensions + item.flatMap { child -> child.allTemplateExtractDefinitions() }
