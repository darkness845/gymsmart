package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.MealEntryRequest
import java.util.UUID

class NutritionService(private val turso: TursoService) {

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
                FOREIGN KEY(user_id) REFERENCES users(id)
            )
        """.trimIndent())

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
    }

    suspend fun addMeal(userId: String, req: MealEntryRequest) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        turso.execute(
            "INSERT INTO meals (id, user_id, name, grams, kcal_per_100, proteins_per_100, carbs_per_100, fat_per_100, meal_type, date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            listOf(id, userId, req.name, req.grams.toString(), req.kcalPer100.toString(),
                req.proteinsPer100.toString(), req.carbsPer100.toString(),
                req.fatPer100.toString(), req.mealType, req.date, now.toString())
        )
    }

    suspend fun getUserMeals(userId: String, date: String): List<List<String?>> {
        val result = turso.execute(
            "SELECT name, grams, kcal_per_100, proteins_per_100, carbs_per_100, fat_per_100, meal_type, id FROM meals WHERE user_id = ? AND date = ? ORDER BY created_at ASC",
            listOf(userId, date)
        )
        return turso.extractRows(result)
    }

    // Historial — últimos alimentos únicos usados (sin repetir nombre)
    suspend fun getFoodHistory(userId: String): List<List<String?>> {
        val result = turso.execute(
            """
            SELECT name, kcal_per_100, proteins_per_100, carbs_per_100, fat_per_100
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

    suspend fun deleteMeal(id: String) {
        turso.execute("DELETE FROM meals WHERE id = ?", listOf(id))
    }

    suspend fun updateMeal(id: String, req: MealEntryRequest) {
        turso.execute(
            "UPDATE meals SET grams = ?, kcal_per_100 = ?, proteins_per_100 = ?, carbs_per_100 = ?, fat_per_100 = ? WHERE id = ?",
            listOf(req.grams.toString(), req.kcalPer100.toString(),
                req.proteinsPer100.toString(), req.carbsPer100.toString(),
                req.fatPer100.toString(), id)
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