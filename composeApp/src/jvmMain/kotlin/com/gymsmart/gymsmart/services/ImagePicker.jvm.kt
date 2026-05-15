package com.gymsmart.gymsmart.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Base64

@Composable
actual fun rememberImagePicker(onImageSelected: (base64: String?, previewBytes: ByteArray?) -> Unit): () -> Unit {
    return remember {
        {
            val dialog = FileDialog(null as Frame?, "Seleccionar imagen", FileDialog.LOAD)
            dialog.file = "*.jpg;*.jpeg;*.png"
            dialog.isVisible = true
            val file = if (dialog.file != null && dialog.directory != null)
                File(dialog.directory, dialog.file)
            else null

            if (file != null && file.exists()) {
                val bytes = file.readBytes()
                val base64 = Base64.getEncoder().encodeToString(bytes)
                onImageSelected(base64, bytes)
            } else {
                onImageSelected(null, null)
            }
        }
    }
}