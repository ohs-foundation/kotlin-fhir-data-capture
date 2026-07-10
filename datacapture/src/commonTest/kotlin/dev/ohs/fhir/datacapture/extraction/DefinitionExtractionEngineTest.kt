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
import dev.ohs.fhir.model.r4.Patient
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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
  fun extractsDemographicsUsingTheExactSpecQuestionnaire() = runTest {
    val questionnaire = questionnaire(demographicsQuestionnaireJson)
    val questionnaireResponse =
      questionnaireResponse(
        """
        {
          "resourceType": "QuestionnaireResponse",
          "questionnaire": "http://hl7.org/fhir/uv/sdc/Questionnaire/demographics",
          "status": "completed",
          "item": [
            {
              "linkId": "patient.id",
              "answer": [
                {
                  "valueString": "patient-123"
                }
              ]
            },
            {
              "linkId": "patient.birthDate",
              "answer": [
                {
                  "valueDate": "1980-01-02"
                }
              ]
            },
            {
              "linkId": "patient.name",
              "item": [
                {
                  "linkId": "patient.name.family",
                  "answer": [
                    {
                      "valueString": "Doe"
                    }
                  ]
                },
                {
                  "linkId": "patient.name.given",
                  "answer": [
                    {
                      "valueString": "Jane"
                    },
                    {
                      "valueString": "Alex"
                    }
                  ]
                }
              ]
            },
            {
              "linkId": "patient.name",
              "item": [
                {
                  "linkId": "patient.name.family",
                  "answer": [
                    {
                      "valueString": "Smith"
                    }
                  ]
                },
                {
                  "linkId": "patient.name.given",
                  "answer": [
                    {
                      "valueString": "Sam"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """
      )

    assertTrue(DefinitionExtractionEngine.canExtract(questionnaire))

    val result =
      DefinitionExtractionEngine.extractByDefinition(
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
      )

    assertEquals(Bundle.BundleType.Transaction, result.type.value)
    assertEquals(1, result.entry.size)

    val entryObjects = bundleEntryObjects(result)
    val patientEntry = entryObjects.single { entry -> resourceType(entry) == "Patient" }

    assertTrue(patientEntry.getValue("fullUrl").jsonPrimitive.content.startsWith("urn:uuid:"))
    assertEquals(
      "PUT",
      requestOf(patientEntry).getValue("method").jsonPrimitive.content.uppercase(),
    )
    assertEquals(
      "Patient/patient-123",
      requestOf(patientEntry).getValue("url").jsonPrimitive.content,
    )

    val patientResource = resourceOf(patientEntry)
    assertEquals("1980-01-02", patientResource.getValue("birthDate").jsonPrimitive.content)
    val patientNames = patientResource.getValue("name").jsonArray.map { name -> name.jsonObject }
    assertEquals(2, patientNames.size)
    assertEquals("Doe", patientNames[0].getValue("family").jsonPrimitive.content)
    assertEquals(
      listOf("Jane", "Alex"),
      patientNames[0].getValue("given").jsonArray.map { value -> value.jsonPrimitive.content },
    )
    assertEquals("Smith", patientNames[1].getValue("family").jsonPrimitive.content)
    assertEquals(
      listOf("Sam"),
      patientNames[1].getValue("given").jsonArray.map { value -> value.jsonPrimitive.content },
    )
  }

  @Test
  fun extractsComplexDefinitionExampleUsingTheExactSpecQuestionnaire() = runTest {
    val questionnaire = questionnaire(complexQuestionnaireJson)
    val authoredAt = "2026-06-26T10:15:30+03:00"
    val questionnaireResponse =
      questionnaireResponse(
        """
        {
          "resourceType": "QuestionnaireResponse",
          "id": "qr-complex-1",
          "questionnaire": "http://hl7.org/fhir/uv/sdc/Questionnaire/extract-complex-defn3",
          "status": "completed",
          "authored": "$authoredAt",
          "author": {
            "reference": "Practitioner/practitioner-1"
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
                      "valueDate": "1980-01-02"
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
                      "valueString": "John Doe"
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
                        "display": "Emergency Contact"
                      }
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
                      "valueString": "Mary Doe"
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
                },
                {
                  "linkId": "weight",
                  "answer": [
                    {
                      "valueDecimal": 68.5
                    }
                  ]
                },
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
      )

    assertTrue(DefinitionExtractionEngine.canExtract(questionnaire))

    val result =
      DefinitionExtractionEngine.extractByDefinition(
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
      )

    assertEquals(Bundle.BundleType.Transaction, result.type.value)
    assertEquals(5, result.entry.size)

    val entryObjects = bundleEntryObjects(result)
    val patientEntry = entryObjects.single { entry -> resourceType(entry) == "Patient" }
    val relatedEntries = entryObjects.filter { entry -> resourceType(entry) == "RelatedPerson" }
    val observationEntries = entryObjects.filter { entry -> resourceType(entry) == "Observation" }

    val patientFullUrl = patientEntry.getValue("fullUrl").jsonPrimitive.content
    assertTrue(patientFullUrl.startsWith("urn:uuid:"))
    assertEquals(
      "POST",
      requestOf(patientEntry).getValue("method").jsonPrimitive.content.uppercase(),
    )
    assertEquals("Patient", requestOf(patientEntry).getValue("url").jsonPrimitive.content)

    val patientResource = resourceOf(patientEntry)
    val patientName = patientResource.getValue("name").jsonArray.single().jsonObject
    assertEquals("Jane Alex Doe", patientName.getValue("text").jsonPrimitive.content)
    assertEquals("Doe", patientName.getValue("family").jsonPrimitive.content)
    assertEquals(
      listOf("Jane", "Alex"),
      patientName.getValue("given").jsonArray.map { value -> value.jsonPrimitive.content },
    )
    assertEquals("female", patientResource.getValue("gender").jsonPrimitive.content)
    assertEquals("1980-01-02", patientResource.getValue("birthDate").jsonPrimitive.content)

    val identifier = patientResource.getValue("identifier").jsonArray.single().jsonObject
    assertEquals("http://example.org/nhio", identifier.getValue("system").jsonPrimitive.content)
    assertEquals("8003601234567890", identifier.getValue("value").jsonPrimitive.content)
    assertEquals(
      "National Identifier (IHI)",
      identifier.getValue("type").jsonObject.getValue("text").jsonPrimitive.content,
    )

    assertEquals(2, relatedEntries.size)
    val relationshipsByName =
      relatedEntries.associate { entry ->
        val resource = resourceOf(entry)
        val nameText =
          resource
            .getValue("name")
            .jsonArray
            .single()
            .jsonObject
            .getValue("text")
            .jsonPrimitive
            .content
        val relationshipCode =
          resource
            .getValue("relationship")
            .jsonArray
            .single()
            .jsonObject
            .getValue("coding")
            .jsonArray
            .single()
            .jsonObject
            .getValue("code")
            .jsonPrimitive
            .content

        assertEquals("POST", requestOf(entry).getValue("method").jsonPrimitive.content.uppercase())
        assertEquals("RelatedPerson", requestOf(entry).getValue("url").jsonPrimitive.content)
        assertEquals(
          patientFullUrl,
          resource.getValue("patient").jsonObject.getValue("reference").jsonPrimitive.content,
        )

        nameText to relationshipCode
      }
    assertEquals(mapOf("John Doe" to "C", "Mary Doe" to "N"), relationshipsByName)

    assertEquals(2, observationEntries.size)
    val observationsByCode =
      observationEntries.associateBy { entry ->
        resourceOf(entry)
          .getValue("code")
          .jsonObject
          .getValue("coding")
          .jsonArray
          .single()
          .jsonObject
          .getValue("code")
          .jsonPrimitive
          .content
      }

    val heightObservation = resourceOf(observationsByCode.getValue("8302-2"))
    assertEquals("final", heightObservation.getValue("status").jsonPrimitive.content)
    assertEquals(
      "vital-signs",
      heightObservation
        .getValue("category")
        .jsonArray
        .single()
        .jsonObject
        .getValue("coding")
        .jsonArray
        .single()
        .jsonObject
        .getValue("code")
        .jsonPrimitive
        .content,
    )
    assertEquals(
      patientFullUrl,
      heightObservation.getValue("subject").jsonObject.getValue("reference").jsonPrimitive.content,
    )
    assertEquals(authoredAt, heightObservation.getValue("effectiveDateTime").jsonPrimitive.content)
    assertEquals(authoredAt, heightObservation.getValue("issued").jsonPrimitive.content)
    assertEquals(
      "Practitioner/practitioner-1",
      heightObservation
        .getValue("performer")
        .jsonArray
        .single()
        .jsonObject
        .getValue("reference")
        .jsonPrimitive
        .content,
    )
    assertEquals(
      "QuestionnaireResponse/qr-complex-1",
      heightObservation
        .getValue("derivedFrom")
        .jsonArray
        .single()
        .jsonObject
        .getValue("reference")
        .jsonPrimitive
        .content,
    )
    assertEquals(
      "1.75",
      heightObservation.getValue("valueQuantity").jsonObject.getValue("value").jsonPrimitive.content,
    )
    assertEquals(
      "m",
      heightObservation.getValue("valueQuantity").jsonObject.getValue("unit").jsonPrimitive.content,
    )

    val weightObservation = resourceOf(observationsByCode.getValue("29463-7"))
    assertEquals(
      "68.5",
      weightObservation.getValue("valueQuantity").jsonObject.getValue("value").jsonPrimitive.content,
    )
    assertEquals(
      "kg",
      weightObservation.getValue("valueQuantity").jsonObject.getValue("unit").jsonPrimitive.content,
    )
    assertEquals(
      patientFullUrl,
      weightObservation.getValue("subject").jsonObject.getValue("reference").jsonPrimitive.content,
    )
    assertTrue(observationsByCode["sigmoidoscopy-complication"] == null)
  }

  @Test
  fun extractsEncounterToShowSupportIsNotLimitedToDemoResourceTypes() = runTest {
    val questionnaire =
      questionnaire(
        """
        {
          "resourceType": "Questionnaire",
          "url": "http://example.org/Questionnaire/encounter-extract",
          "status": "draft",
          "extension": [
            {
              "extension": [
                {
                  "url": "definition",
                  "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Encounter"
                }
              ],
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract"
            },
            {
              "extension": [
                {
                  "url": "definition",
                  "valueUri": "http://hl7.org/fhir/StructureDefinition/Encounter#Encounter.status"
                },
                {
                  "url": "fixed-value",
                  "valueCode": "finished"
                }
              ],
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
            },
            {
              "extension": [
                {
                  "url": "definition",
                  "valueUri": "http://hl7.org/fhir/StructureDefinition/Encounter#Encounter.class"
                },
                {
                  "url": "fixed-value",
                  "valueCoding": {
                    "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                    "code": "AMB",
                    "display": "ambulatory"
                  }
                }
              ],
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
            }
          ],
          "item": [
            {
              "linkId": "subject",
              "definition": "http://hl7.org/fhir/StructureDefinition/Encounter#Encounter.subject.reference",
              "text": "Encounter subject",
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
          "questionnaire": "http://example.org/Questionnaire/encounter-extract",
          "status": "completed",
          "item": [
            {
              "linkId": "subject",
              "answer": [
                {
                  "valueString": "Patient/patient-enc-1"
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

    assertEquals(1, result.entry.size)
    val encounterEntry = bundleEntryObjects(result).single()
    assertEquals("Encounter", resourceType(encounterEntry))
    assertEquals("finished", resourceOf(encounterEntry).getValue("status").jsonPrimitive.content)
    assertEquals(
      "AMB",
      resourceOf(encounterEntry).getValue("class").jsonObject.getValue("code").jsonPrimitive.content,
    )
    assertEquals(
      "Patient/patient-enc-1",
      resourceOf(encounterEntry)
        .getValue("subject")
        .jsonObject
        .getValue("reference")
        .jsonPrimitive
        .content,
    )
  }

  @Test
  fun skipsBrokenResourceAndReturnsTheValidOnes() = runTest {
    val questionnaire =
      questionnaire(
        """
        {
          "resourceType": "Questionnaire",
          "url": "http://example.org/fhir/Questionnaire/partial-definition-extract",
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
                  "linkId": "patient-name",
                  "type": "string",
                  "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.text"
                }
              ]
            },
            {
              "linkId": "broken-observation",
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
                }
              ],
              "item": [
                {
                  "linkId": "bad-field",
                  "type": "string",
                  "definition": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.notARealField"
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
          "questionnaire": "http://example.org/fhir/Questionnaire/partial-definition-extract",
          "status": "completed",
          "item": [
            {
              "linkId": "patient",
              "item": [
                {
                  "linkId": "patient-name",
                  "answer": [
                    {
                      "valueString": "Recovered Patient"
                    }
                  ]
                }
              ]
            },
            {
              "linkId": "broken-observation",
              "item": [
                {
                  "linkId": "bad-field",
                  "answer": [
                    {
                      "valueString": "This resource should be skipped"
                    }
                  ]
                }
              ]
            }
          ]
        }
        """
      )

    val bundle =
      DefinitionExtractionEngine.extractByDefinition(questionnaire, questionnaireResponse)

    assertEquals(Bundle.BundleType.Transaction, bundle.type.value)
    assertEquals(1, bundle.entry.size)

    val resource = bundle.entry.single().resource
    assertIs<Patient>(resource)
    assertEquals("Recovered Patient", resource.name.single().text?.value)
    assertTrue(bundle.entry.single().fullUrl?.value?.startsWith("urn:uuid:") == true)
  }

  private val demographicsQuestionnaireJson: String =
    """
    {
      "resourceType": "Questionnaire",
      "id": "demographics",
      "meta": {
        "profile": [
          "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extr-defn"
        ]
      },
      "language": "en",
      "text": {
        "status": "extensions",
        "div": "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p class=\"res-header-id\"><b>Generated Narrative: Questionnaire demographics</b></p><a name=\"demographics\"> </a><a name=\"hcdemographics\"> </a><div style=\"display: inline-block; background-color: #d9e0e7; padding: 6px; margin: 4px; border: 1px solid #8da1b4; border-radius: 5px; line-height: 60%\"><p style=\"margin-bottom: 0px\"/><p style=\"margin-bottom: 0px\">Profile: <a href=\"StructureDefinition-sdc-questionnaire-extr-defn.html\">Extractable Questionnaire - Definition</a></p></div><table border=\"1\" cellpadding=\"0\" cellspacing=\"0\" style=\"border: 1px #F0F0F0 solid; font-size: 11px; font-family: verdana; vertical-align: top;\"><tr style=\"border: 2px #F0F0F0 solid; font-size: 11px; font-family: verdana; vertical-align: top\"><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"The linkID for the item\">LinkID</a></th><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Text for the item\">Text</a></th><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Minimum and Maximum # of times the item can appear in the instance\">Cardinality</a></th><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"The type of the item\">Type</a></th><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Other attributes of the item\">Flags</a></th><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Additional information about the item\">Description &amp; Constraints</a><span style=\"float: right\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Legend for this format\"><img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH3goXBCwdPqAP0wAAAldJREFUOMuNk0tIlFEYhp9z/vE2jHkhxXA0zJCMitrUQlq4lnSltEqCFhFG2MJFhIvIFpkEWaTQqjaWZRkp0g26URZkTpbaaOJkDqk10szoODP//7XIMUe0elcfnPd9zsfLOYplGrpRwZaqTtw3K7PtGem7Q6FoidbGgqHVy/HRb669R+56zx7eRV1L31JGxYbBtjKK93cxeqfyQHbehkZbUkK20goELEuIzEd+dHS+qz/Y8PTSif0FnGkbiwcAjHaU1+QWOptFiyCLp/LnKptpqIuXHx6rbR26kJcBX3yLgBfnd7CxwJmflpP2wUg0HIAoUUpZBmKzELGWcN8nAr6Gpu7tLU/CkwAaoKTWRSQyt89Q8w6J+oVQkKnBoblH7V0PPvUOvDYXfopE/SJmALsxnVm6LbkotrUtNowMeIrVrBcBpaMmdS0j9df7abpSuy7HWehwJdt1lhVwi/J58U5beXGAF6c3UXLycw1wdFklArBn87xdh0ZsZtArghBdAA3+OEDVubG4UEzP6x1FOWneHh2VDAHBAt80IbdXDcesNoCvs3E5AFyNSU5nbrDPZpcUEQQTFZiEVx+51fxMhhyJEAgvlriadIJZZksRuwBYMOPBbO3hePVVqgEJhFeUuFLhIPkRP6BQLIBrmMenujm/3g4zc398awIe90Zb5A1vREALqneMcYgP/xVQWlG+Ncu5vgwwlaUNx+3799rfe96u9K0JSDXcOzOTJg4B6IgmXfsygc7/Bvg9g9E58/cDVmGIBOP/zT8Bz1zqWqpbXIsd0O9hajXfL6u4BaOS6SeWAAAAAElFTkSuQmCC\" alt=\"doco\" style=\"background-color: inherit\"/></a></span></th></tr><tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck1.png)\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon_q_root.gif\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"QuestionnaireRoot\" class=\"hierarchy\"/> DemographicExample</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">A sample questionnaire using context-based population and extraction</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Questionnaire</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">http://hl7.org/fhir/uv/sdc/Questionnaire/demographics#4.0.0-ci-build</td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck10.png)\" id=\"item.patient.id\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> patient.id</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">(internal use)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/questionnaire-definitions.html#Questionnaire.item.readOnly\" title=\"Is Read Only\"><img src=\"icon-qi-readonly.png\" alt=\"icon\"/></a><img src=\"icon-qi-readonly.png\" alt=\"icon\"/><a href=\"https://hl7.org/fhir/R4/extension-questionnaire-hidden.html\" title=\"Is a hidden item\"><img src=\"icon-qi-hidden.png\" alt=\"icon\"/></a><img src=\"icon-qi-hidden.png\" alt=\"icon\"/></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.id\">Patient.id</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck10.png)\" id=\"item.patient.birthDate\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-date.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"date\" class=\"hierarchy\"/> patient.birthDate</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Date of birth</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">1..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-date\">date</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.birthDate\">Patient.birthDate</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck11.png)\" id=\"item.patient.name\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-group.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"group\" class=\"hierarchy\"/> patient.name</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Name(s)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..*</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-group\">group</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.name\">Patient.name</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck110.png)\" id=\"item.patient.name.family\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> patient.name.family</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Family name</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">1..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.name.family\">Patient.name.family</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck100.png)\" id=\"item.patient.name.given\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin_end.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> patient.name.given</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Given name(s)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">1..*</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.name.given\">Patient.name.given</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck01.png)\" id=\"item.relative\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin_end.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-group.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"group\" class=\"hierarchy\"/> relative</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Relatives, caregivers and other personal relationships</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..*</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-group\">group</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck010.png)\" id=\"item.relative.id\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_blank.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> relative.id</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">(internal use)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/questionnaire-definitions.html#Questionnaire.item.readOnly\" title=\"Is Read Only\"><img src=\"icon-qi-readonly.png\" alt=\"icon\"/></a><img src=\"icon-qi-readonly.png\" alt=\"icon\"/><a href=\"https://hl7.org/fhir/R4/extension-questionnaire-hidden.html\" title=\"Is a hidden item\"><img src=\"icon-qi-hidden.png\" alt=\"icon\"/></a><img src=\"icon-qi-hidden.png\" alt=\"icon\"/></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/relatedperson.html#RelatedPerson.id\">RelatedPerson.id</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck010.png)\" id=\"item.relative.relationship\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_blank.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-coding.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"coding\" class=\"hierarchy\"/> relative.relationship</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Relationship(s)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">1..*</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-choice\">choice</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/relatedperson.html#RelatedPerson.relationship\">RelatedPerson.relationship</a><br/>Value Set: <a href=\"http://hl7.org/fhir/R4/valueset-relatedperson-relationshiptype.html\">Patient relationship type</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck001.png)\" id=\"item.relative.name\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_blank.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin_end.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-group.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"group\" class=\"hierarchy\"/> relative.name</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Name(s)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..*</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-group\">group</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/relatedperson.html#RelatedPerson.name\">RelatedPerson.name</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck0010.png)\" id=\"item.relative.name.family\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_blank.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_blank.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> relative.name.family</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Family name</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">1..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/relatedperson.html#RelatedPerson.name.family\">RelatedPerson.name.family</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck0000.png)\" id=\"item.relative.name.given\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_blank.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_blank.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin_end.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> relative.name.given</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Given name(s)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">1..*</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/relatedperson.html#RelatedPerson.name.given\">RelatedPerson.name.given</a></td></tr>\r\n<tr><td colspan=\"6\" class=\"hierarchy\"><br/><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Legend for this format\"><img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH3goXBCwdPqAP0wAAAldJREFUOMuNk0tIlFEYhp9z/vE2jHkhxXA0zJCMitrUQlq4lnSltEqCFhFG2MJFhIvIFpkEWaTQqjaWZRkp0g26URZkTpbaaOJkDqk10szoODP//7XIMUe0elcfnPd9zsfLOYplGrpRwZaqTtw3K7PtGem7Q6FoidbGgqHVy/HRb669R+56zx7eRV1L31JGxYbBtjKK93cxeqfyQHbehkZbUkK20goELEuIzEd+dHS+qz/Y8PTSif0FnGkbiwcAjHaU1+QWOptFiyCLp/LnKptpqIuXHx6rbR26kJcBX3yLgBfnd7CxwJmflpP2wUg0HIAoUUpZBmKzELGWcN8nAr6Gpu7tLU/CkwAaoKTWRSQyt89Q8w6J+oVQkKnBoblH7V0PPvUOvDYXfopE/SJmALsxnVm6LbkotrUtNowMeIrVrBcBpaMmdS0j9df7abpSuy7HWehwJdt1lhVwi/J58U5beXGAF6c3UXLycw1wdFklArBn87xdh0ZsZtArghBdAA3+OEDVubG4UEzP6x1FOWneHh2VDAHBAt80IbdXDcesNoCvs3E5AFyNSU5nbrDPZpcUEQQTFZiEVx+51fxMhhyJEAgvlriadIJZZksRuwBYMOPBbO3hePVVqgEJhFeUuFLhIPkRP6BQLIBrmMenujm/3g4zc398awIe90Zb5A1vREALqneMcYgP/xVQWlG+Ncu5vgwwlaUNx+3799rfe96u9K0JSDXcOzOTJg4B6IgmXfsygc7/Bvg9g9E58/cDVmGIBOP/zT8Bz1zqWqpbXIsd0O9hajXfL6u4BaOS6SeWAAAAAElFTkSuQmCC\" alt=\"doco\" style=\"background-color: inherit\"/> Documentation for this format</a></td></tr></table></div>"
      },
      "extension": [
        {
          "url": "http://hl7.org/fhir/StructureDefinition/artifact-versionAlgorithm",
          "valueCoding": {
            "system": "http://hl7.org/fhir/version-algorithm",
            "code": "semver"
          }
        },
        {
          "extension": [
            {
              "url": "name",
              "valueCoding": {
                "system": "http://hl7.org/fhir/uv/sdc/CodeSystem/launchContext",
                "code": "patient"
              }
            },
            {
              "url": "type",
              "valueCode": "Patient"
            }
          ],
          "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-launchContext"
        },
        {
          "extension": [
            {
              "url": "definition",
              "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Patient"
            }
          ],
          "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract"
        }
      ],
      "url": "http://hl7.org/fhir/uv/sdc/Questionnaire/demographics",
      "identifier": [
        {
          "system": "urn:ietf:rfc:3986",
          "value": "urn:oid:2.16.840.1.113883.4.642.40.17.35.3"
        }
      ],
      "version": "4.0.0-ci-build",
      "name": "DemographicExample",
      "title": "Questionnaire - Demographics Example",
      "status": "active",
      "experimental": true,
      "subjectType": [
        "Patient"
      ],
      "date": "2026-06-11T04:39:12+00:00",
      "publisher": "HL7 International / FHIR Infrastructure",
      "contact": [
        {
          "name": "HL7 International / FHIR Infrastructure",
          "telecom": [
            {
              "system": "url",
              "value": "http://www.hl7.org/Special/committees/fiwg"
            }
          ]
        },
        {
          "telecom": [
            {
              "system": "url",
              "value": "http://www.hl7.org/Special/committees/fiwg"
            }
          ]
        }
      ],
      "description": "A sample questionnaire using context-based population and extraction",
      "jurisdiction": [
        {
          "coding": [
            {
              "system": "http://unstats.un.org/unsd/methods/m49/m49.htm",
              "code": "001",
              "display": "World"
            }
          ]
        }
      ],
      "item": [
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/questionnaire-hidden",
              "valueBoolean": true
            },
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
              "valueExpression": {
                "language": "text/fhirpath",
                "expression": "%patient.id"
              }
            }
          ],
          "linkId": "patient.id",
          "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.id",
          "text": "(internal use)",
          "type": "string",
          "readOnly": true
        },
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
              "valueExpression": {
                "language": "text/fhirpath",
                "expression": "%patient.birthDate"
              }
            }
          ],
          "linkId": "patient.birthDate",
          "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.birthDate",
          "text": "Date of birth",
          "type": "date",
          "required": true
        },
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-itemPopulationContext",
              "valueExpression": {
                "name": "patientName",
                "language": "text/fhirpath",
                "expression": "%patient.name"
              }
            }
          ],
          "linkId": "patient.name",
          "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name",
          "text": "Name(s)",
          "type": "group",
          "repeats": true,
          "item": [
            {
              "extension": [
                {
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
                  "valueExpression": {
                    "language": "text/fhirpath",
                    "expression": "%patientName.family"
                  }
                }
              ],
              "linkId": "patient.name.family",
              "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.family",
              "text": "Family name",
              "type": "string",
              "required": true
            },
            {
              "extension": [
                {
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
                  "valueExpression": {
                    "language": "text/fhirpath",
                    "expression": "%patientName.given"
                  }
                }
              ],
              "linkId": "patient.name.given",
              "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.given",
              "text": "Given name(s)",
              "type": "string",
              "required": true,
              "repeats": true
            }
          ]
        },
        {
          "extension": [
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-itemPopulationContext",
              "valueExpression": {
                "name": "relative",
                "language": "application/x-fhir-query",
                "expression": "RelatedPerson?patient={{%patient.id}}"
              }
            },
            {
              "extension": [
                {
                  "url": "definition",
                  "valueCanonical": "http://hl7.org/fhir/StructureDefinition/RelatedPerson"
                }
              ],
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract"
            }
          ],
          "linkId": "relative",
          "text": "Relatives, caregivers and other personal relationships",
          "type": "group",
          "repeats": true,
          "item": [
            {
              "extension": [
                {
                  "url": "http://hl7.org/fhir/StructureDefinition/questionnaire-hidden",
                  "valueBoolean": true
                },
                {
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
                  "valueExpression": {
                    "language": "text/fhirpath",
                    "expression": "%relative.id"
                  }
                }
              ],
              "linkId": "relative.id",
              "definition": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.id",
              "text": "(internal use)",
              "type": "string",
              "readOnly": true
            },
            {
              "extension": [
                {
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
                  "valueExpression": {
                    "language": "text/fhirpath",
                    "expression": "%relative.relationship"
                  }
                }
              ],
              "linkId": "relative.relationship",
              "definition": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.relationship",
              "text": "Relationship(s)",
              "type": "choice",
              "required": true,
              "repeats": true,
              "answerValueSet": "http://hl7.org/fhir/ValueSet/relatedperson-relationshiptype"
            },
            {
              "extension": [
                {
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-itemPopulationContext",
                  "valueExpression": {
                    "name": "relativeName",
                    "language": "text/fhirpath",
                    "expression": "%relative.name"
                  }
                }
              ],
              "linkId": "relative.name",
              "definition": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.name",
              "text": "Name(s)",
              "type": "group",
              "repeats": true,
              "item": [
                {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
                      "valueExpression": {
                        "language": "text/fhirpath",
                        "expression": "%relativeName.family"
                      }
                    }
                  ],
                  "linkId": "relative.name.family",
                  "definition": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.name.family",
                  "text": "Family name",
                  "type": "string",
                  "required": true
                },
                {
                  "extension": [
                    {
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
                      "valueExpression": {
                        "language": "text/fhirpath",
                        "expression": "%relativeName.given"
                      }
                    }
                  ],
                  "linkId": "relative.name.given",
                  "definition": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.name.given",
                  "text": "Given name(s)",
                  "type": "string",
                  "required": true,
                  "repeats": true
                }
              ]
            }
          ]
        }
      ]
    }
    """
      .trimIndent()

  private val complexQuestionnaireJson: String =
    """
        {
          "resourceType" : "Questionnaire",
          "id" : "extract-complex-defn3",
          "meta" : {
            "profile" : ["http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extr-defn"]
          },
          "language" : "en",
          "text" : {
            "status" : "extensions",
            "div" : "<div xmlns=\"http://www.w3.org/1999/xhtml\"><p class=\"res-header-id\"><b>Generated Narrative: Questionnaire extract-complex-defn3</b></p><a name=\"extract-complex-defn3\"> </a><a name=\"hcextract-complex-defn3\"> </a><div style=\"display: inline-block; background-color: #d9e0e7; padding: 6px; margin: 4px; border: 1px solid #8da1b4; border-radius: 5px; line-height: 60%\"><p style=\"margin-bottom: 0px\"/><p style=\"margin-bottom: 0px\">Profile: <a href=\"StructureDefinition-sdc-questionnaire-extr-defn.html\">Extractable Questionnaire - Definition</a></p></div><table border=\"1\" cellpadding=\"0\" cellspacing=\"0\" style=\"border: 1px #F0F0F0 solid; font-size: 11px; font-family: verdana; vertical-align: top;\"><tr style=\"border: 2px #F0F0F0 solid; font-size: 11px; font-family: verdana; vertical-align: top\"><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"The linkID for the item\">LinkID</a></th><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Text for the item\">Text</a></th><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Minimum and Maximum # of times the item can appear in the instance\">Cardinality</a></th><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"The type of the item\">Type</a></th><th style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; padding-top: 3px; padding-bottom: 3px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Additional information about the item\">Description &amp; Constraints</a><span style=\"float: right\"><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Legend for this format\"><img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH3goXBCwdPqAP0wAAAldJREFUOMuNk0tIlFEYhp9z/vE2jHkhxXA0zJCMitrUQlq4lnSltEqCFhFG2MJFhIvIFpkEWaTQqjaWZRkp0g26URZkTpbaaOJkDqk10szoODP//7XIMUe0elcfnPd9zsfLOYplGrpRwZaqTtw3K7PtGem7Q6FoidbGgqHVy/HRb669R+56zx7eRV1L31JGxYbBtjKK93cxeqfyQHbehkZbUkK20goELEuIzEd+dHS+qz/Y8PTSif0FnGkbiwcAjHaU1+QWOptFiyCLp/LnKptpqIuXHx6rbR26kJcBX3yLgBfnd7CxwJmflpP2wUg0HIAoUUpZBmKzELGWcN8nAr6Gpu7tLU/CkwAaoKTWRSQyt89Q8w6J+oVQkKnBoblH7V0PPvUOvDYXfopE/SJmALsxnVm6LbkotrUtNowMeIrVrBcBpaMmdS0j9df7abpSuy7HWehwJdt1lhVwi/J58U5beXGAF6c3UXLycw1wdFklArBn87xdh0ZsZtArghBdAA3+OEDVubG4UEzP6x1FOWneHh2VDAHBAt80IbdXDcesNoCvs3E5AFyNSU5nbrDPZpcUEQQTFZiEVx+51fxMhhyJEAgvlriadIJZZksRuwBYMOPBbO3hePVVqgEJhFeUuFLhIPkRP6BQLIBrmMenujm/3g4zc398awIe90Zb5A1vREALqneMcYgP/xVQWlG+Ncu5vgwwlaUNx+3799rfe96u9K0JSDXcOzOTJg4B6IgmXfsygc7/Bvg9g9E58/cDVmGIBOP/zT8Bz1zqWqpbXIsd0O9hajXfL6u4BaOS6SeWAAAAAElFTkSuQmCC\" alt=\"doco\" style=\"background-color: inherit\"/></a></span></th></tr><tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck1.png)\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon_q_root.gif\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"QuestionnaireRoot\" class=\"hierarchy\"/> ExtractComplexDefn3</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Complex definition-based extraction example</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Questionnaire</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">http://hl7.org/fhir/uv/sdc/Questionnaire/extract-complex-defn3#4.0.0-ci-build</td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck11.png)\" id=\"item.patient\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-group.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"group\" class=\"hierarchy\"/> patient</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Patient Information</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-group\">group</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck111.png)\" id=\"item.name\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-group.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"group\" class=\"hierarchy\"/> name</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Name</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..*</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-group\">group</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.name\">Patient.name</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck1110.png)\" id=\"item.given\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> given</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Given Name(s)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..*</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.name.given\">Patient.name.given</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck1100.png)\" id=\"item.family\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin_end.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> family</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Family/Surname</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.name.family\">Patient.name.family</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck110.png)\" id=\"item.gender\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-coding.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"coding\" class=\"hierarchy\"/> gender</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Gender</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-choice\">choice</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.gender\">Patient.gender</a><br/>Value Set: <a href=\"http://hl7.org/fhir/R4/valueset-administrative-gender.html\">AdministrativeGender</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck110.png)\" id=\"item.dob\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-date.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"date\" class=\"hierarchy\"/> dob</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Date of Birth</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-date\">date</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.birthDate\">Patient.birthDate</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck110.png)\" id=\"item.ihi\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> ihi</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">National Identifier (IHI)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.identifier.value\">Patient.identifier.value</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck100.png)\" id=\"item.mobile-phone\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin_end.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> mobile-phone</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Mobile Phone number</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/patient.html#Patient.telecom.value\">Patient.telecom.value</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck11.png)\" id=\"item.contacts\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-group.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"group\" class=\"hierarchy\"/> contacts</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Contacts</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..*</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-group\">group</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck110.png)\" id=\"item.contact-name\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> contact-name</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Name</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/relatedperson.html#RelatedPerson.name.text\">RelatedPerson.name.text</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck110.png)\" id=\"item.relationship\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-coding.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"coding\" class=\"hierarchy\"/> relationship</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Relationship</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-choice\">choice</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/relatedperson.html#RelatedPerson.relationship.coding\">RelatedPerson.relationship.coding</a><br/>Value Set: <a href=\"http://hl7.org/fhir/R4/valueset-patient-contactrelationship.html\">Patient Contact Relationship </a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck100.png)\" id=\"item.phone\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vline.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin_end.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-string.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"string\" class=\"hierarchy\"/> phone</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Phone</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-string\">string</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/relatedperson.html#RelatedPerson.telecom.value\">RelatedPerson.telecom.value</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck01.png)\" id=\"item.obs\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin_end.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-group.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"group\" class=\"hierarchy\"/> obs</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Observations</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-group\">group</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"/></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck010.png)\" id=\"item.height\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_blank.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-decimal.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"decimal\" class=\"hierarchy\"/> height</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">What is your current height (m)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-decimal\">decimal</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/observation.html#Observation.value[x]:valueQuantity.value\">Observation.value[x]:valueQuantity.value</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: #F7F7F7\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck010.png)\" id=\"item.weight\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_blank.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-decimal.png\" alt=\".\" style=\"background-color: #F7F7F7; background-color: inherit\" title=\"decimal\" class=\"hierarchy\"/> weight</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">What is your current weight (kg)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-decimal\">decimal</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: #F7F7F7; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/observation.html#Observation.value[x]:valueQuantity.value\">Observation.value[x]:valueQuantity.value</a></td></tr>\r\n<tr style=\"border: 1px #F0F0F0 solid; padding:0px; vertical-align: top; background-color: white\"><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px; white-space: nowrap; background-image: url(tbl_bck000.png)\" id=\"item.complication\" class=\"hierarchy\"><img src=\"tbl_spacer.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_blank.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"tbl_vjoin_end.png\" alt=\".\" style=\"background-color: inherit\" class=\"hierarchy\"/><img src=\"icon-q-boolean.png\" alt=\".\" style=\"background-color: white; background-color: inherit\" title=\"boolean\" class=\"hierarchy\"/> complication</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Have you had a Sigmoidoscopy Complication (concern with invasive procedure, for example)</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">0..1</td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\"><a href=\"https://hl7.org/fhir/R4/codesystem-item-type.html#item-type-boolean\">boolean</a></td><td style=\"vertical-align: top; text-align : var(--ig-left,left); background-color: white; border: 1px #F0F0F0 solid; padding:0px 4px 0px 4px\" class=\"hierarchy\">Definition: <a href=\"http://hl7.org/fhir/R4/observation.html#Observation.value[x]\">Observation.value[x]</a></td></tr>\r\n<tr><td colspan=\"5\" class=\"hierarchy\"><br/><a href=\"https://hl7.org/fhir/R4/formats.html#table\" title=\"Legend for this format\"><img src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAABmJLR0QA/wD/AP+gvaeTAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH3goXBCwdPqAP0wAAAldJREFUOMuNk0tIlFEYhp9z/vE2jHkhxXA0zJCMitrUQlq4lnSltEqCFhFG2MJFhIvIFpkEWaTQqjaWZRkp0g26URZkTpbaaOJkDqk10szoODP//7XIMUe0elcfnPd9zsfLOYplGrpRwZaqTtw3K7PtGem7Q6FoidbGgqHVy/HRb669R+56zx7eRV1L31JGxYbBtjKK93cxeqfyQHbehkZbUkK20goELEuIzEd+dHS+qz/Y8PTSif0FnGkbiwcAjHaU1+QWOptFiyCLp/LnKptpqIuXHx6rbR26kJcBX3yLgBfnd7CxwJmflpP2wUg0HIAoUUpZBmKzELGWcN8nAr6Gpu7tLU/CkwAaoKTWRSQyt89Q8w6J+oVQkKnBoblH7V0PPvUOvDYXfopE/SJmALsxnVm6LbkotrUtNowMeIrVrBcBpaMmdS0j9df7abpSuy7HWehwJdt1lhVwi/J58U5beXGAF6c3UXLycw1wdFklArBn87xdh0ZsZtArghBdAA3+OEDVubG4UEzP6x1FOWneHh2VDAHBAt80IbdXDcesNoCvs3E5AFyNSU5nbrDPZpcUEQQTFZiEVx+51fxMhhyJEAgvlriadIJZZksRuwBYMOPBbO3hePVVqgEJhFeUuFLhIPkRP6BQLIBrmMenujm/3g4zc398awIe90Zb5A1vREALqneMcYgP/xVQWlG+Ncu5vgwwlaUNx+3799rfe96u9K0JSDXcOzOTJg4B6IgmXfsygc7/Bvg9g9E58/cDVmGIBOP/zT8Bz1zqWqpbXIsd0O9hajXfL6u4BaOS6SeWAAAAAElFTkSuQmCC\" alt=\"doco\" style=\"background-color: inherit\"/> Documentation for this format</a></td></tr></table></div>"
          },
          "extension": [
            {
              "url": "http://hl7.org/fhir/StructureDefinition/artifact-versionAlgorithm",
              "valueCoding": {
                "system": "http://hl7.org/fhir/version-algorithm",
                "code": "semver"
              }
            },
            {
              "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId",
              "valueString": "NewPatientId"
            },
            {
              "url": "http://hl7.org/fhir/StructureDefinition/structuredefinition-wg",
              "valueCode": "fhir"
            }
          ],
          "url": "http://hl7.org/fhir/uv/sdc/Questionnaire/extract-complex-defn3",
          "identifier": [
            {
              "system": "urn:ietf:rfc:3986",
              "value": "urn:oid:2.16.840.1.113883.4.642.40.17.35.32"
            }
          ],
          "version": "4.0.0-ci-build",
          "name": "ExtractComplexDefn3",
          "title": "Complex Extract Demonstration - Definition",
          "status": "draft",
          "experimental": true,
          "date": "2026-06-11T04:39:12+00:00",
          "publisher": "HL7 International / FHIR Infrastructure",
          "contact": [
            {
              "name": "HL7 International / FHIR Infrastructure",
              "telecom": [
                {
                  "system": "url",
                  "value": "http://www.hl7.org/Special/committees/fiwg"
                }
              ]
            },
            {
              "telecom": [
                {
                  "system": "url",
                  "value": "http://www.hl7.org/Special/committees/fiwg"
                }
              ]
            }
          ],
          "description": "Complex definition-based extraction example",
          "jurisdiction": [
            {
              "coding": [
                {
                  "system": "http://unstats.un.org/unsd/methods/m49/m49.htm",
                  "code": "001",
                  "display": "World"
                }
              ]
            }
          ],
          "item": [
            {
              "extension": [
                {
                  "extension": [
                    {
                      "url": "definition",
                      "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Patient"
                    },
                    {
                      "url": "fullUrl",
                      "valueString": "%NewPatientId"
                    }
                  ],
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract"
                }
              ],
              "linkId": "patient",
              "text": "Patient Information",
              "type": "group",
              "item": [
                {
                  "extension": [
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.text"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "item.where(linkId='given' or linkId='family').answer.value.join(' ')"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    }
                  ],
                  "linkId": "name",
                  "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name",
                  "text": "Name",
                  "type": "group",
                  "repeats": true,
                  "item": [
                    {
                      "linkId": "given",
                      "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.given",
                      "text": "Given Name(s)",
                      "type": "string",
                      "repeats": true
                    },
                    {
                      "linkId": "family",
                      "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.name.family",
                      "text": "Family/Surname",
                      "type": "string"
                    }
                  ]
                },
                 {
                   "linkId": "gender",
                   "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.gender",
                   "text": "Gender",
                   "type": "choice",
                   "answerOption": [
                     {
                       "valueCoding": {
                         "system": "http://hl7.org/fhir/administrative-gender",
                         "code": "male",
                         "display": "Male"
                       }
                     },
                     {
                       "valueCoding": {
                         "system": "http://hl7.org/fhir/administrative-gender",
                         "code": "female",
                         "display": "Female"
                       }
                     },
                     {
                       "valueCoding": {
                         "system": "http://hl7.org/fhir/administrative-gender",
                         "code": "other",
                         "display": "Other"
                       }
                     },
                     {
                       "valueCoding": {
                         "system": "http://hl7.org/fhir/administrative-gender",
                         "code": "unknown",
                         "display": "Unknown"
                       }
                     }
                   ]
                 },
                {
                  "linkId": "dob",
                  "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.birthDate",
                  "text": "Date of Birth",
                  "type": "date"
                },
                {
                  "extension": [
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.identifier.type"
                        },
                        {
                          "url": "fixed-value",
                          "valueCodeableConcept": {
                            "text": "National Identifier (IHI)"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.identifier.system"
                        },
                        {
                          "url": "fixed-value",
                          "valueUri": "http://example.org/nhio"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    }
                  ],
                  "linkId": "ihi",
                  "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.identifier.value",
                  "text": "National Identifier (IHI)",
                  "type": "string"
                },
                {
                  "extension": [
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.telecom.use"
                        },
                        {
                          "url": "fixed-value",
                          "valueCode": "phone"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.telecom.system"
                        },
                        {
                          "url": "fixed-value",
                          "valueCode": "mobile"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    }
                  ],
                  "linkId": "mobile-phone",
                  "definition": "http://hl7.org/fhir/StructureDefinition/Patient#Patient.telecom.value",
                  "text": "Mobile Phone number",
                  "type": "string"
                }
              ]
            },
            {
              "extension": [
                {
                  "extension": [
                    {
                      "url": "definition",
                      "valueCanonical": "http://hl7.org/fhir/StructureDefinition/RelatedPerson"
                    }
                  ],
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract"
                },
                {
                  "extension": [
                    {
                      "url": "definition",
                      "valueUri": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.patient.reference"
                    },
                    {
                      "url": "expression",
                      "valueExpression": {
                        "language": "text/fhirpath",
                        "expression": "%NewPatientId"
                      }
                    }
                  ],
                  "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                }
              ],
              "linkId": "contacts",
              "text": "Contacts",
              "type": "group",
              "repeats": true,
              "item": [
                {
                  "linkId": "contact-name",
                  "definition": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.name.text",
                  "text": "Name",
                  "type": "string"
                },
                {
      "linkId": "relationship",
      "definition": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.relationship.coding",
      "text": "Relationship",
      "type": "choice",
      "answerOption": [
        {
          "valueCoding": {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
            "code": "C",
            "display": "Emergency Contact"
          }
        },
        {
          "valueCoding": {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
            "code": "N",
            "display": "Next-of-Kin"
          }
        },
        {
          "valueCoding": {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
            "code": "F",
            "display": "Federal Agency"
          }
        },
        {
          "valueCoding": {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
            "code": "E",
            "display": "Employer"
          }
        },
        {
          "valueCoding": {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
            "code": "S",
            "display": "State Agency"
          }
        },
        {
          "valueCoding": {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
            "code": "O",
            "display": "Other"
          }
        },
        {
          "valueCoding": {
            "system": "http://terminology.hl7.org/CodeSystem/v2-0131",
            "code": "U",
            "display": "Unknown"
          }
        }
      ]
    },
                {
                  "extension": [
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.telecom.use"
                        },
                        {
                          "url": "fixed-value",
                          "valueCode": "phone"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.telecom.system"
                        },
                        {
                          "url": "fixed-value",
                          "valueCode": "mobile"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    }
                  ],
                  "linkId": "phone",
                  "definition": "http://hl7.org/fhir/StructureDefinition/RelatedPerson#RelatedPerson.telecom.value",
                  "text": "Phone",
                  "type": "string"
                }
              ]
            },
            {
              "linkId": "obs",
              "text": "Observations",
              "type": "group",
              "item": [
                {
                  "extension": [
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Observation"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.status"
                        },
                        {
                          "url": "fixed-value",
                          "valueCode": "final"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.category.coding"
                        },
                        {
                          "url": "fixed-value",
                          "valueCoding": {
                            "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                            "code": "vital-signs",
                            "display": "Vital Signs"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.code.coding"
                        },
                        {
                          "url": "fixed-value",
                          "valueCoding": {
                            "system": "http://loinc.org",
                            "code": "8302-2",
                            "display": "Body height"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.subject.reference"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "%NewPatientId"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.effective"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "(%resource.authored | %resource.meta.lastUpdated | now()).first()"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.issued"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "(%resource.authored | %resource.meta.lastUpdated | now()).first()"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.performer"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "%resource.author"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.value[x]:valueQuantity.unit"
                        },
                        {
                          "url": "fixed-value",
                          "valueString": "m"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.derivedFrom.reference"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "'QuestionnaireResponse/' + %resource.id"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    }
                  ],
                  "linkId": "height",
                  "definition": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.value[x]:valueQuantity.value",
                  "text": "What is your current height (m)",
                  "type": "decimal"
                },
                {
                  "extension": [
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueCanonical": "http://hl7.org/fhir/StructureDefinition/Observation"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtract"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.status"
                        },
                        {
                          "url": "fixed-value",
                          "valueCode": "final"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.category.coding"
                        },
                        {
                          "url": "fixed-value",
                          "valueCoding": {
                            "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                            "code": "vital-signs",
                            "display": "Vital Signs"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.code.coding"
                        },
                        {
                          "url": "fixed-value",
                          "valueCoding": {
                            "system": "http://loinc.org",
                            "code": "29463-7",
                            "display": "Weight"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.subject.reference"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "%NewPatientId"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.effective"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "(%resource.authored | %resource.meta.lastUpdated | now()).first()"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.issued"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "(%resource.authored | %resource.meta.lastUpdated | now()).first()"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.performer"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "%resource.author"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.value[x]:valueQuantity.unit"
                        },
                        {
                          "url": "fixed-value",
                          "valueString": "kg"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.derivedFrom.reference"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "'QuestionnaireResponse/' + %resource.id"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    }
                  ],
                  "linkId": "weight",
                  "definition": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.value[x]:valueQuantity.value",
                  "text": "What is your current weight (kg)",
                  "type": "decimal"
                },
                {
                  "extension": [
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.status"
                        },
                        {
                          "url": "fixed-value",
                          "valueCode": "final"
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.code.coding"
                        },
                        {
                          "url": "fixed-value",
                          "valueCoding": {
                            "system": "http://example.org/sdh/demo/CodeSystem/cc-screening-codes",
                            "code": "sigmoidoscopy-complication"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.subject.reference"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "%NewPatientId"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.effective"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "(%resource.authored | %resource.meta.lastUpdated | now()).first()"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.issued"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "(%resource.authored | %resource.meta.lastUpdated | now()).first()"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.performer"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "%resource.author"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    },
                    {
                      "extension": [
                        {
                          "url": "definition",
                          "valueUri": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.derivedFrom.reference"
                        },
                        {
                          "url": "expression",
                          "valueExpression": {
                            "language": "text/fhirpath",
                            "expression": "'QuestionnaireResponse/' + %resource.id"
                          }
                        }
                      ],
                      "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-definitionExtractValue"
                    }
                  ],
                  "linkId": "complication",
                  "definition": "http://hl7.org/fhir/StructureDefinition/Observation#Observation.value[x]",
                  "text": "Have you had a Sigmoidoscopy Complication (concern with invasive procedure, for example)",
                  "type": "boolean"
                }
              ]
            }
          ]
        }
    """
      .trimIndent()

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

  private fun requestOf(entry: JsonObject): JsonObject = entry.getValue("request").jsonObject
}
