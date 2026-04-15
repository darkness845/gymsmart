package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.model.User
import org.mindrot.jbcrypt.BCrypt
import java.util.UUID

class UserService(private val turso: TursoService) {

    // Crea la tabla si no existe (llámalo al arrancar el servidor)
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
    }

    // Registra un usuario nuevo
    suspend fun register(name: String, email: String, password: String): Result<User> {
        // Comprobar si ya existe el email
        val existing = turso.execute(
            "SELECT id FROM users WHERE email = ?",
            listOf(email)
        )
        val rows = turso.extractRows(existing)
        if (rows.isNotEmpty()) {
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

    // Login: devuelve el User si las credenciales son correctas
    suspend fun login(email: String, password: String): Result<User> {
        val result = turso.execute(
            "SELECT id, name, email, password_hash FROM users WHERE email = ?",
            listOf(email)
        )
        val rows = turso.extractRows(result)

        if (rows.isEmpty()) {
            return Result.failure(Exception("Email o contraseña incorrectos"))
        }

        val row = rows.first()
        val id = row[0] ?: return Result.failure(Exception("Error leyendo usuario"))
        val name = row[1] ?: ""
        val storedEmail = row[2] ?: ""
        val hash = row[3] ?: return Result.failure(Exception("Error leyendo hash"))

        if (!BCrypt.checkpw(password, hash)) {
            return Result.failure(Exception("Email o contraseña incorrectos"))
        }

        return Result.success(User(id = id, name = name, email = storedEmail))
    }
}