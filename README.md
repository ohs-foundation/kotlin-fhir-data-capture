# Kotlin FHIR Data Capture

A Kotlin Multiplatform library for collecting, validating, and processing structured healthcare data using [HL7 FHIR Questionnaires](https://www.hl7.org/fhir/questionnaire.html).

This is the KMP port of the [OHS Foundation android-fhir](https://github.com/ohs-foundation/android-fhir) datacapture library, previously documented at [ohs-foundation.github.io/android-fhir](https://ohs-foundation.github.io/android-fhir/). The original library was Android-only; this version targets multiple platforms using [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/).

## Supported platforms

- Android
- iOS
- JVM Desktop
- WebAssembly (wasmJs)

## Usage

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

## Dependency

```kotlin
// build.gradle.kts
implementation("dev.ohs.fhir:data-capture:1.3.1")
```

## Catalog app

The `catalog` module is a multiplatform demo application. To run the iOS variant see [catalog-iosApp/README.md](catalog-iosApp/README.md).
