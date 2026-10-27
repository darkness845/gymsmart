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

    // IA-3 (doc/08-backlog-mejoras.md): el `Json` global por defecto es estricto con los tipos — si el
    // modelo devuelve, por ejemplo, `"bodyFat": 15` (número) en vez de `"bodyFat": "15"` (string), el
    // decodificador lanzaba excepción y el análisis entero se perdía por un detalle de formato que no
    // afecta al contenido real de la respuesta. `isLenient` acepta esa clase de discrepancias de tipo.
    private val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }

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

            // IA-2 (doc/08-backlog-mejoras.md): sin esto, la misma foto analizada dos veces podía dar
            // porcentajes distintos (temperature por defecto es 1.0, máxima variabilidad). Un valor bajo
            // y fijo no elimina la variabilidad del todo (sigue siendo un modelo probabilístico), pero la
            // reduce mucho — razonable para una estimación que se quiere consistente, no creativa.
            put("temperature", 0.2)
            // Modo JSON nativo de gpt-4o: más fiable que pedírselo solo por prompt (que ya de por sí
            // podía devolver markdown, de ahí el `replace` de más abajo, que se deja como red de
            // seguridad por si el modelo lo ignora).
            putJsonObject("response_format") { put("type", "json_object") }

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
                                - Estima siempre un valor razonable basado en el físico visible, nunca
                                  devuelvas campos vacíos ni digas que no puedes ver la imagen: bodyFat,
                                  estimatedWeight, muscleMass y physiqueType deben llevar siempre un
                                  valor.
                                - Si la foto es de mala calidad, el ángulo no es claro o cualquier otro
                                  motivo hace la estimación poco fiable, acláralo en el campo "summary"
                                  en vez de fingir una certeza que no tienes — pero igual da tu mejor
                                  estimación en los demás campos.
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
            lenientJson.decodeFromString<BodyAnalysisResponse>(cleaned)
        } catch (e: Exception) {
            throw IllegalStateException("JSON inválido del modelo:\n$cleaned", e)
        }
    }
}