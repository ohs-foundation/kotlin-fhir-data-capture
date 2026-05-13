/*
 * Copyright 2024-2026 Google LLC
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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ohs.fhir.datacapture.extensions.displayString
import dev.ohs.fhir.datacapture.extensions.elementValue
import dev.ohs.fhir.datacapture.extensions.getLocalizedInstructionsAnnotatedString
import dev.ohs.fhir.datacapture.extensions.itemControl
import dev.ohs.fhir.datacapture.extensions.localizedFlyoverAnnotatedString
import dev.ohs.fhir.datacapture.extensions.localizedPrefixAnnotatedString
import dev.ohs.fhir.datacapture.extensions.shouldUseDialog
import dev.ohs.fhir.datacapture.theme.QuestionnaireTheme
import dev.ohs.fhir.datacapture.validation.Invalid
import dev.ohs.fhir.datacapture.views.QuestionnaireViewItem
import dev.ohs.fhir.datacapture.views.components.QuestionnaireBottomNavigation
import dev.ohs.fhir.datacapture.views.components.RepeatedGroupAddButtonItem
import dev.ohs.fhir.datacapture.views.components.RepeatedGroupHeaderItem
import dev.ohs.fhir.datacapture.views.factories.AttachmentViewFactory
import dev.ohs.fhir.datacapture.views.factories.AutoCompleteViewFactory
import dev.ohs.fhir.datacapture.views.factories.BooleanChoiceViewFactory
import dev.ohs.fhir.datacapture.views.factories.CheckBoxGroupViewFactory
import dev.ohs.fhir.datacapture.views.factories.DateTimeViewFactory
import dev.ohs.fhir.datacapture.views.factories.DateViewFactory
import dev.ohs.fhir.datacapture.views.factories.DialogSelectViewFactory
import dev.ohs.fhir.datacapture.views.factories.DisplayViewFactory
import dev.ohs.fhir.datacapture.views.factories.DropDownViewFactory
import dev.ohs.fhir.datacapture.views.factories.DecimalTextInputFactory
import dev.ohs.fhir.datacapture.views.factories.IntegerTextInputFactory
import dev.ohs.fhir.datacapture.views.factories.MultiLineTextInputFactory
import dev.ohs.fhir.datacapture.views.factories.PhoneNumberTextInputFactory
import dev.ohs.fhir.datacapture.views.factories.SingleLineTextInputFactory
import dev.ohs.fhir.datacapture.views.factories.GroupViewFactory
import dev.ohs.fhir.datacapture.views.factories.QuantityViewFactory
import dev.ohs.fhir.datacapture.views.factories.QuestionnaireItemViewFactory
import dev.ohs.fhir.datacapture.views.factories.RadioGroupViewFactory
import dev.ohs.fhir.datacapture.views.factories.SliderViewFactory
import dev.ohs.fhir.datacapture.views.factories.TimeViewFactory
import dev.ohs.fhir.model.r4.Questionnaire
import kotlin.uuid.ExperimentalUuidApi
import kotlin_fhir_data_capture.datacapture.generated.resources.Res
import kotlin_fhir_data_capture.datacapture.generated.resources.not_answered
import kotlin_fhir_data_capture.datacapture.generated.resources.warning_filled_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

// Choice questions are rendered as dialogs if they have at least this many options
const val MINIMUM_NUMBER_OF_ANSWER_OPTIONS_FOR_DIALOG = 10

// Choice questions are rendered as radio group if number of options less than this constant
const val MINIMUM_NUMBER_OF_ANSWER_OPTIONS_FOR_DROP_DOWN = 4

// Test tag for QuestionnaireEditList
const val QUESTIONNAIRE_EDIT_LIST = "questionnaire_edit_list"

private const val NAVIGATION_ITEM_KEY = "navigation"

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun QuestionnaireEditList(
  items: List<QuestionnaireAdapterItem>,
  displayMode: DisplayMode,
  questionnaireItemViewHolderMatchers: List<QuestionnaireItemViewFactoryMatcher>,
  onUpdateProgressIndicator: (Int, Int) -> Unit,
) {
  val listState = rememberLazyListState()
  LaunchedEffect(listState) {
    if (displayMode is DisplayMode.EditMode && !displayMode.pagination.isPaginated) {
      snapshotFlow {
          val layoutInfo = listState.layoutInfo
          val visibleItems = layoutInfo.visibleItemsInfo
          val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
          val total = layoutInfo.totalItemsCount

          // If all items are visible, we're at 100%
          if (visibleItems.size >= total && total > 0) {
            total to total
          } else {
            lastVisible + 1 to total
          }
        }
        .collect { (visibleCount, total) -> onUpdateProgressIndicator(visibleCount, total) }
    }
  }
  LazyColumn(state = listState, modifier = Modifier.testTag(QUESTIONNAIRE_EDIT_LIST)) {
    items(
      items = items,
      key = { item ->
        when (item) {
          is QuestionnaireAdapterItem.Question ->
            item.id ?: throw IllegalStateException("Missing id for the Question: $item")

          is QuestionnaireAdapterItem.RepeatedGroupHeader -> item.id

          is QuestionnaireAdapterItem.Navigation -> NAVIGATION_ITEM_KEY

          is QuestionnaireAdapterItem.RepeatedGroupAddButton ->
            item.id
              ?: throw IllegalStateException("Missing id for the RepeatedGroupAddButton: $item")
        }
      },
      contentType = { it::class.simpleName },
    ) { adapterItem: QuestionnaireAdapterItem ->
      when (adapterItem) {
        is QuestionnaireAdapterItem.Question -> {
          val questionnaireViewHolderType = getItemViewTypeForQuestion(adapterItem.item)
          val questionnaireItemViewHolderDelegate =
            getQuestionnaireItemViewFactory(
              questionnaireItem = adapterItem.item.questionnaireItem,
              questionnaireItemViewType = questionnaireViewHolderType,
              questionnaireItemViewFactoryMatchers = questionnaireItemViewHolderMatchers,
            )
          questionnaireItemViewHolderDelegate.Content(adapterItem.item)
        }

        is QuestionnaireAdapterItem.Navigation -> {
          QuestionnaireBottomNavigation(adapterItem.questionnaireNavigationUIState)
        }

        is QuestionnaireAdapterItem.RepeatedGroupHeader -> {
          RepeatedGroupHeaderItem(adapterItem)
        }

        is QuestionnaireAdapterItem.RepeatedGroupAddButton -> {
          RepeatedGroupAddButtonItem(adapterItem.item)
        }
      }
    }
  }
}

@Composable
internal fun QuestionnaireReviewList(items: List<QuestionnaireReviewItem>) {
  LazyColumn {
    items(
      items = items,
      key = { item ->
        when (item) {
          is QuestionnaireAdapterItem.Question ->
            item.id ?: throw IllegalStateException("Missing id for the Question: $item")

          is QuestionnaireAdapterItem.Navigation -> NAVIGATION_ITEM_KEY
        }
      },
      contentType = { it::class.simpleName },
    ) { item: QuestionnaireReviewItem ->
      when (item) {
        is QuestionnaireAdapterItem.Question -> {
          QuestionnaireReviewItem(
            questionnaireViewItem = item.item,
            modifier = Modifier.fillMaxWidth(),
          )
        }

        is QuestionnaireAdapterItem.Navigation -> {
          QuestionnaireBottomNavigation(
            pageNavigationUIState = item.questionnaireNavigationUIState,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@Composable
private fun QuestionnaireReviewItem(
  questionnaireViewItem: QuestionnaireViewItem,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
    // Header section with prefix, question, and hint
    val prefixText =
      remember(questionnaireViewItem.questionnaireItem.prefix) {
        questionnaireViewItem.questionnaireItem.localizedPrefixAnnotatedString ?: ""
      }
    val viewItemQuestionText =
      remember(questionnaireViewItem) { questionnaireViewItem.questionText ?: "" }
    val hintText =
      remember(questionnaireViewItem) {
        questionnaireViewItem.enabledDisplayItems.getLocalizedInstructionsAnnotatedString()
      }

    if (prefixText.isNotBlank() || viewItemQuestionText.isNotBlank() || hintText.isNotBlank()) {
      Column {
        // Question with optional prefix
        val questionText = buildAnnotatedString {
          append(prefixText)
          if (viewItemQuestionText.isNotBlank()) append(" ")
          append(viewItemQuestionText)
        }

        if (questionText.isNotBlank()) {
          Text(
            text = questionText,
            style = QuestionnaireTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = QuestionnaireTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp),
          )
        }

        // Hint/instructions
        if (hintText.isNotBlank()) {
          Text(
            text = hintText,
            style = QuestionnaireTheme.typography.bodyMedium,
            color = QuestionnaireTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
          )
        }
      }
    }

    val flyOverText =
      remember(questionnaireViewItem) {
        questionnaireViewItem.enabledDisplayItems.localizedFlyoverAnnotatedString
          ?: AnnotatedString("")
      }

    // Flyover text
    if (flyOverText.isNotBlank()) {
      Text(
        text = flyOverText,
        style = QuestionnaireTheme.typography.bodyMedium,
        color = QuestionnaireTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
      )
    }

    // Answer section (only for non-group, non-display items)
    when (questionnaireViewItem.questionnaireItem.type.value) {
      Questionnaire.QuestionnaireItemType.Group,
      Questionnaire.QuestionnaireItemType.Display -> {
        // No answer display for groups and display items
      }

      else -> {
        val notAnsweredTextString = stringResource(Res.string.not_answered)
        val answerText =
          questionnaireViewItem.answers
            .map { it.elementValue?.displayString ?: "" }
            .joinToString()
            .ifBlank { notAnsweredTextString }

        if (answerText.isNotBlank()) {
          Text(
            text = answerText,
            style = QuestionnaireTheme.typography.bodyLarge,
            color = QuestionnaireTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
          )
        }

        // Error display
        if (questionnaireViewItem.validationResult is Invalid) {
          Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              painter = painterResource(Res.drawable.warning_filled_24dp),
              contentDescription = "Error",
              tint = QuestionnaireTheme.colorScheme.error,
            )
            Text(
              text = questionnaireViewItem.validationResult.singleStringValidationMessage,
              style = QuestionnaireTheme.typography.bodyMedium,
              color = QuestionnaireTheme.colorScheme.error,
            )
          }
        }
      }
    }

    // Divider
    val showDivider =
      remember(
        prefixText,
        viewItemQuestionText,
        hintText,
        flyOverText,
        questionnaireViewItem.questionnaireItem.type,
      ) {
        prefixText.isNotBlank() ||
          viewItemQuestionText.isNotBlank() ||
          hintText.isNotBlank() ||
          flyOverText.isNotBlank() ||
          questionnaireViewItem.questionnaireItem.type.value !in
            arrayOf(
              Questionnaire.QuestionnaireItemType.Group,
              Questionnaire.QuestionnaireItemType.Display,
            )
      }

    if (showDivider) {
      HorizontalDivider(
        modifier = Modifier.padding(top = 16.dp),
        color = QuestionnaireTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
      )
    }
  }
}

fun getQuestionnaireItemViewFactory(
  questionnaireItem: Questionnaire.Item,
  questionnaireItemViewType: QuestionnaireItemViewType,
  questionnaireItemViewFactoryMatchers: List<QuestionnaireItemViewFactoryMatcher>,
): QuestionnaireItemViewFactory =
  questionnaireItemViewFactoryMatchers.find { it.matches(questionnaireItem) }?.factory
    ?: when (questionnaireItemViewType) {
      QuestionnaireItemViewType.EDIT_TEXT_SINGLE_LINE -> SingleLineTextInputFactory
      QuestionnaireItemViewType.EDIT_TEXT_MULTI_LINE -> MultiLineTextInputFactory
      QuestionnaireItemViewType.EDIT_TEXT_INTEGER -> IntegerTextInputFactory
      QuestionnaireItemViewType.EDIT_TEXT_DECIMAL -> DecimalTextInputFactory
      QuestionnaireItemViewType.QUANTITY -> QuantityViewFactory
      QuestionnaireItemViewType.DISPLAY -> DisplayViewFactory
      QuestionnaireItemViewType.SLIDER -> SliderViewFactory
      QuestionnaireItemViewType.PHONE_NUMBER -> PhoneNumberTextInputFactory
      QuestionnaireItemViewType.BOOLEAN_TYPE_PICKER -> BooleanChoiceViewFactory
      QuestionnaireItemViewType.RADIO_GROUP -> RadioGroupViewFactory
      QuestionnaireItemViewType.CHECK_BOX_GROUP -> CheckBoxGroupViewFactory
      QuestionnaireItemViewType.DIALOG_SELECT -> DialogSelectViewFactory
      QuestionnaireItemViewType.DROP_DOWN -> DropDownViewFactory
      QuestionnaireItemViewType.AUTO_COMPLETE -> AutoCompleteViewFactory
      QuestionnaireItemViewType.DATE_PICKER -> DateViewFactory
      QuestionnaireItemViewType.TIME_PICKER -> TimeViewFactory
      QuestionnaireItemViewType.DATE_TIME_PICKER -> DateTimeViewFactory
      QuestionnaireItemViewType.GROUP -> GroupViewFactory
      QuestionnaireItemViewType.ATTACHMENT -> AttachmentViewFactory
    }

/**
 * Returns the [QuestionnaireItemViewType] that will be used to render the
 * [QuestionnaireViewItem]. This is determined by a combination of the data type of the question and
 * any additional Questionnaire Item UI Control Codes
 * (http://hl7.org/fhir/R4/valueset-questionnaire-item-control.html) used in the itemControl
 * extension (http://hl7.org/fhir/R4/extension-questionnaire-itemcontrol.html).
 */
