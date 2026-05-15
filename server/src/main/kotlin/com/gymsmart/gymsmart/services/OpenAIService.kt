package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.BodyAnalysisResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*

class OpenAIService {

    private val client = HttpClient()

    private val apiKey =
        System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY missing")

    suspend fun analyzeBody(imageBase64: String): BodyAnalysisResponse {

        val body = buildJsonObject {

            put("model", "gpt-4o")

            putJsonArray("messages") {

                add(buildJsonObject {
                    put("role", "system")
                    put("content", "Eres un entrenador fitness. Responde exclusivamente en JSON válido.\n" +
                            "Sin markdown.\n" +
                            "Sin explicaciones.\n" +
                            "Sin texto adicional.")
                })

                add(buildJsonObject {
                    put("role", "user")

                    putJsonArray("content") {

                        add(buildJsonObject {
                            put("type", "text")
                            put(
                                "text",
                                """
                                Eres un entrenador personal experto en composición corporal.
                                
                                Analiza la imagen con detalle REAL (no dejes campos vacíos).
                                
                                Devuelve SOLO JSON válido con valores estimados realistas:
                                
                                {
                                  "bodyFat": "porcentaje aproximado",
                                  "estimatedWeight": "kg estimados",
                                  "muscleMass": "bajo | medio | alto",
                                  "physiqueType": "ectomorfo | mesomorfo | endomorfo",
                                  "summary": "descripción corta del físico"
                                }
                                
                                IMPORTANTE:
                                - Si no estás seguro, estima un valor razonable basado en el físico visible.
                                - Nunca devuelvas campos vacíos.
                                - NO digas que no puedes ver la imagen
                                - Usa estimaciones razonables
                                """
                            )
                        })

                        add(buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") {
                                put("url", "data:image/jpeg;base64,$imageBase64")
                            }
                        })
                    }
                })
            }
        }

        val response = client.post("https://api.openai.com/v1/chat/completions") {
            contentType(ContentType.Application.Json)

            header("Authorization", "Bearer $apiKey")

            setBody(body.toString())
        }

        val raw = response.bodyAsText()
        println("OPENAI RAW RESPONSE:\n$raw")

        val json = Json.parseToJsonElement(raw).jsonObject

        if (json.containsKey("error")) {
            throw IllegalStateException("OpenAI error:\n$raw")
        }

        val text = json["choices"]
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.content
            ?: throw IllegalStateException("OpenAI vacío:\n$raw")

        val cleaned = text
            .replace("```json", "")
            .replace("```", "")
            .trim()

        return try {
            Json.decodeFromString<BodyAnalysisResponse>(cleaned)
        } catch (e: Exception) {
            throw IllegalStateException("JSON inválido del modelo:\n$cleaned", e)
        }
    }
}