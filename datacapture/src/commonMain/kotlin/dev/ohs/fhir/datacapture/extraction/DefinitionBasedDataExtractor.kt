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

import dev.ohs.fhir.datacapture.extensions.definitionExtractExtensions
import dev.ohs.fhir.datacapture.extensions.hasDefinitionExtractRecursively
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse

/**
 * Extracts a transaction [Bundle] from a [QuestionnaireResponse] using the HL7 SDC definition-based
 * extraction mechanism.
 *
 * This extractor is intentionally single-purpose and separate from any template-based or
 * StructureMap-based extraction implementation.
 */
object DefinitionBasedDataExtractor {
  fun canExtract(questionnaire: Questionnaire): Boolean =
    questionnaire.definitionExtractExtensions.isNotEmpty() ||
      questionnaire.item.any { it.hasDefinitionExtractRecursively() }

  fun extract(questionnaire: Questionnaire, questionnaireResponse: QuestionnaireResponse): Bundle {
    require(canExtract(questionnaire)) {
      "No definition-based extraction instructions were found in the questionnaire."
    }
    requireMatchingQuestionnaire(questionnaire, questionnaireResponse)
    return DefinitionBasedExtractorEngine.extract(questionnaire, questionnaireResponse)
  }

  internal fun requireMatchingQuestionnaire(
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
