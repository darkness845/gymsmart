package com.gymsmart.gymsmart.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActivityBenchmarksTest {

    @Test
    fun todos_los_niveles_con_formula_tienen_baremo() {
        // El modo pulsera mide en vez de estimar, asi que es el unico sin baremo.
        for (nivel in NutritionMath.ALL_ACTIVITY_LEVELS) {
            if (nivel == NutritionMath.ACTIVITY_WEARABLE) {
                assertNull(ActivityBenchmarks.stepsFor(nivel), "la pulsera no necesita baremo")
            } else {
                assertNotNull(ActivityBenchmarks.stepsFor(nivel), "$nivel se quedo sin baremo")
                assertNotNull(ActivityBenchmarks.bandName(nivel), "$nivel se quedo sin banda")
            }
        }
    }

    @Test
    fun los_baremos_suben_con_el_nivel() {
        // Protege de que alguien reordene los rangos y queden incoherentes con el multiplicador.
        // Se compara el limite SUPERIOR de cada banda: "menos de 5.000" y "5.000-7.499" empiezan
        // por el mismo numero, asi que el primero no distingue.
        val numeros = Regex("[0-9][0-9.]*")
        var anterior = -1
        for (nivel in listOf("sedentary", "light", "moderate", "active", "very_active")) {
            val texto = ActivityBenchmarks.stepsFor(nivel)!!
            val n = numeros.findAll(texto).last().value.replace(".", "").toInt()
            assertTrue(n > anterior, "$nivel ($n) no sube respecto al nivel anterior ($anterior)")
            anterior = n
            assertTrue(
                NutritionMath.activityMultiplier(nivel) > 0.0,
                "$nivel tiene baremo pero no multiplicador"
            )
        }
    }

    @Test
    fun un_nivel_desconocido_no_inventa_baremo() {
        assertNull(ActivityBenchmarks.stepsFor("no_existe"))
        assertNull(ActivityBenchmarks.bandName(""))
    }

    @Test
    fun las_bandas_son_las_de_la_clasificacion_original() {
        assertEquals("sedentary", ActivityBenchmarks.bandName("sedentary"))
        assertEquals("low active", ActivityBenchmarks.bandName("light"))
        assertEquals("somewhat active", ActivityBenchmarks.bandName("moderate"))
        assertEquals("active", ActivityBenchmarks.bandName("active"))
        assertEquals("highly active", ActivityBenchmarks.bandName("very_active"))
    }
}
