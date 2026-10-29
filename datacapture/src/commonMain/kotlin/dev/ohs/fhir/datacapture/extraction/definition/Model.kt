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

import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Represents one `definitionExtract` directive that initiates extraction of a resource instance.
 *
 * In the SDC definition-extract specification, this directive declares the resource/profile being
 * created for the current scope and can also supply expressions for `Bundle.entry.fullUrl` and
 * `Bundle.entry.request` metadata.
 */
internal data class DefinitionExtractConfig(
  val definition: String,
  val fullUrlExpression: String?,
  val ifNoneMatchExpression: String?,
  val ifModifiedSinceExpression: String?,
  val ifMatchExpression: String?,
  val ifNoneExistExpression: String?,
)

/**
 * Represents one `definitionExtractValue` directive in the current extraction scope.
 *
 * Per the SDC definition-extract specification, these directives populate a property in the
 * extracted resource using either a fixed value or a calculated FHIRPath expression, provided the
 * directive's canonical definition matches the active `definitionExtract` scope.
 */
internal data class DefinitionExtractValueConfig(
  val definition: DefinitionPath,
  val expression: Expression?,
  val fixedValue: Extension.Value?,
)

/**
 * Parsed form of a `Questionnaire.item.definition` or `definitionExtractValue.definition`.
 *
 * The definition-extract specification treats the part before `#` as the scoped
 * StructureDefinition/profile canonical and the part after `#` as the element id/path that should
 * be populated in the extracted resource.
 */
internal data class DefinitionPath(
  val canonical: String,
  val resourceType: String,
  val pathSegments: List<String>,
)

/**
 * Describes how one extracted definition path segment maps to the generated FHIR JSON shape.
 *
 * This is not a spec artifact itself. It is the engine's lookup result for a path segment, telling
 * extraction which JSON field name to write, which serializer descriptor governs that field, and
 * whether the target node is repeating. In practice, it is what lets the engine take a spec path
 * such as `Patient.name.given` and safely write into the right Kotlin FHIR model-backed JSON slot.
 */
internal data class FieldInfo(
  val jsonName: String,
  val descriptor: SerialDescriptor,
  val isList: Boolean,
)

/**
 * Aligns one Questionnaire item with the corresponding QuestionnaireResponse item plus its
 * children.
 *
 * The definition-extract spec says traversal happens through the Questionnaire structure while
 * reading values from the QuestionnaireResponse. This pair is the engine's working representation
 * of that alignment, including any expanded child repetitions.
 */
internal data class QuestionnaireItemResponsePair(
  val questionnaireItem: Questionnaire.Item,
  val responseItem: QuestionnaireResponse.Item,
  val children: List<QuestionnaireItemResponsePair>,
)

/**
 * Tracks the current write location inside the resource being built for one extraction scope.
 *
 * As the definition-extract rules walk group items, repeated collections, and descendant
 * definitions, the engine needs to remember which partially built JSON node corresponds to the
 * current resource path anchor.
 */
internal data class AnchorContext(
  val path: List<String>,
  val node: MutableJsonObject,
  val descriptor: SerialDescriptor,
)

internal sealed interface MutableJsonValue {
  fun toJsonElement(): JsonElement
}

internal class MutableJsonObject(
  val descriptor: SerialDescriptor,
  val values: MutableMap<String, MutableJsonValue> = mutableMapOf(),
) : MutableJsonValue {
  override fun toJsonElement(): JsonElement = toJsonObject()

  fun toJsonObject(resourceType: String? = null): JsonObject = buildJsonObject {
    resourceType?.let { put("resourceType", JsonPrimitive(it)) }
    for ((key, value) in values) {
      val jsonValue = value.toJsonElement()
      if (jsonValue != JsonNull) {
        put(key, jsonValue)
      }
    }
  }
}

internal class MutableJsonArray(val values: MutableList<MutableJsonValue> = mutableListOf()) :
  MutableJsonValue {
  override fun toJsonElement(): JsonElement = buildJsonArray {
    for (value in values) {
      add(value.toJsonElement())
    }
  }
}

internal class MutableJsonLiteral(private val value: JsonElement) : MutableJsonValue {
  override fun toJsonElement(): JsonElement = value
}
