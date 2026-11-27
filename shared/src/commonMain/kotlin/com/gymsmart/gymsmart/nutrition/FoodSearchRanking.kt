package com.gymsmart.gymsmart.nutrition

/**
 * Ordena los resultados de una búsqueda de alimentos por lo mucho que se parecen a lo que se escribió.
 *
 * **El problema.** Open Food Facts devuelve sus resultados en un orden que no tiene en cuenta la
 * intención: buscando "arroz" salían primero "Tortitas de arroz y legumbres", "Bebida de coco & arroz"
 * y "Galletas de arroz inflado con chocolate", y el arroz a secas no aparecía. Todos contienen la
 * palabra, pero ninguno es lo que se buscaba.
 *
 * **El criterio.** Cuanto más cerca esté el nombre del producto de ser *exactamente* lo buscado, más
 * arriba. Un nombre que es la consulta tal cual gana a uno que empieza por ella, que gana a uno que la
 * lleva suelta en medio, que gana a uno donde solo aparece dentro de otra palabra. A igualdad de
 * puntuación manda el nombre más corto, porque los nombres largos son los productos compuestos
 * ("tortitas de arroz y legumbres" frente a "arroz").
 */

/**
 * Formas con las que una palabra puede casar con su singular o su plural.
 *
 * **Por qué hace falta.** La comparación es por palabra completa, y en español eso rompía la búsqueda:
 * `"huevo"` no está contenido en `"huevos"`, así que buscar **"huevos"** —la forma natural en que
 * alguien escribe esa búsqueda— no encontraba el genérico `"Huevo entero"` y devolvía 0, lo que hacía
 * que `rankFoodResults` lo descartara por completo. Lo mismo con "platanos", "tomates", "yogures" o
 * "nueces", y `"pechugas de pollo"` devolvía **lista vacía** aunque Open Food Facts hubiera respondido.
 *
 * **Por qué un conjunto y no una forma canónica.** No hay una sola regla que sirva: quitar la "s"
 * resuelve "tomates"→"tomate" pero no "yogures"→"yogur", y quitar "es" resuelve el segundo pero
 * convierte "tomates" en "tomat". Así que se generan las variantes plausibles y dos palabras casan si
 * comparten alguna. No es un lematizador, y no pretende serlo: cubre la formación regular del plural
 * español, que es lo que se teclea al buscar comida.
 *
 * El mínimo de longitud evita plegar palabras cortas donde la "s" es parte de la raíz ("gas", "mes").
 */
internal fun pluralStems(word: String): Set<String> {
    if (word.length < 4 || !word.endsWith("s")) return setOf(word)
    val stems = mutableSetOf(word, word.dropLast(1))          // platanos → platano, tomates → tomate
    if (word.length >= 5 && word.endsWith("es")) {
        stems.add(word.dropLast(2))                           // yogures → yogur, flores → flor
        if (word.endsWith("ces")) stems.add(word.dropLast(3) + "z")  // nueces → nuez, peces → pez
    }
    return stems
}

/** true si las dos palabras son la misma salvo por el número gramatical. */
private fun mismaPalabra(a: String, b: String): Boolean =
    a == b || pluralStems(a).any { it in pluralStems(b) }

