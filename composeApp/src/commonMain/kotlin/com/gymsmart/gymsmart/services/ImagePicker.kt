package com.gymsmart.gymsmart.services

import androidx.compose.runtime.Composable

@Composable
expect fun rememberImagePicker(
    onImageSelected: (
        base64: String?,
        previewBytes: ByteArray?
    ) -> Unit
): () -> Unit