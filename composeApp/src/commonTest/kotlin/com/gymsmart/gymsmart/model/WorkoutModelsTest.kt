package com.gymsmart.gymsmart.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * La aritmética del entrenamiento: convertir entre "lo que el usuario apunta" y el peso real.
 * Desde la Tarea 7 cada serie guarda cómo se apuntó (a cada lado / unilateral); las series viejas,
 * sin esos campos, se leen como siempre se leyeron según el tipo.
 */
class WorkoutModelsTest {

    private val barra = WorkoutExercise(id = "1", name = "Press banca", kind = WorkoutKind.BARRA, barKg = 20.0)
    private val mancuernas = WorkoutExercise(id = "2", name = "Laterales", kind = WorkoutKind.MANCUERNAS)
    private val maquina = WorkoutExercise(id = "3", name = "Aperturas", kind = WorkoutKind.MAQUINA)
    private val corporal = WorkoutExercise(id = "4", name = "Dominadas", kind = WorkoutKind.CORPORAL)

    private fun set(v: Double, reps: Int = 10, ps: Boolean? = null, uni: Boolean? = null) =
        WorkoutSet(value = v, reps = reps, perSide = ps, unilateral = uni)

    @Test
    fun a_cada_lado_en_barra_suma_los_dos_lados_y_la_barra() {
        assertEquals(80.0, barra.totalKg(set(30.0, ps = true)))
        assertEquals(20.0, barra.totalKg(set(0.0, ps = true)))
        assertEquals(70.0, barra.copy(barKg = 10.0).totalKg(set(30.0, ps = true)))
    }

    @Test
    fun en_total_el_numero_es_el_peso_real() {
        assertEquals(75.0, barra.totalKg(set(75.0, ps = false)))
        assertEquals(24.0, mancuernas.totalKg(set(24.0, ps = false)))
    }

    @Test
    fun a_cada_lado_fuera_de_la_barra_es_el_doble() {
        assertEquals(24.0, mancuernas.totalKg(set(12.0, ps = true)))
        assertEquals(50.0, maquina.totalKg(set(25.0, ps = true)))
    }

    @Test
    fun el_lastre_ignora_a_cada_lado() {
        assertEquals(10.0, corporal.totalKg(set(10.0, ps = true)))
        assertEquals(10.0, corporal.totalKg(set(10.0, ps = false)))
    }

    @Test
    fun las_series_viejas_se_leen_segun_el_tipo() {
        // Antes de la Tarea 7 barra y mancuernas se apuntaban a cada lado; máquina y polea en total.
        assertEquals(80.0, barra.totalKg(set(30.0)))
        assertEquals(24.0, mancuernas.totalKg(set(12.0)))
        assertEquals(40.0, maquina.totalKg(set(40.0)))
        assertTrue(barra.defaultPerSide)
        assertFalse(maquina.defaultPerSide)
        assertFalse(set(30.0).isUnilateral)
    }

    @Test
    fun valueForTotal_es_el_camino_inverso() {
        for (ps in listOf(true, false)) for (k in WorkoutKind.ALL) {
            val total = 80.0
            val v = valueForTotal(k.id, 20.0, total, ps)
            assertEquals(total, totalKgOf(k.id, 20.0, v, ps), "${k.id} ps=$ps")
        }
        assertEquals(30.0, valueForTotal(WorkoutKind.BARRA, 20.0, 80.0, true))
        assertEquals(0.0, valueForTotal(WorkoutKind.BARRA, 20.0, 10.0, true)) // nunca negativo
    }

    @Test
    fun el_volumen_usa_el_peso_real_y_cuenta_los_dos_lados_en_unilateral() {
        assertEquals(800.0, barra.volume(listOf(set(30.0, ps = true))))
        assertEquals(400.0, maquina.volume(listOf(set(20.0, uni = true))))
        // Mezclado en el mismo ejercicio: cada serie con su configuración.
        assertEquals(800.0 + 1500.0, barra.volume(listOf(set(30.0, ps = true), set(75.0, reps = 10, ps = false, uni = true))))
    }

    @Test
    fun la_etiqueta_de_la_serie() {
        assertEquals("37,5 kg a cada lado · 11 reps · unilateral", barra.setLabel(set(37.5, 11, ps = true, uni = true)))
        assertEquals("75 kg · 8 reps", barra.setLabel(set(75.0, 8, ps = false)))
        assertEquals("Peso corporal · 12 reps", corporal.setLabel(set(0.0, 12)))
        assertEquals("+10 kg · 6 reps", corporal.setLabel(set(10.0, 6)))
    }

    @Test
    fun unidad_y_rotulo_siguen_a_cada_lado() {
        assertEquals("kg/lado", unitFor(WorkoutKind.BARRA, true))
        assertEquals("kg/mano", unitFor(WorkoutKind.MANCUERNAS, true))
        assertEquals("kg", unitFor(WorkoutKind.MAQUINA, false))
        assertEquals("kg lastre", unitFor(WorkoutKind.CORPORAL, true))
        assertEquals("LO QUE PONES A CADA LADO", bigLabelFor(WorkoutKind.BARRA, true))
        assertEquals("PESO TOTAL CON BARRA", bigLabelFor(WorkoutKind.BARRA, false))
        assertEquals("PESO DE CADA MANCUERNA", bigLabelFor(WorkoutKind.MANCUERNAS, true))
    }

    @Test
    fun formato_espanol_sin_ceros_de_mas() {
        assertEquals("62,5", formatKg(62.5))
        assertEquals("80", formatKg(80.0))
        assertEquals("33,75", formatKg(33.75))
        assertEquals("—", rirLabel(null))
        assertEquals("4+", rirLabel(4))
    }

    @Test
    fun los_ejercicios_unidos_comparten_grupo() {
        val src = barra
        val linked = src.copy(id = "9", linkId = src.groupId)
        assertEquals("1", src.groupId)
        assertEquals(src.groupId, linked.groupId)
        assertEquals("9", maquina.copy(id = "9").groupId)
    }

    @Test
    fun el_nombre_normalizado_ignora_mayusculas_tildes_y_espacios() {
        assertEquals("press banca", normalizeExerciseName("  Press   Banca "))
        assertEquals(normalizeExerciseName("Elevación lateral"), normalizeExerciseName("elevacion  LATERAL"))
        assertTrue(normalizeExerciseName("Press inclinado") != normalizeExerciseName("Press banca"))
    }

    @Test
    fun el_historico_lee_cada_serie_con_su_configuracion() {
        val vieja = WorkoutSession(
            date = "2026-11-25", dayName = "Espalda",
            exercises = listOf(SessionExercise("Remo", WorkoutKind.POLEA, 20.0, listOf(set(20.0)), unilateral = true))
        )
        val nueva = WorkoutSession(
            date = "2026-11-26", dayName = "Espalda",
            exercises = listOf(SessionExercise("Remo", WorkoutKind.POLEA, 20.0, listOf(set(10.0, ps = true, uni = false))))
        )
        assertEquals(400.0, vieja.volumeKg) // la opción vieja del ejercicio sigue valiendo
        assertEquals(200.0, nueva.volumeKg) // la de la serie manda
        assertEquals(80.0, sessionTotalKg(WorkoutKind.BARRA, 30.0, 20.0))
    }
}
