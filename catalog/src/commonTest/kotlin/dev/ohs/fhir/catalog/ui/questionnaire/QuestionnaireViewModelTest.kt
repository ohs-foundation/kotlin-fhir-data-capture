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
package dev.ohs.fhir.catalog.ui.questionnaire

import dev.ohs.fhir.datacapture.extraction.definition.DefinitionExtractionEngine
import kotlin.test.Test
import kotlin.test.assertTrue

class QuestionnaireViewModelTest {
  @Test
  fun simulatesDefinitionExtractionForCatalogQuestionnaires() {
    val viewModel = QuestionnaireViewModel()
    val questionnaireJson =
      """
      {
        "resourceType": "Questionnaire",
        "id": "definition-extraction-sample",
        "url": "http://example.org/fhir/Questionnaire/definition-extraction-sample",
        "status": "active",
        "item": [
          {
            "linkId": "patient",
            "type": "group",
            "extension": [
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract",
                "valueString": "http://hl7.org/fhir/StructureDefinition/Patient"
              }
            ],
            "item": [
              {
                "linkId": "name",
                "type": "string",
                "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.given"
              }
            ]
          }
        ]
      }
      """
        .trimIndent()

    val questionnaire = viewModel.parseQuestionnaire(questionnaireJson)
    val responseJson =
      """
      {
        "resourceType": "QuestionnaireResponse",
        "questionnaire": "http://example.org/fhir/Questionnaire/definition-extraction-sample",
        "status": "completed",
        "item": [
          {
            "linkId": "patient",
            "item": [
              {
                "linkId": "name",
                "answer": [
                  {
                    "valueString": "Ada"
                  }
                ]
              }
            ]
          }
        ]
      }
      """
        .trimIndent()
    val response = viewModel.parseQuestionnaireResponse(responseJson)

    val extractedBundle = viewModel.simulateDefinitionExtraction(questionnaire, response)

    assertTrue(DefinitionExtractionEngine.canExtract(questionnaire))
    assertTrue(extractedBundle.entry.isNotEmpty())
  }
}
