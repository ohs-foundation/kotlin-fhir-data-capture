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
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import dev.ohs.fhir.datacapture.extensions.elementValue
import dev.ohs.fhir.datacapture.extensions.packRepeatedGroups
import dev.ohs.fhir.datacapture.extensions.referencesQuestionnaireResponseResource
import dev.ohs.fhir.datacapture.extraction.template.EXTENSION_EXTRACT_ALLOCATE_ID_URL
import dev.ohs.fhir.datacapture.fhirpath.FhirPathService
import dev.ohs.fhir.fhirpath.types.FhirPathDate
import dev.ohs.fhir.fhirpath.types.FhirPathDateTime
import dev.ohs.fhir.fhirpath.types.FhirPathQuantity
import dev.ohs.fhir.fhirpath.types.FhirPathTime
import dev.ohs.fhir.model.r4.Attachment
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Canonical
import dev.ohs.fhir.model.r4.Code
import dev.ohs.fhir.model.r4.CodeableConcept
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.ContactPoint
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Identifier
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.Period
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.String as FhirString
import dev.ohs.fhir.model.r4.Time
import dev.ohs.fhir.model.r4.Uri
import kotlin.random.Random
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Implements the SDC definition-based extraction workflow for completed [QuestionnaireResponse]s.
 *
 * The SDC "Form Data Extraction" guide describes this approach as:
 * - locating each `definitionExtract` scope that initiates a resource
 * - creating a stub resource for that scope
 * - walking the scoped Questionnaire/QuestionnaireResponse items
 * - populating matching `Questionnaire.item.definition` paths and `definitionExtractValue`
 *   directives that share the same canonical definition
 * - assembling the resulting resource into a transaction `Bundle.entry`
 *
 * Reference: https://build.fhir.org/ig/HL7/sdc/en/extraction.html#definition-extract
 */
internal const val EXTENSION_DEFINITION_EXTRACT_URL: String =
  "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract"

internal const val EXTENSION_DEFINITION_EXTRACT_VALUE_URL: String =
  "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"

object DefinitionExtractionEngine {
  private val json = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  fun canExtract(questionnaire: Questionnaire): Boolean =
    questionnaire.extension.any { it.url == EXTENSION_DEFINITION_EXTRACT_URL } ||
      questionnaire.item.any { it.hasDefinitionExtractRecursively() }

  fun extractByDefinition(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): Bundle {
    requireMatchingQuestionnaire(questionnaire, questionnaireResponse)

    val packedResponse =
      questionnaireResponse.toBuilder().apply { packRepeatedGroups(questionnaire) }.build()
    val rootPairs =
      alignQuestionnaireItemsWithResponseItems(questionnaire.item, packedResponse.item)
    val rootAllocateIds =
      questionnaire.extractAllocateIdVariableNames.associateWith { generateAllocatedFullUrl() }
    val entries = mutableListOf<JsonObject>()

    questionnaire.definitionExtractExtensions.forEach { definitionExtract ->
      appendValidEntryOrSkip(
        outputEntries = entries,
        scopeDescription = "Questionnaire definition '${definitionExtract.definition}'",
      ) {
        extractBundleEntryForDefinitionScope(
          definitionExtract = definitionExtract,
          questionnaire = questionnaire,
          questionnaireResponse = questionnaireResponse,
          scopeBase = packedResponse,
          scopeQuestionnaireItem = null,
          scopePairs = rootPairs,
          inheritedAllocateIds = rootAllocateIds,
        )
      }
    }

    extractNestedDefinitionScopeEntries(
      pairs = rootPairs,
      questionnaire = questionnaire,
      questionnaireResponse = questionnaireResponse,
      inheritedAllocateIds = rootAllocateIds,
      outputEntries = entries,
    )

    if (entries.isEmpty()) {
      Logger.w(
        "Definition-based extraction did not produce any valid bundle entries. Returning an empty transaction bundle."
      )
    }

    val bundleJson = buildJsonObject {
      put("resourceType", JsonPrimitive("Bundle"))
      put("type", JsonPrimitive("transaction"))
      put("entry", JsonArray(entries))
    }

    return FhirPathService.jsonToResource(bundleJson, "Bundle") as Bundle
  }

  private fun requireMatchingQuestionnaire(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ) {
    require(
      questionnaireResponse.questionnaire?.value == null ||
        questionnaireResponse.questionnaire?.value == questionnaire.url?.value
    ) {
      "Mismatching Questionnaire ${questionnaire.url?.value} and QuestionnaireResponse (for Questionnaire ${questionnaireResponse.questionnaire?.value})"
    }
  }

