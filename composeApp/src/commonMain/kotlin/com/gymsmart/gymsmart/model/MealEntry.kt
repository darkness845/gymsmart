package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

/**
 * [basis] es la base sobre la que se pesó el alimento ("raw", "cooked" o null) y [yieldId] el alimento
 * del catálogo de factores que se usó para convertir (Tarea G).
 *
 * Los valores por 100 g **ya están convertidos** a esa base, así que el cálculo del total no cambia:
 * los gramos de [grams] son los que el usuario pesó de verdad, en la base que eligió. Los dos campos
 * sirven para poder decírselo ("150 g en cocido"), para reabrir la edición donde la dejó y para
 * **deshacer** la conversión si el alimento se reutiliza desde el historial. [yieldId] es necesario
 * además de [basis] porque el usuario puede haber elegido el alimento de referencia a mano, y entonces
 * su nombre no basta para volver a encontrarlo en el catálogo.
 *
 * Los dos son null en las comidas registradas antes de esta función y en los alimentos sin conversión.
 */
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
) {
    val kcal:     Double get() = kcalPer100     / 100.0 * grams
    val proteins: Double get() = proteinsPer100 / 100.0 * grams
    val carbs:    Double get() = carbsPer100    / 100.0 * grams
    val fat:      Double get() = fatPer100      / 100.0 * grams
}

@Serializable
data class MealEntryWithTarget(
    val entry: MealEntry,
    val mealType: String
)

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
    /** Ver [nuevoClientId]. Hace idempotente el guardado para que un reintento no duplique la fila. */
    val clientId: String? = null
)

/**
 * Identificador de un **intento de guardado**, generado por el cliente y repetido en los reintentos.
 *
 * Es lo que impide un doble conteo invisible. `saveMealRemote` no puede distinguir "el servidor
 * rechazó" de "no me llegó la respuesta": las dos cosas son un `null`. Así que si el INSERT entraba y
 * el móvil perdía la conexión antes del 201, el usuario veía "no se pudo guardar", volvía a pulsar, y
 * quedaban **dos filas en la base y una sola en pantalla**. Desde ese momento el
 * `SUM(grams * kcal_per_100 / 100)` que alimenta el autoajuste de kcal valía el doble de lo que el
 * usuario tenía delante. En un móvil, perder la conexión a mitad de una petición es lo normal.
 *
 * El servidor lo guarda en `client_id` con un índice único por usuario y hace `INSERT OR IGNORE`, así
 * que el segundo intento devuelve el id de la fila que ya existía en vez de crear otra.
 *
 * No es un UUID: no hay API de UUID en `commonMain` sin APIs experimentales, y no hace falta. Basta
 * que sea distinto entre intentos distintos, y el reloj más 64 bits de azar lo garantizan de sobra
 * para una app de uso personal.
 */
fun nuevoClientId(): String =
    "c-${kotlin.time.Clock.System.now().toEpochMilliseconds()}-${kotlin.random.Random.nextLong()}"

@Serializable
data class FavoriteBody(
    val name: String,
    val kcalPer100: Double,
    val proteinsPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double
)
