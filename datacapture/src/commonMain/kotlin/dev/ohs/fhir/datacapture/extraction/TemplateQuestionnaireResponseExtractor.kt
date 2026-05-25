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

import co.touchlab.kermit.Logger
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.datacapture.extensions.EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL
import dev.ohs.fhir.datacapture.extensions.EXTENSION_TEMPLATE_EXTRACT_VALUE_URL
import dev.ohs.fhir.datacapture.extensions.TemplateExtractDefinition
import dev.ohs.fhir.datacapture.extensions.allocateIdVariableNames
import dev.ohs.fhir.datacapture.extensions.findContainedResource
import dev.ohs.fhir.datacapture.extensions.packRepeatedGroups
import dev.ohs.fhir.datacapture.extensions.templateExtractBundleReference
import dev.ohs.fhir.datacapture.extensions.templateExtractExtensions
import dev.ohs.fhir.datacapture.extensions.unpackRepeatedDescendants
import dev.ohs.fhir.datacapture.fhirpath.FhirPathService
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathQuantity
import dev.ohs.fhir.model.r4.Address
import dev.ohs.fhir.model.r4.Age
import dev.ohs.fhir.model.r4.Annotation
import dev.ohs.fhir.model.r4.Attachment
import dev.ohs.fhir.model.r4.Base64Binary
import dev.ohs.fhir.model.r4.Boolean as FhirBoolean
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Canonical
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.ContactDetail
import dev.ohs.fhir.model.r4.ContactPoint
import dev.ohs.fhir.model.r4.Contributor
import dev.ohs.fhir.model.r4.Count
import dev.ohs.fhir.model.r4.DataRequirement
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.Distance
import dev.ohs.fhir.model.r4.Dosage
import dev.ohs.fhir.model.r4.Duration
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Id
import dev.ohs.fhir.model.r4.Identifier
import dev.ohs.fhir.model.r4.Instant
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.Markdown
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.Money
import dev.ohs.fhir.model.r4.Oid
import dev.ohs.fhir.model.r4.ParameterDefinition
import dev.ohs.fhir.model.r4.Parameters
import dev.ohs.fhir.model.r4.Period
import dev.ohs.fhir.model.r4.PositiveInt
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.Range
import dev.ohs.fhir.model.r4.Ratio
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.RelatedArtifact
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.SampledData
import dev.ohs.fhir.model.r4.Signature
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Time
import dev.ohs.fhir.model.r4.Timing
import dev.ohs.fhir.model.r4.TriggerDefinition
import dev.ohs.fhir.model.r4.UnsignedInt
import dev.ohs.fhir.model.r4.Uri
import dev.ohs.fhir.model.r4.Url
import dev.ohs.fhir.model.r4.UsageContext
import dev.ohs.fhir.model.r4.Uuid
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid as KotlinUuid
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Expands SDC template extraction annotations from a [Questionnaire] and its matching
 * [QuestionnaireResponse] into a transaction [Bundle].
 *
 * At a high level the extractor:
 * 1. Normalizes repeated groups into the packed shape expected by traversal.
 * 2. Walks questionnaire-level and item-level template definitions.
 * 3. Evaluates template context/value expressions as FHIRPath.
 * 4. Converts the processed JSON back into bundle entries with request metadata.
 */
internal object TemplateQuestionnaireResponseExtractor {
  /**
   * Runs one extraction pass for the provided questionnaire/response pair.
   *
   * Repeated groups are packed up front so the traversal logic can treat a repeated group as a
   * single questionnaire item with multiple answers, which matches how extraction templates are
   * usually authored.
   */
  fun extract(questionnaire: Questionnaire, questionnaireResponse: QuestionnaireResponse): Bundle =
    TemplateExtractionEngine(
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        packedQuestionnaireResponse =
          questionnaireResponse.toBuilder().apply { packRepeatedGroups(questionnaire) }.build(),
      )
      .extract()
}

/**
 * Stateful worker for a single extraction run.
 *
 * Keeping the serializers and questionnaire/response pair here avoids threading them through every
 * helper and makes the extraction phases easier to read top-to-bottom.
 */
