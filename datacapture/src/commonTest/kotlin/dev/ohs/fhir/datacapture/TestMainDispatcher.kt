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

/**
 * Installs a Main dispatcher that `runComposeUiTest` observes, for tests that assert on
 * `viewModelScope` work.
 *
 * Only the browser targets need this. On the other targets the test framework drives the real Main
 * dispatcher and tracks UI idleness through it, so the actual is a no-op.
 */
internal expect fun setTestMainDispatcher()

/** Restores the dispatcher replaced by [setTestMainDispatcher]. */
internal expect fun resetTestMainDispatcher()
