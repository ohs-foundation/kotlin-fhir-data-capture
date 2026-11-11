plugins {
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose.multiplatform)
  kotlin("jvm")
}

dependencies {
  implementation(project(":shared"))
  implementation(compose.desktop.currentOs)
}

compose.desktop { application { mainClass = "dev.ohs.fhir.codelab.MainKt" } }
