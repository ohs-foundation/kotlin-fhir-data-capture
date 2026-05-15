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
package dev.ohs.fhir.datacapture.views.factories

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import dev.ohs.fhir.datacapture.extensions.EXTENSION_ENTRY_FORMAT_URL
import dev.ohs.fhir.datacapture.extensions.FhirR4DateType
import dev.ohs.fhir.datacapture.extensions.FhirR4String
import dev.ohs.fhir.datacapture.validation.NotValidated
import dev.ohs.fhir.datacapture.views.QuestionnaireViewItem
import dev.ohs.fhir.datacapture.views.components.DATE_TEXT_INPUT_FIELD
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Extension
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.String
import io.kotest.matchers.shouldBe
import java.util.Locale
import kotlin.test.Test
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number

@OptIn(ExperimentalTestApi::class)
class JVMDateViewFactoryTest {

  @Test
  fun shouldSetDateInput_localeUs() = runComposeUiTest {
    Locale.setDefault(Locale.US)
    val questionnaireViewItem =
      QuestionnaireViewItem(
        Questionnaire.Item(
          linkId = FhirR4String(value = "date-item"),
          type = Enumeration(value = Questionnaire.QuestionnaireItemType.Date),
          text = FhirR4String(value = "Question?"),
        ),
        QuestionnaireResponse.Item(
          linkId = FhirR4String(value = "date-item"),
          answer =
            listOf(
              QuestionnaireResponse.Item.Answer(
                value =
                  QuestionnaireResponse.Item.Answer.Value.Date(
                    value = FhirR4DateType(value = FhirDate.fromString("2020-10-19"))
                  )
              )
            ),
        ),
        validationResult = NotValidated,
        answersChangedCallback = { _, _, _, _ -> },
      )

    setContent { DateViewFactory.Content(questionnaireViewItem) }

    onNodeWithTag(DATE_TEXT_INPUT_FIELD, useUnmergedTree = true).assertTextEquals("10/19/2020")
  }

  @Test
  fun shouldSetDateInput_localeJp() = runComposeUiTest {
    Locale.setDefault(Locale.JAPAN)
    val questionnaireViewItem =
      QuestionnaireViewItem(
        Questionnaire.Item(
          linkId = FhirR4String(value = "date-item"),
          type = Enumeration(value = Questionnaire.QuestionnaireItemType.Date),
          text = FhirR4String(value = "Question?"),
        ),
        QuestionnaireResponse.Item(
          linkId = FhirR4String(value = "date-item"),
          answer =
            listOf(
              QuestionnaireResponse.Item.Answer(
                value =
                  QuestionnaireResponse.Item.Answer.Value.Date(
                    value = FhirR4DateType(value = FhirDate.Date(LocalDate(2020, 10, 19)))
                  )
              )
            ),
        ),
        validationResult = NotValidated,
        answersChangedCallback = { _, _, _, _ -> },
      )

    setContent { DateViewFactory.Content(questionnaireViewItem) }
    onNodeWithTag(DATE_TEXT_INPUT_FIELD, useUnmergedTree = true).assertTextEquals("2020/10/19")
  }

  @Test
  fun shouldSetDateInput_localeEn() = runComposeUiTest {
    Locale.setDefault(Locale.ENGLISH)
    val questionnaireViewItem =
      QuestionnaireViewItem(
        Questionnaire.Item(
          linkId = FhirR4String(value = "date-item"),
          type = Enumeration(value = Questionnaire.QuestionnaireItemType.Date),
          text = FhirR4String(value = "Question?"),
        ),
        QuestionnaireResponse.Item(
          linkId = FhirR4String(value = "date-item"),
          answer =
            listOf(
              QuestionnaireResponse.Item.Answer(
                value =
                  QuestionnaireResponse.Item.Answer.Value.Date(
                    value = FhirR4DateType(value = FhirDate.Date(LocalDate(2020, 10, 19)))
                  )
              )
            ),
        ),
        validationResult = NotValidated,
        answersChangedCallback = { _, _, _, _ -> },
      )

    setContent { DateViewFactory.Content(questionnaireViewItem) }

    onNodeWithTag(DATE_TEXT_INPUT_FIELD, useUnmergedTree = true).assertTextEquals("10/19/2020")
  }

