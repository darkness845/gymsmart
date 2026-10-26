package com.gymsmart.gymsmart.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/** Turso respondió con un fallo real (HTTP de error, o un statement con "type":"error"). */
class TursoException(message: String) : Exception(message)

class TursoService {
    private val logger = LoggerFactory.getLogger(TursoService::class.java)

    private val dbUrl = System.getenv("TURSO_DB_URL")
        ?: throw IllegalStateException("TURSO_DB_URL no definida")
    private val authToken = System.getenv("TURSO_AUTH_TOKEN")
        ?: throw IllegalStateException("TURSO_AUTH_TOKEN no definida")

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    // Ejecuta una query y devuelve el JsonObject completo.
    // Antes, un fallo de Turso (token caducado, cuota superada, constraint violado) se devolvía
    // igual que un resultado vacío: las lecturas parecían "sin datos" y las escrituras fallidas se
    // reportaban como correctas. Aquí se comprueba el status HTTP y el campo "error" de cada
    // statement, y se lanza TursoException en vez de dejarlo pasar en silencio.
    suspend fun execute(sql: String, args: List<String> = emptyList()): JsonObject {
        val response = client.post("$dbUrl/v2/pipeline") {
            header("Authorization", "Bearer $authToken")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                putJsonArray("requests") {
                    addJsonObject {
                        put("type", "execute")
                        putJsonObject("stmt") {
                            put("sql", sql)
                            putJsonArray("args") {
                                args.forEach { arg ->
                                    addJsonObject {
                                        put("type", "text")
                                        put("value", arg)
                                    }
                                }
                            }
                        }
                    }
                    addJsonObject { put("type", "close") }
                }
            })
        }

        if (!response.status.isSuccess()) {
            val raw = response.bodyAsText()
            logger.error("Turso respondió {} para una query", response.status)
            throw TursoException("Turso respondió ${response.status}: $raw")
        }

        val body: JsonObject = response.body()
        val firstResult = body["results"]?.jsonArray?.firstOrNull()?.jsonObject
        if (firstResult?.get("type")?.jsonPrimitive?.contentOrNull == "error") {
            val errMsg = firstResult["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
                ?: "error desconocido"
            logger.error("Turso devolvió error en la query: {}", errMsg)
            throw TursoException("Turso: $errMsg")
        }

        return body
    }

    // Helper: extrae las filas del resultado
    fun extractRows(result: JsonObject): List<List<String?>> {
        val results = result["results"]?.jsonArray ?: return emptyList()
        val firstResult = results.firstOrNull()?.jsonObject ?: return emptyList()
        val response = firstResult["response"]?.jsonObject ?: return emptyList()
        val resultObj = response["result"]?.jsonObject ?: return emptyList()
        val rows = resultObj["rows"]?.jsonArray ?: return emptyList()

        return rows.map { row ->
            row.jsonArray.map { cell ->
                cell.jsonObject["value"]?.jsonPrimitive?.contentOrNull
            }
        }
    }
}