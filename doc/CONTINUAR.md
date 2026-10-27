# Continuar aquí — estado para la próxima sesión

> **Si eres una sesión nueva de Claude**: lee esto primero, es la foto rápida del estado actual.
> No es un historial — para el detalle de qué se hizo y por qué, mira
> [07-registro-sesiones.md](07-registro-sesiones.md) (bitácora completa) y
> [08-backlog-mejoras.md](08-backlog-mejoras.md) (cada hallazgo con su estado).
> Este documento se actualiza cada vez que se termina algo, no solo al final de la sesión.

**Última actualización**: 2026-10-27, sesión en curso.

**Nota del usuario sobre pruebas (2026-10-27)**: el usuario no va a probar nada en dispositivo real
hasta que haga falta con urgencia — lo probará todo de golpe cuando se terminen las mejoras de la Fase 5.
Esto es intencional, no un olvido: no insistir en que pruebe cada bloque, solo mantener la lista de
"Pendiente de verificación por el usuario" (más abajo) actualizada y completa para cuando llegue ese
momento.

---

## Dónde estamos

Proyecto en **Fase 5** (mejora de funcionalidades existentes) de 6, según
[00-roadmap.md](00-roadmap.md). Fases 1-4 completas. Dentro de la Fase 5, se sigue el "orden de
ataque" de [08-backlog-mejoras.md](08-backlog-mejoras.md):

| Bloque | Qué | Estado |
|---|---|---|
| 1 | Pérdida de datos y funciones muertas (RED-1, NUT-1, NUT-2, AUTH-1, DB-1) | ✅ Completo |
| 2 | Fiabilidad del conteo de kcal (NUT-3, NUT-4, WEAR-1, WEAR-2) | ✅ Completo |
| 3 | GPS como funcionalidad real (GPS-1, GPS-2, GPS-3, GPS-4) | ✅ Completo |
| 4 | Resiliencia transversal (cliente HTTP único, timeouts, 401 centralizado) | 🔄 **En curso** |
| 5 | Coste y seguridad (gate premium real, redimensionar imagen IA, caché OFF) | ⏳ Sin empezar |
| — | WEAR-3 (sync del wearable en segundo plano) — arquitectura más grande, no encaja en un bloque | ⏳ Sin empezar |

## Lo último que se hizo

Cerrado el bloque 3 (GPS) entero, incluyendo **GPS-1** (el cambio más invasivo de todo el backlog):

- `GpsTrackingService.kt` (nuevo, Android): foreground service con tipo `location` y una notificación
  simple ("Grabando ruta GPS"). Su único trabajo es mantener el proceso con prioridad elevada — no
  recolecta ubicación él mismo, eso sigue siendo `LocationService` desde `GpsScreen`. Sin esto, Android
  cortaba el GPS en cuanto la pantalla se apagaba.
- `BackgroundTrackingController.kt` (nuevo, común + 5 `actual`): arranca/para ese servicio desde
  `GpsScreen.kt` (código común, no puede llamar a APIs de Android directamente). No-op fuera de
  Android.
- Manifest: permisos nuevos `FOREGROUND_SERVICE_LOCATION` y `POST_NOTIFICATIONS`, declaración del
  `<service>`. Dependencia nueva `androidx.core:core-ktx` (para `NotificationCompat`).
- **Decisión de diseño tomada sin poder confirmarla con el usuario a tiempo**, y resuelta después: la
  notificación del foreground service es mínima (sin botón de "Parar", sin distancia/tiempo en vivo,
  `POST_NOTIFICATIONS` sin flujo de permiso en runtime). El 2026-10-27 el usuario confirmó: **esto queda
  como mejora futura, no se hace ahora** (anotado como GPS-1b en
  [08-backlog-mejoras.md](08-backlog-mejoras.md) 🟡 Bajos). No volver a preguntar por esto — solo
  retomarlo si el usuario lo pide explícitamente.
- Verificado con `:composeApp:compileKotlinJvm`, `:composeApp:compileKotlinJs`,
  `:composeApp:compileKotlinWasmJs` (los tres `BUILD SUCCESSFUL`). El `Service` y su `actual` de
  Android **no se han podido compilar en este entorno** (sin Android SDK) — es, de lejos, el cambio
  que más necesita probarse en un dispositivo/emulador real antes de confiar en él.
- Con esto, **el bloque 3 (GPS) queda completo**.
- Todo esto **sin commitear todavía** — pendiente de que el usuario lo confirme.

## Lo siguiente que hay que hacer

Decidir entre (no hay un orden obligatorio, son independientes):

1. **Bloque 4 — resiliencia transversal**: cliente HTTP único (hoy hay ~5 instancias de `AuthService`/
   `HttpClient` repartidas por pantallas — fue la causa raíz de la regresión R1 de la Fase 4, así que
   tiene buen impacto para evitar que se repita), timeouts (hoy no hay ninguno configurado, lo que
   interactúa mal con los cold starts de Render), manejo centralizado de 401.
