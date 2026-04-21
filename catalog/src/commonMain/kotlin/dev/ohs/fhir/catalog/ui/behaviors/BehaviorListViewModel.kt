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

package dev.ohs.fhir.catalog.ui.behaviors

import androidx.lifecycle.ViewModel
import dev.ohs.fhir.catalog.generated.resources.Res
import dev.ohs.fhir.catalog.generated.resources.behavior_name_answer_expression
import dev.ohs.fhir.catalog.generated.resources.behavior_name_calculated_expression
import dev.ohs.fhir.catalog.generated.resources.behavior_name_context_variables
import dev.ohs.fhir.catalog.generated.resources.behavior_name_dynamic_question_text
import dev.ohs.fhir.catalog.generated.resources.behavior_name_questionnaire_constraint
import dev.ohs.fhir.catalog.generated.resources.behavior_name_skip_logic
import dev.ohs.fhir.catalog.generated.resources.behavior_name_skip_logic_with_expression
import dev.ohs.fhir.catalog.generated.resources.ic_answers_behavior
import dev.ohs.fhir.catalog.generated.resources.ic_calculations_behavior
import dev.ohs.fhir.catalog.generated.resources.ic_context
import dev.ohs.fhir.catalog.generated.resources.ic_dynamic_text_behavior
import dev.ohs.fhir.catalog.generated.resources.ic_rule
import dev.ohs.fhir.catalog.generated.resources.ic_skiplogic_behavior
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

class BehaviorListViewModel : ViewModel() {

  fun getBehaviorList(): List<Behavior> {
    return Behavior.entries
  }

  enum class Behavior(
    val questionnaireFileName: String,
    val icon: DrawableResource,
    val text: StringResource,
  ) {
    CALCULATED_EXPRESSION(
      "behavior_calculated_expression.json",
      Res.drawable.ic_calculations_behavior,
      Res.string.behavior_name_calculated_expression,
    ),
    ANSWER_EXPRESSION(
      "behavior_answer_expression.json",
      Res.drawable.ic_answers_behavior,
      Res.string.behavior_name_answer_expression,
    ),
    CONTEXT_VARIABLES(
      "behavior_context_variables.json",
      Res.drawable.ic_context,
      Res.string.behavior_name_context_variables,
    ),
    SKIP_LOGIC(
      "behavior_skip_logic.json",
      Res.drawable.ic_skiplogic_behavior,
      Res.string.behavior_name_skip_logic,
    ),
    SKIP_LOGIC_WITH_EXPRESSION(
      "behavior_skip_logic_with_expression.json",
      Res.drawable.ic_skiplogic_behavior,
      Res.string.behavior_name_skip_logic_with_expression,
    ),
    DYNAMIC_QUESTION_TEXT(
      "behavior_dynamic_question_text.json",
      Res.drawable.ic_dynamic_text_behavior,
      Res.string.behavior_name_dynamic_question_text,
    ),
    QUESTIONNAIRE_CONSTRAINT(
      "behavior_questionnaire_constraint.json",
      Res.drawable.ic_rule,
      Res.string.behavior_name_questionnaire_constraint,
    ),
  }
}
