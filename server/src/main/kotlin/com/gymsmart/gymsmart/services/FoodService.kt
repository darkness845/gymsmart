package com.gymsmart.gymsmart.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Caché en memoria con TTL para respuestas de Open Food Facts (OFF-1 en doc/08-backlog-mejoras.md).
 * Todas las peticiones salen de la única IP de Render, compartida por todos los usuarios, y OFF limita
 * por IP, así que una búsqueda repetida no debería generar una petición nueva cada vez. Los datos de
 * un producto apenas cambian día a día, así que un TTL largo no sacrifica nada real.
 *
 * No es un LRU de verdad (innecesario a esta escala), pero **sí descarta por antigüedad en vez de
 * vaciarse**: antes, al llegar al límite, hacía `clear()` y tiraba las 500 entradas válidas de golpe,
 * de modo que 500 búsquedas distintas —algo trivial de provocar— desactivaban la caché entera justo
 * en el escenario en que importa. Ahora, si purgar las caducadas no basta, se eliminan las que
 * caducan antes (las más viejas) hasta liberar un cuarto del espacio.
 */
private class TtlCache<K, V>(private val ttlMillis: Long, private val maxEntries: Int) {
    private data class Entry<V>(val value: V, val expiresAt: Long)
    private val map = ConcurrentHashMap<K, Entry<V>>()

    fun get(key: K): V? {
        val entry = map[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            map.remove(key, entry)
            return null
        }
        return entry.value
    }

    fun put(key: K, value: V) {
        if (map.size >= maxEntries) {
            val now = System.currentTimeMillis()
            map.entries.removeAll { it.value.expiresAt < now }
            if (map.size >= maxEntries) {
                // Las que menos vida les queda son las más antiguas: fuera un cuarto del total.
                val sobran = map.size - (maxEntries * 3 / 4)
                map.entries.sortedBy { it.value.expiresAt }.take(sobran).forEach { map.remove(it.key, it.value) }
            }
        }
        map[key] = Entry(value, System.currentTimeMillis() + ttlMillis)
    }
}

// ── Modelos Open Food Facts ────────────────────────────────────────────────────

/**
 * [fiber_100g], [alcohol_100g] y [polyols_100g] existen porque el Anexo XIV del Reglamento (UE)
 * 1169/2011 les asigna factores propios (2, 7 y 2,4 kcal/g) que la regla 4/4/9 no tiene. Sin ellos,
 * estimar la energía de un pan integral se queda corto en 2 kcal por gramo de fibra, y la de una
 * cerveza se queda corta en 7 por gramo de etanol.
 *
 * Nota sobre `carbohydrates_100g`: en etiqueta europea **no** incluye la fibra (verificado contra
 * tres productos reales: Wasa Fibres 333 kcal = 4P+4C+9G + 2×fibra, exacto), pero en los datos de la
 * USDA **sí** la incluye. Por eso la fibra se suma aparte solo cuando se estima desde macros de OFF.
 */
@Serializable
data class Nutriments(
    @SerialName("energy-kcal_100g") val energy_kcal_100g: Double? = null,
    @SerialName("energy-kcal")      val energy_kcal: Double? = null,
    val proteins_100g: Double? = null,
    val carbohydrates_100g: Double? = null,
    val fat_100g: Double? = null,
    val fiber_100g: Double? = null,
    val alcohol_100g: Double? = null,
    val polyols_100g: Double? = null
)

/**
 * [nutritionDataPer] es la base que declara el producto ("100g" o "100ml"). Importa porque el 11,8 %
 * de los productos españoles está en base **100 ml** (toda la leche, los caldos), y la app pedía los
 * gramos y rotulaba "por X g" para todos. [servingQuantity] son los gramos de una ración ya
 * calculados por OFF, que trae el 57 % de los productos: es lo que permite ofrecer "1 ración (90 g)"
 * sin ninguna fuente de datos nueva.
 */
