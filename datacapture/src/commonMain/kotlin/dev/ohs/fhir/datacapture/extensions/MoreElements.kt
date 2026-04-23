/*
 * Copyright 2026 Google LLC
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.ohs.fhir.model.r4.Attachment
import dev.ohs.fhir.model.r4.Coding
import dev.ohs.fhir.model.r4.Date
import dev.ohs.fhir.model.r4.DateTime
import dev.ohs.fhir.model.r4.Decimal
import dev.ohs.fhir.model.r4.Element
import dev.ohs.fhir.model.r4.Expression
import dev.ohs.fhir.model.r4.FhirDate
import dev.ohs.fhir.model.r4.FhirDateTime
import dev.ohs.fhir.model.r4.Quantity
import dev.ohs.fhir.model.r4.Reference
import dev.ohs.fhir.model.r4.Time
import dev.ohs.fhir.datacapture.generated.resources.Res
import dev.ohs.fhir.datacapture.generated.resources.no
import dev.ohs.fhir.datacapture.generated.resources.yes
import dev.ohs.fhir.datacapture.getLocalDateTimeFormatter
import org.jetbrains.compose.resources.stringResource

internal const val EXTENSION_CQF_EXPRESSION_URL: String =
  "http://hl7.org/fhir/StructureDefinition/cqf-expression"

@get:Composable
internal val Element.displayString: String?
  get() {
    return when (this) {
      is Coding -> remember(this) { display?.getLocalizedText() ?: code?.value }
      is DateTime -> {
        val localDateFormatter = getLocalDateTimeFormatter()
        remember(this) {
          val localDateTime = (value as? FhirDateTime.DateTime)?.dateTime
          "${localDateTime?.date?.let { localDateFormatter.format(it) }} ${
                        localDateTime?.time?.let {
                            localDateFormatter.localizedTimeString(
                                it,
                            )
                        }
                    }"
        }
      }
      is Date -> {
        val localDateFormatter = getLocalDateTimeFormatter()
        remember(this) {
          val localDate = (value as? FhirDate.Date)?.date
          localDate?.let { localDateFormatter.format(it) }
        }
      }
      is Time -> {
        val localDateFormatter = getLocalDateTimeFormatter()
        remember(this) { value?.let { localDateFormatter.localizedTimeString(it) } }
      }
      is FhirR4Integer -> remember(this) { value?.toString() }
      is Reference -> remember(this) { display?.value ?: reference?.value }
      is FhirR4String -> remember(this) { getLocalizedText() }
      is Attachment -> remember(this) { url?.value ?: title?.value }
      is FhirR4Boolean -> {
        val yesStringText = stringResource(Res.string.yes)
        val noStringText = stringResource(Res.string.no)

        remember(this) { value?.let { if (it) yesStringText else noStringText } }
      }
      is Quantity -> remember(this) { value?.value?.toStringExpanded() }
      is Decimal -> remember(this) { value?.toStringExpanded() }
      else -> remember(this) { null }
    }
  }

internal val Element.cqfExpression: Expression?
  get() =
    this.extension.find { it.url == EXTENSION_CQF_EXPRESSION_URL }?.value?.asExpression()?.value

internal operator fun Element.compareTo(other: Element): Int {
  if (this::class != other::class) {
    throw IllegalArgumentException(
      "Cannot compare different data types: ${this::class} and ${other::class}",
    )
  }

  return when (this) {
    is FhirR4Integer -> {
      other as FhirR4Integer
      this.value!!.compareTo(other.value!!)
    }
    is FhirR4Decimal -> {
      other as FhirR4Decimal
      this.value!!.compareTo(other.value!!)
    }
    is FhirR4DateType -> {
      other as FhirR4DateType
      this.value.toString().compareTo(other.value.toString())
    }
    is DateTime -> {
      other as DateTime
      this.value.toString().compareTo(other.value.toString())
    }
    is Time -> {
      other as Time
      this.value!!.compareTo(other.value!!)
    }
    is Quantity -> {
      other as Quantity
      if (this.code != other.code) {
        throw IllegalArgumentException(
          "Cannot compare different quantity codes: ${this.code} and ${other.code}",
        )
      }
      this.value!!.value!!.compareTo(other.value!!.value!!)
    }
    else -> throw IllegalArgumentException("Comparison not supported for type :$this")
  }
}
