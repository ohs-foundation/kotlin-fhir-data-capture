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

import dev.ohs.fhir.model.r4.OperationOutcome
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Applies template extraction context and value directives to a cloned JSON resource tree. */
internal class TemplateTreeProcessor(
  private val evaluator: TemplateFhirPathEvaluator,
  private val valueConverter: TemplateValueConverter,
) {
  fun processResource(
    template: JsonObject,
    scope: TemplateEvaluationScope,
    issues: MutableList<TemplateExtractionIssue>,
  ): List<JsonObject> =
    processComplexObject(
      node = template,
      scope = scope,
      path = template["resourceType"]?.toString() ?: "\$",
      issues = issues,
    )

  private fun processComplexObject(
    node: JsonObject,
    scope: TemplateEvaluationScope,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): List<JsonObject> {
    val extensionState = parseTemplateNodeExtensionState(node["extension"], path, issues)
    val cleanedNode = node.withRemainingExtensions(extensionState.remainingExtensions)
    val scopedContexts =
      applyContextExpression(extensionState.controls.contextExpression, scope, path, issues)
    if (scopedContexts.isEmpty()) return emptyList()

    extensionState.controls.valueExpression?.let { valueExpression ->
      return scopedContexts.flatMap { scopedContext ->
        evaluator
          .evaluate(valueExpression, scopedContext, path, issues)
          .mapNotNull { value -> valueConverter.toJsonElement(value, path, issues) }
          .mapNotNull { value ->
            if (value is JsonObject) {
              value
            } else {
              issues +=
                TemplateExtractionIssue(
                  severity = OperationOutcome.IssueSeverity.Error,
                  code = OperationOutcome.IssueType.Invalid,
                  diagnostics =
                    "Template value replacement for '$path' must resolve to an object because the template node is complex.",
                  expressionPath = path,
                )
              null
            }
          }
      }
    }

    return scopedContexts.map { scopedContext ->
      processComplexObjectBody(cleanedNode, scopedContext, path, issues)
    }
  }

  private fun processComplexObjectBody(
    node: JsonObject,
    scope: TemplateEvaluationScope,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): JsonObject {
    val logicalPropertyNames = linkedSetOf<String>()
    node.keys.forEach { key ->
      logicalPropertyNames += if (key.startsWith("_")) key.removePrefix("_") else key
    }

    val output = linkedMapOf<String, JsonElement>()
    logicalPropertyNames.forEach { propertyName ->
      val processedProperty =
        processLogicalProperty(
          propertyName = propertyName,
          valueNode = node[propertyName],
          metadataNode = node["_$propertyName"],
          scope = scope,
          path = appendJsonPath(path, propertyName),
          issues = issues,
        )
      processedProperty.value?.let { output[propertyName] = it }
      processedProperty.metadata?.let { output["_$propertyName"] = it }
    }
    return JsonObject(output)
  }

  private fun processLogicalProperty(
    propertyName: String,
    valueNode: JsonElement?,
    metadataNode: JsonElement?,
    scope: TemplateEvaluationScope,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): ProcessedLogicalProperty =
    when (metadataNode) {
      is JsonObject -> processPrimitiveScalarProperty(valueNode, metadataNode, scope, path, issues)

      is JsonArray ->
        processPrimitiveArrayProperty(valueNode as? JsonArray, metadataNode, scope, path, issues)

      else ->
        when (valueNode) {
          null -> ProcessedLogicalProperty()

          is JsonObject -> {
            val processedValues = processComplexObject(valueNode, scope, path, issues)
            when {
              processedValues.isEmpty() -> ProcessedLogicalProperty()

              processedValues.size == 1 ->
                ProcessedLogicalProperty(value = processedValues.single())

              else -> {
                issues +=
                  TemplateExtractionIssue(
                    severity = OperationOutcome.IssueSeverity.Warning,
                    code = OperationOutcome.IssueType.Invalid,
                    diagnostics =
                      "Template node '$path' expanded to multiple objects, but the property '$propertyName' is singular. Only the first object will be kept.",
                    expressionPath = path,
                  )
                ProcessedLogicalProperty(value = processedValues.first())
              }
            }
          }

          is JsonArray ->
            ProcessedLogicalProperty(value = processArray(valueNode, scope, path, issues))

          else -> ProcessedLogicalProperty(value = valueNode)
        }
    }

  private fun processArray(
    arrayNode: JsonArray,
    scope: TemplateEvaluationScope,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): JsonArray? {
    val processedElements = mutableListOf<JsonElement>()
    arrayNode.forEachIndexed { index, element ->
      val elementPath = appendArrayPath(path, index)
      when (element) {
        is JsonObject ->
          processedElements += processComplexObject(element, scope, elementPath, issues)

        is JsonArray ->
          processArray(element, scope, elementPath, issues)?.let(processedElements::add)

        else -> processedElements += element
      }
    }
    return processedElements.takeIf { it.isNotEmpty() }?.let(::JsonArray)
  }

  private fun processPrimitiveScalarProperty(
    valueNode: JsonElement?,
    metadataNode: JsonObject,
    scope: TemplateEvaluationScope,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): ProcessedLogicalProperty {
    val occurrences = processPrimitiveOccurrences(valueNode, metadataNode, scope, path, issues)
    if (occurrences.isEmpty()) return ProcessedLogicalProperty()
    if (occurrences.size > 1) {
      issues +=
        TemplateExtractionIssue(
          severity = OperationOutcome.IssueSeverity.Warning,
          code = OperationOutcome.IssueType.Invalid,
          diagnostics =
            "Template node '$path' expanded to multiple primitive values, but the property is singular. Only the first value will be kept.",
          expressionPath = path,
        )
    }
    val occurrence = occurrences.first()
    return ProcessedLogicalProperty(value = occurrence.value, metadata = occurrence.metadata)
  }

  private fun processPrimitiveArrayProperty(
    valueNode: JsonArray?,
    metadataNode: JsonArray,
    scope: TemplateEvaluationScope,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): ProcessedLogicalProperty {
    val maxSize = maxOf(valueNode?.size ?: 0, metadataNode.size)
    val occurrences = mutableListOf<PrimitiveOccurrence>()

    for (index in 0 until maxSize) {
      val currentPath = appendArrayPath(path, index)
      val currentValue = valueNode?.getOrNull(index)
      val currentMetadata = metadataNode.getOrNull(index)
      when (currentMetadata) {
        null,
        JsonNull -> {
          if (currentValue != null) {
            occurrences += PrimitiveOccurrence(value = currentValue, metadata = null)
          }
        }

        is JsonObject -> {
          occurrences +=
            processPrimitiveOccurrences(currentValue, currentMetadata, scope, currentPath, issues)
        }

        else -> {
          issues +=
            TemplateExtractionIssue(
              severity = OperationOutcome.IssueSeverity.Error,
              code = OperationOutcome.IssueType.Invalid,
              diagnostics =
                "Primitive metadata array entries must be objects or null. Found ${currentMetadata::class.simpleName}.",
              expressionPath = currentPath,
            )
        }
      }
    }

    if (occurrences.isEmpty()) return ProcessedLogicalProperty()

    val outputValues = mutableListOf<JsonElement>()
    val outputMetadata = mutableListOf<JsonElement>()
    var hasMetadata = false
    occurrences.forEach { occurrence ->
      outputValues += occurrence.value ?: JsonNull
      if (occurrence.metadata != null) {
        outputMetadata += occurrence.metadata
        hasMetadata = true
      } else {
        outputMetadata += JsonNull
      }
    }

    return ProcessedLogicalProperty(
      value = JsonArray(outputValues),
      metadata = hasMetadata.takeIf { it }?.let { JsonArray(outputMetadata) },
    )
  }

  /**
   * Evaluates one primitive template node into zero-to-many concrete array/scalar occurrences.
   *
   * The SDC template rules allow a primitive placeholder inside an array to expand into multiple
   * actual primitive values. We keep the metadata object paired with each expanded value so
   * primitive extension companions stay aligned with the generated output slots.
   */
  private fun processPrimitiveOccurrences(
    valueNode: JsonElement?,
    metadataNode: JsonObject,
    scope: TemplateEvaluationScope,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): List<PrimitiveOccurrence> {
    val extensionState = parseTemplateNodeExtensionState(metadataNode["extension"], path, issues)
    val cleanedMetadata = metadataNode.withRemainingExtensions(extensionState.remainingExtensions)
    val scopedContexts =
      applyContextExpression(extensionState.controls.contextExpression, scope, path, issues)
    if (scopedContexts.isEmpty()) return emptyList()

    return scopedContexts.flatMap { scopedContext ->
      extensionState.controls.valueExpression?.let { valueExpression ->
        val results = evaluator.evaluate(valueExpression, scopedContext, path, issues)
        if (results.isEmpty()) {
          emptyList()
        } else {
          results.mapNotNull { value ->
            valueConverter.toPrimitiveJsonElement(value, path, issues)?.let { converted ->
              PrimitiveOccurrence(
                value = converted,
                metadata = cleanedMetadata.takeIf { it.entries.isNotEmpty() },
              )
            }
          }
        }
      }
        ?: listOf(
          PrimitiveOccurrence(
            value = valueNode,
            metadata = cleanedMetadata.takeIf { it.entries.isNotEmpty() },
          )
        )
    }
  }

  private fun applyContextExpression(
    expression: TemplateExtractExpression?,
    scope: TemplateEvaluationScope,
    path: String,
    issues: MutableList<TemplateExtractionIssue>,
  ): List<TemplateEvaluationScope> {
    if (expression == null) return listOf(scope)
    val results = evaluator.evaluate(expression, scope, path, issues)
    if (results.isEmpty()) return emptyList()
    return results.map { result ->
      scope.withContext(
        nextContext = result,
        namedVariable = expression.variableName,
        namedValue = result,
      )
    }
  }
}

private data class ProcessedLogicalProperty(
  val value: JsonElement? = null,
  val metadata: JsonElement? = null,
)

private data class PrimitiveOccurrence(val value: JsonElement?, val metadata: JsonObject?)

private fun JsonObject.withRemainingExtensions(remainingExtensions: JsonArray?): JsonObject {
  val updated = toMutableMap()
  if (containsKey("extension")) {
    if (remainingExtensions == null) {
      updated.remove("extension")
    } else {
      updated["extension"] = remainingExtensions
    }
  }
  return JsonObject(updated)
}

private fun appendJsonPath(path: String, propertyName: String): String =
  when {
    path.isBlank() -> propertyName
    path == "$" -> "$.$propertyName"
    else -> "$path.$propertyName"
  }

private fun appendArrayPath(path: String, index: Int): String = "$path[$index]"
