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
import dev.ohs.fhir.datacapture.extensions.findContainedResource
import dev.ohs.fhir.datacapture.extensions.isRepeatedGroup
import dev.ohs.fhir.datacapture.extensions.templateExtractExtensions
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.OperationOutcome
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.Resource
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Uri
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Stateful worker for a single template extraction run. */
internal class TemplateExtractionEngine(
  private val questionnaire: Questionnaire,
  private val questionnaireResponse: QuestionnaireResponse,
) {
  private val issues = mutableListOf<TemplateExtractionIssue>()
  private val evaluator = TemplateFhirPathEvaluator()
  private val valueConverter = TemplateValueConverter()
  private val treeProcessor = TemplateTreeProcessor(evaluator, valueConverter)

  /**
   * Runs extraction in the same order SDC expects consumers to reason about it: questionnaire-level
   * templates first, then item-level templates against each logical item occurrence, while carrying
   * forward any allocated `%variable` values that later templates may reference.
   */
  fun extract(): TemplateExtractionResult {
    val entries = mutableListOf<Bundle.Entry>()
    val rootVariables = allocateIdVariables(questionnaire.allocateIdVariableNames)
    val rootScope =
      TemplateEvaluationScope(
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        questionnaireItem = null,
        context = questionnaireResponse,
        variables = rootVariables,
      )

    questionnaire.templateExtractExtensions.forEach { definition ->
      extractTemplate(definition, rootScope, "Questionnaire").let(entries::addIfPresent)
    }

    traverseQuestionnaireItems(
      questionnaireItems = questionnaire.item,
      responseItems = questionnaireResponse.item,
      inheritedVariables = rootVariables,
      outputEntries = entries,
    )

    return TemplateExtractionResult(
      bundle = Bundle(type = Enumeration(value = Bundle.BundleType.Transaction), entry = entries),
      operationOutcome = issues.takeIf { it.isNotEmpty() }?.toOperationOutcome(),
    )
  }

  private fun traverseQuestionnaireItems(
    questionnaireItems: List<Questionnaire.Item>,
    responseItems: List<QuestionnaireResponse.Item>,
    inheritedVariables: Map<String, Any?>,
    outputEntries: MutableList<Bundle.Entry>,
  ) {
    questionnaireItems.forEach { questionnaireItem ->
      val matchingResponseItems =
        responseItems.filter { it.linkId.value == questionnaireItem.linkId.value }
      if (matchingResponseItems.isEmpty()) return@forEach

      if (
        !questionnaireItem.isRepeatedGroup &&
          questionnaireItem.repeats?.value != true &&
          matchingResponseItems.size > 1
      ) {
        issues +=
          TemplateExtractionIssue(
            severity = OperationOutcome.IssueSeverity.Warning,
            code = OperationOutcome.IssueType.Invalid,
            diagnostics =
              "Multiple QuestionnaireResponse items were found for non-repeating item '${questionnaireItem.linkId.value}'. Only the first occurrence will be used for direct extraction.",
            expressionPath = questionnaireItem.linkId.value,
          )
      }

      questionnaireItem.toExtractionContexts(matchingResponseItems).forEach { extractionContext ->
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

        questionnaireItem.templateExtractExtensions.forEach { definition ->
          extractTemplate(definition, scope, questionnaireItem.linkId.value ?: "Questionnaire.item")
            .let(outputEntries::addIfPresent)
        }

        if (questionnaireItem.item.isNotEmpty()) {
          traverseQuestionnaireItems(
            questionnaireItems = questionnaireItem.item,
            responseItems = extractionContext.childResponseItems,
            inheritedVariables = currentVariables,
            outputEntries = outputEntries,
          )
        }
      }
    }
  }

  private fun extractTemplate(
    definition: TemplateExtractDefinition,
    scope: TemplateEvaluationScope,
    path: String,
  ): Bundle.Entry? {
    val templateResource = questionnaire.findContainedResource(definition.templateReference)
    if (templateResource == null) {
      issues +=
        TemplateExtractionIssue(
          severity = OperationOutcome.IssueSeverity.Error,
          code = OperationOutcome.IssueType.Required,
          diagnostics =
            "Contained template '${definition.templateReference}' was not found in the questionnaire.",
          expressionPath = path,
        )
      return null
    }

    val templateJson = valueConverter.resourceToJson(templateResource).withoutTemplateId()
    val resourceType =
      templateJson["resourceType"]?.jsonPrimitive?.contentOrNull
        ?: run {
          issues +=
            TemplateExtractionIssue(
              severity = OperationOutcome.IssueSeverity.Error,
              code = OperationOutcome.IssueType.Invalid,
              diagnostics =
                "Contained template '${definition.templateReference}' is missing resourceType.",
              expressionPath = path,
            )
          return null
        }

    // The JSON tree pass applies templateExtractContext/templateExtractValue recursively before we
    // materialize the resource back into typed Kotlin FHIR models.
    val processedResources = treeProcessor.processResource(templateJson, scope, issues)
    if (processedResources.isEmpty()) return null
    if (processedResources.size > 1) {
      issues +=
        TemplateExtractionIssue(
          severity = OperationOutcome.IssueSeverity.Warning,
          code = OperationOutcome.IssueType.Invalid,
          diagnostics =
            "Template '${definition.templateReference}' expanded to multiple resources in a singular extraction context. Only the first resource will be emitted.",
          expressionPath = path,
        )
    }

    val resourceJson = processedResources.first().withEvaluatedResourceId(definition, scope, path)
    val extractedResource = valueConverter.jsonToResource(resourceJson, path, issues) ?: return null
    return createBundleEntry(definition, scope, path, resourceType, extractedResource)
  }

  private fun JsonObject.withEvaluatedResourceId(
    definition: TemplateExtractDefinition,
    scope: TemplateEvaluationScope,
    path: String,
  ): JsonObject {
    val mutable = toMutableMap()
    val evaluatedId =
      definition.resourceIdExpression
        ?.let {
          evaluator.evaluate(TemplateExtractExpression(it), scope, "$path.resourceId", issues)
        }
        ?.let { values -> valueConverter.toStringValue(values, "$path.resourceId", issues) }
        ?.takeIf { it.isNotBlank() }

    if (evaluatedId == null) {
      mutable.remove("id")
      mutable.remove("_id")
    } else {
      mutable["id"] = JsonPrimitive(evaluatedId)
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
        ?.let { evaluator.evaluate(TemplateExtractExpression(it), scope, "$path.fullUrl", issues) }
        ?.let { values -> valueConverter.toStringValue(values, "$path.fullUrl", issues) }
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
      ?.let {
        evaluator.evaluate(TemplateExtractExpression(it), scope, "$path.ifNoneMatch", issues)
      }
      ?.let { values -> valueConverter.toStringValue(values, "$path.ifNoneMatch", issues) }
      ?.takeIf { it.isNotBlank() }
      ?.let { requestBuilder.ifNoneMatch = FhirString.Builder().apply { value = it } }

    definition.ifMatchExpression
      ?.let { evaluator.evaluate(TemplateExtractExpression(it), scope, "$path.ifMatch", issues) }
      ?.let { values -> valueConverter.toStringValue(values, "$path.ifMatch", issues) }
      ?.takeIf { it.isNotBlank() }
      ?.let { requestBuilder.ifMatch = FhirString.Builder().apply { value = it } }

    definition.ifNoneExistExpression
      ?.let {
        evaluator.evaluate(TemplateExtractExpression(it), scope, "$path.ifNoneExist", issues)
      }
      ?.let { values -> valueConverter.toStringValue(values, "$path.ifNoneExist", issues) }
      ?.takeIf { it.isNotBlank() }
      ?.let { requestBuilder.ifNoneExist = FhirString.Builder().apply { value = it } }

    definition.ifModifiedSinceExpression
      ?.let {
        evaluator.evaluate(TemplateExtractExpression(it), scope, "$path.ifModifiedSince", issues)
      }
      ?.let { values -> valueConverter.toInstantValue(values, "$path.ifModifiedSince", issues) }
      ?.let { instant -> requestBuilder.ifModifiedSince = instant.toBuilder() }

    return Bundle.Entry(
      fullUrl = Uri(value = fullUrl),
      resource = extractedResource,
      request = requestBuilder.build(),
    )
  }

  @OptIn(ExperimentalUuidApi::class)
  private fun allocateIdVariables(variableNames: List<String>): Map<String, Any?> =
    variableNames.associateWith { generateAllocatedFullUrl() }

  @OptIn(ExperimentalUuidApi::class)
  private fun generateAllocatedFullUrl(): String = "urn:uuid:${Uuid.random()}"
}

private fun MutableList<Bundle.Entry>.addIfPresent(entry: Bundle.Entry?) {
  if (entry != null) {
    add(entry)
  }
}

private fun JsonObject.withoutTemplateId(): JsonObject =
  JsonObject(
    toMutableMap().apply {
      remove("id")
      remove("_id")
    }
  )
