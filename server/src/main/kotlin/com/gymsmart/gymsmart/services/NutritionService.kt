package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.FoodCollections
import com.gymsmart.gymsmart.model.MealEntryRequest
import kotlinx.serialization.json.Json
import java.util.UUID

class NutritionService(private val turso: TursoService) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun initTable() {
        turso.execute("""
            CREATE TABLE IF NOT EXISTS meals (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                name TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal_per_100 REAL NOT NULL,
                proteins_per_100 REAL NOT NULL,
                carbs_per_100 REAL NOT NULL,
                fat_per_100 REAL NOT NULL,
                meal_type TEXT NOT NULL,
                date TEXT,
                created_at INTEGER NOT NULL,
                basis TEXT,
                yield_id TEXT,
                FOREIGN KEY(user_id) REFERENCES users(id)
            )
        """.trimIndent())

        // Base de pesaje de la comida ("raw"/"cooked") y alimento del catálogo con el que se convirtió
        // (Tarea G). Vacío o NULL es lo que tendrán todas las comidas registradas antes de esta
        // función, y significa "no se preguntó": se muestran sin etiqueta, como siempre. Mismo ALTER
        // idempotente que en ProfileService — SQLite no soporta "ADD COLUMN IF NOT EXISTS", así que a
        // partir del segundo arranque el error por columna duplicada se ignora a propósito.
        runCatching { turso.execute("ALTER TABLE meals ADD COLUMN basis TEXT") }
        runCatching { turso.execute("ALTER TABLE meals ADD COLUMN yield_id TEXT") }