2. **Bloque 5 — coste y seguridad**: gate premium real en el servidor (`isPremium()` existe pero no se
   llama desde ningún sitio), redimensionar la imagen antes de mandarla a la IA (ahorro de memoria/
   coste, cero pérdida de calidad), caché de búsquedas de Open Food Facts.
3. **WEAR-3** (sync del wearable en segundo plano): similar en invasividad a GPS-1 (`WorkManager`),
   mismo problema de no poder verificar Android aquí.

Ninguno de estos requiere decisiones de producto tan grandes como GPS-1 — se puede proceder sin
bloquear en una respuesta del usuario, salvo que él indique otra prioridad.

## Pendiente de verificación por el usuario (código Android no compilable aquí)

Este entorno no tiene Android SDK instalado. Estos cambios están hechos y con la lógica revisada, pero
**sin verificar con un build real**. El usuario ha decidido no probar nada hasta terminar toda la Fase 5
— esta lista es el checklist a usar entonces, de una sola vez, en IntelliJ con un dispositivo o emulador
real. Se mantiene al día conforme se añaden más cambios Android-only:

- `CookiesStorageFactory.android.kt` — cifrado de la cookie de sesión con `EncryptedSharedPreferences`.
- `HealthConnectProvider.kt` — migración a `aggregate()`.
- `RouteRecoveryStore.android.kt` — persistencia de la ruta GPS en curso.
- **`GpsTrackingService.kt` + `BackgroundTrackingController.android.kt`** (el más importante de
  probar: foreground service nuevo, permisos nuevos, notificación) — confirmar que una ruta grabada
  con la pantalla apagada sigue avanzando, y que la notificación aparece y desaparece correctamente.

## Decisiones que ya tomó el usuario (no re-preguntar)

- `backend/` y `frontend/` (código muerto) → eliminados, confirmado y hecho.
- `TrainingScreen`/`TrainingService` → se dejan como están por ahora, no se tocan (además se descubrió
  que ni siquiera es alcanzable desde el menú, así que no hay urgencia).
- CORS del servidor (solo `localhost`) → no se toca; el cliente web no está publicado en ningún dominio
  real, solo se prueba en local.
- Cifrado de cookie de sesión → sí, al menos en Android (hecho). JVM/Desktop/iOS/Web quedan sin cifrar,
  prioridad baja.
- Commits: el usuario los pide explícitamente cada vez ("comitea", "sigue"); nunca hacer commit sin que
  lo pida. Nunca se ha pedido push — todo sigue solo en local, no desplegado a Render.
- El usuario trabaja normalmente en IntelliJ, ejecutando Gradle por terminal — mismo flujo que se usa
  en estas sesiones para verificar cambios.
- Sistema de continuidad (`CLAUDE.md` + este archivo) pedido explícitamente por el usuario para poder
  cambiar de sesión sin perder el ritmo cuando el contexto se llene.
- GPS-1b (notificación con botón de "Parar", stats en vivo, permiso de notificaciones en runtime) →
  mejora futura, no se hace ahora (confirmado 2026-10-27).
- El usuario no probará nada en dispositivo real hasta terminar toda la Fase 5 — no insistir en pruebas
  por bloque, mantener la lista de verificación pendiente completa y al día.

## Descubrimiento útil para el futuro

`:composeApp:compileKotlinIosSimulatorArm64` **sí compila en este entorno sin Xcode** (el toolchain de
Kotlin/Native para iOS no necesita macOS para la fase de compilación, solo para enlazar/ejecutar la app
final). Al probarlo salió exactamente el error ya documentado en `01-arquitectura.md §5`
(`App()` sin argumentos en `MainViewController.kt`). Esto significa que una sesión futura que trabaje
en soporte real de iOS (Fase 6) **puede verificar bastante más de lo que se pensaba** sin necesitar un
Mac — solo la fase final de construir el `.xcframework`/ejecutar en simulador necesitaría eso.

## Reglas de trabajo específicas de esta fase

- Verificar cada cambio con `./gradlew :server:compileKotlin` y/o `:composeApp:compileKotlinJvm` antes
  de darlo por bueno (no necesitan Android SDK). Para cambios en `commonMain`, también sirven
  `:composeApp:compileKotlinJs`, `:composeApp:compileKotlinWasmJs` y (con las salvedades de arriba)
  `:composeApp:compileKotlinIosSimulatorArm64` — ninguno necesita Android SDK.
- Cuando un cambio toque código `androidMain`, marcarlo explícitamente como "sin verificar con build
  real" en la documentación — no dar a entender que está probado si no lo está.
- Si una decisión de diseño necesita confirmación del usuario y no se puede obtener a tiempo, tomar la
  opción más simple/estándar, avisar explícitamente que se tomó sin confirmar, y documentar cómo
  cambiarla si el usuario quiere otra cosa (no bloquear el trabajo esperando).
- Cada hallazgo cerrado se marca con ✅ en `08-backlog-mejoras.md` (y en `06-funcionalidades.md`/
  `03-seguridad.md` si también aparece ahí), con una frase de qué se hizo y dónde.
- Commit solo cuando el usuario lo pida; nunca push.