@Serializable
data class Product(
    val product_name: String? = null,
    val nutriments: Nutriments? = null,
    val image_url: String? = null,
    val brands: String? = null,
    @SerialName("nutrition_data_per") val nutritionDataPer: String? = null,
    @SerialName("serving_size")       val servingSize: String? = null,
    @SerialName("serving_quantity")   val servingQuantity: Double? = null
)

/**
 * [upstreamFailed] distingue "Open Food Facts no tiene este alimento" de "no he podido preguntarle".
 * Sin esta bandera la app mostraba *"Sin resultados con datos nutricionales"* cuando la búsqueda
 * estaba caída, que es un mensaje falso: el usuario concluía que el alimento no existe y lo daba de
 * alta a mano o lo dejaba sin registrar.
 */
@Serializable
data class FoodSearchResponse(
    val products: List<Product> = emptyList(),
    val count: Int = 0,
    val upstreamFailed: Boolean = false
)

// Respuesta de búsqueda por código de barras (API v2 de producto)
@Serializable
data class BarcodeResponse(
    val status: Int = 0,          // 1 = encontrado, 0 = no encontrado
    val product: Product? = null
)

// ── Modelos de Search-a-licious ───────────────────────────────────────────────

/**
 * Un resultado del índice de búsqueda de OFF. Ojo con dos diferencias frente a la API v1, que son
 * las que obligan a tener un modelo aparte en vez de reutilizar [Product]:
 * - `brands` es una **lista**, no una cadena.
 * - los nombres de campo no llevan guiones, así que no hacen falta `@SerialName` salvo en nutriments.
 */
@Serializable
data class SearchHit(
    val code: String? = null,
    val product_name: String? = null,
    val product_name_es: String? = null,
    val brands: List<String>? = null,
    val nutriments: Nutriments? = null,
    val nutrition_data_per: String? = null,
    val serving_size: String? = null,
    val serving_quantity: Double? = null,
    val completeness: Double? = null,
    val unique_scans_n: Int? = null,
    val data_quality_errors_tags: List<String>? = null,
    val image_url: String? = null
)

@Serializable
data class SearchALiciousResponse(
    val hits: List<SearchHit> = emptyList(),
    val count: Int = 0
)

// ── Saneado de los datos de OFF ───────────────────────────────────────────────

/** Techo físico: 100 g de grasa pura son 900 kcal; 902 deja margen para el redondeo de la etiqueta. */
private const val MAX_KCAL_PER_100 = 902.0

/**
 * Errores que OFF ya ha detectado por su cuenta y que inhabilitan el producto para registrar una
 * comida. OFF marca 17.136 productos españoles con algún error de calidad; estos son los que afectan
 * a los números que usa la app, no a las fotos ni a los ingredientes.
 */
private val ERRORES_DESCARTABLES = setOf(
    "en:nutrition-value-total-over-105",
    "en:nutrition-packaging-as-sold-100g-value-total-over-105",
    "en:nutrition-value-over-1000-energy-kcal",
    "en:nutrition-packaging-as-sold-100g-energy-value-in-kcal-greater-than-in-kj",
    "en:nutrition-data-per-serving-missing-serving-size"
)

/**
 * Energía por 100 g/ml con los factores del Anexo XIV cuando la etiqueta no la trae.
 *
 * `carbohydrates_100g` de una etiqueta europea ya incluye los polioles (Anexo I: *"cualquier hidrato
 * de carbono metabolizado por el ser humano, e incluye los polioles"*), que valen 2,4 y no 4, así que
 * se descuentan y se vuelven a sumar con su factor.
 */
internal fun Nutriments.energiaAnexoXIV(): Double {
    val p = proteins_100g ?: 0.0
    val c = carbohydrates_100g ?: 0.0
    val g = fat_100g ?: 0.0
    val fibra = fiber_100g ?: 0.0
    val polioles = (polyols_100g ?: 0.0).coerceAtMost(c)
    val alcohol = alcohol_100g ?: 0.0
    return p * 4.0 + (c - polioles) * 4.0 + polioles * 2.4 + g * 9.0 + fibra * 2.0 + alcohol * 7.0
}

