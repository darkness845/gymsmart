package com.gymsmart.gymsmart.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.*

class TursoService {
    private val dbUrl = System.getenv("TURSO_DB_URL")
        ?: throw IllegalStateException("TURSO_DB_URL no definida")
    private val authToken = System.getenv("TURSO_AUTH_TOKEN")
        ?: throw IllegalStateException("TURSO_AUTH_TOKEN no definida")

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }

    // Ejecuta una query y devuelve el JsonObject completo
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
        return response.body()
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