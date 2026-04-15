package com.gymsmart.gymsmart.services

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.prefs.Preferences

actual fun createCookiesStorage(): CookiesStorage = JvmCookiesStorage()

class JvmCookiesStorage : CookiesStorage {

    private val prefs = Preferences.userRoot().node("gymsmart_cookies")
    private val mutex = Mutex()

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        val raw = prefs.get(requestUrl.host, null) ?: return@withLock emptyList()
        return@withLock raw.split(";").mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank())
                Cookie(name = parts[0].trim(), value = parts[1].trim())
            else null
        }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie): Unit = mutex.withLock {
        val existing = prefs.get(requestUrl.host, "")
            .split(";")
            .filter { it.isNotBlank() && !it.trim().startsWith("${cookie.name}=") }
            .toMutableList()
        existing.add("${cookie.name}=${cookie.value}")
        prefs.put(requestUrl.host, existing.joinToString(";"))
    }

    override fun close() {}
}