@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  alias(libs.plugins.cashapp.licensee)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.hotreload)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.ksp)
  alias(libs.plugins.maven.publish)
}

val androidCompileSdk: String by project
val androidMinSdk: String by project

val androidNamespace: String by project
val mavenGroupId: String by project
val mavenArtifactId: String by project
val mavenVersion: String by project

kotlin {
  jvmToolchain(21)

  jvm()

  wasmJs {
    browser()
    binaries.library()
  }

  js {
    browser()
    binaries.library()
  }

  androidLibrary {
    namespace = androidNamespace
    compileSdk = androidCompileSdk.toInt()
    minSdk = androidMinSdk.toInt()
    withJava()
    withHostTestBuilder {}
    withDeviceTestBuilder { sourceSetTreeName = "test" }
      .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }

    experimentalProperties["android.experimental.kmp.enableAndroidResources"] = true

    compilations.configureEach {
      compilerOptions.configure { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) }
    }

    packaging {
      resources.excludes.addAll(
        listOf("META-INF/ASL2.0", "META-INF/ASL-2.0.txt", "META-INF/LGPL-3.0.txt")
      )
    }
  }

  listOf(iosSimulatorArm64(), iosArm64(), iosX64()).forEach {
    it.binaries.framework { baseName = "KotlinFhirDataCapture" }
  }

  sourceSets {
    all {
      languageSettings {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
      }
    }

    commonMain {
      dependencies {
        implementation(compose.runtime)
        implementation(compose.foundation)
        implementation(compose.material3)
        implementation(compose.ui)
        implementation(compose.components.resources)
        implementation(compose.components.uiToolingPreview)
        implementation(libs.androidx.lifecycle.viewmodel.compose)
        implementation(libs.androidx.lifecycle.runtime.compose)
        implementation(libs.filekit.dialogs.compose)
        implementation(libs.kermit)
        implementation(libs.kotlinx.coroutines.core)
        implementation(libs.kotlinx.io.core)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.navigation.compose)
        implementation(libs.ohs.fhir.model)
        implementation(libs.ohs.fhir.path)
      }
    }

    commonTest {
      dependencies {
        implementation(libs.androidx.lifecycle.runtime.testing)
        implementation(libs.kotlin.test)
        implementation(libs.kotest.assertions.core)

        @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
        implementation(compose.uiTest)
      }
    }

    androidMain { resources.srcDir("res") }

    getByName("androidDeviceTest") {
      dependencies {
        implementation(libs.androidx.compose.ui.test.junit4)
        implementation(libs.androidx.compose.ui.test.manifest)
        implementation(libs.androidx.test.core)
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.androidx.test.ext.junit.ktx)
        implementation(libs.androidx.test.runner)
        implementation(libs.androidx.test.rules)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.truth)
      }
    }

    getByName("androidHostTest") {
      dependencies {
        implementation(libs.androidx.fragment.testing)
        implementation(libs.androidx.test.core)
        implementation(libs.junit)
        implementation(libs.kotlin.test.junit)
        implementation(libs.kotlinx.coroutines.test)
        implementation(libs.truth)
      }
    }

    @Suppress("unused")
    val jvmMain by getting {
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlinx.coroutines.swing)
      }
    }
  }
}

licensee {
  allow("Apache-2.0")

  // slf4j (pulled in by dbus-java)
  allow("MIT")

  listOf(
      // FileKit
      "https://github.com/vinceglb/FileKit/blob/main/LICENSE",

      // dbus-java (pulled in by FileKit)
      "https://github.com/hypfvieh/dbus-java/blob/master/LICENSE",
    )
    .forEach { allowUrl(it) }
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()
  coordinates(mavenGroupId, mavenArtifactId, mavenVersion)

  pom {
    name = "Kotlin FHIR Data Capture"
    description = "A Kotlin Multiplatform library for FHIR Structured Data Capture (SDC)"
    inceptionYear = "2026"
    url = "https://github.com/ohs-foundation/kotlin-fhir-data-capture"
    licenses {
      license {
        name = "The Apache License, Version 2.0"
        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        distribution = "https://www.apache.org/licenses/LICENSE-2.0.txt"
      }
    }
    developers {
      developer {
        id = "ohs-foundation"
        name = "Open Health Stack Foundation"
        url = "https://ohs.dev/"
      }
    }
    scm {
      url = "https://github.com/ohs-foundation/kotlin-fhir-data-capture/"
      connection = "scm:git:git://github.com/ohs-foundation/kotlin-fhir-data-capture.git"
      developerConnection =
        "scm:git:ssh://git@github.com/ohs-foundation/kotlin-fhir-data-capture.git"
    }
  }
}
