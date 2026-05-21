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

import dev.ohs.fhir.model.r4.FhirR4Json
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin.test.Test
import kotlin.test.assertEquals
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
