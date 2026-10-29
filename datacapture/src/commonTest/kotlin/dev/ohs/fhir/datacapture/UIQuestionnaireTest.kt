/*
 * Copyright 2023-2026 Open Health Stack Foundation
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
package dev.ohs.fhir.datacapture

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.ohs.fhir.datacapture.extensions.FhirR4String
import dev.ohs.fhir.datacapture.views.components.ADD_REPEATED_GROUP_BUTTON_TAG
import dev.ohs.fhir.datacapture.views.components.DELETE_REPEATED_GROUP_ITEM_BUTTON_TAG
import dev.ohs.fhir.datacapture.views.components.EDIT_TEXT_FIELD_TEST_TAG
import dev.ohs.fhir.datacapture.views.components.HINT_HEADER_TAG
import dev.ohs.fhir.datacapture.views.components.QUESTIONNAIRE_BOTTOM_NAVIGATION_TEST_TAG
import dev.ohs.fhir.datacapture.views.components.QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG
import dev.ohs.fhir.datacapture.views.components.QUESTION_HEADER_TAG
import dev.ohs.fhir.datacapture.views.components.REPEATED_GROUP_INSTANCE_HEADER_TITLE_TAG
import dev.ohs.fhir.datacapture.views.components.SLIDER_TAG
import dev.ohs.fhir.datacapture.views.factories.NO_CHOICE_RADIO_BUTTON_TAG
import dev.ohs.fhir.datacapture.views.factories.YES_CHOICE_RADIO_BUTTON_TAG
import dev.ohs.fhir.model.r4.Enumeration
import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.model.r4.terminologies.PublicationStatus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin_fhir_data_capture.datacapture.generated.resources.Res
import kotlin_fhir_data_capture.datacapture.generated.resources.button_pagination_next
import kotlin_fhir_data_capture.datacapture.generated.resources.button_pagination_previous
import kotlin_fhir_data_capture.datacapture.generated.resources.button_review
import kotlin_fhir_data_capture.datacapture.generated.resources.edit_button_text
import kotlin_fhir_data_capture.datacapture.generated.resources.submit_questionnaire
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class UIQuestionnaireTest {

  private val fhirJson = Json {
    explicitNulls = false
    encodeDefaults = false
  }

  @BeforeTest
  fun setUp() {
    setTestMainDispatcher()
  }

  @AfterTest
  fun tearDown() {
    resetTestMainDispatcher()
  }

  @Test
  fun shouldDisplayReviewButtonWhenNoMorePagesToDisplay() = runComposeUiTest {
    setQuestionnaireContent("files/paginated_questionnaire_with_dependent_answer.json", true)
    val reviewButtonText = getString(Res.string.button_review)
    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(reviewButtonText))
      .assertIsDisplayed()

    onNodeWithText("Yes").performClick()

    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(reviewButtonText))
      .assertDoesNotExist()

    onNodeWithText("No").performClick()

    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(reviewButtonText))
      .assertIsDisplayed()
  }

  @Test
  fun shouldHideNextButtonIfDisabled() = runComposeUiTest {
    setQuestionnaireContent("files/layout_paginated.json", true)

    val nextButtonText = getString(Res.string.button_pagination_next)
    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(nextButtonText))
      .performClick()

    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(nextButtonText))
      .assertDoesNotExist()
  }

  @Test
  fun shouldDisplayNextButtonIfEnabled() = runComposeUiTest {
    setQuestionnaireContent("files/layout_paginated.json", true)

    val nextButtonText = getString(Res.string.button_pagination_next)
    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(nextButtonText))
      .assertIsDisplayed()
  }

  @Test
  fun displayItems_shouldGetEnabled_withAnswerChoice() = runComposeUiTest {
    setQuestionnaireContent("files/questionnaire_with_enabled_display_items.json")

    onNodeWithTag(HINT_HEADER_TAG).assertDoesNotExist()
    onNodeWithTag(YES_CHOICE_RADIO_BUTTON_TAG).performClick()
    onNodeWithTag(HINT_HEADER_TAG).assertTextEquals("Text when yes is selected").assertIsDisplayed()

    onNodeWithTag(NO_CHOICE_RADIO_BUTTON_TAG).performClick()
    onNodeWithTag(HINT_HEADER_TAG).assertIsDisplayed().assertTextEquals("Text when no is selected")

    onNodeWithTag(NO_CHOICE_RADIO_BUTTON_TAG).performClick()
    onNodeWithTag(HINT_HEADER_TAG).assertDoesNotExist()
  }

  @Test
  fun cqfExpression_shouldSetText_withEvaluatedAnswer() = runComposeUiTest {
    setQuestionnaireContent("files/questionnaire_with_dynamic_question_text.json")

    onNode(hasTestTag(QUESTION_HEADER_TAG) and hasText("Option Date")).assertIsDisplayed()
    onNode(hasTestTag(QUESTION_HEADER_TAG) and hasText("Provide \"First Option\" Date"))
      .assertDoesNotExist()

    onNodeWithText("First Option").performClick()

    onNode(hasTestTag(QUESTION_HEADER_TAG) and hasText("Option Date")).assertDoesNotExist()
    onNode(hasTestTag(QUESTION_HEADER_TAG) and hasText("Provide \"First Option\" Date"))
      .assertIsDisplayed()
  }

  @Test
  fun progressBar_shouldBeVisible_withSinglePageQuestionnaire() = runComposeUiTest {
    setQuestionnaireContent("files/text_questionnaire_integer.json")
    onNodeWithTag(QUESTIONNAIRE_PROGRESS_INDICATOR_TEST_TAG)
      .assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
  }

  @Test
  fun progressBar_shouldBeVisible_withPaginatedQuestionnaire() = runComposeUiTest {
    setQuestionnaireContent("files/layout_paginated.json")
    onNodeWithTag(QUESTIONNAIRE_PROGRESS_INDICATOR_TEST_TAG)
      .assertRangeInfoEquals(ProgressBarRangeInfo(0.5f, 0f..1f))
  }

  @Test
  fun progressBar_shouldProgress_onPaginationNext() = runComposeUiTest {
    setQuestionnaireContent("files/layout_paginated.json")

    val nextButtonText = getString(Res.string.button_pagination_next)
    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(nextButtonText))
      .performClick()

    onNodeWithTag(QUESTIONNAIRE_PROGRESS_INDICATOR_TEST_TAG)
      .assertRangeInfoEquals(ProgressBarRangeInfo(1f, 0f..1f))
  }

  @Test
  fun progressBar_shouldBeGone_whenNavigatedToReviewScreen() = runComposeUiTest {
    setQuestionnaireContent("files/text_questionnaire_integer.json", isReviewMode = true)

    val reviewButtonText = getString(Res.string.button_review)
    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(reviewButtonText))
      .performClick()

    onNodeWithTag(QUESTIONNAIRE_PROGRESS_INDICATOR_TEST_TAG).assertDoesNotExist()
  }

  @Test
  fun progressBar_shouldBeVisible_whenNavigatedToEditScreenFromReview() = runComposeUiTest {
    setQuestionnaireContent("files/text_questionnaire_integer.json", isReviewMode = true)

    val reviewButtonText = getString(Res.string.button_review)
    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(reviewButtonText))
      .performClick()

    onNodeWithText(getString(Res.string.edit_button_text)).performClick()

    onNodeWithTag(QUESTIONNAIRE_PROGRESS_INDICATOR_TEST_TAG).assertIsDisplayed()
  }

  @Test
  fun test_add_item_button_does_not_exist_for_non_repeated_groups() = runComposeUiTest {
    setQuestionnaireContent("files/component_non_repeated_group.json")
    onNodeWithTag(ADD_REPEATED_GROUP_BUTTON_TAG).assertDoesNotExist()
  }

  @Test
  fun test_repeated_group_is_added() = runComposeUiTest {
    setQuestionnaireContent("files/component_repeated_group.json")
    onNodeWithTag(ADD_REPEATED_GROUP_BUTTON_TAG).performClick()

    onNodeWithTag(QUESTIONNAIRE_EDIT_LIST).assertExists().assertIsDisplayed()

    onNodeWithTag(REPEATED_GROUP_INSTANCE_HEADER_TITLE_TAG).assertIsDisplayed()

    onNodeWithTag(DELETE_REPEATED_GROUP_ITEM_BUTTON_TAG).assertIsDisplayed()
  }

  @Test
  fun test_repeated_group_adds_multiple_items() = runComposeUiTest {
    setQuestionnaireContent("files/component_multiple_repeated_group.json")
    onNode(hasTestTag(ADD_REPEATED_GROUP_BUTTON_TAG) and hasText("Add Repeated Group"))
      .performClick()
    onNodeWithTag(DELETE_REPEATED_GROUP_ITEM_BUTTON_TAG).assertIsDisplayed()
    onNodeWithTag(REPEATED_GROUP_INSTANCE_HEADER_TITLE_TAG).assertIsDisplayed()

    onNode(hasTestTag(ADD_REPEATED_GROUP_BUTTON_TAG) and hasText("Add Decimal Repeated Group"))
      .performClick()
    onAllNodes(hasTestTag(DELETE_REPEATED_GROUP_ITEM_BUTTON_TAG)).assertCountEquals(2)
    onAllNodes(hasTestTag(REPEATED_GROUP_INSTANCE_HEADER_TITLE_TAG)).assertCountEquals(2)
  }

  @Test
  fun test_repeated_group_is_deleted() = runComposeUiTest {
    setQuestionnaireContent(
      "files/component_repeated_group.json",
      responseFileName = "files/repeated_group_response.json",
    )

    onNodeWithTag(QUESTIONNAIRE_EDIT_LIST).assertExists().assertIsDisplayed()
    onNodeWithTag(REPEATED_GROUP_INSTANCE_HEADER_TITLE_TAG).assertIsDisplayed()
    onNodeWithTag(DELETE_REPEATED_GROUP_ITEM_BUTTON_TAG).performClick()
    onNodeWithTag(REPEATED_GROUP_INSTANCE_HEADER_TITLE_TAG).assertDoesNotExist()
  }

  @Test
  fun test_repeated_group_populates_multiple_answers() = runComposeUiTest {
    setQuestionnaireContent(
      "files/component_repeated_group.json",
      responseFileName = "files/repeated_group_multiple_response.json",
    )

    onAllNodes(hasTestTag(REPEATED_GROUP_INSTANCE_HEADER_TITLE_TAG)).assertCountEquals(2)
    onAllNodes(hasTestTag(DELETE_REPEATED_GROUP_ITEM_BUTTON_TAG)).assertCountEquals(2)
  }

  @Test
  fun shouldHideNextButtonOnLastPage() = runComposeUiTest {
    val questionnaireJson =
      """{
  "resourceType": "Questionnaire",
  "status": "active",
  "item": [
    {
      "linkId": "1",
      "type": "group",
      "extension": [
        {
          "url": "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl",
          "valueCodeableConcept": {
            "coding": [
              {
                "system": "http://hl7.org/fhir/questionnaire-item-control",
                "code": "page",
                "display": "Page"
              }
            ],
            "text": "Page"
          }
        }
      ],
      "item": [
        {
          "linkId": "1.1",
          "type": "display",
          "text": "Item 1"
        }
      ]
    },
    {
      "linkId": "2",
      "type": "group",
      "extension": [
        {
          "url": "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl",
          "valueCodeableConcept": {
            "coding": [
              {
                "system": "http://hl7.org/fhir/questionnaire-item-control",
                "code": "page",
                "display": "Page"
              }
            ],
            "text": "Page"
          }
        }
      ],
      "item": [
        {
          "linkId": "2.1",
          "type": "display",
          "text": "Item 2"
        }
      ]
    }
  ]
}
"""
    val questionnaire = fhirJson.decodeFromString<Questionnaire>(questionnaireJson)
    setQuestionnaireContent(questionnaire)
    val nextButtonText = getString(Res.string.button_pagination_next)
    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(nextButtonText))
      .performClick()
    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(nextButtonText))
      .assertDoesNotExist()
  }

  @Test
  fun reviewPageShouldShowBothEditAndSubmitButton() = runComposeUiTest {
    val questionnaire =
      Questionnaire(
        id = "a-questionnaire",
        status = Enumeration(value = PublicationStatus.Active),
        item =
          listOf(
            Questionnaire.Item(
              linkId = FhirR4String(value = "a-link-id"),
              type = Enumeration(value = Questionnaire.QuestionnaireItemType.Boolean),
            )
          ),
      )
    setQuestionnaireContent(questionnaire, isReviewMode = true, showReviewPageFirst = true)

    onNodeWithText(getString(Res.string.edit_button_text)).assertIsDisplayed()
    onNode(
        hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and
          hasText(getString(Res.string.submit_questionnaire))
      )
      .assertIsDisplayed()
  }

  @Test
  fun questionnaireSubmitButtonTextShouldBeEditable() = runComposeUiTest {
    val questionnaire =
      Questionnaire(
        id = "a-questionnaire",
        status = Enumeration(value = PublicationStatus.Active),
        item =
          listOf(
            Questionnaire.Item(
              linkId = FhirR4String(value = "a-link-id"),
              type = Enumeration(value = Questionnaire.QuestionnaireItemType.Boolean),
            )
          ),
      )
    val customButtonText = "Apply"
    setQuestionnaireContent(questionnaire, submitText = customButtonText)
    onNode(hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and hasText(customButtonText))
      .assertIsDisplayed()
  }

  @Test
  fun shouldHidePreviousButtonOnFirstPage() = runComposeUiTest {
    val questionnaireJson =
      """{
  "resourceType": "Questionnaire",
  "status": "active",
  "item": [
    {
      "linkId": "1",
      "type": "group",
      "extension": [
        {
          "url": "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl",
          "valueCodeableConcept": {
            "coding": [
              {
                "system": "http://hl7.org/fhir/questionnaire-item-control",
                "code": "page",
                "display": "Page"
              }
            ],
            "text": "Page"
          }
        }
      ],
      "item": [
        {
          "linkId": "1.1",
          "type": "display",
          "text": "Item 1"
        }
      ]
    },
    {
      "linkId": "2",
      "type": "group",
      "extension": [
        {
          "url": "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl",
          "valueCodeableConcept": {
            "coding": [
              {
                "system": "http://hl7.org/fhir/questionnaire-item-control",
                "code": "page",
                "display": "Page"
              }
            ],
            "text": "Page"
          }
        }
      ],
      "item": [
        {
          "linkId": "2.1",
          "type": "display",
          "text": "Item 2"
        }
      ]
    }
  ]
}
"""
    val questionnaire = fhirJson.decodeFromString<Questionnaire>(questionnaireJson)
    setQuestionnaireContent(questionnaire)
    onNode(
        hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and
          hasText(getString(Res.string.button_pagination_previous))
      )
      .assertDoesNotExist()
  }

  @Test
  fun showBottomNavigationContainerWhenSetShowNavigationInDefaultLongScrollIsSetToFalse() =
    runComposeUiTest {
      val questionnaire =
        Questionnaire(
          id = "a-questionnaire",
          status = Enumeration(value = PublicationStatus.Active),
          item =
            listOf(
              Questionnaire.Item(
                linkId = FhirR4String(value = "a-link-id"),
                type = Enumeration(value = Questionnaire.QuestionnaireItemType.Boolean),
              )
            ),
        )
      setQuestionnaireContent(questionnaire, showNavigationLongScroll = false)

      onNode(
          hasTestTag(QUESTIONNAIRE_BOTTOM_NAVIGATION_TEST_TAG) and
            hasAnyAncestor(hasTestTag(QUESTIONNAIRE_EDIT_LIST))
        )
        .assertDoesNotExist()

      onNode(
          hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and
            hasText(getString(Res.string.submit_questionnaire))
        )
        .assertIsDisplayed()
        .assertIsEnabled()
    }

  @Test
  fun hideTheBottomNavigationContainerWhenSetShowNavigationInDefaultLongScrollIsSetToTrue() =
    runComposeUiTest {
      val questionnaire =
        Questionnaire(
          id = "a-questionnaire",
          status = Enumeration(value = PublicationStatus.Active),
          item =
            listOf(
              Questionnaire.Item(
                linkId = FhirR4String(value = "a-link-id"),
                type = Enumeration(value = Questionnaire.QuestionnaireItemType.Boolean),
              )
            ),
        )
      setQuestionnaireContent(questionnaire, showNavigationLongScroll = true)

      onNode(
          hasTestTag(QUESTIONNAIRE_BOTTOM_NAVIGATION_TEST_TAG) and
            hasAnyAncestor(hasTestTag(QUESTIONNAIRE_EDIT_LIST))
        )
        .assertExists()

      onNode(
          hasTestTag(QUESTIONNAIRE_PAGE_NAVIGATION_BUTTON_TEST_TAG) and
            hasText(getString(Res.string.submit_questionnaire))
        )
        .assertIsDisplayed()
        .assertIsEnabled()
    }

  @Test
  fun sliderViewShouldLimitToMaxWhenProgressIsSetAboveMaxValue() = runComposeUiTest {
    setQuestionnaireContent("files/component_slider.json")
    onNodeWithTag(SLIDER_TAG).performSemanticsAction(SemanticsActions.SetProgress) {
      it.invoke(20f)
    }
    onNodeWithTag(SLIDER_TAG)
      .assertRangeInfoEquals(ProgressBarRangeInfo(current = 15f, range = 0f..15f, steps = 14))
  }

  @Test
  fun sliderValueToShouldComeFromTheMaxValueExtension() = runComposeUiTest {
    setQuestionnaireContent("files/component_slider.json")
    onNodeWithTag(SLIDER_TAG)
      .assertRangeInfoEquals(ProgressBarRangeInfo(current = 0f, range = 0f..15f, steps = 14))
  }

  @Test
  fun sliderValueToShouldComeFromTheMaxValueExtensionWithCQF_calculatedValue() = runComposeUiTest {
    setQuestionnaireContent("files/component_slider_with_cqf_calculatedValue.json")
    onNodeWithTag(SLIDER_TAG)
      .assertRangeInfoEquals(ProgressBarRangeInfo(current = 0f, range = 0f..127f, steps = 126))
  }

  @Test
  fun calculatedExpression_shouldCalculateAge_fromBirthDate() = runComposeUiTest {
    setQuestionnaireContent(ageCalculationQuestionnaire(initialBirthDate = birthDateForAge30()))

    waitForCalculatedValue("30")
  }

  @Test
  fun calculatedExpression_shouldCalculateAge_fromQuestionnaireResponse() = runComposeUiTest {
    val questionnaireResponse =
      fhirJson.decodeFromString<QuestionnaireResponse>(
        """
        {
          "resourceType": "QuestionnaireResponse",
          "status": "in-progress",
          "item": [
            {
              "linkId": "birthdate",
              "answer": [{ "valueDate": "${birthDateForAge30()}" }]
            }
          ]
        }
        """
      )

    setQuestionnaireContent(
      ageCalculationQuestionnaire(),
      questionnaireResponse = questionnaireResponse,
    )

    waitForCalculatedValue("30")
  }

  @Test
  fun calculatedExpression_shouldUpdate_onlyWhenReferencedVariableChanges() = runComposeUiTest {
    // No debounce, so typed answers are committed without depending on how each target
    // advances the clock.
    setQuestionnaireContent(
      doubleWeightQuestionnaire(),
      dataCaptureConfig = DataCaptureConfig(textInputDebounce = Duration.ZERO),
    )
    waitForCalculatedValue("80")

    // Edit fields are rendered in item order: weight, notes, double-weight.
    enterText(fieldIndex = 1, text = "Unrelated note")
    onAllNodesWithTag(EDIT_TEXT_FIELD_TEST_TAG)[2].assertTextEquals("80")

    enterText(fieldIndex = 0, text = "50")
    waitForCalculatedValue("100")
  }

  private fun ComposeUiTest.enterText(fieldIndex: Int, text: String) {
    onAllNodesWithTag(EDIT_TEXT_FIELD_TEST_TAG)[fieldIndex].performTextReplacement(text)
    waitForIdle()
  }

  private fun doubleWeightQuestionnaire(): Questionnaire =
    fhirJson.decodeFromString<Questionnaire>(
      """
      {
        "resourceType": "Questionnaire",
        "status": "active",
        "item": [
          {
            "linkId": "weight",
            "text": "Weight",
            "type": "integer",
            "initial": [{ "valueInteger": 40 }]
          },
          {
            "linkId": "notes",
            "text": "Notes",
            "type": "string"
          },
          {
            "linkId": "double-weight",
            "text": "Double weight",
            "type": "integer",
            "extension": [
              {
                "url": "http://hl7.org/fhir/StructureDefinition/variable",
                "valueExpression": {
                  "name": "weight",
                  "language": "text/fhirpath",
                  "expression": "%resource.repeat(item).where(linkId='weight').answer.value"
                }
              },
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-calculatedExpression",
                "valueExpression": {
                  "language": "text/fhirpath",
                  "expression": "%weight * 2"
                }
              }
            ]
          }
        ]
      }
      """
    )

  private fun birthDateForAge30(): LocalDate =
    Clock.System.todayIn(TimeZone.currentSystemDefault())
      .minus(30, DateTimeUnit.YEAR)
      .minus(1, DateTimeUnit.DAY)

  private fun ageCalculationQuestionnaire(initialBirthDate: LocalDate? = null): Questionnaire {
    val initial = initialBirthDate?.let { """"initial": [{ "valueDate": "$it" }],""" } ?: ""
    return fhirJson.decodeFromString<Questionnaire>(
      """
      {
        "resourceType": "Questionnaire",
        "status": "active",
        "item": [
          {
            "linkId": "birthdate",
            "text": "Date of birth",
            $initial
            "type": "date"
          },
          {
            "linkId": "age",
            "text": "Age",
            "type": "integer",
            "extension": [
              {
                "url": "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-calculatedExpression",
                "valueExpression": {
                  "language": "text/fhirpath",
                  "expression": "%resource.repeat(item).where(linkId='birthdate' and answer.empty().not()).select(today().toString().substring(0,4).toInteger() - answer.value.toString().substring(0,4).toInteger() + iif(today().toString().substring(5,2) & today().toString().substring(8,2) > answer.value.toString().substring(5,2) & answer.value.toString().substring(8,2), 1, 0) - 1)"
                }
              }
            ]
          }
        ]
      }
      """
    )
  }

  private fun ComposeUiTest.waitForCalculatedValue(value: String) {
    waitUntil(timeoutMillis = 5_000) {
      onAllNodes(hasTestTag(EDIT_TEXT_FIELD_TEST_TAG) and hasText(value))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
  }

  private suspend fun ComposeUiTest.setQuestionnaireContent(
    fileName: String,
    isReviewMode: Boolean = false,
    responseFileName: String? = null,
  ) {
    val questionnaireJsonString = Res.readBytes(fileName).decodeToString()
    val questionnaireResponseJsonString =
      responseFileName?.let { Res.readBytes(it).decodeToString() }

    val testLifecycleOwner = TestLifecycleOwner(Lifecycle.State.RESUMED)
    setContent {
      val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
          override val viewModelStore = ViewModelStore()
        }
      }

      CompositionLocalProvider(
        LocalLifecycleOwner provides testLifecycleOwner,
        LocalViewModelStoreOwner provides viewModelStoreOwner,
      ) {
        Questionnaire(
          questionnaireJson = questionnaireJsonString,
          questionnaireResponseJson = questionnaireResponseJsonString,
          config = QuestionnaireConfig(showCancelButton = true, showReviewPage = isReviewMode),
          onSubmit = { _ -> },
          onCancel = {},
        )
      }
    }
  }

  private suspend fun ComposeUiTest.setQuestionnaireContent(
    questionnaire: Questionnaire,
    questionnaireResponse: QuestionnaireResponse? = null,
    isReviewMode: Boolean = false,
    showReviewPageFirst: Boolean = false,
    showNavigationLongScroll: Boolean = false,
    submitText: String? = null,
    dataCaptureConfig: DataCaptureConfig = DataCaptureConfig(),
  ) {
    val questionnaireJsonString = fhirJson.encodeToString(questionnaire)

    val testLifecycleOwner = TestLifecycleOwner(Lifecycle.State.RESUMED)
    setContent {
      val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
          override val viewModelStore = ViewModelStore()
        }
      }

      CompositionLocalProvider(
        LocalLifecycleOwner provides testLifecycleOwner,
        LocalViewModelStoreOwner provides viewModelStoreOwner,
        LocalDataCaptureConfig provides dataCaptureConfig,
      ) {
        Questionnaire(
          questionnaireJson = questionnaireJsonString,
          questionnaireResponseJson = questionnaireResponse?.let { fhirJson.encodeToString(it) },
          config =
            QuestionnaireConfig(
              showReviewPage = isReviewMode,
              showReviewPageFirst = showReviewPageFirst,
              showNavigationLongScroll = showNavigationLongScroll,
              submitButtonText = submitText,
            ),
          onSubmit = { _ -> },
          onCancel = {},
        )
      }
    }
  }
}
