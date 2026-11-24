package com.gymsmart.gymsmart.nutrition

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CookingYieldTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.5) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "esperado ~$expected pero fue $actual (tolerancia $tolerance)"
        )
    }

    // ── Reconocimiento del alimento ───────────────────────────────────────────

    @Test
    fun reconoce_pechuga_de_pollo() {
        assertEquals("pollo_pechuga", findCookingYield("Pechuga de pollo fileteada")?.id)
    }

    @Test
    fun reconoce_el_muslo_antes_que_la_pechuga() {
        // El muslo va primero en el catálogo justamente para esto.
        assertEquals("pollo_muslo", findCookingYield("Muslo de pollo")?.id)
    }

    @Test
    fun reconoce_arroz_integral_antes_que_arroz_blanco() {
        assertEquals("arroz_integral", findCookingYield("Arroz integral")?.id)
        assertEquals("arroz_blanco", findCookingYield("Arroz largo")?.id)
    }

    @Test
    fun ignora_tildes_y_mayusculas() {
        assertEquals("pasta", findCookingYield("ESPAGUETIS")?.id)
        assertEquals("pasta", findCookingYield("Hélices de trigo duro")?.id)
    }

    @Test
    fun no_confunde_una_palabra_contenida_en_otra() {
        // "repollo" contiene "pollo"; "arrozal" contiene "arroz".
        assertNull(findCookingYield("Repollo"))
        assertNull(findCookingYield("Arrozales"))
    }

    // ── Casos en los que NO debe ofrecer conversión ────────────────────────────

    @Test
    fun no_convierte_lo_que_ya_viene_cocinado() {
        // Sus datos ya están en base cocinada: aplicar el factor sería contarlo dos veces.
        assertNull(findCookingYield("Arroz cocido"))
        assertNull(findCookingYield("Pollo asado"))
        assertNull(findCookingYield("Lentejas cocidas"))
    }

    @Test
    fun no_convierte_derivados_ni_preparados() {
        assertNull(findCookingYield("Bebida de arroz"))
        assertNull(findCookingYield("Nuggets de pollo"))
        assertNull(findCookingYield("Hummus de garbanzo"))
        assertNull(findCookingYield("Jamón de pavo"))
        assertNull(findCookingYield("Harina de lentejas"))
    }

    @Test
    fun no_convierte_un_producto_desconocido() {
        assertNull(findCookingYield("Yogur natural"))
        assertNull(findCookingYield("Aceite de oliva virgen extra"))
        assertNull(findCookingYield(null))
        assertNull(findCookingYield("   "))
    }

    // ── Conversión de valores por 100 g ───────────────────────────────────────

    @Test
    fun en_crudo_no_cambia_nada() {
        val yield = findCookingYield("Pechuga de pollo")!!
        assertEquals(120.0, yield.per100For(120.0, WeighBasis.RAW))
        assertEquals(250.0, yield.rawWeightFor(250.0, WeighBasis.RAW))
    }

    @Test
    fun la_carne_cocinada_concentra_las_kcal() {
        // Pechuga cruda 120 kcal/100 g; con factor 0,76 el cocinado sale 157,9, que es exactamente el
        // valor que la USDA midió en pechuga asada (157). Es la comprobación de que el factor no está
        // invertido: cocinar carne SUBE las kcal por 100 g.
        val yield = findCookingYield("Pechuga de pollo")!!
        val cooked = yield.per100For(120.0, WeighBasis.COOKED)
        assertClose(157.9, cooked, 1.0)
        assertTrue(cooked > 120.0, "cocinar carne tiene que subir las kcal por 100 g")
    }

    @Test
    fun el_arroz_cocido_diluye_las_kcal() {
        // Al contrario que la carne: absorbe agua, así que por 100 g hay menos kcal.
        val yield = findCookingYield("Arroz blanco")!!
        val cooked = yield.per100For(365.0, WeighBasis.COOKED)
        assertClose(130.0, cooked, 2.0)
        assertTrue(cooked < 365.0, "el arroz cocido tiene que bajar las kcal por 100 g")
    }

    @Test
    fun reconoce_pescado_de_varias_especies() {
        // El hueco más grave de la primera versión del catálogo: no había ni un pescado.
        for (nombre in listOf("Merluza", "Lomos de bacalao", "Salmón fresco", "Lubina", "Filete de panga")) {
            assertEquals("pescado", findCookingYield(nombre)?.id, "no reconoció $nombre")
        }
    }

    @Test
    fun no_convierte_pescado_de_lata() {
        // El atún en España es sobre todo de lata, y los datos de la lata ya son del producto final.
        assertNull(findCookingYield("Atún claro en aceite de girasol"))
        assertNull(findCookingYield("Atún en conserva"))
        assertNull(findCookingYield("Sardinas en escabeche"))
        assertNull(findCookingYield("Salmón ahumado"))
    }

    @Test
    fun se_puede_elegir_el_alimento_de_referencia_a_mano() {
        // Lo que hace que la función sirva para alimentos que el nombre no identifica.
        val elegido = cookingYieldById("pescado")
        assertNotNull(elegido)
        assertEquals("pescado", elegido.id)
        assertNull(cookingYieldById("no_existe"))
        assertNull(cookingYieldById(null))
    }

    @Test
    fun lo_ya_cocinado_se_detecta_aparte_de_la_busqueda() {
        // La interfaz lo necesita separado: si el producto ya viene cocinado, no se ofrece ni elegir
        // a mano; si simplemente no se reconoce, sí.
        assertTrue(isExcludedFromCooking("Arroz cocido"))
        assertTrue(isExcludedFromCooking("Nuggets de pollo"))
        assertFalse(isExcludedFromCooking("Solomillo de avestruz"))
        assertFalse(isExcludedFromCooking(null))
    }

    @Test
    fun todas_las_familias_tienen_alimentos() {
        // Si una familia quedase vacía, aparecería como un encabezado suelto en el diálogo.
        assertEquals(
            COOKING_YIELDS.size,
            COOKING_YIELD_FAMILIES.sumOf { (_, foods) -> foods.size },
            "hay alimentos del catálogo que no caen en ninguna familia"
        )
        for ((family, foods) in COOKING_YIELD_FAMILIES) {
            assertTrue(foods.isNotEmpty(), "la familia '$family' no tiene alimentos")
        }
    }

    @Test
    fun la_conversion_reproduce_los_valores_medidos_por_la_usda() {
        // Cada factor se derivó de una pareja crudo/cocido real: aplicarlo al valor crudo tiene que
        // devolver el valor cocido de esa misma pareja. Si alguien toca un factor sin tocar su fuente,
        // esto salta.
        val casos = listOf(
            Triple("Arroz blanco", 365.0, 130.0),
            Triple("Arroz integral", 367.0, 123.0),
            Triple("Espaguetis", 371.0, 158.0),
            Triple("Lentejas", 352.0, 116.0),
            Triple("Garbanzos", 378.0, 164.0),
            Triple("Quinoa", 368.0, 120.0),
            Triple("Cuscús", 376.0, 112.0),
            Triple("Bulgur", 342.0, 83.0),
            Triple("Cebada perlada", 352.0, 123.0),
            Triple("Alubias blancas", 333.0, 139.0),
            Triple("Alubias rojas", 333.0, 127.0),
            Triple("Alubias negras", 341.0, 132.0),
            Triple("Habas", 341.0, 110.0),
            Triple("Bacalao", 82.0, 105.0),
            Triple("Pechuga de pollo", 120.0, 157.0),
            Triple("Muslo de pollo", 121.0, 176.0),
            Triple("Pavo picado", 148.0, 203.0),
            Triple("Solomillo de cerdo", 109.0, 143.0),
            Triple("Gambas", 85.0, 99.0),
            Triple("Patata", 69.0, 92.0)
        )
        for ((name, raw, cookedEsperado) in casos) {
            val yield = assertNotNull(findCookingYield(name), "no reconoció $name")
            assertClose(cookedEsperado, yield.per100For(raw, WeighBasis.COOKED), 1.5)
        }
    }

    @Test
    fun el_total_de_la_racion_no_depende_de_la_base_elegida() {
        // La invariante de todo esto: la misma comida real tiene las mismas kcal, se pese antes o
        // después de cocinarla. 100 g de pollo crudo son 76 g de pollo hecho.
        val yield = findCookingYield("Pechuga de pollo")!!
        val kcalPer100Raw = 120.0

        val totalPesandoCrudo = yield.per100For(kcalPer100Raw, WeighBasis.RAW) / 100.0 * 100.0
        val totalPesandoCocinado = yield.per100For(kcalPer100Raw, WeighBasis.COOKED) / 100.0 * 76.0

        assertClose(totalPesandoCrudo, totalPesandoCocinado, 0.5)
    }

    @Test
    fun el_peso_crudo_equivalente_es_coherente() {
        val yield = findCookingYield("Pechuga de pollo")!!
        // 152 g de pollo hecho vienen de 200 g de crudo (152 / 0,76).
        assertClose(200.0, yield.rawWeightFor(152.0, WeighBasis.COOKED), 1.0)
    }

    @Test
    fun multiplicar_por_el_factor_deshace_la_conversion() {
        // El historial de alimentos guarda los valores ya convertidos, así que el cliente los
        // devuelve a base cruda multiplicando por el factor antes de reutilizarlos. Si eso no fuese
        // el inverso exacto de per100For, reutilizar un alimento pesado en cocido lo convertiría dos
        // veces. Esta prueba es la que fija esa relación.
        for (yield in COOKING_YIELDS) {
            val raw = 250.0
            val cooked = yield.per100For(raw, WeighBasis.COOKED)
            assertClose(raw, cooked * yield.factor, 0.001)
        }
    }

    // ── Integridad del catálogo ───────────────────────────────────────────────

    @Test
    fun el_catalogo_esta_bien_formado() {
        assertEquals(
            COOKING_YIELDS.size,
            COOKING_YIELDS.map { it.id }.distinct().size,
            "hay ids repetidos en el catálogo"
        )
        for (yield in COOKING_YIELDS) {
            assertTrue(yield.factor > 0.0, "${yield.id}: el factor tiene que ser positivo")
            assertTrue(yield.keywords.isNotEmpty(), "${yield.id}: sin palabras clave no se encuentra")
            assertTrue(yield.source.isNotBlank(), "${yield.id}: todo factor necesita su fuente")
            assertTrue(yield.label.isNotBlank(), "${yield.id}: sin etiqueta para la interfaz")
        }
    }

    @Test
    fun cada_alimento_del_catalogo_se_encuentra_por_sus_propias_palabras() {
        // Protege de añadir una categoría con palabras clave que una exclusión global anula, dejándola
        // inalcanzable sin que nada avise.
        for (yield in COOKING_YIELDS) {
            val encontrado = findCookingYield(yield.keywords.first())
            assertNotNull(encontrado, "${yield.id}: su primera palabra clave no encuentra nada")
        }
    }
}
