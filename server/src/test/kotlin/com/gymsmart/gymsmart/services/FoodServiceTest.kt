package com.gymsmart.gymsmart.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests del saneado de los datos de Open Food Facts y de la energía del Anexo XIV.
 *
 * Son los primeros tests del módulo `server`. Hasta ahora solo existía el esqueleto
 * `ApplicationTest`, y la auditoría del 2026-11-27 (doc/13) señaló esto como el hueco de test más
 * rentable del proyecto: toda la capa de datos de alimentos estaba sin cubrir.
 *
 * Los productos de los casos son **reales**, descargados de OFF-España durante esa auditoría, con su
 * EAN en el nombre del test para poder volver a comprobarlos.
 */
class FoodServiceTest {

    private fun nutr(
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

    private fun producto(n: Nutriments?, nombre: String = "X") =
        Product(product_name = nombre, nutriments = n)

    // ── Energía del Anexo XIV ────────────────────────────────────────────────

    @Test
    fun la_fibra_se_cobra_a_2_kcal_por_gramo_y_no_a_4() {
        // Wasa "Fibres" (EAN 7300400481588): 333 kcal de etiqueta. 4/4/9 da 281, un -15,6 %.
        val n = nutr(p = 12.0, c = 47.0, g = 5.0, fibra = 26.0)
        assertEquals(333.0, n.energiaAnexoXIV(), 0.5)
        val cuatroCuatroNueve = 12.0 * 4 + 47.0 * 4 + 5.0 * 9
        assertEquals(281.0, cuatroCuatroNueve, 0.5)
    }

    @Test
    fun el_alcohol_se_cuenta_a_7_kcal_por_gramo() {
        // Leffe Blonde (EAN 5410228142218): 58 kcal/100 ml de etiqueta, 6,6 g de alcohol.
        // Sin el término de alcohol, 4/4/9 da 23,2: un -60 %.
        val n = nutr(p = 0.4, c = 5.4, g = 0.0, alcohol = 6.6)
        assertEquals(69.4, n.energiaAnexoXIV(), 0.1)
        assertEquals(23.2, 0.4 * 4 + 5.4 * 4 + 0.0 * 9, 0.1)
    }

    @Test
    fun los_polioles_no_se_cuentan_dos_veces() {
        // El Anexo I mete los polioles dentro de los hidratos, y el Anexo XIV los cobra a 2,4.
        // 40 g de maltitol en 100 g: con 4/4/9 se sobrecuenta en 1,6 x 40 = 64 kcal.
        val conPolioles = nutr(p = 5.0, c = 60.0, g = 30.0, polioles = 40.0)
        val sinDistinguir = 5.0 * 4 + 60.0 * 4 + 30.0 * 9
        assertEquals(64.0, sinDistinguir - conPolioles.energiaAnexoXIV(), 0.1)
    }

    @Test
    fun unos_polioles_mayores_que_los_hidratos_no_dan_energia_negativa() {
        // Dato incoherente de OFF: polioles > carbohidratos. Se recorta, no se resta de más.
        val n = nutr(p = 0.0, c = 10.0, g = 0.0, polioles = 90.0)
        assertTrue(n.energiaAnexoXIV() >= 0.0)
        assertEquals(24.0, n.energiaAnexoXIV(), 0.1)
    }

    @Test
    fun sin_ningun_macro_la_energia_estimada_es_cero() {
        assertEquals(0.0, nutr().energiaAnexoXIV(), 0.0)
    }

    // ── Saneado: lo que debe rechazarse ──────────────────────────────────────

    @Test
    fun sin_bloque_de_nutrientes_no_hay_producto() {
        assertNull(sanearProducto(producto(null)))
    }

    @Test
    fun sin_kcal_por_100g_no_hay_producto() {
        // Es el caso del vino español en OFF: ni kcal, ni macros, ni alcohol. Antes esto llegaba al
        // selector de ración por el escáner y se podía registrar una comida de 0 kcal.
        assertNull(sanearProducto(producto(nutr(), "Vino tinto")))
    }

    @Test
    fun se_rechaza_una_energia_imposible() {
        assertNull(sanearProducto(producto(nutr(kcal = 0.0, p = 1.0))))
        assertNull(sanearProducto(producto(nutr(kcal = -10.0, p = 1.0))))
        // Por encima de 100 g de grasa pura (900 kcal) no hay alimento posible.
        assertNull(sanearProducto(producto(nutr(kcal = 1500.0, p = 0.0, c = 0.0, g = 100.0))))
    }

    @Test
    fun se_rechaza_coco_rallado_con_105_g_por_100_g_EAN_8411378000172() {
        assertNull(sanearProducto(producto(nutr(kcal = 735.0, p = 16.0, c = 24.0, g = 65.0), "Coco rallado")))
    }

    @Test
    fun un_exceso_de_decimas_sobre_100_g_se_tolera_a_proposito_EAN_8413176904720() {
        // "Aceite de Oliva" real: P+C+G = 100,6 g/100 g, que es imposible... por 0,6 g.
        //
        // Este test documenta que **se acepta**, y por qué. El umbral está en 101 g porque con ese
        // valor, de 102 productos españoles válidos medidos no se descarta ninguno; bajarlo a 100,5
        // para cazar este caso arriesga falsos positivos que no se han medido. Y el daño aquí es
        // nulo: 900 kcal con 100 g de grasa es justo lo que es un aceite, y las 0,6 g de proteína
        // son ruido de la etiqueta. Lo que el filtro tiene que cazar es el coco rallado (105 g) y,
        // sobre todo, la energía que no cuadra con los macros, que es donde están los errores
        // grandes (75 % y 150 %).
        assertNotNull(sanearProducto(producto(nutr(kcal = 900.0, p = 0.6, c = 0.0, g = 100.0), "Aceite de Oliva")))
    }

    @Test
    fun se_rechaza_un_queso_cuya_energia_no_cuadra_con_sus_macros_EAN_8480017232007() {
        // "Queso fresco de cabra" real: declara 99,6 kcal y sus macros dan 249. Error del 150 %.
        assertNull(sanearProducto(producto(nutr(kcal = 99.6, p = 13.5, c = 1.6, g = 21.0), "Queso fresco de cabra")))
    }

    @Test
    fun se_rechazan_unos_caramelos_cuya_energia_no_cuadra_EAN_8413080004868() {
        // Declara 238 kcal; sus macros dan 416. Error del 75 %.
        assertNull(sanearProducto(producto(nutr(kcal = 238.0, p = 7.0, c = 97.0, g = 0.0), "Caramelos de eucalipto")))
    }

    @Test
    fun se_rechaza_un_macro_fuera_de_rango_o_no_finito() {
        assertNull(sanearProducto(producto(nutr(kcal = 100.0, p = -5.0))))
        assertNull(sanearProducto(producto(nutr(kcal = 100.0, c = 150.0))))
        assertNull(sanearProducto(producto(nutr(kcal = Double.NaN, p = 10.0))))
        assertNull(sanearProducto(producto(nutr(kcal = Double.POSITIVE_INFINITY, p = 10.0))))
        assertNull(sanearProducto(producto(nutr(kcal = 100.0, p = 10.0, fibra = Double.NaN))))
    }

    @Test
    fun se_rechaza_un_producto_que_OFF_ya_marca_como_erroneo() {
        val bueno = producto(nutr(kcal = 120.0, p = 22.5, c = 0.0, g = 2.6), "Pechuga de pollo")
        assertNotNull(sanearProducto(bueno, errores = emptyList()))
        assertNull(sanearProducto(bueno, errores = listOf("en:nutrition-value-over-1000-energy-kcal")))
        // Un error de calidad que no afecta a los números no descarta el producto.
        assertNotNull(sanearProducto(bueno, errores = listOf("en:ingredients-single-ingredient-from-category-does-not-match-actual-ingredients")))
    }

    // ── Saneado: lo que debe aceptarse ───────────────────────────────────────

    @Test
    fun un_producto_correcto_pasa_intacto() {
        val p = producto(nutr(kcal = 120.0, p = 22.5, c = 0.0, g = 2.62), "Pechuga de pollo")
        assertEquals(p, sanearProducto(p))
    }

    @Test
    fun un_pan_con_mucha_fibra_pasa_pese_a_desviarse_de_4_4_9() {
        // All-Bran real: 412 kcal de etiqueta, 388 con 4/4/9. La fibra justifica la diferencia y el
        // margen del saneado tiene que dejarla pasar.
        assertNotNull(sanearProducto(producto(nutr(kcal = 412.0, p = 8.0, c = 62.0, g = 12.0, fibra = 12.0))))
    }

    @Test
    fun el_aceite_de_oliva_de_la_usda_pasa_el_techo_de_energia() {
        // 884 kcal/100 g: el alimento más calórico de la tabla de genéricos. No debe caerse.
        assertNotNull(sanearProducto(producto(nutr(kcal = 884.0, p = 0.0, c = 0.0, g = 100.0), "Aceite de oliva")))
    }

    @Test
    fun una_cerveza_con_alcohol_declarado_pasa_pese_a_que_sus_macros_no_cuadren() {
        // 58 kcal de etiqueta frente a 23 según 4/4/9: lo explica el etanol, así que con el término
        // de alcohol la comprobación cruzada cuadra y el producto se acepta.
        assertNotNull(sanearProducto(producto(nutr(kcal = 58.0, p = 0.4, c = 5.4, g = 0.0, alcohol = 6.6), "Cerveza")))
    }

    @Test
    fun un_producto_sin_macros_pero_con_kcal_pasa_porque_no_hay_con_que_contrastar() {
        // La comprobación cruzada solo se aplica si hay macros con los que estimar. Un producto con
        // kcal y sin desglose es pobre, pero no es imposible, y rechazarlo seria inventarse un error.
        assertNotNull(sanearProducto(producto(nutr(kcal = 250.0))))
    }
}
