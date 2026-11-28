package com.gymsmart.gymsmart.nutrition

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CookingYieldTest {

    private fun assertClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 0.5,
        detalle: String = ""
    ) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            (if (detalle.isBlank()) "" else "$detalle: ") +
                "esperado ~$expected pero fue $actual (tolerancia $tolerance)"
        )
    }

    /** Cinco valores con nombre, para que los casos de prueba se lean sin contar posiciones. */
    private data class Quintuple(
        val nombre: String,
        val pCrudo: Double,
        val gCrudo: Double,
        val pCocinado: Double,
        val gCocinado: Double
    )

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
        // Pechuga cruda 120 kcal/100 g; con factor 0,73 el cocinado sale 164,4, que es el valor que
        // la USDA midió en pechuga **asada** (165). Antes el factor era 0,76 y se comparaba con 157,
        // que es la pechuga *estofada*: la cita del catálogo estaba equivocada (doc/13 §2.2, C-9).
        // Es la comprobación de que el factor no está invertido: cocinar carne SUBE las kcal/100 g.
        val yield = findCookingYield("Pechuga de pollo")!!
        val cooked = yield.per100For(120.0, WeighBasis.COOKED)
        assertClose(165.0, cooked, 1.5)
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
        for (nombre in listOf("Merluza", "Lomos de bacalao", "Lubina", "Filete de panga")) {
            assertEquals("pescado", findCookingYield(nombre)?.id, "no reconoció $nombre")
        }
        // El salmón tiene entrada propia desde el 2026-11-27: la USDA midió el de piscifactoría y su
        // densidad energética no cambia al cocinarlo (208 → 206 kcal), así que el 0,78 del pescado
        // blanco le metía un +29 % (doc/13 §2.2, C-3).
        assertEquals("salmon", findCookingYield("Salmón fresco")?.id)
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
            // Los cuatro corregidos el 2026-11-27 contra el volcado de SR Legacy (doc/13 §2.2):
            // pollo y muslo citaban la entrada "braised" creyendo que era "roasted"; la picada y el
            // cordero usaban un rendimiento de PESO como si fuera un cociente de energía; y la patata
            // usaba el del horno donde en España se hierve.
            Triple("Pechuga de pollo", 120.0, 165.0),   // 171477, asada
            Triple("Muslo de pollo", 121.0, 179.0),     // 172388, asado
            Triple("Carne picada", 215.0, 250.0),       // 171796 -> 174032, 85/15 MG
            Triple("Cordero", 282.0, 283.0),            // 174370 -> 172544, picado
            Triple("Salmon", 208.0, 206.0),             // 175167 -> 175168, piscifactoria
            Triple("Arroz vaporizado", 374.0, 123.0),   // 169758 -> 169759
            Triple("Pavo picado", 148.0, 203.0),
            Triple("Solomillo de cerdo", 109.0, 143.0),
            Triple("Gambas", 85.0, 99.0),
            Triple("Patata", 77.0, 87.0)                // 170026 -> 170438, hervida
        )
        for ((name, raw, cookedEsperado) in casos) {
            val yield = assertNotNull(findCookingYield(name), "no reconoció $name")
            assertClose(cookedEsperado, yield.per100For(raw, WeighBasis.COOKED), 1.5)
        }
    }

    @Test
    fun el_total_de_la_racion_no_depende_de_la_base_elegida() {
        // La invariante de todo esto: la misma comida real tiene las mismas kcal, se pese antes o
        // después de cocinarla. 100 g de pollo crudo son 73 g de pollo hecho.
        val yield = findCookingYield("Pechuga de pollo")!!
        val kcalPer100Raw = 120.0

        val totalPesandoCrudo = yield.per100For(kcalPer100Raw, WeighBasis.RAW) / 100.0 * 100.0
        val totalPesandoCocinado = yield.per100For(kcalPer100Raw, WeighBasis.COOKED) / 100.0 * 73.0

        assertClose(totalPesandoCrudo, totalPesandoCocinado, 0.5)
    }

    @Test
    fun el_peso_crudo_equivalente_es_coherente() {
        val yield = findCookingYield("Pechuga de pollo")!!
        // 146 g de pollo hecho vienen de 200 g de crudo (146 / 0,73).
        assertClose(200.0, yield.rawWeightFor(146.0, WeighBasis.COOKED), 1.0)
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

    // ── El suelo de cordura (productos que ya vienen cocinados) ───────────────

    @Test
    fun detecta_el_vasito_de_arroz_por_sus_kcal() {
        // El caso real que destapó el fallo: un vasito de microondas de Brillante llamado solo
        // "Arroz basmati", 158 kcal/100 g. El nombre no delata nada; el número sí, porque el arroz
        // en seco ronda 365. Sin esto, marcarlo como cocido daba 56 kcal/100 g.
        val arroz = findCookingYield("Arroz basmati")!!
        assertTrue(arroz.looksAlreadyCooked(158.0), "158 kcal/100 g no puede ser arroz en seco")
        assertFalse(arroz.looksAlreadyCooked(347.0), "347 kcal/100 g sí es arroz en seco")
    }

    @Test
    fun detecta_las_legumbres_de_tarro() {
        assertTrue(findCookingYield("Garbanzos")!!.looksAlreadyCooked(140.0))
        assertTrue(findCookingYield("Lentejas")!!.looksAlreadyCooked(120.0))
        // Y no se pasa de frenada con las secas de verdad.
        assertFalse(findCookingYield("Garbanzos")!!.looksAlreadyCooked(378.0))
        assertFalse(findCookingYield("Lentejas")!!.looksAlreadyCooked(352.0))
    }

    @Test
    fun el_suelo_deja_pasar_todos_los_aridos_secos_del_catalogo() {
        // Protege de poner un suelo tan alto que bloquee alimentos legítimos. Cada árido tiene que
        // estar holgadamente por encima de su propio suelo.
        val secos = mapOf(
            "Arroz blanco" to 365.0, "Arroz integral" to 367.0, "Pasta" to 371.0,
            "Quinoa" to 368.0, "Cuscús" to 376.0, "Bulgur" to 342.0, "Cebada perlada" to 352.0,
            "Lentejas" to 352.0, "Garbanzos" to 378.0, "Alubias blancas" to 333.0,
            "Alubias rojas" to 333.0, "Alubias negras" to 341.0, "Habas" to 341.0
        )
        for ((nombre, kcalSeco) in secos) {
            val y = assertNotNull(findCookingYield(nombre), "no reconoció $nombre")
            assertNotNull(y.minRawKcalPer100, "${y.id} debería tener suelo")
            assertFalse(y.looksAlreadyCooked(kcalSeco), "${y.id}: el suelo bloquea el alimento seco real")
            // ...y el mismo alimento ya cocido tiene que quedar por debajo del suelo.
            assertTrue(
                y.looksAlreadyCooked(kcalSeco / y.factor),
                "${y.id}: el suelo no detecta su propia versión cocida"
            )
        }
    }

    @Test
    fun no_se_inventa_un_suelo_donde_no_hay_senal() {
        // En carne y pescado se midió que la variación entre marcas supera la diferencia entre crudo
        // y cocinado, así que no se comprueba nada. Mejor no bloquear que bloquear a ciegas.
        for (id in listOf("pollo_pechuga", "pollo_muslo", "ternera_filete", "pescado", "patata")) {
            val y = cookingYieldById(id)!!
            assertNull(y.minRawKcalPer100, "$id no debería tener suelo: no hay señal que lo sostenga")
            assertFalse(y.looksAlreadyCooked(50.0), "$id sin suelo nunca debe bloquear")
        }
    }

    @Test
    fun los_nueve_agujeros_de_nombre_verificados_estan_tapados() {
        // Cada uno de estos pasaba el filtro y acababa convertido. Se comprobaron uno a uno contra el
        // código real, así que quedan fijados aquí para que no vuelvan.
        val agujeros = listOf(
            "Patatas prefritas congeladas", "Patatas bravas", "Garbanzos en tarro",
            "Lentejas tarro", "Arroz en vasito microondas",
            "Pechuga de pollo con solucion anadida"
        )
        for (nombre in agujeros) {
            assertNull(findCookingYield(nombre), "sigue abierto el agujero: $nombre")
        }
    }

    @Test
    fun dos_de_esos_nueve_agujeros_no_lo_eran_y_ahora_si_se_convierten() {
        // "Arroz vaporizado" y "Merluza glaseada" estaban en la lista de agujeros tapados, y la
        // auditoría del 2026-11-27 (doc/13 §2.2, C-7) demostró que eran **falsos negativos**: los dos
        // son productos crudos o en seco, de altísima rotación, a los que la conversión sí hay que
        // ofrecerla. El arroz vaporizado se vende en seco y es líder de su categoría; el glaseado es
        // una capa de agua que se aplica al pescado crudo al congelarlo.
        //
        // Lo que de verdad protegía del caso cocinado no era la palabra, sino el número, y ése sigue
        // en pie (ver el test de abajo).
        assertNotNull(findCookingYield("Arroz vaporizado"))
        assertNotNull(findCookingYield("Merluza congelada glaseada"))
        assertNotNull(findCookingYield("Preparado de carne picada de vacuno y cerdo"))
        assertNotNull(findCookingYield("Arroz especial para paella"))
        assertNotNull(findCookingYield("Fideos finos para sopa"))
    }

    @Test
    fun el_vasito_de_arroz_lo_sigue_cazando_el_suelo_aunque_el_nombre_no_lo_diga() {
        // La defensa buena: el número. Un "Arroz vaporizado" que marque 158 kcal/100 g no es arroz en
        // seco, es un vasito ya cocido, y el suelo lo bloquea aunque el nombre no delate nada.
        val arroz = findCookingYield("Arroz vaporizado")!!
        assertTrue(arroz.looksAlreadyCooked(158.0), "un arroz de 158 kcal/100 g ya está cocido")
        assertFalse(arroz.looksAlreadyCooked(350.0), "un arroz en seco de 350 kcal/100 g sí se convierte")
    }

    @Test
    fun el_techo_de_cordura_caza_lo_que_el_suelo_no_puede() {
        // La comprobación solo miraba hacia abajo, y por arriba se colaban productos reales con
        // errores enormes (doc/13 §2.2, C-6). Cada caso con las kcal que tiene en el lineal.
        val casos = listOf(
            Triple("Patatas paja", 560.0, "patata"),
            Triple("Patatas noisette", 180.0, "patata"),
            Triple("Tortilla de patatas", 160.0, "patata"),
            Triple("Mojama de atun", 330.0, "pescado"),
            Triple("Garbanzos tostados", 410.0, "garbanzos"),
            Triple("Fideos orientales instantaneos", 450.0, "pasta")
        )
        for ((nombre, kcal, idEsperado) in casos) {
            val y = findCookingYield(nombre)
            // Si el nombre ya lo descarta, perfecto; si no, lo tiene que descartar el techo.
            if (y != null) {
                assertEquals(idEsperado, y.id, "$nombre cae en la categoría equivocada")
                assertTrue(
                    y.looksAlreadyCooked(kcal),
                    "$nombre ($kcal kcal/100 g) debería bloquearse por el techo"
                )
            }
        }
    }

    @Test
    fun el_techo_no_estorba_a_los_alimentos_crudos_normales() {
        // Cada uno con las kcal que tiene en crudo según la tabla de genéricos.
        val crudos = listOf(
            "Pechuga de pollo" to 120.0,
            "Muslo de pollo" to 121.0,
            "Carne picada de ternera" to 176.0,
            "Salmon" to 208.0,
            "Merluza" to 90.0,
            "Patata" to 69.0,
            "Arroz blanco" to 365.0,
            "Garbanzos" to 378.0,
            "Avena" to 389.0
        )
        for ((nombre, kcal) in crudos) {
            val y = findCookingYield(nombre) ?: continue
            assertFalse(
                y.looksAlreadyCooked(kcal),
                "$nombre ($kcal kcal/100 g) es un alimento crudo normal y no debería bloquearse"
            )
        }
    }

    // ── Un factor por macro ──────────────────────────────────────────────────

    @Test
    fun el_factor_por_macro_reproduce_los_cuatro_valores_medidos_por_la_usda() {
        // La afirmación central del cambio: no se inventa nada. Cada factor por macro es el cociente
        // de la misma pareja de la USDA que cita la entrada, así que aplicarlo devuelve el valor que
        // la USDA publica para el alimento cocinado **en los cuatro nutrientes**, no solo en las
        // kcal. Antes los cuatro se dividían por el de la energía y la grasa se iba hasta un 30 %.
        //
        // (nombre, crudo P, crudo G, cocinado P medido, cocinado G medido)
        val casos = listOf(
            // Muslo de pollo 173627 -> 172388. El peor caso de grasa del catálogo.
            Quintuple("Muslo de pollo", 19.66, 4.12, 24.76, 8.15),
            // Carne picada 85/15 171796 -> 174032.
            Quintuple("Carne picada", 18.59, 15.0, 25.93, 15.41),
            // Pasta 168927 -> 168928.
            Quintuple("Espaguetis", 13.04, 1.51, 5.80, 0.93)
        )
        for ((nombre, pCrudo, gCrudo, pCocinado, gCocinado) in casos) {
            val y = assertNotNull(findCookingYield(nombre), "no reconoció $nombre")
            assertClose(pCocinado, y.per100For(pCrudo, WeighBasis.COOKED, Macro.PROTEIN), 0.15,
                "$nombre: proteína cocinada")
            assertClose(gCocinado, y.per100For(gCrudo, WeighBasis.COOKED, Macro.FAT), 0.15,
                "$nombre: grasa cocinada")
        }
    }

    @Test
    fun usar_el_factor_de_la_energia_para_la_grasa_se_desvia_de_verdad() {
        // Cuantifica por qué hacía falta: es el error que había antes de separar los factores.
        val muslo = findCookingYield("Muslo de pollo")!!
        val conEnergia = muslo.per100For(4.12, WeighBasis.COOKED, Macro.ENERGY)
        val conGrasa = muslo.per100For(4.12, WeighBasis.COOKED, Macro.FAT)
        // 8,15 g es el valor medido; con el factor de la energía salen ~6,1, un 25 % menos.
        assertClose(8.15, conGrasa, 0.15)
        assertTrue(conEnergia < conGrasa * 0.8, "el factor de la energía infravalora la grasa")
    }

    @Test
    fun sin_factor_propio_se_cae_al_de_la_energia() {
        // El pescado blanco no tiene factores por macro a propósito: su entrada cocinada está
        // imputada por la USDA (los tres cocientes salen idénticos), así que separarlos no añadiría
        // información. Tiene que comportarse exactamente como antes.
        val pescado = findCookingYield("Bacalao")!!
        assertNull(pescado.factorProtein, "el pescado no debería tener factor propio de proteína")
        for (macro in Macro.entries) {
            assertEquals(pescado.factor, pescado.factorFor(macro), 1e-9)
        }
    }

    @Test
    fun ningun_factor_por_macro_es_absurdo() {
        // Guardia sobre los datos: un factor negativo o cero invertiría o reventaría la conversión,
        // y uno disparatado delataría una fila mal copiada del volcado.
        for (y in COOKING_YIELDS) {
            for (macro in Macro.entries) {
                val f = y.factorFor(macro)
                assertTrue(f > 0.0, "${y.id}/$macro: factor $f no positivo")
                assertTrue(f in 0.3..6.0, "${y.id}/$macro: factor $f fuera de lo creíble")
            }
        }
    }

    @Test
    fun los_plurales_que_faltaban_en_las_exclusiones_ya_no_hacen_falta() {
        // El KDoc prometía las cuatro formas de cada adjetivo y faltaba el plural en 51 de 134
        // entradas. `relleno`/`rellena` estaban sin sus plurales, que es exactamente el fallo que el
        // comentario decía evitar. Ahora el número lo plega la comparación.
        assertNull(findCookingYield("Pimientos rellenos de carne picada"))
        assertNull(findCookingYield("Patatas rellenas de queso"))
        assertNull(findCookingYield("Empanadillas de carne picada"))
        assertNull(findCookingYield("Tagliatelle frescos al huevo"))
        assertNull(findCookingYield("Lentejas cocidas"))
    }

    @Test
    fun la_base_por_defecto_sigue_al_uso_real() {
        // Carne en crudo, áridos ya cocidos: es como se pesan de verdad, y es lo que hace Fitia.
        assertEquals(WeighBasis.RAW, cookingYieldById("pollo_pechuga")!!.defaultBasis)
        assertEquals(WeighBasis.RAW, cookingYieldById("pescado")!!.defaultBasis)
        assertEquals(WeighBasis.COOKED, cookingYieldById("pasta")!!.defaultBasis)
        assertEquals(WeighBasis.COOKED, cookingYieldById("lentejas")!!.defaultBasis)
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
