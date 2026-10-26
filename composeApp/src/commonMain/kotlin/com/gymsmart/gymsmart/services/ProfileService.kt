package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.ProfileRequest
import com.gymsmart.gymsmart.model.ProfileResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

class ProfileService(private val client: HttpClient) {

    private val base = AppConfig.BASE_URL

    // ¿El usuario ya completó su perfil? null = no se pudo saber (red caída, respuesta inesperada):
    // NUNCA se debe interpretar como "no tiene perfil", porque eso manda a rehacer el onboarding y
    // el guardado final sobrescribe un perfil real que sí existía (ver RED-1 en
    // doc/08-backlog-mejoras.md).
    suspend fun hasProfile(): Boolean? {
        return try {
            val response = client.get("$base/profile/has")
            if (!response.status.isSuccess()) return null
            Json.parseToJsonElement(response.bodyAsText()).jsonObject["hasProfile"]
                ?.jsonPrimitive?.boolean
        } catch (e: Exception) {
            null
        }
    }

    // Obtiene perfil + targets calculados
    suspend fun getProfileResponse(): Result<ProfileResponse> = runCatching {
        client.get("$base/profile/me").body<ProfileResponse>()
    }

    // Crea o actualiza el perfil
    suspend fun saveProfile(req: ProfileRequest): Result<ProfileResponse> = runCatching {
        client.post("$base/profile/save") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body<ProfileResponse>()
    }
}