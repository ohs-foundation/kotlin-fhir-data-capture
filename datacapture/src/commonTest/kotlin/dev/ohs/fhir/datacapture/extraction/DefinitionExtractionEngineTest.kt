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

import dev.ohs.fhir.datacapture.extraction.definition.DefinitionExtractionEngine
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefinitionExtractionEngineTest {
  private val json = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  @Test
  fun keepsFirstValueAndWarnsWhenSingularFieldReceivesMultipleAnswers() = runTest {
    val questionnaire =
      questionnaire(
        """
      {
        "resourceType": "Questionnaire",
        "url": "https://ohs.dev/fhir/Questionnaire/demographics",
        "status": "active",
        "item": [
          {
            "linkId": "patient",
            "type": "group",
            "extension": [
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract",
                "extension": [
                  {
                    "url": "definition",
                    "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Patient"
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "family",
                "type": "string",
                "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.family"
              },
              {
                "linkId": "gender",
                "type": "choice",
                "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.gender"
              },
              {
                "linkId": "birthDate",
                "type": "date",
                "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.birthDate"
              }
            ]
          }
        ]
      }
      """
      )
    val questionnaireResponse =
      questionnaireResponse(
        """
      {
        "resourceType": "QuestionnaireResponse",
        "questionnaire": "https://ohs.dev/fhir/Questionnaire/demographics",
        "status": "completed",
        "item": [
          {
            "linkId": "patient",
            "item": [
              {
                "linkId": "family",
                "answer": [
                  { "valueString": "Doe" }
                ]
              },
              {
                "linkId": "gender",
                "answer": [
                  {
                    "valueCoding": {
                      "system": "http://hl7.org/fhir/administrative-gender",
                      "code": "female"
                    }
                  }
                ]
              },
              {
                "linkId": "birthDate",
                "answer": [
                  { "valueDate": "1980-01-02" },
                  { "valueDate": "1990-05-05" }
                ]
              }
            ]
          }
        ]
      }
      """
      )

    val result =
      DefinitionExtractionEngine.extractByDefinition(
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
      )

    // A cardinality overflow on one singular field must not drop the whole resource.
    assertEquals(1, result.entry.size)

    val patientEntry = bundleEntryObjects(result).single()
    assertEquals("Patient", resourceType(patientEntry))

    val patientResource = resourceOf(patientEntry)

    // Sibling fields written before/after the overflowing one must still be present.
    assertEquals(
      "Doe",
      patientResource
        .getValue("name")
        .jsonArray
        .single()
        .jsonObject
        .getValue("family")
        .jsonPrimitive
        .content,
    )
    assertEquals("female", patientResource.getValue("gender").jsonPrimitive.content)

    // First value wins for the overflowing singular field; the second is dropped, not thrown.
    assertEquals("1980-01-02", patientResource.getValue("birthDate").jsonPrimitive.content)
  }

  @Test
  fun extractsEachRepeatedGroupInstanceAsSeparateListElement() = runTest {
    /**
     * A repeating group (Patient.name) must produce one HumanName array element per repetition,
     * with each repetition's child answers landing on that same element rather than being merged.
     */
    val questionnaire =
      questionnaire(
        """
      {
        "resourceType": "Questionnaire",
        "url": "https://ohs.dev/fhir/Questionnaire/patient-names",
        "status": "active",
        "item": [
          {
            "linkId": "patient",
            "type": "group",
            "extension": [
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract",
                "extension": [
                  {
                    "url": "definition",
                    "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Patient"
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "name",
                "type": "group",
                "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name",
                "repeats": true,
                "item": [
                  {
                    "linkId": "given",
                    "type": "string",
                    "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.given"
                  },
                  {
                    "linkId": "family",
                    "type": "string",
                    "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.family"
                  }
                ]
              }
            ]
          }
        ]
      }
      """
      )
    val questionnaireResponse =
      questionnaireResponse(
        """
      {
        "resourceType": "QuestionnaireResponse",
        "questionnaire": "https://ohs.dev/fhir/Questionnaire/patient-names",
        "status": "completed",
        "item": [
          {
            "linkId": "patient",
            "item": [
              {
                "linkId": "name",
                "item": [
                  {
                    "linkId": "given",
                    "answer": [
                      { "valueString": "Jane" }
                    ]
                  },
                  {
                    "linkId": "family",
                    "answer": [
                      { "valueString": "Doe" }
                    ]
                  }
                ]
              },
              {
                "linkId": "name",
                "item": [
                  {
                    "linkId": "given",
                    "answer": [
                      { "valueString": "Janie" }
                    ]
                  },
                  {
                    "linkId": "family",
                    "answer": [
                      { "valueString": "Smith" }
                    ]
                  }
                ]
              }
            ]
          }
        ]
      }
      """
      )

    val result =
      DefinitionExtractionEngine.extractByDefinition(
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
      )

    val patientEntry = bundleEntryObjects(result).single()
    assertEquals("Patient", resourceType(patientEntry))

    val names = resourceOf(patientEntry).getValue("name").jsonArray
    assertEquals(2, names.size)

    assertEquals(
      "Jane",
      names[0].jsonObject.getValue("given").jsonArray.single().jsonPrimitive.content,
    )
    assertEquals("Doe", names[0].jsonObject.getValue("family").jsonPrimitive.content)

    assertEquals(
      "Janie",
      names[1].jsonObject.getValue("given").jsonArray.single().jsonPrimitive.content,
    )
    assertEquals("Smith", names[1].jsonObject.getValue("family").jsonPrimitive.content)
  }

  private fun questionnaire(jsonString: String): Questionnaire =
    json.decodeFromString<Questionnaire>(jsonString.trimIndent())

  private fun questionnaireResponse(jsonString: String): QuestionnaireResponse =
    json.decodeFromString<QuestionnaireResponse>(jsonString.trimIndent())

  private fun bundleEntryObjects(result: Bundle): List<JsonObject> =
    json
      .parseToJsonElement(json.encodeToString(result))
      .jsonObject
      .getValue("entry")
      .jsonArray
      .map { entry -> entry.jsonObject }

  private fun resourceType(entry: JsonObject): String =
    resourceOf(entry).getValue("resourceType").jsonPrimitive.content

  private fun resourceOf(entry: JsonObject): JsonObject = entry.getValue("resource").jsonObject
}
