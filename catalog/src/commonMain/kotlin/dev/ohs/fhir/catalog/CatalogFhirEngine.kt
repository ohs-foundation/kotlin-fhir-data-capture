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
package dev.ohs.fhir.catalog

import co.touchlab.kermit.Logger
import dev.ohs.fhir.datacapture.XFhirQueryResolver
import dev.ohs.fhir.engine.FhirEngineConfiguration
import dev.ohs.fhir.engine.FhirEngineProvider
import dev.ohs.fhir.engine.search.search

fun catalogXFhirQueryResolver(context: Any = Unit): XFhirQueryResolver {
  val engine =
    runCatching {
        if (FhirEngineProvider.isNotInitialized()) {
          FhirEngineProvider.init(FhirEngineConfiguration(), context)
        }
        FhirEngineProvider.getInstance(context)
      }
      .onFailure { throwable ->
        Logger.e(
          "Failed to initialize FhirEngine: ${throwable.message ?: "Unknown error"}",
          throwable,
        )
      }
      .getOrNull()
  return XFhirQueryResolver { query -> engine?.search(query)?.map { it.resource } ?: emptyList() }
}
