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
package dev.ohs.fhir.datacapture.validation

import dev.ohs.fhir.model.r4.Questionnaire
import dev.ohs.fhir.model.r4.QuestionnaireResponse
import kotlin_fhir_data_capture.datacapture.generated.resources.Res
import kotlin_fhir_data_capture.datacapture.generated.resources.required_constraint_validation_error_msg
import org.jetbrains.compose.resources.getString

internal object RequiredValidator : QuestionnaireResponseItemConstraintValidator {
  override suspend fun validate(
    questionnaireItem: Questionnaire.Item,
    questionnaireResponseItem: QuestionnaireResponse.Item,
  ): List<ConstraintValidator.Result> {
    if (
      questionnaireItem.required?.value != true ||
        questionnaireResponseItem.answer.any { it.value != null }
    ) {
      return listOf(ConstraintValidator.Result(true, null))
    }
    return listOf(
      ConstraintValidator.Result(
        false,
        getString(Res.string.required_constraint_validation_error_msg),
      )
    )
  }
}
