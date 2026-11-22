# Revisión técnica independiente — segunda pasada

**Fecha**: 2026-10-29 · **Autor**: sesión de Claude sin memoria de las sesiones anteriores
**Encargo**: revisar de arriba a abajo el proyecto y la documentación de las Fases 1-5, verificar por
segunda vez lo que se dio por arreglado, y reportar **solo lo que está mal y lo que se puede hacer
mejor**. Sin especulaciones: cada hallazgo lleva archivo, línea y el razonamiento que lo sostiene.

> **Qué NO es este documento**: no es un plan de trabajo ni una lista de tareas aprobada. Es el
> resultado de una auditoría. No se ha tocado ni una línea de código del proyecto.

---

## 0. Método y alcance

> **Nota sobre la elaboración de este informe**: se hizo en dos pasadas. La primera cubrió la
> documentación de continuidad, todo el servidor y las pantallas principales del cliente. La segunda
> (tras una revisión crítica del alcance) cubrió lo que faltaba: 6 documentos que no se habían leído
> (`00-roadmap.md`, `01-arquitectura.md`, `04-produccion-playstore.md`, `06-funcionalidades.md`,
> `07-registro-sesiones.md`, `README.md`), las 9 pantallas que no se habían leído, todos los `actual`
> de JVM/Desktop, los modelos, y los dos targets de compilación que no se habían verificado. La segunda
> pasada encontró 8 bugs nuevos, resolvió 2 afirmaciones de la documentación que estaban sin confirmar,
> y **corrigió 2 errores de la primera pasada** (marcados con ⚠️ **CORRECCIÓN** donde corresponde).

**Leído por completo**: los 11 documentos de `doc/`, `CLAUDE.md`, los 4 `build.gradle.kts`,
`libs.versions.toml`, `settings.gradle.kts`, `gradle.properties`, `AndroidManifest.xml`, `Dockerfile`,
`logback.xml`, los 21 archivos del servidor, y el cliente completo: `commonMain` (las 18 pantallas, los
13 servicios, los 10 modelos, tema y navegación), `androidMain` completo, y los `actual` de
jvm/js/wasm/ios.

**Verificado por ejecución**:
- `:server:compileKotlin` + `:composeApp:compileKotlinJvm` → **exit 0**.
- `:composeApp:compileKotlinJs` + `:composeApp:compileKotlinWasmJs` → **exit 0**.
- `:composeApp:compileKotlinIosSimulatorArm64` → **FALLA**, con exactamente los 3 errores que
  `01-arquitectura.md §5` documenta (`MainViewController.kt:5:57`, `App()` sin argumentos). **La
  afirmación de la documentación queda confirmada por ejecución.**
- Warnings del build: solo 2, y ambos son del plugin (`'org.jetbrains.kotlin.multiplatform' plugin
  deprecated compatibility with Android Gradle plugin`), ninguno de código propio. El criterio de
  salida de la Fase 3 ("build limpio, sin warnings relevantes") **se cumple** en lo que a warnings se
  refiere.
- `grep` sistemático para confirmar/refutar afirmaciones concretas (transformadores de sesión, `PRAGMA`,
  código supuestamente muerto, instancias de `HttpClient`, `println`, limpieza de cookies).

**No verificable en este entorno** (sin Android SDK, igual que las sesiones previas): nada de
`androidMain` se ha podido compilar. Los hallazgos sobre código Android se sostienen en lectura de
código y en el contrato documentado de las APIs implicadas, y se marcan como tales.

**Veredicto general**: el trabajo de las Fases 1-5 es real y de buena calidad. Los arreglos que la
documentación da por hechos **están efectivamente en el código** (los verifiqué uno a uno, ver §5).
La documentación es inusualmente honesta — marca sus propias limitaciones y lo que quedó a medias.

Dicho eso, esta segunda pasada encuentra **tres cosas que no estaban en ningún documento y que importan
más que casi todo el backlog pendiente**, y un puñado de arreglos de Fase 5 con agujeros. Lo resumo así:

| | Cuántos | Lo más grave |
|---|---|---|
| 🔴 Hallazgos nuevos críticos | 2 | Cookie de sesión sin firmar → suplantación de cualquier usuario |
| 🟠 Arreglos de Fase 5 incompletos | 6 | HTTP-1 dejó dos `HttpClient` más; GPS-1 solo cubre una de las dos pantallas con GPS |
| 🟠 Bugs nuevos medios | 17 | `START_STICKY` deja notificación de GPS zombi; cerrar sesión sin red no cierra sesión |
| 🟡 Bugs nuevos bajos | 12 | `formatDouble` muestra "8.19" donde debería "8.20" |
| 📄 Inexactitudes en la doc | 8 | `01-arquitectura.md` describe como actual el estado previo a las Fases 2-5 |
| 📋 Desvíos respecto al plan | 2 | La Fase 4 se cerró ✅ sin su entregable declarado (tests) |
| 🧹 Código/dependencias muertas nuevas | 15 | `jakarta.mail`, `getLocation()`, `GpsData` (×2), modelos de Stripe |

---

## 1. 🔴 Críticos nuevos (no aparecen en ningún documento)

### SEC-N1 — La cookie de sesión no está firmada ni cifrada: cualquiera puede suplantar a cualquier usuario

**Dónde**: `server/.../Application.kt:29-35`

```kotlin
install(Sessions) {
    cookie<UserSession>("gymsmart_session") {
        cookie.httpOnly = true
        cookie.maxAgeInSeconds = 60 * 60 * 24 * 7
        cookie.path = "/"
    }
}
```

**El problema**: en Ktor, una sesión de tipo `cookie<T>` **sin transformador** serializa el objeto
completo en el valor de la cookie y se lo cree sin verificar nada al recibirlo. No hay firma (HMAC) ni
cifrado. Lo confirmé con grep en todo el repo: no existe ni una aparición de `transform`,
`SessionTransportTransformerMessageAuthentication`, `SessionTransportTransformerEncrypt`, ni ninguna
clave de firma/sesión en el código ni en el `.env`.

`UserSession` es `data class UserSession(val userId: String, val email: String, val name: String)`
(`server/.../model/User.kt`). Es decir: el contenido entero de la autenticación del usuario viaja en
texto plano en una cookie que el cliente controla.

**Por qué es explotable de verdad, y no solo teórico**: para suplantar a alguien hace falta su `userId`.
Y el `userId` **no es secreto**: `GET /gps/routes/community` (`GpsRoute.kt:131-136`) devuelve una lista
de `GpsRouteResponse`, y ese modelo incluye `userId` (`GpsService.kt:91`, campo `userId = row[1]`). Es
decir, cualquier usuario registrado puede listar las rutas públicas, leer los `userId` de otras personas,
fabricarse una cookie `gymsmart_session` con ese `userId` y pasar a ser esa persona: leer y borrar sus
comidas, su perfil, su historial de peso, sus rutas privadas, y gastar su cuota premium de OpenAI.

**Impacto hoy** (app de uso personal, un usuario real): bajo en la práctica, porque no hay otros
usuarios a los que suplantar. **Impacto el día que haya una segunda cuenta**: es un bypass total de
autenticación. Deja sin sentido el resto del trabajo de seguridad de la Fase 2 (el IDOR de nutrición
se arregló filtrando por `session.userId`… pero `session.userId` es un dato que el atacante elige).

**Nota importante**: esto no es un hallazgo de "si algún día vas a producción". El servidor está hoy
desplegado y accesible en `https://gymsmart-server.onrender.com` (`AppConfig.kt`), con las rutas
públicas en internet.

**Arreglo**: añadir un transformador de autenticación de mensaje con una clave en variable de entorno:

```kotlin
val sessionKey = hex(System.getenv("SESSION_SIGN_KEY"))  // nueva var de entorno
cookie<UserSession>("gymsmart_session") {
    cookie.httpOnly = true
    cookie.maxAgeInSeconds = 60 * 60 * 24 * 7
    cookie.path = "/"
    cookie.secure = true                                  // ver SEC-N7
    transform(SessionTransportTransformerMessageAuthentication(sessionKey))
}
```

Es un cambio de 3 líneas + una variable de entorno. Efecto secundario esperado y aceptable: invalida
todas las sesiones existentes una vez (habrá que volver a iniciar sesión). Alternativa más fuerte:
`SessionTransportTransformerEncrypt` (cifra además de firmar), o mover la sesión a servidor con
`SessionStorage`. Para este proyecto, firmar es suficiente y es el mínimo imprescindible.

---

### SEC-N2 — El gate premium de SEC-1 es franqueable: cualquier número de tarjeta de 16 dígitos concede premium

**Dónde**: `server/.../services/StripeService.kt:32-37` + `routes/SubscriptionRoute.kt:85-88` +
`composeApp/.../screens/SubscriptionScreen.kt:335`

```kotlin
val testToken = when (cardNumber.replace(" ", "")) {
    "4242424242424242" -> "tok_visa"
    "4000000000000002" -> "tok_chargeDeclined"
    "4000000000009995" -> "tok_chargeDeclinedInsufficientFunds"
    else -> "tok_visa" // fallback        // ← cualquier otra tarjeta = pago correcto
}
```

**El problema**: el `else -> "tok_visa"` significa que **cualquier** número de tarjeta que no sea uno de
los dos de rechazo produce un cargo de test que Stripe aprueba (`status == "succeeded"`), tras lo cual
`SubscriptionRoute.kt:88` llama a `activatePremium()`. El cliente solo exige
`cardNumberRaw.length == 16` (`SubscriptionScreen.kt:335`). Teclear 16 dígitos cualesquiera concede
premium.

**Por qué esto importa ahora y no antes**: en la Fase 5 se cerró SEC-1 añadiendo
`if (!profileService.isPremium(session.userId)) → 403` en `BodyAnalysisRoute.kt:29-31`. Ese arreglo es
correcto y necesario, pero su valor real es "nadie gasta mis créditos de OpenAI sin pagar". Con este
`fallback`, el camino para obtener premium sin pagar sigue abierto y es trivial. El `08-backlog-mejoras.md`
lista el formulario de tarjeta como *"el mejor validado de la app (irónicamente, el del pago que no
funciona)"* — eso describe la validación de **formato** del formulario, pero pasa por alto que la
**decisión de cobrar** siempre dice sí.

**Matiz honesto**: en modo test de Stripe no se mueve dinero real, así que esto no es un fraude
económico hoy. El problema es que el único control que protege la cuenta de OpenAI es franqueable, y que
el día que se ponga una clave `live` el comportamiento cambia de golpe (`tok_visa` no existe en live →
todos los pagos fallarían, ver §4 FUNC-N2).

**Arreglo mínimo mientras el pago no sea real**: cambiar el `else` por un rechazo explícito
(`else -> return false`), de modo que solo las tres tarjetas de test conocidas funcionen. Así el gate
premium pasa a significar algo durante las pruebas.

---

## 2. 🟠 Arreglos de la Fase 5 que están incompletos

Estos cinco están marcados ✅ en `08-backlog-mejoras.md`. El arreglo existe y funciona en el caso
principal, pero queda un camino sin cubrir que la documentación no menciona.

### INC-1 — HTTP-1: queda un segundo `HttpClient`, sin cookies, sin timeouts y sin el validador de 401

**Dónde**: `composeApp/.../screens/NutritionScreen.kt:76-78`

```kotlin
private val httpClient = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}
```

Es un cliente a nivel de archivo (no de pantalla), y se usa en dos sitios:
- `NutritionScreen.kt:181` → `GET /food/barcode/$code` (escanear código de barras)
- `NutritionScreen.kt:611` → `GET /food/search?q=` (buscar alimento)

**Por qué contradice la documentación**: `08-backlog-mejoras.md` (HTTP-1) y `CONTINUAR.md` afirman que
*"`NavGraph` crea el único `AuthService()` de toda la app"* y que había *"5 instancias"* que ahora son
una. En realidad siguen existiendo **dos** `HttpClient` en el cliente. La regla 10 de
`02-convenciones.md` lo dice con precisión: *"Mientras exista más de un `HttpClient` en el proyecto,
este riesgo sigue vivo"*. Sigue vivo.

**Consecuencias concretas, no teóricas**:
1. **Sin `HttpTimeout`**. El arreglo RED-2 instaló timeouts de 45 s en el cliente de `AuthService`, pero
   no en este. Buscar un alimento o escanear un código contra un Render en cold start **se queda
   colgado indefinidamente**, que es exactamente el síntoma que RED-2 pretendía eliminar. Y el ítem 8 de
   `09-plan-de-pruebas.md` ("activa el modo avión… no debería quedarse cargando para siempre") pasará
   si se prueba en Nutrición → la búsqueda de alimentos es justo el camino que no tiene el límite.
2. **Sin cookies ni validador de 401**. Hoy funciona por un acoplamiento accidental: `/food/*` no exige
   sesión (ver SEC-N3). El día que se le ponga auth a `/food/*` —que es lo que habría que hacer—
   la búsqueda de alimentos se romperá en silencio, **exactamente igual que pasó con R1** en el análisis
   corporal. La lección está escrita en `02-convenciones.md` regla 10; el código todavía no la cumple.

**Arreglo**: pasar `nutritionService`/`authService.client` a estas dos llamadas (la pantalla ya recibe
`nutritionService`, que lleva dentro el cliente compartido) y borrar el cliente del archivo.

### INC-2 — VAL-1: la validación rechaza la coma decimal, y el teclado español ofrece coma

**Dónde**: `composeApp/.../validation/ProfileValidation.kt:11-14`

```kotlin
fun validateWeightKg(raw: String): String? {
    val value = raw.toDoubleOrNull()
    return if (value == null || value !in 30.0..300.0) "Introduce un peso válido (30–300 kg)" else null
}
```

`"75,5".toDoubleOrNull()` devuelve `null`, así que la coma produce el mensaje *"Introduce un peso válido
(30–300 kg)"*. Eso es una mejora clara frente a mandar `0.0` en silencio, y el ítem 3 de
`09-plan-de-pruebas.md` lo describe como el comportamiento esperado, así que **es intencional**.

**Pero es un mal resultado de producto**: en `WeightScreen.kt:442` y `ProfileScreen.kt:371` se fija
`keyboardType = KeyboardType.Decimal`. En un Android con configuración regional española, ese teclado
ofrece la **coma** como separador decimal. El usuario teclea lo que el teclado le ofrece, y la app le
responde que 75,5 kg no es un peso válido entre 30 y 300 — un mensaje que no describe el problema real.

**Arreglo** (una línea, en un solo sitio, y arregla los tres formularios a la vez):

```kotlin
private fun String.parseDecimal(): Double? = trim().replace(',', '.').toDoubleOrNull()
```

y usarlo en `validateWeightKg`/`validateHeightCm`. Ojo: también habría que normalizar la coma antes del
`weight.toDouble()` de `ProfileScreen.kt:462` y el `inputWeight.toDouble()` de `WeightScreen.kt:464`,
porque hoy esas conversiones se apoyan en que la validación ya rechazó la coma.

### INC-3 — VAL-2 protege un camino de servidor que nunca se ejecuta (ver BUG-N2)

VAL-2 arregló el reguardado derivado del perfil en el cliente, y ese arreglo es correcto. Pero el motivo
por el que ese reguardado existe —"la fecha de nacimiento cambia, así que hay que recalcular la edad"—
se apoya en un servidor que **nunca consigue parsear la fecha de nacimiento** (BUG-N2). El arreglo de
cliente es válido; el diseño alrededor está roto desde antes y nadie lo había detectado.

### INC-4 — SEC-1: el 403 nuevo llega al usuario como "error genérico"

