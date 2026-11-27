plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

val androidNamespace: String by project
val androidApplicationId: String by project
val applicationVersionCode: String by project
val applicationVersionName: String by project
val androidCompileSdk: String by project
val androidCompileSdkMinor: String by project
val androidMinSdk: String by project
val androidTargetSdk: String by project

android {
  namespace = androidNamespace
  compileSdk {
    version = release(androidCompileSdk.toInt()) { minorApiLevel = androidCompileSdkMinor.toInt() }
  }

  defaultConfig {
    applicationId = androidApplicationId
    minSdk = androidMinSdk.toInt()
    targetSdk = androidTargetSdk.toInt()
    versionCode = applicationVersionCode.toInt()
    versionName = applicationVersionName
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

dependencies {
  implementation(project(":catalog:shared"))
  implementation(project(":datacapture"))
  implementation(libs.androidx.activity.compose)
}
