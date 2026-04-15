package com.gymsmart.gymsmart.services

import android.content.Context
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// Necesitas pasar contexto en Android — ver Paso 5
lateinit var appContext: Context

actual fun createCookiesStorage(): CookiesStorage = AndroidCookiesStorage(appContext)

class AndroidCookiesStorage(context: Context) : CookiesStorage {

    private val prefs = context.getSharedPreferences("gymsmart_cookies", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        val raw = prefs.getString(requestUrl.host, null) ?: return@withLock emptyList()
        return@withLock raw.split(";").mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank())
                Cookie(name = parts[0].trim(), value = parts[1].trim())
            else null
        }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie): Unit = mutex.withLock {
        val existing = (prefs.getString(requestUrl.host, "") ?: "")
            .split(";")
            .filter { it.isNotBlank() && !it.trim().startsWith("${cookie.name}=") }
            .toMutableList()
        existing.add("${cookie.name}=${cookie.value}")
        prefs.edit().putString(requestUrl.host, existing.joinToString(";")).apply()
    }

    override fun close() {}
}