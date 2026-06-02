/*
 * Copyright 2021-2026 Open Health Stack Foundation
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

/** Stores config and global state of the Structured Data Capture Library. */
expect object DataCapture {

  /**
   * Initialises the library with the provided [config]. Must be called before
   * [getConfiguration] is used. If called more than once, subsequent calls are ignored.
   */
  fun initialize(config: DataCaptureConfig)

  /**
   * Returns the [DataCaptureConfig] supplied via [initialize], or a default [DataCaptureConfig]
   * if [initialize] has not been called.
   */
  fun getConfiguration(): DataCaptureConfig
}
