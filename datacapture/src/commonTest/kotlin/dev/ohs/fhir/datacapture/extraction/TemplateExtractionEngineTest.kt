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

import dev.ohs.fhir.datacapture.RunWith
import dev.ohs.fhir.datacapture.AndroidJUnit4
import dev.ohs.fhir.datacapture.extraction.template.TemplateEvaluationScope
import dev.ohs.fhir.datacapture.extraction.template.TemplateExtractionEngine
import dev.ohs.fhir.datacapture.extraction.template.TemplateTreeProcessor
import dev.ohs.fhir.model.r4.Bundle
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@RunWith(AndroidJUnit4::class)
class TemplateExtractionEngineTest {
  private val json = Json {
    explicitNulls = false
    encodeDefaults = false
  }
  private val treeProcessor = TemplateTreeProcessor()

  @Test
  fun extractsRootAndRepeatedGroupResourcesUsingSharedAllocatedIds() {
    val questionnaire =
      questionnaire(
        """
        {
          "resourceType": "Questionnaire",
          "url": "http://example.org/Questionnaire/template-root-and-group",
          "status": "active",
          "extension": [
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract",
              "extension": [
                {
                  "url": "template",
                  "valueReference": {
                    "reference": "#patient-template"
                  }
                },
                {
                  "url": "fullUrl",
                  "valueString": "%patientFullUrl"
                }
              ]
            },
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId",
              "valueString": "%patientFullUrl"
            }
          ],
          "item": [
            {
              "linkId": "given",
              "text": "Given name",
              "type": "string",
              "repeats": true
            },
            {
              "linkId": "family",
              "text": "Family name",
              "type": "string"
            },
            {
              "linkId": "condition",
              "text": "Condition",
              "type": "group",
              "repeats": true,
              "extension": [
                {
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract",
                  "extension": [
                    {
                      "url": "template",
                      "valueReference": {
                        "reference": "#condition-template"
                      }
                    }
                  ]
                }
              ],
              "item": [
                {
                  "linkId": "condition-name",
                  "text": "Condition name",
                  "type": "string"
                }
              ]
            }
          ],
          "contained": [
            {
              "resourceType": "Patient",
              "id": "patient-template",
              "name": [
                {
                  "family": "",
                  "_family": {
                    "extension": [
                      {
                        "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                        "valueString": "%resource.repeat(item).where(linkId='family').answer.value.first()"
                      }
                    ]
                  },
                  "given": [
                    ""
                  ],
                  "_given": [
                    {
                      "extension": [
                        {
                          "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                          "valueString": "%resource.repeat(item).where(linkId='given').answer.value"
                        }
                      ]
                    }
                  ]
                }
              ],
              "birthDate": "1900-01-01",
              "_birthDate": {
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                    "valueString": "%resource.repeat(item).where(linkId='birthDate').answer.value.first()"
                  }
                ]
              }
            },
            {
              "resourceType": "Condition",
              "id": "condition-template",
              "subject": {
                "reference": "",
                "_reference": {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                      "valueString": "%patientFullUrl"
                    }
                  ]
                }
              },
              "code": {
                "text": "",
                "_text": {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                      "valueString": "%context.item.where(linkId='condition-name').answer.value.first()"
                    }
                  ]
                }
              }
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
          "questionnaire": "http://example.org/Questionnaire/template-root-and-group",
          "status": "completed",
          "item": [
            {
              "linkId": "family",
              "answer": [
                {
                  "valueString": "Doe"
                }
              ]
            },
            {
              "linkId": "given",
              "answer": [
                {
                  "valueString": "Jane"
                },
                {
                  "valueString": "Alex"
                }
              ]
            },
            {
              "linkId": "condition",
              "item": [
                {
                  "linkId": "condition-name",
                  "answer": [
                    {
                      "valueString": "Asthma"
                    }
                  ]
                }
              ]
            },
            {
              "linkId": "condition",
              "item": [
                {
                  "linkId": "condition-name",
                  "answer": [
                    {
                      "valueString": "Diabetes"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """
      )

    val result = TemplateExtractionEngine.extract(questionnaire, questionnaireResponse)

    assertEquals(3, result.entry.size)

    val entryObjects = bundleEntryObjects(result)
    val patientEntry = entryObjects.single { resourceType(it) == "Patient" }
    val conditionEntries = entryObjects.filter { resourceType(it) == "Condition" }
    assertEquals(2, conditionEntries.size)

    val patientFullUrl = patientEntry.getValue("fullUrl").jsonPrimitive.content
    val patientResource = patientEntry.getValue("resource").jsonObject
    val patientName = patientResource.getValue("name").jsonArray.single().jsonObject
    assertEquals("Doe", patientName.getValue("family").jsonPrimitive.content)
    assertEquals(
      listOf("Jane", "Alex"),
      patientName.getValue("given").jsonArray.map { it.jsonPrimitive.content },
    )
    assertFalse(patientResource.containsKey("birthDate"))
    assertEquals(
      "POST",
      patientEntry
        .getValue("request")
        .jsonObject
        .getValue("method")
        .jsonPrimitive
        .content
        .uppercase(),
    )
    assertEquals(
      "Patient",
      patientEntry.getValue("request").jsonObject.getValue("url").jsonPrimitive.content,
    )

    val conditionTexts =
      conditionEntries.map { conditionEntry ->
        val request = conditionEntry.getValue("request").jsonObject
        assertEquals("POST", request.getValue("method").jsonPrimitive.content.uppercase())
        assertEquals("Condition", request.getValue("url").jsonPrimitive.content)

        val resource = conditionEntry.getValue("resource").jsonObject
        assertEquals(
          patientFullUrl,
          resource.getValue("subject").jsonObject.getValue("reference").jsonPrimitive.content,
        )
        resource.getValue("code").jsonObject.getValue("text").jsonPrimitive.content
      }

    assertEquals(setOf("Asthma", "Diabetes"), conditionTexts.toSet())
  }

