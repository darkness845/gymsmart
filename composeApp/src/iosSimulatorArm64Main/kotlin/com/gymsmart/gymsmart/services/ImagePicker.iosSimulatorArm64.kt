package com.gymsmart.gymsmart.services

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImagePicker(onImageSelected: (base64: String?, previewBytes: ByteArray?) -> Unit): () -> Unit {
    TODO("Not yet implemented")
}