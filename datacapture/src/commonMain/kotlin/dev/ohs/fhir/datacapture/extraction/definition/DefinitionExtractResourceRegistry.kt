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
package dev.ohs.fhir.datacapture.extraction.definition

import dev.ohs.fhir.model.r4.Resource
import kotlinx.serialization.descriptors.SerialDescriptor

internal object DefinitionExtractResourceRegistry {
  private val descriptorsByType: Map<String, SerialDescriptor> by lazy {
    val resourceDescriptor = Resource.serializer().descriptor
    val union = resourceDescriptor.getElementDescriptor(resourceDescriptor.getElementIndex("value"))
    (0 until union.elementsCount).associate { index ->
      union.getElementName(index) to union.getElementDescriptor(index)
    }
  }
  internal val supportedResourceTypes: Set<String>
    get() = descriptorsByType.keys

  internal fun descriptorFor(resourceType: String): SerialDescriptor? =
    descriptorsByType[resourceType]
}
