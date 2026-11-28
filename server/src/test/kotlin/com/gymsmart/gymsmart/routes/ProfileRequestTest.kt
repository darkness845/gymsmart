package com.gymsmart.gymsmart.routes

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Compatibilidad del cuerpo de `POST /profile/save` con las apps **ya instaladas**.
 *
 * El servidor usa `json()` sin argumentos, es decir `Json.Default`: estricto con las claves
 * desconocidas. Eso ya obligó una vez a conservar un campo muerto (`hasWearable`, AUD-16) porque
 * quitarlo habría devuelto un 400 a las apps instaladas. El riesgo simétrico al **añadir** un campo es
 * que su valor por defecto sea el equivocado: `recordWeighIn` gobierna si se registra un pesaje, así
 * que si una app vieja —que no manda el campo— se interpretara como `false`, dejaría de registrar
 * pesajes **en silencio**, y el autoajuste se quedaría sin datos sin que nada avisara.
 */
class ProfileRequestTest {

    private val json = Json

    /** El cuerpo que manda una app anterior a C-6: sin `recordWeighIn`. */
    private val cuerpoAppInstalada = """
        {"weightKg":75.5,"heightCm":184.0,"age":21,"sex":"male",
         "activityLevel":"sedentary","goal":"lose_fat","goalRate":-0.75,"hasWearable":false}
    """.trimIndent()

    @Test
    fun una_app_sin_el_campo_nuevo_sigue_registrando_pesaje() {
        val req = json.decodeFromString<ProfileRequest>(cuerpoAppInstalada)
        assertTrue(
            req.recordWeighIn,
            "Una app instalada que no manda el campo tiene que seguir registrando el pesaje"
        )
        // Y el resto del cuerpo se sigue leyendo igual.
        assertEquals(75.5, req.weightKg)
        assertEquals("lose_fat", req.goal)
    }

    @Test
    fun el_campo_se_respeta_cuando_la_app_lo_manda() {
        val sinPesaje = json.decodeFromString<ProfileRequest>(
            """
            {"weightKg":75.5,"heightCm":184.0,"age":21,"sex":"male",
             "activityLevel":"sedentary","goal":"lose_fat","goalRate":-0.75,"recordWeighIn":false}
            """.trimIndent()
        )
        assertFalse(sinPesaje.recordWeighIn, "El reguardado de datos personales no debe registrar pesaje")

        val conPesaje = json.decodeFromString<ProfileRequest>(
            """
            {"weightKg":76.0,"heightCm":184.0,"age":21,"sex":"male",
             "activityLevel":"sedentary","goal":"lose_fat","goalRate":-0.75,"recordWeighIn":true}
            """.trimIndent()
        )
        assertTrue(conPesaje.recordWeighIn)
    }

    @Test
    fun el_cuerpo_sin_hasWearable_tampoco_falla() {
        // La otra mitad de la compatibilidad: una app *nueva* que ya no mande el campo muerto.
        val req = json.decodeFromString<ProfileRequest>(
            """
            {"weightKg":75.5,"heightCm":184.0,"age":21,"sex":"male",
             "activityLevel":"sedentary","goal":"lose_fat","goalRate":-0.75}
            """.trimIndent()
        )
        assertTrue(req.recordWeighIn)
        assertFalse(req.hasWearable)
    }
}
