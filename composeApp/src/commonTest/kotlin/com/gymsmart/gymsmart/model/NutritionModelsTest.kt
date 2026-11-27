package com.gymsmart.gymsmart.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests de [estimatedKcalPer100] y del rotulado de líquidos.
 *
 * El motivo de que esto exista está en doc/13 §D-3: la regla 4/4/9 no tiene término de fibra,
 * polioles ni alcohol, y los tres aparecen en productos reales del lineal español. Los casos usan
 * productos verificados contra Open Food Facts, con su EAN en el nombre del test.
 */
class NutritionModelsTest {

    private fun n(
        kcal: Double? = null,
        p: Double? = null,
        c: Double? = null,
        g: Double? = null,
        fibra: Double? = null,
        alcohol: Double? = null,
        polioles: Double? = null
    ) = Nutriments(
        energy_kcal_100g = kcal, proteins_100g = p, carbohydrates_100g = c, fat_100g = g,
        fiber_100g = fibra, alcohol_100g = alcohol, polyols_100g = polioles
    )

    @Test
    fun la_etiqueta_manda_sobre_cualquier_estimacion() {
        // Si el producto declara su energía, se usa esa y no se recalcula. Es la regla que doc/13 §D-2
        // fija como invariante: las kcal nunca se derivan de los macros cuando hay dato de origen.
        assertEquals(250.0, n(kcal = 250.0, p = 1.0, c = 1.0, g = 1.0).estimatedKcalPer100(), 0.0)
    }

    @Test
    fun sin_nutrientes_devuelve_cero() {
        assertEquals(0.0, null.estimatedKcalPer100(), 0.0)
        assertEquals(0.0, n().estimatedKcalPer100(), 0.0)
    }

    @Test
    fun la_fibra_vale_2_kcal_por_gramo_EAN_7300400481588() {
        // Wasa "Fibres": 333 kcal de etiqueta. 4/4/9 daría 281 (-15,6 %).
        assertEquals(333.0, n(p = 12.0, c = 47.0, g = 5.0, fibra = 26.0).estimatedKcalPer100(), 0.5)
    }

    @Test
    fun el_alcohol_vale_7_kcal_por_gramo_EAN_5410228142218() {
        // Leffe Blonde: 6,6 g de alcohol/100 ml. Sin ese término, 4/4/9 da 23,2 de los 58 reales.
        val cerveza = n(p = 0.4, c = 5.4, g = 0.0, alcohol = 6.6)
        assertEquals(69.4, cerveza.estimatedKcalPer100(), 0.1)
        assertTrue(cerveza.estimatedKcalPer100() > 23.2 * 2)
    }

    @Test
    fun los_polioles_se_cobran_a_2_4_y_no_se_cuentan_dos_veces() {
        // El Anexo I los mete dentro de los hidratos; el Anexo XIV los cobra a 2,4.
        val conMaltitol = n(p = 5.0, c = 60.0, g = 30.0, polioles = 40.0)
        val cuatroCuatroNueve = 5.0 * 4 + 60.0 * 4 + 30.0 * 9
        assertEquals(64.0, cuatroCuatroNueve - conMaltitol.estimatedKcalPer100(), 0.1)
    }

    @Test
    fun unos_polioles_incoherentes_no_dan_energia_negativa() {
        val raro = n(p = 0.0, c = 10.0, g = 0.0, polioles = 90.0)
        assertTrue(raro.estimatedKcalPer100() >= 0.0)
    }

    @Test
    fun un_producto_con_solo_fibra_declarada_ya_se_estima() {
        // Antes la rama de estimación exigía proteína, hidratos o grasa; con solo fibra caía al campo
        // ambiguo `energy-kcal` o a 0.
        assertEquals(20.0, n(fibra = 10.0).estimatedKcalPer100(), 0.1)
    }

    @Test
    fun el_campo_ambiguo_es_el_ultimo_recurso() {
        // `energy-kcal` sin sufijo no está garantizado por 100 g, así que solo se usa si no hay nada
        // más con lo que estimar.
        assertEquals(180.0, Nutriments(energy_kcal = 180.0).estimatedKcalPer100(), 0.0)
        // Si hay macros, se estiman en vez de creerse el campo ambiguo.
        assertEquals(40.0, Nutriments(energy_kcal = 999.0, proteins_100g = 10.0).estimatedKcalPer100(), 0.1)
    }

    // ── Base 100 g frente a 100 ml ───────────────────────────────────────────

    @Test
    fun un_producto_en_base_100ml_se_rotula_en_ml() {
        val leche = Product(product_name = "Leche entera", nutritionDataPer = "100ml")
        assertTrue(leche.isLiquid)
        assertEquals("ml", leche.unitLabel)
    }

    @Test
    fun lo_demas_se_rotula_en_gramos() {
        assertEquals("g", Product(nutritionDataPer = "100g").unitLabel)
        assertEquals("g", Product(nutritionDataPer = null).unitLabel)
        assertFalse(Product(nutritionDataPer = "100g").isLiquid)
    }

    @Test
    fun el_rotulado_no_depende_de_mayusculas_ni_espacios() {
        assertTrue(Product(nutritionDataPer = " 100ML ").isLiquid)
    }
}
