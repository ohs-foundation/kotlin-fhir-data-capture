# Kotlin FHIR Data Capture

[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture?color=yellow&label=fhir-data-capture)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-jvm?color=yellow&label=jvm)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-jvm)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-wasm-js?color=yellow&label=wasm-js)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-wasm-js)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-js?color=yellow&label=js)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-js)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-android?color=yellow&label=android)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-android)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-iossimulatorarm64?color=yellow&label=iossimulatorarm64)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-iossimulatorarm64)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-iosarm64?color=yellow&label=iosarm64)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-iosarm64)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-iosx64?color=yellow&label=iosx64)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-iosx64)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Kotlin Multiplatform library for collecting, validating, and processing structured healthcare data using [HL7 FHIR Questionnaires](https://www.hl7.org/fhir/questionnaire.html).

This is the KMP port of the [OHS Foundation android-fhir](https://github.com/ohs-foundation/android-fhir) datacapture library, previously documented at [ohs-foundation.github.io/android-fhir](https://ohs-foundation.github.io/android-fhir/). The original library was Android-only; this version targets multiple platforms using [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).

## Supported platforms

The library's support for different
[target platforms](https://kotlinlang.org/docs/multiplatform-dsl-reference.html#targets)
is listed in the following table:

| Target platform                    | Gradle target  | Artifact suffix  | Support |
|:-----------------------------------|:---------------|:-----------------|:--------|
| Kotlin/JVM                         | `jvm`          | `-jvm`           | ✅       |
| Kotlin/Wasm                        | `wasmJs`       | `-wasm-js`       | ✅       |
| Kotlin/Wasm                        | `wasmWasi`     | `-wasm-wasi`     | ⛔       |
| Kotlin/JS                          | `js`           | `-js`            | ✅       |
| Android applications and libraries | `android`      | `-android`       | ✅       |

The library also supports the following
[Kotlin/Native targets](https://kotlinlang.org/docs/native-target-support.html):

| Gradle target      | Artifact suffix      | Tier | Support |
|:-------------------|:---------------------|:-----|:--------|
| iosSimulatorArm64  | `-iossimulatorarm64` | 1    | ✅       |
| iosArm64           | `-iosarm64`          | 1    | ✅       |
| iosX64             | `-iosx64`            | 3    | ✅       |

## Catalog app

The `catalog` module is a multiplatform demo application. To run the iOS variant see [catalog-iosApp/README.md](catalog-iosApp/README.md).

## User Guide

### Adding the library dependency to your project

To use the Kotlin FHIR Data Capture library in your project, you need to add the library dependency
to your project. To do that, first make sure to include the `mavenCentral()`[^1] repository in the
`build.gradle.kts` file in your project root.

[^1]: Early versions of this library (up to `1.0.0-beta02`) were published under the group ID
`com.google.android.fhir` and artifact ID `data-capture` on
[Google Maven](https://maven.google.com/web/index.html#com.google.android.fhir:data-capture).

```
// build.gradle.kts
repositories {
    // Other repositories such as gradlePluginPortal() and google()
    mavenCentral()
}
```

Next, follow the instructions for your specific project type.

#### Kotlin Multiplatform Projects

For Kotlin Multiplatform projects, add the dependency to the shared `commonMain` source set within
the `kotlin` block of the module's `build.gradle.kts` file (e.g., `composeApp/build.gradle.kts` or
`shared/build.gradle.kts`). This makes the library available across all platforms in your project.

```
// e.g., composeApp/build.gradle.kts or shared/build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.ohs.fhir:fhir-data-capture:2.0.0-alpha01")
        }
    }
}
```

#### Android projects

For Android projects, add the dependency to the `dependency` block in the module's
`build.gradle.kts` file (e.g., `app/build.gradle.kts`).

```
// e.g., app/build.gradle.kts
dependencies {
    implementation("dev.ohs.fhir:fhir-data-capture:2.0.0-alpha01")
}
```

### Working with Questionnaires

Render a questionnaire using the `Questionnaire` composable:

```kotlin
Questionnaire(
    questionnaireJson = myQuestionnaireJson,
    questionnaireResponseJson = existingResponseJson, // optional pre-fill
    showSubmitButton = true,
    showCancelButton = true,
    showReviewPage = false,
    isReadOnly = false,
    onSubmit = { getResponse ->
        val response = getResponse()
        // handle QuestionnaireResponse
    },
    onCancel = {
        navController.popBackStack()
    },
)
```

## Developer guide

### Publishing

To publish a new release, first update `mavenVersion` in `gradle.properties` to the new version.
Then follow one of the methods below:

#### Maven Local

To publish artifacts to your local Maven repository (`~/.m2/repository`) for local development and
testing, run:

```bash
./gradlew :datacapture:publishToMavenLocal
```

#### Maven Central

Publishing to Maven Central requires two sets of credentials:

1. Maven Central credentials: your Sonatype portal username and password tokens.
2. GPG signing: a GPG key and its passphrase, used to sign all published artifacts.

See the
[Kotlin Multiplatform Publishing Guide](https://kotlinlang.org/docs/multiplatform/multiplatform-publish-libraries-to-maven.html)
and the
[Maven Central Publishing Guide](https://central.sonatype.org/publish/publish-portal-guide/) for
more information on how to set up these credentials.

##### Publishing to Maven Central manually

For manual publishing, store the credentials in the global `~/.gradle/gradle.properties` in your
environment (not the project's `gradle.properties`) so they are never committed to the repository:

```properties
# Maven Central Credentials
mavenCentralUsername=YOUR_USERNAME_TOKEN
mavenCentralPassword=YOUR_PASSWORD_TOKEN

# GPG Signing (file-based)
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_KEY_PASSWORD
signing.secretKeyRingFile=/path/to/secring.gpg
```

Then run:

```bash
./gradlew :datacapture:publishToMavenCentral
```

##### Publishing to Maven Central using GitHub Actions

The project includes a GitHub Actions [workflow](.github/workflows/publish.yml) that publishes to
Maven Central when a new GitHub release (or pre-release) is created.

The workflow requires the following GitHub organization or repository secrets (already set up):

| Secret                   | Description                                                                           |
|:-------------------------|:--------------------------------------------------------------------------------------|
| `MAVEN_CENTRAL_USERNAME` | Same as `mavenCentralUsername`                                                        |
| `MAVEN_CENTRAL_PASSWORD` | Same as `mavenCentralPassword`                                                        |
| `GPG_KEY_CONTENTS`       | Needs to be exported using the command `gpg --armor --export-secret-keys YOUR_KEY_ID` |
| `SIGNING_PASSWORD`       | Same as `signing.password`                                                            |