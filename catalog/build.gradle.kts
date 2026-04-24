import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.androidx.navigation.safeargs)
}

val groupId: String by project
val androidNamespace: String by project
val applicationId: String by project
val applicationVersionCode: String by project
val applicationVersionName: String by project

group = groupId

android {
  namespace = androidNamespace
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = applicationId
    minSdk = libs.versions.minSdk.get().toInt()
    versionCode = applicationVersionCode.toInt()
    versionName = applicationVersionName
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  packaging {
    resources.excludes.addAll(
      listOf("META-INF/ASL2.0", "META-INF/ASL-2.0.txt", "META-INF/LGPL-3.0.txt"),
    )
  }
}

kotlin {
  androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

  jvm("desktop")

  val isWasmEnabled = project.findProperty("catalog.wasm.enabled") == "true"
  if (isWasmEnabled) {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
      browser {
        val rootProjectDir = rootProject.projectDir.path
        commonWebpackConfig {
          devServer =
            (devServer ?: KotlinWebpackConfig.DevServer()).copy(
              static = (devServer?.static ?: mutableListOf()).apply { add(rootProjectDir) },
            )
        }
      }
      binaries.executable()
    }
  }

  listOf(
      iosX64(),
      iosArm64(),
      iosSimulatorArm64(),
    )
    .forEach { iosTarget ->
      iosTarget.binaries.framework {
        baseName = "CatalogKit"
        isStatic = true
      }
    }

  sourceSets {
    androidMain.dependencies {
      implementation(libs.androidx.appcompat)
      implementation(libs.androidx.core)
      implementation(libs.material)
      // TODO restore after these libraries are migrated to Kotlin Multiplatform
      //      implementation(project(":engine"))
    }
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.ui)
      implementation(compose.components.resources)
      implementation(compose.components.uiToolingPreview)
      implementation(libs.androidx.lifecycle.viewmodel.compose)
      implementation(libs.androidx.lifecycle.runtime.compose)
      implementation(libs.kermit)
      implementation(libs.ohs.fhir.model)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.navigation.compose)
      implementation(project(":contrib:barcode"))
      implementation(project(":contrib:locationwidget"))
      implementation(project(":datacapture"))
    }

    val desktopMain by getting { dependencies { implementation(compose.desktop.currentOs) } }
  }
}
