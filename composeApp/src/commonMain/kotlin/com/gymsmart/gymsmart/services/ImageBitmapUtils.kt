package com.gymsmart.gymsmart.services

import androidx.compose.ui.graphics.ImageBitmap

expect fun byteArrayToImageBitmap(
    bytes: ByteArray
): ImageBitmap