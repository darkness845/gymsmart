package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.User
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID

class UserService(private val turso: TursoService) {

    suspend fun initTable() {
        turso.execute(
            """
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        // Tabla de tokens de reseteo
        turso.execute(
            """
            CREATE TABLE IF NOT EXISTS password_reset_tokens (
                token TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                expires_at INTEGER NOT NULL,
                used INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
    }

    suspend fun register(name: String, email: String, password: String): Result<User> {
        val existing = turso.execute("SELECT id FROM users WHERE email = ?", listOf(email))
        if (turso.extractRows(existing).isNotEmpty()) {
            return Result.failure(Exception("El email ya está registrado"))
        }
        val id = UUID.randomUUID().toString()
        val hash = BCrypt.hashpw(password, BCrypt.gensalt())
        val now = System.currentTimeMillis()
        turso.execute(
            "INSERT INTO users (id, name, email, password_hash, created_at) VALUES (?, ?, ?, ?, ?)",
            listOf(id, name, email, hash, now.toString())
        )
        return Result.success(User(id = id, name = name, email = email))
    }

    suspend fun login(email: String, password: String): Result<User> {
        val result = turso.execute(
            "SELECT id, name, email, password_hash FROM users WHERE email = ?",
            listOf(email)
        )
        val rows = turso.extractRows(result)
        if (rows.isEmpty()) return Result.failure(Exception("Email o contraseña incorrectos"))
        val row = rows.first()
        val id = row[0] ?: return Result.failure(Exception("Error leyendo usuario"))
        val name = row[1] ?: ""
        val storedEmail = row[2] ?: ""
        val hash = row[3] ?: return Result.failure(Exception("Error leyendo hash"))
        if (!BCrypt.checkpw(password, hash)) return Result.failure(Exception("Email o contraseña incorrectos"))
        return Result.success(User(id = id, name = name, email = storedEmail))
    }

    // Genera un token de reseteo y lo guarda en Turso
    suspend fun createResetToken(email: String): Result<Pair<String, String>> {
        val result = turso.execute(
            "SELECT id, name FROM users WHERE email = ?",
            listOf(email)
        )
        val rows = turso.extractRows(result)
        if (rows.isEmpty()) return Result.failure(Exception("Email no encontrado"))

        val userId = rows.first()[0] ?: return Result.failure(Exception("Error leyendo usuario"))
        val userName = rows.first()[1] ?: ""

        val token = UUID.randomUUID().toString()
        val expiresAt = System.currentTimeMillis() + (60 * 60 * 1000) // 1 hora

        turso.execute(
            "INSERT INTO password_reset_tokens (token, user_id, expires_at, used) VALUES (?, ?, ?, 0)",
            listOf(token, userId, expiresAt.toString())
        )

        return Result.success(Pair(token, userName))
    }

    // Valida el token y cambia la contraseña
    suspend fun resetPassword(token: String, newPassword: String): Result<Unit> {
        val result = turso.execute(
            "SELECT user_id, expires_at, used FROM password_reset_tokens WHERE token = ?",
            listOf(token)
        )
        val rows = turso.extractRows(result)
        if (rows.isEmpty()) return Result.failure(Exception("Token inválido"))

        val row = rows.first()
        val userId = row[0] ?: return Result.failure(Exception("Token inválido"))
        val expiresAt = row[1]?.toLongOrNull() ?: return Result.failure(Exception("Token inválido"))
        val used = row[2]?.toIntOrNull() ?: 1

        if (used == 1) return Result.failure(Exception("El token ya fue usado"))
        if (System.currentTimeMillis() > expiresAt) return Result.failure(Exception("El token ha expirado"))

        val newHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
        turso.execute(
            "UPDATE users SET password_hash = ? WHERE id = ?",
            listOf(newHash, userId)
        )
        turso.execute(
            "UPDATE password_reset_tokens SET used = 1 WHERE token = ?",
            listOf(token)
        )

        return Result.success(Unit)
    }

    suspend fun verifyResetToken(token: String): Result<Unit> {
        val result = turso.execute(
            "SELECT expires_at, used FROM password_reset_tokens WHERE token = ?",
            listOf(token)
        )
        val rows = turso.extractRows(result)
        if (rows.isEmpty()) return Result.failure(Exception("Código incorrecto"))
        val expiresAt = rows.first()[0]?.toLongOrNull() ?: return Result.failure(Exception("Código inválido"))
        val used = rows.first()[1]?.toIntOrNull() ?: 1
        if (used == 1) return Result.failure(Exception("El código ya fue usado"))
        if (System.currentTimeMillis() > expiresAt) return Result.failure(Exception("El código ha expirado"))
        return Result.success(Unit)
    }
}