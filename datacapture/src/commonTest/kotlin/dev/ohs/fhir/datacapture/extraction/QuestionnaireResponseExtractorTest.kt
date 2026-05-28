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

import dev.ohs.fhir.datacapture.extensions.EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL
import dev.ohs.fhir.datacapture.extensions.EXTENSION_TEMPLATE_EXTRACT_VALUE_URL
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin_fhir_data_capture.datacapture.generated.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class QuestionnaireResponseExtractorTest {
  private val fhirJson = FhirR4Json()
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun extract_singleResourceTemplate_returnsTransactionBundleWithCleanPatient() = runTest {
    val questionnaire =
      fhirJson.decodeFromString(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "contained": [
            {
              "resourceType": "Patient",
              "id": "patientTemplate",
              "name": [
                {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractContext",
                      "valueString": "item.where(linkId='name')"
                    }
                  ],
                  "_family": {
                    "extension": [
                      {
                        "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                        "valueString": "item.where(linkId='family').answer.value.first()"
                      }
                    ]
                  },
                  "_given": [
                    {
                      "extension": [
                        {
                          "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                          "valueString": "item.where(linkId='given').answer.value.first()"
                        }
                      ]
                    }
                  ]
                }
              ],
              "_active": {
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                    "valueString": "item.where(linkId='active').answer.value.first()"
                  }
                ]
              }
            }
          ],
          "extension": [
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract",
              "extension": [
                {
                  "url": "template",
                  "valueReference": {
                    "reference": "#patientTemplate"
                  }
                }
              ]
            }
          ],
          "item": [
            {
              "linkId": "name",
              "type": "group",
              "item": [
                {
                  "linkId": "given",
                  "type": "string"
                },
                {
                  "linkId": "family",
                  "type": "string"
                }
              ]
            },
            {
              "linkId": "active",
              "type": "boolean"
            }
          ]
        }
        """
          .trimIndent()
      ) as Questionnaire
    val questionnaireResponse =
      fhirJson.decodeFromString(
        """
        {
          "resourceType": "QuestionnaireResponse",
          "status": "completed",
          "item": [
            {
              "linkId": "name",
              "item": [
                {
                  "linkId": "given",
                  "answer": [
                    {
                      "valueString": "Jane"
                    }
                  ]
                },
                {
                  "linkId": "family",
                  "answer": [
                    {
                      "valueString": "Doe"
                    }
                  ]
                }
              ]
            },
            {
              "linkId": "active",
              "answer": [
                {
                  "valueBoolean": true
                }
              ]
            }
          ]
        }
        """
          .trimIndent()
      ) as QuestionnaireResponse

    val bundle = QuestionnaireResponseExtractor.extractTemplate(questionnaire, questionnaireResponse)
    val bundleJson = json.parseToJsonElement(fhirJson.encodeToString(bundle)).jsonObject
    val patientResource = bundleJson.requireArray("entry").singleByResourceType("Patient")
      .requireObject("resource")

    assertEquals(Bundle.BundleType.Transaction, bundle.type.value)
    assertFalse("id" in patientResource)
    assertEquals(true, patientResource.requireString("active").toBoolean())

    val serializedBundle = fhirJson.encodeToString(bundle)
    assertFalse(serializedBundle.contains(EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL))
    assertFalse(serializedBundle.contains(EXTENSION_TEMPLATE_EXTRACT_VALUE_URL))
  }

  @Test
  fun extract_prefersTemplateBasedExtractionWhenQuestionnaireHasTemplateMetadata() = runTest {
    val questionnaire =
      fhirJson.decodeFromString(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "contained": [
            {
              "resourceType": "Patient",
              "id": "patientTemplate",
              "name": [
                {
                  "_text": {
                    "extension": [
                      {
                        "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                        "valueString": "item.where(linkId='name').answer.value.first()"
                      }
                    ]
                  }
                }
              ]
            }
          ],
          "extension": [
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract",
              "extension": [
                {
                  "url": "template",
                  "valueReference": {
                    "reference": "#patientTemplate"
                  }
                }
              ]
            },
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract",
              "extension": []
            }
          ],
          "item": [
            {
              "linkId": "name",
              "type": "string"
            }
          ]
        }
        """
          .trimIndent()
      ) as Questionnaire
    val questionnaireResponse =
      fhirJson.decodeFromString(
        """
        {
          "resourceType": "QuestionnaireResponse",
          "status": "completed",
          "item": [
            {
              "linkId": "name",
              "answer": [
                {
                  "valueString": "Template Priority Patient"
                }
              ]
            }
          ]
        }
        """
          .trimIndent()
      ) as QuestionnaireResponse

    val bundle = QuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)
    val bundleJson = json.parseToJsonElement(fhirJson.encodeToString(bundle)).jsonObject

    assertEquals(Bundle.BundleType.Transaction, bundle.type.value)
    assertEquals(
      "Template Priority Patient",
      bundleJson
        .requireArray("entry")
        .singleByResourceType("Patient")
        .requireObject("resource")
        .requireArray("name")
        .single()
        .jsonObject
        .requireString("text"),
    )
  }

  @Test
  fun canExtract_containedResourcesWithoutExtractionMetadata_returnsFalse() = runTest {
    val questionnaire =
      fhirJson.decodeFromString(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "contained": [
            {
              "resourceType": "Patient",
              "id": "authoring-only-resource"
            }
          ]
        }
        """
          .trimIndent()
      ) as Questionnaire

    assertFalse(QuestionnaireResponseExtractor.canExtract(questionnaire))
    assertFalse(QuestionnaireResponseExtractor.canExtractTemplate(questionnaire))
  }

  @Test
  fun extract_definitionBasedQuestionnaireWithContainedResources_usesDefinitionExtraction() =
    runTest {
      val questionnaire =
        fhirJson.decodeFromString(
          """
          {
            "resourceType": "Questionnaire",
            "status": "active",
            "contained": [
              {
                "resourceType": "Patient",
                "id": "authoring-only-resource"
              }
            ],
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
                    "type": "string",
                    "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.text"
                  }
                ]
              }
            ]
          }
          """
            .trimIndent()
        ) as Questionnaire
      val questionnaireResponse =
        fhirJson.decodeFromString(
          """
          {
            "resourceType": "QuestionnaireResponse",
            "status": "completed",
            "item": [
              {
                "linkId": "patient",
                "item": [
                  {
                    "linkId": "name",
                    "answer": [
                      {
                        "valueString": "Amina Odhiambo"
                      }
                    ]
                  }
                ]
              }
            ]
          }
          """
            .trimIndent()
        ) as QuestionnaireResponse

      val bundle = QuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)
      val bundleJson = json.parseToJsonElement(fhirJson.encodeToString(bundle)).jsonObject

      assertEquals(
        "Amina Odhiambo",
        bundleJson
          .requireArray("entry")
          .singleByResourceType("Patient")
          .requireObject("resource")
          .requireArray("name")
          .single()
          .jsonObject
          .requireString("text"),
      )
    }

  @Test
  fun extractTemplate_questionnaireWithoutTemplateInstructions_throws() = runTest {
    val questionnaire =
      fhirJson.decodeFromString(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "contained": [
            {
              "resourceType": "Patient",
              "id": "authoring-only-resource"
            }
          ]
        }
        """
          .trimIndent()
      ) as Questionnaire
    val questionnaireResponse =
      fhirJson.decodeFromString(
        """
        {
          "resourceType": "QuestionnaireResponse",
          "status": "completed"
        }
        """
          .trimIndent()
      ) as QuestionnaireResponse

    val error =
      assertFailsWith<IllegalArgumentException> {
        QuestionnaireResponseExtractor.extractTemplate(questionnaire, questionnaireResponse)
      }

    assertEquals(
      "No template-based extraction instructions were found in the questionnaire.",
      error.message,
    )
  }

  @Test
  fun extractsTransactionBundleFromDefinitionBasedQuestionnaire() = runTest {
    val questionnaire = loadQuestionnaire("behavior_definition_extraction.json")
    val questionnaireResponse =
      loadQuestionnaireResponse("behavior_definition_extraction_response.json")

    assertTrue(QuestionnaireResponseExtractor.canExtract(questionnaire))

    val bundle = QuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)
    val bundleJson = json.parseToJsonElement(fhirJson.encodeToString(bundle)).jsonObject
    val entries = bundleJson.requireArray("entry")

    assertEquals("transaction", bundleJson.requireString("type"))
    assertEquals(4, entries.size)

    val patientEntry = entries.singleByResourceType("Patient")
    val patientResource = patientEntry.requireObject("resource")
    val patientFullUrl = patientEntry.requireString("fullUrl")

    assertTrue(patientFullUrl.startsWith("urn:uuid:"))
    assertEquals("POST", patientEntry.requireObject("request").requireString("method"))
    assertEquals(
      "Amina Odhiambo",
      patientResource.requireArray("name").single().jsonObject.requireString("text"),
    )
    assertEquals(
      "Amina",
      patientResource
        .requireArray("name")
        .single()
        .jsonObject
        .requireArray("given")
        .single()
        .jsonPrimitive
        .content,
    )
    assertEquals(
      "Odhiambo",
      patientResource.requireArray("name").single().jsonObject.requireString("family"),
    )
    assertEquals("female", patientResource.requireString("gender"))
    assertEquals("1992-04-16", patientResource.requireString("birthDate"))
    assertEquals(
      "National Identifier",
      patientResource
        .requireArray("identifier")
        .single()
        .jsonObject
        .requireObject("type")
        .requireString("text"),
    )
    assertEquals(
      "http://example.org/fhir/sid/national-id",
      patientResource.requireArray("identifier").single().jsonObject.requireString("system"),
    )
    assertEquals(
      "NH-12345",
      patientResource.requireArray("identifier").single().jsonObject.requireString("value"),
    )
    assertEquals(
      "phone",
      patientResource.requireArray("telecom").single().jsonObject.requireString("system"),
    )
    assertEquals(
      "mobile",
      patientResource.requireArray("telecom").single().jsonObject.requireString("use"),
    )

    val relatedPersonEntries = entries.filterByResourceType("RelatedPerson")
    assertEquals(2, relatedPersonEntries.size)
    relatedPersonEntries.forEach { entry ->
      val resource = entry.requireObject("resource")
      assertEquals(patientFullUrl, resource.requireObject("patient").requireString("reference"))
      assertEquals(
        "phone",
        resource.requireArray("telecom").single().jsonObject.requireString("system"),
      )
      assertEquals(
        "mobile",
        resource.requireArray("telecom").single().jsonObject.requireString("use"),
      )
    }

    val observationEntry = entries.singleByResourceType("Observation")
    val observationResource = observationEntry.requireObject("resource")

    assertEquals("final", observationResource.requireString("status"))
    assertEquals(
      "8302-2",
      observationResource
        .requireObject("code")
        .requireArray("coding")
        .single()
        .jsonObject
        .requireString("code"),
    )
    assertEquals(
      patientFullUrl,
      observationResource.requireObject("subject").requireString("reference"),
    )
    assertEquals("1.68", observationResource.requireObject("valueQuantity").requireString("value"))
    assertEquals("m", observationResource.requireObject("valueQuantity").requireString("unit"))
    assertEquals(
      "Practitioner/demo-author",
      observationResource.requireArray("performer").single().jsonObject.requireString("reference"),
    )
    assertEquals(
      "QuestionnaireResponse/behavior-definition-extraction-response",
      observationResource.requireArray("derivedFrom").single().jsonObject.requireString("reference"),
    )
    assertTrue(
      observationResource.requireString("effectiveDateTime").startsWith("2026-05-21T09:30:00")
    )
    assertTrue(observationResource.requireString("issued").startsWith("2026-05-21T09:30:00"))
  }

  private suspend fun loadQuestionnaire(fileName: String): Questionnaire =
    fhirJson.decodeFromString(Res.readBytes("files/$fileName").decodeToString()) as Questionnaire

  private suspend fun loadQuestionnaireResponse(fileName: String): QuestionnaireResponse =
    fhirJson.decodeFromString(Res.readBytes("files/$fileName").decodeToString())
      as QuestionnaireResponse

  private fun JsonObject.requireArray(name: String): JsonArray = getValue(name).jsonArray

  private fun JsonObject.requireObject(name: String): JsonObject = getValue(name).jsonObject

  private fun JsonObject.requireString(name: String): String = getValue(name).jsonPrimitive.content

  private fun JsonArray.singleByResourceType(resourceType: String): JsonObject =
    single { it.jsonObject.requireObject("resource").requireString("resourceType") == resourceType }
      .jsonObject

  private fun JsonArray.filterByResourceType(resourceType: String): List<JsonObject> =
    filter { it.jsonObject.requireObject("resource").requireString("resourceType") == resourceType }
      .map { it.jsonObject }
}
