package com.gymsmart.gymsmart

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    /**
     * Test del esqueleto de la plantilla de Kotlin Multiplatform, obsoleto por dos motivos
     * independientes, los dos comprobados el 2026-11-27:
     *
     * 1. **Comprueba una respuesta que ya no existe.** No hay ninguna ruta `get("/")` en el servidor,
     *    así que la aserción sobre `"Ktor: ${Greeting().greet()}"` no puede cumplirse.
     * 2. **Necesita la base de datos.** `module()` construye `TursoService`, que exige `TURSO_DB_URL`
     *    y lanza `IllegalStateException` sin ella.
     *
     * Estaba fallando desde antes de que hubiera cualquier otro test en este módulo, y eso dejaba la
     * tarea `:server:test` en rojo de forma permanente: de ahí que la lista de verificación de
     * CLAUDE.md use `:server:compileKotlin` y nunca `:server:test`, y que no se pudieran añadir
     * tests de servidor sin que se perdieran entre el ruido.
     *
     * Se desactiva en vez de borrarse para no tirar código sin que el usuario lo confirme. Cuando se
     * decida, lo que tiene sentido es sustituirlo por tests de ruta con `testApplication` inyectando
     * un `TursoService` de prueba — está anotado en doc/13 §3 como el hueco de test más rentable.
     */
    @Ignore("Obsoleto: no existe la ruta / y module() exige TURSO_DB_URL. Ver doc/13-auditoria-nutricion-2026-11-27.md")
    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Ktor: ${Greeting().greet()}", response.bodyAsText())
    }
}