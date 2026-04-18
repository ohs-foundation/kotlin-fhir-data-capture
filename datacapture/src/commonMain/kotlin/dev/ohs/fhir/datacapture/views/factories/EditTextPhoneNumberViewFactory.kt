/*
 * Copyright 2022-2026 Google LLC
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

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.google.fhir.model.r4.QuestionnaireResponse
import dev.ohs.fhir.datacapture.extensions.FhirR4String
import dev.ohs.fhir.datacapture.generated.resources.Res
import dev.ohs.fhir.datacapture.generated.resources.decimal_format_validation_error_msg

internal val EditTextPhoneNumberViewFactory =
  EditTextViewFactoryDelegate(
    KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
    uiInputText = { it.answers.singleOrNull()?.value?.asString()?.value?.value ?: "" },
    handleInput = { inputText, questionnaireViewItem ->
      val input =
        inputText.let {
          if (it.isEmpty()) {
            null
          } else {
            QuestionnaireResponse.Item.Answer(
              value =
                QuestionnaireResponse.Item.Answer.Value.String(value = FhirR4String(value = it)),
            )
          }
        }

      if (input != null) {
        questionnaireViewItem.setAnswer(input)
      } else {
        questionnaireViewItem.clearAnswer()
      }
    },
    validationMessageStringRes = Res.string.decimal_format_validation_error_msg,
  )
