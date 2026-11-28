package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.nutrition.NutritionMath
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Periodo de gracia del autoajuste tras cambiar de objetivo (C-5).
 *
 * El sesgo que esto corrige es grande y silencioso: el glucógeno y el agua que se mueven al empezar un
 * déficit se leen como gasto a 7700 kcal/kg, +278 kcal/día en la primera ventana, hasta el punto de que
 * el primer ajuste sale **peor que no ajustar**. Pero el mecanismo es aritmética de fechas con un caso
 * nulo y dos bordes, que es exactamente donde se cuelan los fallos de uno en uno.
 */
class AdaptiveWindowTest {

    private val zona = ZoneId.of("UTC")
    private val hoy = LocalDate.of(2026, 11, 28)
    private val ventanaNominal = 28

    /** Epoch millis del día indicado, a mediodía para no depender del borde del día. */
    private fun hace(dias: Long): Long =
        ZonedDateTime.of(hoy.minusDays(dias).atTime(12, 0), zona).toInstant().toEpochMilli()

    private fun calcula(goalChangedAt: Long?) =
        AdaptiveWindow.compute(hoy, goalChangedAt, ventanaNominal, zona)

    @Test
    fun sin_fecha_de_cambio_no_hay_gracia() {
        // Perfil anterior a la columna: se mide como se medía antes. Bloquear el autoajuste dos semanas
        // a quien ya llevaba tiempo con su objetivo puesto sería un castigo por una migración.
        val r = assertIs<AdaptiveWindow.Result.Measurable>(calcula(null))
        assertEquals(hoy.minusDays(28), r.since)
        assertEquals(28, r.windowDays, "La ventana debe ser la nominal íntegra")
    }

    @Test
    fun un_cambio_reciente_bloquea_la_medicion() {
        val r = assertIs<AdaptiveWindow.Result.InGrace>(calcula(hace(3)))
        assertEquals(11, r.daysLeft, "Cambió hace 3 días, la gracia es de 14: faltan 11")
    }

    @Test
    fun un_cambio_hoy_mismo_bloquea_los_catorce_dias() {
        val r = assertIs<AdaptiveWindow.Result.InGrace>(calcula(hace(0)))
        assertEquals(NutritionMath.ADAPTIVE_GOAL_CHANGE_GRACE_DAYS, r.daysLeft)
    }

    @Test
    fun el_dia_que_vence_la_gracia_ya_se_puede_medir() {
        // Borde: la gracia vence exactamente hoy. No debe quedar bloqueado un día más.
        val r = assertIs<AdaptiveWindow.Result.Measurable>(
            calcula(hace(NutritionMath.ADAPTIVE_GOAL_CHANGE_GRACE_DAYS.toLong()))
        )
        assertEquals(hoy, r.since)
        assertEquals(0, r.windowDays, "Todavía no hay días limpios, pero ya no está en gracia")
    }

    @Test
    fun los_dias_previos_al_cambio_quedan_fuera_de_la_ventana() {
        // Cambió hace 16 días: solo los 2 días posteriores a la gracia pueden medirse, aunque la ventana
        // nominal sean 28. Si se usaran los 28, entrarían 14 días de la etapa anterior más los 14 de
        // glucógeno — justo lo que el periodo de gracia existe para excluir.
        val r = assertIs<AdaptiveWindow.Result.Measurable>(calcula(hace(16)))
        assertEquals(hoy.minusDays(2), r.since)
        assertEquals(2, r.windowDays)
    }

    @Test
    fun un_cambio_antiguo_no_recorta_nada() {
        // Cambió hace 90 días: la gracia venció hace mucho y la ventana vuelve a ser la nominal.
        val r = assertIs<AdaptiveWindow.Result.Measurable>(calcula(hace(90)))
        assertEquals(hoy.minusDays(28), r.since)
        assertEquals(28, r.windowDays)
    }

    @Test
    fun la_ventana_efectiva_nunca_supera_la_nominal_ni_es_negativa() {
        // Barrido de todos los casos de cambio reciente, que es donde la aritmética puede descuadrar.
        for (dias in 0L..120L) {
            when (val r = calcula(hace(dias))) {
                is AdaptiveWindow.Result.InGrace ->
                    assertTrue(r.daysLeft in 1..NutritionMath.ADAPTIVE_GOAL_CHANGE_GRACE_DAYS,
                        "daysLeft fuera de rango con cambio hace $dias días: ${r.daysLeft}")
                is AdaptiveWindow.Result.Measurable -> {
                    assertTrue(r.windowDays in 0..ventanaNominal,
                        "Ventana de ${r.windowDays} días con cambio hace $dias días")
                    assertTrue(!r.since.isAfter(hoy), "La ventana no puede empezar en el futuro")
                }
            }
        }
    }
}
