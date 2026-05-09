package com.gymsmart.gymsmart

import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.concurrent.futures.await
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
actual fun BarcodeScannerSheet(
    onBarcodeDetected: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var hasScanned by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // Handle
        Box(Modifier.fillMaxWidth().padding(bottom = 16.dp), Alignment.Center) {
            Box(Modifier.size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .padding(0.dp) // fondo lo pone el padre
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Escanear producto", style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar")
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Apunta la cámara al código de barras del producto",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )

        Spacer(Modifier.height(12.dp))

        // Vista de cámara
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)

                    scope.launch {
                        val cameraProvider = ProcessCameraProvider.getInstance(ctx).await()  // ← sin addListener

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        val scanner = BarcodeScanning.getClient()

                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null && !hasScanned) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    imageProxy.imageInfo.rotationDegrees
                                )
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val barcode = barcodes.firstOrNull {
                                            it.format == Barcode.FORMAT_EAN_13 ||
                                                    it.format == Barcode.FORMAT_EAN_8  ||
                                                    it.format == Barcode.FORMAT_UPC_A
                                        }
                                        barcode?.rawValue?.let { code ->
                                            if (!hasScanned) {
                                                hasScanned = true
                                                onBarcodeDetected(code)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            println(">>> CameraX error: ${e.message}")
                        }
                    }

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}