plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  kotlin("android")
}

android {
  namespace = "dev.ohs.fhir.codelab"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.ohs.fhir.codelabs.datacapture"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  packaging {
    resources.excludes.addAll(
      listOf("META-INF/ASL2.0", "META-INF/ASL-2.0.txt", "META-INF/LGPL-3.0.txt")
    )
  }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }

dependencies {
  implementation(project(":shared"))
  implementation("androidx.activity:activity-compose:1.10.1")
}
