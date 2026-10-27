package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.BodyAnalysisResponse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.awt.Image
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

class OpenAIService {

    private val logger = LoggerFactory.getLogger(OpenAIService::class.java)
    private val client = HttpClient()

    private val apiKey =
        System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY missing")

    companion object {
        // gpt-4o reescala internamente cualquier imagen a <=768px de lado corto antes de tokenizarla
        // (ver https://platform.openai.com/docs/guides/vision) — mandarla más grande no aporta nada,
        // solo multiplica el payload (base64 es ~33% más grande que los bytes originales) y el heap
        // que ocupa esa petición en una instancia de Render de 512 MB (IA-1 en doc/08-backlog-mejoras.md).
        private const val MAX_SIDE_PX = 1024
    }

    /** Redimensiona la imagen si excede [MAX_SIDE_PX] de lado largo; si falla la decodificación (formato
     * raro, imagen corrupta) se manda la original en vez de romper el análisis por un paso que es
     * puramente de optimización. */
    private fun resizeIfNeeded(imageBase64: String): String {
        val original = try {
            Base64.getDecoder().decode(imageBase64)
        } catch (e: Exception) {
            return imageBase64
        }
        val image = try {
            ImageIO.read(ByteArrayInputStream(original)) ?: return imageBase64
        } catch (e: Exception) {
            logger.warn("No se pudo decodificar la imagen para redimensionarla, se manda la original", e)
            return imageBase64
        }

        val longestSide = maxOf(image.width, image.height)
        if (longestSide <= MAX_SIDE_PX) return imageBase64

        val scale = MAX_SIDE_PX.toDouble() / longestSide
        val targetWidth = (image.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (image.height * scale).toInt().coerceAtLeast(1)

        val scaled = image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)
        val resized = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB)
        resized.createGraphics().apply {
            drawImage(scaled, 0, 0, null)
            dispose()
        }

        val output = ByteArrayOutputStream()
        val wrote = ImageIO.write(resized, "jpg", output)
        if (!wrote) return imageBase64

        logger.debug(
            "Imagen redimensionada de {}x{} a {}x{} ({} KB -> {} KB)",
            image.width, image.height, targetWidth, targetHeight,
            original.size / 1024, output.size() / 1024
        )
        return Base64.getEncoder().encodeToString(output.toByteArray())
    }

    suspend fun analyzeBody(imageBase64Original: String): BodyAnalysisResponse {

        val imageBase64 = resizeIfNeeded(imageBase64Original)

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
        logger.debug("OpenAI raw response: {}", raw)

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