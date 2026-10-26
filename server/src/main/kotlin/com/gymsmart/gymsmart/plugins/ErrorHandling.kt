package com.gymsmart.gymsmart.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ErrorHandling")

/**
 * Sin esto, una excepción no capturada en una ruta (p.ej. Turso caído o devolviendo un error)
 * deja al cliente sin respuesta útil. Con esto, siempre vuelve un JSON claro y nunca un fallo de
 * Turso se confunde con "no hay datos": antes TursoService se tragaba los errores en silencio.
 */
fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error("Error no controlado en {}", call.request.uri, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Error interno del servidor")
            )
        }
    }
}
