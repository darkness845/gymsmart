package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.nutrition.NutritionMath
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Decide qué tramo de datos puede usar el autoajuste, aplicando el periodo de gracia posterior a un
 * cambio de objetivo (C-5 en doc/12-auditoria-comite-kcal-2026-11-27.md).
 *
 * Vive fuera de [ProfileService] para poder probarse: la lógica de verdad aquí es aritmética de fechas
 * con un caso nulo y dos bordes, y `runAdaptiveAdjustment` no se puede instanciar sin un Turso real.
 */
internal object AdaptiveWindow {

    sealed class Result {
        /**
         * Todavía dentro del periodo de gracia: no se mide nada.
         *
         * @param daysLeft días que faltan para poder volver a medir, mínimo 1.
         */
        data class InGrace(val daysLeft: Int) : Result()

        /**
         * Se puede medir. [since] es el primer día cuyos datos cuentan y [windowDays] el ancho real de
         * la ventana, que es el que debe usarse para la cobertura: si se usara el ancho nominal, una
         * ventana recortada por la gracia daría una cobertura falsamente baja y el mensaje culparía al
         * usuario de no haber registrado comidas que sí registró.
         */
        data class Measurable(val since: LocalDate, val windowDays: Int) : Result()
    }

    /**
     * @param goalChangedAt cuándo se cambió objetivo o ritmo por última vez, en epoch millis. `null` en
     *   los perfiles anteriores a esa columna, y se trata como **sin gracia**: se mide como se medía
     *   antes, en vez de dejar sin autoajuste dos semanas a quien ya llevaba tiempo con su objetivo.
     * @param windowDays ancho nominal de la ventana (28 días).
     */
    fun compute(
        today:         LocalDate,
        goalChangedAt: Long?,
        windowDays:    Int,
        zone:          ZoneId = ZoneId.systemDefault(),
        graceDays:     Int = NutritionMath.ADAPTIVE_GOAL_CHANGE_GRACE_DAYS
    ): Result {
        val nominalSince = today.minusDays(windowDays.toLong())

        val graceEnd = goalChangedAt
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            ?.plusDays(graceDays.toLong())
            ?: return Result.Measurable(nominalSince, windowDays)

        if (graceEnd.isAfter(today)) {
            return Result.InGrace(ChronoUnit.DAYS.between(today, graceEnd).toInt())
        }

        // El arranque es el más reciente de los dos: un cambio de objetivo hace 16 días deja solo los
        // 2 días limpios posteriores, aunque la ventana nominal fuera de 28.
        val since = if (graceEnd.isAfter(nominalSince)) graceEnd else nominalSince
        return Result.Measurable(since, ChronoUnit.DAYS.between(since, today).toInt())
    }
}
