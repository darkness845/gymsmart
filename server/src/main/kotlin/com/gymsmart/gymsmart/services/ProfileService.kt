package com.gymsmart.gymsmart.services

import kotlin.math.roundToInt

// ── Modelos internos del servidor ─────────────────────────────────────────────

data class UserProfile(
    val userId:        String,
    val weightKg:      Double,
    val heightCm:      Double,
    val age:           Int,
    val sex:           String,
    val activityLevel: String,
    val goal:          String,
    val goalRate:      Double,
    val updatedAt:     Long
)

data class NutritionTargets(
    val tdee:        Int,
    val targetKcal:  Int,
    val proteinG:    Int,
    val carbsG:      Int,
    val fatG:        Int,
    val bmi:         Double,
    val bmiCategory: String
)

// ── Servicio ──────────────────────────────────────────────────────────────────

class ProfileService(private val turso: TursoService) {

    suspend fun initTable() {
        turso.execute(
            """
            CREATE TABLE IF NOT EXISTS user_profiles (
                user_id        TEXT    PRIMARY KEY,
                weight_kg      REAL    NOT NULL,
                height_cm      REAL    NOT NULL,
                age            INTEGER NOT NULL,
                sex            TEXT    NOT NULL,
                activity_level TEXT    NOT NULL,
                goal           TEXT    NOT NULL,
                goal_rate      REAL    NOT NULL,
                updated_at     INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    suspend fun initWeightHistoryTable() {
        turso.execute(
            """
        CREATE TABLE IF NOT EXISTS weight_history (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id    TEXT    NOT NULL,
            weight_kg  REAL    NOT NULL,
            day        TEXT    NOT NULL,
            created_at INTEGER NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """.trimIndent()
        )
    }

    suspend fun initSubscriptionTable() {
        turso.execute("""
        CREATE TABLE IF NOT EXISTS subscriptions (
            user_id    TEXT    PRIMARY KEY,
            plan       TEXT    NOT NULL DEFAULT 'free',
            expires_at INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    """.trimIndent())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    suspend fun upsertProfile(
        userId:        String,
        weightKg:      Double,
        heightCm:      Double,
        age:           Int,
        sex:           String,
        activityLevel: String,
        goal:          String,
        goalRate:      Double
    ): UserProfile {
        val now = System.currentTimeMillis()

        // Obtener peso anterior
        val previous = getProfile(userId)

        turso.execute(
            """
        INSERT OR REPLACE INTO user_profiles
            (user_id, weight_kg, height_cm, age, sex, activity_level, goal, goal_rate, has_wearable, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
            listOf(
                userId, weightKg.toString(), heightCm.toString(), age.toString(),
                sex, activityLevel, goal, goalRate.toString(),
                if (activityLevel == "wearable") "1" else "0", now.toString()
            )
        )

        // Guardar historial solo si el peso cambió (o es la primera vez)
        if (previous == null || previous.weightKg != weightKg) {
            val today = java.time.LocalDate.now().toString() // "2025-05-14"

            // Evitar duplicado del mismo día
            val existing = turso.execute(
                "SELECT id FROM weight_history WHERE user_id = ? AND day = ?",
                listOf(userId, today)
            )
            if (turso.extractRows(existing).isEmpty()) {
                turso.execute(
                    "INSERT INTO weight_history (user_id, weight_kg, day, created_at) VALUES (?, ?, ?, ?)",
                    listOf(userId, weightKg.toString(), today, now.toString())
                )
            } else {
                // Actualizar el registro del día si ya existe
                turso.execute(
                    "UPDATE weight_history SET weight_kg = ?, created_at = ? WHERE user_id = ? AND day = ?",
                    listOf(weightKg.toString(), now.toString(), userId, today)
                )
            }
        }

        return UserProfile(userId, weightKg, heightCm, age, sex, activityLevel, goal, goalRate, now)
    }

    suspend fun getProfile(userId: String): UserProfile? {
        val result = turso.execute(
            "SELECT user_id, weight_kg, height_cm, age, sex, activity_level, goal, goal_rate, updated_at FROM user_profiles WHERE user_id = ?",
            listOf(userId)
        )
        val row = turso.extractRows(result).firstOrNull() ?: return null
        return UserProfile(
            userId        = row[0] ?: return null,
            weightKg      = row[1]?.toDoubleOrNull() ?: return null,
            heightCm      = row[2]?.toDoubleOrNull() ?: return null,
            age           = row[3]?.toIntOrNull()    ?: return null,
            sex           = row[4] ?: return null,
            activityLevel = row[5] ?: return null,
            goal          = row[6] ?: return null,
            goalRate      = row[7]?.toDoubleOrNull() ?: return null,
            updatedAt     = row[8]?.toLongOrNull()   ?: 0L
        )
    }

    suspend fun hasProfile(userId: String): Boolean = getProfile(userId) != null

    // ── Cálculo de TDEE y macros ──────────────────────────────────────────────
    //
    // Mifflin-St Jeor:
    //   Hombre: BMR = 10×kg + 6.25×cm − 5×edad + 5
    //   Mujer:  BMR = 10×kg + 6.25×cm − 5×edad − 161
    //
    // Macros (ISSN 2017):
    //   Proteína: 2.0 g/kg en déficit  /  1.8 g/kg en mantenimiento y superávit
    //   Grasa:    25% kcal totales (mínimo para función hormonal)
    //   Carbos:   resto calórico

    fun computeTargets(profile: UserProfile): NutritionTargets {
        val bmr = if (profile.sex == "male")
            10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.age + 5.0
        else
            10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.age - 161.0

        // Con pulsera usamos BMR directamente — la pulsera añade el resto en tiempo real
        val tdee = if (profile.activityLevel == "wearable") {
            bmr.roundToInt()
        } else {
            val multiplier = when (profile.activityLevel) {
                "sedentary"   -> 1.2
                "light"       -> 1.375
                "moderate"    -> 1.55
                "active"      -> 1.725
                "very_active" -> 1.9
                else          -> 1.2
            }
            (bmr * multiplier).roundToInt()
        }

        // goalRate ahora es directamente kcal con signo: -300, -400, -500, 0, 300, 400, 500
        val dailyDelta = profile.goalRate.roundToInt()
        val minKcal    = if (profile.sex == "male") 1500 else 1200
        val targetKcal = (tdee + dailyDelta).coerceAtLeast(minKcal)

        val proteinG = (profile.weightKg * if (profile.goal == "lose_fat") 2.0 else 1.8).roundToInt()
        val fatG     = (targetKcal * 0.25 / 9.0).roundToInt()
        val carbsG   = ((targetKcal - proteinG * 4 - fatG * 9) / 4.0).roundToInt().coerceAtLeast(0)

        val heightM     = profile.heightCm / 100.0
        val bmi         = profile.weightKg / (heightM * heightM)
        val bmiRounded  = (bmi * 10.0).roundToInt() / 10.0
        val bmiCategory = when {
            bmi < 18.5 -> "Bajo peso"
            bmi < 25.0 -> "Normopeso"
            bmi < 30.0 -> "Sobrepeso"
            bmi < 35.0 -> "Obesidad grado I"
            bmi < 40.0 -> "Obesidad grado II"
            else       -> "Obesidad grado III"
        }

        return NutritionTargets(tdee, targetKcal, proteinG, carbsG, fatG, bmiRounded, bmiCategory)
    }

    suspend fun getWeightHistory(userId: String): List<Map<String, String>> {
        val result = turso.execute(
            "SELECT weight_kg, day FROM weight_history WHERE user_id = ? ORDER BY day ASC",
            listOf(userId)
        )
        return turso.extractRows(result).mapNotNull { row ->
            val w = row[0] ?: return@mapNotNull null
            val d = row[1] ?: return@mapNotNull null
            mapOf("weightKg" to w, "day" to d)
        }
    }

    suspend fun getSubscription(userId: String): Pair<String, Long> {
        val result = turso.execute(
            "SELECT plan, expires_at FROM subscriptions WHERE user_id = ?",
            listOf(userId)
        )
        val row = turso.extractRows(result).firstOrNull()
            ?: return Pair("free", 0L)
        return Pair(row[0] ?: "free", row[1]?.toLongOrNull() ?: 0L)
    }

    suspend fun activatePremium(userId: String, durationMs: Long) {
        val expiresAt = System.currentTimeMillis() + durationMs
        turso.execute("""
        INSERT OR REPLACE INTO subscriptions (user_id, plan, expires_at)
        VALUES (?, 'premium', ?)
    """.trimIndent(), listOf(userId, expiresAt.toString()))
    }

    suspend fun isPremium(userId: String): Boolean {
        val (plan, expiresAt) = getSubscription(userId)
        return plan == "premium" && expiresAt > System.currentTimeMillis()
    }
}