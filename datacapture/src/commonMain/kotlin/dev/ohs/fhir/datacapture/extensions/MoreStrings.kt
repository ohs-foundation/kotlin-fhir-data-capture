/*
 * Copyright 2025-2026 Open Health Stack Foundation
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

import androidx.compose.ui.text.AnnotatedString
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal fun String.toAnnotatedString(): AnnotatedString = AnnotatedString(this)

internal fun String.toBigDecimalOrNull(): BigDecimal? =
  try {
    this.toBigDecimal()
  } catch (_: NumberFormatException) {
    null
  } catch (_: ArithmeticException) {
    null
  }

/** FHIRPath variables are referenced with `%`, while lookup maps store the bare identifier. */
internal fun String.normalizedVariableName(): String = removePrefix("%")

private val questionnaireResponseResourceReferenceRegex =
  Regex("""(?<![A-Za-z0-9_'])%resource(?![A-Za-z0-9_'])""")

internal fun String.referencesQuestionnaireResponseResource(): Boolean =
  questionnaireResponseResourceReferenceRegex.containsMatchIn(this)

internal const val EXTENSION_EXTRACT_ALLOCATE_ID_URL: String =
  "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId"

@OptIn(ExperimentalUuidApi::class)
internal fun generateAllocatedFullUrl(): String = "urn:uuid:${Uuid.random()}"
