package com.gymsmart.gymsmart.services

// Desktop no tiene GPS real (ver DesktopLocationProvider), así que no hay nada que recuperar.
actual class RouteRecoveryStore {
    actual fun save(route: RecoverableRoute) {}
    actual fun load(): RecoverableRoute? = null
    actual fun clear() {}
}