  /**
   * Executes one `definitionExtract` scope and returns the `Bundle.entry` JSON for that scope.
   *
   * This is the spec's "initiate resource extraction" step. It creates the stub resource identified
   * by the `definitionExtract.definition`, applies any root/item `definitionExtractValue`
   * directives in scope, walks descendant items whose `Questionnaire.item.definition` canonical
   * matches the scoped canonical, and then assembles request metadata such as `fullUrl`, `method`,
   * and conditional headers.
   */
  private fun extractBundleEntryForDefinitionScope(
    definitionExtract: DefinitionExtractConfig,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    scopeBase: Any,
    scopeQuestionnaireItem: Questionnaire.Item?,
    scopePairs: List<QuestionnaireItemResponsePair>,
    inheritedAllocateIds: Map<String, String>,
  ): JsonObject {
    val resourceType = inferResourceType(definitionExtract.definition, scopePairs)
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

    val entryJson = buildJsonObject {
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
        buildJsonObject {
          put("method", JsonPrimitive(requestMethod))
          put(
            "url",
            JsonPrimitive(
              if (resourceId.isNullOrBlank()) resourceType else "$resourceType/$resourceId"
            ),
          )
          definitionExtract.ifNoneMatchExpression
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
            ?.let { put("ifNoneMatch", JsonPrimitive(it)) }
          definitionExtract.ifModifiedSinceExpression
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
            ?.let { put("ifModifiedSince", JsonPrimitive(it)) }
          definitionExtract.ifMatchExpression
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
            ?.let { put("ifMatch", JsonPrimitive(it)) }
          definitionExtract.ifNoneExistExpression
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
            ?.let { put("ifNoneExist", JsonPrimitive(it)) }
        },
      )
    }