**Dónde**: `composeApp/.../services/WeightService.kt` (`analyzeBody`) + `screens/WeightScreen.kt:202-203`

`analyzeBody()` hace `.body<BodyAnalysisResponse>()` sin mirar el status. Cuando SEC-1 responde
`403 Forbidden` con cuerpo `{"error": "Se requiere suscripción premium"}`, la deserialización a
`BodyAnalysisResponse` (5 `String` obligatorios) lanza excepción, que `WeightScreen.kt:202` captura y
convierte en:

```kotlin
analysisError = "No se pudo completar el análisis. Inténtalo de nuevo."
```

Resultado: "no eres premium", "OpenAI falló", "se cayó la red" y "la foto no se pudo parsear" producen
**el mismo mensaje**, y uno de ellos invita a reintentar algo que nunca va a funcionar. Combinado con
IA-4 (que sigue abierto y devuelve los errores con HTTP 200), el análisis por IA no tiene hoy ningún
camino de error distinguible.

**Arreglo**: comprobar `response.status` en `analyzeBody` y devolver un tipo que distinga al menos
403 (premium) del resto.

### INC-6 — GPS-1 solo cubre una de las dos pantallas que consumen GPS

**Dónde**: `composeApp/.../screens/RouteDetailScreen.kt:57-77`

`GpsScreen` arranca el foreground service antes de recolectar ubicaciones (`GpsScreen.kt:69`), que es
el arreglo de GPS-1. Pero **`RouteDetailScreen` también consume ubicación en directo** y no lo hace:

```kotlin
LaunchedEffect(isFollowing) {
    if (!isFollowing || routePoints.isEmpty()) return@LaunchedEffect
    …
    locationProvider.getLocationUpdates().collect { userPoint ->   // sin backgroundTracking.start()
```

Es la funcionalidad "Iniciar seguimiento": seguir una ruta guardada mientras corres, comparando tu
posición con los puntos de la ruta para calcular el progreso. Es, por definición, una actividad al aire
libre y de duración larga — exactamente el mismo escenario que motivó GPS-1. Sin foreground service,
Android cortará o estrangulará las actualizaciones en cuanto se apague la pantalla, y el progreso de la
ruta se congelará. No hay ni `rememberBackgroundTrackingController()` ni ningún `DisposableEffect` en
todo el archivo (verificado con grep).

El hallazgo GPS-1 y la sección de GPS de `06-funcionalidades.md` hablan solo de "la grabación", así que
esta segunda pantalla nunca entró en el alcance. No es un error del arreglo, es un alcance incompleto.

**Arreglo**: el mismo patrón de `GpsScreen` (3 líneas: `rememberBackgroundTrackingController()`,
`start()` al empezar a seguir, `stop()` + `DisposableEffect` al salir).

⚠️ Sin verificar con build real.

### INC-5 — OFF-1: la caché no protege de lo que decía protegerse, porque `/food/*` es público

Ver SEC-N3. La caché de 24 h es correcta y está bien implementada (incluido el acierto de **no** cachear
resultados vacíos), pero su objetivo declarado —*"reducir el riesgo de que OFF bloquee la única IP de
Render"*— no se cumple mientras cualquiera sin cuenta pueda generar consultas únicas ilimitadas.

---

## 3. 🟠 Bugs nuevos de severidad media

### BUG-N1 — `GpsTrackingService` usa `START_STICKY`: deja una notificación "Grabando ruta GPS" zombi

