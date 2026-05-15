package com.gymsmart.gymsmart.config

object AppConfig {
    // Cambia según donde ejecutes:
    // Web/Desktop: "http://localhost:8080"
    // Android físico: "http://192.168.1.140:8080"
    // Mirar en el cmd el comando ipconfig y poner el puerto de ipv4
    //const val BASE_URL = "http://192.168.1.140:8080"
    // const val BASE_URL = "http://192.168.1.145:8080" DANI
    //const val BASE_URL = "http://100.91.80.89:8080" //JONY

    const val BASE_URL = "https://gymsmart-server.onrender.com" //Render

    const val STRIPE_PK = "pk_test_51TX5MCFCBAAys7G81bnlpN3DobN1UVKjyZYq7eRATp0EPLJjxQQ4ydsXyGXehwaje2JUX4M4kxbPN0ossA5nSpVV007AOz6UJj"
}