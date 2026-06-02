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
import dev.ohs.fhir.model.r4.Encounter
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.Observation
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Practitioner
import dev.ohs.fhir.model.r4.Provenance
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.RelatedPerson
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
  private val json = FhirR4Json()
  private val bundleJsonParser = Json { ignoreUnknownKeys = true }

  @Test
  fun extract_singleResourceTemplate_returnsTransactionBundleWithCleanPatient() = runTest {
    val bundle =
      extract(
        questionnaireJson =
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
            .trimIndent(),
        questionnaireResponseJson =
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
            .trimIndent(),
      )

    assertEquals(Bundle.BundleType.Transaction, bundle.type.value)

    val patient = bundle.resources().filterIsInstance<Patient>().single()
    assertNull(patient.id)
    assertEquals(true, patient.active?.value)

    val serializedBundle = json.encodeToString(bundle)
    assertFalse(serializedBundle.contains(EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL))
    assertFalse(serializedBundle.contains(EXTENSION_TEMPLATE_EXTRACT_VALUE_URL))
  }

  @Test
  fun extract_prefersTemplateBasedExtractionWhenQuestionnaireHasTemplateExtensions() = runTest {
    val bundle =
      extract(
        questionnaireJson =
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
            .trimIndent(),
        questionnaireResponseJson =
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
            .trimIndent(),
      )

    assertEquals(Bundle.BundleType.Transaction, bundle.type.value)
    assertEquals(
      "Template Priority Patient",
      bundle.resources().filterIsInstance<Patient>().single().name.single().text?.value,
    )
  }

  @Test
  fun canExtract_templateBasedQuestionnaireWithoutTemplateExtensions_returnsFalse() = runTest {
    val questionnaire =
      json.decodeFromString(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "contained": [
            {
              "resourceType": "Patient",
              "id": "patientTemplate"
            }
          ]
        }
        """
          .trimIndent()
      ) as Questionnaire

    assertFalse(QuestionnaireResponseExtractor.canExtract(questionnaire))
  }

  @Test
  fun canExtract_templateExtractBundleAtQuestionnaireRoot_returnsTrue() = runTest {
    val questionnaire =
      json.decodeFromString(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "extension": [
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractBundle",
              "valueReference": {
                "reference": "#bundleTemplate"
              }
            }
          ]
        }
        """
          .trimIndent()
      ) as Questionnaire

    assertTrue(QuestionnaireResponseExtractor.canExtract(questionnaire))
  }

  @Test
  fun canExtract_nestedItemTemplateExtractExtension_returnsTrue() = runTest {
    val questionnaire =
      json.decodeFromString(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "item": [
            {
              "linkId": "group",
              "type": "group",
              "item": [
                {
                  "linkId": "name",
                  "type": "group",
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
                  ]
                }
              ]
            }
          ]
        }
        """
          .trimIndent()
      ) as Questionnaire

    assertTrue(QuestionnaireResponseExtractor.canExtract(questionnaire))
  }

  @Test
  fun extract_templateWithoutTemplateExtensions_throws() = runTest {
    val questionnaire =
      json.decodeFromString(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active",
          "contained": [
            {
              "resourceType": "Patient",
              "id": "patientTemplate"
            }
          ]
        }
        """
          .trimIndent()
      ) as Questionnaire
    val questionnaireResponse =
      json.decodeFromString(
        """
        {
          "resourceType": "QuestionnaireResponse",
          "status": "completed"
        }
        """
          .trimIndent()
      ) as QuestionnaireResponse

    assertFailsWith<IllegalArgumentException> {
      TemplateQuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)
    }
  }

  @Test
  fun extract_definitionWithoutDefinitionExtractExtensions_throws() = runTest {
    val questionnaire =
      json.decodeFromString(
        """
        {
          "resourceType": "Questionnaire",
          "status": "active"
        }
        """
          .trimIndent()
      ) as Questionnaire
    val questionnaireResponse =
      json.decodeFromString(
        """
        {
          "resourceType": "QuestionnaireResponse",
          "status": "completed"
        }
        """
          .trimIndent()
      ) as QuestionnaireResponse

    assertFailsWith<IllegalArgumentException> {
      DefinitionQuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)
    }
  }

  @Test
  fun extract_definitionBasedQuestionnaireWithUnansweredItemExtract_returnsEmptyTransactionBundle() =
    runTest {
      val questionnaire =
        json.decodeFromString(
          """
          {
            "resourceType": "Questionnaire",
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
                ]
              }
            ]
          }
          """
            .trimIndent()
        ) as Questionnaire
      val questionnaireResponse =
        json.decodeFromString(
          """
          {
            "resourceType": "QuestionnaireResponse",
            "status": "completed"
          }
          """
            .trimIndent()
        ) as QuestionnaireResponse

      val bundle = QuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)

      assertEquals(Bundle.BundleType.Transaction, bundle.type.value)
      assertTrue(bundle.entry.isEmpty())
    }

  @Test
  fun extract_definitionBasedQuestionnaireWithoutContainedResources_usesDefinitionExtraction() =
    runTest {
      val questionnaire =
        json.decodeFromString(loadFixture("behavior_definition_extraction.json")) as Questionnaire
      val questionnaireResponse =
        json.decodeFromString(loadFixture("behavior_definition_extraction_response.json"))
          as QuestionnaireResponse

      assertTrue(questionnaire.contained.isEmpty())
      assertTrue(QuestionnaireResponseExtractor.canExtract(questionnaire))

      val bundle = QuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)
      val bundleJson = bundleJsonParser.parseToJsonElement(json.encodeToString(bundle)).jsonObject
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
      assertEquals("female", patientResource.requireString("gender"))
      assertEquals("1992-04-16", patientResource.requireString("birthDate"))
      assertEquals(
        "NH-12345",
        patientResource.requireArray("identifier").single().jsonObject.requireString("value"),
      )
      assertEquals(
        "+254700123456",
        patientResource.requireArray("telecom").single().jsonObject.requireString("value"),
      )

      val relatedPersonEntries = entries.filterByResourceType("RelatedPerson")
      assertEquals(2, relatedPersonEntries.size)
      relatedPersonEntries.forEach { entry ->
        val resource = entry.requireObject("resource")
        assertEquals(patientFullUrl, resource.requireObject("patient").requireString("reference"))
      }

      val observationEntry = entries.singleByResourceType("Observation")
      val observationResource = observationEntry.requireObject("resource")

      assertEquals("final", observationResource.requireString("status"))
      assertEquals(
        patientFullUrl,
        observationResource.requireObject("subject").requireString("reference"),
      )
      assertEquals(
        "1.68",
        observationResource.requireObject("valueQuantity").requireString("value"),
      )
      assertEquals("m", observationResource.requireObject("valueQuantity").requireString("unit"))
      assertEquals(
        "Practitioner/demo-author",
        observationResource.requireArray("performer").single().jsonObject.requireString("reference"),
      )
      assertEquals(
        "QuestionnaireResponse/behavior-definition-extraction-response",
        observationResource
          .requireArray("derivedFrom")
          .single()
          .jsonObject
          .requireString("reference"),
      )
    }

  @Test
  fun definitionExtractResourceRegistry_supportsFullR4ResourceRegistry() {
    assertTrue(DefinitionExtractResourceRegistry.supportedResourceTypes.size > 100)
    assertTrue(
      DefinitionExtractResourceRegistry.supportedResourceTypes.containsAll(
        setOf(
          "Patient",
          "RelatedPerson",
          "Observation",
          "Practitioner",
          "ServiceRequest",
          "Task",
          "Bundle",
          "ValueSet",
        )
      )
    )
  }

  @Test
  fun extract_definitionBasedQuestionnaire_supportsPractitionerResource() = runTest {
    val bundle =
      extract(
        questionnaireJson =
          """
          {
            "resourceType": "Questionnaire",
            "status": "active",
            "item": [
              {
                "linkId": "practitioner",
                "text": "Practitioner details",
                "type": "group",
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract",
                    "extension": [
                      {
                        "url": "definition",
                        "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Practitioner"
                      }
                    ]
                  }
                ],
                "item": [
                  {
                    "linkId": "active",
                    "type": "boolean",
                    "definition": "http://hl7.org/fhir/StructureDefinition/Practitioner#Practitioner.active"
                  },
                  {
                    "linkId": "name",
                    "type": "string",
                    "definition": "http://hl7.org/fhir/StructureDefinition/Practitioner#Practitioner.name.text"
                  },
                  {
                    "linkId": "license",
                    "type": "string",
                    "definition": "http://hl7.org/fhir/StructureDefinition/Practitioner#Practitioner.identifier.value",
                    "extension": [
                      {
                        "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                        "extension": [
                          {
                            "url": "definition",
                            "valueUri": "http://hl7.org/fhir/StructureDefinition/Practitioner#Practitioner.identifier.system"
                          },
                          {
                            "url": "fixed-value",
                            "valueUri": "http://example.org/fhir/sid/practitioner-license"
                          }
                        ]
                      }
                    ]
                  },
                  {
                    "linkId": "phone",
                    "type": "string",
                    "definition": "http://hl7.org/fhir/StructureDefinition/Practitioner#Practitioner.telecom.value",
                    "extension": [
                      {
                        "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                        "extension": [
                          {
                            "url": "definition",
                            "valueUri": "http://hl7.org/fhir/StructureDefinition/Practitioner#Practitioner.telecom.system"
                          },
                          {
                            "url": "fixed-value",
                            "valueCode": "phone"
                          }
                        ]
                      },
                      {
                        "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                        "extension": [
                          {
                            "url": "definition",
                            "valueUri": "http://hl7.org/fhir/StructureDefinition/Practitioner#Practitioner.telecom.use"
                          },
                          {
                            "url": "fixed-value",
                            "valueCode": "mobile"
                          }
                        ]
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
            "status": "completed",
            "item": [
              {
                "linkId": "practitioner",
                "item": [
                  {
                    "linkId": "active",
                    "answer": [
                      {
                        "valueBoolean": true
                      }
                    ]
                  },
                  {
                    "linkId": "name",
                    "answer": [
                      {
                        "valueString": "Naomi Wanjiku"
                      }
                    ]
                  },
                  {
                    "linkId": "license",
                    "answer": [
                      {
                        "valueString": "LIC-7788"
                      }
                    ]
                  },
                  {
                    "linkId": "phone",
                    "answer": [
                      {
                        "valueString": "+254711223344"
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

    val bundleJson = bundleJsonParser.parseToJsonElement(json.encodeToString(bundle)).jsonObject
    val practitionerEntry = bundleJson.requireArray("entry").singleByResourceType("Practitioner")
    val practitionerResource = practitionerEntry.requireObject("resource")
    val practitioner = bundle.resources().filterIsInstance<Practitioner>().single()

    assertEquals(Bundle.BundleType.Transaction, bundle.type.value)
    assertEquals("POST", practitionerEntry.requireObject("request").requireString("method"))
    assertEquals("Practitioner", practitionerEntry.requireObject("request").requireString("url"))
    assertTrue(practitionerEntry.requireString("fullUrl").startsWith("urn:uuid:"))
    assertEquals(true, practitioner.active?.value)
    assertEquals("Naomi Wanjiku", practitioner.name.single().text?.value)
    assertEquals("LIC-7788", practitioner.identifier.single().value?.value)
    assertEquals(
      "http://example.org/fhir/sid/practitioner-license",
      practitioner.identifier.single().system?.value,
    )
    assertEquals("+254711223344", practitioner.telecom.single().value?.value)
    assertEquals(
      "phone",
      practitionerResource.requireArray("telecom").single().jsonObject.requireString("system"),
    )
    assertEquals(
      "mobile",
      practitionerResource.requireArray("telecom").single().jsonObject.requireString("use"),
    )
  }

  @Test
  fun extract_officialComplexTemplate_extractsExpectedResources() = runTest {
    val bundle =
      extract(
        questionnaireJson = loadFixture("questionnaire_extract_complex_template.json"),
        questionnaireResponseJson =
          officialComplexTemplateResponseJson(
            questionnaireResponseId = "qr-complex-template-official",
            includeSecondContact = true,
          ),
      )

    assertEquals(Bundle.BundleType.Transaction, bundle.type.value)
    assertTrue(bundle.resources().all { it.id == null })

    val patientEntry = bundle.entry.first { it.resource is Patient }
    val patient = patientEntry.resource as Patient
    val relatedPeople =
      bundle.resources().filterIsInstance<RelatedPerson>().sortedBy {
        it.name.single().text?.value.orEmpty()
      }
    val observationsByCode =
      bundle.resources().filterIsInstance<Observation>().associateBy {
        it.code.coding.single().code?.value
      }

    assertTrue(relatedPeople.all { it.patient.reference?.value == patientEntry.fullUrl?.value })

    val height = observationsByCode.getValue("8302-2")
    val weight = observationsByCode.getValue("29463-7")
    val complication = observationsByCode.getValue("sigmoidoscopy-complication")

    assertEquals("Alex Jordan Example", patient.name.single().text?.value)
    assertEquals("Example", patient.name.single().family?.value)
    assertEquals(listOf("Alex", "Jordan"), patient.name.single().given.mapNotNull { it.value })
    assertEquals("8003601234567890", patient.identifier.single().value?.value)
    assertEquals("+254700000001", patient.telecom.single().value?.value)
    assertEquals("female", patient.gender?.value?.toString())
    assertEquals(
      listOf("Alice Example", "Bob Example"),
      relatedPeople.map { it.name.single().text?.value },
    )
    assertEquals(
      listOf("+254700000002", "+254700000003"),
      relatedPeople.map { it.telecom.single().value?.value },
    )
    assertEquals(patientEntry.fullUrl?.value, height.subject?.reference?.value)
    assertEquals("1.72E+2", height.value?.asQuantity()?.value?.value?.value?.toString())
    assertEquals("cm", height.value?.asQuantity()?.value?.unit?.value)
    assertEquals("6.84E+1", weight.value?.asQuantity()?.value?.value?.value?.toString())
    assertEquals("kg", weight.value?.asQuantity()?.value?.unit?.value)
    assertEquals(false, complication.value?.asBoolean()?.value?.value)

    val serializedBundle = json.encodeToString(bundle)
    assertFalse(serializedBundle.contains(EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL))
    assertFalse(serializedBundle.contains(EXTENSION_TEMPLATE_EXTRACT_VALUE_URL))
  }

  @Test
  fun extract_officialComplexTemplateBundle_preservesTemplateBundleRequestMetadata() = runTest {
    val bundle =
      extract(
        questionnaireJson = loadFixture("questionnaire_extract_complex_template_bundle.json"),
        questionnaireResponseJson =
          officialComplexTemplateResponseJson(
            questionnaireResponseId = "qr-complex-template-bundle",
            includeSecondContact = false,
          ),
      )

    assertEquals(Bundle.BundleType.Transaction, bundle.type.value)
    assertTrue(bundle.resources().all { it.id == null })

    val patientEntry = bundle.entry.first { it.resource is Patient }
    val patient = patientEntry.resource as Patient
    val relatedPersonEntry = bundle.entry.first { it.resource is RelatedPerson }
    val relatedPerson = relatedPersonEntry.resource as RelatedPerson
    val observationEntries = bundle.entry.filter { it.resource is Observation }
    val observationsByCode =
      observationEntries
        .map { it.resource as Observation }
        .associateBy { it.code.coding.single().code?.value }
    val height = observationsByCode.getValue("8302-2")
    val weight = observationsByCode.getValue("29463-7")
    val complication = observationsByCode.getValue("sigmoidoscopy-complication")

    assertEquals("urn:uuid:6f6177d2-13ee-4d27-b0e8-3eaf663dd031", patientEntry.fullUrl?.value)
    assertEquals(Bundle.HTTPVerb.Post, patientEntry.request?.method?.value)
    assertEquals("Patient", patientEntry.request?.url?.value)
    assertEquals(
      "Patient?_name=urn:uuid:6f6177d2-13ee-4d27-b0e8-3eaf663dd031",
      patientEntry.request?.ifMatch?.value,
    )
    assertEquals("Alex Jordan Example", patient.name.single().text?.value)
    assertEquals("Example", patient.name.single().family?.value)
    assertEquals(listOf("Alex", "Jordan"), patient.name.single().given.mapNotNull { it.value })
    assertEquals("8003601234567890", patient.identifier.single().value?.value)
    assertEquals("+254700000001", patient.telecom.single().value?.value)
    assertEquals("female", patient.gender?.value?.toString())
    assertEquals(patientEntry.fullUrl?.value, relatedPerson.patient.reference?.value)
    assertEquals(Bundle.HTTPVerb.Post, relatedPersonEntry.request?.method?.value)
    assertEquals("RelatedPerson", relatedPersonEntry.request?.url?.value)
    assertEquals("Alice Example", relatedPerson.name.single().text?.value)
    assertEquals("+254700000002", relatedPerson.telecom.single().value?.value)
    assertTrue(observationEntries.all { it.request?.method?.value == Bundle.HTTPVerb.Post })
    assertTrue(observationEntries.all { it.request?.url?.value == "Observation" })
    assertEquals("1.72E+2", height.value?.asQuantity()?.value?.value?.value?.toString())
    assertEquals("6.84E+1", weight.value?.asQuantity()?.value?.value?.value?.toString())
    assertEquals(false, complication.value?.asBoolean()?.value?.value)
  }

  @Test
  fun extract_templateBundle_preservesSharedAllocatedReference() = runTest {
    val bundle =
      extract(
        questionnaireJson =
          """
          {
            "resourceType": "Questionnaire",
            "status": "active",
            "contained": [
              {
                "resourceType": "Bundle",
                "id": "transactionTemplate",
                "type": "transaction",
                "entry": [
                  {
                    "_fullUrl": {
                      "extension": [
                        {
                          "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                          "valueString": "%patientRef"
                        }
                      ]
                    },
                    "resource": {
                      "resourceType": "Patient",
                      "id": "patientTemplate",
                      "_active": {
                        "extension": [
                          {
                            "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                            "valueString": "item.where(linkId='patient-active').answer.value.first()"
                          }
                        ]
                      }
                    },
                    "request": {
                      "method": "POST",
                      "url": "Patient"
                    }
                  },
                  {
                    "resource": {
                      "resourceType": "Observation",
                      "id": "observationTemplate",
                      "status": "final",
                      "code": {
                        "text": "Smoking status"
                      },
                      "subject": {
                        "_reference": {
                          "extension": [
                            {
                              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                              "valueString": "%patientRef"
                            }
                          ]
                        }
                      },
                      "_valueBoolean": {
                        "extension": [
                          {
                            "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                            "valueString": "item.where(linkId='smoker').answer.value.first()"
                          }
                        ]
                      }
                    },
                    "request": {
                      "method": "POST",
                      "url": "Observation"
                    }
                  }
                ]
              }
            ],
            "extension": [
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId",
                "valueString": "patientRef"
              },
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractBundle",
                "valueReference": {
                  "reference": "#transactionTemplate"
                }
              }
            ],
            "item": [
              {
                "linkId": "patient-active",
                "type": "boolean"
              },
              {
                "linkId": "smoker",
                "type": "boolean"
              }
            ]
          }
          """
            .trimIndent(),
        questionnaireResponseJson =
          """
          {
            "resourceType": "QuestionnaireResponse",
            "status": "completed",
            "item": [
              {
                "linkId": "patient-active",
                "answer": [
                  {
                    "valueBoolean": true
                  }
                ]
              },
              {
                "linkId": "smoker",
                "answer": [
                  {
                    "valueBoolean": false
                  }
                ]
              }
            ]
          }
          """
            .trimIndent(),
      )

    val patientEntry = bundle.entry.first { it.resource is Patient }
    val observation = bundle.resources().filterIsInstance<Observation>().single()

    assertEquals(patientEntry.fullUrl?.value, observation.subject?.reference?.value)
    assertEquals(false, observation.value?.asBoolean()?.value?.value)
  }

  @Test
  fun extract_templateExtractReferencingBundle_extractsBundleResource() = runTest {
    val bundle =
      extract(
        questionnaireJson =
          """
          {
            "resourceType": "Questionnaire",
            "status": "active",
            "contained": [
              {
                "resourceType": "Bundle",
                "id": "collectionTemplate",
                "type": "collection",
                "entry": [
                  {
                    "resource": {
                      "resourceType": "Observation",
                      "status": "final",
                      "code": {
                        "text": "Smoking status"
                      },
                      "_valueBoolean": {
                        "extension": [
                          {
                            "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                            "valueString": "item.where(linkId='smoker').answer.value.first()"
                          }
                        ]
                      }
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
                      "reference": "#collectionTemplate"
                    }
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "smoker",
                "type": "boolean"
              }
            ]
          }
          """
            .trimIndent(),
        questionnaireResponseJson =
          """
          {
            "resourceType": "QuestionnaireResponse",
            "status": "completed",
            "item": [
              {
                "linkId": "smoker",
                "answer": [
                  {
                    "valueBoolean": false
                  }
                ]
              }
            ]
          }
          """
            .trimIndent(),
      )

    val extractedBundle = bundle.resources().filterIsInstance<Bundle>().single()
    val observation = extractedBundle.entry.single().resource as Observation

    assertEquals(1, bundle.resources().size)
    assertNull(extractedBundle.id)
    assertEquals(Bundle.BundleType.Collection, extractedBundle.type.value)
    assertEquals("Bundle", bundle.entry.first { it.resource is Bundle }.request?.url?.value)
    assertEquals(false, observation.value?.asBoolean()?.value?.value)
  }

  @Test
  fun extract_templateExtractReferencingBundle_withResourceId_usesPutRequest() = runTest {
    val bundle =
      extract(
        questionnaireJson =
          """
          {
            "resourceType": "Questionnaire",
            "status": "active",
            "contained": [
              {
                "resourceType": "Bundle",
                "id": "collectionTemplate",
                "type": "collection",
                "entry": [
                  {
                    "resource": {
                      "resourceType": "Observation",
                      "status": "final",
                      "code": {
                        "text": "Smoking status"
                      },
                      "_valueBoolean": {
                        "extension": [
                          {
                            "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                            "valueString": "item.where(linkId='smoker').answer.value.first()"
                          }
                        ]
                      }
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
                      "reference": "#collectionTemplate"
                    }
                  },
                  {
                    "url": "resourceId",
                    "valueString": "item.where(linkId='bundle-id').answer.value.first()"
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "bundle-id",
                "type": "string"
              },
              {
                "linkId": "smoker",
                "type": "boolean"
              }
            ]
          }
          """
            .trimIndent(),
        questionnaireResponseJson =
          """
          {
            "resourceType": "QuestionnaireResponse",
            "status": "completed",
            "item": [
              {
                "linkId": "bundle-id",
                "answer": [
                  {
                    "valueString": "supplemental-bundle"
                  }
                ]
              },
              {
                "linkId": "smoker",
                "answer": [
                  {
                    "valueBoolean": false
                  }
                ]
              }
            ]
          }
          """
            .trimIndent(),
      )

    val bundleEntry = bundle.entry.first { it.resource is Bundle }
    val extractedBundle = bundleEntry.resource as Bundle
    val observation = extractedBundle.entry.single().resource as Observation

    assertNull(extractedBundle.id)
    assertEquals(Bundle.HTTPVerb.Put, bundleEntry.request?.method?.value)
    assertEquals("Bundle/supplemental-bundle", bundleEntry.request?.url?.value)
    assertEquals(false, observation.value?.asBoolean()?.value?.value)
  }

  @Test
  fun extract_repeatingGroupTemplateExtractReferencingBundle_createsOneBundlePerIteration() =
    runTest {
      val bundle =
        extract(
          questionnaireJson =
            """
            {
              "resourceType": "Questionnaire",
              "status": "active",
              "contained": [
                {
                  "resourceType": "Bundle",
                  "id": "collectionTemplate",
                  "type": "collection",
                  "entry": [
                    {
                      "resource": {
                        "resourceType": "Observation",
                        "status": "final",
                        "code": {
                          "text": "Visit smoking status"
                        },
                        "_valueBoolean": {
                          "extension": [
                            {
                              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                              "valueString": "item.where(linkId='smoker').answer.value.first()"
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
              ],
              "item": [
                {
                  "linkId": "visits",
                  "type": "group",
                  "repeats": true,
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract",
                      "extension": [
                        {
                          "url": "template",
                          "valueReference": {
                            "reference": "#collectionTemplate"
                          }
                        }
                      ]
                    }
                  ],
                  "item": [
                    {
                      "linkId": "smoker",
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
              "status": "completed",
              "item": [
                {
                  "linkId": "visits",
                  "item": [
                    {
                      "linkId": "smoker",
                      "answer": [
                        {
                          "valueBoolean": false
                        }
                      ]
                    }
                  ]
                },
                {
                  "linkId": "visits",
                  "item": [
                    {
                      "linkId": "smoker",
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

      val extractedBundles = bundle.resources().filterIsInstance<Bundle>()
      val values =
        extractedBundles.map { extractedBundle ->
          val observation = extractedBundle.entry.single().resource as Observation
          observation.value?.asBoolean()?.value?.value
        }

      assertEquals(2, extractedBundles.size)
      assertTrue(
        bundle.entry.filter { it.resource is Bundle }.all { it.request?.url?.value == "Bundle" }
      )
    }

  @Test
  fun extract_templateBundleContainingBundleResource_keepsInnerBundleAsResource() = runTest {
    val bundle =
      extract(
        questionnaireJson =
          """
          {
            "resourceType": "Questionnaire",
            "status": "active",
            "contained": [
              {
                "resourceType": "Bundle",
                "id": "transactionTemplate",
                "type": "transaction",
                "entry": [
                  {
                    "_fullUrl": {
                      "extension": [
                        {
                          "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                          "valueString": "%patientRef"
                        }
                      ]
                    },
                    "resource": {
                      "resourceType": "Patient",
                      "_active": {
                        "extension": [
                          {
                            "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                            "valueString": "item.where(linkId='patient-active').answer.value.first()"
                          }
                        ]
                      }
                    },
                    "request": {
                      "method": "POST",
                      "url": "Patient"
                    }
                  },
                  {
                    "resource": {
                      "resourceType": "Encounter",
                      "status": "finished",
                      "class": {
                        "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                        "code": "AMB",
                        "display": "ambulatory"
                      },
                      "subject": {
                        "_reference": {
                          "extension": [
                            {
                              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                              "valueString": "%patientRef"
                            }
                          ]
                        }
                      }
                    },
                    "request": {
                      "method": "POST",
                      "url": "Encounter"
                    }
                  },
                  {
                    "resource": {
                      "resourceType": "Bundle",
                      "id": "supplementalTemplate",
                      "type": "collection",
                      "entry": [
                        {
                          "resource": {
                            "resourceType": "Observation",
                            "status": "final",
                            "code": {
                              "text": "Smoking status"
                            },
                            "subject": {
                              "_reference": {
                                "extension": [
                                  {
                                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                                    "valueString": "%patientRef"
                                  }
                                ]
                              }
                            },
                            "_valueBoolean": {
                              "extension": [
                                {
                                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                                  "valueString": "item.where(linkId='smoker').answer.value.first()"
                                }
                              ]
                            }
                          }
                        }
                      ]
                    },
                    "request": {
                      "method": "PUT",
                      "url": "Bundle/supplemental-bundle"
                    }
                  }
                ]
              }
            ],
            "extension": [
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId",
                "valueString": "patientRef"
              },
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractBundle",
                "valueReference": {
                  "reference": "#transactionTemplate"
                }
              }
            ],
            "item": [
              {
                "linkId": "patient-active",
                "type": "boolean"
              },
              {
                "linkId": "smoker",
                "type": "boolean"
              }
            ]
          }
          """
            .trimIndent(),
        questionnaireResponseJson =
          """
          {
            "resourceType": "QuestionnaireResponse",
            "status": "completed",
            "item": [
              {
                "linkId": "patient-active",
                "answer": [
                  {
                    "valueBoolean": true
                  }
                ]
              },
              {
                "linkId": "smoker",
                "answer": [
                  {
                    "valueBoolean": false
                  }
                ]
              }
            ]
          }
          """
            .trimIndent(),
      )

    val patientEntry = bundle.entry.first { it.resource is Patient }
    val encounter = bundle.resources().filterIsInstance<Encounter>().single()
    val innerBundleEntry = bundle.entry.first { it.resource is Bundle }
    val innerBundle = innerBundleEntry.resource as Bundle
    val innerObservation = innerBundle.entry.single().resource as Observation

    assertEquals(3, bundle.resources().size)
    assertNull(innerBundle.id)
    assertEquals(Bundle.BundleType.Collection, innerBundle.type.value)
    assertEquals(Bundle.HTTPVerb.Put, innerBundleEntry.request?.method?.value)
    assertEquals("Bundle/supplemental-bundle", innerBundleEntry.request?.url?.value)
    assertEquals(patientEntry.fullUrl?.value, encounter.subject?.reference?.value)
    assertEquals(patientEntry.fullUrl?.value, innerObservation.subject?.reference?.value)
    assertEquals(false, innerObservation.value?.asBoolean()?.value?.value)
  }

  @Test
  fun extract_repeatingGroup_createsOneResourcePerIterationAndSharesRootUuid() = runTest {
    val bundle =
      extract(
        questionnaireJson =
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
                          "valueString": "item.where(linkId='patient-name').answer.value.first()"
                        }
                      ]
                    }
                  }
                ]
              },
              {
                "resourceType": "RelatedPerson",
                "id": "contactTemplate",
                "patient": {
                  "_reference": {
                    "extension": [
                      {
                        "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                        "valueString": "%patientRef"
                      }
                    ]
                  }
                },
                "name": [
                  {
                    "_text": {
                      "extension": [
                        {
                          "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                          "valueString": "item.where(linkId='contact-name').answer.value.first()"
                        }
                      ]
                    }
                  }
                ]
              }
            ],
            "extension": [
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId",
                "valueString": "patientRef"
              },
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract",
                "extension": [
                  {
                    "url": "template",
                    "valueReference": {
                      "reference": "#patientTemplate"
                    }
                  },
                  {
                    "url": "fullUrl",
                    "valueString": "%patientRef"
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "patient-name",
                "type": "string"
              },
              {
                "linkId": "contacts",
                "type": "group",
                "repeats": true,
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract",
                    "extension": [
                      {
                        "url": "template",
                        "valueReference": {
                          "reference": "#contactTemplate"
                        }
                      }
                    ]
                  }
                ],
                "item": [
                  {
                    "linkId": "contact-name",
                    "type": "string"
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
            "status": "completed",
            "item": [
              {
                "linkId": "patient-name",
                "answer": [
                  {
                    "valueString": "Primary Patient"
                  }
                ]
              },
              {
                "linkId": "contacts",
                "item": [
                  {
                    "linkId": "contact-name",
                    "answer": [
                      {
                        "valueString": "Alice"
                      }
                    ]
                  }
                ]
              },
              {
                "linkId": "contacts",
                "item": [
                  {
                    "linkId": "contact-name",
                    "answer": [
                      {
                        "valueString": "Bob"
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

    val patientEntry = bundle.entry.first { it.resource is Patient }
    val patient = patientEntry.resource as Patient
    val relatedPeople =
      bundle.resources().filterIsInstance<RelatedPerson>().sortedBy {
        it.name.single().text?.value.orEmpty()
      }
    println("Test Results here ")
    assertEquals("Primary Patient", patient.name.single().text?.value)
    assertEquals(2, relatedPeople.size)
    assertTrue(relatedPeople.all { it.patient.reference?.value == patientEntry.fullUrl?.value })
    assertEquals(listOf("Alice", "Bob"), relatedPeople.map { it.name.single().text?.value })
  }

  @Test
  fun extract_collectionCondition_removesOnlyTheMissingCollectionElement() = runTest {
    val bundle =
      extract(
        questionnaireJson =
          """
          {
            "resourceType": "Questionnaire",
            "status": "active",
            "contained": [
              {
                "resourceType": "Patient",
                "id": "patientTemplate",
                "telecom": [
                  {
                    "extension": [
                      {
                        "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractContext",
                        "valueString": "item.where(linkId='phone').answer.value.first()"
                      }
                    ],
                    "system": "phone",
                    "_value": {
                      "extension": [
                        {
                          "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                          "valueString": "%context"
                        }
                      ]
                    }
                  },
                  {
                    "extension": [
                      {
                        "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractContext",
                        "valueString": "item.where(linkId='email').answer.value.first()"
                      }
                    ],
                    "system": "email",
                    "_value": {
                      "extension": [
                        {
                          "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                          "valueString": "%context"
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
              }
            ],
            "item": [
              {
                "linkId": "phone",
                "type": "string"
              },
              {
                "linkId": "email",
                "type": "string"
              }
            ]
          }
          """
            .trimIndent(),
        questionnaireResponseJson =
          """
          {
            "resourceType": "QuestionnaireResponse",
            "status": "completed",
            "item": [
              {
                "linkId": "phone",
                "answer": [
                  {
                    "valueString": "+254700000000"
                  }
                ]
              }
            ]
          }
          """
            .trimIndent(),
      )

    val patient = bundle.resources().filterIsInstance<Patient>().single()
    assertEquals(1, patient.telecom.size)
    assertEquals("phone", patient.telecom.single().system?.value?.toString())
    assertEquals("+254700000000", patient.telecom.single().value?.value)
  }

  @Test
  fun extract_expressionFailure_skipsBadPropertyAndContinuesExtraction() = runTest {
    val bundle =
      extract(
        questionnaireJson =
          """
          {
            "resourceType": "Questionnaire",
            "status": "active",
            "contained": [
              {
                "resourceType": "Patient",
                "id": "patientTemplate",
                "_active": {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                      "valueString": "item.where(linkId='active').answer.value.first("
                    }
                  ]
                },
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
              }
            ],
            "item": [
              {
                "linkId": "active",
                "type": "boolean"
              },
              {
                "linkId": "name",
                "type": "string"
              }
            ]
          }
          """
            .trimIndent(),
        questionnaireResponseJson =
          """
          {
            "resourceType": "QuestionnaireResponse",
            "status": "completed",
            "item": [
              {
                "linkId": "active",
                "answer": [
                  {
                    "valueBoolean": true
                  }
                ]
              },
              {
                "linkId": "name",
                "answer": [
                  {
                    "valueString": "Recovered Patient"
                  }
                ]
              }
            ]
          }
          """
            .trimIndent(),
      )

    val patient = bundle.resources().filterIsInstance<Patient>().single()
    assertNull(patient.active)
    assertEquals("Recovered Patient", patient.name.single().text?.value)
  }

  private suspend fun extract(
    questionnaireJson: String,
    questionnaireResponseJson: String,
  ): Bundle {
    val questionnaire = json.decodeFromString(questionnaireJson) as Questionnaire
    val questionnaireResponse =
      json.decodeFromString(questionnaireResponseJson) as QuestionnaireResponse
    return QuestionnaireResponseExtractor.extract(questionnaire, questionnaireResponse)
  }

  private suspend fun loadFixture(fileName: String): String =
    Res.readBytes("files/$fileName").decodeToString()

  private fun JsonObject.requireArray(name: String): JsonArray = getValue(name).jsonArray

  private fun JsonObject.requireObject(name: String): JsonObject = getValue(name).jsonObject

  private fun JsonObject.requireString(name: String): String = getValue(name).jsonPrimitive.content

  private fun JsonArray.singleByResourceType(resourceType: String): JsonObject =
    single { it.jsonObject.requireObject("resource").requireString("resourceType") == resourceType }
      .jsonObject

  private fun JsonArray.filterByResourceType(resourceType: String): List<JsonObject> =
    filter { it.jsonObject.requireObject("resource").requireString("resourceType") == resourceType }
      .map { it.jsonObject }

  private fun officialComplexTemplateResponseJson(
    questionnaireResponseId: String,
    includeSecondContact: Boolean,
  ): String {
    val secondContact =
      if (!includeSecondContact) {
        ""
      } else {
        """
        ,
        {
          "linkId": "contacts",
          "item": [
            {
              "linkId": "contact-name",
              "answer": [
                {
                  "valueString": "Bob Example"
                }
              ]
            },
            {
              "linkId": "relationship",
              "answer": [
                {
                  "valueCoding": {
                    "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                    "code": "N",
                    "display": "Next-of-kin"
                  }
                }
              ]
            },
            {
              "linkId": "phone",
              "answer": [
                {
                  "valueString": "+254700000003"
                }
              ]
            }
          ]
        }
        """
          .trimIndent()
      }

    return """
      {
        "resourceType": "QuestionnaireResponse",
        "id": "$questionnaireResponseId",
        "status": "completed",
        "authored": "2026-04-30T12:34:56Z",
        "author": {
          "reference": "Practitioner/prac-1"
        },
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
                      {
                        "valueString": "Alex"
                      },
                      {
                        "valueString": "Jordan"
                      }
                    ]
                  },
                  {
                    "linkId": "family",
                    "answer": [
                      {
                        "valueString": "Example"
                      }
                    ]
                  }
                ]
              },
              {
                "linkId": "gender",
                "answer": [
                  {
                    "valueCoding": {
                      "system": "http://hl7.org/fhir/administrative-gender",
                      "code": "female",
                      "display": "Female"
                    }
                  }
                ]
              },
              {
                "linkId": "dob",
                "answer": [
                  {
                    "valueDate": "1990-04-12"
                  }
                ]
              },
              {
                "linkId": "ihi",
                "answer": [
                  {
                    "valueString": "8003601234567890"
                  }
                ]
              },
              {
                "linkId": "mobile-phone",
                "answer": [
                  {
                    "valueString": "+254700000001"
                  }
                ]
              }
            ]
          },
          {
            "linkId": "contacts",
            "item": [
              {
                "linkId": "contact-name",
                "answer": [
                  {
                    "valueString": "Alice Example"
                  }
                ]
              },
              {
                "linkId": "relationship",
                "answer": [
                  {
                    "valueCoding": {
                      "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
                      "code": "C",
                      "display": "Emergency contact"
                    }
                  }
                ]
              },
              {
                "linkId": "phone",
                "answer": [
                  {
                    "valueString": "+254700000002"
                  }
                ]
              }
            ]
          }$secondContact,
          {
            "linkId": "obs",
            "item": [
              {
                "linkId": "height",
                "answer": [
                  {
                    "valueDecimal": 1.72
                  }
                ]
              },
              {
                "linkId": "weight",
                "answer": [
                  {
                    "valueDecimal": 68.4
                  }
                ]
              },
              {
                "linkId": "complication",
                "answer": [
                  {
                    "valueBoolean": false
                  }
                ]
              }
            ]
          }
        ]
      }
      """
      .trimIndent()
  }

  private fun Bundle.resources() = entry.mapNotNull { it.resource }.filterNot { it is Provenance }
}
