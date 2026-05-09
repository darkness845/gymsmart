package com.gymsmart.gymsmart

import androidx.compose.runtime.Composable

@Composable
expect fun BarcodeScannerSheet(
    onBarcodeDetected: (String) -> Unit,
    onClose: () -> Unit
)