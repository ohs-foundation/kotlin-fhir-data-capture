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
package dev.ohs.fhir.datacapture.fhirpath

import dev.ohs.fhir.model.r4.Patient
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for [FhirPathService]'s FHIRPath dateTime/time-to-string conversion.
 *
 * These specifically target the whole-seconds/nanoseconds decomposition of the FHIRPath
 * `dateTime`/`time` types' `BigDecimal` second component (see
 * https://github.com/ohs-foundation/kotlin-fhir-data-capture/pull/47), since that decomposition is
 * not otherwise exercised by fixtures that only use whole-second timestamps.
 */
class FhirPathServiceTest {
  // A pure FHIRPath literal expression does not read from the resource context, so any resource
  // works as the evaluation target.
  private val resource = Patient.Builder().build()

  private fun evaluate(expression: String) =
    FhirPathService.evaluateFhirPathToString(expression, resource)

  @Test
  fun evaluateFhirPathToString_dateTimeWithFractionalSecondsAndOffset_preservesFraction() {
    assertEquals("2024-01-15T10:30:45.123+05:00", evaluate("@2024-01-15T10:30:45.123+05:00"))
  }

  @Test
  fun evaluateFhirPathToString_dateTimeWithWholeSecondsAndOffset_hasNoFraction() {
    assertEquals("2024-01-15T10:30:07+05:00", evaluate("@2024-01-15T10:30:07+05:00"))
  }

  @Test
  fun evaluateFhirPathToString_dateTimeWithFractionalSecondsAndNoOffset_preservesFraction() {
    assertEquals("2024-01-15T10:30:45.123", evaluate("@2024-01-15T10:30:45.123"))
  }

  @Test
  fun evaluateFhirPathToString_dateTimeWithWholeSecondsAndNoOffset_hasNoFraction() {
    assertEquals("2024-01-15T10:30:07", evaluate("@2024-01-15T10:30:07"))
  }

  @Test
  fun evaluateFhirPathToString_timeWithFractionalSeconds_preservesFraction() {
    assertEquals("10:30:45.123", evaluate("@T10:30:45.123"))
  }

  @Test
  fun evaluateFhirPathToString_timeWithWholeSeconds_hasNoFraction() {
    assertEquals("10:30:07", evaluate("@T10:30:07"))
  }
}
