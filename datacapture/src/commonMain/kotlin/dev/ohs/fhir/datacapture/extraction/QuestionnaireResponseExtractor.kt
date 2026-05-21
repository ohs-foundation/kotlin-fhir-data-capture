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
import dev.ohs.fhir.datacapture.extensions.elementValue
import dev.ohs.fhir.datacapture.extensions.packRepeatedGroups
import dev.ohs.fhir.fhirpath.FhirPathEngine
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
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.HumanName
import dev.ohs.fhir.model.r4.Identifier
import dev.ohs.fhir.model.r4.Integer
import dev.ohs.fhir.model.r4.Meta
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Period
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.RelatedPerson
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Extracts a transaction [Bundle] from a completed [QuestionnaireResponse] using the SDC
 * definition-based extraction mechanism.
 *
 * The current implementation focuses on the end-to-end workflow described in
 * https://build.fhir.org/ig/HL7/sdc/en/extraction.html#definition-extract:
 * - `definitionExtract`
 * - `definitionExtractValue`
 * - `extractAllocateId`
 * - `Questionnaire.item.definition`
 *
 * The extractor currently targets core R4 resources whose serializers are registered below.
 */
public object QuestionnaireResponseExtractor {
  private val fhirJson = FhirR4Json()
  private val json = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  private val fhirPathEngine = FhirPathEngine.forR4()

  public fun canExtract(questionnaire: Questionnaire): Boolean =
    questionnaire.extension.any { it.url == EXTENSION_DEFINITION_EXTRACT_URL } ||
      questionnaire.item.any { it.hasDefinitionExtractRecursively() }

