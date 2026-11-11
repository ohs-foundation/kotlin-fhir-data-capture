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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
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

  @Test
  fun usesFullUrlExpressionAndFallsBackToGeneratedUrnUuid() = runTest {
    /**
     * `definitionExtract.fullUrl` is evaluated against the scope's response item. When it is
     * absent, or evaluates to nothing, the engine must still emit a unique `urn:uuid:` fullUrl.
     */
    val questionnaire =
      questionnaire(
        """
      {
        "resourceType": "Questionnaire",
        "url": "https://ohs.dev/fhir/Questionnaire/full-urls",
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
                  },
                  {
                    "url": "fullUrl",
                    "valueString": "'http://example.org/fhir/Patient/' + item.where(linkId='mrn').answer.value"
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "mrn",
                "type": "string"
              },
              {
                "linkId": "family",
                "type": "string",
                "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.family"
              }
            ]
          },
          {
            "linkId": "practitioner",
            "type": "group",
            "extension": [
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract",
                "extension": [
                  {
                    "url": "definition",
                    "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Practitioner"
                  },
                  {
                    "url": "fullUrl",
                    "valueString": "item.where(linkId='unanswered').answer.value"
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "unanswered",
                "type": "string"
              },
              {
                "linkId": "practitioner-family",
                "type": "string",
                "definition": "http://hl7.org/fhir/StructureDefinition/Practitioner#Practitioner.name.family"
              }
            ]
          },
          {
            "linkId": "observation",
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
                    "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.code.text"
                  },
                  {
                    "url": "expression",
                    "valueExpression": {
                      "language": "text/fhirpath",
                      "expression": "'Observation'"
                    }
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "status",
                "type": "choice",
                "definition": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.status"
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
        "questionnaire": "https://ohs.dev/fhir/Questionnaire/full-urls",
        "status": "completed",
        "item": [
          {
            "linkId": "patient",
            "item": [
              { "linkId": "mrn", "answer": [ { "valueString": "MRN-123" } ] },
              { "linkId": "family", "answer": [ { "valueString": "Doe" } ] }
            ]
          },
          {
            "linkId": "practitioner",
            "item": [
              { "linkId": "practitioner-family", "answer": [ { "valueString": "Roe" } ] }
            ]
          },
          {
            "linkId": "observation",
            "item": [
              {
                "linkId": "status",
                "answer": [
                  {
                    "valueCoding": {
                      "system": "http://hl7.org/fhir/observation-status",
                      "code": "final"
                    }
                  }
                ]
              }
            ]
          }
        ]
      }
      """
      )

    val entries =
      bundleEntryObjects(
        DefinitionExtractionEngine.extractByDefinition(
          questionnaire = questionnaire,
          questionnaireResponse = questionnaireResponse,
        )
      )
    assertEquals(listOf("Patient", "Practitioner", "Observation"), entries.map { resourceType(it) })

    val (patientEntry, practitionerEntry, observationEntry) = entries
    assertEquals("http://example.org/fhir/Patient/MRN-123", fullUrlOf(patientEntry))

    // Empty fullUrl expression result and missing fullUrl both fall back to a generated urn:uuid.
    assertUrnUuid(fullUrlOf(practitionerEntry))
    assertUrnUuid(fullUrlOf(observationEntry))
    assertNotEquals(fullUrlOf(practitionerEntry), fullUrlOf(observationEntry))
  }

  @Test
  fun sharesQuestionnaireLevelAllocatedIdBetweenFullUrlAndReferences() = runTest {
    /**
     * A Questionnaire-level `extractAllocateId` reserves one id for the whole extraction, so the
     * Patient's fullUrl and the Observation's subject reference must resolve to the same value.
     */
    val questionnaire =
      questionnaire(
        """
      {
        "resourceType": "Questionnaire",
        "url": "https://ohs.dev/fhir/Questionnaire/allocated-ids",
        "status": "active",
        "extension": [
          {
            "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId",
            "valueString": "patientId"
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
                  },
                  {
                    "url": "fullUrl",
                    "valueString": "%patientId"
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "family",
                "type": "string",
                "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.family"
              }
            ]
          },
          {
            "linkId": "observation",
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
                    "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.code.text"
                  },
                  {
                    "url": "expression",
                    "valueExpression": {
                      "language": "text/fhirpath",
                      "expression": "'Observation'"
                    }
                  }
                ]
              },
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                "extension": [
                  {
                    "url": "definition",
                    "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.subject.reference"
                  },
                  {
                    "url": "expression",
                    "valueExpression": {
                      "language": "text/fhirpath",
                      "expression": "%patientId"
                    }
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "status",
                "type": "choice",
                "definition": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.status"
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
        "questionnaire": "https://ohs.dev/fhir/Questionnaire/allocated-ids",
        "status": "completed",
        "item": [
          {
            "linkId": "patient",
            "item": [
              { "linkId": "family", "answer": [ { "valueString": "Doe" } ] }
            ]
          },
          {
            "linkId": "observation",
            "item": [
              {
                "linkId": "status",
                "answer": [
                  {
                    "valueCoding": {
                      "system": "http://hl7.org/fhir/observation-status",
                      "code": "final"
                    }
                  }
                ]
              }
            ]
          }
        ]
      }
      """
      )

    val entries =
      bundleEntryObjects(
        DefinitionExtractionEngine.extractByDefinition(
          questionnaire = questionnaire,
          questionnaireResponse = questionnaireResponse,
        )
      )
    val patientEntry = entries.single { resourceType(it) == "Patient" }
    val observationEntry = entries.single { resourceType(it) == "Observation" }

    val patientFullUrl = fullUrlOf(patientEntry)
    assertUrnUuid(patientFullUrl)
    assertEquals(patientFullUrl, subjectReferenceOf(observationEntry))
    assertNotEquals(patientFullUrl, fullUrlOf(observationEntry))
  }

  @Test
  fun allocatesDistinctIdsPerRepeatedGroupInstanceAndSharesThemWithNestedScopes() = runTest {
    /**
     * An item-level `extractAllocateId` on a repeating group is allocated once per repetition. Each
     * repetition's Condition must get its own fullUrl, the nested Observation in the same
     * repetition must reference that Condition, and all of them must still share the
     * Questionnaire-level Patient id.
     */
    val questionnaire =
      questionnaire(
        """
      {
        "resourceType": "Questionnaire",
        "url": "https://ohs.dev/fhir/Questionnaire/repeated-allocated-ids",
        "status": "active",
        "extension": [
          {
            "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId",
            "valueString": "patientId"
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
                  },
                  {
                    "url": "fullUrl",
                    "valueString": "%patientId"
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "family",
                "type": "string",
                "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.family"
              }
            ]
          },
          {
            "linkId": "condition",
            "type": "group",
            "repeats": true,
            "extension": [
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId",
                "valueString": "conditionId"
              },
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract",
                "extension": [
                  {
                    "url": "definition",
                    "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Condition"
                  },
                  {
                    "url": "fullUrl",
                    "valueString": "%conditionId"
                  }
                ]
              },
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                "extension": [
                  {
                    "url": "definition",
                    "valueUri": "http://hl7.org/fhir/StructureDefinition/Condition#Condition.subject.reference"
                  },
                  {
                    "url": "expression",
                    "valueExpression": {
                      "language": "text/fhirpath",
                      "expression": "%patientId"
                    }
                  }
                ]
              },
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                "extension": [
                  {
                    "url": "definition",
                    "valueUri": "http://hl7.org/fhir/StructureDefinition/Condition#Condition.identifier.value"
                  },
                  {
                    "url": "expression",
                    "valueExpression": {
                      "language": "text/fhirpath",
                      "expression": "%conditionId"
                    }
                  }
                ]
              }
            ],
            "item": [
              {
                "linkId": "condition-text",
                "type": "string",
                "definition": "http://hl7.org/fhir/StructureDefinition/Condition#Condition.code.text"
              },
              {
                "linkId": "severity-observation",
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
                        "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.code.text"
                      },
                      {
                        "url": "expression",
                        "valueExpression": {
                          "language": "text/fhirpath",
                          "expression": "'Observation'"
                        }
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
                        "url": "expression",
                        "valueExpression": {
                          "language": "text/fhirpath",
                          "expression": "'final'"
                        }
                      }
                    ]
                  },
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                    "extension": [
                      {
                        "url": "definition",
                        "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.focus.reference"
                      },
                      {
                        "url": "expression",
                        "valueExpression": {
                          "language": "text/fhirpath",
                          "expression": "%conditionId"
                        }
                      }
                    ]
                  },
                  {
                    "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue",
                    "extension": [
                      {
                        "url": "definition",
                        "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.subject.reference"
                      },
                      {
                        "url": "expression",
                        "valueExpression": {
                          "language": "text/fhirpath",
                          "expression": "%patientId"
                        }
                      }
                    ]
                  }
                ],
                "item": [
                  {
                    "linkId": "severity",
                    "type": "string",
                    "definition": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.valueString"
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
        "questionnaire": "https://ohs.dev/fhir/Questionnaire/repeated-allocated-ids",
        "status": "completed",
        "item": [
          {
            "linkId": "patient",
            "item": [
              { "linkId": "family", "answer": [ { "valueString": "Doe" } ] }
            ]
          },
          {
            "linkId": "condition",
            "item": [
              { "linkId": "condition-text", "answer": [ { "valueString": "Asthma" } ] },
              {
                "linkId": "severity-observation",
                "item": [
                  { "linkId": "severity", "answer": [ { "valueString": "mild" } ] }
                ]
              }
            ]
          },
          {
            "linkId": "condition",
            "item": [
              { "linkId": "condition-text", "answer": [ { "valueString": "Diabetes" } ] },
              {
                "linkId": "severity-observation",
                "item": [
                  { "linkId": "severity", "answer": [ { "valueString": "severe" } ] }
                ]
              }
            ]
          }
        ]
      }
      """
      )

    val entries =
      bundleEntryObjects(
        DefinitionExtractionEngine.extractByDefinition(
          questionnaire = questionnaire,
          questionnaireResponse = questionnaireResponse,
        )
      )
    val patientFullUrl = fullUrlOf(entries.single { resourceType(it) == "Patient" })
    assertUrnUuid(patientFullUrl)

    val conditionEntries = entries.filter { resourceType(it) == "Condition" }
    val observationEntries = entries.filter { resourceType(it) == "Observation" }
    assertEquals(2, conditionEntries.size)
    assertEquals(2, observationEntries.size)

    assertEquals(
      listOf("Asthma", "Diabetes"),
      conditionEntries.map {
        resourceOf(it).getValue("code").jsonObject.getValue("text").jsonPrimitive.content
      },
    )

    val conditionFullUrls = conditionEntries.map(::fullUrlOf)
    conditionFullUrls.forEach(::assertUrnUuid)
    assertEquals(2, conditionFullUrls.toSet().size, "Each repetition needs its own allocated id")
    assertTrue(patientFullUrl !in conditionFullUrls)

    // A directive on the repeating group itself sees the same allocated id as its entry fullUrl.
    assertEquals(
      conditionFullUrls,
      conditionEntries.map {
        resourceOf(it)
          .getValue("identifier")
          .jsonArray
          .single()
          .jsonObject
          .getValue("value")
          .jsonPrimitive
          .content
      },
    )

    // Every Condition and Observation references the single Questionnaire-level Patient id.
    (conditionEntries + observationEntries).forEach {
      assertEquals(patientFullUrl, subjectReferenceOf(it))
    }

    // Each Observation focuses on the Condition allocated in its own repetition.
    assertEquals(
      listOf("mild", "severe"),
      observationEntries.map { resourceOf(it).getValue("valueString").jsonPrimitive.content },
    )
    assertEquals(
      conditionFullUrls,
      observationEntries.map {
        resourceOf(it)
          .getValue("focus")
          .jsonArray
          .single()
          .jsonObject
          .getValue("reference")
          .jsonPrimitive
          .content
      },
    )
  }

  private fun fullUrlOf(entry: JsonObject): String = entry.getValue("fullUrl").jsonPrimitive.content

  private fun subjectReferenceOf(entry: JsonObject): String =
    resourceOf(entry).getValue("subject").jsonObject.getValue("reference").jsonPrimitive.content

  private fun assertUrnUuid(value: String) {
    assertTrue(
      Regex("^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        .matches(value),
      "Expected a urn:uuid fullUrl but was '$value'",
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
      .map { entry -> entry.jsonObject }

  private fun resourceType(entry: JsonObject): String =
    resourceOf(entry).getValue("resourceType").jsonPrimitive.content

  private fun resourceOf(entry: JsonObject): JsonObject = entry.getValue("resource").jsonObject
}
