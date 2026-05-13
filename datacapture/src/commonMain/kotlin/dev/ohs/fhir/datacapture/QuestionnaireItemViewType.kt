/*
 * Copyright 2023-2026 Google LLC
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

/**
 * Questionnaire item view holder types supported by default by the data capture library.
 *
 * This is used by the [dev.ohs.fhir.datacapture.QuestionnaireEditList] lists to determine how each
 * [dev.ohs.fhir.model.r4.Questionnaire.Item] is rendered.
 *
 * This list should provide sufficient coverage for values in
 * https://www.hl7.org/fhir/valueset-item-type.html and
 * http://hl7.org/fhir/R4/valueset-questionnaire-item-control.html.
 */
enum class QuestionnaireItemViewType {
  GROUP,
  BOOLEAN_TYPE_PICKER,
  DATE_PICKER,
  DATE_TIME_PICKER,
  EDIT_TEXT_SINGLE_LINE,
  EDIT_TEXT_MULTI_LINE,
  EDIT_TEXT_INTEGER,
  EDIT_TEXT_DECIMAL,
  RADIO_GROUP,
  DROP_DOWN,
  DISPLAY,
  QUANTITY,
  CHECK_BOX_GROUP,
  AUTO_COMPLETE,
  DIALOG_SELECT,
  SLIDER,
  PHONE_NUMBER,
  ATTACHMENT,
  TIME_PICKER;
}