  public fun extract(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): Bundle {
    val packedResponse =
      questionnaireResponse.toBuilder().apply { packRepeatedGroups(questionnaire) }.build()
    val rootPairs = buildItemPairs(questionnaire.item, packedResponse.item)
    val rootAllocateIds =
      questionnaire.extractAllocateIdVariableNames.associateWith { generateAllocatedFullUrl() }
    val entries = mutableListOf<JsonObject>()

    questionnaire.definitionExtractExtensions.forEach { definitionExtract ->
      entries.add(
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
      outputEntries = entries,
    )

    require(entries.isNotEmpty()) {
      "No definition-based extraction instructions were found in the questionnaire."
    }

    val bundleJson = buildJsonObject {
      put("resourceType", JsonPrimitive("Bundle"))
      put("type", JsonPrimitive("transaction"))
      put("entry", JsonArray(entries))
    }

    return fhirJson.decodeFromString(bundleJson.toString()) as Bundle
  }

  private fun extractScope(
    definitionExtract: DefinitionExtractConfig,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    scopeBase: Any,
    scopeQuestionnaireItem: Questionnaire.Item?,
    scopePairs: List<ItemPair>,
    inheritedAllocateIds: Map<String, String>,
  ): JsonObject {
    val resourceType = inferResourceType(definitionExtract.definition, scopePairs)
    val rootDescriptor = resourceDescriptor(resourceType)
    val resourceNode = MutableJsonObject(rootDescriptor)
    val rootAnchor =
      AnchorContext(path = emptyList(), node = resourceNode, descriptor = rootDescriptor)
    val scopeCanonical = definitionExtract.definition

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
    val requestMethod = if (resourceId.isNullOrBlank()) "POST" else "PUT"

    val entryJson = buildJsonObject {
      val fullUrl =
        definitionExtract.fullUrlExpression
          ?.let {
            evaluateExpressionToString(
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
          ?: definitionExtract.fullUrlExpression?.let { null }
          ?: generateAllocatedFullUrl()
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
              evaluateExpressionToString(
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
              evaluateExpressionToString(
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
              evaluateExpressionToString(
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
              evaluateExpressionToString(
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

  private fun walkPairsForDefinitionExtracts(
    pairs: List<ItemPair>,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    inheritedAllocateIds: Map<String, String>,
    outputEntries: MutableList<JsonObject>,
  ) {
    pairs.forEach { pair ->
      val pairAllocateIds =
        inheritedAllocateIds +
          pair.questionnaireItem.extractAllocateIdVariableNames.associateWith {
            generateAllocatedFullUrl()
          }

      pair.questionnaireItem.definitionExtractExtensions.forEach { definitionExtract ->
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

  private fun processPairInScope(
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
        pair.questionnaireItem.extractAllocateIdVariableNames.associateWith {
          generateAllocatedFullUrl()
        }

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
          ensureAnchor(
            rootAnchor = rootAnchor,
            parentAnchor = parentAnchor,
            anchorPath = anchorPath,
          )
        }
      }

    if (!pair.questionnaireItem.isGroup() && definitionPath != null) {
      val answerValues = pair.responseItem.answer.mapNotNull { it.elementValue }
      if (answerValues.isNotEmpty()) {
        setPathValues(
          rootAnchor = rootAnchor,
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

  private fun applyDefinitionExtractValues(
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
                rootAnchor = rootAnchor,
                parentAnchor = rootAnchor,
                anchorPath = computeValueAnchorPath(config.definition.pathSegments),
              )
          }

        setPathValues(
          rootAnchor = rootAnchor,
          anchor = targetAnchor,
          fullPath = config.definition.pathSegments,
          rawValues = rawValues,
        )
      }
  }

  private fun evaluateExpression(
    expression: Expression,
    base: Any,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    questionnaireItem: Questionnaire.Item?,
    responseItem: QuestionnaireResponse.Item?,
    allocateIds: Map<String, String>,
  ): List<Any> =
    expression.expression
      ?.value
      ?.let { evaluateResourceExpressionFallback(it, questionnaireResponse) }
      ?.takeIf { it.isNotEmpty() }
      ?: try {
        fhirPathEngine
          .evaluateExpression(
            expression = expression.expression?.value ?: "",
            base = base,
            variables =
              buildMap {
                put("resource", questionnaireResponse)
                put("context", responseItem ?: base)
                put("questionnaire", questionnaire)
                questionnaireItem?.let { put("qItem", it) }
                putAll(allocateIds)
              },
          )
          .toList()
      } catch (throwable: Throwable) {
        Logger.e(
          "Error evaluating definition extract expression ${expression.expression?.value}",
          throwable,
        )
        emptyList()
      }

  private fun evaluateExpressionToString(
    expression: String,
    base: Any,
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    questionnaireItem: Questionnaire.Item?,
    responseItem: QuestionnaireResponse.Item?,
    allocateIds: Map<String, String>,
  ): String =
    evaluateExpression(
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
      )
      .firstOrNull()
      ?.let(::stringifyValue) ?: ""

  private fun evaluateResourceExpressionFallback(
    expression: String,
    questionnaireResponse: QuestionnaireResponse,
  ): List<Any> =
    when (expression.trim()) {
      "%resource.author" -> listOfNotNull(questionnaireResponse.author)

      "%resource.id" -> questionnaireResponse.id?.let(::listOf) ?: emptyList()

      "'QuestionnaireResponse/' + %resource.id" ->
        questionnaireResponse.id?.let { listOf("QuestionnaireResponse/$it") } ?: emptyList()

      "(%resource.authored | %resource.meta.lastUpdated | now()).first()" ->
        listOfNotNull(questionnaireResponse.authored)

      else -> emptyList()
    }

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

  private fun setPathValues(
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
        existingArray
          ?: MutableJsonArray(elementDescriptor).also { currentNode.values[leafName] = it }
      rawValues
        .map { encodeValueForField(it, elementDescriptor) }
        .forEach { targetArray.values.add(MutableJsonLiteral(it)) }
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
        rawValue is kotlin.String
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
          json.encodeToJsonElement(dev.ohs.fhir.model.r4.Quantity.serializer(), rawValue)
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

  private fun ensureAnchor(
    rootAnchor: AnchorContext,
    parentAnchor: AnchorContext,
    anchorPath: List<String>,
  ): AnchorContext {
    require(anchorPath.startsWithPath(parentAnchor.path)) {
      "Anchor path ${anchorPath.joinToString(".")} must extend parent anchor ${parentAnchor.path.joinToString(".")}"
    }

    var currentNode = parentAnchor.node
    var currentDescriptor = parentAnchor.descriptor

    anchorPath.drop(parentAnchor.path.size).forEach { segment ->
      val fieldInfo = findFieldInfo(currentDescriptor, segment)
      currentNode = ensureObjectChild(currentNode, fieldInfo, appendToList = true)
      currentDescriptor = currentNode.descriptor
    }

    return AnchorContext(anchorPath, currentNode, currentDescriptor)
  }

  private fun ensureObjectChild(
    currentNode: MutableJsonObject,
    fieldInfo: FieldInfo,
    appendToList: Boolean = false,
  ): MutableJsonObject {
    if (fieldInfo.isList) {
      val array =
        (currentNode.values[fieldInfo.jsonName] as? MutableJsonArray)
          ?: MutableJsonArray(fieldInfo.descriptor.getElementDescriptor(0)).also {
            currentNode.values[fieldInfo.jsonName] = it
          }
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
    val suffix =
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
    val candidate = "$fieldName$suffix"
    require(descriptor.getElementIndex(candidate) != CompositeDecoder.UNKNOWN_NAME) {
      "Choice field '$fieldName' does not support value type '$suffix'."
    }
    return candidate
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
    val coreCanonical = "$CORE_STRUCTURE_DEFINITION_PREFIX$resourceType"
    if (canonicalWithoutVersion == coreCanonical) {
      return
    }
    val metaNode =
      (resourceNode.values["meta"] as? MutableJsonObject)
        ?: MutableJsonObject(Meta.serializer().descriptor).also { resourceNode.values["meta"] = it }
    val profileDescriptor =
      metaNode.descriptor.getElementDescriptor(metaNode.descriptor.getElementIndex("profile"))
    val profiles =
      (metaNode.values["profile"] as? MutableJsonArray)
        ?: MutableJsonArray(profileDescriptor).also { metaNode.values["profile"] = it }
    profiles.values.add(MutableJsonLiteral(JsonPrimitive(definitionCanonical)))
  }

  private fun inferResourceType(definitionCanonical: String, scopePairs: List<ItemPair>): String {
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
    when (resourceType) {
      "Observation" -> Observation.serializer().descriptor

      "Patient" -> Patient.serializer().descriptor

      "RelatedPerson" -> RelatedPerson.serializer().descriptor

      else ->
        error(
          "Definition-based extraction currently supports Observation, Patient, and RelatedPerson resources. Unsupported resource type: $resourceType."
        )
    }

  private fun isSupportedResourceType(resourceType: String): Boolean =
    resourceType == "Observation" || resourceType == "Patient" || resourceType == "RelatedPerson"

  private fun buildItemPairs(
    questionnaireItems: List<Questionnaire.Item>,
    responseItems: List<QuestionnaireResponse.Item>,
  ): List<ItemPair> {
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

  private fun hasAnyContent(item: QuestionnaireResponse.Item): Boolean {
    if (item.answer.any { it.value != null }) {
      return true
    }
    if (item.item.any(::hasAnyContent)) {
      return true
    }
    return item.answer.any { answer -> answer.item.any(::hasAnyContent) }
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
    return "urn:uuid:${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
  }

  private fun stringifyValue(value: Any): String =
    when (value) {
      is Boolean -> value.toString()
      is Int -> value.toString()
      is Long -> value.toString()
      is BigDecimal -> value.toString()
      is FhirString -> value.value.orEmpty()
      is dev.ohs.fhir.model.r4.Boolean -> value.value?.toString().orEmpty()
      is Integer -> value.value?.toString().orEmpty()
      is Decimal -> value.value?.toString().orEmpty()
      is Date -> value.value?.toString().orEmpty()
      is DateTime -> value.value?.toString().orEmpty()
      is Time -> value.value?.toString().orEmpty()
      is Uri -> value.value.orEmpty()
      is Canonical -> value.value.orEmpty()
      is Code -> value.value.orEmpty()
      is Coding -> value.code?.value ?: value.display?.value.orEmpty()
      is Reference -> value.reference?.value.orEmpty()
      is FhirPathDate -> value.toString()
      is FhirPathDateTime -> formatFhirPathDateTime(value)
      is FhirPathTime -> formatFhirPathTime(value)
      is FhirPathQuantity -> listOfNotNull(value.value?.toString(), value.unit).joinToString(" ")
      else -> value.toString()
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

  private data class DefinitionExtractConfig(
    val definition: String,
    val fullUrlExpression: String?,
    val ifNoneMatchExpression: String?,
    val ifModifiedSinceExpression: String?,
    val ifMatchExpression: String?,
    val ifNoneExistExpression: String?,
  )

  private data class DefinitionExtractValueConfig(
    val definition: DefinitionPath,
    val expression: Expression?,
    val fixedValue: Extension.Value?,
  )

  private data class DefinitionPath(
    val canonical: String,
    val resourceType: String,
    val pathSegments: List<String>,
  )

  private data class FieldInfo(
    val jsonName: String,
    val descriptor: SerialDescriptor,
    val isList: Boolean,
  )

  private data class ItemPair(
    val questionnaireItem: Questionnaire.Item,
    val responseItem: QuestionnaireResponse.Item,
    val children: List<ItemPair>,
  )

  private data class AnchorContext(
    val path: List<String>,
    val node: MutableJsonObject,
    val descriptor: SerialDescriptor,
  )

  private sealed interface MutableJsonValue {
    fun toJsonElement(): JsonElement
  }

  private class MutableJsonObject(
    val descriptor: SerialDescriptor,
    val values: MutableMap<String, MutableJsonValue> = mutableMapOf(),
  ) : MutableJsonValue {
    override fun toJsonElement(): JsonElement = toJsonObject()

    fun toJsonObject(resourceType: String? = null): JsonObject = buildJsonObject {
      resourceType?.let { put("resourceType", JsonPrimitive(it)) }
      values.forEach { (key, value) ->
        val jsonValue = value.toJsonElement()
        if (jsonValue != JsonNull) {
          put(key, jsonValue)
        }
      }
    }
  }

  private class MutableJsonArray(
    val descriptor: SerialDescriptor,
    val values: MutableList<MutableJsonValue> = mutableListOf(),
  ) : MutableJsonValue {
    override fun toJsonElement(): JsonElement = buildJsonArray {
      values.map { it.toJsonElement() }.forEach { add(it) }
    }
  }

  private class MutableJsonLiteral(private val value: JsonElement) : MutableJsonValue {
    override fun toJsonElement(): JsonElement = value
  }

  private const val CORE_STRUCTURE_DEFINITION_PREFIX = "http://hl7.org/fhir/StructureDefinition/"
  private const val EXTENSION_DEFINITION_EXTRACT_URL =
    "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract"
  private const val EXTENSION_DEFINITION_EXTRACT_VALUE_URL =
    "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
  private const val EXTENSION_EXTRACT_ALLOCATE_ID_URL =
    "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId"
}
