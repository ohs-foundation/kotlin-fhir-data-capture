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
package dev.ohs.fhir.datacapture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import dev.ohs.fhir.datacapture.extensions.length
import java.text.ParseException
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalTime
import kotlinx.datetime.toKotlinLocalDate

object JVMLocalDateTimeFormatter : LocalDateTimeFormatter {
  override fun parseStringToLocalDate(str: String, pattern: String): LocalDate {
    val localDate =
      java.time.LocalDate.parse(
        str,
        DateTimeFormatter.ofPattern(pattern, Locale.current.platformLocale),
      )

    // Throw ParseException if year is less or more than 4 digits.
    val yearLengthDiff = localDate.year.length() - 4
    if (yearLengthDiff != 0) {
      throw ParseException(
        "Year has ${if (yearLengthDiff < 0) "less than" else "more than" } 4 digits.",
        str.indexOf('y'),
      )
    }

    return localDate.toKotlinLocalDate()
  }

  override fun format(localDate: LocalDate, pattern: String?): String =
    if (pattern.isNullOrEmpty()) {
      DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(localDate.toJavaLocalDate())
    } else {
      DateTimeFormatter.ofPattern(pattern).format(localDate.toJavaLocalDate())
    }

  override val localDateShortFormatPattern: String
    get() =
      DateTimeFormatterBuilder.getLocalizedDateTimePattern(
        FormatStyle.SHORT,
        null,
        IsoChronology.INSTANCE,
        Locale.current.platformLocale,
      )

  override fun localizedTimeString(time: LocalTime): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(time.toJavaLocalTime())
}

@Composable
actual fun getLocalDateTimeFormatter(): LocalDateTimeFormatter = remember {
  JVMLocalDateTimeFormatter
}
