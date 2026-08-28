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

/**
 * Memoizes expression evaluation for the duration of a single questionnaire state computation.
 *
 * Off by default. [activate] / [deactivate] / [invalidate] match the android-fhir cache: only
 * switched on for the span of building questionnaire state, where answers are not written.
 */
internal class QuestionnaireExpressionCache {

  var isActive = false
    private set

  private val expressionResults = mutableMapOf<String, List<Any>>()

  fun activate() {
    invalidate()
    isActive = true
  }

  fun deactivate() {
    isActive = false
    invalidate()
  }

  fun invalidate() {
    expressionResults.clear()
  }

  fun cachedResult(key: String): List<Any>? = if (isActive) expressionResults[key] else null

  fun cacheResult(key: String, result: List<Any>) {
    if (isActive) expressionResults[key] = result
  }
}
