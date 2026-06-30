package dev.ohs.fhir.datacapture

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A [androidx.compose.runtime.CompositionLocal] that provides [DataCaptureConfig] to the Compose
 * UI tree. Wrap your root composable with
 * [androidx.compose.runtime.CompositionLocalProvider] to supply a custom config:
 *
 * ```kotlin
 * CompositionLocalProvider(LocalDataCaptureConfig provides DataCaptureConfig(...)) {
 *   App()
 * }
 * ```
 *
 * Falls back to a default [DataCaptureConfig] (all resolvers null) if no provider is set.
 */
val LocalDataCaptureConfig = staticCompositionLocalOf { DataCaptureConfig() }