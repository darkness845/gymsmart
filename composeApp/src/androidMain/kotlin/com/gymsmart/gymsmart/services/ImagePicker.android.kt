package com.gymsmart.gymsmart.services

import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

@Composable
actual fun rememberImagePicker(
    onImageSelected: (
        base64: String?,
        previewBytes: ByteArray?
    ) -> Unit
): () -> Unit {

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            if (uri == null) {

                onImageSelected(null, null)

                return@rememberLauncherForActivityResult
            }

            try {

                val bytes =
                    appContext.contentResolver
                        .openInputStream(uri)
                        ?.readBytes()

                if (bytes == null) {

                    onImageSelected(null, null)

                    return@rememberLauncherForActivityResult
                }

                val base64 =
                    Base64.encodeToString(
                        bytes,
                        Base64.NO_WRAP
                    )

                onImageSelected(base64, bytes)

            } catch (e: Exception) {

                e.printStackTrace()

                onImageSelected(null, null)
            }
        }

    return {
        launcher.launch("image/*")
    }
}