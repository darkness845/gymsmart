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

    // ¿El usuario ya completó su perfil?
    suspend fun hasProfile(): Boolean {
        return try {
            val text = client.get("$base/profile/has").bodyAsText()
            Json.parseToJsonElement(text).jsonObject["hasProfile"]
                ?.jsonPrimitive?.boolean ?: false
        } catch (e: Exception) {
            false
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