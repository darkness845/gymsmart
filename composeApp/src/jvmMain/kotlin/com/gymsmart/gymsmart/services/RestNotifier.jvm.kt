package com.gymsmart.gymsmart.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// El escritorio no se lleva al gimnasio: basta con el aviso de dentro de la app.
@Composable
actual fun rememberRestNotifier(): RestNotifier =
    remember {
        object : RestNotifier {
            override fun schedule(seconds: Int) {}
            override fun cancel() {}
        }
    }
