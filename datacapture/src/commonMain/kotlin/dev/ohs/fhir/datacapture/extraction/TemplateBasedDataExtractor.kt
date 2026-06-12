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

import dev.ohs.fhir.datacapture.extensions.findContainedResource
import dev.ohs.fhir.datacapture.extensions.templateExtractExtensions
import dev.ohs.fhir.datacapture.extraction.template.TemplateExtractDefinition
import dev.ohs.fhir.datacapture.extraction.template.TemplateExtractionEngine
import dev.ohs.fhir.datacapture.extraction.template.TemplateExtractionResult
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse

/**
 * Template-based extraction is the Structured Data Capture (SDC) mechanism for deriving one
 * transaction Bundle from a completed [QuestionnaireResponse] by cloning contained resource
 * templates and replacing their templated values with data selected from the response:
 * https://build.fhir.org/ig/HL7/sdc/en/extraction.html
 * https://build.fhir.org/ig/HL7/sdc/en/StructureDefinition-sdc-questionnaire-templateExtract.html
 *
 * This implementation supports the `sdc-questionnaire-templateExtract`,
 * `sdc-questionnaire-templateExtractContext`, `sdc-questionnaire-templateExtractValue`, and
 * `sdc-questionnaire-extractAllocateId` extensions defined by SDC. The extractor is
 * platform-independent and lives in `commonMain`, so callers can use it from Android, iOS, JVM, JS,
 * or Wasm after obtaining a completed questionnaire response from the data capture workflow.
 */
object TemplateBasedDataExtractor {
  /** Returns `true` when the questionnaire declares at least one template extraction definition. */
  public fun canExtract(questionnaire: Questionnaire): Boolean =
    questionnaire.templateExtractExtensions.isNotEmpty() ||
      questionnaire.item.any { item -> item.hasTemplateExtractExtensionRecursively() }

  /**
   * Runs one template extraction pass for the provided questionnaire/response pair.
   *
   * @throws IllegalArgumentException if the questionnaire does not declare template extraction, if
   *   any declared template reference cannot be resolved to a contained resource, or if the
   *   questionnaire response points to a different questionnaire URL.
   */
  fun extract(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): TemplateExtractionResult {
    require(canExtract(questionnaire)) {
      "Template-based extraction requires sdc-questionnaire-templateExtract on the questionnaire or one of its items."
    }

    val questionnaireReference = questionnaireResponse.questionnaire?.value
    require(questionnaireReference == null || questionnaireReference == questionnaire.url?.value) {
      "Mismatching Questionnaire ${questionnaire.url?.value} and QuestionnaireResponse (for Questionnaire $questionnaireReference)."
    }

    val missingTemplateReferences = questionnaire.missingTemplateReferences()
    require(missingTemplateReferences.isEmpty()) {
      "Missing contained template resource(s): ${missingTemplateReferences.joinToString()}. Each sdc-questionnaire-templateExtract reference must resolve before extraction starts."
    }

    return TemplateExtractionEngine(questionnaire, questionnaireResponse).extract()
  }
}

/** Returns `true` when this questionnaire item subtree declares template extraction anywhere. */
private fun Questionnaire.Item.hasTemplateExtractExtensionRecursively(): Boolean =
  templateExtractExtensions.isNotEmpty() ||
    item.any { child -> child.hasTemplateExtractExtensionRecursively() }

/** Collects unresolved contained resource references declared by template extraction extensions. */
private fun Questionnaire.missingTemplateReferences(): List<String> =
  allTemplateExtractDefinitions()
    .map { it.templateReference }
    .distinct()
    .filter { findContainedResource(it) == null }

/** Flattens questionnaire-level and item-level template extraction declarations into one list. */
private fun Questionnaire.allTemplateExtractDefinitions(): List<TemplateExtractDefinition> =
  buildList {
    addAll(templateExtractExtensions)
    item.forEach { questionnaireItem -> addAll(questionnaireItem.allTemplateExtractDefinitions()) }
  }

/** Recursively gathers template extraction declarations for one questionnaire item subtree. */
private fun Questionnaire.Item.allTemplateExtractDefinitions(): List<TemplateExtractDefinition> =
  buildList {
    addAll(templateExtractExtensions)
    item.forEach { child -> addAll(child.allTemplateExtractDefinitions()) }
  }