/**
 * `null` si el producto no sirve para registrar una comida.
 *
 * Antes no había **ninguna** comprobación de rango en toda la cadena, y entraban productos españoles
 * reales imposibles: un aceite con P+C+G = 100,6 g por 100 g, unos caramelos con 238 kcal declaradas
 * frente a 416 reales (error del 75 %) y un queso fresco de cabra con 99,6 frente a 249 (150 %).
 *
 * El margen de la comprobación cruzada es holgado a propósito (35 % o 50 kcal, lo que sea mayor):
 * la fibra, los polioles y el alcohol desvían de forma legítima, así que un umbral estrecho
 * descartaría productos correctos. Con este, de 102 productos válidos medidos no se cae ninguno
 * (la mayor desviación legítima observada fue del 9 %) y sí se caen los dos casos graves de arriba.
 */
internal fun sanearProducto(p: Product, errores: List<String>? = null): Product? {
    val n = p.nutriments ?: return null
    val kcal = n.energy_kcal_100g ?: return null
    if (!kcal.isFinite() || kcal <= 0.0 || kcal > MAX_KCAL_PER_100) return null

    val macros = listOf(n.proteins_100g, n.carbohydrates_100g, n.fat_100g).map { it ?: 0.0 }
    if (macros.any { !it.isFinite() || it < 0.0 || it > 100.0 }) return null
    // Más de 100 g de materia en 100 g de producto es físicamente imposible; 101 tolera el redondeo.
    if (macros.sum() > 101.0) return null
    if (listOfNotNull(n.fiber_100g, n.alcohol_100g, n.polyols_100g).any { !it.isFinite() || it < 0.0 || it > 100.0 }) return null

    val estimada = n.energiaAnexoXIV()
    if (estimada > 0.0 && abs(estimada - kcal) > maxOf(50.0, 0.35 * kcal)) return null

    if (errores.orEmpty().any { it in ERRORES_DESCARTABLES }) return null
    return p
}

class FoodService {

