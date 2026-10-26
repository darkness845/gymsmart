package com.gymsmart.gymsmart.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val name: String,
    val email: String,
    val phone: String = "",
    val country: String = "",
    val birthDate: String = ""
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val success: Boolean,
    val message: String,
    val user: User? = null,
    // true solo cuando la petición ni siquiera llegó a tener respuesta del servidor (red caída,
    // timeout, cold start de Render). Sin esto, "no hay sesión" y "no hay red" eran indistinguibles
    // y un fallo de red podía expulsar al login a un usuario con sesión válida (ver RED-1 en
    // doc/08-backlog-mejoras.md). El servidor nunca manda este campo; por eso tiene valor por
    // defecto y no rompe la deserialización de sus respuestas.
    val networkError: Boolean = false
)