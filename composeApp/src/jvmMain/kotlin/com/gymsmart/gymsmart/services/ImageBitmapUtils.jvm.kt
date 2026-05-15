package com.gymsmart.gymsmart.services

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun byteArrayToImageBitmap(bytes: ByteArray): ImageBitmap {
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
