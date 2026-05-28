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
 * Entry point for QuestionnaireResponse extraction.
 *
 * Definition-based extraction remains available through the existing [extract] API, while
 * template-based extraction is exposed explicitly for callers that know which mechanism their
 * questionnaire uses.
 */
public object QuestionnaireResponseExtractor {
  public fun canExtractTemplate(questionnaire: Questionnaire): Boolean =
    TemplateQuestionnaireResponseExtractor.canExtract(questionnaire)

  public fun canExtract(questionnaire: Questionnaire): Boolean =
    selectExtractionMode(questionnaire) != ExtractionMode.NONE

  public fun extractTemplate(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): Bundle = TemplateQuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)

  public fun extract(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse,
  ): Bundle =
    when (selectExtractionMode(questionnaire)) {
      ExtractionMode.TEMPLATE -> extractTemplate(questionnaire, questionnaireResponse)
      ExtractionMode.DEFINITION ->
        DefinitionQuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)

      ExtractionMode.NONE -> error("No extraction instructions were found in the questionnaire.")
    }

  private fun selectExtractionMode(questionnaire: Questionnaire): ExtractionMode =
    when {
      canExtractTemplate(questionnaire) -> ExtractionMode.TEMPLATE

      DefinitionQuestionnaireResponseExtractor.canExtract(questionnaire) ->
        ExtractionMode.DEFINITION

      else -> ExtractionMode.NONE
    }
}

private enum class ExtractionMode {
  TEMPLATE,
  DEFINITION,
  NONE,
}
