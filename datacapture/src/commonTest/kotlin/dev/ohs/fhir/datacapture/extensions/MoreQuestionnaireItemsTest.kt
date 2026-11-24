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
package dev.ohs.fhir.datacapture.extensions

import dev.ohs.fhir.model.r4.Questionnaire
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class MoreQuestionnaireItemsTest {

  private val fhirJson = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  @Test
  fun createQuestionnaireResponseItem_multipleInitialValuesWithoutRepeats_shouldThrow() {
    // `repeats` is optional: an item without it does not repeat.
    val questionnaireItem =
      fhirJson.decodeFromString<Questionnaire.Item>(
        """
        {
          "linkId": "a",
          "type": "integer",
          "initial": [{ "valueInteger": 1 }, { "valueInteger": 2 }]
        }
        """
      )

    assertFailsWith<IllegalStateException> { questionnaireItem.createQuestionnaireResponseItem() }
  }
}