  @Test
  fun extractsOneResourcePerRepeatedAnswerForRepeatingQuestion() {
    val questionnaire =
      questionnaire(
        """
        {
          "resourceType": "Questionnaire",
          "url": "http://example.org/Questionnaire/repeating-answer-template",
          "status": "active",
          "contained": [
            {
              "resourceType": "Observation",
              "id": "phone-template",
              "status": "final",
              "code": {
                "text": "phone"
              },
              "valueString": "",
              "_valueString": {
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                    "valueString": "%context.answer.value.first()"
                  }
                ]
              }
            }
          ],
          "item": [
            {
              "linkId": "phone",
              "text": "Phone",
              "type": "string",
              "repeats": true,
              "extension": [
                {
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract",
                  "extension": [
                    {
                      "url": "template",
                      "valueReference": {
                        "reference": "#phone-template"
                      }
                    },
                    {
                      "url": "resourceId",
                      "valueString": "%context.answer.value.first()"
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
          "questionnaire": "http://example.org/Questionnaire/repeating-answer-template",
          "status": "completed",
          "item": [
            {
              "linkId": "phone",
              "answer": [
                {
                  "valueString": "phone-1"
                },
                {
                  "valueString": "phone-2"
                }
              ]
            }
          ]
        }
        """
      )

    val result = TemplateExtractionEngine.extract(questionnaire, questionnaireResponse)

    assertEquals(2, result.entry.size)

    val entryObjects = bundleEntryObjects(result)
    val requestUrls =
      entryObjects.map { entry ->
        val request = entry.getValue("request").jsonObject
        assertEquals("PUT", request.getValue("method").jsonPrimitive.content.uppercase())
        request.getValue("url").jsonPrimitive.content
      }
    assertEquals(setOf("Observation/phone-1", "Observation/phone-2"), requestUrls.toSet())

    val extractedIds =
      entryObjects.map { entry ->
        val resource = entry.getValue("resource").jsonObject
        assertEquals("Observation", resourceType(entry))
        resource.getValue("id").jsonPrimitive.content to
          resource.getValue("valueString").jsonPrimitive.content
      }
    assertEquals(setOf("phone-1" to "phone-1", "phone-2" to "phone-2"), extractedIds.toSet())
  }

