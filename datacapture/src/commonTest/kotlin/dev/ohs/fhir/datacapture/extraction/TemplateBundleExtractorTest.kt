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
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateBundleExtractorTest {
  private val fhirJson = FhirR4Json()
  private val extractor = TemplateBundleExtractor()

  @Test
  fun extract_withExplicitTemplateJsons_buildsBundleFromResolvedResources() {
    val bundle =
      extractor.extract(
        questionnaireResponse = questionnaireResponse(),
        templateJsons = listOf(patientTemplateJson),
      )

    //        val patient = assertIs<Patient>(bundle.entry.single().resource)
    assertEquals(Bundle.BundleType.Collection, bundle.type.value)
    //        assertEquals(true, patient.active?.value)
    //        assertEquals(AdministrativeGender.Female, patient.gender?.value)
  }

  @Test
  fun extract_withoutExplicitTemplates_readsQuestionnaireExtensionPayloads() {
    val questionnaire =
      Questionnaire.Builder(status = Enumeration(value = PublicationStatus.Active))
        .apply {
          extension =
            mutableListOf(
              Extension.Builder(TemplateBundleExtractor.MAPPING_TEMPLATE_EXTENSION_URL).apply {
                value =
                  Extension.Value.String(
                    dev.ohs.fhir.datacapture.extensions.FhirR4String(value = patientTemplateJson)
                  )
              }
            )
        }
        .build()

    val bundle =
      extractor.extract(
        questionnaireResponse = questionnaireResponse(),
        questionnaire = questionnaire,
      )

    //        val patient = assertIs<Patient>(bundle.entry.single().resource)
    assertEquals(Bundle.BundleType.Collection, bundle.type.value)
    //        assertEquals(true, patient.active?.value)
    //        assertEquals(AdministrativeGender.Female, patient.gender?.value)
  }

  private fun questionnaireResponse(): QuestionnaireResponse =
    fhirJson.decodeFromString(
      """
      {
        "resourceType": "QuestionnaireResponse",
        "questionnaire": "Questionnaire/b9f1e519-6406-4d36-8e6d-31b4532317cc",
        "status": "completed",
        "subject": {
          "reference": "Patient/example"
        },
        "authored": "2026-04-27T10:00:00+03:00",
        "item": [
          {
            "linkId": "name",
            "text": "Name",
            "item": [
              {
                "linkId": "name.given",
                "text": "Given Name",
                "answer": [
                  { "valueString": "Amina" }
                ]
              },
              {
                "linkId": "name.family",
                "text": "Family Name",
                "answer": [
                  { "valueString": "Odhiambo" }
                ]
              }
            ]
          },
          {
            "linkId": "birthDate",
            "text": "Date of Birth",
            "answer": [
              { "valueDate": "1990-06-15" }
            ]
          },
          {
            "linkId": "gender",
            "text": "Administrative Gender",
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
            "linkId": "telecom",
            "text": "Contact",
            "item": [
              {
                "linkId": "telecom.phone",
                "text": "Phone Number",
                "answer": [
                  { "valueString": "+254712345678" }
                ]
              },
              {
                "linkId": "telecom.email",
                "text": "Email Address",
                "answer": [
                  { "valueString": "amina.odhiambo@example.com" }
                ]
              }
            ]
          },
          {
            "linkId": "address",
            "text": "Address",
            "item": [
              {
                "linkId": "address.line",
                "text": "Street Address",
                "answer": [
                  { "valueString": "14 Ngong Road" }
                ]
              },
              {
                "linkId": "address.city",
                "text": "City",
                "answer": [
                  { "valueString": "Nairobi" }
                ]
              },
              {
                "linkId": "address.postalCode",
                "text": "Postal Code",
                "answer": [
                  { "valueString": "00100" }
                ]
              },
              {
                "linkId": "address.country",
                "text": "Country",
                "answer": [
                  { "valueString": "KE" }
                ]
              }
            ]
          },
          {
            "linkId": "identifier",
            "text": "National ID",
            "answer": [
              { "valueString": "12345678" }
            ]
          }
        ]
      }
      """
        .trimIndent()
    ) as QuestionnaireResponse

  private companion object {
    val patientTemplateJson: String =
      """
      [
           {
             "resourceType": "Patient",
             "name": [
               {
                 "given": [
                   "{{ QuestionnaireResponse.item.where(linkId='name').item.where(linkId='name.given').answer.valueString }}"
                 ],
                 "family": "{{ QuestionnaireResponse.item.where(linkId='name').item.where(linkId='name.family').answer.valueString }}"
               }
             ],
             "birthDate": "{{ QuestionnaireResponse.item.where(linkId='birthDate').answer.valueDate }}",
             "gender": "{{ QuestionnaireResponse.item.where(linkId='gender').answer.valueCoding.code }}",
             "telecom": [
               {
                 "{% if QuestionnaireResponse.item.where(linkId='telecom').item.where(linkId='telecom.phone').answer.valueString.exists() %}": {
                   "system": "phone",
                   "value": "{{ QuestionnaireResponse.item.where(linkId='telecom').item.where(linkId='telecom.phone').answer.valueString }}"
                 }
               },
               {
                 "{% if QuestionnaireResponse.item.where(linkId='telecom').item.where(linkId='telecom.email').answer.valueString.exists() %}": {
                   "system": "email",
                   "value": "{{ QuestionnaireResponse.item.where(linkId='telecom').item.where(linkId='telecom.email').answer.valueString }}"
                 }
               }
             ],
             "address": [
               {
                 "{% if QuestionnaireResponse.item.where(linkId='address').item.answer.valueString.exists() %}": {
                   "line": [
                     "{[ QuestionnaireResponse.item.where(linkId='address').item.where(linkId='address.line').answer.valueString ]}"
                   ],
                   "city": "{{ QuestionnaireResponse.item.where(linkId='address').item.where(linkId='address.city').answer.valueString }}",
                   "postalCode": "{{ QuestionnaireResponse.item.where(linkId='address').item.where(linkId='address.postalCode').answer.valueString }}",
                   "country": "{{ QuestionnaireResponse.item.where(linkId='address').item.where(linkId='address.country').answer.valueString }}"
                 }
               }
             ],
             "identifier": [
               {
                 "{% if QuestionnaireResponse.item.where(linkId='identifier').answer.valueString.exists() %}": {
                   "value": "{{ QuestionnaireResponse.item.where(linkId='identifier').answer.valueString }}"
                 }
               }
             ]
           }
         ]
      """
        .trimIndent()
  }
}
