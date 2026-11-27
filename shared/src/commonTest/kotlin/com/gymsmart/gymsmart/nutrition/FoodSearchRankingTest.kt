package com.gymsmart.gymsmart.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FoodSearchRankingTest {

    /** Los resultados reales que devolvía Open Food Facts al buscar "arroz", en su orden original. */
    private val resultadosRealesDeArroz = listOf(
        "Tortitas de arroz y legumbres",
        "Copos de trigo integral y arroz 0% azúcares añadidos",
        "Cereales rellenos de leche",
        "Barritas de trigo y arroz integral con chocolate con leche",
        "Galletas de arroz inflado rellenas de chocolate y avellana",
        "Tortitas de arroz",
        "Bebida de Coco & Arroz",
        "Arroz largo",
        "Arroz Largo Hacendado"
    )

    private fun ordenar(query: String, nombres: List<String>): List<String> =
        rankFoodResults(
            query = query,
            generics = matchingGenerics(query),
            branded = nombres.map { it to it },
            toGeneric = { it.name }
        )

    @Test
    fun buscando_arroz_el_arroz_va_antes_que_las_tortitas_de_arroz() {
        val orden = ordenar("arroz", resultadosRealesDeArroz)
        val arroz = orden.indexOf("Arroz blanco")      // genérico del catálogo
        val tortitas = orden.indexOf("Tortitas de arroz")
        assertTrue(arroz >= 0, "el genérico de arroz debería aparecer")
        assertTrue(tortitas >= 0, "las tortitas no se descartan, solo bajan")
        assertTrue(arroz < tortitas, "el arroz tiene que ir por delante de las tortitas de arroz")
    }

    @Test
    fun el_generico_abre_la_lista() {
        val orden = ordenar("arroz", resultadosRealesDeArroz)
        // El catálogo no tiene "Arroz" a secas sino "Arroz blanco", que es lo que debe encabezar.
        assertEquals("Arroz blanco", orden.first(), "lo primero al buscar 'arroz' debe ser el genérico")
    }

    @Test
    fun los_productos_compuestos_quedan_por_detras() {
        val orden = ordenar("arroz", resultadosRealesDeArroz)
        val ultimos = orden.takeLast(4)
        assertTrue(
            ultimos.any { it.contains("Bebida") } || ultimos.any { it.contains("Barritas") },
            "los derivados deberían caer al final, y quedaron: $ultimos"
        )
    }

    @Test
    fun lo_que_no_tiene_nada_que_ver_se_descarta() {
        // "Cereales rellenos de leche" no contiene "arroz": no debería colarse en la lista.
        assertTrue("Cereales rellenos de leche" !in ordenar("arroz", resultadosRealesDeArroz))
    }

    @Test
    fun buscar_una_marca_concreta_la_sigue_encontrando_primero() {
        // El empujón del genérico no puede secuestrar una búsqueda por marca.
        val orden = ordenar("hacendado", listOf("Arroz Largo Hacendado", "Leche Hacendado"))
        assertTrue(orden.isNotEmpty())
        assertTrue(orden.first().contains("Hacendado"))
    }

    @Test
    fun el_nombre_exacto_gana_al_que_solo_empieza_igual() {
        assertTrue(searchRelevance("Arroz", "arroz") > searchRelevance("Arroz blanco", "arroz"))
        assertTrue(searchRelevance("Arroz blanco", "arroz") > searchRelevance("Tortitas de arroz", "arroz"))
    }

    @Test
    fun ignora_tildes_y_mayusculas() {
        assertTrue(searchRelevance("PLÁTANO", "platano") > 0)
        assertTrue(searchRelevance("Platano", "plátano") > 0)
    }

    @Test
    fun una_busqueda_de_varias_palabras_encuentra_el_alimento() {
        val orden = ordenar("pechuga de pollo", listOf("Nuggets de pollo", "Pechuga de pollo Hacendado"))
        assertEquals("Pechuga de pollo", orden.first())
    }

    @Test
    fun no_puntua_lo_que_no_coincide() {
        assertEquals(0, searchRelevance("Yogur natural", "arroz"))
        assertEquals(0, searchRelevance(null, "arroz"))
        assertEquals(0, searchRelevance("Arroz", ""))
    }

    @Test
    fun la_tabla_de_genericos_esta_bien_formada() {
        assertTrue(GENERIC_FOODS.size >= 80, "se esperaban al menos 80 genéricos")
        assertEquals(
            GENERIC_FOODS.size,
            GENERIC_FOODS.map { it.name }.distinct().size,
            "hay nombres repetidos en la tabla de genéricos"
        )
        for (f in GENERIC_FOODS) {
            assertTrue(f.name.isNotBlank(), "alimento sin nombre")
            assertTrue(f.kcalPer100 > 0.0, "${f.name}: sin kcal no se puede registrar")
            assertTrue(f.fdcId.isNotBlank(), "${f.name}: sin identificador no se puede verificar")
            // Las kcal tienen que cuadrar con los macros por la regla 4/4/9, con holgura amplia
            // porque fibra, alcohol y redondeos la desvían. Detecta una fila mal copiada.
            val estimadas = f.proteinsPer100 * 4 + f.carbsPer100 * 4 + f.fatPer100 * 9
            assertTrue(
                estimadas <= f.kcalPer100 * 1.45 + 60 && estimadas >= f.kcalPer100 * 0.55 - 60,
                "${f.name}: ${f.kcalPer100} kcal no cuadra con sus macros (${estimadas.toInt()})"
            )
        }
    }

    @Test
    fun los_genericos_clave_enlazan_con_su_factor_de_cocinado() {
        // Un genérico vale doble: su base es conocida, así que la conversión crudo/cocinado es fiable.
        for (nombre in listOf("Pechuga de pollo", "Arroz blanco", "Pasta", "Lentejas", "Merluza (pescadilla)")) {
            val g = GENERIC_FOODS.firstOrNull { it.name == nombre }
            assertTrue(g != null, "falta el genérico $nombre")
            assertTrue(
                findCookingYield(nombre) != null,
                "$nombre debería enlazar con un factor de cocinado"
            )
        }
    }

    // ── Singular y plural ────────────────────────────────────────────────────
    //
    // Los seis casos que la auditoría del 2026-11-27 (doc/13 §2.1, N-3) midió como roídos: la
    // comparación era por palabra completa, así que "huevo" no estaba contenido en "huevos" y el
    // genérico desaparecía de los resultados. Ninguno de los 11 tests anteriores cubría un plural.

    @Test
    fun buscar_en_plural_encuentra_el_generico_en_singular() {
        for ((consulta, nombre) in listOf(
            "huevos" to "Huevo entero",
            "platanos" to "Platano",
            "tomates" to "Tomate",
            "yogures" to "Yogur natural",
            "lentejas" to "Lentejas",
            "almendras" to "Almendras"
        )) {
            assertTrue(
                searchRelevance(nombre, consulta) > 0,
                "buscando '$consulta' debería encontrarse '$nombre'"
            )
        }
    }

    @Test
    fun buscar_en_singular_encuentra_el_generico_en_plural() {
        for ((consulta, nombre) in listOf(
            "lenteja" to "Lentejas",
            "garbanzo" to "Garbanzos",
            "espinaca" to "Espinacas",
            "almendra" to "Almendras",
            "nuez" to "Nueces",
            "fresa" to "Fresas"
        )) {
            assertTrue(
                searchRelevance(nombre, consulta) > 0,
                "buscando '$consulta' debería encontrarse '$nombre'"
            )
        }
    }

    @Test
    fun una_consulta_de_varias_palabras_en_plural_no_devuelve_lista_vacia() {
        // El peor caso del informe: devolvía 0 y rankFoodResults lo descartaba entero.
        assertTrue(searchRelevance("Pechuga de pollo", "pechugas de pollo") > 0)
        assertTrue(matchingGenerics("pechugas de pollo").isNotEmpty())
        assertTrue(matchingGenerics("huevos").isNotEmpty())
        assertTrue(matchingGenerics("nuez").isNotEmpty())
    }

    @Test
    fun el_generico_en_plural_gana_a_un_producto_que_solo_menciona_la_palabra() {
        // El caso concreto del informe: buscando "huevo", "Huevos" puntuaba 44 y se quedaba por
        // detrás de "Lacasitos huevo" (385). Con el plegado, el alimento de verdad va delante.
        val generico = searchRelevance("Huevo entero", "huevos") + 250  // + GENERIC_BOOST
        val ruido = searchRelevance("Lacasitos huevo", "huevos")
        assertTrue(generico > ruido, "el genérico ($generico) debería ganar al producto compuesto ($ruido)")
    }

    @Test
    fun una_coincidencia_literal_gana_siempre_a_una_plegada() {
        // El plegado no puede desordenar lo que ya funcionaba: los escalones del plural van justo por
        // debajo de su equivalente exacto.
        assertTrue(searchRelevance("Huevo entero", "huevo") > searchRelevance("Huevo entero", "huevos"))
        assertTrue(searchRelevance("Tomate", "tomate") > searchRelevance("Tomate", "tomates"))
    }

    @Test
    fun no_se_plegan_palabras_cortas_donde_la_s_es_de_la_raiz() {
        assertEquals(setOf("gas"), pluralStems("gas"))
        assertEquals(setOf("mes"), pluralStems("mes"))
        assertEquals(setOf("arroz"), pluralStems("arroz"))
    }

    @Test
    fun el_plegado_cubre_las_tres_formas_del_plural_espanol() {
        assertTrue("platano" in pluralStems("platanos"))   // + s
        assertTrue("tomate" in pluralStems("tomates"))     // + s sobre palabra en -e
        assertTrue("yogur" in pluralStems("yogures"))      // + es
        assertTrue("nuez" in pluralStems("nueces"))        // z → ces
        assertTrue("pez" in pluralStems("peces"))
    }
}
