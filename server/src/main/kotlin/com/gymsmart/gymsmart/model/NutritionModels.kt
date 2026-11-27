package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

/**
 * [basis] es la base sobre la que el usuario pesó el alimento ("raw", "cooked" o null) y [yieldId] el
 * alimento del catálogo de factores que se usó para convertir (Tarea G).
 *
 * Son informativos: los valores por 100 g que llegan aquí **ya vienen convertidos** a esa base desde
 * el cliente, así que el total se sigue calculando igual que siempre (gramos × por-100 ÷ 100) y nada
 * del servidor necesita saber de factores de rendimiento. Se guardan por dos razones: para que la app
 * pueda decir "150 g en cocido" al mostrar la comida y reabrir la edición donde se dejó, y para poder
 * **deshacer** la conversión cuando el alimento se reutiliza desde el historial. Lo segundo es lo que
 * obliga a guardar [yieldId] y no solo [basis]: si el usuario eligió el alimento de referencia a mano,
 * su nombre no basta para volver a encontrarlo en el catálogo.
 *
 * Ambos son null en las comidas antiguas y en los alimentos a los que no se aplicó conversión.
 */
@Serializable
data class MealEntryRequest(
    val name: String,
    val grams: Double,
    val kcalPer100: Double,
    val proteinsPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val mealType: String,
    val date: String,
    val basis: String? = null,
    val yieldId: String? = null,
    /**
     * Identificador que genera el **cliente** para este intento de guardado, y que repite si tiene que
     * reintentar. Hace que `POST /nutrition/add` sea idempotente.
     *
     * Existe por un fallo real y silencioso: `saveMealRemote` no distinguía "el servidor rechazó" de
     * "no me llegó la respuesta", así que si el INSERT entraba y el móvil perdía la conexión antes del
     * 201, el usuario veía un error, reintentaba, y quedaban **dos filas en la base y una sola en
     * pantalla**. A partir de ahí el `SUM(grams * kcal_per_100 / 100)` que alimenta el autoajuste de
     * kcal era el doble de lo que el usuario veía. En un móvil eso no es hipotético.
     *
     * No es la clave primaria y no la elige el cliente: la fila sigue teniendo su propio `id` de
     * servidor. Si el cliente pudiera fijar la clave primaria, podría ocupar el id de la fila de otro.
     *
     * Es null en las apps ya instaladas, que no lo mandan; esas conservan el comportamiento anterior.
     */
    val clientId: String? = null
)

@Serializable
data class MealEntry(
    val id: String = "",
    val name: String,
    val grams: Double,
    val kcalPer100: Double,
    val proteinsPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    val basis: String? = null,
    val yieldId: String? = null
)

@Serializable
data class MealEntryWithTarget(
    val entry: MealEntry,
    val mealType: String
)

@Serializable
data class FavoriteRequest(
    val name: String,
    val kcalPer100: Double,
    val proteinsPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double
)

/**
 * Colección de alimentos con su cantidad ya fijada (Tarea B del usuario), para añadirlos todos de
 * una vez a una comida en vez de buscarlos uno por uno. Reutiliza `MealEntry` para cada ítem: ya
 * tiene exactamente los campos que hacen falta para crear una comida real (nombre, gramos, kcal y
 * macros por 100g), y así no hay un segundo modelo casi idéntico que mantener en paralelo.
 */
@Serializable
data class FoodCollection(
    val id: String,
    val name: String,
    val items: List<MealEntry> = emptyList()
)

/**
 * Documento único por usuario (igual que `WorkoutPlan`): no hace falta consultar dentro de él desde
 * el servidor, así que normalizarlo en tablas relacionadas (colección + items) sería mucho más
 * código para lo mismo — ver doc/05-deuda-tecnica.md.
 */
@Serializable
data class FoodCollections(
    val collections: List<FoodCollection> = emptyList()
)