private fun getItemViewTypeForQuestion(
  questionnaireViewItem: QuestionnaireViewItem
): QuestionnaireItemViewType {
  val questionnaireItem = questionnaireViewItem.questionnaireItem

  if (questionnaireViewItem.enabledAnswerOptions.isNotEmpty()) {
    return getChoiceViewHolderType(questionnaireViewItem)
  }

  return when (val type = questionnaireItem.type.value) {
    Questionnaire.QuestionnaireItemType.Group -> QuestionnaireItemViewType.GROUP

    Questionnaire.QuestionnaireItemType.Boolean -> QuestionnaireItemViewType.BOOLEAN_TYPE_PICKER

    Questionnaire.QuestionnaireItemType.Date -> QuestionnaireItemViewType.DATE_PICKER

    Questionnaire.QuestionnaireItemType.Time -> QuestionnaireItemViewType.TIME_PICKER

    Questionnaire.QuestionnaireItemType.DateTime -> QuestionnaireItemViewType.DATE_TIME_PICKER

    Questionnaire.QuestionnaireItemType.String -> getStringViewHolderType(questionnaireViewItem)

    Questionnaire.QuestionnaireItemType.Text -> QuestionnaireItemViewType.EDIT_TEXT_MULTI_LINE

    Questionnaire.QuestionnaireItemType.Integer -> getIntegerViewHolderType(questionnaireViewItem)

    Questionnaire.QuestionnaireItemType.Decimal -> QuestionnaireItemViewType.EDIT_TEXT_DECIMAL

    Questionnaire.QuestionnaireItemType.Choice,
    Questionnaire.QuestionnaireItemType.Reference -> getChoiceViewHolderType(questionnaireViewItem)

    Questionnaire.QuestionnaireItemType.Display -> QuestionnaireItemViewType.DISPLAY

    Questionnaire.QuestionnaireItemType.Quantity -> QuestionnaireItemViewType.QUANTITY

    Questionnaire.QuestionnaireItemType.Attachment -> QuestionnaireItemViewType.ATTACHMENT

    else -> throw NotImplementedError("Question type $type not supported.")
  }
}

