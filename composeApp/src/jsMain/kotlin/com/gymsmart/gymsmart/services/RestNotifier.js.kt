package com.gymsmart.gymsmart.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// En el navegador el aviso de dentro de la app es el unico disponible.
@Composable
actual fun rememberRestNotifier(): RestNotifier =
    remember {
        object : RestNotifier {
            override fun schedule(seconds: Int) {}
            override fun cancel() {}
        }
    }