**Dónde**: `composeApp/src/androidMain/.../services/GpsTrackingService.kt:23-26`

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(NOTIFICATION_ID, buildNotification())
    return START_STICKY
}
```

**El problema**: `START_STICKY` le dice a Android *"si matas este servicio, vuelve a arrancarlo"*. Pero
este servicio, por diseño documentado, **no recolecta ubicación** — su único trabajo es sostener la
prioridad del proceso mientras `GpsScreen` recolecta vía `LocationService`. Si Android mata el proceso
(que es el escenario central para el que se construyó `RouteRecoveryStore`, GPS-3), lo reinicia con
`intent == null`, el servicio vuelve a hacer `startForeground` y muestra **"Grabando ruta GPS"** de forma
indefinida, mientras no se está grabando nada: no hay `GpsScreen` compuesta, no hay `collect` de
ubicaciones, no se acumulan puntos.

El usuario ve una notificación persistente que afirma que se está grabando una ruta, sin forma de pararla
desde la propia notificación (GPS-1b dejó el botón "Parar" como mejora futura) y sin que la app esté
haciendo nada. La única vía de salida es abrir la app y entrar y salir de la pantalla de GPS (para que
corra el `onDispose` de `GpsScreen.kt:44-46`) o forzar el cierre.

**Se cruza justo con la prueba más importante del plan**: el ítem 4 de la sección 6 de
`09-plan-de-pruebas.md` pide cerrar la app desde el gestor de recientes mientras se graba. Ese es
precisamente el escenario que dispara esto.

**Arreglo**: `return START_NOT_STICKY`. Un servicio que solo sostiene prioridad para un trabajo que vive
en la UI no debe sobrevivir a la muerte del proceso; la ruta ya se recupera por `RouteRecoveryStore`,
que es el mecanismo correcto.

⚠️ Sin verificar con build real (sin Android SDK aquí) — pero el razonamiento es sobre el contrato
documentado de `START_STICKY`, no sobre comportamiento observado.

### BUG-N2 — La edad derivada de la fecha de nacimiento nunca funciona: formatos incompatibles cliente/servidor

**Dónde**: `composeApp/.../screens/ProfileScreen.kt:230-236` (escritura) vs
`server/.../routes/ProfileRoute.kt:99-110` (lectura)

El cliente guarda la fecha de nacimiento como **8 dígitos sin separadores**. En
`ProfileScreen.kt:230` el `onValueChange` hace `it.filter { c -> c.isDigit() }.take(8)`, y ese es el
valor que viaja: `authService.updatePersonalData(userName, userPhone, userCountry, userBirthDate)`
(`ProfileScreen.kt:270`) → `UPDATE users SET … birth_date = ?` con, por ejemplo, `"01011990"`. Las barras
solo existen al pintar, vía `BirthDateVisualTransformation` (`ProfileScreen.kt:49-75`).

El servidor lo lee así:

```kotlin
val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
Period.between(LocalDate.parse(bd, formatter), LocalDate.now()).years
} catch (_: Exception) { req.age }
```

`LocalDate.parse("01011990", ofPattern("dd/MM/yyyy"))` lanza `DateTimeParseException` → el `catch` cae
siempre a `req.age`.

**Consecuencia**: `resolvedAge` **es siempre `req.age`**. Toda la lógica de "la edad se recalcula sola
desde la fecha de nacimiento" es código muerto en la práctica. Lo confirmé con grep: `ProfileScreen` es
el único escritor de `birthDate` en todo el cliente, y `ProfileRoute.kt:104` el único lector en el
servidor; no hay ningún sitio que lo escriba con barras.

**Dónde se nota**: el ítem 4 de la sección 3 de `09-plan-de-pruebas.md` dice *"Si además cambiaste la
fecha de nacimiento, la edad del perfil físico se actualiza sola por detrás"*. Se actualiza, pero por el
cálculo **del cliente** (`ProfileScreen.kt:276-284`, que sí parsea los 8 dígitos correctamente), no por
el del servidor. El del servidor nunca ha funcionado.

**Arreglo**: decidir un formato único. Lo más limpio es ISO (`yyyy-MM-dd`) en el almacenamiento y la
API, y dejar la presentación `DD/MM/AAAA` solo en la `VisualTransformation`. Si se prefiere no migrar
datos existentes, el servidor puede aceptar los 8 dígitos (`ofPattern("ddMMyyyy")`).

### BUG-N3 — `resolvedAge` se guarda sin revalidar, saltándose la validación de rango que acaba de pasar

**Dónde**: `server/.../routes/ProfileRoute.kt:90-116`

El orden es: se valida `req.age !in 14..100` → se calcula `resolvedAge` desde la fecha de nacimiento →
se guarda **`resolvedAge`**, no `req.age`. Pero `resolvedAge` no pasa por ninguna validación. Si
`birth_date` contuviera una fecha de 2024, `Period.between(...).years` daría `1` y se guardaría `age = 1`
en `user_profiles`, pese a que el endpoint acaba de rechazar cualquier `req.age` fuera de 14-100.

Hoy es inalcanzable **solo por casualidad**, porque BUG-N2 hace que `resolvedAge == req.age` siempre. Es
decir: arreglar BUG-N2 activa BUG-N3. Hay que arreglar los dos juntos.

Con `age = 1`, `computeTargets` da un BMR inflado (`- 5.0 * 1`) y por tanto un objetivo de calorías
incorrecto para todo el día. Nótese que el cliente (`ProfileScreen.kt:292`) sí comprueba
`calculatedAge in 14..100` — la validación existe en el cliente pero no en el servidor, que es donde
tiene que estar.

**Arreglo**: validar `resolvedAge in 14..100` después de calcularlo, y si no cumple, caer a `req.age`
(ya validado) en vez de guardar el valor derivado.

### BUG-N4 — El diálogo de "Añadir peso" no hace absolutamente nada si el perfil no se pudo cargar

**Dónde**: `composeApp/.../screens/WeightScreen.kt:467`

```kotlin
val profile = currentProfile?.profile ?: return@launch
```

`currentProfile` se rellena en el `LaunchedEffect` de apertura (`WeightScreen.kt:88`). Si esa carga
falla —Render en cold start, servidor local apagado, sesión recién caducada— `currentProfile` queda
`null`. Entonces el usuario escribe un peso válido, pulsa "Guardar", y **no pasa nada**: el diálogo sigue
abierto, no hay error, no hay spinner, no hay mensaje.

Esto es la misma clase de fallo que VAL-1 vino a arreglar en esta misma pantalla (*"antes un peso vacío
o mal escrito simplemente no hacía nada al pulsar el botón, sin ninguna explicación visible"*). El
arreglo cubrió el valor inválido y el fallo de guardado, pero dejó esta precondición sin cubrir, y es la
que se dispara justo en el escenario de red mala.

**Se cruza con el plan de pruebas**: el ítem 2 de la sección 4 pide probar con el servidor apagado,
esperando *"el diálogo se queda abierto… y aparece un mensaje de error"*. Si el servidor estaba apagado
**ya al abrir la pantalla**, no aparecerá ningún mensaje — el botón no hará nada. Si se apaga **después**
de que la pantalla cargara, sí funcionará como dice el plan. Dos resultados distintos para lo que el
plan presenta como una sola prueba.

**Arreglo**: sustituir el `?: return@launch` por un error visible
(`inputWeightError = "No se pudieron cargar tus datos. Reintenta."`).

### BUG-N5 — Guardar un peso correctamente puede mostrar "No se pudo guardar"

**Dónde**: `composeApp/.../screens/WeightScreen.kt:482-494`

```kotlin
profileService.saveProfile(request)
    .onSuccess {
        entries = weightService.getHistory()   // ← puede lanzar
        inputWeight = ""
        …
        showDialog = false
    }
    .onFailure { inputWeightError = "No se pudo guardar. Inténtalo de nuevo." }
} catch (e: Exception) {
    inputWeightError = "No se pudo guardar. Inténtalo de nuevo."
}
```

`WeightService.getHistory()` no está envuelto en `runCatching` (a diferencia de la carga inicial de la
línea 87, que sí lo está desde el arreglo de R2). Si el guardado va bien pero la recarga del historial
falla —muy posible: dos peticiones seguidas, la segunda puede pillar un corte—, la excepción sube al
`catch` de la línea 492 y el usuario ve *"No se pudo guardar"* **después de que el peso se haya guardado
correctamente**. Además el diálogo se queda abierto con el peso dentro, invitando a reintentar algo que
ya está hecho.

No causa datos corruptos (el servidor hace `UPDATE` del registro del mismo día, así que reintentar es
idempotente), pero es un mensaje que miente sobre lo que pasó.

**Arreglo**: `entries = runCatching { weightService.getHistory() }.getOrElse { entries }` dentro del
`onSuccess`, y cerrar el diálogo igualmente.

### BUG-N6 — ERR-1 confirmado abierto en el sitio que más se va a probar: guardar datos físicos no informa de fallos

**Dónde**: `composeApp/.../screens/ProfileScreen.kt:469-474` y `306-309`

```kotlin
profileService.saveProfile(request).onSuccess { response ->
    savedTargets = response.targets
    showSummaryDialog = true
    originalProfile = OriginalProfile(...)
}
isSaving = false
```

Sin `onFailure`. Si el guardado falla, el spinner se apaga y **no ocurre nada visible**: ni error, ni
diálogo de resumen, ni cambio en el formulario. El botón sigue habilitado (porque `hasChanges` sigue
siendo `true`, ya que `originalProfile` no se actualizó), así que el usuario pulsará otra vez.

`08-backlog-mejoras.md` lo documenta como abierto citando `ProfileScreen.kt:446-450`; **confirmo que
sigue abierto**, ahora en las líneas 469-474 (y hay una segunda instancia en 306-309, en el reguardado
derivado de VAL-2, donde la omisión es deliberada y está justificada).

Lo señalo pese a estar ya documentado porque la sección 3 completa del plan de pruebas se ejecuta sobre
este botón, y tres de sus cinco ítems piden comprobar que "no se guarda nada" — un fallo de red
producirá el mismo silencio que un rechazo de validación, y será imposible distinguirlos al probar.

### BUG-N7 — Las calorías activas de hoy se suman al presupuesto de días pasados

**Dónde**: `composeApp/.../screens/NutritionScreen.kt:343` + `376`

```kotlin
activeCalories = if (hasWearable) activeCalories else 0.0   // sin relación con selectedDate
…
val realGoal = kcalGoal + activeCalories
```

`activeCalories` se obtiene de `healthDataProvider.getTodayActiveCalories()`
(`NutritionScreen.kt:162`), que por contrato devuelve **las de hoy**
(`HealthConnectProvider.startOfToday()`). Pero el `WeekDayPicker` permite seleccionar cualquier día en
±14 días (`NutritionScreen.kt:965`), y `CalorieCard` recibe `activeCalories` sin tener en cuenta
`selectedDate`.

**Consecuencia**: si el usuario está en modo pulsera y mira el diario de ayer, ve el objetivo de ayer
**más las calorías activas que ha quemado hoy**, con el indicador "+N kcal 🔥" incluido. El histórico
queda falseado, y con un recuento que además cambia a lo largo del día.

**Arreglo mínimo**: `activeCalories = if (hasWearable && selectedDate == today) activeCalories else 0.0`.
Lo correcto de verdad sería leer las calorías activas del día seleccionado, que Health Connect sí
soporta (`TimeRangeFilter.between` con las fechas de ese día) — hoy el provider solo expone "hoy".

### BUG-N8 — El historial de alimentos devuelve una entrada arbitraria por nombre, no la última

**Dónde**: `server/.../services/NutritionService.kt:63-79`

```sql
SELECT name, kcal_per_100, proteins_per_100, carbs_per_100, fat_per_100
FROM meals
WHERE user_id = ? AND id IN (
    SELECT id FROM meals WHERE user_id = ?
    GROUP BY name
    HAVING MAX(created_at)        -- ← no hace lo que parece
)
ORDER BY created_at DESC
LIMIT 30
```

Dos problemas en ese `HAVING`:
1. `HAVING MAX(created_at)` no es una comparación, es una **expresión usada como condición booleana**.
   `MAX(created_at)` es un timestamp en milisegundos, siempre distinto de cero, siempre "verdadero". El
   `HAVING` no filtra nada.
2. En la subconsulta, `id` es una columna suelta junto a un `GROUP BY name`. SQLite solo garantiza que
   una columna suelta venga de la fila del máximo cuando `min()`/`max()` aparece en la **lista de
   resultados**, no en el `HAVING`. Aquí no aparece, así que `id` es el de una fila **arbitraria** de cada
   grupo.

**Consecuencia funcional**: la pestaña "Historial" del buscador de alimentos
(`NutritionScreen.kt:549`, `getFoodHistory`) muestra, para cada alimento, los macros de **una entrada
cualquiera** de las que el usuario registró con ese nombre, no de la más reciente. Si alguien registró
"Pollo" con unos valores y luego los corrigió, el historial puede seguir ofreciendo los viejos — y al
seleccionarlo desde ahí, esos son los valores que se guardan en el diario.

**Arreglo** (aprovechando la extensión documentada de SQLite para columnas sueltas con `max()`):

```sql
SELECT name, kcal_per_100, proteins_per_100, carbs_per_100, fat_per_100, MAX(created_at) AS last_used
FROM meals WHERE user_id = ?
GROUP BY name
ORDER BY last_used DESC
LIMIT 30
```

Esto además elimina la subconsulta y el parámetro `userId` duplicado.

### BUG-N9 — Los `HttpClient` del servidor no tienen ningún timeout

**Dónde**: `OpenAIService.kt:20` (`HttpClient()`), `EmailService.kt:18` (`HttpClient(CIO)`),
`StripeService.kt:17` (`HttpClient(CIO)`), `TursoService.kt:25` (`HttpClient(CIO)`),
`GpsRoute.kt:43` (`HttpClient(CIO)`).

De los cinco clientes HTTP del servidor, **solo `FoodService` configura `HttpTimeout`**
(`FoodService.kt:89-93`, con 30 s/10 s/30 s — bien hecho). Los otros cuatro no tienen límite alguno.

**Por qué importa**: RED-2 se preocupó de los timeouts del cliente, pero el servidor hace de proxy hacia
cuatro servicios externos (OpenAI, Brevo, Stripe, Turso) sin ninguna cota. Una conexión colgada contra
OpenAI —el caso más probable, porque es la petición más lenta y pesada— **bloquea un hilo de Netty en
una instancia gratuita de Render**, sin liberarse nunca. Varias de esas y el servidor deja de responder
a todo, incluido el login.

El único sitio donde hay una cota es el email de confirmación de compra
(`SubscriptionRoute.kt:93`, `withTimeout(10_000)`), que es precisamente el menos crítico. Los envíos de
email del registro (`AuthRoute.kt:49`) y del reset de contraseña (`AuthRoute.kt:129`) no tienen ninguna.

**Arreglo**: instalar `HttpTimeout` en los cuatro clientes, copiando el patrón que ya existe en
`FoodService`. Para OpenAI conviene un valor alto (60-90 s); para Brevo/Stripe/Turso, 10-30 s.

### BUG-N10 — Sin `OPENAI_API_KEY`, el servidor entero no arranca (ni el login)

**Dónde**: `routes/BodyAnalysisRoute.kt:19` + `services/OpenAIService.kt:28-29`

```kotlin
fun Route.bodyAnalysisRoute(profileService: ProfileService) {
    val openAIService = OpenAIService()      // ← se ejecuta al configurar el routing, en el arranque
```
```kotlin
private val apiKey = System.getenv("OPENAI_API_KEY") ?: error("OPENAI_API_KEY missing")
```

`OpenAIService()` se instancia al construir el grafo de rutas, es decir **durante el arranque de la
aplicación**, no en la primera petición. Si falta `OPENAI_API_KEY`, el `error(...)` tira
`IllegalStateException` dentro de `Application.module()` y el proceso no llega a escuchar. Igual ocurre
con `BREVO_API_KEY`/`BREVO_USER` (`EmailService.kt:13-16`), `STRIPE_SECRET_KEY` (`StripeService.kt:14`)
y las de Turso (`TursoService.kt:20-23`), todos instanciados en `configureRouting`/`module`.

**Consecuencia**: la función premium más caro y menos esencial de la app es un requisito para que
funcione el login. En Render, una clave caducada o borrada por error convierte un degradado parcial en
una caída total con reinicios en bucle.

Relacionado: `Application.kt:43-50` hace `runBlocking { … 6 DDL contra Turso … }` en el arranque. Desde
el arreglo de DB-1, `TursoService.execute()` **lanza** `TursoException` ante un fallo de Turso. Antes,
un fallo transitorio de Turso en el arranque se tragaba en silencio y el servidor subía igual; ahora
tumba el arranque. Es el comportamiento correcto desde el punto de vista de "no ocultar errores", pero
cambió el perfil de fallo del arranque y conviene saberlo: un hipo de Turso durante un cold start de
Render ahora es un arranque fallido.

**Arreglo**: construir `OpenAIService` de forma perezosa (`by lazy`) y devolver `503` si falta la clave,
en vez de impedir el arranque. Y considerar envolver los `initTable()` para que un fallo se registre sin
abortar el arranque (o aceptarlo explícitamente como decisión).

### BUG-N11 — Las variables de entorno vacías derrotan las comprobaciones de arranque

**Dónde**: `server/build.gradle.kts:53-64`

```kotlin
tasks.named<JavaExec>("run") {
    environment("TURSO_DB_URL", envProps["TURSO_DB_URL"] ?: "")
    …
```

Si una clave falta en `.env`, Gradle inyecta la variable con valor **cadena vacía**, no la deja sin
definir. Y las comprobaciones del código son `System.getenv("X") ?: throw …` — una cadena vacía no es
`null`, así que **pasan**. El servidor arranca con `dbUrl = ""` y cada llamada a Turso hace un POST a
`"/v2/pipeline"` (ruta relativa sin host), que falla de una forma que no se parece en nada al mensaje
claro que el código intentaba dar (*"TURSO_DB_URL no definida"*).

Relevante porque la ronda de pruebas que empieza ahora arranca el servidor en local con `./gradlew
:server:run`, que es exactamente esta ruta de código.

**Arreglo**: en el `build.gradle.kts`, inyectar solo las claves presentes
(`envProps.forEach { (k, v) -> environment(k.toString(), v.toString()) }`), y/o endurecer las
comprobaciones del código a `?.takeIf { it.isNotBlank() } ?: throw …`.

### BUG-N12 — "¿Olvidaste tu contraseña?" desde el Login navega a una ruta con un segmento vacío

**Dónde**: `composeApp/.../screens/LoginScreen.kt:323` + `navigation/NavGraph.kt:42-43, 216-226`

```kotlin
// LoginScreen.kt:323
navController.navigate(Screen.ForgotPassword.route(fromProfile = false, email = ""))

// NavGraph.kt:43
fun route(fromProfile: Boolean = false, email: String = "") = "forgot_password/$fromProfile/$email"
```

Con `email = ""`, la ruta generada es la cadena `"forgot_password/false/"` — con el último segmento
vacío. El destino está registrado como `"forgot_password/{fromProfile}/{email}"` con tres segmentos
obligatorios (`NavGraph.kt:217`). En Navigation Compose, un argumento de **path** se compila a un grupo
de captura que exige al menos un carácter; el `defaultValue = ""` declarado en `navArgument`
(`NavGraph.kt:220`) aplica a argumentos ausentes o de query, no hace opcional un segmento de path.

**Por tanto es muy probable que esta navegación lance `IllegalArgumentException` ("Navigation
destination that matches route … cannot be found")**, es decir, un crash al pulsar "¿Olvidaste tu
contraseña?" desde el Login.

⚠️ **No he podido ejecutarlo**, así que lo marco como "a confirmar en la primera prueba" y no como hecho.
Lo que sí es un hecho verificado es que se genera una ruta con un segmento vacío y que el destino exige
tres segmentos. El camino desde Perfil (`ProfileScreen.kt:343`) pasa un email no vacío y no tiene este
problema, lo que explicaría por qué podría no haberse detectado antes.

**Es la primera prueba de la sección 2 del plan de pruebas**, así que se verá enseguida.

**Arreglo**: declarar el argumento como query (`"forgot_password/{fromProfile}?email={email}"`) o pasar
un centinela no vacío. De paso resuelve el hallazgo 🟡 ya documentado de *"el email va sin codificar en
un segmento de path"*: un email contiene `@` y `.`, que ahí no deberían viajar sin codificar.

### BUG-N13 — Cerrar sesión sin conexión no cierra la sesión, pero la app dice que sí

**Dónde**: `composeApp/.../screens/DashboardScreen.kt:186-191` + `services/AuthService.kt:86-92`

```kotlin
onClick = {
    menuExpanded = false
    scope.launch {
        authService.logout()                                    // resultado ignorado
        navController.navigate(Screen.Login.route) { popUpTo(0) { inclusive = true } }
    }
}
```

Dos problemas que se combinan:
1. **El resultado de `logout()` se ignora.** `AuthService.logout()` devuelve `AuthResponse` y captura las
   excepciones devolviendo `AuthResponse(false, "Error de conexión: …")`. Nadie lo mira: se navega al
   Login en cualquier caso.
2. **Nadie borra la cookie local.** Verificado con grep sobre las 5 implementaciones de
   `CookiesStorage`: ninguna expone ni recibe una llamada de limpieza. La única llamada a `.clear()`
   sobre preferencias de cookies es la migración del almacenamiento antiguo en
   `CookiesStorageFactory.android.kt:39`, que corre al arrancar, no al cerrar sesión.

**Consecuencia**: si el usuario pulsa "Cerrar sesión" sin conexión (o con el servidor caído), el POST a
`/auth/logout` falla, **la sesión sigue viva en el servidor**, la cookie sigue en el dispositivo, y la
app lo manda al Login como si hubiera cerrado sesión. Al reabrir la app, `me()` encuentra la cookie
válida y lo lleva directo al Dashboard, con la sesión del usuario anterior. En un móvil compartido o
prestado, eso es un cierre de sesión que no cierra nada.

Con logout correcto el riesgo desaparece (el servidor invalida la sesión, y la cookie huérfana produce
un 401 que RED-3 ya gestiona). El problema es solo el camino de fallo.

**Arreglo**: añadir un método de limpieza a `CookiesStorage` (o recrear el `HttpClient`), llamarlo
siempre, y avisar si el `logout()` remoto falló en vez de navegar en silencio.

### BUG-N14 — Borrar una ruta que falla se ve exactamente igual que una borrada con éxito

**Dónde**: `composeApp/.../services/GpsService.kt:50-57` + `screens/MyRoutesScreen.kt:153-158`

```kotlin
// GpsService (cliente)
suspend fun deleteRoute(routeId: String): Result<Unit> {
    return try {
        client.delete("${AppConfig.BASE_URL}/gps/routes/$routeId")   // status ignorado
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}
```
```kotlin
// MyRoutesScreen
onDelete = {
    scope.launch {
        gpsService.deleteRoute(route.id)          // Result ignorado
        routes = routes.filter { it.id != route.id }   // se quita de la lista igualmente
    }
}
```

Dos capas tirando el resultado. Sus hermanas `publishRoute`/`unpublishRoute` **sí** comprueban
`response.status.isSuccess()` (líneas 59-77), lo que confirma que es un olvido y no una decisión.

**Consecuencia visible**: si el borrado falla (404, 401, servidor caído), la ruta **desaparece de la
lista** y el usuario cree que la borró. Reaparece al volver a entrar en la pantalla. Es la instancia de
ERR-1 que más fácil es de notar, y es la que la documentación ya señalaba (ver la corrección en §9.6).

**Arreglo**: comprobar el status en el servicio y, en la pantalla, solo quitar la fila si el `Result` fue
correcto (mostrando un snackbar si no — la pantalla ya tiene `snackbarHostState` montado para el caso de
"publicar bloqueado").

### BUG-N15 — El seguimiento de una ruta se queda bloqueado para siempre si te saltas un punto

**Dónde**: `composeApp/.../screens/RouteDetailScreen.kt:62-72`

```kotlin
if (currentPointIndex >= routePoints.size) return@collect
val indicesToCheck = listOf(currentPointIndex, currentPointIndex + 1)
    .filter { it < routePoints.size }
for (idx in indicesToCheck) {
    if (haversine(routePoints[idx], userPoint) < 20.0) {
        currentPointIndex = idx + 1
        …
```

El progreso solo puede avanzar si el usuario pasa a menos de 20 m del punto actual **o del siguiente**.
No hay ninguna otra forma de avanzar. Por tanto:

- Si se salta dos puntos seguidos (un hueco de señal, un atajo, un cruce por el otro lado de la calle,
  o simplemente puntos muy juntos de una ruta grabada a 1 punto/segundo), el índice **nunca vuelve a
  avanzar**: todas las posiciones siguientes se comparan contra un punto que ya quedó atrás.
- El progreso se queda congelado y el diálogo de "¡Ruta completada!" nunca puede aparecer.
- Empezar la ruta por el final, o unirse a mitad, tampoco funciona: el índice arranca en 0.

Dado que `LocationService` emite con filtro de 5 m y `GpsScreen` descarta puntos a menos de 5 m del
anterior, una ruta grabada tiene puntos cada ~5-10 m; con un radio de acierto de 20 m y una ventana de
solo 2 puntos, el margen es estrecho.

Esta funcionalidad no aparece evaluada en `06-funcionalidades.md` (la sección de GPS habla de grabar y
guardar rutas, no de seguirlas), así que nunca tuvo un veredicto.

**Arreglo**: buscar el punto más cercano por delante del actual en una ventana mayor (p. ej. los
siguientes 10-20 puntos) y saltar a él, en vez de exigir pasar por cada punto en orden estricto.

### BUG-N16 — Las rutas de la comunidad se abren con el título "Ruta" en vez de su nombre

**Dónde**: `composeApp/.../screens/RouteDetailScreen.kt:41-46`

```kotlin
val routesResult = gpsService.getMyRoutes()
if (routesResult.isSuccess) {
    routesResult.getOrNull()?.find { it.id == routeId }?.let { routeName = it.name }
}
```

El nombre de la ruta se resuelve buscándola entre **las rutas propias**. `RouteDetailScreen` se alcanza
desde dos sitios: `MyRoutesScreen` (rutas propias, funciona) y `CommunityRoutesScreen:…`
(`navController.navigate(RouteDetailArgs(routeId = route.id))`, rutas de otros usuarios). En el segundo
caso el `find` no encuentra nada y `routeName` se queda con su valor inicial `"Ruta"`, que es lo que se
muestra en la cabecera y en el diálogo de ruta completada ("Has completado la ruta Ruta").

De paso, es una petición HTTP completa (`/gps/routes/mine`, con su `LEFT JOIN` y `GROUP BY`) solo para
obtener un string que la pantalla anterior ya tenía en memoria.

**Arreglo**: pasar el nombre como argumento de navegación (`RouteDetailArgs` ya es una ruta tipada
`@Serializable`, añadir un campo es trivial), o usar `/gps/routes/community` como segunda fuente.

### BUG-N17 — En Desktop, el botón de escanear código de barras no hace nada y no avisa

**Dónde**: `composeApp/.../App.kt:16` + `jvmMain/.../main.kt:11-15` + `BarcodeScannerSheet.jvm.kt`

`App()` declara `onRequestCameraPermission: (callback: (Boolean) -> Unit) -> Unit = {}` — un valor por
defecto que **recibe el callback y no lo invoca nunca**. `main.kt` (Desktop) solo pasa tres de los cuatro
parámetros, así que se queda con ese valor por defecto. En `NutritionScreen.kt:353`:

```kotlin
onRequestCameraPermission { granted ->
    if (granted) sheetMode = SheetMode.Scanner(meal)
    else scope.launch { snackbarHostState.showSnackbar("Permiso de cámara denegado") }
}
```

El callback nunca se ejecuta, así que **no pasa absolutamente nada**: no se abre el escáner, no sale el
snackbar de permiso denegado, no hay error. El botón parece roto.

Aunque el callback se invocara, `BarcodeScannerSheet.jvm.kt` es un `@Composable` con cuerpo vacío, así
que se abriría un panel en blanco. En Desktop esto es una limitación esperada (no hay cámara gestionada),
pero el modo de fallo elegido —silencio absoluto— es el peor de los tres posibles.

Nótese el contraste: para la ubicación, `main.kt` **sí** pasa explícitamente `{ it(true) }`, así que ese
camino sí funciona. Es solo la cámara la que quedó con el valor por defecto.

**Arreglo**: cambiar el valor por defecto de `App.kt` a `{ it(false) }`. Una palabra, y el usuario pasa
de "botón roto" a "Permiso de cámara denegado".

### BUG-N18 — El buscador de países está implementado pero es inalcanzable

**Dónde**: `composeApp/.../screens/ProfileScreen.kt` (`CountryPickerField`)

```kotlin
var query    by remember { mutableStateOf("") }
val filtered = remember(query) {
    if (query.isBlank()) COUNTRIES else COUNTRIES.filter { it.contains(query, ignoreCase = true) }
}
…
OutlinedTextField(
    value = selected,          // muestra el país elegido, no `query`
    onValueChange = {},        // no hace nada
    readOnly = true,           // no se puede escribir
```

`query` se escribe en tres sitios y en los tres se le asigna `""` (al abrir, al cerrar y al
seleccionar). **No existe ninguna ruta por la que el usuario pueda darle un valor**, así que `filtered`
es siempre la lista completa. El filtro por texto está escrito, cuesta ciclos de recomposición y nunca
filtra nada.

**Consecuencia**: para elegir "España" hay que desplazarse por un desplegable de **190 países** en orden
alfabético. En un móvil es una lista interminable.

**Arreglo**: o se conecta el campo (`value = query`, `onValueChange = { query = it }`, `readOnly = false`,
`MenuAnchorType.PrimaryEditable`), o se borra el `query`/`filtered` muerto. Lo primero es lo que
claramente se pretendía.

---

## 4. 🟠 Seguridad: hallazgos nuevos de severidad media

Ninguno de estos cinco aparece en `03-seguridad.md`.

### SEC-N3 — `/food/search` y `/food/barcode/{code}` no exigen sesión

**Dónde**: `server/.../routes/FoodRoute.kt` (todo el archivo)

Son los **dos únicos endpoints de la app, aparte de `/` y `/health`, que no comprueban
`call.sessions.get<UserSession>()`**. Todos los demás (nutrición, perfil, gps, suscripción, IA) sí.

**Impacto**: cualquiera con la URL de Render puede usar el servidor como proxy anónimo gratuito contra
Open Food Facts, desde la IP única de Render. Eso es exactamente el riesgo que OFF-1 quería mitigar
(*"OFF limita `search.pl` de forma agresiva por IP, así que si la bloquea, la búsqueda de alimentos muere
para todos a la vez"*). Además, al ser consultas únicas, no las absorbe la caché: cada una sale a OFF, y
con más de 500 entradas la caché se **vacía entera** (`FoodService.kt:42`, `map.clear()`), tirando también
las búsquedas legítimas ya calentadas.

Esto incumple además la regla 8 de `02-convenciones.md` (*"antes de añadir un endpoint nuevo, decidir
explícitamente si requiere sesión o es público"*) — no hay rastro de que esto se decidiera.

**Arreglo**: exigir sesión en `/food/*`. Ojo: **requiere arreglar INC-1 primero**, porque el cliente las
llama con el `HttpClient` sin cookies. Si se hace al revés, se reproduce R1 exactamente.

### SEC-N4 — Sin límite de intentos en autenticación ni en el envío de emails

No existe `RateLimit` ni ningún control equivalente en todo el servidor (grep: ninguna aparición).
Quedan sin cota:
- `POST /auth/login` → fuerza bruta de contraseñas sin ningún freno. BCrypt encarece cada intento, lo
  que mitiga, pero no sustituye a un límite.
- `POST /auth/forgot-password` y `POST /auth/resend-verification` → **cada petición envía un email vía
  Brevo**. Un bucle trivial agota la cuota gratuita de Brevo y, de paso, bombardea el buzón de la
  víctima. Se agrava con EMAIL-1 (ya documentado), que dispara un envío con solo *entrar* en la pantalla.
- `POST /ai/body-analysis` → ya protegido por el gate premium (SEC-1), pero sin cuota diaria, como
  reconoce el propio hallazgo SEC-1. Con SEC-N2 (premium gratis con cualquier tarjeta), queda efectivamente
  sin cota.

**Arreglo**: instalar `io.ktor:ktor-server-rate-limit` con un límite por IP para `/auth/*` y uno más
estricto para los dos endpoints que mandan email.

### SEC-N5 — El email no se normaliza ni se valida: cuentas duplicadas e irrecuperables

**Dónde**: `services/UserService.kt:55, 83, 134, 154` y `routes/AuthRoute.kt:38-57`

Dos defectos independientes:

1. **Sin normalizar**. Ni `register` ni `login` ni `createResetToken` aplican `trim()`/`lowercase()`, y
   la columna se declara `email TEXT NOT NULL UNIQUE` sin `COLLATE NOCASE` (`UserService.kt:18`). Las
   comparaciones son `WHERE email = ?`, sensibles a mayúsculas. Consecuencias: `Juan@x.com` y
   `juan@x.com` son **dos cuentas distintas** (el `UNIQUE` no lo impide), y alguien que se registre con
   mayúsculas e inicie sesión en minúsculas recibirá *"Email o contraseña incorrectos"* sin entender por
   qué. Un espacio al final pegado desde el gestor de contraseñas produce lo mismo.

2. **Sin validar el formato**. La única comprobación es `req.email.isBlank()`
   (`AuthRoute.kt:40`). Se puede registrar `pepe` como email: la cuenta se crea con
   `email_verified = 0`, el envío por Brevo falla, y como el login exige verificación
   (`UserService.kt:96`) **la cuenta queda inservible para siempre**, sin forma de corregir el email.
   Es el mismo callejón sin salida que AUTH-1 vino a cerrar, por una puerta distinta: AUTH-1 arregló "no
   puedo introducir el código", pero no "nunca va a llegar un código".

**Arreglo**: normalizar con `email.trim().lowercase()` en los cuatro puntos de entrada, validar el
formato con una regex razonable en `/auth/register`, y añadir `COLLATE NOCASE` a la columna (en una base
nueva; sobre la actual requeriría migración).

### SEC-N6 — Los emails interpolan el nombre del usuario en HTML sin escapar

**Dónde**: `server/.../services/EmailService.kt:51, 66, 82`

```kotlin
<h2 style="color: #1a1a1a;">¡Bienvenido a GymSmart, $toName! 💪</h2>
```

El hallazgo #8 de `03-seguridad.md` arregló la construcción del **JSON** (ahora con `buildJsonObject`,
correcto), pero el **HTML** sigue construyéndose por interpolación de strings. `toName` viene del campo
"Nombre" del registro y del perfil, sin validación ni escapado (`AuthRoute.kt:40` solo comprueba
`isBlank()`, y `PUT /auth/profile` no valida nada).

Un nombre con `<`, `>` o `"` rompe la maquetación del correo; uno construido a propósito puede inyectar
etiquetas arbitrarias (enlaces, imágenes remotas) en un email que se envía **desde el dominio
verificado de GymSmart**. El destinatario es el propio usuario en los tres casos, lo que limita mucho el
impacto —no es un vector para atacar a terceros—, pero un correo legítimo con HTML ajeno dentro es un
riesgo de reputación del remitente y de entregabilidad.

**Arreglo**: escapar `toName` (`&`, `<`, `>`, `"`) antes de interpolarlo, y validar el nombre en el
registro.

### SEC-N7 — La cookie de sesión no lleva el flag `secure`

**Dónde**: `Application.kt:30-34`. Se fija `httpOnly`, `maxAgeInSeconds` y `path`, pero no
`cookie.secure = true`, así que el navegador/cliente la enviará también por HTTP en claro. Combinado con
`usesCleartextTraffic="true"` en el manifest (ya documentado como 🟡) y con el `BASE_URL` apuntando hoy a
`http://` (ver §6), la cookie de sesión puede viajar sin cifrar.

Hoy es de bajo impacto (se prueba en una red doméstica, y por Tailscale el túnel va cifrado de todas
formas). Pero debe activarse antes de volver a apuntar a Render. Nótese que `secure = true` **rompería**
las pruebas en local por `http://`, así que lo natural es condicionarlo a una variable de entorno.

---

## 5. Verificación de los arreglos que la documentación da por hechos

Comprobé uno por uno los 24 hallazgos marcados ✅. **Todos están efectivamente implementados.** Detalle
de los que requerían más que un vistazo:

| Hallazgo | Verificado en | Veredicto |
|---|---|---|
| R1, R2, R3 | `WeightScreen.kt:85-99` | ✅ Correcto. Cliente con cookies, `runCatching` en `getHistory`, `getStatus` fuera del `else` |
| DB-1 | `TursoService.kt:66-80` | ✅ Correcto y bien hecho. Comprueba status HTTP **y** `type == "error"` por statement, con el índice en el mensaje |
| NUT-1 | `NutritionScreen.kt:182-186` | ✅ Correcto |
| NUT-2 | `NutritionRoutes.kt:24` + `NutritionService.kt:28` | ✅ Correcto, el id vuelve y se usa |
| NUT-3 | `NutritionScreen.kt:147, 319-334` | ✅ Correcto, con aviso visible |
| NUT-4 | `NutritionScreen.kt:619` | ✅ Correcto. `estimatedKcalPer100()` (`NutritionModels.kt`) está **especialmente bien razonado** |
| AUTH-1 | `LoginScreen.kt:40-46, 73+` | ✅ Correcto |
| RED-1 | `AuthService.kt:94-103`, `ProfileService.kt:21-32`, `NavGraph.kt:119-139` | ✅ Correcto. `hasProfile(): Boolean?` y `networkError` bien propagados |
| RED-3 (401) | `AuthService.kt:49-59`, `NavGraph.kt:104-113` | ✅ Correcto. La exclusión de `/auth/` evita el falso positivo en el primer arranque |
| RED-2 (timeouts) | `AuthService.kt:43-48`, `build.gradle.kts:49-53` | ⚠️ Correcto **pero incompleto** → INC-1 |
| HTTP-1 | `NavGraph.kt:90-93` | ⚠️ Correcto **pero incompleto** → INC-1 |
| GPS-2 | `GpsScreen.kt:81-89` | ✅ Correcto. El punto de referencia se actualiza sin sumar la distancia |
| GPS-3 | `RouteRecoveryStore.kt` + `GpsScreen.kt:60-62, 96-100, 435` | ✅ Correcto. Nota menor: el guardado periódico (`% 10`) solo se evalúa en la rama final, así que los puntos añadidos por la rama del hueco >100 m pueden saltarse un ciclo. Impacto bajo: hay guardado final al parar |
| GPS-4 | `TursoService.kt:39-83`, `GpsService.kt:48-75` | ✅ Correcto. Lotes de 200, con el `INSERT` de la ruta primero por la FK |
| GPS-1 | `GpsTrackingService.kt`, manifest | ⚠️ Implementado, pero ver **BUG-N1** (`START_STICKY`) |
| WEAR-1/2 | `HealthConnectProvider.kt:38-57` | ✅ Correcto. `aggregate()` con `COUNT_TOTAL`/`ACTIVE_CALORIES_TOTAL` |
| WEAR-3 | `NutritionScreen.kt:159-165`, `DashboardScreen.kt:48-59` | ✅ Correcto. Nota: el `delay(500)` que la doc describe como parte del problema anterior **sigue en el código** (`DashboardScreen.kt:49`), ahora antes del bucle |
| SEC-1 | `BodyAnalysisRoute.kt:29-31` | ✅ Correcto en el servidor, pero franqueable por **SEC-N2** y con mala UX por **INC-4** |
| IA-1 | `OpenAIService.kt:42-79` | ✅ Correcto. Nota: `ImageIO.read` sigue decodificando la imagen completa en heap, así que reduce el payload hacia OpenAI pero **no** el pico de memoria, que es el riesgo real en una instancia de 512 MB |
| IA-2 | `OpenAIService.kt:93, 97` | ✅ `temperature = 0.2` y `response_format` correctos. Pero ver la nota de prompt en §7 |
| IA-3 | `OpenAIService.kt:26, 191` | ✅ Correcto |
| OFF-1 | `FoodService.kt:25-46, 99-130` | ✅ Correcto y bien razonado (no cachear vacíos es el acierto clave). Ver INC-5 |
| VAL-1 | `ProfileValidation.kt`, 3 pantallas | ✅ Correcto. Ver INC-2 (coma decimal) |
| VAL-2 | `ProfileScreen.kt:285-310` | ✅ Correcto. Ver INC-3 |
| ERR-1 (`WeightScreen`) | `WeightScreen.kt:478-494` | ✅ Correcto en lo que cubría. Ver BUG-N4 y BUG-N5 |

---

## 6. ⚠️ Riesgos inmediatos para la ronda de pruebas que empieza ahora

Esto es lo que conviene saber **antes** de empezar a probar, para no perder tiempo diagnosticando cosas
ya conocidas.

### PRU-1 — `BASE_URL` está hoy en `http://localhost:8080`, que no funcionará desde el móvil

**Dónde**: `composeApp/.../config/AppConfig.kt` (cambio **sin commitear**, visible en `git diff`)

```kotlin
//const val BASE_URL = "https://gymsmart-server.onrender.com"
const val BASE_URL = "http://localhost:8080"
```

En un móvil Android, `localhost` es **el propio teléfono**, no el PC donde corre el servidor. Con este
valor, todas las peticiones desde el móvil fallarán con error de conexión. El paso 2 de la sección 0 de
`09-plan-de-pruebas.md` lo dice correctamente ("cambia el valor a `http://<IP de Tailscale>:8080`"), pero
el valor que hay puesto ahora mismo no es ese. En Desktop (misma máquina que el servidor) sí funcionará,
lo que puede confundir: Desktop bien, Android mal, por configuración y no por código.

Nota adicional: la sección 0 del plan dice *"Hoy apunta a Render"* y muestra la línea de Render. Esa
descripción ya no coincide con el archivo.

### PRU-2 — Para probar el análisis por IA hace falta premium, y premium dura 1 hora

**Dónde**: `server/.../routes/SubscriptionRoute.kt:87`

```kotlin
val duration = 60L * 60 * 1000       // 1 hora
profileService.activatePremium(session.userId, duration)
```

Una hora de premium por un cargo de 4,99 € (`SubscriptionRoute.kt:73`). No es un descuido aislado: la UI
está escrita alrededor de ello — `ProfileScreen.kt:540-557` y `SubscriptionScreen.kt:200-206` calculan
el tiempo restante en minutos y muestran literalmente **"Caduca en N minutos"** / **"Premium activo —
caduca en N min"**.

⚠️ **CORRECCIÓN de la primera pasada de este informe**: escribí que esto "no estaba en ningún
documento". **Sí lo está**: `01-arquitectura.md §3` ("Code smells del servidor") dice textualmente
*"Precios/duraciones de 'suscripción' hardcodeados (499 céntimos, 1 hora de premium) — valores de
prueba, no reales"*. Es decir, está correctamente identificado como valor de prueba. Lo que sigue
siendo válido del hallazgo es su **interacción con el plan de pruebas**, que nadie había conectado: dos
ítems de la sección 4 requieren una cuenta premium para probar el análisis por IA, y dejarán de
funcionar una hora después de activarla.

**Dos consecuencias para la ronda de pruebas**:
1. Los dos ítems de la sección 4 del plan que requieren "una cuenta premium" (reproducibilidad del
   análisis, foto de mala calidad) **dejarán de funcionar una hora después de activar premium**, y el
   síntoma será un 403 que la app muestra como *"No se pudo completar el análisis. Inténtalo de nuevo."*
   (INC-4) — indistinguible de un fallo de red. Conviene activar premium justo antes de esas pruebas.
2. El plan dice *"Stripe/pagos: no funcional por diseño, confirmado"* y lo excluye de las pruebas, pero
   el pago es **el único camino** para obtener premium en la app. Para probar la IA hay que pasar por él
   (o insertar a mano una fila en `subscriptions` en Turso).

**Decisión pendiente, no un bug**: si premium debe durar un mes, es cambiar `60L * 60 * 1000` por
`30L * 24 * 60 * 60 * 1000` y el texto de `ProfileScreen`. Pero es una decisión de producto, no la tomo yo.

### PRU-3 — Pruebas que, tal como está el código, no pueden dar el resultado que el plan espera

| Ítem del plan | Qué pasará realmente |
|---|---|
| §2, ítem 2 ("¿Olvidaste tu contraseña?" desde Login) | Posible crash por **BUG-N12** antes de llegar a la pantalla |
| §4, ítem 2 (guardar peso con el servidor apagado) | Si el servidor ya estaba apagado **al abrir** la pantalla, el botón no hará nada y no habrá mensaje (**BUG-N4**). Solo da el resultado esperado si se apaga después de cargar |
| §3, ítems 1-3 (validación en datos físicos) | Un fallo de red da el mismo silencio que un rechazo de validación (**BUG-N6**), así que no se podrán distinguir |
| §6, ítem 4 (cerrar la app grabando) | Recuperará la ruta correctamente (GPS-3 ✅), pero puede quedar la notificación zombi de **BUG-N1** |
| §8, ítem 2 (modo avión, "no infinito") | En Nutrición (búsqueda de alimentos) **sí es infinito**: ese cliente no tiene timeout (**INC-1**) |

### PRU-4 — `:server:test` está roto y fallará si se ejecuta

`server/src/test/.../ApplicationTest.kt` afirma que `GET /` devuelve `"Ktor: ${Greeting().greet()}"`,
pero la ruta real devuelve `"GymSmart API running 🏋️"` (`plugins/Routing.kt:28`). Además llama a
`module()`, que requiere las variables de entorno de Turso/OpenAI/Brevo/Stripe (que la tarea `test` **no**
inyecta, solo lo hace `run`) y ejecuta DDL real contra Turso. Es boilerplate del wizard de KMP que nunca
se actualizó. Los otros dos tests del proyecto son `assertEquals(3, 1 + 2)`.

---

## 7. 🟡 Hallazgos nuevos de severidad baja

- **BAJ-1 — `formatDouble` trunca el último decimal.** `screens/GpsUtils.kt:20-30`: calcula la parte
  decimal con `((rounded - intPart) * factor).toLong()`, y `toLong()` **trunca**. Con 8,2 km:
  `8.2 - 8 = 0.1999999999999993`, `× 100 = 19.99999999999993`, `toLong() = 19` → muestra **"8.19 km"**.
  Afecta a todas las distancias de GPS (`GpsScreen.kt:133, 345`, `MyRoutesScreen.kt:228`,
  `CommunityRoutesScreen.kt:154`, `RouteDetailScreen.kt:200`). Aparte, pierde el signo entre -1 y 0
  (ya documentado 🟡). Arreglo: construir la cadena desde el entero redondeado
  (`round(value * factor).toLong()`) y partirlo, en vez de restar.
- **BAJ-2 — `PRAGMA foreign_keys` no se activa nunca** (grep: ninguna aparición). SQLite desactiva la
  comprobación de claves foráneas por conexión salvo que se active explícitamente. Hay 8 declaraciones
  `ON DELETE CASCADE`, y la única que importa operativamente es `gps_points → gps_routes`:
  `GpsService.deleteRoute` (`:138`) borra solo de `gps_routes` y confía en la cascada. Si no está
  activa, cada ruta borrada **deja sus puntos huérfanos para siempre** (cientos de filas por ruta).
  No puedo confirmar qué hace libSQL/Turso por defecto sin consultarlo, así que lo dejo como "verificar":
  basta borrar una ruta y contar `SELECT COUNT(*) FROM gps_points WHERE route_id = '<id borrado>'`.
  Arreglo robusto: borrar explícitamente de `gps_points` en el mismo batch, sin depender de la cascada.
- **BAJ-3 — `/nutrition/add` no valida nada.** `routes/NutritionRoutes.kt:19-25`: ni `grams`, ni
  `kcalPer100`, ni los macros, ni el formato de `date`. Se puede registrar una comida de -500 g o de
  99.999 kcal/100 g. Contrasta con `/profile/save`, que valida seis campos con rangos. Un fallo de dedo
  corrompe el recuento del día sin aviso, y el cliente solo limita la longitud del campo
  (`NutritionScreen.kt:755`), no el rango.
- **BAJ-4 — `goalRate` es el único campo del perfil sin validar en el servidor.**
  `routes/ProfileRoute.kt:86-97` valida peso, altura, edad, sexo, nivel de actividad y objetivo, pero no
  `goalRate`. `computeTargets` lo suma directo (`ProfileService.kt:189-191`). Un valor absurdo positivo
  produce un objetivo de calorías absurdo (el negativo lo contiene el `coerceAtLeast(minKcal)`). En el
  cliente, `validateGoalRate` solo comprueba que no esté en blanco, no el rango.
- **BAJ-5 — `removeFavorite` por nombre en un segmento de path.** `NutritionService.kt` (cliente) usa
  `name.encodeURLPath()`, que **por diseño no escapa `/`**. Un alimento con barra en el nombre
  ("Pan 1/2") genera una URL con un segmento extra y la ruta
  `DELETE /nutrition/favorites/remove/{name}` no casará → 404 silencioso (`catch → false`). Mejor:
  mandar el nombre en el cuerpo o usar el `id` del favorito, que la tabla ya tiene.
- **BAJ-6 — Enumeración de usuarios en `/auth/resend-verification`.** `AuthRoute.kt:108-118` responde
  `400 "Email no encontrado"` si el email no existe, revelando qué emails están registrados. Justo al
  lado, `/auth/forgot-password` (`:121-132`) hace lo correcto y responde siempre lo mismo
  (*"Si el email existe…"*). Inconsistencia dentro del mismo archivo.
- **BAJ-7 — El registro ignora si el email se envió.** `AuthRoute.kt:49-51` guarda el resultado en
  `emailSent`, lo **loguea**, y responde `201 VERIFY_EMAIL` igualmente. Si Brevo falla, el usuario queda
  en la pantalla de "introduce el código" sin que exista ningún código en su buzón. Hay salida (reenviar),
  pero el mensaje miente. Es la misma clase de fallo que EMAIL-1, en el camino crítico del registro.
- **BAJ-8 — `/nutrition/update` y `/nutrition/delete` responden OK siempre.** `NutritionRoutes.kt:47-64`:
  no se comprueba si alguna fila casó. Borrar una comida inexistente (o de otro usuario) devuelve
  `200 "Eliminado"`. El filtrado por `user_id` está bien puesto (el arreglo del IDOR es correcto), pero
  la respuesta no distingue "borrado" de "no existía". De paso: el hallazgo ERR-1 menciona que
  `GpsService.deleteRoute` *"no comprueba el status y devuelve éxito siempre"* — esa parte **ya no es
  cierta** desde el arreglo de DB-1, porque ahora `turso.execute` lanza ante un fallo.
- **BAJ-9 — `/nutrition/mine` usa `toDouble()` en vez de `toDoubleOrNull()`.**
  `NutritionRoutes.kt:36-40`: cinco conversiones que lanzan si el valor no es numérico, dentro de una
  ruta cuyo resto del código usa el patrón seguro (`?: 0.0`). Hoy no es alcanzable (las columnas son
  `REAL NOT NULL`), pero es un 500 latente e inconsistente con el estilo del resto.
- **BAJ-10 — `ImagePicker.jvm` lee el archivo y lo codifica en base64 en el hilo de UI.**
  `jvmMain/.../ImagePicker.jvm.kt`: la lambda que se devuelve hace `dialog.isVisible = true` (AWT
  `FileDialog`, modal y bloqueante), y después `file.readBytes()` + `Base64.getEncoder().encodeToString(…)`
  — todo dentro del `onClick`, es decir en el hilo de UI de Compose. Con una foto de varios MB, la
  ventana de Desktop se congela durante la operación. Solo afecta a Desktop, que es de uso propio, pero
  es el tipo de cosa que se notará al probar el análisis por IA en Desktop.
- **BAJ-11 — La pantalla de pago afirma "Pago seguro procesado por Stripe".**
  `SubscriptionScreen.kt:288`. En realidad el PAN y el CVC viajan en claro al propio backend, que los
  descarta y usa un token de test (ver SEC-N2 y el hallazgo #6 de `03-seguridad.md`). La afirmación de la
  UI no se corresponde con lo que ocurre. No tiene impacto técnico —el pago no es real— pero en un TFG es
  precisamente el tipo de detalle que un tribunal puede preguntar, y es trivial de matizar.
- **BAJ-12 — `RoutePoint.accuracyM` es el único campo del modelo sin valor por defecto.**
  `model/RoutePoint.kt:12`: `speedMs` y `altitudeM` tienen `= 0f` / `= 0.0`, `accuracyM` no. Como
  `RoutePoint` se serializa en `RecoverableRoute` (GPS-3), un JSON guardado por una versión futura que
  omitiera ese campo no se podría leer. Inofensivo hoy (lo escribe y lo lee el mismo código), pero es una
  asimetría gratuita en un modelo que ahora se persiste a disco.
- **BAJ-13 — Reenviar el código de verificación siempre dice que se reenvió.**
  `LoginScreen.kt:161-169`: `authService.resendVerification(email)` y, sin mirar el resultado,
  `verifyError = "Código reenviado, revisa tu bandeja"`. Si el servidor responde 400 ("El email ya está
  verificado", "Email no encontrado") o no hay red, el mensaje es el mismo. Misma clase que BAJ-7.
- **BAJ-14 — La regla de negocio de los 100 m está duplicada en cliente y servidor.**
  `MyRoutesScreen.kt:244` (`route.distanceMeters >= 100.0`) y `GpsService.kt:150` del servidor
  (`if (distancia < 100.0) return false`). Dos copias del mismo umbral que pueden divergir; el mensaje de
  usuario ("mínimo 0.1 km") está además escrito a mano en los dos lados.

---

## 8. 🧹 Código y dependencias muertas no inventariadas en `05-deuda-tecnica.md`

Todas verificadas con grep sobre el repo completo (excluyendo `build/`):

| Qué | Dónde | Evidencia |
|---|---|---|
| `getLocation()` + `GpsLocation` + `IpApiResponse` + su `httpClient` y caché | `server/.../routes/GpsRoute.kt:25-52, 140-169` | 30 líneas, un cliente HTTP y dos modelos. No hay ninguna ruta que lo exponga ni ninguna llamada. Su único "uso" es un **import sin usar** en `Application.kt:15`. Además pega a `http://ip-api.com` en claro |
| `calcularDistancia(points, nuevo)` | `screens/GpsScreen.kt:494-497` | Pública, sin un solo llamante. Además es O(n²) (recalcula la ruta entera por punto) |
| `calcularDistanciaTotal` duplicada | `GpsScreen.kt:499-503` y `RouteDetailScreen.kt:316` | Misma función, `private` en dos archivos. La de `GpsScreen` solo la usa la función muerta de arriba |
| `Double.round1()` | `screens/NutritionScreen.kt:1019` | Pública, sin llamantes |
| `MetricBox` | `screens/GpsUtils.kt:13-18` | `@Composable` público, sin llamantes. Además hardcodea `Color.Gray`, contra la convención de usar `GymSmartColors` |
| `com.sun.mail:jakarta.mail:2.0.1` | `server/build.gradle.kts:40` | Cero imports de `jakarta.mail`/`javax.mail`. Resto de la época del SMTP de Gmail; hoy `EmailService` usa la API HTTP de Brevo |
| `ktor-client-logging` | `server/build.gradle.kts:31` | Ninguna instalación del plugin `Logging` |
| `ktor-clientAndroid` | `gradle/libs.versions.toml:56` | Correctamente retirado del classpath por RED-2, pero sigue declarado en el catálogo |
| 4 variables de entorno fantasma | `server/build.gradle.kts:56-63` | `RESEND_API_KEY`, `GMAIL_USER`, `GMAIL_PASS`, `BREVO_SMTP_KEY` se inyectan pero no las lee nadie. El código solo usa `PORT`, `TURSO_DB_URL`, `TURSO_AUTH_TOKEN`, `BREVO_API_KEY`, `BREVO_USER`, `OPENAI_API_KEY`, `STRIPE_SECRET_KEY` |
| Columna `has_wearable` | `ProfileService.kt:45, 105` | Se escribe en cada `upsertProfile` pero **ningún `SELECT` la lee** (`getProfile` no la incluye). Es dato de solo escritura. Se relaciona con WEAR-4 (el campo viaja con tres significados distintos) |
| Dependencias duplicadas | `composeApp/build.gradle.kts:104-105` vs `96-98` | `ktor-client-content-negotiation` y `ktor-serialization-kotlinx-json` declarados dos veces, una por catálogo y otra con versión literal `3.3.3` |
| `ClientSecretResponse` y `ConfirmPaymentRequest` | `composeApp/.../model/SubscriptionModels.kt:13-20` | Sin una sola referencia. Restos del flujo anterior de Stripe por `PaymentIntent`, sustituido por el endpoint único `/subscription/pay` |
| `GpsData` | `composeApp/.../model/GpsData.kt:6` **y** `server/.../model/GpsData.kt:6` | Sin referencias en ninguno de los dos módulos. Está duplicado y muerto a la vez |
| `ActiveRoute.durationSeconds` | `composeApp/.../model/RoutePoint.kt:18` | Campo nunca leído ni escrito: `GpsScreen` calcula la duración desde `startedAt` al guardar (de ahí GPS-6) |
| `query`/`filtered` de `CountryPickerField` | `ProfileScreen.kt` | Filtro de países inalcanzable — ver BUG-N18 |
| Tercer `HttpClient` | `services/TrainingService.kt:16` | `val client = HttpClient { … }` **público** y top-level en el paquete `services`. Ver la nota de abajo |

**Nota sobre el tercer `HttpClient`**: `TrainingService.kt:16` declara `val client = HttpClient { … }`
sin modificador de visibilidad, es decir **público**, top-level, y llamado literalmente `client`, en el
mismo paquete (`com.gymsmart.gymsmart.services`) que todos los demás servicios. `05-deuda-tecnica.md` lo
menciona ("usa una función suelta + `HttpClient` top-level propio") y el usuario decidió no tocar
`Training`, así que **no es un hallazgo nuevo en sí**. Lo que sí corrige es la cuenta: en `composeApp`
hay **tres** `HttpClient`, no uno — el de `AuthService` (el bueno), el de `NutritionScreen.kt:76`
(INC-1) y este. La afirmación de HTTP-1 de que `NavGraph` crea "el único de toda la app" está, por
tanto, doblemente desfasada. Al no alcanzarse nunca `TrainingScreen`, este cliente no llega a
instanciarse en ejecución (las propiedades top-level de Kotlin se inicializan al cargar el archivo), así
que no tiene coste real — pero sí es un nombre público genérico (`client`) en un paquete muy poblado.

**Nota sobre `frontend/`**: `05-deuda-tecnica.md` dice que quedó pendiente borrar la carpeta vacía
(bloqueada por Windows). Confirmo que **sigue existiendo** y que está **completamente vacía** y sin
rastrear por git (`git ls-files | grep frontend` → 0 resultados). Borrarla es seguro.

**Nota sobre `settings.gradle.kts`**: verificado que solo incluye `:composeApp`, `:server` y `:shared` —
no hay rastro de `backend`/`frontend` en la configuración de Gradle, así que su eliminación fue completa
a efectos de build.

**Versiones literales en lugar del catálogo**: `composeApp/build.gradle.kts` fija `3.3.3` a mano en 5
sitios (líneas 54, 104, 105, 117) y `server/build.gradle.kts` en 6 (líneas 31-36), mientras
`libs.versions.toml` ya define `ktor = "3.3.3"`. El día que se suba la versión de Ktor en el catálogo,
estas quedarán atrás y el classpath mezclará versiones.

---

## 8.bis 📋 ¿Está todo de acuerdo al plan? — contraste con los criterios de salida del roadmap

`00-roadmap.md` define un **criterio de salida** explícito por fase. Contrastándolos uno a uno:

| Fase | Criterio de salida declarado | Veredicto |
|---|---|---|
| 0 — Preparación | "esta estructura existe y el usuario la ha visto" | ✅ Cumplido |
| 1 — Documentación | "Claude puede explicar cualquier flujo citando archivos concretos **sin re-explorar el repo**" | ❌ **No se cumple hoy** (ver PLAN-1) |
| 2 — Seguridad | "no quedan hallazgos **críticos** abiertos" | ⚠️ Se creía cumplido; SEC-N1 y SEC-N2 son críticos y estaban abiertos sin detectar |
| 3 — Limpieza | "build limpio, **sin warnings relevantes**, sin carpetas/archivos huérfanos **sin justificar**" | ⚠️ Warnings: ✅ cumplido (verificado, solo 2 y del plugin). Huérfanos: ❌ 15 ítems sin inventariar (§8) |
| 4 — Validación | "hay un veredicto objetivo sobre cada funcionalidad" + entregable: "`06-funcionalidades.md` … **y referencia a los tests añadidos**" | ❌ **Cerrada ✅ sin su entregable** (ver PLAN-2) |
| 5 — Mejora | "el usuario confía en los números que da la app tanto como en una app profesional" | 🔄 En curso, legítimamente — depende de la ronda de pruebas |
| 6 — Nuevas funcs. | (bloqueada hasta cerrar 1-5) | Correctamente bloqueada |

### PLAN-1 — El criterio de salida de la Fase 1 ya no se cumple, porque `01-arquitectura.md` quedó congelado

`01-arquitectura.md` se presenta como *"la app **tal y como está hoy**"*, y `README.md` (regla 1) manda
leerlo antes de tocar código precisamente para **no** tener que releer el repo. Pero el documento es un
retrato del 2026-10-26, **anterior a casi todo lo que se arregló después**, y describe como estado actual
al menos diez cosas ya resueltas:

| Lo que afirma `01-arquitectura.md` | Realidad verificada |
|---|---|
| §3 tabla de endpoints: `/ai/body-analysis` → "**Sin ninguna comprobación de sesión**" | Exige sesión **y** premium (`BodyAnalysisRoute.kt:23-31`) |
| §3 tabla de endpoints: `/nutrition/delete\|update` → "**sin comprobar propiedad**" | Filtran por `user_id` desde el arreglo del IDOR |
| §2: "en `WeightScreen.kt:54-66` coexisten **tres** `HttpClient`" | Hoy usa el compartido (`WeightScreen.kt:55-56`) |
| §2: "La cookie de sesión se persiste **sin cifrar en las 4 plataformas**" | Android usa `EncryptedSharedPreferences` |
| §2 Navegación: "mientras se resuelve, **no hay pantalla de carga explícita**" | `StartupScreen` existe (`NavGraph.kt:56-80`) |
| §3: "Logging por `println` (26 ocurrencias)" en el servidor | Migrado a SLF4J; 0 `println` en `server/` |
| §3: "Esquema desincronizado: `is_public` y `has_wearable` no están en los `CREATE TABLE`" | Ambas añadidas |
| §3: "Duplicación entre `getRoutesByUser` y `getCommunityRoutes`" | Unificadas en `queryRoutes` |
| §3 Email: "body JSON construido por interpolación directa, sin escapar" | Usa `buildJsonObject` |
| §2/§3: "`Product`/`Nutriments` definidos **dentro de `NutritionScreen.kt`**" | Movidos a `model/NutritionModels.kt` |
| §Wearable: "`HealthConnectProvider` lee … con **`readRecords`**" | Usa `aggregate()` (WEAR-1/2) |

Esto incumple la **regla 2 del propio `README.md`**: *"Si el código y el doc no coinciden, manda el
código — el doc está desactualizado y hay que corregirlo **en el mismo cambio**"*. Durante las Fases 2-5
se actualizaron religiosamente `CONTINUAR.md`, `08-backlog-mejoras.md` y `07-registro-sesiones.md`, pero
`01-arquitectura.md` se quedó atrás.

**Por qué importa más de lo que parece**: es el documento que `CLAUDE.md` y `README.md` señalan como
mapa de referencia. Una sesión nueva que lo lea y se lo crea puede ponerse a "arreglar" la falta de auth
en `/ai/body-analysis` (ya hecha), o tomar decisiones de diseño sobre la base de que hay tres clientes
HTTP en `WeightScreen`. Es la inexactitud de documentación de mayor impacto práctico del proyecto, por
encima de las dos de `03-seguridad.md` que ya listé en §9.

**Arreglo**: una pasada de actualización, o —más honesto y más barato— añadirle una cabecera que diga
"foto del 2026-10-26, previa a las Fases 2-5; para el estado actual ver `08-backlog-mejoras.md`", y
corregir al menos la tabla de endpoints de §3, que es la parte que puede inducir a un error de seguridad.

**Dato menor de rebote**: §4 dejaba una pregunta abierta — *"El origen de los datos nutricionales (Open
Food Facts vía `/food/search|barcode`) **no está confirmado como conectado directamente** desde
`NutritionScreen` en esta pasada — revisar en Fase 4"*. Queda resuelta: **sí** está conectado
directamente, y lo hace con un `HttpClient` propio del archivo (`NutritionScreen.kt:181` y `:611`), que
es justo el hallazgo INC-1.

### PLAN-2 — La Fase 4 se cerró como ✅ Completada sin entregar los tests que ella misma exigía

`00-roadmap.md`, Fase 4, alcance declarado:

> - Añadir **tests** donde no los haya para la lógica crítica (cálculos de nutrición sobre todo), para
>   poder refactorizar después sin miedo.
>
> **Entregable:** `06-funcionalidades.md` con el veredicto de cada funcionalidad … **y referencia a los
> tests añadidos**.

Estado real verificado:
- La Fase 4 figura como **"✅ Completada"** en la tabla de estado del roadmap.
- `06-funcionalidades.md` **no contiene ni una sola referencia a un test** (verificado leyéndolo
  completo).
- Los únicos tests del repo son los tres del wizard de KMP: `ApplicationTest` (roto, ver PRU-4) y dos
  `assertEquals(3, 1 + 2)`.
- No existe ningún archivo de test escrito para este proyecto.

Es decir: **la mitad del alcance de la Fase 4 se omitió y la fase se marcó como completa igualmente**, sin
dejar anotado que se había diferido ni por qué. Es el único desvío del plan que no está declarado en
ninguna parte — todo lo demás que se dejó fuera (Stripe, CORS, logging del cliente, GPS-1b) está
explícitamente marcado como diferido y con su motivo.

**Por qué importa, y no es un tecnicismo**: el motivo que el propio roadmap da para esos tests es *"para
poder refactorizar después sin miedo"*. Después de la Fase 4 vino precisamente la Fase 5, que refactorizó
la validación de perfiles, el cálculo de kcal de Open Food Facts, el cliente HTTP único, el parseo del
análisis por IA y el guardado de rutas — todo sin esa red. Y la única verificación disponible en este
entorno es "compila", que no dice nada sobre si `computeTargets` o `estimatedKcalPer100` siguen dando el
número correcto. Esta revisión ha encontrado por lectura dos bugs (BAJ-1 en `formatDouble`, INC-2 en la
coma decimal) que un test de tres líneas habría pillado solo.

Ver **MEJ-1** para la propuesta concreta y mínima.

---

## 9. 📄 Inexactitudes en la documentación

La documentación es buena y honesta. Estas siete cosas conviene corregirlas porque una sesión futura
tomará decisiones basándose en ellas:

1. **`03-seguridad.md` está desincronizado con `08-backlog-mejoras.md`.** Los hallazgos **#11** (gate
   premium) y **#12** (errores de BBDD silenciosos) siguen marcados *"🟠 Abierto"* (líneas 121 y 133),
   pero ambos están arreglados (SEC-1 y DB-1, ✅ en el backlog y verificados por mí en el código).
   `CLAUDE.md` indica leer `03-seguridad.md` para el contexto de seguridad, así que una sesión nueva
   puede ponerse a "arreglar" algo ya hecho.
2. **HTTP-1 afirma un cliente único que no es único.** Ver INC-1: `NutritionScreen.kt:76` tiene otro.
   La frase *"`NavGraph` crea el único `AuthService()` de toda la app"* debería decir "el único con
   cookies, timeouts y manejo de 401".
3. **RED-4 aparece como abierto y como arreglado a la vez.** `08-backlog-mejoras.md:256` lo lista en
   🟠 Medios (*"`NavGraph.kt:71` hace `if (startDestination == null) return`: no se dibuja nada"*), pero
   el punto 4 del "orden de ataque" dice *"pantalla de carga ya existe desde RED-1"*. Lo segundo es lo
   correcto: `StartupScreen` existe (`NavGraph.kt:56-80`) y se pinta antes del `return`
   (`NavGraph.kt:136-139). La entrada de 🟠 Medios está obsoleta.
4. **El `delay(500)` de Dashboard sigue ahí.** WEAR-3 lo describe como parte del problema anterior
   (*"con un `delay(500)` arbitrario en Dashboard"*), lo que sugiere que se quitó.
   `DashboardScreen.kt:49` lo conserva, ahora justo antes del bucle.
5. **IA-2 exagera el suavizado del prompt.** `CONTINUAR.md` dice *"El prompt ya no obliga a fingir
   certeza"*. Se **añadió** el permiso de avisar en `summary` (`OpenAIService.kt:138-141`, correcto),
   pero la instrucción original sigue literalmente en el prompt (`OpenAIService.kt:134-137`):
   *"Estima siempre un valor razonable…, **nunca devuelvas campos vacíos ni digas que no puedes ver la
   imagen**"*. El prompt ahora dice las dos cosas a la vez. Funciona, pero la descripción no es exacta.
6. ⚠️ **CORRECCIÓN de la primera pasada de este informe.** Escribí que la sub-afirmación de ERR-1 sobre
   `GpsService.deleteRoute` ("no comprueba el status y devuelve éxito siempre") estaba obsoleta porque
   DB-1 hace que `turso.execute` lance. **Me equivoqué**: asumí que hablaba del `GpsService` del
   *servidor*, y hay dos clases con ese nombre. El del servidor (`server/.../services/GpsService.kt:132`)
   sí comprueba la propiedad y devuelve `false` si no existe. El que ERR-1 describe es el del **cliente**
   (`composeApp/.../services/GpsService.kt:50-57`), y ahí la afirmación **sigue siendo exacta y el
   hallazgo sigue abierto** — ver BUG-N15. La documentación tenía razón y yo no.
7. **La sección 0 de `09-plan-de-pruebas.md` describe un `BASE_URL` que ya no es el del archivo**
   (ver PRU-1). Dice *"Hoy apunta a Render"*; hoy apunta a `http://localhost:8080`.

**Incumplimientos de las propias reglas de `02-convenciones.md`** (más allá de lo ya documentado):
- **Regla 1** (*"no instanciar `HttpClient` dentro de cada pantalla"*) → `NutritionScreen.kt:76`.
- **Regla 4** (*"nunca loguear… payloads completos con PII (email…)"*) → el cambio **sin commitear** de
  `EmailService.kt:39` introduce `logger.warn("Brevo rechazó el envío a {}: {} — {}", to, …)`, que vuelca
  **el email completo del destinatario** y el cuerpo de respuesta de Brevo. Mejorar el diagnóstico de
  fallos de Brevo es acertado y resuelve una carencia real; pero conviene loguear el `userId` o el
  dominio del email, no la dirección entera, para no deshacer el arreglo del hallazgo #4.
- **Regla 6** (*"modelos de red van en `model/`"*) → `PayCardRequest` vive en
  `services/SubscriptionService.kt:11-18`, y `ChangePasswordRequest`, `VerifyEmailRequest`,
  `ResendVerificationRequest`, `ForgotPasswordRequest`, `ResetPasswordRequest` y
  `UpdatePersonalDataRequest` en `services/AuthService.kt:190-209`. La excepción que la doc anotaba
  (`Product`/`Nutriments` en `NutritionScreen`) **sí** se arregló; estas siguen.
- **Regla 8** (*"decidir explícitamente si un endpoint requiere sesión"*) → `/food/*` (SEC-N3).

---

## 10. 💡 Mejoras (no son bugs)

Ordenadas por relación valor/esfuerzo, en mi criterio.

### MEJ-1 — Tests unitarios de las funciones puras: la mejora de mayor valor para este proyecto concreto

La restricción estructural de este trabajo es *"no hay Android SDK aquí, así que no se puede verificar"*,
y aparece en cada documento. Pero una parte importante de la lógica que se ha estado arreglando **no
tiene nada de Android** y es puramente funcional:

| Función | Dónde | Qué se protegería |
|---|---|---|
| `validateWeightKg/HeightCm/Age/GoalRate` | `validation/ProfileValidation.kt` | VAL-1, y la coma decimal de INC-2 |
| `Nutriments?.estimatedKcalPer100()` | `model/NutritionModels.kt` | NUT-4 y la lógica de kcal de OFF |
| `computeTargets(profile)` | `server/.../ProfileService.kt:167` | TDEE/macros, el corazón de la app |
| `formatDouble` / `formatFloat` | `screens/GpsUtils.kt` | BAJ-1, que un test de 3 líneas pilla |
| `haversine` | `screens/GpsScreen.kt:505` | Base de toda la distancia de GPS |
| `MealEntry.kcal/proteins/carbs/fat` | `model/MealEntry.kt` | Aritmética del diario |

Todas son deterministas, sin red, sin Android, sin base de datos. `commonTest` y `server/src/test` ya
existen con `kotlin-test` configurado. Son los dos sitios donde los tests cuestan casi nada y donde ya
se han encontrado bugs reales (BAJ-1, INC-2). Empezaría por `formatDouble` y `computeTargets`.

Como paso previo: **arreglar o borrar `ApplicationTest`** (PRU-4), porque mientras falle nadie va a
ejecutar `gradle test` y los tests nuevos no se correrán.

### MEJ-2 — Índices en la base de datos

No hay ni un `CREATE INDEX` en el proyecto. Las consultas más frecuentes escanean tabla completa:
- `meals WHERE user_id = ? AND date = ?` (cada apertura de Nutrición) → `CREATE INDEX IF NOT EXISTS idx_meals_user_date ON meals(user_id, date)`
- `gps_points WHERE route_id = ? ORDER BY timestamp` (cada detalle de ruta) → `idx_gps_points_route ON gps_points(route_id, timestamp)`
- `weight_history WHERE user_id = ?`, `gps_routes WHERE user_id = ?`, `food_favorites WHERE user_id = ?`

`gps_points` es la que más crece (cientos de filas por ruta, y ya hay un `LEFT JOIN … GROUP BY` sobre
ella en `queryRoutes`). Con un solo usuario no se nota; es barato hacerlo ahora, en los mismos
`initTables()`, que cuando moleste.

### MEJ-3 — Cerrar el círculo del coste de OpenAI

SEC-1 puso la puerta, pero quedan tres piezas que la propia documentación reconoce como pendientes y que
juntas forman el control real:
1. **Cuota por usuario/día** (reconocido como fuera de alcance en SEC-1). Una tabla
   `ai_usage(user_id, day, count)` y un tope.
2. **Redimensionar en el cliente, no solo en el servidor** (reconocido en IA-1). Hoy el cliente manda la
   imagen completa en base64 (`ImagePicker.android.kt`), así que el payload de subida y el pico de heap
   del servidor siguen intactos: `ImageIO.read` decodifica la imagen entera antes de reescalarla.
3. **Persistir el análisis** (hoy es un `remember`, ya documentado en 🟡). Es la única función premium y
   la más caro por uso, y su resultado se tira al salir de la pantalla. Una tabla `body_analyses` daría
   histórico de composición corporal **y** permitiría no re-cobrar un análisis repetido.

### MEJ-4 — Robustez de la decodificación de imágenes (IMG-1, ya documentado, pero se va a tocar al probar)

`ImageBitmapUtils.android.kt:12-14` hace `BitmapFactory.decodeByteArray(...).asImageBitmap()` sin
`inSampleSize` y sin comprobar el `null`. Lo menciono aunque esté documentado porque está en el camino
exacto de la sección 4 del plan de pruebas: `WeightScreen.kt:189` lo llama con los bytes completos de la
foto para pintar una vista previa de 300 dp. Con una foto de 12 MP eso son ~48 MB en ARGB_8888, más los
~4 MB del `ByteArray`, más los ~5,3 MB del base64, todo vivo a la vez. En un móvil modesto es un OOM
plausible, y el `decodeByteArray` devuelve `null` con un archivo corrupto → NPE. Arreglo estándar:
decodificar dos veces, la primera con `inJustDecodeBounds = true` para calcular `inSampleSize`.

### MEJ-5 — Endurecimiento del `GpsTrackingService` (más allá de BUG-N1)

- Usar `ServiceCompat.startForeground(this, id, notification, FOREGROUND_SERVICE_TYPE_LOCATION)` en vez
  de `startForeground(id, notification)`. El tipo ya está en el manifest, pero la forma explícita es la
  recomendada con `targetSdk 34+` y falla de manera más legible.
- Capturar la excepción de arranque. En Android 12+ arrancar un foreground service puede lanzar
  (`ForegroundServiceStartNotAllowedException`); hoy `BackgroundTrackingController.android.kt:14-20`
  llama a `startForegroundService` sin `try/catch`, así que un rechazo del sistema **crashea la app** en
  vez de degradar el seguimiento.
- `android:theme="@android:style/Theme.Material.Light.NoActionBar"` (manifest) es un tema **claro** en
  una app de interfaz oscura (`GymSmartColors.Background`): probablemente produzca un destello blanco al
  arrancar. Es polish, pero se verá en la primera prueba.

### MEJ-6 — Despliegue y empaquetado

- **No hay `.dockerignore`**, y el `Dockerfile` hace `COPY . .`. Como `.env` está en la raíz (ignorado por
  git, pero presente en el disco), un `docker build` local **lo mete en una capa de la imagen** junto con
  todas las claves. Añadir un `.dockerignore` con `.env`, `build/`, `.gradle/`, `.git/`.
- `isMinifyEnabled = false` en release y sin `signingConfig` (`composeApp/build.gradle.kts:135-139`),
  con `versionCode = 1`. Ya está en `04-produccion-playstore.md`; lo repito solo porque es el tipo de
  cosa que se olvida.
- **CORS no permite credenciales.** `plugins/CORS.kt` declara los hosts y métodos, pero no
  `allowCredentials = true`. Como la autenticación es por cookie, el cliente web (JS/Wasm) no podría
  autenticarse cross-origin ni con los hosts correctos. El usuario ya decidió no tocar CORS (el cliente
  web solo se usa en local, mismo origen), así que **no es acción** — solo conviene saberlo para cuando
  se retome la web, porque añadir el host de producción no bastará.

### MEJ-7 — Mover los modelos compartidos a `shared/`: ~15 clases duplicadas hoy

`05-deuda-tecnica.md` señala la duplicación de `FoodSearchResponse`/`Product`/`Nutriments` entre cliente
y servidor como *"candidatos naturales para `shared/`"*. Al revisar los modelos completos, el alcance
real es mucho mayor. Clases definidas **dos veces**, una en `composeApp/.../model/` y otra en
`server/.../model/` o en el propio archivo de ruta:

`User`, `RegisterRequest`, `LoginRequest`, `AuthResponse`, `ProfileRequest`, `UserProfile`,
`NutritionTargets`, `RoutePointDto`, `SaveRouteRequest`, `SaveRouteResponse`, `GpsRouteResponse`,
`GpsData`, `BodyAnalysisRequest`, `BodyAnalysisResponse`, `Nutriments`, `Product`, `FoodSearchResponse`,
`MealEntryRequest`.

Lo relevante es que **esta duplicación ya ha causado bugs reales**, no es solo estética:
- **BUG-N2** (la edad por fecha de nacimiento nunca funciona) es exactamente una divergencia de contrato
  entre las dos copias: el cliente escribe `DDMMYYYY`, el servidor parsea `dd/MM/yyyy`. Con un modelo
  único y un tipo de fecha compartido, no habría sido posible.
- El desajuste de `/nutrition/history` (números como `String` en el servidor, parseados con
  `toDoubleOrNull()` en el cliente) es otra: las dos copias ni siquiera usan el mismo tipo.
- `AuthResponse` tiene un campo extra solo en el cliente (`networkError`), lo cual **es correcto y
  deliberado** (está bien comentado), pero convive con 17 divergencias no deliberadas.

Lo barato aquí es que **`shared/` ya existe y ya es dependencia de los dos módulos**
(`implementation(projects.shared)` tanto en `composeApp/build.gradle.kts:93` como en
`server/build.gradle.kts:19`), y hoy solo contiene el `Platform.kt`/`Greeting.kt` del wizard. No hay que
crear infraestructura: solo mover archivos. `01-arquitectura.md` lo llama "vestigial" y sugiere no poner
lógica de negocio ahí — y los modelos de red no son lógica de negocio, son justo el caso de uso para el
que ese módulo existe.

Haría esto **después** de la ronda de pruebas y de los arreglos críticos, pero antes de la Fase 6: es la
única mejora estructural que reduce la probabilidad de volver a crear un bug de la clase de BUG-N2.

### MEJ-8 — Consistencia de la forma de la API

`/nutrition/mine` devuelve números tipados (`MealEntryWithTarget`), mientras `/nutrition/history` y
`/nutrition/favorites` devuelven `List<Map<String, String>>` con los números **como cadenas**
(`NutritionRoutes.kt:71-97`), que el cliente vuelve a parsear con `toDoubleOrNull()`
(`NutritionService.kt:72-80`). Tres endpoints de la misma funcionalidad con dos contratos distintos.
Unificar en modelos `@Serializable` quitaría el doble parseo y una fuente silenciosa de ceros.

---

## 11. Lo que está genuinamente bien hecho

Lo incluyo porque una revisión que solo lista defectos da una idea falsa del estado real, y porque
**nada de esto debería tocarse** en los arreglos que salgan de aquí.

- **`TursoService.executeBatch`** (`:39-83`) es el mejor código del servidor: comprueba el status HTTP y
  además el `type == "error"` de **cada** statement, e incluye el índice del statement en el mensaje de
  error. Eso es exactamente lo que pide un protocolo de pipeline.
- **`Nutriments?.estimatedKcalPer100()`** (`model/NutritionModels.kt`) razona sobre la ambigüedad real de
  los datos de Open Food Facts (que `energy-kcal` sin sufijo puede ser por ración y no por 100 g) y
  prefiere estimar desde los macros antes que confiar en un campo ambiguo. El comentario explica el
  *por qué*, no el *qué*. Es el nivel al que debería estar el resto.
- **El razonamiento de WEAR-3**: cambiar un `WorkManager` Android-only no verificable por un bucle en
  `commonMain` 100% verificable, tras releer el síntoma real, es exactamente el tipo de decisión que hay
  que tomar en un entorno sin SDK. Y la documentación dice explícitamente qué **no** resuelve.
- **No cachear resultados vacíos en OFF-1** (`FoodService.kt:126-128`): el detalle que separa una caché
  útil de una que congela falsos negativos 24 h.
- **RED-1 / `hasProfile(): Boolean?`**: distinguir "no sé" de "no" en el tipo, en vez de con un flag, es
  la solución correcta y la que impide que el bug vuelva.
- **`OnboardingScreen`** sigue siendo el modelo de la app: valida con mensajes concretos y maneja éxito
  **y** fallo (`:169-170`). VAL-1 hizo lo correcto extrayendo su lógica en vez de reescribirla.
- **SQL 100 % parametrizado** en los 40+ puntos de consulta. Ni una concatenación. Sin excepciones.
- **BCrypt** correctamente aplicado, y tokens de un solo uso con expiración en los tres flujos
  (verificación 24 h, reset 1 h), con comprobación de `used` antes de `expires_at`.
- **El sistema de continuidad `CLAUDE.md` + `CONTINUAR.md` funciona.** Como sesión nueva sin contexto,
  pude situarme y empezar a verificar en minutos. Las limitaciones marcadas con ⚠️ "sin verificar con
  build real" son honestas y resultaron ser exactamente las que no pude verificar yo tampoco.

---

## 12. Si tuviera que priorizar

No es una propuesta de trabajo —el acuerdo es probar antes de tocar código— sino mi lectura del orden
en que estas cosas merecen atención cuando se retome.

**Antes de probar** (minutos, evitan diagnósticos falsos):
1. `AppConfig.BASE_URL` → la IP de Tailscale, no `localhost` (PRU-1).
2. Decidir cómo se va a conceder premium para probar la IA, sabiendo que dura 1 hora (PRU-2).

**Antes de volver a desplegar en Render** (es la frontera real, y hoy el servidor está vivo en internet):
3. **SEC-N1** — firmar la cookie de sesión. Es el hallazgo más grave de esta revisión y son 3 líneas.
4. **SEC-N2** — quitar el `else -> "tok_visa"`, para que el gate premium signifique algo.
5. **BUG-N9** — timeouts en los clientes HTTP del servidor.
6. **SEC-N3** + **INC-1** — auth en `/food/*`, en este orden: primero unificar el cliente, luego la auth
   (al revés reproduce R1).

**Mientras se prueba, para no perseguir fantasmas**: tener a mano BUG-N12, BUG-N4 y BUG-N6, que son los
tres que harán que una prueba del plan parezca fallar por una razón distinta de la real.

**Arreglos de una palabra o una línea**, que tienen la mejor relación coste/beneficio de toda la lista:
7. **BUG-N1** → `START_NOT_STICKY` (una palabra) elimina la notificación de GPS zombi.
8. **BUG-N17** → `App.kt`, valor por defecto `{ it(false) }` en vez de `{}`: el botón de escanear en
   Desktop pasa de "roto en silencio" a dar un mensaje.
9. **INC-2** → normalizar la coma decimal en `ProfileValidation.kt`: arregla los tres formularios.
10. **BUG-N7** → añadir `&& selectedDate == today` a `NutritionScreen.kt:343`.

**Deuda de documentación**, barata y con impacto real en sesiones futuras:
11. **PLAN-1** → cabecera de "foto del 2026-10-26" en `01-arquitectura.md` y corregir su tabla de
    endpoints de §3 (es la parte que puede inducir a un error de seguridad).
12. **§9.1** → sincronizar los estados #11 y #12 de `03-seguridad.md`, que están marcados abiertos y
    llevan arreglados desde la Fase 5.

**Lo demás** puede entrar por severidad en el backlog normal. De todo ello, lo que más
desproporcionadamente barato/útil me parece es **MEJ-1** (tests de funciones puras): es además el único
ítem que no es opcional respecto al plan, porque la Fase 4 lo declaró como entregable y se cerró sin él
(**PLAN-2**). Esta revisión ha encontrado dos bugs (BAJ-1, INC-2) que un test de tres líneas habría
pillado solo, lo que es la mejor evidencia posible de que hacen falta.

---

## 13. Lo que verifiqué y resultó estar correcto

Para que el informe no dé una impresión falsa: estas son cosas que revisé específicamente **esperando
encontrar un problema y no lo había**. Las anoto porque descartar una hipótesis también es información
útil, y evita que una sesión futura las vuelva a investigar.

- **El onboarding con pulsera no se rompe.** Al ver que `visibleSteps` se salta el paso de ACTIVITY
  cuando `hasWearable == true` (`OnboardingScreen.kt:53-56`), supuse que `activityLevel` se enviaría
  vacío y el servidor devolvería `400 "Nivel de actividad inválido"`. **No ocurre**: la línea 267 asigna
  `activityLevel = "wearable"` al seleccionar "Sí, tengo pulsera" en el paso anterior. Correcto.
- **`compileKotlinJs` y `compileKotlinWasmJs` pasan**, como afirma la documentación. Verificado por
  ejecución.
- **El fallo de iOS es exactamente el documentado**, ni uno más: los 3 errores son todos
  `MainViewController.kt:5:57`. El "descubrimiento útil" de `CONTINUAR.md` (que iOS compila lo
  suficiente en Windows para diagnosticar) es correcto y sigue siendo válido.
- **El `.env` no está en git** ni lo está `local.properties` (`git ls-files` → 0 resultados para ambos),
  y `.gitignore` los cubre. La gestión de secretos en el repo es correcta.
- **El SQL sigue 100 % parametrizado** tras todos los cambios de la Fase 5, incluido el nuevo
  `executeBatch` (los `args` van como array JSON tipado, nunca interpolados en el `sql`).
- **La tabla `meals` no tiene `ON DELETE CASCADE`** mientras las demás sí — parecía un olvido, pero al no
  existir borrado de usuarios en la app, no tiene consecuencia práctica hoy. No lo cuento como hallazgo.
- **El `delay(500)` de `DashboardScreen`** sobrevive dentro del bucle de WEAR-3, pero es inofensivo (una
  espera única antes de la primera lectura, probablemente para dar tiempo a Health Connect). Solo lo
  menciono en §9.4 como imprecisión de la documentación, no como bug.
- **`GpsScreen` no permite guardar una ruta de 0 puntos**: el `08-backlog-mejoras.md` lo lista en 🟡
  ("no se impide guardar una ruta de 0 puntos"), pero `GpsScreen.kt:432` comprueba
  `activeRoute.points.isNotEmpty()` antes de abrir el diálogo. Ese ítem del backlog ya no aplica tal como
  está escrito (lo que sí ocurre es que parar con 0 puntos descarta la ruta en silencio, sin avisar).

---

# Anexo A — Investigación: conexión del wearable y cálculo de calorías

> **Esto no forma parte de la auditoría de arriba.** Es una investigación aparte que el usuario pidió
> el **2026-10-29**, después de la revisión: *"ver una mejor forma de conectar el wearable del reloj a la
> app para el cálculo de las calorías, y si es posible conectar cualquier pulsera Xiaomi"*. El usuario
> usa hoy una **Xiaomi Smart Band 10**.
>
> **Método**: relectura del código actual de wearable + investigación web (el usuario planteó
> explícitamente que "a lo mejor hubo cambios"). Las fuentes están enlazadas en §A.7. **No se tocó
> código**: el acuerdo de parar hasta probar la app sigue vigente.
>
> **Estado de cada afirmación**: lo que viene de documentación oficial o del código está marcado como
> verificado; lo que depende del comportamiento real de Mi Fitness en el dispositivo está marcado como
> **pendiente de medir**, porque las fuentes se contradicen y no se puede resolver sin el móvil.

## A.1 Punto de partida: qué hay hoy

`HealthConnectProvider.kt` (Android) implementa la interfaz común `HealthDataProvider`
(`getTodaySteps()`, `getTodayActiveCalories()`), usando `aggregate()` de Health Connect con
`StepsRecord.COUNT_TOTAL` y `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`, desde la medianoche
local hasta ahora. Dos permisos declarados: `READ_STEPS` y `READ_ACTIVE_CALORIES_BURNED`.
Dependencia: `androidx.health.connect:connect-client:1.1.0-alpha11`. Desktop devuelve 0; iOS/Web no
tienen implementación.

## A.2 Conclusión principal: no hace falta "ampliar la rama de relojes" — ya es universal

La premisa de la pregunta ("ir ampliando esta rama de relojes para que todos funcionen") es la que
aplicaría con una integración por marca. **Health Connect no funciona así, y es exactamente lo que el
proyecto ya usa.**

La app no habla con Xiaomi: habla con Health Connect, y es la **app compañera de cada marca** la que
escribe ahí. Mi Fitness escribe en Health Connect, y lo mismo hacen Zepp/Amazfit, Samsung Health, Garmin,
Fitbit o Huawei Health.

**Por tanto `HealthConnectProvider.kt`, tal como está hoy, ya soporta cualquier pulsera Xiaomi — y
también las que no son Xiaomi.** No hay código por marca que escribir, y no lo habrá. La Band 10 del
usuario funciona por la misma vía que una Band 7 o una Amazfit. Verificado que Mi Fitness comparte con
Health Connect, y que **Xiaomi no publica ninguna API pública para terceros**, así que esta es la única
ruta real de integración.

**La decisión arquitectónica del proyecto es la correcta y no debe cambiarse.** El margen de mejora está
en *cómo* se usa Health Connect, no en con qué se habla.

## A.3 Dos caminos alternativos, descartados con evidencia

**Conexión Bluetooth directa a la pulsera** (el enfoque de Gadgetbridge): descartado. Requiere extraer
una *auth key* de la app de Xiaomi, normalmente con el móvil rooteado o leyendo ficheros de log. En la
**Band 10 concretamente** se documenta que hubo que sacar el valor `encryptKey` del log de Mi Fitness, y
que la clave **se invalida al desparear** la pulsera de la app oficial. La extracción ha sido además
inconsistente entre versiones de firmware (en la Band 9 hay casos de clave nula). Inviable para una app
que use otra persona, y rompible con cada actualización de firmware. Para un TFG, directamente
descartable.

**Google Fit como respaldo o alternativa**: descartado. Google lo soporta **solo hasta finales de 2026**
y Health Connect es el reemplazo oficial para apps Android móviles. Las altas de nuevos desarrolladores
están cerradas desde mayo de 2024. Añadirlo hoy sería deuda técnica con fecha de caducidad conocida.

## A.4 🔴 WEAR-5 — El hallazgo más importante: puede que Mi Fitness no escriba calorías *activas*

**Pendiente de medir en dispositivo. Si se confirma, es la causa principal de la imprecisión percibida.**

Health Connect distingue dos tipos de registro de energía, y la diferencia es exactamente el metabolismo
basal:
- `ActiveCaloriesBurnedRecord` → energía activa, **excluye el BMR**.
- `TotalCaloriesBurnedRecord` → **activa + basal**.
- (`BasalMetabolicRateRecord` existe como tipo propio aparte.)

El diseño de GymSmart es **semánticamente correcto**: el servidor manda `targetKcal = BMR + ajuste`
cuando `activityLevel == "wearable"` (`ProfileService.computeTargets`), y el cliente suma las activas
(`NutritionScreen.kt:376`, `realGoal = kcalGoal + activeCalories`). Leer las **activas** es lo que toca,
y así lo validó la Fase 4 en `06-funcionalidades.md`.

**El problema**: las fuentes consultadas **no coinciden** en qué escribe realmente Mi Fitness. Una lista
pasos, pulso, sueño **y calorías**; otras listan solo pasos, sueño y pulso. No encontré una lista
autoritativa de qué apps escriben cada tipo de registro.

**Si Mi Fitness escribe solo las totales** (o no escribe energía en absoluto), entonces
`getTodayActiveCalories()` devuelve **0 indefinidamente**, el `aggregate()` no falla ni avisa (devuelve
`null` → `?: 0.0`), y el presupuesto del día se queda en **BMR puro** — que es un objetivo muy
restrictivo y coincide **exactamente** con el síntoma que el usuario describió como "los números no van
muy allá". La Fase 4 atribuyó ese síntoma a la lectura puntual, y WEAR-3 lo arregló; pero el refresco
puede no haber sido la única causa.

**Corolario crítico de diseño, si se decide añadir las totales como respaldo**: hay que **restarles el
BMR** antes de sumarlas al objetivo. Sumar `TotalCaloriesBurned` tal cual duplicaría el metabolismo
basal, porque el servidor ya lo usa como base — un error de entre 1.500 y 1.800 kcal/día, es decir
mucho peor que el problema que vendría a resolver.

**Cómo medirlo, sin escribir código** (5 minutos, durante la ronda de pruebas que ya está en marcha):
en el móvil, Health Connect → *Datos y acceso* → *Energía* / *Actividad*, y comprobar si Mi Fitness
aparece como fuente en **"Calorías activas"**, en **"Calorías totales"**, en ambas o en ninguna. Ese
dato decide todo lo demás de este anexo.

## A.5 Mejoras concretas, ordenadas por valor/esfuerzo

Numeradas para que puedan pasarse tal cual a `08-backlog-mejoras.md` (continúan la serie WEAR-1..4).

### 🟠 WEAR-6 — Diagnóstico de la conexión (la de mejor relación valor/esfuerzo)

Hoy hay **cuatro causas de fallo distintas que producen la misma salida** (un `0` o un `null`), y cada
una requiere una acción diferente del usuario:

| Causa real | Qué ve el usuario hoy | Qué debería hacer |
|---|---|---|
| Health Connect no instalado (Android ≤13) | "⚠️ Health Connect no disponible" | Instalarlo desde Play Store |
| Instalado, permiso no concedido | `0` / "—" sin explicación | Conceder el permiso |
| Permiso concedido, Mi Fitness no comparte | `0` / "—" sin explicación | Activar el compartido en Mi Fitness |
| Todo bien, pero sin datos sincronizados aún | `0` / "—" sin explicación | Abrir Mi Fitness y pulsar Sincronizar |

Health Connect permite saber **qué app escribió cada dato**: la respuesta de `aggregate()` expone los
`dataOrigins`, y se puede filtrar por origen en la propia petición. Con eso la app puede mostrar
"Fuente: Mi Fitness · 4.230 pasos" o "Permiso concedido, pero ninguna app está escribiendo datos".

Esto **arregla la UX y de paso responde a WEAR-5** con datos reales del dispositivo del usuario, sin
depender de documentación contradictoria. Además cierra **WEAR-4** (el campo `hasWearable` derivado de
tres formas contradictorias), porque pasaría a existir una única fuente de verdad sobre si hay wearable
conectado y funcionando.

Encaja sin romper nada en la arquitectura actual: `HealthDataProvider` seguiría siendo la interfaz común
(está bien diseñada, es agnóstica de plataforma), devolviendo un tipo más rico — por ejemplo un `sealed
class` con `Unavailable` / `PermissionDenied` / `NoData` / `Data(steps, activeKcal, sources)` — en vez de
un `Long`/`Double` que no puede expresar "no lo sé".

### 🟠 WEAR-7 — Lectura en segundo plano (lo que WEAR-3 dejó fuera a propósito)

WEAR-3 resolvió el caso "la pantalla está abierta y el número no se mueve", y dejó anotado que no
resuelve el caso de la app cerrada. Para eso hace falta:
- El permiso `READ_HEALTH_DATA_IN_BACKGROUND`, que es **una concesión aparte del usuario**: declararlo
  en el manifest es solo la precondición, hay que pedirlo por el contrato de permisos de Health Connect.
  Hay casos documentados de apps que lo declaran y nunca lo piden, con lo que **todas** las lecturas en
  background fallan.
- Comprobar antes `getFeatureStatus` para saber si el dispositivo soporta la función, según el codelab
  oficial de Google.
- Sin el permiso, una lectura en background lanza `SecurityException`.

### 🟡 WEAR-8 — API de cambios en lugar de releer todo cada 5 minutos

Hoy se repite el `aggregate()` completo cada `WEARABLE_POLL_INTERVAL_MS`. El patrón correcto es
`getChangesToken` + `getChanges`, que devuelve solo lo que cambió desde el último token. Detalles a
tener en cuenta:
- Los tokens **caducan** (las fuentes dicen ~30 días pero no coinciden en la cifra exacta, y no pude
  confirmarlo contra documentación de Google). Hay que tratar la caducidad como "resincronización
  completa", no como error de permisos.
- Health Connect **no tiene notificaciones push** de cambios (no existe equivalente a las *observer
  queries* de HealthKit), así que el patrón habitual sigue siendo WorkManager periódico + permiso de
  background. Es decir, WEAR-8 depende de WEAR-7 para tener sentido pleno.

### 🟡 WEAR-9 — Dependencia en versión alpha

`androidx.health.connect:connect-client:1.1.0-alpha11`. Es una alpha en la ruta crítica de la
funcionalidad. Conviene comprobar si ya existe una versión estable antes de construir WEAR-6/7/8 encima,
porque la API de `getFeatureStatus` y la de permisos de background han ido moviéndose entre versiones.

### Sobre añadir más tipos de registro

Tentador (pulso, `ExerciseSessionRecord`, `TotalCaloriesBurned`) pero **hay que ir con cuidado**: la
política de Google Play exige pedir **el mínimo necesario** y eliminar los permisos no usados (ver A.6).
Pedir pulso "por si acaso" es un motivo de rechazo, no una mejora. Lo justificable hoy es, como mucho,
`TotalCaloriesBurned` **si** WEAR-5 confirma que es la única fuente de energía disponible.

## A.6 Dos restricciones que conviene conocer ya

**Publicar en Play Store leyendo Health Connect exige trámite.** Hace falta el formulario *Health apps
declaration* en Play Console, una política de privacidad publicada, y un caso de uso principal aprobado
(salud, fitness, atención médica o investigación), con **justificación detallada por cada permiso**.
Google exige además limitar el acceso al mínimo necesario y retirar los permisos que no se usen. Esto
afecta a `04-produccion-playstore.md`, que debería recoger este requisito. Nota adicional de fuentes
secundarias (sin confirmar contra el texto oficial): una app que no sea principalmente de salud pero
acceda a datos de salud **sigue estando dentro del alcance** de la política.

**Hay un techo de frescura que no depende del código de la app.** La propia FAQ de Xiaomi dice que, por
el ciclo de sincronización entre la pulsera y Mi Fitness, **habrá diferencias en los datos**, y recomienda
pulsar *Sincronizar* en la app si se ven desactualizados. Es decir: aunque WEAR-6/7/8 estén perfectos, la
app verá lo que Mi Fitness haya subido a Health Connect, no lo que la pulsera tiene en ese instante. Útil
para calibrar expectativas y para redactar el mensaje de diagnóstico de WEAR-6 ("último dato: hace 20
min") en vez de prometer tiempo real.

## A.7 Orden propuesto

Respetando que no se toca código hasta que el usuario pruebe:

1. **Durante la ronda de pruebas** (coste: 5 min, sin código): comprobar en Health Connect si Mi Fitness
   escribe calorías activas, totales o ninguna → resuelve **WEAR-5**.
2. Si no escribe activas: leer también las totales **restándoles el BMR**. Cambio pequeño y localizado
   en `HealthConnectProvider.kt`, sin tocar el servidor ni el contrato de la API.
3. **WEAR-6** (diagnóstico + `dataOrigins`), que de paso cierra **WEAR-4**.
4. **WEAR-9** (revisar si hay estable) y luego **WEAR-7 + WEAR-8** como un bloque propio, ya que
   comparten el permiso de background y el WorkManager.

## A.8 Fuentes

- [Google Fit Migration FAQ — Android Developers](https://developer.android.com/health-and-fitness/health-connect/migration/fit/faq) (soporte de Fit hasta finales de 2026)
- [ActiveCaloriesBurnedRecord — Android Developers](https://developer.android.com/reference/android/health/connect/datatypes/ActiveCaloriesBurnedRecord) (excluye BMR)
- [TotalCaloriesBurnedRecord — Android Developers](https://developer.android.com/reference/android/health/connect/datatypes/TotalCaloriesBurnedRecord) (activa + basal)
- [Build a complete integration with Health Connect (codelab) — Android Developers](https://developer.android.com/codelabs/health-connect) (`getFeatureStatus` antes del permiso de background)
- [Publish your health app on Google Play — Android Developers](https://developer.android.com/health-and-fitness/health-connect/publish)
- [Health Content and Services — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16679511?hl=en) (formulario de declaración)
- [Permissions and APIs that Access Sensitive Information — Play Console Help](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en) (mínimo necesario)
- [Xiaomi FAQ: calorías, pasos y de pie en la pulsera](https://www.mi.com/uk/support/faq/details/KA-171111/) (ciclo de sincronización)
- [Xiaomi (Mi Band / Watch) integration — WalkFit](https://walkfit.welltech.com/help-center/integrations-devices/xiaomi-mi-band--watch-integration/) (cómo se activa el compartido con Health Connect)
- [Cómo vincular Xiaomi (Mi Fitness) a Health Connect — Healthy Chronos](https://healthychronos.com/en/mi-fitness/)
- [Xiaomi y Amazfit en Health Connect — FitMesh](https://www.fitmesh.fit/en/blog/xiaomi-amazfit-health-connect-data-dashboard) (afirma que escribe calorías)
- [Xiaomi — Gadgetbridge](https://gadgetbridge.org/gadgets/wearables/xiaomi/) y [Huami/Xiaomi server pairing](https://gadgetbridge.org/basics/pairing/huami-xiaomi-server/) (requiere token)
- [Gadgetbridge issue #5323 — Mi Smart Band 10](https://codeberg.org/Freeyourgadget/Gadgetbridge/issues/5323) (`encryptKey` del log)
- [xiaomi-band-ios-export (GitHub)](https://github.com/artyomxx/xiaomi-band-ios-export) (la clave se invalida al desparear)

> **Fiabilidad de las fuentes**: las de `developer.android.com`, `support.google.com` y `mi.com` son
> oficiales. Las de WalkFit, Healthy Chronos, FitMesh y Gadgetbridge son de terceros y se usan solo para
> el comportamiento observado de Mi Fitness y de la Band 10, que no está documentado oficialmente en
> ninguna parte — motivo por el cual **WEAR-5 queda pendiente de medir en el dispositivo** en vez de
> darse por cierto.
