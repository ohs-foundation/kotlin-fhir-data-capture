import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
  // Add more targets here (jvm(), wasmJs { browser() }, iosArm64(), ...) to run the codelab on
  // other platforms. The code in commonMain is shared by all of them.
  androidLibrary {
    namespace = "dev.ohs.fhir.codelab.shared"
    compileSdk = 36
    minSdk = 26
    experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true
    compilations.configureEach {
      compilerOptions.configure { jvmTarget.set(JvmTarget.JVM_11) }
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.ui)
      implementation(compose.components.resources)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.ohs.fhir.model)

      // 3 Add the dependency for the Structured Data Capture Library
      implementation("dev.ohs.fhir:fhir-data-capture:2.0.0-alpha05")
    }
  }
}

compose.resources { packageOfResClass = "dev.ohs.fhir.codelab.shared.resources" }
