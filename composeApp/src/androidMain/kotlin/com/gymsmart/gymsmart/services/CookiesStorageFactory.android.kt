package com.gymsmart.gymsmart.services

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.ktor.client.plugins.cookies.*
import io.ktor.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

lateinit var appContext: Context

actual fun createCookiesStorage(): CookiesStorage = AndroidCookiesStorage(appContext)

/**
 * La cookie de sesión equivale a una contraseña temporal: si el dispositivo se compromete (malware,
 * acceso físico, root), no debe quedar legible en SharedPreferences en claro. Se usa
 * EncryptedSharedPreferences (clave maestra en el Android Keystore) en vez de SharedPreferences normal.
 */
class AndroidCookiesStorage(context: Context) : CookiesStorage {

    private val prefs = createEncryptedPrefs(context)
    private val mutex = Mutex()

    private fun createEncryptedPrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encrypted = EncryptedSharedPreferences.create(
            context,
            "gymsmart_cookies_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        // Borra el residuo en claro del almacenamiento antiguo (versiones previas de la app guardaban
        // la cookie sin cifrar aquí); el usuario simplemente tendrá que volver a iniciar sesión una vez.
        context.getSharedPreferences("gymsmart_cookies", Context.MODE_PRIVATE).edit().clear().apply()
        encrypted
    } catch (e: Exception) {
        // Si el Keystore del dispositivo falla (muy raro), no bloqueamos el login: se cae a
        // SharedPreferences normal en vez de dejar la app inusable.
        Log.w("AndroidCookiesStorage", "No se pudo crear almacenamiento cifrado, usando fallback sin cifrar", e)
        context.getSharedPreferences("gymsmart_cookies", Context.MODE_PRIVATE)
    }

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