        // Idempotencia de POST /nutrition/add: el cliente manda un client_id por intento de guardado
        // y lo repite al reintentar, así que un reintento tras un fallo de red no crea una segunda
        // fila. El índice es UNIQUE por (user_id, client_id) y no sobre client_id a secas, para que
        // dos usuarios puedan generar el mismo identificador sin estorbarse.
        //
        // En SQLite los NULL son distintos entre sí dentro de un índice UNIQUE, así que todas las
        // comidas antiguas (client_id NULL) conviven sin violar la restricción. Mismo ALTER
        // idempotente que arriba: a partir del segundo arranque el error por columna duplicada se
        // ignora a propósito.
        runCatching { turso.execute("ALTER TABLE meals ADD COLUMN client_id TEXT") }
        runCatching {
            turso.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_meals_user_client ON meals(user_id, client_id)"
            )
        }

        // Tabla de favoritos
        turso.execute("""
            CREATE TABLE IF NOT EXISTS food_favorites (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                name TEXT NOT NULL,
                kcal_per_100 REAL NOT NULL,
                proteins_per_100 REAL NOT NULL,
                carbs_per_100 REAL NOT NULL,
                fat_per_100 REAL NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """.trimIndent())

        // Colecciones (Tarea B): documento único por usuario, igual que `workout_plans` — el
        // servidor no necesita consultar dentro de él, solo guardarlo y devolverlo entero.
        turso.execute("""
            CREATE TABLE IF NOT EXISTS food_collections (
                user_id    TEXT PRIMARY KEY,
                data       TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """.trimIndent())
    }

    suspend fun getCollections(userId: String): FoodCollections {
        val result = turso.execute("SELECT data FROM food_collections WHERE user_id = ?", listOf(userId))
        val raw = turso.extractRows(result).firstOrNull()?.firstOrNull() ?: return FoodCollections()
        return runCatching { json.decodeFromString<FoodCollections>(raw) }.getOrElse { FoodCollections() }
    }

    suspend fun saveCollections(userId: String, collections: FoodCollections) {
        turso.execute(
            """INSERT INTO food_collections (user_id, data, updated_at) VALUES (?, ?, ?)
               ON CONFLICT(user_id) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at""",
            listOf(userId, json.encodeToString(collections), System.currentTimeMillis().toString())
        )
    }

    /**
     * Guarda una comida y devuelve su id.
     *
     * **Es idempotente cuando el cliente manda `clientId`**: el `INSERT OR IGNORE` no hace nada si ese
     * (user_id, client_id) ya existe, y después se lee el id de la fila que hay, así que un reintento
     * devuelve el id de la primera inserción en vez de crear una segunda fila. Eso es lo que impide el
     * doble conteo cuando el móvil pierde la conexión justo después de que el INSERT haya entrado.
     *
     * Sin `clientId` (apps ya instaladas) el comportamiento es el de antes: una fila por llamada.
     */
    suspend fun addMeal(userId: String, req: MealEntryRequest): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val clientId = req.clientId?.takeIf { it.isNotBlank() }

        // TursoService.execute solo acepta List<String>, así que no hay forma de pasar un NULL. Para
        // `basis` y `yield_id` da igual (la cadena vacía se vuelve a leer como null en la ruta), pero
        // para `client_id` **no**: con el índice UNIQUE(user_id, client_id), guardar "" haría que la
        // segunda comida de un usuario que no manda clientId chocara con la primera y se ignorara en
        // silencio. Los NULL sí son distintos entre sí en un índice UNIQUE de SQLite, así que cuando
        // no hay clientId se omite la columna y queda NULL.
        val columnasBase = "id, user_id, name, grams, kcal_per_100, proteins_per_100, carbs_per_100, " +
            "fat_per_100, meal_type, date, created_at, basis, yield_id"
        val valoresBase = listOf(
            id, userId, req.name, req.grams.toString(), req.kcalPer100.toString(),
            req.proteinsPer100.toString(), req.carbsPer100.toString(),
            req.fatPer100.toString(), req.mealType, req.date, now.toString(),
            req.basis ?: "", req.yieldId ?: ""
        )

        if (clientId == null) {
            turso.execute(
                "INSERT INTO meals ($columnasBase) VALUES (${"?, ".repeat(12)}?)",
                valoresBase
            )
            return id
        }

        turso.execute(
            "INSERT OR IGNORE INTO meals ($columnasBase, client_id) VALUES (${"?, ".repeat(13)}?)",
            valoresBase + clientId
        )

        // Puede ser el id que acabamos de insertar o el de la fila que ya estaba: en los dos casos es
        // el id correcto que el cliente tiene que usar para editar o borrar esta comida.
        val existente = turso.execute(
            "SELECT id FROM meals WHERE user_id = ? AND client_id = ? LIMIT 1",
            listOf(userId, clientId)
        )
        return turso.extractRows(existente).firstOrNull()?.firstOrNull() ?: id
    }

    suspend fun getUserMeals(userId: String, date: String): List<List<String?>> {
        val result = turso.execute(
            "SELECT name, grams, kcal_per_100, proteins_per_100, carbs_per_100, fat_per_100, meal_type, id, basis, yield_id FROM meals WHERE user_id = ? AND date = ? ORDER BY created_at ASC",
            listOf(userId, date)
        )
        return turso.extractRows(result)
    }

    /**
     * Ingesta total por día desde `sinceDay` (inclusive), para el autoajuste
     * (doc/10-fundamento-cientifico.md §5). Solo devuelve días con comidas registradas: los días sin
     * registrar **no** cuentan como 0 kcal, que falsearía la media a la baja y haría creer al sistema
     * que el usuario gasta mucho más de lo real.
     */
    suspend fun getDailyIntake(userId: String, sinceDay: String): List<Pair<String, Double>> {
        val result = turso.execute(
            """
            SELECT date, SUM(grams * kcal_per_100 / 100.0) AS kcal
            FROM meals
            WHERE user_id = ? AND date >= ?
            GROUP BY date
            ORDER BY date ASC
            """.trimIndent(),
            listOf(userId, sinceDay)
        )
        return turso.extractRows(result).mapNotNull { row ->
            val day = row.getOrNull(0) ?: return@mapNotNull null
            val kcal = row.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            day to kcal
        }
    }

    // Historial — últimos alimentos únicos usados
    suspend fun getFoodHistory(userId: String): List<List<String?>> {
        val result = turso.execute(
            """
            SELECT name, kcal_per_100, proteins_per_100, carbs_per_100, fat_per_100, basis, yield_id
            FROM meals
            WHERE user_id = ? AND id IN (
                SELECT id FROM meals WHERE user_id = ?
                GROUP BY name
                HAVING MAX(created_at)
            )
            ORDER BY created_at DESC
            LIMIT 30
            """.trimIndent(),
            listOf(userId, userId)
        )
        return turso.extractRows(result)
    }

    suspend fun deleteMeal(id: String, userId: String) {
        turso.execute("DELETE FROM meals WHERE id = ? AND user_id = ?", listOf(id, userId))
    }

    suspend fun updateMeal(id: String, userId: String, req: MealEntryRequest) {
        turso.execute(
            "UPDATE meals SET grams = ?, kcal_per_100 = ?, proteins_per_100 = ?, carbs_per_100 = ?, fat_per_100 = ?, basis = ?, yield_id = ? WHERE id = ? AND user_id = ?",
            listOf(req.grams.toString(), req.kcalPer100.toString(),
                req.proteinsPer100.toString(), req.carbsPer100.toString(),
                req.fatPer100.toString(), req.basis ?: "", req.yieldId ?: "", id, userId)
        )
    }

    // Favoritos
    suspend fun addFavorite(userId: String, name: String, kcalPer100: Double,
                            proteinsPer100: Double, carbsPer100: Double, fatPer100: Double) {
        val id = UUID.randomUUID().toString()
        turso.execute(
            "INSERT INTO food_favorites (id, user_id, name, kcal_per_100, proteins_per_100, carbs_per_100, fat_per_100, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(id, userId, name, kcalPer100.toString(), proteinsPer100.toString(),
                carbsPer100.toString(), fatPer100.toString(), System.currentTimeMillis().toString())
        )
    }

    suspend fun removeFavorite(userId: String, name: String) {
        turso.execute(
            "DELETE FROM food_favorites WHERE user_id = ? AND name = ?",
            listOf(userId, name)
        )
    }

    suspend fun getFavorites(userId: String): List<List<String?>> {
        val result = turso.execute(
            "SELECT name, kcal_per_100, proteins_per_100, carbs_per_100, fat_per_100 FROM food_favorites WHERE user_id = ? ORDER BY created_at DESC",
            listOf(userId)
        )
        return turso.extractRows(result)
    }

    suspend fun isFavorite(userId: String, name: String): Boolean {
        val result = turso.execute(
            "SELECT id FROM food_favorites WHERE user_id = ? AND name = ?",
            listOf(userId, name)
        )
        return turso.extractRows(result).isNotEmpty()
    }
}