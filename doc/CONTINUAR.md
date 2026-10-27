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

**Ese momento ya llegó (2026-10-27)**: se creó [09-plan-de-pruebas.md](09-plan-de-pruebas.md), el
checklist completo para esta ronda de pruebas (Desktop + Android, en local vía Tailscale, sin Render
todavía). El usuario está probando ahora mismo — si vuelve con resultados, revisar primero ese documento
para ver qué ítem corresponde a lo que reporte.

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
| 4 | Resiliencia transversal (cliente HTTP único, timeouts, 401 centralizado) | ✅ Completo |
| 5 | Coste y seguridad (gate premium real, redimensionar imagen IA, caché OFF) | ✅ Completo |
| — | WEAR-3 (refresco periódico de datos de la pulsera) | ✅ Completo |
| — | VAL-1/VAL-2 (validación de peso/altura/edad/ritmo) e IA-2/IA-3 (reproducibilidad y parseo del análisis) | ✅ Completo |

**Importante para la próxima sesión**: el usuario y Claude acordaron explícitamente (2026-10-27) que,
tras cerrar este backlog pequeño, **toca parar de tocar código y que el usuario pruebe la app de verdad**
en su máquina (IntelliJ, con Android SDK) antes de seguir. Hay bastante acumulado sin verificar en este
entorno (ver la sección de abajo). Si el usuario vuelve pidiendo seguir con más desarrollo sin haber
mencionado una prueba, está bien seguir — pero no hay que insistir en bloquear el trabajo esperando esa
prueba; es una recomendación acordada, no una regla dura. Lo que sí hay que evitar es seguir
amontonando más cambios Android-only sin probar los que ya hay, salvo que el usuario lo pida.

## Lo último que se hizo

**Backlog residual cerrado: VAL-1, VAL-2, IA-2, IA-3.** Tras cerrar los 5 bloques + WEAR-3, se acordó
con el usuario rematar estos cuatro hallazgos (rápidos, sin tocar Android) antes de parar a probar:

- **VAL-1 (validación de peso/altura/edad/ritmo)**: `ProfileScreen` y `WeightScreen` convertían con
  `toDoubleOrNull() ?: 0.0` y mandaban ceros en silencio si el campo estaba vacío o mal escrito. La
  validación correcta ya existía, pero solo en `OnboardingScreen`. Se extrajo a
  `validation/ProfileValidation.kt` (nuevo: `validateWeightKg`, `validateHeightCm`, `validateAge`,
  `validateGoalRate`) y las tres pantallas la reutilizan ahora. `WeightScreen` además ganó
  `keyboardType = Decimal` y un error visible en el propio campo (antes un valor inválido no hacía nada
  al pulsar "Guardar", sin explicación).
- **De rebote, se arregló también una instancia de ERR-1**: al tocar el diálogo de añadir peso de
  `WeightScreen` para VAL-1, se vio que se cerraba y vaciaba el campo sin comprobar si `saveProfile()`
  había tenido éxito. Ahora solo se cierra dentro de `onSuccess`; si falla, se queda abierto con el peso
  introducido y un error visible. El resto de instancias de ERR-1 sigue abierto.
- **VAL-2 (guardado de datos personales saltaba la validación del ritmo)**: guardar nombre/teléfono/etc.
  recalculaba la edad desde la fecha de nacimiento y, si procedía, reguardaba todo el perfil físico a
  escondidas con `goalRate.toDoubleOrNull() ?: 0.0` — si el usuario había elegido "perder peso" sin
  ritmo, ese reguardado derivado grababa un déficit de 0.0 en silencio. Ahora ese reguardado solo se hace
  si pasa la misma validación que el botón físico; si no, se omite en silencio (no se le muestra un error
  al usuario por un efecto derivado de un botón que no pulsó — simplemente ya no corrompe el dato).
- **IA-2 (análisis no reproducible)**: `OpenAIService` no fijaba `temperature` ni `response_format`.
  Ahora fija `temperature = 0.2` y `response_format: json_object`. El prompt ya no obliga a fingir
  certeza — puede avisar en `summary` cuando la foto no permite una estimación fiable.
- **IA-3 (parseo frágil)**: se decodifica ahora con un `Json { isLenient = true }` dedicado en vez del
  estricto por defecto, así que un número donde se esperaba texto ya no revienta el análisis entero.
- Los cuatro compilan limpio en los targets verificables aquí (`:composeApp:compileKotlinJvm`,
  `compileKotlinJs`, `compileKotlinWasmJs`, `:server:compileKotlin`) — **ninguno toca código Android**,
  así que no añaden nada a la lista de "sin verificar".
- Pendiente de que el usuario confirme el commit.

### Resumen de lo hecho en WEAR-3 (refresco de datos de la pulsera), ya commiteado antes de esto (commit `5419f2c`)

Arreglado con un enfoque deliberadamente más simple que el que se había esbozado en el hallazgo
original:

