# Kotlin FHIR Data Capture

[![tests](https://github.com/ohs-foundation/kotlin-fhir-data-capture/actions/workflows/run-tests.yml/badge.svg)](https://github.com/ohs-foundation/kotlin-fhir-data-capture/actions/workflows/run-tests.yml)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture?color=yellow&label=fhir-data-capture)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-jvm?color=yellow&label=jvm)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-jvm)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-wasm-js?color=yellow&label=wasm-js)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-wasm-js)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-js?color=yellow&label=js)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-js)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-android?color=yellow&label=android)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-android)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-iossimulatorarm64?color=yellow&label=iossimulatorarm64)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-iossimulatorarm64)
[![Release](https://img.shields.io/maven-central/v/dev.ohs.fhir/fhir-data-capture-iosarm64?color=yellow&label=iosarm64)](https://central.sonatype.com/artifact/dev.ohs.fhir/fhir-data-capture-iosarm64)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Kotlin Multiplatform library for collecting, validating, and processing structured healthcare data using [HL7 FHIR Questionnaires](https://www.hl7.org/fhir/questionnaire.html).

## Key features

* Renders FHIR R4 [Questionnaires](https://www.hl7.org/fhir/questionnaire.html) as
  [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) forms across Android,
  iOS, Desktop (JVM), and Web (JS/Wasm)
* Skip logic via `enableWhen` and SDC expression extensions (`enableWhenExpression`,
  `calculatedExpression`, `variable`, `answerExpression`)
* Answer validation against questionnaire constraints, with per-field error messages and a
  standalone validation API
* Pagination, review page, read-only mode, repeating groups, and entry-mode control
* Template-based [data extraction](docs/conformance.md#data-extraction) of FHIR resources from
  questionnaire responses
* FHIRPath evaluation powered by
  [Kotlin FHIRPath](https://github.com/ohs-foundation/kotlin-fhirpath)
* Predictable and [well-documented](#conformance) behavior, including explicit documentation of
  what is *not* supported

## Conformance

For the full conformance analysis, see the [conformance](docs/conformance.md) doc.

### FHIR Questionnaire specification

The library renders and processes
[Questionnaire](https://hl7.org/fhir/R4/questionnaire.html) and
[QuestionnaireResponse](https://hl7.org/fhir/R4/questionnaireresponse.html) resources from
[FHIR R4 (v4.0.1)](https://hl7.org/fhir/R4/).

See [FHIR Questionnaire specification conformance](docs/conformance.md#fhir-questionnaire-specification)
for the implementation status of every item type, item control, form behavior element, and
standard extension.

### Structured Data Capture specification

The library implements a subset of the
[Structured Data Capture implementation guide STU4 (v4.0.0)](https://hl7.org/fhir/uv/sdc/STU4/).
Advanced rendering, form behavior and calculation, and template-based extraction are implemented.
The SDC population module and the other extraction mechanisms are not.

See [SDC conformance](docs/conformance.md#structured-data-capture-specification) for
feature-by-feature status, supported expression languages, and FHIRPath environment variables.

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

## Catalog app

The `catalog` module is a multiplatform demo application. To run the iOS variant see
[catalog-iosApp/README.md](catalog-iosApp/README.md).

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
            implementation("dev.ohs.fhir:fhir-data-capture:2.0.0-alpha02")
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
    implementation("dev.ohs.fhir:fhir-data-capture:2.0.0-alpha03")
}
```

### Working with Questionnaires

Render a questionnaire using the `Questionnaire` composable.

```kotlin
val coroutineScope = rememberCoroutineScope()

Questionnaire(
    questionnaireJson = myQuestionnaireJson,
    questionnaireResponseJson = existingResponseJson, // optional pre-fill
    config = QuestionnaireConfig(
        showSubmitButton = true,
        showCancelButton = true,
        showReviewPage = false,
        isReadOnly = false,
    ),
    onSubmit = { getResponse ->
        coroutineScope.launch {
            // Validates the response first. On failure an error dialog
            // is shown and the coroutine is cancelled.
            val response = getResponse()
            // handle QuestionnaireResponse
        }
    },
    onCancel = {
        navController.popBackStack()
    },
)
```

See [`QuestionnaireConfig`](datacapture/src/commonMain/kotlin/dev/ohs/fhir/datacapture/QuestionnaireComposable.kt)
for all display options (review page, read-only mode, required and optional labels, long-scroll
navigation, custom submit button text, and the "submit anyway" escape hatch).

To make [launch context](docs/conformance.md#form-behavior-and-calculation) resources such as
`%patient` available to the questionnaire's FHIRPath expressions, pass them as JSON via
`questionnaireLaunchContextMap`, keyed by the launch context name declared in the questionnaire.

```kotlin
Questionnaire(
    questionnaireJson = myQuestionnaireJson,
    questionnaireLaunchContextMap = mapOf("patient" to patientJson),
    ...
)
```

### Configuring the library

Optional integration hooks are supplied through
[`DataCaptureConfig`](datacapture/src/commonMain/kotlin/dev/ohs/fhir/datacapture/DataCaptureConfig.kt)
via a CompositionLocal.

```kotlin
CompositionLocalProvider(
    LocalDataCaptureConfig provides
        DataCaptureConfig(
            // Resolve external (non-contained) answerValueSet URIs to answer options.
            valueSetResolverExternal = myValueSetResolver,
            // Resolve application/x-fhir-query expressions (answerExpression, variable).
            xFhirQueryResolver = myXFhirQueryResolver,
            // Fetch media content referenced by URL (itemMedia).
            urlResolver = myUrlResolver,
        ),
) {
    Questionnaire(...)
}
```

Without these hooks, external value sets resolve to no options and x-fhir-query expressions fail.
See the [conformance](docs/conformance.md) doc for which features depend on which resolver.

### Validating a QuestionnaireResponse

The `Questionnaire` composable validates answers as the user fills the form and on submit. To
validate a response outside the UI, use
[`QuestionnaireResponseValidator`](datacapture/src/commonMain/kotlin/dev/ohs/fhir/datacapture/validation/QuestionnaireResponseValidator.kt).

```kotlin
val results: Map<String, List<ValidationResult>> = // keyed by linkId
    QuestionnaireResponseValidator.validateQuestionnaireResponse(
        questionnaire = questionnaire,
        questionnaireResponse = questionnaireResponse,
    )
```

See [validation conformance](docs/conformance.md#validation-extensions-and-elements) for the
supported constraints and their caveats.

### Extracting FHIR resources

If the questionnaire is authored for
[SDC template-based extraction](docs/conformance.md#data-extraction), extract a transaction
`Bundle` of FHIR resources from the completed response with
[`TemplateExtractionEngine`](datacapture/src/commonMain/kotlin/dev/ohs/fhir/datacapture/extraction/template/TemplateExtractionEngine.kt).

```kotlin
if (TemplateExtractionEngine.canExtract(questionnaire)) {
    val bundle = TemplateExtractionEngine.extract(questionnaire, questionnaireResponse)
    // post the transaction bundle to your FHIR server
}
```

Extraction is not invoked automatically by the `Questionnaire` composable. Call it with the
response returned from `onSubmit`. Definition, StructureMap, and observation based extraction are
not supported (see [extraction conformance](docs/conformance.md#data-extraction)).

## Developer guide

### Testing

Tests are located in the following source sets:

- `commonTest`: Shared tests (logical validation rules and Compose UI rendering/flows) that run
  across all targets.
- `jvmTest`: JVM-specific tests verifying localized date, time, and datetime input
  parsing/formatting using JVM Locales (`java.util.Locale`).
- `androidDeviceTest`: Android-specific instrumentation tests verifying interactions with native
  Android date, time, and datetime picker dialogs (requires a connected device or emulator).

#### CI Platform Coverage

The [CI pipeline](.github/workflows/run-tests.yml) automatically runs checks on every push and pull
request. The table below details which test source sets (listed above) are executed by each target's
CI task:

| Platform              | Gradle task                          | CI runner       | Test source sets            | Notes |
|:----------------------|:-------------------------------------|:----------------|:----------------------------|:------|
| **JVM**               | `:datacapture:jvmTest`               | `ubuntu-latest` | `commonTest`, `jvmTest`     | Requires `xvfb-run` on Linux runners to host virtual framebuffer for Compose tests |
| **Wasm JS (Browser)** | `:datacapture:wasmJsBrowserTest`     | `ubuntu-latest` | `commonTest`                | Runs in headless Chrome |
| **JS (Browser)**      | `:datacapture:jsBrowserTest`         | `ubuntu-latest` | `commonTest`                | Runs in headless Chrome |
| **Android**           | `:datacapture:testAndroidHostTest`   | `ubuntu-latest` | `commonTest`                | Runs host unit tests on JVM |
| **iOS (Simulator)**   | `:datacapture:iosSimulatorArm64Test` | `macos-latest`  | `commonTest`                | Runs in simulator environment |
| **iOS Release Framework** | `:datacapture:linkReleaseFrameworkIosArm64` | `macos-latest` | N/A | Build-only regression check (no test source sets) guarding against the Kotlin/Native LTO OOM in [#35](https://github.com/ohs-foundation/kotlin-fhir-data-capture/issues/35) |

#### Running Tests Locally

To run all CI-validated test suites locally:

```bash
./gradlew check
```

To run a specific test suite locally, run the corresponding Gradle task:

- **JVM**: `./gradlew :datacapture:jvmTest`
- **Wasm**: `./gradlew :datacapture:wasmJsBrowserTest`
- **JS**: `./gradlew :datacapture:jsBrowserTest`
- **Android Host**: `./datacapture:testAndroidHostTest`
- **iOS Simulator**: `./gradlew :datacapture:iosSimulatorArm64Test`
- **iOS Release Framework**: `./gradlew :datacapture:linkReleaseFrameworkIosArm64`

##### On-Device Android Tests

The platform-specific Android UI tests (located under `androidDeviceTest`) are **not** run
automatically on CI. To run them locally:

1. Connect a physical Android device or start an emulator.
2. Execute the connected test task:
   ```bash
   ./gradlew :datacapture:connectedAndroidDeviceTest
   ```

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