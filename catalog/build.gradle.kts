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

val androidNamespace: String by project
val applicationId: String by project
val applicationVersionCode: String by project
val applicationVersionName: String by project
val androidCompileSdk: String by project
val androidMinSdk: String by project
val androidTargetSdk: String by project

android {
  namespace = androidNamespace
  compileSdk = androidCompileSdk.toInt()

  defaultConfig {
    applicationId = applicationId
    minSdk = androidMinSdk.toInt()
    targetSdk = androidTargetSdk.toInt()
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
      listOf("META-INF/ASL2.0", "META-INF/ASL-2.0.txt", "META-INF/LGPL-3.0.txt")
    )
  }
}

kotlin {
  jvm()

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser {
      val rootProjectDir = rootProject.projectDir.path
      commonWebpackConfig {
        devServer =
          (devServer ?: KotlinWebpackConfig.DevServer()).copy(
            static = (devServer?.static ?: mutableListOf()).apply { add(rootProjectDir) }
          )
      }
    }
    binaries.executable()
  }

  androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

  listOf(iosSimulatorArm64(), iosArm64(), iosX64()).forEach {
    it.binaries.framework {
      baseName = "Catalog"
      isStatic = true
    }
  }

  sourceSets {
    androidMain {
      dependencies {
        implementation(libs.androidx.appcompat)
        implementation(libs.androidx.core)
        implementation(libs.compass.geolocation.mobile)
        implementation(libs.compass.permissions.mobile)
        implementation(libs.material)
        implementation(libs.moko.permissions.camera)
        implementation(libs.moko.permissions.compose)
      }
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
      implementation(libs.compass.geolocation)
      implementation(libs.kermit)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kscan)
      implementation(libs.navigation.compose)
      implementation(libs.ohs.fhir.model)
      implementation(project(":datacapture"))
    }

    @Suppress("unused")
    val jvmMain by getting { dependencies { implementation(compose.desktop.currentOs) } }

    iosMain {
      dependencies {
        implementation(libs.compass.geolocation.mobile)
        implementation(libs.compass.permissions.mobile)
        implementation(libs.moko.permissions.camera)
        implementation(libs.moko.permissions.compose)
      }
    }

    wasmJsMain { dependencies { implementation(libs.compass.geolocation.browser) } }
  }
}