private fun getChoiceViewHolderType(
  questionnaireViewItem: QuestionnaireViewItem
): QuestionnaireItemViewType {
  val questionnaireItem = questionnaireViewItem.questionnaireItem

  // Use the view type that the client wants if they specified an itemControl or dialog extension
  return when {
    questionnaireItem.shouldUseDialog -> QuestionnaireItemViewType.DIALOG_SELECT
    else -> questionnaireItem.itemControl?.viewHolderType
  }
    // Otherwise, choose a sensible UI element automatically
    ?: run {
      val numOptions = questionnaireViewItem.enabledAnswerOptions.size
      when {
        // Always use a dialog for questions with a large number of options
        numOptions >= MINIMUM_NUMBER_OF_ANSWER_OPTIONS_FOR_DIALOG ->
          QuestionnaireItemViewType.DIALOG_SELECT

        // Use a check box group if repeated answers are permitted
        questionnaireItem.repeats?.value == true -> QuestionnaireItemViewType.CHECK_BOX_GROUP

        // Use a dropdown if there are a medium number of options
        numOptions >= MINIMUM_NUMBER_OF_ANSWER_OPTIONS_FOR_DROP_DOWN ->
          QuestionnaireItemViewType.DROP_DOWN

        // Use a radio group only if there are a small number of options
        else -> QuestionnaireItemViewType.RADIO_GROUP
      }
    }
}

private fun getIntegerViewHolderType(
  questionnaireViewItem: QuestionnaireViewItem
): QuestionnaireItemViewType {
  val questionnaireItem = questionnaireViewItem.questionnaireItem
  // Use the view type that the client wants if they specified an itemControl
  return questionnaireItem.itemControl?.viewHolderType
    ?: QuestionnaireItemViewType.EDIT_TEXT_INTEGER
}

private fun getStringViewHolderType(
  questionnaireViewItem: QuestionnaireViewItem
): QuestionnaireItemViewType {
  val questionnaireItem = questionnaireViewItem.questionnaireItem
  // Use the view type that the client wants if they specified an itemControl
  return questionnaireItem.itemControl?.viewHolderType
    ?: QuestionnaireItemViewType.EDIT_TEXT_SINGLE_LINE
}