  @Test
  fun processResource_allowsTemplateIdToBeOverriddenDuringJsonProcessing() {
    val template =
      json
        .parseToJsonElement(
          """
          {
            "resourceType": "Observation",
            "id": "observation-template",
            "_id": {
              "extension": [
                {
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                  "valueString": "%context.answer.value.first()"
                }
              ]
            },
            "status": "final",
            "code": {
              "text": "phone"
            }
          }
          """
        )
        .jsonObject

    val questionnaire =
      questionnaire(
        """
        {
          "resourceType": "Questionnaire",
          "url": "http://example.org/Questionnaire/template-id-override",
          "status": "active",
          "item": [
            {
              "linkId": "phone",
              "text": "Phone",
              "type": "string"
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
          "questionnaire": "http://example.org/Questionnaire/template-id-override",
          "status": "completed",
          "item": [
            {
              "linkId": "phone",
              "answer": [
                {
                  "valueString": "phone-1"
                }
              ]
            }
          ]
        }
        """
      )

    val processedResources =
      treeProcessor.processResource(
        template = template,
        scope =
          TemplateEvaluationScope(
            questionnaire = questionnaire,
            questionnaireResponse = questionnaireResponse,
            questionnaireItem = questionnaire.item.single(),
            context = questionnaireResponse.item.single(),
            variables = emptyMap(),
          ),
      )

    assertEquals(1, processedResources.size)
    val processedResource = processedResources.single()
    assertEquals("phone-1", processedResource.getValue("id").jsonPrimitive.content)
    assertFalse(processedResource.containsKey("_id"))
  }

  @Test
  fun throwsWhenTemplateEvaluationEncountersFatalExtractionError() {
    val questionnaire =
      questionnaire(
        """
        {
          "resourceType": "Questionnaire",
          "url": "http://example.org/Questionnaire/invalid-template-language",
          "status": "active",
          "extension": [
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract",
              "extension": [
                {
                  "url": "template",
                  "valueReference": {
                    "reference": "#observation-template"
                  }
                }
              ]
            }
          ],
          "contained": [
            {
              "resourceType": "Observation",
              "id": "observation-template",
              "status": "final",
              "code": {
                "text": "demo"
              },
              "valueString": "",
              "_valueString": {
                "extension": [
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                    "valueExpression": {
                      "language": "text/cql",
                      "expression": "Patient.name.first()"
                    }
                  }
                ]
              }
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
          "questionnaire": "http://example.org/Questionnaire/invalid-template-language",
          "status": "completed"
        }
        """
      )

    val throwable =
      assertFailsWith<DataExtractionException> {
        TemplateExtractionEngine.extract(questionnaire, questionnaireResponse)
      }

    assertTrue(
      throwable.message.orEmpty().contains("Only FHIRPath expressions are supported"),
      "Expected fatal runtime extraction errors to hard-fail with the underlying diagnostics.",
    )
  }

