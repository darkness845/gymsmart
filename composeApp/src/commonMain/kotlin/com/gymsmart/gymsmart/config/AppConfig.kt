package com.gymsmart.gymsmart.config

object AppConfig {
    // Para probar contra un servidor local en vez de Render, comenta la línea de abajo y usa
    // "http://localhost:8080" (Web/Desktop) o "http://<tu IP local>:8080" (Android físico/emulador,
    // mirando la IPv4 con `ipconfig`/`ifconfig`).

    //Render
    const val BASE_URL = "https://gymsmart-server.onrender.com"

    //tailscale
    //const val BASE_URL = "http://100.91.80.89:8080"

    //Desktop
    //const val BASE_URL = "http://localhost:8080"
}