/** Puntuación de relevancia de [name] para la consulta [query]. Más alto es mejor. */
fun searchRelevance(name: String?, query: String): Int {
    val n = normalizeFoodName(name ?: return 0).trim()
    val q = normalizeFoodName(query).trim()
    if (n.isEmpty() || q.isEmpty()) return 0

    val terms = q.split(' ').filter { it.isNotEmpty() }
    val nameWords = n.split(' ').filter { it.isNotEmpty() }
    val padded = " $n "

    var score = when {
        n == q -> 1000                                   // "arroz" buscando "arroz"
        n.startsWith("$q ") -> 700                       // "arroz blanco"
        padded.contains(" $q ") -> 400                   // "tortitas de arroz y legumbres"
        else -> 0
    }

    // Todas las palabras de la consulta presentes como palabra completa, aunque estén sueltas.
    if (score == 0 && terms.all { padded.contains(" $it ") }) score = 200

    // Los mismos tres escalones, pero tolerando singular/plural. Van justo por debajo de su
    // equivalente exacto, así que una coincidencia literal siempre gana a una plegada: buscando
    // "huevo" sigue saliendo primero "Huevo" y no "Huevos rotos".
    if (score == 0) {
        val igual = nameWords.size == terms.size &&
            nameWords.zip(terms).all { (a, b) -> mismaPalabra(a, b) }
        val empiezaPor = nameWords.size > terms.size &&
            nameWords.take(terms.size).zip(terms).all { (a, b) -> mismaPalabra(a, b) }
        val todasPresentes = terms.all { t -> nameWords.any { mismaPalabra(it, t) } }
        score = when {
            igual -> 900          // "huevos" encontrando "Huevo"
            empiezaPor -> 650     // "huevos" encontrando "Huevo entero"
            todasPresentes -> 150 // "pechugas de pollo" encontrando "Pechuga de pollo fileteada"
            else -> 0
        }
    }

    // Último recurso: aparece dentro de otra palabra ("arrozal"). Vale muy poco, pero no es cero.
    if (score == 0 && terms.all { n.contains(it) }) score = 50
    if (score == 0) return 0

    // Penaliza los nombres largos: a igual encaje, el compuesto es menos probable que sea lo buscado.
    // Se recorta para que la longitud nunca pueda saltar un escalón de los de arriba.
    return score - minOf(n.length, 150)
}

/**
 * Mezcla los alimentos genéricos con los productos de marca y lo deja todo ordenado por relevancia.
 *
 * Los genéricos entran con un empujón fijo, no colocados a la fuerza en cabeza: así "Arroz" sale por
 * encima de "Arroz largo Hacendado" al buscar "arroz", pero buscar "hacendado" sigue devolviendo lo
 * que toca. Es el comportamiento de Fitia, que presenta "Genérico" como una marca más y lo prioriza.
 *
 * @param branded pares (nombre, dato) de los productos de marca, en el orden en que llegaron.
 * @return los índices/datos ya ordenados, genéricos y marcas entremezclados por relevancia.
 */
fun <T> rankFoodResults(
    query: String,
    generics: List<GenericFood>,
    branded: List<Pair<String?, T>>,
    toGeneric: (GenericFood) -> T
): List<T> {
    val scored = ArrayList<Triple<Int, Int, T>>(generics.size + branded.size)

    for (g in generics) {
        val s = searchRelevance(g.name, query)
        if (s > 0) scored.add(Triple(s + GENERIC_BOOST, g.name.length, toGeneric(g)))
    }
    for ((name, item) in branded) {
        val s = searchRelevance(name, query)
        if (s > 0) scored.add(Triple(s, name?.length ?: 999, item))
    }

    return scored
        .sortedWith(compareByDescending<Triple<Int, Int, T>> { it.first }.thenBy { it.second })
        .map { it.third }
}

/**
 * Lo que se le suma a un genérico sobre un producto de marca con el mismo encaje.
 *
 * 250 es deliberado: supera la diferencia entre "lleva la palabra suelta" (400) y "empieza por ella"
 * (700), así que un genérico que contiene el término adelanta a una marca que solo lo contiene, pero
 * **no** adelanta a una marca que coincide exactamente con lo que se escribió. Buscar el nombre
 * concreto de un producto sigue encontrándolo primero.
 */
private const val GENERIC_BOOST = 250

/** Los genéricos que encajan con la consulta, ya filtrados. */
fun matchingGenerics(query: String): List<GenericFood> =
    GENERIC_FOODS.filter { searchRelevance(it.name, query) > 0 }
