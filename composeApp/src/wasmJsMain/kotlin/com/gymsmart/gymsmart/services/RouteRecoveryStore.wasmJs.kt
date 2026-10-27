package com.gymsmart.gymsmart.services

// Web (Wasm) todavía no tiene un LocationProvider real (ver 01-arquitectura.md §5), así que no hay
// nada que grabar ni que recuperar.
actual class RouteRecoveryStore {
    actual fun save(route: RecoverableRoute) {}
    actual fun load(): RecoverableRoute? = null
    actual fun clear() {}
}