- El análisis original proponía mover la lectura de Health Connect a un `WorkManager` periódico con
  caché local — arquitectura más grande, Android-only, con el mismo problema de no poder verificarse en
  este entorno que tuvo GPS-1. Revisando el síntoma real descrito ("si abres la app por la mañana, el
  presupuesto se queda congelado todo el día" — es decir, obsoleto **mientras la pantalla está abierta**,
  no solo con la app cerrada), un refresco periódico dentro de la propia pantalla resuelve exactamente
  eso con mucho menos riesgo.
- Se implementó así: nueva constante `WEARABLE_POLL_INTERVAL_MS` (5 minutos) en `HealthDataProvider.kt`
  (común). `NutritionScreen.kt` separó la lectura de calorías activas en su propio `LaunchedEffect(
  hasWearable)` con un bucle `while(true) { leer; delay(...) }` en vez de una lectura única dentro del
  efecto que también carga perfil/comidas. `DashboardScreen.kt` convirtió su lectura única de
  pasos/calorías en el mismo patrón de bucle. El bucle se cancela solo al salir de la pantalla (ciclo de
  vida normal de `LaunchedEffect`), sin código nuevo de gestión de lifecycle.
- **Esto vive entero en `commonMain` — no se tocó ni una línea de código Android.** A diferencia de
  GPS-1/WEAR-1/WEAR-2, no hay ninguna limitación de "sin verificar con build real" aquí: se compiló y
  verificó al 100% con `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs` y
  `:server:compileKotlin` (los cuatro `BUILD SUCCESSFUL`).
- **Lo que esto no resuelve** (y donde sí compensaría el `WorkManager` si en el uso real hiciera falta):
  el número no se actualiza si la app está completamente cerrada o en segundo plano sin esa pantalla
  abierta. Se consideró un caso mucho menos común que el síntoma real descrito, así que no se implementó
  — queda anotado por si el usuario nota que no es suficiente.
- `ProfileScreen.kt` también lee pasos/calorías, pero ahí es solo un dato informativo al editar el
  perfil (no alimenta ningún presupuesto que se vea todo el día) — se dejó como lectura única a
  propósito, para no añadir complejidad donde no cambia nada visible.

### Resumen de lo hecho en el bloque 5 (coste y seguridad), ya commiteado antes de esto (commit `45879a9`)

Cerrado el bloque 5 (coste y seguridad) entero: SEC-1, IA-1, OFF-1. Todos son cambios de servidor,
verificados con `:server:compileKotlin` (sin limitación de Android SDK en ninguno de los tres).

- **SEC-1 (gate premium real)**: `bodyAnalysisRoute` ahora comprueba `profileService.isPremium(session.
  userId)` y responde `403 Forbidden` si no lo es, antes de llamar a OpenAI. Antes `isPremium()` existía
  pero no se llamaba desde ningún sitio — cualquier usuario logueado podía gastar créditos de OpenAI sin
  límite con una petición directa a la API (el control de cliente no protege nada real). **Sigue sin
  haber** cuota diaria/rate limit para usuarios premium — fuera del alcance pedido, candidato a futuro.
- **IA-1 (redimensionar imagen antes de OpenAI)**: `OpenAIService.resizeIfNeeded()` decodifica la imagen
  (`javax.imageio`, sin dependencias nuevas) y la reescala a máximo 1024px de lado largo antes de
  mandarla a OpenAI (que de todas formas reescala a ≤768px internamente, así que la resolución extra se
  descartaba sin ganancia). Si falla la decodificación, se manda la original en vez de romper el
  análisis. **Importante**: reduce el payload hacia OpenAI, no el payload cliente→servidor — el cliente
  sigue mandando la imagen completa al endpoint (arreglarlo necesitaría tocar el `ImagePicker` de cada
  plataforma, con el añadido de que solo Android lo tiene implementado hoy, ver IOS-1).
- **OFF-1 (caché de Open Food Facts)**: `FoodService` tiene ahora una caché en memoria con TTL de 24h
  (clase `TtlCache` interna, sin dependencias nuevas) para `searchFood()` y `lookupBarcode()`. Un
  resultado vacío o un fallo nunca se cachea (podría ser un fallo transitorio de OFF, no "no existe").
  Reduce el riesgo de que OFF bloquee la única IP de Render por exceso de peticiones repetidas.
- Al implementar SEC-1/IA-1/OFF-1 no hubo errores de compilación — los tres pasaron a la primera.

### Resumen de lo hecho en el bloque 4 (resiliencia transversal), ya commiteado antes de esto (commit `1199fd6`)

- **HTTP-1 (cliente único)**: había 5 instancias de `AuthService()`/`HttpClient` (una por pantalla:
  `NavGraph`, `WeightScreen`, `DashboardScreen`, `ProfileScreen`, `SubscriptionScreen`). Ahora `NavGraph`
  crea el único `AuthService()` de toda la app y lo pasa como parámetro a las 4 pantallas que antes
  creaban el suyo. `ProfileScreen` además reutiliza el `profileService` ya compartido de `NavGraph` en
  vez de crear uno nuevo. Las pantallas que construyen un servicio de dominio propio (`WeightService`,
  `SubscriptionService`) lo siguen haciendo, pero a partir de `authService.client` compartido.