    private val logger = LoggerFactory.getLogger(FoodService::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; isLenient = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
            socketTimeoutMillis  = 30000
        }
        defaultRequest {
            headers.append("User-Agent", USER_AGENT)
        }
    }

    private val searchCache = TtlCache<String, FoodSearchResponse>(
        ttlMillis = 24 * 60 * 60 * 1000L,
        maxEntries = 500
    )
    private val barcodeCache = TtlCache<String, Product>(
        ttlMillis = 24 * 60 * 60 * 1000L,
        maxEntries = 500
    )

    // ── Búsqueda por texto ──────────────────────────────────────────────────

    /**
     * Busca en Open Food Facts y devuelve solo productos registrables.
     *
     * **Por qué Search-a-licious y no `cgi/search.pl`.** El endpoint clásico es el único de la API v1
     * con búsqueda a texto libre, y desde el 2026-11-27 devuelve **503 en el 100 % de las peticiones**
     * incluso respetando su límite de 10/min; su cuerpo dice que la página *"is not available to
     * anonymous users"*. El índice nuevo (`search.openfoodfacts.org`) responde 200 sin espaciado.
     *
     * **Por qué hace falta una segunda petición por EAN.** El índice declara un campo `nutriments`,
     * y es tentador usarlo y ahorrarse la hidratación. No se puede: está poblado de forma irregular y
     * es `null` en productos que sí tienen datos. Medido el 2026-11-27 con tres leches españolas
     * (8411700026139, 8480000238191, 7751271032667): `nutriments` nulo en el índice y 63, 63 y 131
     * kcal/100 ml en la API de producto, con `completeness` de 0,975 en el primero. Así que el índice
     * sirve para **encontrar** y la API de producto para **leer los nutrientes**.
     *
     * Cuando el índice sí trae la energía, se usa y se ahorra la petición: no es raro en productos no
     * españoles, y cada llamada ahorrada cuenta contra el límite de 15/min por IP.
     *
     * `search.pl` se mantiene como respaldo por si el índice nuevo falla y el viejo revive.
     */
    suspend fun searchFood(query: String): FoodSearchResponse {
        val key = query.trim().lowercase().take(120)
        if (key.isEmpty()) return FoodSearchResponse()
        searchCache.get(key)?.let { return it }

        val hits = buscarEnIndice(key)
        val result = if (hits != null) {
            FoodSearchResponse(products = hidratarYSanear(hits))
        } else {
            buscarEnEndpointClasico(key) ?: run {
                logger.warn(
                    "Búsqueda de alimentos no disponible: ni Search-a-licious ni search.pl respondieron para '{}'",
                    key
                )
                return FoodSearchResponse(upstreamFailed = true)
            }
        }

        // Un resultado vacío no se cachea: puede ser un fallo transitorio y no "este alimento no
        // existe"; cachearlo congelaría ese falso negativo durante todo el TTL.
        if (result.products.isNotEmpty()) searchCache.put(key, result)
        return result
    }

    /**
     * Candidatos del índice, ya ordenados por relevancia. `null` si el índice no se pudo consultar
     * (distinto de "consultado y sin resultados").
     *
     * Se prueban hasta tres consultas, de la más específica a la más laxa. El filtro
     * `states_tags:"en:nutrition-facts-completed"` importa más de lo que parece: sin él, los primeros
     * resultados españoles de "leche" son fichas a medio rellenar y se gastarían hidrataciones en
     * productos que no van a servir.
     */
    private suspend fun buscarEnIndice(query: String): List<SearchHit>? {
        val termino = escaparLucene(query)
        if (termino.isBlank()) return emptyList()

        val consultas = listOf(
            "$termino countries:\"en:spain\" states_tags:\"en:nutrition-facts-completed\"",
            "$termino countries:\"en:spain\"",
            "$termino states_tags:\"en:nutrition-facts-completed\""
        )
        var algunaRespondio = false
        for (q in consultas) {
            val hits = pedirAlIndice(q) ?: continue
            algunaRespondio = true
            if (hits.isNotEmpty()) return hits
        }
        return if (algunaRespondio) emptyList() else null
    }

    private suspend fun pedirAlIndice(q: String): List<SearchHit>? = try {
        val response = client.get("https://search.openfoodfacts.org/search") {
            parameter("q", q)
            parameter("page_size", CANDIDATOS_DEL_INDICE)
            parameter(
                "fields",
                "code,product_name,product_name_es,brands,nutriments,nutrition_data_per," +
                    "serving_size,serving_quantity,completeness,unique_scans_n," +
                    "data_quality_errors_tags,image_url"
            )
        }
        if (response.status.value != 200) {
            logger.warn("Search-a-licious respondió {} para '{}'", response.status.value, q)
            null
        } else {
            response.body<SearchALiciousResponse>().hits
        }
    } catch (e: Exception) {
        logger.warn("Error consultando Search-a-licious para '{}': {}", q, e.message)
        null
    }

    /**
     * Completa los nutrientes de los primeros candidatos y descarta lo que no sirve.
     *
     * Se hidratan como mucho [MAX_HIDRATACIONES] por búsqueda, en tandas pequeñas, para no pasarse
     * del límite de 15 peticiones por minuto de la API de producto —que se cuenta contra la única IP
     * de Render, compartida por todos los usuarios—. La caché por EAN hace que un producto ya visto
     * no gaste ninguna petición, y como los alimentos que registra una persona se repiten mucho, en
     * pocos días la mayoría de las búsquedas se resuelven sin salir a la red.
     *
     * Un fallo al hidratar un producto concreto descarta ese producto, no la búsqueda entera.
     */
    private suspend fun hidratarYSanear(hits: List<SearchHit>): List<Product> {
        val candidatos = deduplicarHits(hits)

        // El orden que da el índice es el de relevancia y hay que conservarlo, así que cada resultado
        // viaja con la posición de su candidato. Casar por nombre no serviría: el nombre del producto
        // hidratado puede no coincidir con el del candidato.
        val resueltos = sortedMapOf<Int, Product>()
        val porHidratar = mutableListOf<IndexedValue<SearchHit>>()

        for ((indice, hit) in candidatos.withIndex()) {
            // Lo que el índice ya trae completo no se vuelve a pedir: cada petición ahorrada cuenta
            // contra el límite de 15/min.
            val delIndice = hit.nutriments?.energy_kcal_100g?.takeIf { it.isFinite() && it > 0.0 }
            if (delIndice != null) {
                sanearProducto(hit.aProducto(), hit.data_quality_errors_tags)
                    ?.let { resueltos[indice] = it }
            } else if (hit.code != null) {
                porHidratar.add(IndexedValue(indice, hit))
            }
        }

        for (tanda in porHidratar.take(MAX_HIDRATACIONES).chunked(TANDA_HIDRATACION)) {
            val resultados = coroutineScope {
                tanda.map { (indice, hit) -> async { indice to (hit to fetchProduct(hit.code!!)) } }.awaitAll()
            }
            for ((indice, par) in resultados) {
                val (hit, producto) = par
                if (producto == null) continue
                val completado = producto.copy(
                    // El nombre en español del índice es mejor que el que devuelve la API de producto:
                    // el 10,2 % de los productos españoles tiene otro idioma principal, y en la lista
                    // aparecían cosas como "Llet fresca semidesnatada".
                    product_name = hit.product_name_es?.takeIf { it.isNotBlank() }
                        ?: producto.product_name
                        ?: hit.product_name,
                    brands = producto.brands ?: hit.marcasLegibles()
                )
                sanearProducto(completado, hit.data_quality_errors_tags)?.let { resueltos[indice] = it }
            }
        }

        // Un mismo producto puede aparecer con dos EAN distintos y el mismo nombre y marca; eso solo
        // se ve una vez hidratado, así que la última pasada de deduplicación va aquí.
        val vistos = mutableSetOf<String>()
        return resueltos.values.filter { vistos.add(clavePara(it)) }
    }

    /** Un producto ya pedido antes no gasta petición: la caché de EAN se comparte con el escáner. */
    private suspend fun fetchProduct(code: String): Product? {
        barcodeCache.get(code)?.let { return it }
        return when (val r = pedirProducto(code)) {
            is BarcodeLookup.Found -> r.product.also { barcodeCache.put(code, it) }
            else -> null
        }
    }

    /**
     * Respaldo con el endpoint clásico. Hoy devuelve 503 siempre, pero si vuelve a funcionar esto
     * sigue sirviendo; y si no, al menos el 503 **se registra**, que antes no pasaba: `fetchSearch`
     * devolvía una respuesta vacía sin lanzar y sin loguear, así que la caída era invisible en los
     * logs del servidor y la app la presentaba como "sin resultados".
     */
    private suspend fun buscarEnEndpointClasico(query: String): FoodSearchResponse? = try {
        val response = client.get("https://world.openfoodfacts.org/cgi/search.pl") {
            parameter("search_terms", query)
            parameter("search_simple", 1)
            parameter("action", "process")
            parameter("json", 1)
            parameter("page_size", 50)
            parameter("fields", "product_name,brands,nutriments,image_url,nutrition_data_per,serving_size,serving_quantity")
        }
        if (response.status.value != 200) {
            logger.warn("search.pl respondió {} para '{}'", response.status.value, query)
            null
        } else {
            val cuerpo = response.body<RespuestaClasica>()
            FoodSearchResponse(
                products = cuerpo.products.mapNotNull { sanearProducto(it) },
                count = cuerpo.count
            )
        }
    } catch (e: Exception) {
        logger.warn("Error consultando search.pl para '{}': {}", query, e.message)
        null
    }

    @Serializable
    private data class RespuestaClasica(
        val products: List<Product> = emptyList(),
        val count: Int = 0
    )

    /**
     * Quita duplicados **antes** de hidratar, que es cuando sale barato: cada duplicado que se cuela
     * es una petición desperdiciada contra el límite de 15/min.
     *
     * OFF-España tiene "Pan de molde" **74 veces** y "Aceite de oliva virgen extra" 55; una búsqueda
     * de "pollo" devolvía seis filas llamadas exactamente "Pollo", sin marca, empatadas en relevancia
     * e indistinguibles para el usuario. Se agrupa por (nombre, marca) normalizados y se conserva el
     * ejemplar mejor documentado: primero más `completeness`, y a igualdad más escaneos.
     */
    private fun deduplicarHits(hits: List<SearchHit>): List<SearchHit> {
        val mejores = LinkedHashMap<String, SearchHit>()
        for (hit in hits) {
            if (!esNombreUtil(hit.nombreVisible())) continue
            val clave = clavePara(hit.aProducto())
            val previo = mejores[clave]
            if (previo == null || hit.esMejorQue(previo)) mejores[clave] = hit
        }
        return mejores.values.toList()
    }

    private fun clavePara(p: Product): String =
        p.product_name.orEmpty().trim().lowercase() + "|" + p.brands.orEmpty().trim().lowercase()

    /**
     * Descarta los nombres que no son nombres. Un 5,8 % de los productos españoles de OFF no tiene
     * nombre, y entre los que lo tienen hay fichas cuyo "nombre" es el propio código de barras
     * (visto en la verificación: un pan de Bimbo llamado `8412600049297`). Mostrarlos en la lista no
     * ayuda a elegir y da la impresión de un catálogo descuidado.
     */
    private fun esNombreUtil(nombre: String?): Boolean {
        val limpio = nombre?.trim() ?: return false
        if (limpio.length < 2) return false
        // Al menos una letra: si solo hay dígitos y signos, es un código, no un alimento.
        return limpio.any { it.isLetter() }
    }

    private fun SearchHit.esMejorQue(otro: SearchHit): Boolean {
        val c1 = completeness ?: 0.0
        val c2 = otro.completeness ?: 0.0
        if (c1 != c2) return c1 > c2
        return (unique_scans_n ?: 0) > (otro.unique_scans_n ?: 0)
    }

    /** El nombre en español cuando existe; si no, el principal del producto. */
    private fun SearchHit.nombreVisible(): String? =
        product_name_es?.takeIf { it.isNotBlank() } ?: product_name

    /** En este índice las marcas son una lista y vienen con prefijo de idioma ("xx:lidl"). */
    private fun SearchHit.marcasLegibles(): String? =
        brands?.mapNotNull { it.substringAfter(':', it).takeIf(String::isNotBlank) }
            ?.distinct()?.joinToString(", ")?.takeIf { it.isNotBlank() }

    private fun SearchHit.aProducto() = Product(
        product_name = nombreVisible(),
        nutriments = nutriments,
        image_url = image_url,
        brands = marcasLegibles(),
        nutritionDataPer = nutrition_data_per,
        servingSize = serving_size,
        servingQuantity = serving_quantity
    )

    /**
     * Neutraliza los caracteres con significado en la sintaxis de consulta del índice.
     *
     * No es cosmético: el término del usuario se concatena con `countries:"en:spain"`, así que un
     * texto con comillas o dos puntos podría cerrar la cadena y cambiar el filtro de la consulta.
     * Se eliminan en vez de escaparse porque ninguno aporta nada al buscar un alimento.
     */
    private fun escaparLucene(texto: String): String =
        texto.map { if (it in "+-&|!(){}[]^\"~*?:\\/" ) ' ' else it }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ")

    // ── Búsqueda por código de barras EAN-13 ─────────────────────────────────

    /**
     * El saneado se aplica **también aquí**, y no solo en la búsqueda, porque este era el camino por
     * el que entraban los productos imposibles: la lista de resultados filtraba los de 0 kcal, pero
     * el escáner iba directo al selector de ración. Escanear una botella de vino —que en OFF no tiene
     * ni kcal ni macros ni alcohol— permitía registrar la comida a **0 kcal**.
     *
     * Se distinguen los tres desenlaces porque al usuario no le sirve el mismo mensaje para todos:
     * que el código no esté en OFF (puede darlo de alta), que esté pero sin datos utilizables (no es
     * culpa suya ni del escaneo) y que OFF no responda (puede reintentar).
     */
    sealed interface BarcodeLookup {
        data class Found(val product: Product) : BarcodeLookup
        /** El producto existe en OFF, pero sus datos no permiten registrar una comida. */
        data object Unusable : BarcodeLookup
        data object NotFound : BarcodeLookup
        data object UpstreamFailed : BarcodeLookup
    }

    suspend fun lookupBarcode(code: String): BarcodeLookup {
        if (!code.all { it.isDigit() } || code.length !in 8..14) return BarcodeLookup.NotFound
        barcodeCache.get(code)?.let { return BarcodeLookup.Found(it) }

        val resultado = pedirProducto(code)
        // Mismo criterio que searchFood: un fallo de red no es "código inexistente", no se cachea.
        if (resultado is BarcodeLookup.Found) barcodeCache.put(code, resultado.product)
        return resultado
    }

    /** La petición cruda a la API de producto, sin caché. La comparten el escáner y la hidratación. */
    private suspend fun pedirProducto(code: String): BarcodeLookup {
        return try {
            val response = client.get("https://world.openfoodfacts.org/api/v2/product/$code") {
                parameter(
                    "fields",
                    "product_name,brands,nutriments,image_url,nutrition_data_per," +
                        "serving_size,serving_quantity,data_quality_errors_tags"
                )
            }
            if (response.status.value == 404) {
                BarcodeLookup.NotFound
            } else if (response.status.value != 200) {
                logger.warn("API de producto respondió {} para el código {}", response.status.value, code)
                BarcodeLookup.UpstreamFailed
            } else {
                val body = response.body<BarcodeResponse>()
                val crudo = body.product
                when {
                    body.status != 1 || crudo == null -> BarcodeLookup.NotFound
                    else -> sanearProducto(crudo)
                        ?.let { BarcodeLookup.Found(it) }
                        ?: BarcodeLookup.Unusable
                }
            }
        } catch (e: Exception) {
            logger.warn("Error en barcode lookup (Open Food Facts): {}", e.message)
            BarcodeLookup.UpstreamFailed
        }
    }

    private companion object {
        const val USER_AGENT = "GymSmart/1.0 (contact@gymsmart.com)"

        /** Cuántos candidatos se piden al índice. Baratos: el índice no tiene límite observado. */
        const val CANDIDATOS_DEL_INDICE = 50

        /**
         * Cuántos productos se completan con la API de producto en una búsqueda. Cada uno es una
         * petición contra el límite de **15 por minuto y por IP**, y la IP de Render es única para
         * todo el servidor, así que este número es el que decide cuántas búsquedas en frío caben por
         * minuto (12 → una búsqueda completa por minuto en el peor caso, sin caché).
         */
        const val MAX_HIDRATACIONES = 12

        /** Tamaño de la tanda concurrente: acota la ráfaga sin hacer la búsqueda secuencial y lenta. */
        const val TANDA_HIDRATACION = 4
    }
}
