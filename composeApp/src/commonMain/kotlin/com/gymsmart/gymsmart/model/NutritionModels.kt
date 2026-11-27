package com.gymsmart.gymsmart.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
 * [nutritionDataPer] es la base que declara el producto: "100g" o **"100ml"**. El 11,8 % de los
 * productos españoles está en base 100 ml (toda la leche, los caldos) y la app pedía gramos y
 * rotulaba "por X g" para todos, lo que introduce el error de la densidad (3 % en leche, 9 % en
 * aceite) además de etiquetar mal.
 *
 * [servingQuantity] son los gramos de una ración, ya calculados por Open Food Facts. Lo trae el 57 %
 * de los productos, y es lo que permite ofrecer un botón "1 ración (90 g)" sin ninguna fuente nueva.
 */
@Serializable
data class Product(
    val product_name: String? = null,
    val brands: String? = null,
    val nutriments: Nutriments? = null,
    @SerialName("nutrition_data_per") val nutritionDataPer: String? = null,
    @SerialName("serving_size")       val servingSize: String? = null,
    @SerialName("serving_quantity")   val servingQuantity: Double? = null
) {
    /** true si el producto declara sus valores por 100 **ml** y no por 100 g. */
    val isLiquid: Boolean get() = nutritionDataPer?.trim()?.lowercase() == "100ml"

    /** "g" o "ml", para rotular el campo de cantidad con la unidad que de verdad corresponde. */
    val unitLabel: String get() = if (isLiquid) "ml" else "g"
}

/**
 * kcal por 100 g (o por 100 ml) a usar para esta entrada. `energy-kcal_100g` es el campo estándar de
 * Open Food Facts para esto; `energy-kcal` (sin sufijo) NO está garantizado a ser por 100 g (puede
 * ser por ración), así que no se usa como si lo fuera. Si falta `energy-kcal_100g` se estima desde
 * los macros, y solo como último recurso se acepta el campo ambiguo.
 *
 * **La estimación usa los factores del Anexo XIV del Reglamento (UE) 1169/2011, no la regla 4/4/9.**
 * 4/4/9 no tiene término de fibra (2 kcal/g), ni de polioles (2,4) ni de alcohol (7), y los tres
 * aparecen en productos reales del lineal español:
 * - Wasa Fibres (26 g de fibra/100 g) declara 333 kcal; 4/4/9 da 281, un **−15,6 %**. Con el Anexo
 *   XIV da 333 exacto. Verificado igual en All-Bran y en un Carrefour "fibra".
 * - Un chocolate "sin azúcar" con 40 g de maltitol se sobrecuenta en **+64 kcal/100 g** con 4/4/9,
 *   porque el poliol ya va dentro de `carbohydrates_100g` (Anexo I) pero vale 2,4 y no 4.
 * - Una cerveza de 6,6 g de alcohol/100 ml tiene 58 kcal de etiqueta y 23 según 4/4/9: **−60 %**.
 */
fun Nutriments?.estimatedKcalPer100(): Double {
    this ?: return 0.0
    energy_kcal_100g?.let { return it }
    val hasMacros = proteins_100g != null || carbohydrates_100g != null || fat_100g != null ||
        fiber_100g != null || alcohol_100g != null
    if (hasMacros) {
        val p = proteins_100g ?: 0.0
        val c = carbohydrates_100g ?: 0.0
        val g = fat_100g ?: 0.0
        val fibra = fiber_100g ?: 0.0
        // Los polioles ya están contados dentro de los hidratos: se descuentan y se recobran a 2,4.
        val polioles = (polyols_100g ?: 0.0).coerceAtMost(c)
        val alcohol = alcohol_100g ?: 0.0
        return p * 4.0 + (c - polioles) * 4.0 + polioles * 2.4 + g * 9.0 + fibra * 2.0 + alcohol * 7.0
    }
    return energy_kcal ?: 0.0
}

/**
 * [upstreamFailed] distingue "Open Food Facts no tiene este alimento" de "no he podido preguntarle".
 * Sin ella la app decía *"Sin resultados con datos nutricionales"* cuando la búsqueda estaba caída,
 * que es falso y llevaba al usuario a pensar que el alimento no existe.
 */
@Serializable
data class FoodSearchResponse(
    val products: List<Product> = emptyList(),
    val upstreamFailed: Boolean = false
)

/**
 * Colección de alimentos con su cantidad ya fijada, para añadirlos todos de una vez a una comida en
 * vez de buscarlos uno por uno. Reutiliza `MealEntry` para cada ítem: ya tiene exactamente los
 * campos que hacen falta (nombre, gramos, kcal y macros por 100g), sin un segundo modelo paralelo.
 */
@Serializable
data class FoodCollection(
    val id: String,
    val name: String,
    val items: List<MealEntry> = emptyList()
)

/** Documento único por usuario, espejo exacto del que guarda y devuelve el servidor. */
@Serializable
data class FoodCollections(
    val collections: List<FoodCollection> = emptyList()
)
