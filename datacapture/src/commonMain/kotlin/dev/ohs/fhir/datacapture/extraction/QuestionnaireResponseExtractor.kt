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
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse

/**
 * Chooses the extraction strategy for a questionnaire/response pair.
 *
 * The selector follows the SDC extraction extensions:
 * - questionnaires with `templateExtract`/`templateExtractBundle` metadata use template extraction
 * - otherwise questionnaires with definition-based extraction metadata use definition extraction
 * - otherwise extraction is skipped by [canExtract]
 */
public object QuestionnaireResponseDataExtractor {
  public fun canExtract(questionnaire: Questionnaire): Boolean =
    selectExtractionMode(questionnaire) != ExtractionMode.NONE

  public fun extract(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): Bundle =
    when (selectExtractionMode(questionnaire)) {
      ExtractionMode.TEMPLATE ->
        TemplateQuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)

      ExtractionMode.DEFINITION ->
        DefinitionQuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)

      ExtractionMode.NONE -> error("No extraction instructions were found in the questionnaire.")
    }

  private fun selectExtractionMode(questionnaire: Questionnaire): ExtractionMode =
    when {
      TemplateQuestionnaireResponseExtractor.canExtract(questionnaire) -> ExtractionMode.TEMPLATE

      DefinitionQuestionnaireResponseExtractor.canExtract(questionnaire) ->
        ExtractionMode.DEFINITION

      else -> ExtractionMode.NONE
    }
}

private enum class DataExtractionMethod {
  TEMPLATE_BASED
  DEFINITION_BASED
}
