package com.gymsmart.gymsmart.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La aritmética del entrenamiento: convertir entre "lo que el usuario apunta" y el peso real.
 * Es donde más fácil es meter la pata (un disco se pone a los dos lados, una mancuerna va en cada
 * mano) y no se puede comprobar a ojo desde la UI.
 */
class WorkoutModelsTest {

    private val barra = WorkoutExercise(id = "1", name = "Press banca", kind = WorkoutKind.BARRA, barKg = 20.0)
    private val mancuernas = WorkoutExercise(id = "2", name = "Laterales", kind = WorkoutKind.MANCUERNAS)
    private val maquina = WorkoutExercise(id = "3", name = "Aperturas", kind = WorkoutKind.MAQUINA)
    private val corporal = WorkoutExercise(id = "4", name = "Dominadas", kind = WorkoutKind.CORPORAL)

    @Test
    fun barra_suma_los_dos_lados_y_la_barra() {
        // 30 kg a cada lado de una barra de 20 son 80 kg, no 50.
        assertEquals(80.0, barra.totalKg(30.0))
        assertEquals(20.0, barra.totalKg(0.0))
        assertEquals(87.5, barra.totalKg(33.75))
    }

    @Test
    fun barra_con_otra_barra() {
        val z = barra.copy(barKg = 7.5)
        assertEquals(27.5, z.totalKg(10.0))
    }

    @Test
    fun mancuernas_cuentan_las_dos() {
        assertEquals(16.0, mancuernas.totalKg(8.0))
    }

    @Test
    fun maquina_y_lastre_son_el_numero_tal_cual() {
        assertEquals(45.0, maquina.totalKg(45.0))
        assertEquals(10.0, corporal.totalKg(10.0))
    }

    @Test
    fun escribir_el_total_hace_la_conversion_inversa() {
        assertEquals(30.0, barra.valueFromTotal(80.0))
        assertEquals(40.0, barra.valueFromTotal(100.0))
        assertEquals(8.0, mancuernas.valueFromTotal(16.0))
        assertEquals(45.0, maquina.valueFromTotal(45.0))
        // Un total por debajo de la barra no puede dar negativo.
        assertEquals(0.0, barra.valueFromTotal(10.0))
    }

    @Test
    fun ida_y_vuelta_del_total() {
        listOf(0.0, 1.25, 20.0, 33.75, 60.0).forEach { porLado ->
            assertEquals(porLado, barra.valueFromTotal(barra.totalKg(porLado)))
        }
    }

    @Test
    fun formato_espanol_sin_ceros_de_mas() {
        assertEquals("60", formatKg(60.0))
        assertEquals("62,5", formatKg(62.5))
        assertEquals("1,25", formatKg(1.25))
        assertEquals("0", formatKg(0.0))
        assertEquals("0,25", formatKg(0.25))
    }

    @Test
    fun la_etiqueta_de_la_serie_habla_en_la_unidad_del_ejercicio() {
        assertEquals("30 por lado · 10 reps", barra.setLabel(WorkoutSet(30.0, 10)))
        assertEquals("8 kg × 2 · 15 reps", mancuernas.setLabel(WorkoutSet(8.0, 15)))
        assertEquals("45 kg · 12 reps", maquina.setLabel(WorkoutSet(45.0, 12)))
        assertEquals("Peso corporal · 8 reps", corporal.setLabel(WorkoutSet(0.0, 8)))
        assertEquals("+10 kg · 8 reps", corporal.setLabel(WorkoutSet(10.0, 8)))
    }

    @Test
    fun el_volumen_usa_el_peso_real_no_el_apuntado() {
        val series = listOf(WorkoutSet(30.0, 10), WorkoutSet(30.0, 8))
        // (20 + 60) * 10 + (20 + 60) * 8 = 1440
        assertEquals(1440.0, barra.volume(series))
    }

    @Test
    fun el_peso_propuesto_sale_de_la_serie_que_toca() {
        val ex = barra.copy(
            lastSession = listOf(WorkoutSet(30.0, 10), WorkoutSet(30.0, 9), WorkoutSet(27.5, 8))
        )
        assertEquals(30.0, ex.lastSession[0].value)
        assertEquals(9, ex.lastSession[1].reps)
        assertEquals(27.5, ex.lastSession[2].value)
        // La cuarta serie no existía aquel día: no hay nada que proponer.
        assertNull(ex.lastSession.getOrNull(3))
    }

    @Test
    fun un_ejercicio_nuevo_no_propone_nada() {
        assertNull(barra.lastSession.getOrNull(0))
    }

    @Test
    fun cada_tipo_trae_su_salto_y_su_unidad() {
        assertEquals("kg/lado", WorkoutKind.of(WorkoutKind.BARRA).unit)
        assertEquals("kg/mano", WorkoutKind.of(WorkoutKind.MANCUERNAS).unit)
        assertEquals(2.5, WorkoutKind.of(WorkoutKind.MAQUINA).defaultStep)
        assertEquals(true, WorkoutKind.of(WorkoutKind.BARRA).usesBar)
        assertEquals(false, WorkoutKind.of(WorkoutKind.POLEA).usesBar)
        // Un id desconocido (plan viejo, dato corrupto) no revienta: cae en el primero.
        assertEquals(WorkoutKind.BARRA, WorkoutKind.of("inventado").id)
    }

    @Test
    fun rir_se_lee_igual_que_en_la_pantalla() {
        assertEquals("—", rirLabel(null))
        assertEquals("0", rirLabel(0))
        assertEquals("4+", rirLabel(4))
    }
}