  @Test
  fun extractsBundleTemplateEntriesFromQuestionnaireLevelExtension() {
    val questionnaire =
      questionnaire(
        """
        {
          "resourceType": "Questionnaire",
          "url": "http://example.org/Questionnaire/template-bundle",
          "status": "active",
          "extension": [
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractBundle",
              "valueReference": {
                "reference": "#bundle-template"
              }
            }
          ],
          "item": [
            {
              "linkId": "patient",
              "text": "Patient",
              "type": "group",
              "item": [
                {
                  "linkId": "name",
                  "text": "Name",
                  "type": "group",
                  "item": [
                    {
                      "linkId": "given",
                      "text": "Given",
                      "type": "string",
                      "repeats": true
                    },
                    {
                      "linkId": "family",
                      "text": "Family",
                      "type": "string"
                    }
                  ]
                }
              ]
            },
            {
              "linkId": "obs",
              "text": "Observation",
              "type": "group",
              "item": [
                {
                  "linkId": "height",
                  "text": "Height in m",
                  "type": "decimal"
                }
              ]
            }
          ],
          "contained": [
            {
              "resourceType": "Bundle",
              "id": "bundle-template",
              "type": "transaction",
              "entry": [
                {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractContext",
                      "valueString": "item.where(linkId = 'patient')"
                    }
                  ],
                  "fullUrl": "urn:uuid:patient-1",
                  "resource": {
                    "resourceType": "Patient",
                    "name": [
                      {
                        "extension": [
                          {
                            "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractContext",
                            "valueString": "item.where(linkId = 'name')"
                          }
                        ],
                        "_family": {
                          "extension": [
                            {
                              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                              "valueString": "item.where(linkId = 'family').answer.value.first()"
                            }
                          ]
                        },
                        "_given": [
                          {
                            "extension": [
                              {
                                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                                "valueString": "item.where(linkId = 'given').answer.value"
                              }
                            ]
                          }
                        ]
                      }
                    ]
                  },
                  "request": {
                    "method": "POST",
                    "url": "Patient"
                  }
                },
                {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractContext",
                      "valueString": "item.where(linkId = 'obs').item.where(linkId = 'height')"
                    }
                  ],
                  "fullUrl": "urn:uuid:height-1",
                  "resource": {
                    "resourceType": "Observation",
                    "status": "final",
                    "code": {
                      "text": "Height"
                    },
                    "subject": {
                      "reference": "urn:uuid:patient-1"
                    },
                    "valueQuantity": {
                      "value": 0,
                      "_value": {
                        "extension": [
                          {
                            "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue",
                            "valueString": "answer.value * 100"
                          }
                        ]
                      },
                      "unit": "cm",
                      "system": "http://unitsofmeasure.org",
                      "code": "cm"
                    }
                  },
                  "request": {
                    "method": "POST",
                    "url": "Observation"
                  }
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
          "id": "qr-123",
          "questionnaire": "http://example.org/Questionnaire/template-bundle",
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
                        {
                          "valueString": "Jane"
                        },
                        {
                          "valueString": "Alex"
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
                }
              ]
            },
            {
              "linkId": "obs",
              "item": [
                {
                  "linkId": "height",
                  "answer": [
                    {
                      "valueDecimal": 1.75
                    }
                  ]
                }
              ]
            }
          ]
        }
        """
      )

    val result = TemplateExtractionEngine.extract(questionnaire, questionnaireResponse)

    assertEquals(Bundle.BundleType.Transaction, result.type.value)
    assertEquals(2, result.entry.size)

    val entryObjects = bundleEntryObjects(result)
    val patientEntry = entryObjects.single { resourceType(it) == "Patient" }
    val observationEntry = entryObjects.single { resourceType(it) == "Observation" }

    val patientName =
      patientEntry.getValue("resource").jsonObject.getValue("name").jsonArray.single().jsonObject
    assertEquals("Doe", patientName.getValue("family").jsonPrimitive.content)
    assertEquals(
      listOf("Jane", "Alex"),
      patientName.getValue("given").jsonArray.map { it.jsonPrimitive.content },
    )

    val valueQuantity =
      observationEntry.getValue("resource").jsonObject.getValue("valueQuantity").jsonObject
    assertEquals("175", valueQuantity.getValue("value").jsonPrimitive.content)
    assertEquals(
      "urn:uuid:patient-1",
      observationEntry
        .getValue("resource")
        .jsonObject
        .getValue("subject")
        .jsonObject
        .getValue("reference")
        .jsonPrimitive
        .content,
    )
  }

  @Test
  fun requiresContainedTemplateReferenceBeforeExtraction() {
    val questionnaire =
      questionnaire(
        """
        {
          "resourceType": "Questionnaire",
          "url": "http://example.org/Questionnaire/missing-template",
          "status": "active",
          "extension": [
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract",
              "extension": [
                {
                  "url": "template",
                  "valueReference": {
                    "reference": "#missing-template"
                  }
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
          "questionnaire": "http://example.org/Questionnaire/missing-template",
          "status": "completed"
        }
        """
      )

    val throwable =
      assertFailsWith<IllegalArgumentException> {
        TemplateExtractionEngine.extract(questionnaire, questionnaireResponse)
      }

    assertTrue(
      throwable.message.orEmpty().contains("missing-template"),
      "Expected the preflight error to mention the missing contained template reference.",
    )
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
      .map { it.jsonObject }

  private fun resourceType(entry: JsonObject): String =
    entry.getValue("resource").jsonObject.getValue("resourceType").jsonPrimitive.content
}
