package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.config.AppConfig
import com.gymsmart.gymsmart.model.AdaptiveResponse
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

    /**
     * Obtiene perfil + objetivos calculados.
     *
     * En modo pulsera hay que pasarle [energy]: el servidor no tiene acceso a Health Connect, así que
     * es el cliente quien aporta la media de días completos y el arrastre de ayer. Sin eso el objetivo
     * cae al BMR puro, que es conservador pero no inventa actividad que no se ha podido medir.
     */
    suspend fun getProfileResponse(
        avgActiveKcal: Double? = null,
        carryOverKcal: Double = 0.0
    ): Result<ProfileResponse> = runCatching {
        client.get("$base/profile/me") {
            if (avgActiveKcal != null) {
                parameter("avgActiveKcal", avgActiveKcal)
                parameter("carryOverKcal", carryOverKcal)
            }
        }.body<ProfileResponse>()
    }

    /**
     * Lanza el autoajuste: el servidor mide el gasto real a partir de la tendencia de peso y de lo
     * comido, y corrige el objetivo si hay datos suficientes (doc/10-fundamento-cientifico.md §5).
     */
    suspend fun runAdaptiveAdjustment(avgActiveKcal: Double? = null): Result<AdaptiveResponse> = runCatching {
        client.get("$base/profile/adaptive") {
            // El servidor necesita la media de activas para anclar el ajuste al gasto con el que el
            // usuario ha estado comiendo de verdad. Sin ella caía al respaldo `BMR × 1,2` y el ajuste
            // salía en dirección contraria a la medición (AUD-4 en doc/11-auditoria-kcal.md).
            if (avgActiveKcal != null) parameter("avgActiveKcal", avgActiveKcal)
        }.body<AdaptiveResponse>()
    }

    /**
     * Crea o actualiza el perfil.
     *
     * [avgActiveKcal] importa aunque parezca accesorio: es lo que hace que el resumen de objetivos que
     * se enseña justo después de guardar coincida con el que luego se ve en Nutrición. Sin él, a los
     * usuarios de pulsera se les calculaba con 0 de actividad.
     */
    suspend fun saveProfile(
        req: ProfileRequest,
        avgActiveKcal: Double? = null
    ): Result<ProfileResponse> = runCatching {
        client.post("$base/profile/save") {
            if (avgActiveKcal != null) parameter("avgActiveKcal", avgActiveKcal)
            contentType(ContentType.Application.Json)
            setBody(req)
        }.body<ProfileResponse>()
    }
}