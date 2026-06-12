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

import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin_fhir_data_capture.datacapture.generated.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefinitionBasedDataExtractorTest {

  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun canExtract_shouldReturnFalse_whenQuestionnaireHasNoDefinitionExtractInstructions() = runTest {
    val questionnaire =
      json.decodeFromString(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "item": [
            {
              "linkId": "name",
              "text": "Name",
              "type": "string"
            }
          ]
        }
        """
          .trimIndent()
      ) as Questionnaire

    assertFalse(DefinitionBasedDataExtractor.canExtract(questionnaire))
  }

  @Test
  fun extract_shouldRequireMatchingQuestionnaireCanonical() = runTest {
    val questionnaire = loadQuestionnaire("behavior_definition_extraction.json")
    val questionnaireResponse =
      json.decodeFromString(
        Res.readBytes("files/behavior_definition_extraction_response.json")
          .decodeToString()
          .replace(
            "http://example.org/fhir/Questionnaire/behavior-definition-extraction",
            "http://example.org/fhir/Questionnaire/other",
          )
      ) as QuestionnaireResponse

    assertFailsWith<IllegalArgumentException> {
      DefinitionBasedDataExtractor.extract(questionnaire, questionnaireResponse)
    }
  }

  @Test
  fun extract_shouldBuildTransactionBundleFromDefinitionBasedQuestionnaire() = runTest {
    val questionnaire = loadQuestionnaire("behavior_definition_extraction.json")
    val questionnaireResponse =
      loadQuestionnaireResponse("behavior_definition_extraction_response.json")

    assertTrue(DefinitionBasedDataExtractor.canExtract(questionnaire))

    val bundle = DefinitionBasedDataExtractor.extract(questionnaire, questionnaireResponse)
    val bundleJson = json.parseToJsonElement(json.encodeToString(bundle)).jsonObject
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
      "+254700123456",
      patientResource.requireArray("telecom").single().jsonObject.requireString("value"),
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

  @Test
  fun extract_shouldRespectResourceBoundariesInOfficialComplexDefinitionQuestionnaire() = runTest {
    val questionnaire = loadQuestionnaire("behavior_definition_extraction_complex_defn3.json")
    val questionnaireResponse =
      loadQuestionnaireResponse("behavior_definition_extraction_complex_defn3_response.json")

    assertTrue(DefinitionBasedDataExtractor.canExtract(questionnaire))

    val bundle = DefinitionBasedDataExtractor.extract(questionnaire, questionnaireResponse)
    val bundleJson = json.parseToJsonElement(json.encodeToString(bundle)).jsonObject
    val entries = bundleJson.requireArray("entry")

    assertEquals("transaction", bundleJson.requireString("type"))
    assertEquals(5, entries.size)

    val patientEntry = entries.singleByResourceType("Patient")
    val patientResource = patientEntry.requireObject("resource")
    val patientFullUrl = patientEntry.requireString("fullUrl")
    val patientNames = patientResource.requireArray("name")

    assertTrue(patientFullUrl.startsWith("urn:uuid:"))
    assertEquals(2, patientNames.size)
    assertEquals("Amina Zawadi Odhiambo", patientNames[0].jsonObject.requireString("text"))
    assertEquals(
      listOf("Amina", "Zawadi"),
      patientNames[0].jsonObject.requireArray("given").map { it.jsonPrimitive.content },
    )
    assertEquals("Odhiambo", patientNames[0].jsonObject.requireString("family"))
    assertEquals("Amy Akinyi Atieno", patientNames[1].jsonObject.requireString("text"))
    assertEquals(
      listOf("Amy", "Akinyi"),
      patientNames[1].jsonObject.requireArray("given").map { it.jsonPrimitive.content },
    )
    assertEquals("Atieno", patientNames[1].jsonObject.requireString("family"))
    assertEquals("female", patientResource.requireString("gender"))
    assertEquals("1992-04-16", patientResource.requireString("birthDate"))
    assertEquals(
      "National Identifier (IHI)",
      patientResource
        .requireArray("identifier")
        .single()
        .jsonObject
        .requireObject("type")
        .requireString("text"),
    )
    assertEquals(
      "http://example.org/nhio",
      patientResource.requireArray("identifier").single().jsonObject.requireString("system"),
    )
    assertEquals(
      "8003608833357361",
      patientResource.requireArray("identifier").single().jsonObject.requireString("value"),
    )
    assertFalse(patientResource.containsKey("telecom"))

    val relatedPersonEntries = entries.filterByResourceType("RelatedPerson")
    assertEquals(2, relatedPersonEntries.size)
    assertEquals(
      listOf("Otieno Odhiambo", "Mary Auma"),
      relatedPersonEntries.map {
        it.requireObject("resource").requireArray("name").single().jsonObject.requireString("text")
      },
    )
    relatedPersonEntries.forEach { entry ->
      val resource = entry.requireObject("resource")
      assertEquals(patientFullUrl, resource.requireObject("patient").requireString("reference"))
      assertFalse(resource.containsKey("telecom"))
    }

    val heightObservation = entries.singleObservationByCode("8302-2")
    val heightResource = heightObservation.requireObject("resource")
    assertEquals("final", heightResource.requireString("status"))
    assertEquals(
      "vital-signs",
      heightResource
        .requireArray("category")
        .single()
        .jsonObject
        .requireArray("coding")
        .single()
        .jsonObject
        .requireString("code"),
    )
    assertEquals(patientFullUrl, heightResource.requireObject("subject").requireString("reference"))
    assertEquals("1.68", heightResource.requireObject("valueQuantity").requireString("value"))
    assertEquals("m", heightResource.requireObject("valueQuantity").requireString("unit"))

    val weightObservation = entries.singleObservationByCode("29463-7")
    val weightResource = weightObservation.requireObject("resource")
    assertEquals("72.4", weightResource.requireObject("valueQuantity").requireString("value"))
    assertEquals("kg", weightResource.requireObject("valueQuantity").requireString("unit"))

    assertEquals(
      listOf("8302-2", "29463-7"),
      entries.filterByResourceType("Observation").map {
        it
          .requireObject("resource")
          .requireObject("code")
          .requireArray("coding")
          .single()
          .jsonObject
          .requireString("code")
      },
    )

    listOf(heightResource, weightResource).forEach { resource ->
      assertEquals(patientFullUrl, resource.requireObject("subject").requireString("reference"))
      assertEquals(
        "Practitioner/demo-author",
        resource.requireArray("performer").single().jsonObject.requireString("reference"),
      )
      assertEquals(
        "QuestionnaireResponse/extract-complex-defn3-response",
        resource.requireArray("derivedFrom").single().jsonObject.requireString("reference"),
      )
      assertTrue(resource.requireString("effectiveDateTime").startsWith("2026-06-09T08:15:00"))
      assertTrue(resource.requireString("issued").startsWith("2026-06-09T08:15:00"))
    }
  }

  @Test
  fun extract_shouldResolveUnslicedBooleanChoiceFields() = runTest {
    val bundleJson =
      extractBundleJson(
        questionnaireJson =
          """
          {
            "resourceType": "Questionnaire",
            "url": "http://example.org/fhir/Questionnaire/extract-observation-boolean",
            "status": "active",
            "item": [
              {
                "linkId": "obs",
                "text": "Observation",
                "type": "group",
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract",
                    "extension": [
                      {
                        "url": "definition",
                        "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Observation"
                      }
                    ]
                  },
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                    "extension": [
                      {
                        "url": "definition",
                        "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.status"
                      },
                      {
                        "url": "fixed-value",
                        "valueCode": "final"
                      }
                    ]
                  },
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                    "extension": [
                      {
                        "url": "definition",
                        "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.code.coding"
                      },
                      {
                        "url": "fixed-value",
                        "valueCoding": {
                          "system": "http://example.org/test-codes",
                          "code": "sigmoidoscopy-complication"
                        }
                      }
                    ]
                  }
                ],
                "item": [
                  {
                    "linkId": "complication",
                    "definition": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.value[x]",
                    "text": "Complication",
                    "type": "boolean"
                  }
                ]
              }
            ]
          }
          """
            .trimIndent(),
        questionnaireResponseJson =
          """
          {
            "resourceType": "QuestionnaireResponse",
            "questionnaire": "http://example.org/fhir/Questionnaire/extract-observation-boolean",
            "status": "completed",
            "item": [
              {
                "linkId": "obs",
                "item": [
                  {
                    "linkId": "complication",
                    "answer": [
                      {
                        "valueBoolean": true
                      }
                    ]
                  }
                ]
              }
            ]
          }
          """
            .trimIndent(),
      )

    val entries = bundleJson.requireArray("entry")
    val observation = entries.singleByResourceType("Observation").requireObject("resource")

    assertEquals("true", observation.requireString("valueBoolean"))
    assertFalse(observation.containsKey("valueString"))
    assertFalse(observation.containsKey("valueCodeableConcept"))
  }

  @Test
  fun extract_shouldResolveCodingAnswersIntoCodeableConceptChoices() = runTest {
    val bundleJson =
      extractBundleJson(
        questionnaireJson =
          """
          {
            "resourceType": "Questionnaire",
            "url": "http://example.org/fhir/Questionnaire/extract-observation-choice",
            "status": "active",
            "item": [
              {
                "linkId": "obs",
                "text": "Observation",
                "type": "group",
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract",
                    "extension": [
                      {
                        "url": "definition",
                        "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Observation"
                      }
                    ]
                  },
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                    "extension": [
                      {
                        "url": "definition",
                        "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.status"
                      },
                      {
                        "url": "fixed-value",
                        "valueCode": "final"
                      }
                    ]
                  },
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                    "extension": [
                      {
                        "url": "definition",
                        "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.code.coding"
                      },
                      {
                        "url": "fixed-value",
                        "valueCoding": {
                          "system": "http://example.org/test-codes",
                          "code": "screening-result"
                        }
                      }
                    ]
                  }
                ],
                "item": [
                  {
                    "linkId": "result",
                    "definition": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.value[x]",
                    "text": "Result",
                    "type": "choice",
                    "answerOption": [
                      {
                        "valueCoding": {
                          "system": "http://example.org/result-codes",
                          "code": "positive",
                          "display": "Positive"
                        }
                      }
                    ]
                  }
                ]
              }
            ]
          }
          """
            .trimIndent(),
        questionnaireResponseJson =
          """
          {
            "resourceType": "QuestionnaireResponse",
            "questionnaire": "http://example.org/fhir/Questionnaire/extract-observation-choice",
            "status": "completed",
            "item": [
              {
                "linkId": "obs",
                "item": [
                  {
                    "linkId": "result",
                    "answer": [
                      {
                        "valueCoding": {
                          "system": "http://example.org/result-codes",
                          "code": "positive",
                          "display": "Positive"
                        }
                      }
                    ]
                  }
                ]
              }
            ]
          }
          """
            .trimIndent(),
      )

    val entries = bundleJson.requireArray("entry")
    val observation = entries.singleByResourceType("Observation").requireObject("resource")
    val valueCoding =
      observation.requireObject("valueCodeableConcept").requireArray("coding").single().jsonObject

    assertEquals("positive", valueCoding.requireString("code"))
    assertEquals("http://example.org/result-codes", valueCoding.requireString("system"))
    assertFalse(observation.containsKey("valueCoding"))
    assertFalse(observation.containsKey("valueString"))
  }

  private fun extractBundleJson(
    questionnaireJson: String,
    questionnaireResponseJson: String,
  ): JsonObject {
    val questionnaire = json.decodeFromString(questionnaireJson) as Questionnaire
    val questionnaireResponse =
      json.decodeFromString(questionnaireResponseJson) as QuestionnaireResponse
    val bundle = DefinitionBasedDataExtractor.extract(questionnaire, questionnaireResponse)
    return json.parseToJsonElement(json.encodeToString(bundle)).jsonObject
  }

  private suspend fun loadQuestionnaire(fileName: String): Questionnaire =
    json.decodeFromString(Res.readBytes("files/$fileName").decodeToString()) as Questionnaire

  private suspend fun loadQuestionnaireResponse(fileName: String): QuestionnaireResponse =
    json.decodeFromString(Res.readBytes("files/$fileName").decodeToString())
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

  private fun JsonArray.singleObservationByCode(code: String): JsonObject =
    filterByResourceType("Observation").single {
      it
        .requireObject("resource")
        .requireObject("code")
        .requireArray("coding")
        .single()
        .jsonObject
        .requireString("code") == code
    }
}
