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

/**
 * These constants mirror the Structured Data Capture (SDC) template extraction extensions used by
 * this module.
 *
 * The extraction flow combines the `templateExtract` complex extension declared on a Questionnaire
 * or Questionnaire.item with `templateExtractContext` and `templateExtractValue` directives inside
 * the contained resource template, plus optional `extractAllocateId` variables for cross-resource
 * references: https://build.fhir.org/ig/HL7/sdc/en/extraction.html
 * https://build.fhir.org/ig/HL7/sdc/en/StructureDefinition-sdc-questionnaire-templateExtract.html
 * https://build.fhir.org/ig/HL7/sdc/en/StructureDefinition-sdc-questionnaire-templateExtractContext.html
 * https://build.fhir.org/ig/HL7/sdc/en/StructureDefinition-sdc-questionnaire-templateExtractValue.html
 * https://build.fhir.org/ig/HL7/sdc/en/StructureDefinition-sdc-questionnaire-extractAllocateId.html
 */
internal const val EXTENSION_TEMPLATE_EXTRACT_URL: String =
  "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtract"

internal const val EXTENSION_TEMPLATE_EXTRACT_CONTEXT_URL: String =
  "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractContext"

internal const val EXTENSION_TEMPLATE_EXTRACT_VALUE_URL: String =
  "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-templateExtractValue"

internal const val EXTENSION_EXTRACT_ALLOCATE_ID_URL: String =
  "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-extractAllocateId"

internal const val TEMPLATE_EXTRACT_CHILD_TEMPLATE_URL: String = "template"

internal const val TEMPLATE_EXTRACT_CHILD_FULL_URL: String = "fullUrl"

internal const val TEMPLATE_EXTRACT_CHILD_RESOURCE_ID_URL: String = "resourceId"

internal const val TEMPLATE_EXTRACT_CHILD_IF_NONE_MATCH_URL: String = "ifNoneMatch"

internal const val TEMPLATE_EXTRACT_CHILD_IF_MODIFIED_SINCE_URL: String = "ifModifiedSince"

internal const val TEMPLATE_EXTRACT_CHILD_IF_MATCH_URL: String = "ifMatch"

internal const val TEMPLATE_EXTRACT_CHILD_IF_NONE_EXIST_URL: String = "ifNoneExist"
