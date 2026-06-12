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

import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

// ********************************************************************************************** //
//                                                                                                //
// Extractor instruction models.                                                                  //
//                                                                                                //
// These types represent parsed SDC extraction directives, resolved questionnaire-response pairs, //
// and the metadata needed while building transaction bundle entries.                             //
//                                                                                                //
// ********************************************************************************************** //

internal data class DefinitionExtractConfig(
  val definition: String,
  val fullUrlExpression: String?,
  val ifNoneMatchExpression: String?,
  val ifModifiedSinceExpression: String?,
  val ifMatchExpression: String?,
  val ifNoneExistExpression: String?,
)

internal data class DefinitionExtractValueConfig(
  val definition: DefinitionPath,
  val expression: Expression?,
  val fixedValue: Extension.Value?,
)

internal data class DefinitionPath(
  val canonical: String,
  val resourceType: String,
  val pathSegments: List<String>,
)

internal data class DefinitionExtractedEntry(
  val fullUrl: String,
  val resourceJson: JsonObject,
  val requestMethod: Bundle.HTTPVerb,
  val requestUrl: String,
  val ifNoneMatch: String?,
  val ifModifiedSince: String?,
  val ifMatch: String?,
  val ifNoneExist: String?,
)

internal data class FieldInfo(
  val jsonName: String,
  val descriptor: SerialDescriptor,
  val isList: Boolean,
)

internal data class ChoiceCandidate(
  val jsonName: String,
  val suffix: String,
  val descriptor: SerialDescriptor,
)

internal data class ItemPair(
  val questionnaireItem: Questionnaire.Item,
  val responseItem: QuestionnaireResponse.Item,
  val children: List<ItemPair>,
)

internal data class AnchorContext(
  val path: List<kotlin.String>,
  val node: MutableJsonObject,
  val descriptor: SerialDescriptor,
)

// ********************************************************************************************** //
//                                                                                                //
// Mutable JSON tree models.                                                                      //
//                                                                                                //
// Definition-based extraction stages write into a lightweight mutable tree first so descriptor-  //
// driven path resolution can happen before the final typed resource is decoded.                  //
//                                                                                                //
// ********************************************************************************************** //

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
    values.forEach { (key, value) ->
      val jsonValue = value.toJsonElement()
      if (jsonValue != JsonNull) {
        put(key, jsonValue)
      }
    }
  }
}

internal class MutableJsonArray(val values: MutableList<MutableJsonValue> = mutableListOf()) :
  MutableJsonValue {
  override fun toJsonElement(): JsonElement =
    kotlinx.serialization.json.buildJsonArray {
      values.map { it.toJsonElement() }.forEach { add(it) }
    }
}

internal class MutableJsonLiteral(private val value: JsonElement) : MutableJsonValue {
  override fun toJsonElement(): JsonElement = value
}
