package dev.ohs.fhir.datacapture.views.barcode

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Surface
import org.ncgroup.kscan.BarcodeFormat
import org.ncgroup.kscan.BarcodeResult
import org.ncgroup.kscan.ScannerView

@Composable
actual fun ScannerDialog(
    onDismiss: () -> Unit,
    onBarcode: (String?) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ScannerView(
                codeTypes = listOf(BarcodeFormat.FORMAT_ALL_FORMATS)
            ) { result ->
                when (result) {
                    is BarcodeResult.OnSuccess -> {
                        onBarcode(result.barcode.data)
                        onDismiss()
                    }
                    is BarcodeResult.OnFailed -> {
                        result.exception.printStackTrace()
                        onBarcode(null)
                        onDismiss()
                    }
                    is BarcodeResult.OnCanceled -> {
                        onBarcode(null)
                        onDismiss()
                    }
                }
            }
        }
    }
}