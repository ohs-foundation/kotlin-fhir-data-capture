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
package dev.ohs.fhir.datacapture.extraction

class DefaultTemplateRepository : TemplateRepository {
  /** Builds a fresh engine instance and resolves one template execution request end to end. */
  override fun resolve(request: TemplateExecutionRequest): TemplateExecutionResult {
    val engine = TemplateEngine(options = request.options, strict = request.strict)
    return TemplateExecutionResult(
      values =
        engine.resolve(
          resource = request.resource,
          templates = request.templates,
          context = request.context,
        )
    )
  }
}
