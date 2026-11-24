package com.gymsmart.gymsmart.services

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// iOS no esta soportado todavia (ver 01-arquitectura.md): sin implementar.
@Composable
actual fun rememberRestNotifier(): RestNotifier =
    remember {
        object : RestNotifier {
            override fun schedule(seconds: Int) {}
            override fun cancel() {}
        }
    }
