package com.gymsmart.gymsmart.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    // ── Calorias activas por nivel ────────────────────────────────────────────

    /** BMR de referencia: hombre de 80 kg, 180 cm, 25 anos (Mifflin-St Jeor). */
    private val bmrRef = NutritionMath.bmr("male", 80.0, 180.0, 25)

    @Test
    fun las_calorias_activas_suben_con_el_nivel() {
        var anterior = -1.0
        for (nivel in listOf("sedentary", "light", "moderate", "active", "very_active")) {
            val kcal = ActivityBenchmarks.activeKcalFor(nivel, bmrRef)
            assertNotNull(kcal, "$nivel se quedo sin calorias activas")
            assertTrue(kcal > anterior, "$nivel ($kcal) no sube respecto al anterior ($anterior)")
            anterior = kcal
        }
    }

    @Test
    fun el_TEF_esta_descontado_para_que_sea_comparable_con_la_pulsera() {
        // Esta es LA comprobacion de esta funcion. Lo que devuelve tiene que ser menor que la resta
        // ingenua (gasto - BMR), porque esa resta incluye la digestion y la pulsera no la cuenta.
        // Si alguien "simplifica" la formula a bmr * (pal - 1), este test lo caza.
        for (nivel in listOf("sedentary", "light", "moderate", "active", "very_active")) {
            val pal = NutritionMath.activityMultiplier(nivel)
            val restaIngenua = bmrRef * (pal - 1.0)
            val conTefDescontado = ActivityBenchmarks.activeKcalFor(nivel, bmrRef)!!
            assertTrue(
                conTefDescontado < restaIngenua,
                "$nivel: $conTefDescontado deberia ser menor que la resta sin TEF ($restaIngenua)"
            )
            // Y la diferencia tiene que ser exactamente el TEF del gasto de ese nivel.
            val tef = ActivityBenchmarks.TEF_FRACTION * bmrRef * pal
            assertEquals(restaIngenua - tef, conTefDescontado, 0.001)
        }
    }

    @Test
    fun el_resultado_cuadra_con_el_gasto_total_que_calcula_la_app() {
        // Invariante de coherencia: BMR + activas + TEF tiene que dar el mismo gasto que
        // NutritionMath.tdee. Si alguien cambia un multiplicador, las dos cosas se mueven juntas.
        for (nivel in listOf("sedentary", "light", "moderate", "active", "very_active")) {
            val gasto = NutritionMath.tdee(bmrRef, nivel)
            val activas = ActivityBenchmarks.activeKcalFor(nivel, bmrRef)!!
            val tef = ActivityBenchmarks.TEF_FRACTION * gasto
            assertEquals(gasto, bmrRef + activas + tef, 0.001)
        }
    }

    @Test
    fun el_orden_de_magnitud_coincide_con_el_coste_de_andar() {
        // Comprobacion cruzada por un camino independiente de la formula: andar cuesta del orden de
        // 0,75 kcal/kg/km brutos y un paso mide ~0,72 m, asi que los ~4.000 pasos de un sedentario
        // de 80 kg salen por unas 170 kcal. No se exige exactitud, solo que no haya un factor 10.
        val kcalPorKgPorKm = 0.75
        val pasosSedentario = 4000
        val kcalAndando = kcalPorKgPorKm * 80.0 * (pasosSedentario * 0.72 / 1000.0)
        val estimado = ActivityBenchmarks.activeKcalFor("sedentary", bmrRef)!!
        assertTrue(
            estimado > kcalAndando * 0.5 && estimado < kcalAndando * 3.0,
            "sedentario estimado en $estimado kcal frente a $kcalAndando andando: se han ido lejos"
        )
    }

    @Test
    fun la_pulsera_y_los_niveles_desconocidos_no_inventan_calorias() {
        assertNull(ActivityBenchmarks.activeKcalFor(NutritionMath.ACTIVITY_WEARABLE, bmrRef))
        assertNull(ActivityBenchmarks.activeKcalFor("no_existe", bmrRef))
        assertNull(ActivityBenchmarks.activeKcalLabelFor(NutritionMath.ACTIVITY_WEARABLE, bmrRef))
    }

    @Test
    fun un_BMR_imposible_no_produce_un_numero() {
        // Si el perfil esta a medio rellenar, mas vale no decir nada que decir una cifra absurda.
        assertNull(ActivityBenchmarks.activeKcalFor("moderate", 0.0))
        assertNull(ActivityBenchmarks.activeKcalFor("moderate", -500.0))
        assertNull(ActivityBenchmarks.activeKcalFor("moderate", Double.NaN))
    }

    @Test
    fun nunca_se_muestra_un_gasto_negativo() {
        // Con un BMR minusculo el termino puede irse por debajo de cero; se recorta, no se muestra.
        assertTrue(ActivityBenchmarks.activeKcalFor("sedentary", 1.0)!! >= 0.0)
    }

    @Test
    fun la_etiqueta_se_redondea_a_la_decena() {
        val etiqueta = ActivityBenchmarks.activeKcalLabelFor("moderate", bmrRef)!!
        assertTrue(etiqueta.startsWith("~"), "falta la marca de estimacion: $etiqueta")
        assertTrue(etiqueta.endsWith("kcal activas/dia".replace("dia", "día")), etiqueta)
        val numero = Regex("[0-9]+").find(etiqueta)!!.value.toInt()
        assertEquals(0, numero % 10, "no esta redondeado a la decena: $etiqueta")
    }

    @Test
    fun la_linea_de_ayuda_junta_pasos_y_calorias_y_aguanta_sin_BMR() {
        val conBmr = ActivityBenchmarks.hintFor("moderate", bmrRef)!!
        assertTrue(conBmr.contains("pasos"), conBmr)
        assertTrue(conBmr.contains("kcal"), conBmr)

        // Sin BMR (perfil incompleto) sigue dando los pasos, que es mejor que no dar nada.
        val sinBmr = ActivityBenchmarks.hintFor("moderate")!!
        assertEquals(ActivityBenchmarks.stepsFor("moderate"), sinBmr)

        assertNull(ActivityBenchmarks.hintFor(NutritionMath.ACTIVITY_WEARABLE, bmrRef))
    }

    // ── Del día de hoy al nivel ───────────────────────────────────────────────

    @Test
    fun los_cortes_de_un_dia_son_los_del_baremo() {
        // Los bordes exactos: cada corte pertenece al nivel de arriba, igual que en el texto
        // "5.000–7.499" de stepsFor.
        assertEquals("sedentary", ActivityBenchmarks.levelForSteps(0))
        assertEquals("sedentary", ActivityBenchmarks.levelForSteps(4_999))
        assertEquals("light", ActivityBenchmarks.levelForSteps(5_000))
        assertEquals("light", ActivityBenchmarks.levelForSteps(7_499))
        assertEquals("moderate", ActivityBenchmarks.levelForSteps(7_500))
        assertEquals("moderate", ActivityBenchmarks.levelForSteps(9_999))
        assertEquals("active", ActivityBenchmarks.levelForSteps(10_000))
        assertEquals("active", ActivityBenchmarks.levelForSteps(12_499))
        assertEquals("very_active", ActivityBenchmarks.levelForSteps(12_500))
        assertEquals("very_active", ActivityBenchmarks.levelForSteps(40_000))
    }

    @Test
    fun los_cortes_numericos_coinciden_con_el_texto_del_baremo() {
        // Protege de cambiar un corte en un sitio y olvidar el otro: el último número del texto de
        // cada nivel, +1, tiene que ser donde empieza el siguiente.
        val numeros = Regex("[0-9][0-9.]*")
        for (nivel in listOf("light", "moderate", "active")) {
            val texto = ActivityBenchmarks.stepsFor(nivel)!!
            val desde = numeros.findAll(texto).first().value.replace(".", "").toLong()
            assertEquals(nivel, ActivityBenchmarks.levelForSteps(desde), "$nivel debería empezar en $desde")
            assertNotEquals(nivel, ActivityBenchmarks.levelForSteps(desde - 1),
                "$nivel no debería empezar antes de $desde")
        }
    }

    @Test
    fun la_meta_es_el_siguiente_corte_y_desaparece_en_el_maximo() {
        assertEquals(5_000L, ActivityBenchmarks.nextLevelThreshold(0))
        assertEquals(10_000L, ActivityBenchmarks.nextLevelThreshold(7_842))
        assertEquals(12_500L, ActivityBenchmarks.nextLevelThreshold(10_000))
        assertNull(ActivityBenchmarks.nextLevelThreshold(12_500))
    }

    @Test
    fun un_dato_negativo_no_rompe_nada() {
        assertEquals("sedentary", ActivityBenchmarks.levelForSteps(-20))
        assertEquals(5_000L, ActivityBenchmarks.nextLevelThreshold(-20))
    }

    @Test
    fun todos_los_niveles_con_baremo_tienen_nombre() {
        for (nivel in listOf("sedentary", "light", "moderate", "active", "very_active")) {
            assertNotNull(ActivityBenchmarks.labelFor(nivel), "$nivel sin nombre")
        }
        assertNull(ActivityBenchmarks.labelFor(NutritionMath.ACTIVITY_WEARABLE))
    }
}
