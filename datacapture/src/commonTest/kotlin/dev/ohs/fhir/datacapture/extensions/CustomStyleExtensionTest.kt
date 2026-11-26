/*
 * Copyright 2022-2026 Open Health Stack Foundation
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

import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.String as FhirString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomStyleExtensionTest {
  @Test
  fun readsCustomStyleExtension() {
    val item = item(styleExtension(EXTENSION_CUSTOM_STYLE_URL, "Style"))
    assertEquals("Style", item.readCustomStyleExtension(StyleUrl.QUESTION_TEXT_VIEW))
    assertNull(item.readCustomStyleExtension(StyleUrl.PREFIX_TEXT_VIEW))
  }

  @Test
  fun readsLegacyAndroidStyleExtension() {
    val item = item(styleExtension(EXTENSION_CUSTOM_STYLE_URL_ANDROID_FHIR, "Legacy"))
    assertEquals("Legacy", item.readCustomStyleExtension(StyleUrl.QUESTION_TEXT_VIEW))
  }

  @Test
  fun prefersCustomStyleOverLegacyExtension() {
    val item =
      item(
        styleExtension(EXTENSION_CUSTOM_STYLE_URL_ANDROID_FHIR, "Legacy"),
        styleExtension(EXTENSION_CUSTOM_STYLE_URL, "Style"),
      )
    assertEquals("Style", item.readCustomStyleExtension(StyleUrl.QUESTION_TEXT_VIEW))
  }

  @Test
  fun returnsNullWithoutStyleExtension() {
    assertNull(item().readCustomStyleExtension(StyleUrl.QUESTION_TEXT_VIEW))
  }

  private fun item(vararg extensions: Extension) =
    Questionnaire.Item(
      linkId = FhirString(value = "1"),
      type = Enumeration(value = Questionnaire.QuestionnaireItemType.Display),
      extension = extensions.toList(),
    )

  private fun styleExtension(url: String, style: String) =
    Extension(
      url = url,
      extension =
        listOf(
          Extension(
            url = StyleUrl.QUESTION_TEXT_VIEW.url,
            value = Extension.Value.String(value = FhirString(value = style)),
          )
        ),
    )
}