private class TemplateExtractionEngine(
  private val questionnaire: Questionnaire,
  private val questionnaireResponse: QuestionnaireResponse,
  private val packedQuestionnaireResponse: QuestionnaireResponse,
) {
  private val json = Json { prettyPrint = true }
  private val fhirJson = FhirR4Json()

  fun extract(): Bundle {
    require(
      questionnaireResponse.questionnaire?.value == null ||
        questionnaireResponse.questionnaire?.value == questionnaire.url?.value
    ) {
      "Mismatching Questionnaire ${questionnaire.url?.value} and QuestionnaireResponse (for Questionnaire ${questionnaireResponse.questionnaire?.value})"
    }

    /**
     * Questionnaire-level allocate-id variables are created once per extraction run so every
     * template that references the same variable name sees the same generated URN.
     */
    val rootScope =
      TemplateEvaluationScope(
        baseContext = questionnaireResponse,
        questionnaireItem = null,
        questionnaireResponseItem = null,
        variables = allocateIdVariables(questionnaire.allocateIdVariableNames),
      )

    val extractedEntries = mutableListOf<ExtractedBundleEntry>()

    // A bundle template can expand into many bundle entries in a single pass.
    questionnaire.templateExtractBundleReference?.let { reference ->
      extractedEntries += extractBundleTemplate(reference, rootScope)
    }

    // Root-level resource templates each contribute at most one entry for the current scope.
    questionnaire.templateExtractExtensions.forEach { definition ->
      extractTemplate(definition, rootScope)?.let(extractedEntries::add)
    }

    // Traverse the packed response so repeated groups are represented in the answer-based shape
    // expected by toExtractionContexts().
    traverseQuestionnaireItems(
      questionnaireItems = questionnaire.item,
      questionnaireResponseItems = packedQuestionnaireResponse.item,
      inheritedVariables = rootScope.variables,
      extractedEntries = extractedEntries,
    )

    return assembleBundle(extractedEntries)
  }

  /**
   * Walks questionnaire items in questionnaire order and evaluates any item-scoped templates.
   *
   * Each questionnaire item can yield one or more logical extraction contexts. Repeated items
   * produce one context per answer/group instance so downstream templates behave as if they were
   * written against a single occurrence.
   */
  private fun traverseQuestionnaireItems(
    questionnaireItems: List<Questionnaire.Item>,
    questionnaireResponseItems: List<QuestionnaireResponse.Item>,
    inheritedVariables: Map<String, Any?>,
    extractedEntries: MutableList<ExtractedBundleEntry>,
  ) {
    questionnaireItems.forEach { questionnaireItem ->
      // Extraction only continues when the response contains the questionnaire item being
      // traversed; missing answers simply mean there is nothing to materialize here.
      val matchingResponseItem =
        questionnaireResponseItems.firstOrNull { it.linkId == questionnaireItem.linkId }
          ?: return@forEach

      questionnaireItem.toExtractionContexts(matchingResponseItem).forEach {
        questionnaireItemContext ->
        // Item-scoped allocate-id variables are created once per logical context so
        // nested templates can share generated references across sibling resources.
        val currentVariables =
          inheritedVariables + allocateIdVariables(questionnaireItem.allocateIdVariableNames)
        val expressionBaseContext =
          questionnaireItemContext.baseContext.unpackRepeatedDescendants(questionnaireItem)

        val scope =
          TemplateEvaluationScope(
            baseContext = expressionBaseContext,
            questionnaireItem = questionnaireItem,
            questionnaireResponseItem = expressionBaseContext,
            variables = currentVariables,
          )

        questionnaireItem.templateExtractExtensions.forEach { definition ->
          extractTemplate(definition, scope)?.let(extractedEntries::add)
        }

        if (questionnaireItem.item.isNotEmpty()) {
          traverseQuestionnaireItems(
            questionnaireItems = questionnaireItem.item,
            questionnaireResponseItems = questionnaireItemContext.childItems,
            inheritedVariables = currentVariables,
            extractedEntries = extractedEntries,
          )
        }
      }
    }
  }

  /**
   * Expands a contained bundle template and normalizes each resulting entry.
   *
   * Bundle templates are useful when authors want to control `Bundle.entry.request` metadata
   * directly instead of relying on the extractor's resource-template defaults.
   */
  private fun extractBundleTemplate(
    reference: String,
    scope: TemplateEvaluationScope,
  ): List<ExtractedBundleEntry> {
    val bundleTemplate =
      questionnaire.findContainedResource(reference) as? Bundle
        ?: return emptyList<ExtractedBundleEntry>().also {
          Logger.w("Bundle template $reference could not be found in Questionnaire.contained.")
        }

    return materializeBundleTemplate(bundleTemplate, scope)
  }

  /**
   * Expands a `templateExtract` definition into one intermediate bundle entry.
   *
   * A contained Bundle referenced through `templateExtract` is treated as an ordinary extracted
   * Bundle resource. Only `templateExtractBundle` fans a Bundle's `entry[]` out into outer
   * transaction entries.
   */
  private fun extractTemplate(
    definition: TemplateExtractDefinition,
    scope: TemplateEvaluationScope,
  ): ExtractedBundleEntry? {
    val resourceTemplate =
      questionnaire.findContainedResource(definition.templateReference)
        ?: return null.also {
          Logger.w(
            "Resource template ${definition.templateReference} could not be found in Questionnaire.contained."
          )
        }

    return extractResourceTemplate(definition, resourceTemplate, scope)
  }

  private fun materializeBundleTemplate(
    bundleTemplate: Bundle,
    scope: TemplateEvaluationScope,
    path: String = "Bundle",
  ): List<ExtractedBundleEntry> {
    val processedTemplate =
      processJsonElement(stripTemplateBundleIds(bundleTemplate.toJsonObject()), scope, path = path)

    return processedTemplate.flatMap { processedElement ->
      val bundleObject = processedElement as? JsonObject ?: return@flatMap emptyList()
      val entryArray = bundleObject["entry"]?.jsonArray ?: return@flatMap emptyList()
      entryArray.mapNotNullIndexed { index, entryElement ->
        normalizeBundleTemplateEntry(entryElement = entryElement, path = "$path.entry[$index]")
      }
    }
  }

  /**
   * Expands a non-bundle contained resource template into a single intermediate bundle entry.
   *
   * The resource template itself only describes the resource payload. Request metadata such as
   * `fullUrl`, `PUT`/`POST`, and conditional headers are derived from the template definition.
   */
  private fun extractResourceTemplate(
    definition: TemplateExtractDefinition,
    resourceTemplate: Resource,
    scope: TemplateEvaluationScope,
  ): ExtractedBundleEntry? {
    val processedTemplate =
      // Template ids are authoring artifacts. The extracted transaction decides whether a
      // concrete resource id should exist from the optional resourceId expression instead.
      processJsonElement(
          stripTopLevelResourceId(resourceTemplate.toJsonObject()),
          scope,
          path =
            resourceTemplate.toJsonObject()["resourceType"]?.jsonPrimitive?.contentOrNull
              ?: "Resource",
        )
        .firstOrNull() as? JsonObject ?: return null

    val resourceType =
      processedTemplate["resourceType"]?.jsonPrimitive?.contentOrNull
        ?: return null.also {
          Logger.w(
            "Skipping extracted resource without a resourceType in ${definition.templateReference}."
          )
        }

    val fullUrl =
      evaluateToString(definition.fullUrlExpression, scope, "$resourceType.fullUrl")
        ?.takeIf(String::isNotBlank) ?: allocateIdValue()

    val resourceId =
      evaluateToString(definition.resourceIdExpression, scope, "$resourceType.resourceId")
        ?.takeIf(String::isNotBlank)

    val ifModifiedSince =
      evaluateToInstant(
        definition.ifModifiedSinceExpression,
        scope,
        "$resourceType.request.ifModifiedSince",
      )

    return ExtractedBundleEntry(
      resourceType = resourceType,
      resourceJson = processedTemplate,
      fullUrl = fullUrl,
      requestMethod = if (resourceId == null) Bundle.HTTPVerb.Post else Bundle.HTTPVerb.Put,
      requestUrl = if (resourceId == null) resourceType else "$resourceType/$resourceId",
      ifNoneMatch =
        evaluateToString(
          definition.ifNoneMatchExpression,
          scope,
          "$resourceType.request.ifNoneMatch",
        ),
      ifModifiedSince = ifModifiedSince,
      ifMatch =
        evaluateToString(definition.ifMatchExpression, scope, "$resourceType.request.ifMatch"),
      ifNoneExist =
        evaluateToString(
          definition.ifNoneExistExpression,
          scope,
          "$resourceType.request.ifNoneExist",
        ),
    )
  }

  /**
   * Processes a template node and returns zero, one, or many output nodes.
   *
   * Returning a list is important because context/value expressions can fan a single template node
   * out into multiple siblings, or suppress it entirely when no context matches.
   */
  private fun processJsonElement(
    element: JsonElement,
    scope: TemplateEvaluationScope,
    path: String,
  ): List<JsonElement> =
    when (element) {
      is JsonObject -> processJsonObject(element, scope, path)

      is JsonArray -> {
        val processedChildren =
          element.flatMapIndexed { index, child ->
            processJsonElement(child, scope, "$path[$index]")
          }
        processedChildren.takeIf(List<JsonElement>::isNotEmpty)?.let(::JsonArray)?.let(::listOf)
          ?: emptyList()
      }

      else -> listOf(element)
    }

  /**
   * Processes a JSON object after peeling off extraction-only annotations.
   *
   * Context expressions duplicate the template object across multiple scopes. Value expressions
   * replace the current node with the evaluated result, optionally merging that result back into
   * the remaining authored object when the evaluated value is itself an object.
   */
  private fun processJsonObject(
    jsonObject: JsonObject,
    scope: TemplateEvaluationScope,
    path: String,
  ): List<JsonElement> {
    val strippedTemplateAnnotations = jsonObject.withoutTemplateAnnotations()
    val contextScopes =
      resolveContextScopes(strippedTemplateAnnotations.contextExpression, scope, path)
    if (contextScopes.isEmpty()) return emptyList()

    val processedResults = mutableListOf<JsonElement>()

    contextScopes.forEach { contextScope ->
      val valueExpression = strippedTemplateAnnotations.valueExpression
      if (valueExpression == null) {
        processedResults +=
          processObjectChildren(strippedTemplateAnnotations.cleanedObject, contextScope, path)
        return@forEach
      }

      val evaluationResults = evaluateExpression(valueExpression, contextScope, path)
      if (evaluationResults.isEmpty()) return@forEach

      evaluationResults.forEach { evaluationResult ->
        val replacement = encodeResultToJsonElement(evaluationResult)
        if (replacement == null) {
          Logger.w("Skipping unsupported template extraction result at $path: $evaluationResult")
          return@forEach
        }

        val resultScope = contextScope.withBaseContext(evaluationResult)
        when (replacement) {
          is JsonObject -> {
            // Object results can still receive static siblings that were authored in
            // the template, so we overlay the evaluated object onto the cleaned
            // template before descending into child properties.
            val mergedObject =
              mergeJsonObjects(strippedTemplateAnnotations.cleanedObject, replacement)!!
            processedResults += processObjectChildren(mergedObject, resultScope, path)
          }

          else -> {
            if (strippedTemplateAnnotations.cleanedObject.hasMaterialChildren()) {
              Logger.w(
                "Skipping child overlay at $path because a primitive or array result cannot host templated child properties."
              )
            }
            processedResults +=
              coercePrimitiveObjectReplacement(
                path = path,
                replacement = replacement,
                templateObject = strippedTemplateAnnotations.cleanedObject,
              )
          }
        }
      }
    }

    return processedResults
  }

  /**
   * Processes object properties while preserving FHIR's primitive metadata pairing rules.
   *
   * In FHIR JSON, a primitive property like `valueString` can be accompanied by a sibling
   * `"_valueString"` object/array that stores extensions and element metadata. Those two shapes
   * must be processed together or their indices drift out of sync.
   */
  private fun processObjectChildren(
    jsonObject: JsonObject,
    scope: TemplateEvaluationScope,
    path: String,
  ): JsonObject {
    val processedProperties = linkedMapOf<String, JsonElement>()
    val handledPropertyNames = mutableSetOf<String>()

    jsonObject.keys.forEach { key ->
      val basePropertyName = key.removePrefix("_")
      if (handledPropertyNames.contains(basePropertyName)) return@forEach

      val valueElement = jsonObject[basePropertyName]
      val metadataElement = jsonObject["_$basePropertyName"]

      if (valueElement.isPrimitiveProperty(metadataElement)) {
        val processedPrimitiveProperty =
          processPrimitiveProperty(
            valueElement = valueElement,
            metadataElement = metadataElement,
            scope = scope,
            path = "$path.$basePropertyName",
          )

        processedPrimitiveProperty?.value?.let { processedProperties[basePropertyName] = it }
        processedPrimitiveProperty?.metadata?.let { processedProperties["_$basePropertyName"] = it }
        handledPropertyNames += basePropertyName
        return@forEach
      }

      if (valueElement == null) return@forEach

      val processedPropertyValue =
        when (valueElement) {
          is JsonObject ->
            processJsonObject(valueElement, scope, "$path.$basePropertyName").toPropertyValue()

          is JsonArray -> processJsonArray(valueElement, scope, "$path.$basePropertyName")

          else -> valueElement
        }

      processedPropertyValue?.let { processedProperties[basePropertyName] = it }
      handledPropertyNames += basePropertyName
    }

    return JsonObject(processedProperties)
  }

  /** Flattens child expansions inside an array so one template element can emit many outputs. */
  private fun processJsonArray(
    jsonArray: JsonArray,
    scope: TemplateEvaluationScope,
    path: String,
  ): JsonElement? {
    val processedElements = mutableListOf<JsonElement>()
    jsonArray.forEachIndexed { index, element ->
      processedElements += processJsonElement(element, scope, "$path[$index]")
    }
    return processedElements.takeIf(List<JsonElement>::isNotEmpty)?.let(::JsonArray)
  }

  /**
   * Routes primitive processing through scalar or array handling while keeping metadata aligned.
   */
  private fun processPrimitiveProperty(
    valueElement: JsonElement?,
    metadataElement: JsonElement?,
    scope: TemplateEvaluationScope,
    path: String,
  ): ProcessedPrimitiveProperty? {
    val hasArrayShape = valueElement is JsonArray || metadataElement is JsonArray
    return if (hasArrayShape) {
      processPrimitiveArray(valueElement, metadataElement, scope, path)
    } else {
      processPrimitiveSingle(valueElement, metadataElement, scope, path)
    }
  }

  /**
   * Processes a primitive array and its underscore-metadata array in lockstep.
   *
   * FHIR JSON uses parallel arrays here, so we preserve the slot count even when either the value
   * side or the metadata side is missing for a particular index.
   */
  private fun processPrimitiveArray(
    valueElement: JsonElement?,
    metadataElement: JsonElement?,
    scope: TemplateEvaluationScope,
    path: String,
  ): ProcessedPrimitiveProperty? {
    val valueArray = (valueElement as? JsonArray)?.toList().orEmpty()
    val metadataArray = (metadataElement as? JsonArray)?.toList().orEmpty()
    val slotCount = maxOf(valueArray.size, metadataArray.size)
    val processedSlots = mutableListOf<PrimitiveSlotOutput>()

    repeat(slotCount) { index ->
      processedSlots +=
        processPrimitiveSlot(
          valueElement = valueArray.getOrNull(index),
          metadataElement = metadataArray.getOrNull(index),
          scope = scope,
          path = "$path[$index]",
        )
    }

    if (processedSlots.isEmpty()) return null

    val anyValues = processedSlots.any { it.value != null }
    val anyMetadata = processedSlots.any { it.metadata != null }

    return ProcessedPrimitiveProperty(
      value = if (anyValues) JsonArray(processedSlots.map { it.value ?: JsonNull }) else null,
      metadata =
        if (anyMetadata) {
          JsonArray(processedSlots.map { it.metadata ?: JsonNull })
        } else {
          null
        },
    )
  }

  /**
   * Processes a non-array primitive slot.
   *
   * A value expression can still return multiple primitive results, so a scalar template node can
   * legitimately widen into a JSON array after evaluation.
   */
  private fun processPrimitiveSingle(
    valueElement: JsonElement?,
    metadataElement: JsonElement?,
    scope: TemplateEvaluationScope,
    path: String,
  ): ProcessedPrimitiveProperty? {
    val processedSlots =
      processPrimitiveSlot(
        valueElement = valueElement,
        metadataElement = metadataElement,
        scope = scope,
        path = path,
      )

    if (processedSlots.isEmpty()) return null

    if (processedSlots.size == 1) {
      return ProcessedPrimitiveProperty(
        value = processedSlots.single().value,
        metadata = processedSlots.single().metadata,
      )
    }

    val anyValues = processedSlots.any { it.value != null }
    val anyMetadata = processedSlots.any { it.metadata != null }

    return ProcessedPrimitiveProperty(
      value = if (anyValues) JsonArray(processedSlots.map { it.value ?: JsonNull }) else null,
      metadata =
        if (anyMetadata) {
          JsonArray(processedSlots.map { it.metadata ?: JsonNull })
        } else {
          null
        },
    )
  }

  /**
   * Resolves one primitive value/metadata slot.
   *
   * Primitive metadata objects are also allowed to carry template extraction extensions, so the
   * underscore object is not just copied through blindly.
   */
  private fun processPrimitiveSlot(
    valueElement: JsonElement?,
    metadataElement: JsonElement?,
    scope: TemplateEvaluationScope,
    path: String,
  ): List<PrimitiveSlotOutput> {
    val metadataObject = metadataElement as? JsonObject
    val strippedMetadataAnnotations = metadataObject?.withoutTemplateAnnotations()
    val contextScopes =
      resolveContextScopes(strippedMetadataAnnotations?.contextExpression, scope, path)

    if (contextScopes.isEmpty()) return emptyList()

    val outputs = mutableListOf<PrimitiveSlotOutput>()
    contextScopes.forEach { contextScope ->
      val valueExpression = strippedMetadataAnnotations?.valueExpression
      if (valueExpression == null) {
        if (
          valueElement == null && strippedMetadataAnnotations?.cleanedObject?.isEmpty() != false
        ) {
          return@forEach
        }
        outputs +=
          PrimitiveSlotOutput(
            value = valueElement,
            metadata = strippedMetadataAnnotations?.cleanedObject?.takeUnless(JsonObject::isEmpty),
          )
        return@forEach
      }

      val evaluationResults = evaluateExpression(valueExpression, contextScope, path)
      if (evaluationResults.isEmpty()) return@forEach

      evaluationResults.forEach { evaluationResult ->
        encodeResultToPrimitiveSlot(
            evaluationResult = evaluationResult,
            templateMetadata =
              strippedMetadataAnnotations?.cleanedObject?.takeUnless(JsonObject::isEmpty),
          )
          ?.let(outputs::add)
      }
    }

    return outputs
  }

  /**
   * Evaluates a context expression into one scope per result.
   *
   * When the expression declares a name, for example `%patient`, the evaluated result becomes both
   * the new base context and a named variable available to descendant expressions.
   */
  private fun resolveContextScopes(
    expression: TemplateExpression?,
    scope: TemplateEvaluationScope,
    path: String,
  ): List<TemplateEvaluationScope> {
    if (expression == null) return listOf(scope)

    val evaluationResults = evaluateExpression(expression, scope, path)
    if (evaluationResults.isEmpty()) return emptyList()

    return evaluationResults.map { result ->
      val namedVariable =
        expression.name?.takeIf(String::isNotBlank)?.let { mapOf(it.removePrefix("%") to result) }
          ?: emptyMap()
      scope.withBaseContext(result, namedVariable)
    }
  }

  /**
   * Evaluates a template expression with the same implicit variables reviewers expect from SDC.
   *
   * `resource` and `rootResource` both point at the original questionnaire response, `context`
   * follows the current template scope, `questionnaire` exposes the authored questionnaire, and
   * `qItem` is added while traversing a concrete questionnaire item.
   */
  private fun evaluateExpression(
    expression: TemplateExpression,
    scope: TemplateEvaluationScope,
    path: String,
  ): List<Any> {
    if (!expression.language.isNullOrBlank() && expression.language != "text/fhirpath") {
      Logger.w("Skipping non-FHIRPath extraction expression at $path.")
      return emptyList()
    }

    return FhirPathService.evaluate(
      expression = expression.expression,
      resource = scope.baseContext,
      variables =
        scope.variables +
          mapOf(
            "resource" to questionnaireResponse,
            "rootResource" to questionnaireResponse,
            "questionnaire" to questionnaire,
            "context" to scope.baseContext,
          ) +
          (scope.questionnaireItem?.let { mapOf("qItem" to it) } ?: emptyMap()),
    )
  }

  private fun evaluateToString(
    expression: String?,
    scope: TemplateEvaluationScope,
    path: String,
  ): String? =
    expression
      ?.let { TemplateExpression(expression = it) }
      ?.let { templateExpression ->
        evaluateExpressionForScalar(templateExpression, scope, path)?.let(::scalarResultToString)
      }

  private fun evaluateToInstant(
    expression: String?,
    scope: TemplateEvaluationScope,
    path: String,
  ): Instant? =
    expression
      ?.let { TemplateExpression(expression = it) }
      ?.let { templateExpression ->
        evaluateExpressionForScalar(templateExpression, scope, path)?.let(::scalarResultToInstant)
      }

  private fun evaluateExpressionForScalar(
    expression: TemplateExpression,
    scope: TemplateEvaluationScope,
    path: String,
  ): Any? = runCatching { evaluateExpression(expression, scope, path).firstOrNull() }.getOrNull()

  /** Always returns a transaction bundle, even when no templates produced entries. */
  private fun assembleBundle(extractedEntries: List<ExtractedBundleEntry>): Bundle {
    if (extractedEntries.isEmpty()) {
      return Bundle(type = Enumeration(value = Bundle.BundleType.Transaction))
    }

    val bundleEntries = extractedEntries.map { it.toBundleEntry() }.toMutableList()

    return Bundle(type = Enumeration(value = Bundle.BundleType.Transaction), entry = bundleEntries)
  }

  /**
   * Converts a processed bundle-template entry into the same intermediate form used by resource
   * templates, filling in extractor defaults when optional request pieces are omitted.
   */
  private fun normalizeBundleTemplateEntry(
    entryElement: JsonElement,
    path: String,
  ): ExtractedBundleEntry? {
    val entryObject = entryElement as? JsonObject ?: return null
    val resourceObject = entryObject["resource"]?.jsonObject ?: return null
    val resourceType =
      resourceObject["resourceType"]?.jsonPrimitive?.contentOrNull
        ?: return null.also {
          Logger.w("Skipping bundle template entry without a resourceType at $path.")
        }

    val requestObject = entryObject["request"]?.jsonObject
    val requestUrl =
      requestObject?.get("url")?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
        ?: resourceType
    val requestMethod =
      requestObject?.get("method")?.jsonPrimitive?.contentOrNull?.let { method ->
        runCatching { Bundle.HTTPVerb.fromCode(method) }.getOrNull()
      } ?: Bundle.HTTPVerb.Post

    val ifModifiedSince =
      requestObject?.get("ifModifiedSince")?.jsonPrimitive?.contentOrNull?.let { value ->
        Instant(value = FhirDateTime.fromString(value))
      }

    return ExtractedBundleEntry(
      resourceType = resourceType,
      resourceJson = stripTopLevelResourceId(resourceObject),
      fullUrl =
        entryObject["fullUrl"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
          ?: allocateIdValue(),
      requestMethod = requestMethod,
      requestUrl = requestUrl,
      ifNoneMatch = requestObject?.get("ifNoneMatch")?.jsonPrimitive?.contentOrNull,
      ifModifiedSince = ifModifiedSince,
      ifMatch = requestObject?.get("ifMatch")?.jsonPrimitive?.contentOrNull,
      ifNoneExist = requestObject?.get("ifNoneExist")?.jsonPrimitive?.contentOrNull,
    )
  }

  /**
   * Encodes an evaluated FHIRPath result as ordinary JSON.
   *
   * This handles both raw JSON values and typed FHIR model objects so template authors can return
   * either without caring how the extractor serializes them.
   */
  private fun encodeResultToJsonElement(evaluationResult: Any): JsonElement? =
    when (evaluationResult) {
      is JsonElement -> evaluationResult

      is kotlin.String -> JsonPrimitive(evaluationResult)

      is Boolean -> JsonPrimitive(evaluationResult)

      is Int -> JsonPrimitive(evaluationResult)

      is Long -> JsonPrimitive(evaluationResult)

      is Double -> JsonPrimitive(evaluationResult)

      is Float -> JsonPrimitive(evaluationResult)

      is BigDecimal -> JsonPrimitive(evaluationResult.toString())

      is FhirPathDate -> JsonPrimitive(evaluationResult.toString())

      is FhirPathDateTime -> JsonPrimitive(fhirPathDateTimeToString(evaluationResult))

      is FhirPathQuantity ->
        encodeFhirValueToJson(
            Parameters.Parameter.Value.Quantity(
              Quantity(
                value = evaluationResult.value?.let { Decimal(value = it) },
                unit = evaluationResult.unit?.let { FhirString(value = it) },
                code = evaluationResult.unit?.let { Code(value = it) },
              )
            )
          )
          ?.value

      is Resource -> json.parseToJsonElement(fhirJson.encodeToString(evaluationResult))

      else -> evaluationResult.toParametersValue()?.let(::encodeFhirValueToJson)?.value
    }

  /**
   * Encodes an evaluated result for placement in a primitive value/metadata slot.
   *
   * Primitive slots can only host scalar JSON values plus underscore metadata. Complex FHIR values
   * are therefore accepted only when they serialize down to that primitive shape.
   */
  private fun encodeResultToPrimitiveSlot(
    evaluationResult: Any,
    templateMetadata: JsonObject?,
  ): PrimitiveSlotOutput? {
    val directPrimitive =
      when (evaluationResult) {
        is JsonPrimitive -> PrimitiveSlotOutput(evaluationResult, templateMetadata)

        is kotlin.String -> PrimitiveSlotOutput(JsonPrimitive(evaluationResult), templateMetadata)

        is Boolean -> PrimitiveSlotOutput(JsonPrimitive(evaluationResult), templateMetadata)

        is Int -> PrimitiveSlotOutput(JsonPrimitive(evaluationResult), templateMetadata)

        is Long -> PrimitiveSlotOutput(JsonPrimitive(evaluationResult), templateMetadata)

        is Double -> PrimitiveSlotOutput(JsonPrimitive(evaluationResult), templateMetadata)

        is Float -> PrimitiveSlotOutput(JsonPrimitive(evaluationResult), templateMetadata)

        is BigDecimal ->
          PrimitiveSlotOutput(JsonPrimitive(evaluationResult.toString()), templateMetadata)

        is FhirPathDate ->
          PrimitiveSlotOutput(JsonPrimitive(evaluationResult.toString()), templateMetadata)

        is FhirPathDateTime ->
          PrimitiveSlotOutput(
            JsonPrimitive(fhirPathDateTimeToString(evaluationResult)),
            templateMetadata,
          )

        else -> null
      }

    if (directPrimitive != null) return directPrimitive

    val encodedValue =
      evaluationResult.toParametersValue()?.let(::encodeFhirValueToJson) ?: return null

    return when (encodedValue.value) {
      null ->
        PrimitiveSlotOutput(
          value = null,
          metadata = mergeJsonObjects(templateMetadata, encodedValue.metadata),
        )

      is JsonPrimitive ->
        PrimitiveSlotOutput(
          value = encodedValue.value,
          metadata = mergeJsonObjects(templateMetadata, encodedValue.metadata),
        )

      else -> null
    }
  }

  /**
   * Uses the existing FHIR serializer to split a typed value into its primitive JSON value and
   * underscore metadata companion, mirroring the wire format exactly.
   *
   * Wrapping the value in `Parameters.parameter.value[x]` lets us reuse generated serializers
   * instead of reimplementing the primitive/metadata encoding rules by hand.
   */
  private fun encodeFhirValueToJson(value: Parameters.Parameter.Value): EncodedFhirValue? {
    val parameter = Parameters.Parameter(name = FhirString(value = "value"), value = value)
    val parameters = Parameters(parameter = listOf(parameter))
    val parameterObject =
      json
        .parseToJsonElement(fhirJson.encodeToString(parameters))
        .jsonObject["parameter"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject ?: return null

    val valueKey =
      parameterObject.keys.firstOrNull { key -> key.startsWith("value") && !key.startsWith("_") }
        ?: return null

    return EncodedFhirValue(
      value = parameterObject[valueKey],
      metadata = parameterObject["_$valueKey"] as? JsonObject,
    )
  }

  private fun Any.toParametersValue(): Parameters.Parameter.Value? =
    when (this) {
      is Base64Binary -> Parameters.Parameter.Value.Base64Binary(this)
      is FhirBoolean -> Parameters.Parameter.Value.Boolean(this)
      is Canonical -> Parameters.Parameter.Value.Canonical(this)
      is Code -> Parameters.Parameter.Value.Code(this)
      is Date -> Parameters.Parameter.Value.Date(this)
      is DateTime -> Parameters.Parameter.Value.DateTime(this)
      is Decimal -> Parameters.Parameter.Value.Decimal(this)
      is Id -> Parameters.Parameter.Value.Id(this)
      is Instant -> Parameters.Parameter.Value.Instant(this)
      is Integer -> Parameters.Parameter.Value.Integer(this)
      is Markdown -> Parameters.Parameter.Value.Markdown(this)
      is Oid -> Parameters.Parameter.Value.Oid(this)
      is PositiveInt -> Parameters.Parameter.Value.PositiveInt(this)
      is FhirString -> Parameters.Parameter.Value.String(this)
      is Time -> Parameters.Parameter.Value.Time(this)
      is UnsignedInt -> Parameters.Parameter.Value.UnsignedInt(this)
      is Uri -> Parameters.Parameter.Value.Uri(this)
      is Url -> Parameters.Parameter.Value.Url(this)
      is Uuid -> Parameters.Parameter.Value.Uuid(this)
      is Address -> Parameters.Parameter.Value.Address(this)
      is Age -> Parameters.Parameter.Value.Age(this)
      is Annotation -> Parameters.Parameter.Value.Annotation(this)
      is Attachment -> Parameters.Parameter.Value.Attachment(this)
      is CodeableConcept -> Parameters.Parameter.Value.CodeableConcept(this)
      is Coding -> Parameters.Parameter.Value.Coding(this)
      is ContactPoint -> Parameters.Parameter.Value.ContactPoint(this)
      is Count -> Parameters.Parameter.Value.Count(this)
      is Distance -> Parameters.Parameter.Value.Distance(this)
      is Duration -> Parameters.Parameter.Value.Duration(this)
      is HumanName -> Parameters.Parameter.Value.HumanName(this)
      is Identifier -> Parameters.Parameter.Value.Identifier(this)
      is Money -> Parameters.Parameter.Value.Money(this)
      is Period -> Parameters.Parameter.Value.Period(this)
      is Quantity -> Parameters.Parameter.Value.Quantity(this)
      is Range -> Parameters.Parameter.Value.Range(this)
      is Ratio -> Parameters.Parameter.Value.Ratio(this)
      is Reference -> Parameters.Parameter.Value.Reference(this)
      is SampledData -> Parameters.Parameter.Value.SampledData(this)
      is Signature -> Parameters.Parameter.Value.Signature(this)
      is Timing -> Parameters.Parameter.Value.Timing(this)
      is ContactDetail -> Parameters.Parameter.Value.ContactDetail(this)
      is Contributor -> Parameters.Parameter.Value.Contributor(this)
      is DataRequirement -> Parameters.Parameter.Value.DataRequirement(this)
      is Expression -> Parameters.Parameter.Value.Expression(this)
      is ParameterDefinition -> Parameters.Parameter.Value.ParameterDefinition(this)
      is RelatedArtifact -> Parameters.Parameter.Value.RelatedArtifact(this)
      is TriggerDefinition -> Parameters.Parameter.Value.TriggerDefinition(this)
      is UsageContext -> Parameters.Parameter.Value.UsageContext(this)
      is Dosage -> Parameters.Parameter.Value.Dosage(this)
      is Meta -> Parameters.Parameter.Value.Meta(this)
      else -> null
    }

  /** Converts scalar expression results into request-header friendly strings when possible. */
  private fun scalarResultToString(result: Any): String? =
    when (result) {
      is kotlin.String -> result

      is Boolean,
      is Int,
      is Long,
      is Double,
      is Float -> result.toString()

      is BigDecimal -> result.toString()

      is FhirPathDate -> result.toString()

      is FhirPathDateTime -> fhirPathDateTimeToString(result)

      is FhirPathQuantity -> result.value?.toString()

      is FhirString -> result.value

      is Code -> result.value

      is Uri -> result.value

      is Url -> result.value

      is Canonical -> result.value

      is Id -> result.value

      is Uuid -> result.value

      is Markdown -> result.value

      is Oid -> result.value

      is Date -> result.value?.toString()

      is DateTime -> result.value?.toString()

      is Instant -> result.value?.toString()

      is Time -> result.value?.toString()

      else -> null
    }

  private fun scalarResultToInstant(result: Any): Instant? =
    when (result) {
      is Instant -> result

      is DateTime ->
        result.value?.toString()?.let(FhirDateTime::fromString)?.let { Instant(value = it) }

      is FhirPathDateTime ->
        FhirDateTime.fromString(fhirPathDateTimeToString(result))?.let { Instant(value = it) }

      else ->
        scalarResultToString(result)?.let(FhirDateTime::fromString)?.let { Instant(value = it) }
    }

  /** Pre-populates allocate-id variables with generated URN values for the current scope. */
  private fun allocateIdVariables(variableNames: List<String>): Map<String, Any?> =
    variableNames.associateWith { allocateIdValue() }

  @OptIn(ExperimentalUuidApi::class)
  private fun allocateIdValue(): String = "urn:uuid:${KotlinUuid.random()}"

  /** Reconstructs a FHIRPath dateTime string without inventing precision the value did not have. */
  private fun fhirPathDateTimeToString(value: FhirPathDateTime): String {
    val year = value.year.toString().padStart(4, '0')
    val month = value.month?.toString()?.padStart(2, '0')
    val day = value.day?.toString()?.padStart(2, '0')
    val hour = value.hour?.toString()?.padStart(2, '0')
    val minute = value.minute?.toString()?.padStart(2, '0')
    val second =
      value.second?.let {
        val wholeSeconds = it.toInt()
        if (it == wholeSeconds.toDouble()) {
          wholeSeconds.toString().padStart(2, '0')
        } else {
          it.toString().padStart(2, '0')
        }
      }

    val datePart =
      when (value.precision) {
        FhirPathDateTime.Precision.YEAR -> year
        FhirPathDateTime.Precision.MONTH -> "$year-$month"
        FhirPathDateTime.Precision.DAY -> "$year-$month-$day"
        FhirPathDateTime.Precision.HOUR -> "$year-$month-$day" + "T$hour"
        FhirPathDateTime.Precision.MINUTE -> "$year-$month-$day" + "T$hour:$minute"
        FhirPathDateTime.Precision.SECOND -> "$year-$month-$day" + "T$hour:$minute:$second"
      }

    return datePart + (value.utcOffset?.toString() ?: "")
  }

  private fun Resource.toJsonObject(): JsonObject =
    json.parseToJsonElement(fhirJson.encodeToString(this)).jsonObject

  /** Removes authoring-time template ids so extracted resources do not inherit contained ids. */
  private fun stripTopLevelResourceId(resourceJson: JsonObject): JsonObject =
    JsonObject(resourceJson.toMutableMap().apply { remove("id") })

  /**
   * Removes ids from the bundle template itself and from every entry resource.
   *
   * Contained resource ids are only stable enough for intra-questionnaire references. The extracted
   * transaction should derive ids/fullUrls from extraction rules instead.
   */
  private fun stripTemplateBundleIds(bundleJson: JsonObject): JsonObject {
    val cleanedEntries =
      bundleJson["entry"]?.jsonArray?.map { entryElement ->
        val entryObject = entryElement.jsonObject
        val resourceObject = entryObject["resource"]?.jsonObject
        if (resourceObject == null) {
          entryObject
        } else {
          JsonObject(
            entryObject.toMutableMap().apply {
              this["resource"] = stripTopLevelResourceId(resourceObject)
            }
          )
        }
      }

    return JsonObject(
      bundleJson.toMutableMap().apply {
        remove("id")
        cleanedEntries?.let { this["entry"] = JsonArray(it) }
      }
    )
  }
}

/** Parsed representation of a template annotation expression. */
private data class TemplateExpression(
  val expression: String,
  val name: String? = null,
  val language: String? = null,
)

/**
 * Inputs available while evaluating one template node.
 *
 * `baseContext` moves as context/value expressions dive deeper into the response, while the other
 * fields keep the broader questionnaire traversal state available for FHIRPath variables.
 */
private data class TemplateEvaluationScope(
  val baseContext: Any,
  val questionnaireItem: Questionnaire.Item?,
  val questionnaireResponseItem: QuestionnaireResponse.Item?,
  val variables: Map<String, Any?>,
) {
  fun withBaseContext(baseContext: Any, additionalVariables: Map<String, Any?> = emptyMap()) =
    copy(baseContext = baseContext, variables = variables + additionalVariables)
}

/** Template object with extraction-only annotations split away from the payload to be emitted. */
private data class StrippedTemplateAnnotations(
  val cleanedObject: JsonObject,
  val contextExpression: TemplateExpression?,
  val valueExpression: TemplateExpression?,
)

private data class PrimitiveSlotOutput(val value: JsonElement?, val metadata: JsonObject?)

private data class ProcessedPrimitiveProperty(val value: JsonElement?, val metadata: JsonElement?)

private data class EncodedFhirValue(val value: JsonElement?, val metadata: JsonObject?)

/** Intermediate representation used before rehydrating JSON into strongly typed bundle entries. */
private data class ExtractedBundleEntry(
  val resourceType: String,
  val resourceJson: JsonObject,
  val fullUrl: String,
  val requestMethod: Bundle.HTTPVerb,
  val requestUrl: String,
  val ifNoneMatch: String? = null,
  val ifModifiedSince: Instant? = null,
  val ifMatch: String? = null,
  val ifNoneExist: String? = null,
) {
  private val fhirJson = FhirR4Json()

  fun toBundleEntry(): Bundle.Entry =
    Bundle.Entry(
      fullUrl = Uri(value = fullUrl),
      resource = fhirJson.decodeFromString(resourceJson.toString()),
      request =
        Bundle.Entry.Request(
          method = Enumeration(value = requestMethod),
          url = Uri(value = requestUrl),
          ifNoneMatch = ifNoneMatch?.let { FhirString(value = it) },
          ifModifiedSince = ifModifiedSince,
          ifMatch = ifMatch?.let { FhirString(value = it) },
          ifNoneExist = ifNoneExist?.let { FhirString(value = it) },
        ),
    )
}

/**
 * Logical extraction context for a questionnaire item.
 *
 * Repeated items produce one context per answer/group instance so downstream expressions can reason
 * about a single occurrence at a time.
 */
private data class QuestionnaireItemContext(
  val baseContext: QuestionnaireResponse.Item,
  val childItems: List<QuestionnaireResponse.Item>,
)

/**
 * Expands one questionnaire item into the extraction contexts that should be traversed.
 *
 * Repeated groups are packed as answers before extraction, so each answer becomes its own logical
 * context. Non-group repeated items follow the same pattern, except their single answer is kept in
 * the copied `baseContext` so expressions can still read `answer.first()`.
 */
private fun Questionnaire.Item.toExtractionContexts(
  questionnaireResponseItem: QuestionnaireResponse.Item
): List<QuestionnaireItemContext> {
  if (repeats?.value == true) {
    return questionnaireResponseItem.answer.map { answer ->
      val baseContext =
        if (type.value == Questionnaire.QuestionnaireItemType.Group) {
          questionnaireResponseItem.copy(answer = emptyList(), item = answer.item)
        } else {
          questionnaireResponseItem.copy(answer = listOf(answer), item = answer.item)
        }
      QuestionnaireItemContext(baseContext = baseContext, childItems = answer.item)
    }
  }

  val childItems =
    when {
      type.value == Questionnaire.QuestionnaireItemType.Group -> questionnaireResponseItem.item
      else -> questionnaireResponseItem.answer.firstOrNull()?.item ?: emptyList()
    }

  return listOf(
    QuestionnaireItemContext(baseContext = questionnaireResponseItem, childItems = childItems)
  )
}

/**
 * Removes extraction-only extensions from a template object before it is emitted.
 *
 * The returned expressions are still evaluated during processing, but the resulting extracted
 * resources must not leak the template annotations into the final transaction bundle.
 */
private fun JsonObject.withoutTemplateAnnotations(): StrippedTemplateAnnotations {
  val extensionArray =
    this["extension"] as? JsonArray ?: return StrippedTemplateAnnotations(this, null, null)

  var contextExpression: TemplateExpression? = null
  var valueExpression: TemplateExpression? = null
  val remainingExtensions = mutableListOf<JsonElement>()

  extensionArray.forEach { extensionElement ->
    val extensionObject = extensionElement.jsonObject
    when (extensionObject["url"]?.jsonPrimitive?.contentOrNull) {
      EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL ->
        if (contextExpression == null) {
          contextExpression = extensionObject.toTemplateExpression()
        }

      EXTENSION_TEMPLATE_EXTRACT_VALUE_URL ->
        if (valueExpression == null) {
          valueExpression = extensionObject.toTemplateExpression()
        }

      else -> remainingExtensions += extensionElement
    }
  }

  val cleanedObject =
    JsonObject(
      toMutableMap().apply {
        if (remainingExtensions.isEmpty()) {
          remove("extension")
        } else {
          this["extension"] = JsonArray(remainingExtensions)
        }
      }
    )

  return StrippedTemplateAnnotations(
    cleanedObject = cleanedObject,
    contextExpression = contextExpression,
    valueExpression = valueExpression,
  )
}

/** Supports both shorthand `valueString` expressions and structured `valueExpression` forms. */
private fun JsonObject.toTemplateExpression(): TemplateExpression? {
  val valueString = this["valueString"]?.jsonPrimitive?.contentOrNull
  if (valueString != null) {
    return TemplateExpression(expression = valueString)
  }

  val valueExpression = this["valueExpression"]?.jsonObject ?: return null
  val expression = valueExpression["expression"]?.jsonPrimitive?.contentOrNull ?: return null
  return TemplateExpression(
    expression = expression,
    name = valueExpression["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("%"),
    language = valueExpression["language"]?.jsonPrimitive?.contentOrNull,
  )
}

private fun JsonObject.hasMaterialChildren(): Boolean =
  keys.any { key -> key !in setOf("id", "extension", "modifierExtension") }

/**
 * Treats a bare string result as `Reference.reference` for known reference-valued properties.
 *
 * This keeps authoring ergonomic for templates that conceptually want to say "the value of
 * `subject` is this reference string" without forcing them to author the wrapping object.
 */
private fun coercePrimitiveObjectReplacement(
  path: String,
  replacement: JsonElement,
  templateObject: JsonObject,
): JsonElement {
  if (replacement !is JsonPrimitive || !replacement.isString) return replacement
  if (templateObject.hasMaterialChildren()) return replacement

  val propertyName = path.substringAfterLast('.').substringBefore('[')
  return if (propertyName in referenceElementNames) {
    JsonObject(mapOf("reference" to replacement))
  } else {
    replacement
  }
}

/** Property names where a bare string result should be wrapped as a FHIR `Reference`. */
private val referenceElementNames =
  setOf(
    "subject",
    "patient",
    "author",
    "encounter",
    "focus",
    "what",
    "who",
    "device",
    "specimen",
    "location",
    "organization",
    "source",
    "destination",
    "managingOrganization",
    "generalPractitioner",
  )

private fun JsonElement?.isPrimitiveProperty(metadataElement: JsonElement?): Boolean =
  metadataElement != null ||
    this is JsonPrimitive ||
    (this is JsonArray && all { element -> element is JsonPrimitive || element is JsonNull })

private fun List<JsonElement>.toPropertyValue(): JsonElement? =
  when {
    isEmpty() -> null
    size == 1 -> single()
    else -> JsonArray(this)
  }

private fun JsonArray.mapNotNullIndexed(
  transform: (Int, JsonElement) -> ExtractedBundleEntry?
): List<ExtractedBundleEntry> =
  buildList<ExtractedBundleEntry> {
    this@mapNotNullIndexed.forEachIndexed { index, element ->
      val transformed = transform(index, element)
      if (transformed != null) {
        add(transformed)
      }
    }
  }

/**
 * Merges template overlays while appending extension arrays instead of replacing them wholesale.
 */
private fun mergeJsonObjects(first: JsonObject?, second: JsonObject?): JsonObject? {
  if (first == null) return second
  if (second == null) return first

  val merged = first.toMutableMap()
  second.forEach { (key, value) ->
    val currentValue = merged[key]
    merged[key] =
      when {
        key == "extension" && currentValue is JsonArray && value is JsonArray ->
          JsonArray(currentValue + value)

        key == "modifierExtension" && currentValue is JsonArray && value is JsonArray ->
          JsonArray(currentValue + value)

        currentValue is JsonObject && value is JsonObject -> mergeJsonObjects(currentValue, value)!!

        else -> value
      }
  }

  return JsonObject(merged)
}
