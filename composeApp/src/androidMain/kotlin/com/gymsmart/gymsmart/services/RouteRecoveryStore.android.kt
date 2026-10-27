package com.gymsmart.gymsmart.services

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual class RouteRecoveryStore {

    private fun prefs() =
        appContext.getSharedPreferences("gymsmart_route_recovery", Context.MODE_PRIVATE)

    actual fun save(route: RecoverableRoute) {
        prefs().edit().putString(KEY, Json.encodeToString(route)).apply()
    }

    actual fun load(): RecoverableRoute? {
        val raw = prefs().getString(KEY, null) ?: return null
        return try {
            Json { ignoreUnknownKeys = true }.decodeFromString<RecoverableRoute>(raw)
        } catch (e: Exception) {
            null
        }
    }

    actual fun clear() {
        prefs().edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "route"
    }
}
