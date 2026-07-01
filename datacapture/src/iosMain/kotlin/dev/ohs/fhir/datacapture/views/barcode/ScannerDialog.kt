package dev.ohs.fhir.datacapture.views.barcode

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

@Composable
actual fun ScannerDialog(
    onDismiss: () -> Unit,
    onBarcode: (String?) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Barcode scanner not supported on iOS yet")
    }
}