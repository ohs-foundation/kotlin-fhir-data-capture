package dev.ohs.fhir.datacapture.views.barcode

import androidx.compose.runtime.Composable

@Composable
expect fun ScannerDialog(
    onDismiss: () -> Unit,
    onBarcode: (String?) -> Unit
)