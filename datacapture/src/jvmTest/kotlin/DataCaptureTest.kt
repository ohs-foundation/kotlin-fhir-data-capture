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
import dev.ohs.fhir.datacapture.DataCapture
import dev.ohs.fhir.datacapture.DataCaptureConfig
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlin.test.BeforeTest
import kotlin.test.Test

class DataCaptureTest {

  @BeforeTest
  fun resetConfiguration() {
    // TODO: Replace with internal reset function when tests move to commonTest
    val field = DataCapture::class.java.getDeclaredField("configuration")
    field.isAccessible = true
    field.set(DataCapture, null)
  }

  // TC-1: getConfiguration before initialize returns a default DataCaptureConfig
  @Test
  fun getConfigurationBeforeInitializeReturnsDefault() {
    val config = DataCapture.getConfiguration()

    config shouldBe DataCaptureConfig()
  }

  // TC-2: initialize stores the provided config
  @Test
  fun initializeStoresProvidedConfig() {
    val customConfig = DataCaptureConfig(xFhirQueryResolver = { emptyList() })

    DataCapture.initialize(customConfig)

    DataCapture.getConfiguration() shouldBeSameInstanceAs customConfig
  }

  // TC-3: second call to initialize is ignored — first-call-wins
  @Test
  fun initializeIsFirstCallWins() {
    val firstConfig = DataCaptureConfig()
    val secondConfig = DataCaptureConfig(xFhirQueryResolver = { emptyList() })

    DataCapture.initialize(firstConfig)
    DataCapture.initialize(secondConfig)

    DataCapture.getConfiguration() shouldBeSameInstanceAs firstConfig
  }

  // TC-4: getConfiguration is idempotent after initialize — same instance on every call
  @Test
  fun getConfigurationIsIdempotent() {
    val config = DataCaptureConfig()
    DataCapture.initialize(config)

    val first = DataCapture.getConfiguration()
    val second = DataCapture.getConfiguration()
    val third = DataCapture.getConfiguration()

    first shouldBeSameInstanceAs config
    first shouldBeSameInstanceAs second
    second shouldBeSameInstanceAs third
  }
}