  @Test
  fun parseDateTextInputInUsLocale() = runComposeUiTest {
    Locale.setDefault(Locale.US)
    var answers: List<QuestionnaireResponse.Item.Answer>? = null
    val item =
      QuestionnaireViewItem(
        Questionnaire.Item(
          linkId = FhirR4String(value = "date-item"),
          type = Enumeration(value = Questionnaire.QuestionnaireItemType.Date),
        ),
        QuestionnaireResponse.Item(linkId = FhirR4String(value = "date-item")),
        validationResult = NotValidated,
        answersChangedCallback = { _, _, result, _ ->
          println(result)
          answers = result
        },
      )

    setContent { DateViewFactory.Content(item) }
    onNodeWithTag(DATE_TEXT_INPUT_FIELD).performTextInput("11/19/2020")
    waitUntil { answers != null }

    val answer = (answers!!.single().value?.asDate()?.value?.value as? FhirDate.Date)?.date

    answer?.day.shouldBe(19)
    answer?.month?.number.shouldBe(11)
    answer?.year.shouldBe(2020)
  }

  @Test
  fun parseDateTextInputInJapanLocale() = runComposeUiTest {
    Locale.setDefault(Locale.JAPAN)
    var answers: List<QuestionnaireResponse.Item.Answer>? = null
    val item =
      QuestionnaireViewItem(
        Questionnaire.Item(
          linkId = FhirR4String(value = "date-item"),
          type = Enumeration(value = Questionnaire.QuestionnaireItemType.Date),
        ),
        QuestionnaireResponse.Item(linkId = FhirR4String(value = "date-item")),
        validationResult = NotValidated,
        answersChangedCallback = { _, _, result, _ -> answers = result },
      )
    setContent { DateViewFactory.Content(item) }
    onNodeWithTag(DATE_TEXT_INPUT_FIELD).performTextInput("2020/11/19")
    waitUntil { answers != null }
    val answer = (answers!!.single().value?.asDate()?.value?.value as? FhirDate.Date)?.date

    answer?.day.shouldBe(19)
    answer?.month?.number.shouldBe(11)
    answer?.year.shouldBe(2020)
  }

  @Test
  fun shouldSetLocalDateInputFormatWhenEntryFormatExtensionHasIncorrectFormatStringInQuestionnaire() =
    runComposeUiTest {
      Locale.setDefault(Locale.US)
      val questionnaireViewItem =
        QuestionnaireViewItem(
          Questionnaire.Item(
            linkId = FhirR4String(value = "date-item"),
            type = Enumeration(value = Questionnaire.QuestionnaireItemType.Date),
            extension =
              listOf(
                Extension(
                  url = EXTENSION_ENTRY_FORMAT_URL,
                  value = Extension.Value.String(value = FhirR4String(value = "yMyd")),
                )
              ),
          ),
          QuestionnaireResponse.Item(linkId = FhirR4String(value = "date-item")),
          validationResult = NotValidated,
          answersChangedCallback = { _, _, _, _ -> },
        )

      setContent { DateViewFactory.Content(questionnaireViewItem) }
      onNodeWithTag(DATE_TEXT_INPUT_FIELD).assertTextEquals("yyyymmdd", includeEditableText = false)
    }

  @Test
  fun shouldSetLocalDateInputFormatWhenEntryFormatExtensionHasEmptyStringInQuestionnaire() =
    runComposeUiTest {
      Locale.setDefault(Locale.US)
      val questionnaireViewItem =
        QuestionnaireViewItem(
          Questionnaire.Item(
            linkId = FhirR4String(value = "date-item"),
            type = Enumeration(value = Questionnaire.QuestionnaireItemType.Date),
            extension =
              listOf(
                Extension(
                  url = EXTENSION_ENTRY_FORMAT_URL,
                  value = Extension.Value.String(value = FhirR4String(value = "")),
                )
              ),
          ),
          QuestionnaireResponse.Item(linkId = FhirR4String(value = "date-item")),
          validationResult = NotValidated,
          answersChangedCallback = { _, _, _, _ -> },
        )

      setContent { DateViewFactory.Content(questionnaireViewItem) }
      onNodeWithTag(DATE_TEXT_INPUT_FIELD)
        .assertTextEquals("mm/dd/yyyy", includeEditableText = false)
    }

  @Test
  fun shouldSetLocalDateInputFormatWhenNoEntryFormatExtensionInQuestionnaire() = runComposeUiTest {
    Locale.setDefault(Locale.US)
    val questionnaireViewItem =
      QuestionnaireViewItem(
        Questionnaire.Item(
          linkId = FhirR4String(value = "date-item"),
          type = Enumeration(value = Questionnaire.QuestionnaireItemType.Date),
        ),
        QuestionnaireResponse.Item(linkId = FhirR4String(value = "date-item")),
        validationResult = NotValidated,
        answersChangedCallback = { _, _, _, _ -> },
      )

    setContent { DateViewFactory.Content(questionnaireViewItem) }
    onNodeWithTag(DATE_TEXT_INPUT_FIELD).assertTextEquals("mm/dd/yyyy", includeEditableText = false)
  }
}
