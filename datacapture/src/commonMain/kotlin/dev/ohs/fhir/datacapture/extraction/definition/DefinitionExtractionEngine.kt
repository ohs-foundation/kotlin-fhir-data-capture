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
import dev.ohs.fhir.datacapture.extensions.allocateIdVariableNames
import dev.ohs.fhir.datacapture.extensions.generateAllocatedFullUrl
import dev.ohs.fhir.datacapture.extensions.packRepeatedGroups
import dev.ohs.fhir.datacapture.fhirpath.FhirPathService
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
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
  fun canExtract(questionnaire: Questionnaire): Boolean =
    questionnaire.extension.any { it.url == EXTENSION_DEFINITION_EXTRACT_URL } ||
      questionnaire.item.any { it.hasDefinitionExtractRecursively() }

  fun extractByDefinition(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): Bundle =
    extractByDefinition(
      questionnaire = questionnaire,
      questionnaireResponse = questionnaireResponse,
      resolveProfileResourceType = null,
    )

  /**
   * Extracts resources using SDC definition-based rules with an optional custom profile resolver.
   *
   * Some `definitionExtract.definition` canonicals point at custom StructureDefinitions whose last
   * URL segment is the profile id rather than a core FHIR resource type. When local typed
   * `item.definition` or `definitionExtractValue.definition` hints are not present, callers can
   * provide [resolveProfileResourceType] to map that canonical to a supported base resource type
   * such as `Observation`.
   */
  fun extractByDefinition(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
    resolveProfileResourceType: ((String) -> String?)?,
  ): Bundle {
    requireMatchingQuestionnaire(questionnaire, questionnaireResponse)

    val packedResponse =
      questionnaireResponse.toBuilder().apply { packRepeatedGroups(questionnaire) }.build()
    val rootPairs =
      alignQuestionnaireItemsWithResponseItems(questionnaire.item, packedResponse.item)
    val rootAllocateIds =
      questionnaire.allocateIdVariableNames.associateWith { generateAllocatedFullUrl() }
    val entries = buildList {
      addAll(
        questionnaire.definitionExtractExtensions.mapNotNull { definitionExtract ->
          materializeValidEntryOrNull(
            scopeDescription = "Questionnaire definition '${definitionExtract.definition}'"
          ) {
            extractBundleEntryForDefinitionScope(
              definitionExtract = definitionExtract,
              questionnaire = questionnaire,
              questionnaireResponse = questionnaireResponse,
              scopeBase = packedResponse,
              scopeQuestionnaireItem = null,
              scopePairs = rootPairs,
              inheritedAllocateIds = rootAllocateIds,
              resolveProfileResourceType = resolveProfileResourceType,
            )
          }
        }
      )
      addAll(
        extractNestedDefinitionScopeEntries(
          pairs = rootPairs,
          questionnaire = questionnaire,
          questionnaireResponse = questionnaireResponse,
          inheritedAllocateIds = rootAllocateIds,
          resolveProfileResourceType = resolveProfileResourceType,
        )
      )
    }

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
}