    return entryJson
  }

  /**
   * Recursively finds nested `definitionExtract` scopes below the Questionnaire root.
   *
   * The SDC definition-based rules say that item-level `definitionExtract` directives only create a
   * resource when the corresponding QuestionnaireResponse item has content, and that repeating
   * items create a distinct resource per repetition. This traversal enforces both rules while also
   * carrying inherited `extractAllocateId` variables down the tree.
   */
  private fun extractNestedDefinitionScopeEntries(
    pairs: List<QuestionnaireItemResponsePair>,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    inheritedAllocateIds: Map<String, String>,
    outputEntries: MutableList<JsonObject>,
  ) {
    for (pair in pairs) {
      val pairAllocateIds =
        inheritedAllocateIds +
          pair.questionnaireItem.extractAllocateIdVariableNames.associateWith {
            generateAllocatedFullUrl()
          }

      for (definitionExtract in pair.questionnaireItem.definitionExtractExtensions) {
        if (pair.questionnaireItem.repeats?.value == true && !pair.questionnaireItem.isGroup()) {
          for (answer in pair.responseItem.answer) {
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
            if (hasResponseContent(syntheticResponseItem)) {
              appendValidEntryOrSkip(
                outputEntries = outputEntries,
                scopeDescription =
                  "item '${pair.questionnaireItem.linkId.value.orEmpty()}' definition '${definitionExtract.definition}'",
              ) {
                extractBundleEntryForDefinitionScope(
                  definitionExtract = definitionExtract,
                  questionnaire = questionnaire,
                  questionnaireResponse = questionnaireResponse,
                  scopeBase = syntheticResponseItem,
                  scopeQuestionnaireItem = pair.questionnaireItem,
                  scopePairs = listOf(syntheticPair),
                  inheritedAllocateIds = pairAllocateIds,
                )
              }
            }
          }
        } else if (hasResponseContent(pair.responseItem)) {
          appendValidEntryOrSkip(
            outputEntries = outputEntries,
            scopeDescription =
              "item '${pair.questionnaireItem.linkId.value.orEmpty()}' definition '${definitionExtract.definition}'",
          ) {
            extractBundleEntryForDefinitionScope(
              definitionExtract = definitionExtract,
              questionnaire = questionnaire,
              questionnaireResponse = questionnaireResponse,
              scopeBase = pair.responseItem,
              scopeQuestionnaireItem = pair.questionnaireItem,
              scopePairs = listOf(pair),
              inheritedAllocateIds = pairAllocateIds,
            )
          }
        }
      }

      extractNestedDefinitionScopeEntries(
        pairs = pair.children,
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
        inheritedAllocateIds = pairAllocateIds,
        outputEntries = outputEntries,
      )
    }
  }

  /**
   * Applies one aligned Questionnaire/QuestionnaireResponse item pair to the current resource
   * scope.
   *
   * If the item's `Questionnaire.item.definition` belongs to the current `definitionExtract`
   * canonical, its answers populate the matching StructureDefinition path. Group items act as
   * anchors for repeated backbone/collection elements so descendant answers land in the same
   * extracted object instance, as described by the SDC parent/child matching rules.
   */
  private fun applyQuestionnairePairToDefinitionScope(
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
        pair.questionnaireItem.extractAllocateIdVariableNames.associateWith {
          generateAllocatedFullUrl()
        }

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

  private fun canonicalMatches(left: String, right: String): Boolean =
    left.substringBefore("|") == right.substringBefore("|")

  /**
   * Applies `definitionExtractValue` directives for the current scope.
   *
   * The SDC definition-based spec allows fixed values and calculated FHIRPath values to populate
   * the extracted resource even when the user did not directly answer that exact property. Only
   * directives whose canonical definition matches the active `definitionExtract` scope are applied.
   */
  private fun applyDefinitionExtractValueDirectives(
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
      .asSequence()
      .filter { it.url == EXTENSION_DEFINITION_EXTRACT_VALUE_URL }
      .map(::parseDefinitionExtractValue)
      .filter { config ->
        canonicalMatches(left = config.definition.canonical, right = scopeCanonical)
      }
      .forEach { config ->
        val rawValues =
          config.expression?.let { expression ->
            evaluateDefinitionExtractExpression(
              expression = expression,
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

        val definitionPath = config.definition.pathSegments

        val targetAnchor =
          when {
            directAnchor != null && definitionPath.startsWithPath(directAnchor.path) -> {
              directAnchor
            }

            parentAnchor.path.isNotEmpty() && definitionPath.startsWithPath(parentAnchor.path) -> {
              parentAnchor
            }

            else -> {
              ensureDefinitionPathAnchor(
                parentAnchor = rootAnchor,
                anchorPath = computeValueAnchorPath(definitionPath),
              )
            }
          }

        writeValuesToDefinitionPath(
          rootAnchor = rootAnchor,
          anchor = targetAnchor,
          fullPath = definitionPath,
          rawValues = rawValues,
        )
      }
  }

  /**
   * Adds an extracted entry only if it fully materializes as a valid `Bundle.entry`.
   *
   * The SDC guide says extraction errors should be logged and processing should continue as though
   * the failed query or directive produced no data. This helper keeps that behavior localized to
   * one resource scope instead of failing the whole extraction transaction.
   */
  private inline fun appendValidEntryOrSkip(
    outputEntries: MutableList<JsonObject>,
    scopeDescription: String,
    block: () -> JsonObject,
  ) {
    try {
      val entryJson = block()
      json.decodeFromJsonElement(Bundle.Entry.serializer(), entryJson)
      outputEntries.add(entryJson)
    } catch (throwable: Throwable) {
      Logger.w(
        "Skipping definition-based extraction for $scopeDescription because it could not be fully materialized.",
        throwable,
      )
    }
  }

  /**
   * Evaluates a FHIRPath expression in the SDC definition-extraction context.
   *
   * Per the spec, `$extract` expressions only have access to QuestionnaireResponse data,
   * Questionnaire data, and `extractAllocateId` variables. This wrapper builds that scoped variable
   * map and delegates raw FHIRPath execution to the shared [FhirPathService].
   *
   * Item-scoped directives still need `%resource` to resolve against the whole
   * QuestionnaireResponse even though relative paths such as `item.where(...)` should evaluate
   * against the current `QuestionnaireResponse.Item`. Choose the evaluation root up front based on
   * whether the expression references `%resource` instead of maintaining a brittle
   * expression-by-expression fallback table.
   */
  private fun evaluateDefinitionExtractExpression(
    expression: Expression,
    base: Any,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    questionnaireItem: Questionnaire.Item?,
    responseItem: QuestionnaireResponse.Item?,
    allocateIds: Map<String, String>,
  ): List<Any> {
    val expressionString = expression.expression?.value ?: return emptyList()
    val variables = buildMap {
      put("resource", questionnaireResponse)
      put("context", responseItem ?: base)
      put("questionnaire", questionnaire)
      questionnaireItem?.let { put("qItem", it) }
      putAll(allocateIds)
    }

    val evaluationRoot =
      if (expressionString.referencesQuestionnaireResponseResource()) {
        questionnaireResponse
      } else {
        base
      }

    return FhirPathService.evaluate(
      expression = expressionString,
      resource = evaluationRoot,
      variables = variables,
    )
  }

  /**
   * Resolves a definition-extraction FHIRPath expression to the singular string form required by
   * `Bundle.entry` request metadata such as `fullUrl`, `ifMatch`, and `ifNoneExist`.
   */
  private fun evaluateDefinitionExtractExpressionToString(
    expression: String,
    base: Any,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    questionnaireItem: Questionnaire.Item?,
    responseItem: QuestionnaireResponse.Item?,
    allocateIds: Map<String, String>,
  ): String =
    FhirPathService.toStringValue(
      values =
        evaluateDefinitionExtractExpression(
          expression =
            Expression(
              language = Enumeration(value = Expression.ExpressionLanguage.Text_Fhirpath),
              expression = FhirString(value = expression),
            ),
          base = base,
          questionnaire = questionnaire,
          questionnaireResponse = questionnaireResponse,
          questionnaireItem = questionnaireItem,
          responseItem = responseItem,
          allocateIds = allocateIds,
        ),
      path = "definition extraction expression '$expression'",
    ) ?: ""

  private fun fixedValueToRawValue(fixedValue: Extension.Value): Any =
    when (fixedValue) {
      is Extension.Value.Boolean -> fixedValue.value

      is Extension.Value.Code -> fixedValue.value

      is Extension.Value.CodeableConcept -> fixedValue.value

      is Extension.Value.Coding -> fixedValue.value

      is Extension.Value.Date -> fixedValue.value

      is Extension.Value.DateTime -> fixedValue.value

      is Extension.Value.Decimal -> fixedValue.value

      is Extension.Value.Identifier -> fixedValue.value

      is Extension.Value.Integer -> fixedValue.value

      is Extension.Value.Meta -> fixedValue.value

      is Extension.Value.Quantity -> fixedValue.value

      is Extension.Value.Reference -> fixedValue.value

      is Extension.Value.String -> fixedValue.value

      is Extension.Value.Time -> fixedValue.value

      is Extension.Value.Uri -> fixedValue.value

      is Extension.Value.Canonical -> fixedValue.value

      is Extension.Value.Attachment -> fixedValue.value

      is Extension.Value.HumanName -> fixedValue.value

      is Extension.Value.ContactPoint -> fixedValue.value

      is Extension.Value.Period -> fixedValue.value

      else ->
        error(
          "Unsupported fixed value type ${fixedValue::class.simpleName} in definition-based extraction"
        )
    }

  /**
   * Writes extracted answer/fixed/calculated values into the StructureDefinition path named by
   * `Questionnaire.item.definition` or `definitionExtractValue.definition`.
   *
   * This implements the spec rule that intermediate resource elements do not need matching
   * Questionnaire items; the extractor creates the missing backbone/data type objects as needed and
   * only enforces cardinality when the target leaf is singular.
   */
  private fun writeValuesToDefinitionPath(
    rootAnchor: AnchorContext,
    anchor: AnchorContext,
    fullPath: List<String>,
    rawValues: List<Any>,
  ) {
    val relativePath = fullPath.drop(anchor.path.size)
    if (relativePath.isEmpty()) {
      return
    }

    var currentNode = anchor.node
    var currentDescriptor = anchor.descriptor

    relativePath.dropLast(1).forEach { segment ->
      val fieldInfo = findFieldInfo(currentDescriptor, segment)
      currentNode = ensureObjectChild(currentNode, fieldInfo)
      currentDescriptor = currentNode.descriptor
    }

    val leafFieldInfo = findFieldInfo(currentDescriptor, relativePath.last(), rawValues)
    val leafName = leafFieldInfo.jsonName

    if (leafFieldInfo.isList) {
      val elementDescriptor = leafFieldInfo.descriptor.getElementDescriptor(0)
      val existingArray = currentNode.values[leafName] as? MutableJsonArray
      val targetArray =
        existingArray ?: MutableJsonArray().also { currentNode.values[leafName] = it }
      for (rawValue in rawValues) {
        targetArray.values.add(MutableJsonLiteral(encodeValueForField(rawValue, elementDescriptor)))
      }
      return
    }

    require(rawValues.size == 1) {
      "Multiple values cannot be assigned to singular field '$leafName'."
    }
    currentNode.values[leafName] =
      MutableJsonLiteral(encodeValueForField(rawValues.single(), leafFieldInfo.descriptor))
  }

  private fun encodeValueForField(rawValue: Any, fieldDescriptor: SerialDescriptor): JsonElement {
    if (
      fieldDescriptor.kind == StructureKind.CLASS &&
        looksLikeCodeableConcept(fieldDescriptor) &&
        rawValue is Coding
    ) {
      return buildJsonObject {
        put(
          "coding",
          buildJsonArray { add(json.encodeToJsonElement(Coding.serializer(), rawValue)) },
        )
      }
    }

    if (
      fieldDescriptor.kind == StructureKind.CLASS &&
        looksLikeReference(fieldDescriptor) &&
        rawValue is String
    ) {
      return buildJsonObject { put("reference", JsonPrimitive(rawValue)) }
    }

    return when (rawValue) {
      is String -> JsonPrimitive(rawValue)

      is Boolean -> JsonPrimitive(rawValue)

      is Int -> JsonPrimitive(rawValue)

      is Long -> JsonPrimitive(rawValue)

      is BigDecimal -> JsonPrimitive(rawValue.toString())

      is FhirString -> JsonPrimitive(rawValue.value ?: "")

      is dev.ohs.fhir.model.r4.Boolean -> JsonPrimitive(rawValue.value ?: false)

      is Integer -> JsonPrimitive(rawValue.value ?: 0)

      is Decimal -> JsonPrimitive(rawValue.value?.toString() ?: "0")

      is Date -> JsonPrimitive(rawValue.value?.toString() ?: "")

      is DateTime -> JsonPrimitive(rawValue.value?.toString() ?: "")

      is Time -> JsonPrimitive(rawValue.value?.toString() ?: "")

      is Uri -> JsonPrimitive(rawValue.value ?: "")

      is Canonical -> JsonPrimitive(rawValue.value ?: "")

      is Code -> JsonPrimitive(rawValue.value ?: "")

      is Coding ->
        if (fieldDescriptor.kind is PrimitiveKind) {
          JsonPrimitive(rawValue.code?.value ?: "")
        } else {
          json.encodeToJsonElement(Coding.serializer(), rawValue)
        }

      is Reference ->
        if (fieldDescriptor.kind is PrimitiveKind) {
          JsonPrimitive(rawValue.reference?.value ?: "")
        } else {
          json.encodeToJsonElement(Reference.serializer(), rawValue)
        }

      is Quantity ->
        if (
          fieldDescriptor.kind is PrimitiveKind && fieldDescriptor.serialName.endsWith(".value")
        ) {
          JsonPrimitive(rawValue.value?.value?.toString() ?: "")
        } else {
          json.encodeToJsonElement(Quantity.serializer(), rawValue)
        }

      is CodeableConcept -> json.encodeToJsonElement(CodeableConcept.serializer(), rawValue)

      is Identifier -> json.encodeToJsonElement(Identifier.serializer(), rawValue)

      is HumanName -> json.encodeToJsonElement(HumanName.serializer(), rawValue)

      is ContactPoint -> json.encodeToJsonElement(ContactPoint.serializer(), rawValue)

      is Meta -> json.encodeToJsonElement(Meta.serializer(), rawValue)

      is Period -> json.encodeToJsonElement(Period.serializer(), rawValue)

      is Attachment -> json.encodeToJsonElement(Attachment.serializer(), rawValue)

      is FhirPathDate -> JsonPrimitive(rawValue.toString())

      is FhirPathDateTime -> JsonPrimitive(formatFhirPathDateTime(rawValue))

      is FhirPathTime -> JsonPrimitive(formatFhirPathTime(rawValue))

      is FhirPathQuantity ->
        buildJsonObject {
          rawValue.value?.let { put("value", JsonPrimitive(it.toString())) }
          rawValue.unit?.let { put("unit", JsonPrimitive(it)) }
        }

      else ->
        error(
          "Unsupported value type ${rawValue::class.simpleName} for descriptor ${fieldDescriptor.serialName}"
        )
    }
  }

  private fun ensureDefinitionPathAnchor(
    parentAnchor: AnchorContext,
    anchorPath: List<String>,
    appendFinalListElement: Boolean = false,
  ): AnchorContext {
    require(anchorPath.startsWithPath(parentAnchor.path)) {
      "Anchor path '${anchorPath.joinToString(".")}' must extend parent anchor " +
        "'${parentAnchor.path.joinToString(".")}'."
    }

    val relativePath = anchorPath.drop(parentAnchor.path.size)

    var currentNode = parentAnchor.node
    var currentDescriptor = parentAnchor.descriptor

    relativePath.forEachIndexed { index, segment ->
      val fieldInfo = findFieldInfo(currentDescriptor, segment)
      val isFinalSegment = index == relativePath.lastIndex

      currentNode =
        ensureObjectChild(
          currentNode = currentNode,
          fieldInfo = fieldInfo,
          appendToList = appendFinalListElement && isFinalSegment,
        )

      currentDescriptor = currentNode.descriptor
    }

    return AnchorContext(path = anchorPath, node = currentNode, descriptor = currentDescriptor)
  }

  private fun ensureObjectChild(
    currentNode: MutableJsonObject,
    fieldInfo: FieldInfo,
    appendToList: Boolean = false,
  ): MutableJsonObject {
    if (fieldInfo.isList) {
      val array =
        (currentNode.values[fieldInfo.jsonName] as? MutableJsonArray)
          ?: MutableJsonArray().also { currentNode.values[fieldInfo.jsonName] = it }
      if (!appendToList && array.values.lastOrNull() is MutableJsonObject) {
        return array.values.last() as MutableJsonObject
      }
      val objectValue = MutableJsonObject(fieldInfo.descriptor.getElementDescriptor(0))
      array.values.add(objectValue)
      return objectValue
    }

    val existing = currentNode.values[fieldInfo.jsonName] as? MutableJsonObject
    if (existing != null) {
      return existing
    }
    require(fieldInfo.descriptor.kind == StructureKind.CLASS) {
      "Cannot descend into primitive field ${fieldInfo.jsonName}"
    }
    return MutableJsonObject(fieldInfo.descriptor).also {
      currentNode.values[fieldInfo.jsonName] = it
    }
  }

  private fun findFieldInfo(
    descriptor: SerialDescriptor,
    requestedName: String,
    rawValues: List<Any> = emptyList(),
  ): FieldInfo {
    val directIndex = descriptor.getElementIndex(requestedName)
    if (directIndex != CompositeDecoder.UNKNOWN_NAME) {
      val directDescriptor = descriptor.getElementDescriptor(directIndex)
      if (isChoiceContainer(requestedName, directDescriptor)) {
        val childName = resolveChoiceChildName(requestedName, directDescriptor, rawValues)
        val childIndex = directDescriptor.getElementIndex(childName)
        return FieldInfo(
          jsonName = childName,
          descriptor = directDescriptor.getElementDescriptor(childIndex),
          isList = false,
        )
      }
      return FieldInfo(
        jsonName = requestedName,
        descriptor = directDescriptor,
        isList = directDescriptor.kind == StructureKind.LIST,
      )
    }

    val flatChoiceCandidateNames =
      (0 until descriptor.elementsCount).map(descriptor::getElementName).filter { candidateName ->
        candidateName.startsWith(requestedName) &&
          candidateName.length > requestedName.length &&
          candidateName[requestedName.length].isUpperCase()
      }
    if (flatChoiceCandidateNames.isNotEmpty()) {
      val childName = resolveFlatChoiceChildName(requestedName, flatChoiceCandidateNames, rawValues)
      val childIndex = descriptor.getElementIndex(childName)
      return FieldInfo(
        jsonName = childName,
        descriptor = descriptor.getElementDescriptor(childIndex),
        isList = descriptor.getElementDescriptor(childIndex).kind == StructureKind.LIST,
      )
    }

    repeat(descriptor.elementsCount) { index ->
      val candidateName = descriptor.getElementName(index)
      val candidateDescriptor = descriptor.getElementDescriptor(index)
      if (!isChoiceContainer(candidateName, candidateDescriptor)) {
        return@repeat
      }
      val childIndex = candidateDescriptor.getElementIndex(requestedName)
      if (childIndex != CompositeDecoder.UNKNOWN_NAME) {
        return FieldInfo(
          jsonName = requestedName,
          descriptor = candidateDescriptor.getElementDescriptor(childIndex),
          isList = false,
        )
      }
    }

    error("Field '$requestedName' was not found in descriptor ${descriptor.serialName}")
  }

  private fun isChoiceContainer(fieldName: String, descriptor: SerialDescriptor): Boolean {
    if (descriptor.kind != StructureKind.CLASS || descriptor.elementsCount == 0) {
      return false
    }
    return (0 until descriptor.elementsCount).all { childIndex ->
      val childName = descriptor.getElementName(childIndex)
      childName.startsWith(fieldName) || childName.startsWith("_$fieldName")
    }
  }

  private fun resolveChoiceChildName(
    fieldName: String,
    descriptor: SerialDescriptor,
    rawValues: List<Any>,
  ): String {
    val rawValue =
      rawValues.firstOrNull() ?: error("Cannot resolve choice for '$fieldName' without a value.")
    val suffix = choiceTypeSuffix(rawValue)
    val candidate = "$fieldName$suffix"
    require(descriptor.getElementIndex(candidate) != CompositeDecoder.UNKNOWN_NAME) {
      "Choice field '$fieldName' does not support value type '$suffix'."
    }
    return candidate
  }

  private fun resolveFlatChoiceChildName(
    fieldName: String,
    candidateNames: List<String>,
    rawValues: List<Any>,
  ): String {
    val rawValue =
      rawValues.firstOrNull() ?: error("Cannot resolve choice for '$fieldName' without a value.")
    val suffix = choiceTypeSuffix(rawValue)
    val candidate = "$fieldName$suffix"
    require(candidate in candidateNames) {
      "Choice field '$fieldName' does not support value type '$suffix'."
    }
    return candidate
  }

  private fun choiceTypeSuffix(rawValue: Any): String =
    when (rawValue) {
      is Boolean,
      is dev.ohs.fhir.model.r4.Boolean -> "Boolean"

      is Int,
      is Integer -> "Integer"

      is BigDecimal,
      is Decimal -> "Decimal"

      is Date,
      is FhirPathDate -> "Date"

      is DateTime,
      is FhirPathDateTime -> "DateTime"

      is Time,
      is FhirPathTime -> "Time"

      is FhirString -> "String"

      is Quantity,
      is FhirPathQuantity -> "Quantity"

      is CodeableConcept -> "CodeableConcept"

      is Coding -> "Coding"

      is Reference -> "Reference"

      is Period -> "Period"

      else -> rawValue::class.simpleName ?: error("Unsupported choice type ${rawValue::class}")
    }

  private fun looksLikeCodeableConcept(descriptor: SerialDescriptor): Boolean =
    descriptor.kind == StructureKind.CLASS &&
      descriptor.getElementIndex("coding") != CompositeDecoder.UNKNOWN_NAME

  private fun looksLikeReference(descriptor: SerialDescriptor): Boolean =
    descriptor.kind == StructureKind.CLASS &&
      descriptor.getElementIndex("reference") != CompositeDecoder.UNKNOWN_NAME

  private fun addProfileIfNeeded(
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

  private fun inferResourceType(
    definitionCanonical: String,
    scopePairs: List<QuestionnaireItemResponsePair>,
  ): String {
    val canonicalWithoutVersion = definitionCanonical.substringBefore("|")
    val coreCandidate = canonicalWithoutVersion.substringAfterLast("/")
    if (isSupportedResourceType(coreCandidate)) {
      return coreCandidate
    }

    scopePairs
      .asSequence()
      .mapNotNull { pair -> pair.questionnaireItem.definition?.value?.let(::parseDefinitionPath) }
      .firstOrNull { it.canonical == definitionCanonical }
      ?.let {
        return it.resourceType
      }

    scopePairs
      .asSequence()
      .flatMap { pair -> pair.children.asSequence() }
      .mapNotNull { pair -> pair.questionnaireItem.definition?.value?.let(::parseDefinitionPath) }
      .firstOrNull { it.canonical == definitionCanonical }
      ?.let {
        return it.resourceType
      }

    error("Unable to infer resource type from definition '$definitionCanonical'.")
  }

  private fun resourceDescriptor(resourceType: String): SerialDescriptor =
    DefinitionExtractResourceRegistry.descriptorFor(resourceType)
      ?: error(
        "Definition-based extraction could not resolve a descriptor for resource type: $resourceType."
      )

  private fun isSupportedResourceType(resourceType: String): Boolean =
    resourceType in DefinitionExtractResourceRegistry.supportedResourceTypes

  /**
   * Aligns Questionnaire items with their QuestionnaireResponse counterparts before extraction.
   *
   * The definition-based spec expects traversal to follow the Questionnaire structure from the root
   * through each item. Repeating groups are expanded here so each repetition can establish its own
   * extraction scope and collection/backbone anchor.
   */
  private fun alignQuestionnaireItemsWithResponseItems(
    questionnaireItems: List<Questionnaire.Item>,
    responseItems: List<QuestionnaireResponse.Item>,
  ): List<QuestionnaireItemResponsePair> {
    val responseItemsByLinkId = responseItems.groupBy { it.linkId.value.orEmpty() }
    return questionnaireItems.flatMap { questionnaireItem ->
      val itemLinkId = questionnaireItem.linkId.value.orEmpty()
      val matchingResponseItems = responseItemsByLinkId[itemLinkId].orEmpty()
      if (questionnaireItem.isRepeatedGroup()) {
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
            QuestionnaireItemResponsePair(
              questionnaireItem = questionnaireItem,
              responseItem = syntheticResponseItem,
              children =
                alignQuestionnaireItemsWithResponseItems(questionnaireItem.item, answer.item),
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
          QuestionnaireItemResponsePair(
            questionnaireItem = questionnaireItem,
            responseItem = responseItem,
            children =
              alignQuestionnaireItemsWithResponseItems(questionnaireItem.item, childResponseItems),
          )
        }
      }
    }
  }

  private fun hasResponseContent(item: QuestionnaireResponse.Item): Boolean {
    if (item.answer.any { it.value != null }) {
      return true
    }
    if (item.item.any(::hasResponseContent)) {
      return true
    }
    return item.answer.any { answer -> answer.item.any(::hasResponseContent) }
  }

  private fun Questionnaire.Item.isGroup(): Boolean =
    type.value == Questionnaire.QuestionnaireItemType.Group

  private fun Questionnaire.Item.isRepeatedGroup(): Boolean = isGroup() && repeats?.value == true

  private fun Questionnaire.Item.hasDefinitionExtractRecursively(): Boolean =
    extension.any { it.url == EXTENSION_DEFINITION_EXTRACT_URL } ||
      item.any { it.hasDefinitionExtractRecursively() }

  private val Questionnaire.definitionExtractExtensions: List<DefinitionExtractConfig>
    get() =
      extension.filter { it.url == EXTENSION_DEFINITION_EXTRACT_URL }.map(::parseDefinitionExtract)

  private val Questionnaire.Item.definitionExtractExtensions: List<DefinitionExtractConfig>
    get() =
      extension.filter { it.url == EXTENSION_DEFINITION_EXTRACT_URL }.map(::parseDefinitionExtract)

  private val Questionnaire.extractAllocateIdVariableNames: List<String>
    get() =
      extension
        .filter { it.url == EXTENSION_EXTRACT_ALLOCATE_ID_URL }
        .mapNotNull { it.value?.asString()?.value?.value }

  private val Questionnaire.Item.extractAllocateIdVariableNames: List<String>
    get() =
      extension
        .filter { it.url == EXTENSION_EXTRACT_ALLOCATE_ID_URL }
        .mapNotNull { it.value?.asString()?.value?.value }

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

  private fun parseDefinitionExtractValue(extension: Extension): DefinitionExtractValueConfig {
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

  private fun parseDefinitionPath(rawDefinition: String): DefinitionPath {
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

  private fun computeItemAnchorPath(
    questionnaireItem: Questionnaire.Item,
    fullPath: List<String>,
  ): List<String> =
    when {
      fullPath.isEmpty() -> emptyList()
      questionnaireItem.isGroup() -> fullPath
      fullPath.size == 1 -> emptyList()
      else -> fullPath.dropLast(1)
    }

  private fun computeValueAnchorPath(fullPath: List<String>): List<String> =
    if (fullPath.size <= 1) emptyList() else fullPath.dropLast(1)

  private fun generateAllocatedFullUrl(): String {
    val bytes = ByteArray(16)
    Random.nextBytes(bytes)
    bytes[6] = ((bytes[6].toInt() and 0x0F) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3F) or 0x80).toByte()
    val hex =
      bytes.joinToString(separator = "") { byte -> byte.toUByte().toString(16).padStart(2, '0') }
    return "urn:uuid:${hex.substring(0, 8)}-${hex.substring(8, 12)}-${
      hex.substring(
        12,
        16,
      )
    }-${hex.substring(16, 20)}-${hex.substring(20)}"
  }

  private fun formatFhirPathDateTime(value: FhirPathDateTime): String {
    val year = value.year.toString().padStart(4, '0')
    val month = value.month?.toString()?.padStart(2, '0')
    val day = value.day?.toString()?.padStart(2, '0')
    val hour = value.hour?.toString()?.padStart(2, '0')
    val minute = value.minute?.toString()?.padStart(2, '0')
    val second =
      value.second?.let {
        if (it.rem(1.0) == 0.0) {
          it.toInt().toString().padStart(2, '0')
        } else {
          it.toString().padStart(2, '0')
        }
      }
    return buildString {
      append(year)
      month?.let {
        append("-")
        append(it)
      }
      day?.let {
        append("-")
        append(it)
      }
      hour?.let {
        append("T")
        append(it)
      }
      minute?.let {
        append(":")
        append(it)
      }
      second?.let {
        append(":")
        append(it)
      }
      value.utcOffset?.let { append(it.toString()) }
    }
  }

  private fun formatFhirPathTime(value: FhirPathTime): String {
    val hour = value.hour.toString().padStart(2, '0')
    val minute = value.minute?.toString()?.padStart(2, '0')
    val second =
      value.second?.let {
        if (it.rem(1.0) == 0.0) {
          it.toInt().toString().padStart(2, '0')
        } else {
          it.toString().padStart(2, '0')
        }
      }
    return buildString {
      append(hour)
      minute?.let {
        append(":")
        append(it)
      }
      second?.let {
        append(":")
        append(it)
      }
    }
  }

  private fun JsonElement.asString(): String? =
    (this as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

  private fun List<String>.startsWithPath(prefix: List<String>): Boolean =
    size >= prefix.size && take(prefix.size) == prefix

  private const val CORE_STRUCTURE_DEFINITION_BASE = "http://hl7.org/fhir/StructureDefinition/"
}
