package com.gymsmart.gymsmart.services

import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.Foundation.NSUserDefaults

actual fun createCookiesStorage(): CookiesStorage = IosCookiesStorage()

class IosCookiesStorage : CookiesStorage {

    private val defaults = NSUserDefaults.standardUserDefaults
    private val mutex = Mutex()

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        val raw = defaults.stringForKey(requestUrl.host) ?: return@withLock emptyList()
        return@withLock raw.split(";").mapNotNull { entry ->
            val parts = entry.split("=", limit = 2)
            if (parts.size == 2 && parts[0].isNotBlank())
                Cookie(name = parts[0].trim(), value = parts[1].trim())
            else null
        }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie): Unit = mutex.withLock {
        val existing = (defaults.stringForKey(requestUrl.host) ?: "")
            .split(";")
            .filter { it.isNotBlank() && !it.trim().startsWith("${cookie.name}=") }
            .toMutableList()
        existing.add("${cookie.name}=${cookie.value}")
        defaults.setObject(existing.joinToString(";"), requestUrl.host)
    }

    override fun close() {}
}