- **RED-2 (timeouts + motor de red determinista)**: se quitó `ktor-client-android` del classpath de
  Android (quedaba junto a `ktor-client-okhttp`, y cuál cargaba Ktor no era determinista). Se instaló
  `HttpTimeout` en el `HttpClient` único: `requestTimeoutMillis = 45_000`, `connectTimeoutMillis =
  15_000`, `socketTimeoutMillis = 45_000` — generosos a propósito por los cold starts de Render.
  **Ojo**: el análisis corporal por IA ahora también tiene este límite; si en uso real se corta antes de
  que OpenAI responda, es un ajuste de una línea (timeout específico para esa llamada).
- **RED-3 (401 centralizado)**: `AuthService` tiene un `HttpResponseValidator` que detecta cualquier 401
  fuera de las rutas `/auth` (que gestionan su propio 401 como parte del flujo normal) y expone
  `sessionExpired: StateFlow<Boolean>`. `NavGraph` lo observa y, si se activa, limpia la pila de
  navegación y manda a `LoginScreen` con un aviso ("Tu sesión caducó, inicia sesión de nuevo."). Antes,
  una sesión caducada a mitad de uso dejaba cada pantalla con datos vacíos sin explicación.
- **Sigue abierto a propósito** (menor impacto ahora que el 401 ya es centralizado): el almacenamiento de
  cookies sigue guardando solo `nombre=valor`, sin expiración/dominio/path/`secure` — es un cambio más
  grande (afecta a los 5 `CookiesStorage` por plataforma) que no se hizo en este bloque.
- Verificado con `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs` y
  `:server:compileKotlin` (todos `BUILD SUCCESSFUL`); también se repitió
  `:composeApp:compileKotlinIosSimulatorArm64` y sigue fallando solo por el error ya documentado de
  `MainViewController.kt` (no por este cambio). **Sin verificar con build real de Android** — el cambio
  de motor de red (quitar `ktor-client-android`) es justo el tipo de cosa que conviene comprobar antes de
  confiar en los tiempos de carga reales en un dispositivo.
- Todo esto, junto con el cierre del bloque 3 (GPS, ver detalle más abajo), **ya está commiteado**.

### Resumen de lo hecho en el bloque 3 (GPS), ya commiteado antes de esto

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
- Con esto, **el bloque 3 (GPS) queda completo**. Ya commiteado (commit `ff98ae7`), igual que el
  bloque 4 (commit `1199fd6`) y el bloque 5 (commit `45879a9`).

## Lo siguiente que hay que hacer

**Recomendación acordada con el usuario (2026-10-27): parar aquí y que pruebe la app de verdad** antes
de seguir tocando código — ver el aviso al principio de este documento y el checklist de abajo. Si el
usuario pide seguir con más desarrollo sin haber probado, no bloquear por eso, pero no hay que ofrecerse
a seguir acumulando cambios Android-only por iniciativa propia.

Si se retoma el trabajo de código (ahora o después de probar), lo que queda es backlog residual por
severidad (ver `08-backlog-mejoras.md`), sin orden obligatorio:

- GPS-5/GPS-6 (filtros de velocidad/precisión sin comprobar `hasSpeed()`/`hasAccuracy()`; duración que
  incluye el tiempo parado) — toca `GpsScreen.kt`, común, sí verificable aquí.
- PERM-1 (callbacks de permisos de cámara/GPS que pueden perderse en una recomposición de
  `MainActivity.kt` — Android-only, sin poder verificar aquí).
- El resto de 🟡 Bajos: seguridad/configuración menor (`usesCleartextTraffic`, `allowBackup`), datos de
  alimentos (paginación, `lc=es`), análisis IA sin persistir histórico, código muerto/inconsistencias
  menores, UI-1/UI-2.
- Cabos sueltos de Fase 2/3 (fuera de la Fase 5): logging del cliente, estrategia de DI mínima, y
  decidir cuándo desplegar todo lo acumulado a Render (hoy sigue todo solo en local).

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
- **Cambio de motor de red en Android** (se quitó `ktor-client-android`, queda solo `ktor-client-okhttp`)
  — confirmar que login, carga de pantallas y, sobre todo, un cold start de Render (primera petición
  tras inactividad del servidor) siguen funcionando con normalidad y que el timeout de 45s es suficiente
  en la práctica.
- **401 centralizado**: forzar una sesión caducada (borrar la cookie en el servidor o esperar a que
  expire) y confirmar que la app manda sola al login con el aviso, en vez de quedarse con pantallas
  vacías.

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
- Acordado (2026-10-27): antes de entrar en Fase 6 (funcionalidades nuevas) hace falta que el usuario
  pruebe la app de verdad — por eso, una vez cerrado el backlog pequeño de la Fase 5, toca parar de
  acumular más cambios sin verificar y dejar que pruebe, en vez de seguir ofreciendo más desarrollo.

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
