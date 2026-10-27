# Backlog de bugs y mejoras — resultado de la Fase 4

Inventario priorizado de todo lo encontrado en la investigación exhaustiva de Fase 4 (2026-10-26):
GPS, wearable, análisis por IA, datos de alimentos, resiliencia de red y auditoría de pantallas.

Este documento es **la cola de trabajo de la Fase 5**. Cada entrada tiene severidad, ubicación concreta
y estado. Severidad medida por impacto real sobre el usuario, no por elegancia del código.

Leyenda: 🔴 crítico · 🟠 medio · 🟡 bajo · ✅ arreglado

---

## ✅ Arreglado

| # | Qué | Dónde |
|---|---|---|
| R1 | **Regresión introducida por el fix de seguridad de Fase 2**: el análisis corporal por IA usaba un `HttpClient` sin cookies, así que al exigir sesión en `/ai/body-analysis` empezó a devolver 401 y la función quedó muerta, fallando en silencio (`printStackTrace`). Ahora usa el cliente con cookies, se eliminó el cliente duplicado y los fallos muestran un mensaje visible al usuario. | `WeightScreen.kt` |
| R2 | **Crash al abrir la pantalla de Peso sin conexión**: `weightService.getHistory()` era la única llamada de servicio sin `try/catch`, dentro de un `LaunchedEffect` con `try/finally` sin `catch` → excepción no capturada. Ahora va envuelta en `runCatching`. | `WeightScreen.kt` |
| R3 | **El estado premium no se cargaba nunca sin historial de peso**: `subService.getStatus()` estaba solo en la rama `else`, así que un usuario premium sin pesos registrados veía "Solo Premium" y se le mandaba a pagar de nuevo. Ahora se carga siempre. | `WeightScreen.kt` |
| DB-1 | **Los errores de Turso se convertían en silencio en "éxito"/"sin datos"**: `TursoService.execute()` no comprobaba el status HTTP ni el campo `error` de cada statement. Ahora lanza `TursoException` si Turso falla, y se añadió el plugin `StatusPages` (antes no existía ningún manejo global de errores) para que cualquier excepción no capturada devuelva un JSON claro en vez de dejar al cliente sin respuesta. | `TursoService.kt`, `plugins/ErrorHandling.kt` (nuevo) |
| NUT-1 | **Un código de barras no encontrado se guardaba como comida de 0 kcal**: no se comprobaba el status HTTP antes de deserializar; un 404 con cuerpo `{"error":...}` se parseaba como un `Product` vacío válido. Ahora se comprueba `response.status.isSuccess()` antes de parsear. | `NutritionScreen.kt` |
| NUT-2 | **Una comida recién añadida no se podía editar ni borrar**: `/nutrition/add` no devolvía el id creado, así que la entrada local quedaba con `id = ""`. Ahora el servidor devuelve `{"id": "..."}` y el cliente lo usa para la entrada local. | `NutritionRoutes.kt`, `NutritionService.kt` (servidor y cliente), `NutritionScreen.kt` |
| NUT-3 | **Objetivos de nutrición inventados (2000 kcal, etc.) mostrados como reales si fallaba la carga del perfil**: no había ninguna señal de que esos valores no eran los del usuario. Ahora hay un aviso visible cuando no se pudieron cargar los objetivos reales. | `NutritionScreen.kt` |
| NUT-4 | **Productos sin datos nutricionales se podían añadir como comidas de 0 kcal**: la búsqueda de alimentos no filtraba resultados sin kcal/macros útiles, y no distinguía "sin resultados" de "falló la búsqueda". Ahora se filtran los productos sin datos útiles y se muestra un mensaje de "sin resultados" distinto del error de red. | `NutritionScreen.kt` |
| AUTH-1 | **Una cuenta registrada sin verificar quedaba bloqueada para siempre**: el login solo mostraba un texto ante `EMAIL_NOT_VERIFIED`, sin forma de introducir el código ni de reenviarlo. Ahora `LoginScreen` muestra el mismo paso de verificación (código + reenviar) que ya existía en `RegisterScreen`. | `LoginScreen.kt` |
| RED-1 | **Un fallo de red podía sobrescribir el perfil real del usuario**: `me()`/`hasProfile()` convertían cualquier excepción en una respuesta negativa, indistinguible de "no tienes sesión"/"no tienes perfil" — eso mandaba a Login o a rehacer el Onboarding (que sobrescribe el perfil real al guardar). Ahora `AuthResponse` tiene un campo `networkError`, `hasProfile()` devuelve `Boolean?` (`null` = no se pudo saber, nunca se trata como "no tiene perfil"), y el arranque de la app muestra una pantalla de carga/reintento en vez de adivinar el destino. **De regalo, arregla también RED-4** (pantalla en blanco indefinida al arrancar). | `UserSession.kt` (modelo), `AuthService.kt`, `ProfileService.kt`, `NavGraph.kt`, `LoginScreen.kt` |
| WEAR-1/2 | **El recuento del wearable podía contar de más y de menos a la vez**: se sumaban registros crudos (`readRecords`) sin deduplicar entre fuentes y sin paginar (truncaba en los primeros 1000 registros). Ahora usa `aggregate()` con `COUNT_TOTAL`/`ACTIVE_CALORIES_TOTAL`, que resuelve ambos de golpe. ⚠️ Sin verificar con build real (sin Android SDK en este entorno). | `HealthConnectProvider.kt` |
| GPS-4 | **Guardar una ruta hacía N+1 peticiones HTTP secuenciales a Turso** (45s-varios minutos para una ruta normal). `TursoService` ahora tiene `executeBatch()`, que manda varios statements en una sola llamada a `/v2/pipeline`; `saveRoute` lo usa en lotes de 200. Verificado con `:server:compileKotlin`. | `TursoService.kt`, `GpsService.kt` |
| GPS-2 | **Un hueco de señal >100m mataba la grabación en cascada**: el punto de referencia se quedaba congelado al descartar un salto grande, así que todos los puntos siguientes también quedaban lejos de él. Ahora el punto de referencia se actualiza aunque no se sume esa distancia al total. Verificado con `:composeApp:compileKotlinJvm`. | `GpsScreen.kt` |
| GPS-3 | **La ruta en curso solo vivía en memoria** y se perdía entera si Android mataba el proceso. Ahora se guarda periódicamente (cada 10 puntos, y al parar) en almacenamiento local (`RouteRecoveryStore`, nuevo, con `expect/actual` — real en Android vía `SharedPreferences`, no-op en el resto de plataformas porque el GPS no funciona ahí todavía); al reabrir la pantalla, si hay una ruta sin guardar se ofrece recuperarla o descartarla. Verificado con `:composeApp:compileKotlinJvm`, `compileKotlinJs` y `compileKotlinWasmJs` (todos `BUILD SUCCESSFUL`). ⚠️ El `actual` de Android (`RouteRecoveryStore.android.kt`) no se ha podido compilar en este entorno (sin Android SDK) — recomendado probar en un dispositivo/emulador real. | `RouteRecoveryStore.kt` (nuevo, común + 5 actuals), `GpsScreen.kt` |
| GPS-1 | **El seguimiento se detenía al bloquear el móvil** (sin foreground service). Ahora `GpsTrackingService` (nuevo) se arranca como foreground service con tipo `location` mientras se graba, con una notificación simple ("Grabando ruta GPS") — eso basta para que Android siga entregando ubicaciones en segundo plano, sin que el servicio tenga que recolectarlas él mismo. Controlado desde `GpsScreen.kt` vía `BackgroundTrackingController` (nuevo, `expect/actual`, no-op fuera de Android). Permisos nuevos en el manifest: `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS` (esta última sin flujo de permiso en runtime todavía — si no se concede, el servicio funciona igual pero la notificación puede no mostrarse en Android 13+). Verificado con `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs`. ⚠️ El `Service` y su `actual` de Android no se han podido compilar en este entorno (sin Android SDK) — es el cambio más invasivo de todo el backlog, recomendado encarecidamente probarlo en un dispositivo o emulador real antes de confiar en él. **Decidido por el usuario (2026-10-27): la notificación enriquecida (botón de "Parar", distancia/tiempo en vivo, pedir `POST_NOTIFICATIONS` en runtime) queda como mejora futura, no se hace ahora** — ver GPS-1b en 🟡 Bajos. | `GpsTrackingService.kt` (nuevo), `BackgroundTrackingController.kt` (nuevo, común + 5 actuals), `AndroidManifest.xml`, `GpsScreen.kt` |

Los tres primeros (R1-R3) se detectaron porque tres investigaciones independientes señalaron el mismo
síntoma. **Lección registrada en [02-convenciones.md](02-convenciones.md)**: al añadir una comprobación
de sesión a un endpoint, hay que verificar con qué cliente HTTP lo llama el cliente, y nunca derivar
estado de negocio de que una excepción haya ocurrido.

Todo lo anterior está verificado con `./gradlew :server:compileKotlin` y
`:composeApp:compileKotlinJvm` (ambos `BUILD SUCCESSFUL`) tras cada cambio, salvo `HealthConnectProvider.kt`
(WEAR-1/2), que es código Android puro y no se ha podido compilar en este entorno (sin Android SDK
instalado). Recomendado compilar/probar en un dispositivo o emulador Android antes de confiar en ese
cambio al 100%.

---

## 🔴 Críticos abiertos

### ✅ GPS-1 — El seguimiento se detenía al bloquear el móvil — Arreglado (⚠️ sin verificar con build real)
No existía ningún `Service` de Android en el proyecto. El tracking vivía en un `callbackFlow` dentro
del Composable, y con `targetSdk = 36`, en cuanto la app pasaba a segundo plano Android cortaba o
estrangulaba las actualizaciones de ubicación. Ver "✅ Arreglado" al principio del documento para el
detalle de la solución (`GpsTrackingService` + `BackgroundTrackingController`). La notificación mínima
(sin botón de "Parar", sin stats en vivo) fue una decisión tomada sin poder confirmarla con el usuario a
tiempo; el 2026-10-27 el usuario confirmó que mejorarla queda como mejora futura, no bloquea nada — ver
GPS-1b en 🟡 Bajos.

Este es el cambio más invasivo de todo el backlog de Fase 5 y el único que toca un `Service` de
Android nuevo — recomendado encarecidamente probarlo en un dispositivo o emulador real antes de
confiar en él.

### ✅ GPS-2 — Un hueco de señal mataba la grabación para el resto de la sesión — Arreglado
`GpsScreen.kt:57` descartaba un punto a más de 100 m del último aceptado **sin actualizar el punto de
referencia**, que quedaba congelado y hacía que todos los puntos siguientes también se descartaran en
cascada. Ver "✅ Arreglado" al principio del documento.

*Se combina con GPS-1*: el segundo plano provoca huecos, y los huecos mataban la grabación.

### ✅ GPS-3 — La ruta en curso solo vivía en memoria — Arreglado
`activeRoute` era un `remember` sin persistencia. Si Android mataba el proceso —muy probable en una
ruta larga con la pantalla apagada— se perdía la ruta completa. Ver "✅ Arreglado" al principio del
documento (`RouteRecoveryStore`).

### ✅ RED-1 — Un fallo de red puede sobrescribir el perfil del usuario (pérdida de datos) — Arreglado
Cadena completa del problema (histórica, ya cerrada):
1. `AuthService.me()` captura cualquier excepción y devuelve `success = false` → **"sin red" es
   indistinguible de "sin sesión"**.
2. `ProfileService.hasProfile()` devolvía `false` ante cualquier excepción (incluida una página de error
   502 de Render, que además parsea a mano).
3. `NavGraph.kt:60-69` interpretaba ese `false` como "este usuario no tiene perfil" y lo mandaba a
   **Onboarding**.
4. Rehacer el onboarding llama a `/profile/save`, que en el servidor es `INSERT OR REPLACE INTO
   user_profiles` → **sobrescribe peso, altura, edad y objetivos reales**, y además escribe en
   `weight_history`.

Era el único camino de pérdida de datos encontrado en toda la auditoría. Ver el arreglo completo en la
sección "✅ Arreglado" al principio de este documento.

### ✅ NUT-1 — Un código de barras no encontrado se guardaba como comida de 0 kcal — Arreglado
`NutritionScreen.kt:156-166` no comprobaba el código de estado. Cuando el producto no existe, el
servidor responde 404 con `{"error":"..."}` (`FoodRoute.kt:32`), pero como `Product` tiene los tres
campos nulables con valor por defecto y se usa `ignoreUnknownKeys = true`, ese JSON de error se
deserializaba sin excepción en un `Product` vacío, guardable en el diario. Ver "✅ Arreglado" arriba.

### ✅ NUT-2 — Una comida recién añadida no se podía editar ni borrar — Arreglado
`NutritionScreen.kt:185` creaba la entrada con `id = ""` porque `saveMealRemote` solo devolvía `Boolean`
y el UUID que genera el servidor nunca volvía al cliente. Rompía el flujo más común de la funcionalidad
principal ("me he pasado con los gramos, lo borro"). Ver "✅ Arreglado" arriba.

### ✅ NUT-3 — La pantalla de nutrición mostraba objetivos inventados como si fueran reales — Arreglado
`NutritionScreen.kt:108-111` inicializaba los objetivos hardcodeados (2000 kcal, etc.) y solo los
sobrescribía en el `onSuccess` del perfil, sin `onFailure`. Era el peor fallo de confianza del
inventario en una app cuyo propósito es contar calorías con fiabilidad. Ver "✅ Arreglado" arriba.

### ✅ AUTH-1 — Cuenta registrada sin verificar = callejón sin salida permanente — Arreglado
El paso de verificación por código vivía solo en el estado local de `RegisterScreen`; el login no
ofrecía forma de introducir el código ni de reenviarlo ante `EMAIL_NOT_VERIFIED`. Cuenta inutilizable
desde la app, sin recuperación posible. Ver "✅ Arreglado" arriba.

### ✅ DB-1 — Los errores de base de datos se convertían en silencio en "éxito" — Arreglado
`TursoService.execute()` no comprobaba el status HTTP ni el campo `error` de Turso. Un token caducado o
una cuota superada hacían que las lecturas devolvieran "no hay datos" y las escrituras fallidas se
reportaran como correctas. Ver "✅ Arreglado" arriba.

### SEC-1 — El gate premium no existe en el servidor
`ProfileService.isPremium()` **existe pero no se llama desde ningún sitio** (verificado con grep: solo
aparece su propia definición). El único control es de cliente (`WeightScreen.kt:173`). Tras el fix de
sesión, cualquier usuario registrado y logueado —gratis— puede llamar a `/ai/body-analysis` sin límite:
no hay cuota diaria, ni rate limit, ni caché, ni registro de consumo.

*Impacto*: tus créditos de OpenAI. Ver también [03-seguridad.md](03-seguridad.md).

---

## 🟠 Medios

### Rendimiento y coste

- ✅ **GPS-4 — Arreglado.** Guardar una ruta hacía una petición HTTP por punto (N+1). Ahora se manda en
  lotes de 200 statements por llamada a `/v2/pipeline`. Nota: sigue sin haber transacción real (Turso vía
  HTTP no la expone de forma sencilla aquí) — si falla a mitad de un lote grande, puede quedar una ruta
  con puntos incompletos; impacto bajo porque ahora son pocas llamadas en vez de cientos.
- **IA-1 — La imagen se manda a resolución completa, sin ninguna ganancia.** No existe redimensionado ni
  compresión en ningún punto (verificado por grep). Una foto de 12 MP son ~4 MB → ~5,3 MB en base64, y en
  la JVM un `String` es UTF-16, con al menos tres copias vivas por petición: **30-45 MB de heap por
  análisis** en una instancia de Render de 512 MB. Lo decisivo: gpt-4o reescala internamente la imagen a
  ≤768 px de lado corto antes de tokenizarla, así que esa resolución extra **se descarta**. Redimensionar
  a ~1024 px recorta el payload ~95% sin perder nada de calidad. Es el mejor ratio impacto/esfuerzo del
  inventario.
- **IMG-1 — Decodificación de bitmaps sin `inSampleSize`** (`ImageBitmapUtils.android.kt:12-14`): una foto
  de 12 MP se decodifica completa en ARGB_8888 (~48 MB) para mostrarla en 300 dp → riesgo real de OOM en
  móviles modestos. Además `decodeByteArray` devuelve `null` con un archivo corrupto y se llama
  `.asImageBitmap()` sin comprobarlo → NPE.
- **OFF-1 — Riesgo de rate limit de Open Food Facts.** Cada búsqueda puede disparar 2 peticiones
  (`FoodService.kt:70` + fallback línea 74, que probablemente son idénticas porque el filtro
  `countries_tags_en=spain` sobre el subdominio `world` no es la forma documentada de filtrar). Sin caché
  de ninguna clase, y todas las peticiones salen de **la única IP de Render**, compartida por todos los
  usuarios. OFF limita `search.pl` de forma agresiva por IP. Si bloquea la IP, la búsqueda de alimentos
  muere para todos a la vez, y el `catch` lo presenta como "sin resultados". El `User-Agent` sí cumple la
  política ✅.

### Exactitud de los datos

- ✅ **WEAR-1/WEAR-2 — Arreglado.** El recuento del wearable podía contar de más (varias fuentes
  escribiendo los mismos pasos, sin deduplicar) y de menos a la vez (`readRecords` pagina y se ignoraba
  el `pageToken`, truncando en la primera página de 1000 registros). `HealthConnectProvider` ahora usa
  `aggregate()` con `StepsRecord.COUNT_TOTAL` y `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`, que
  deduplica solapes entre fuentes y no pagina. **⚠️ Sin verificar con build real** (este entorno no tiene
  Android SDK) — recomendado compilar/probar en un dispositivo o emulador Android antes de confiar al
  100%.
- **WEAR-3 — Lectura puntual en vez de sincronización.** Cada pantalla lee una sola vez al entrar
  (`LaunchedEffect(Unit)`, con un `delay(500)` arbitrario en Dashboard). Si abres la app por la mañana,
  el presupuesto de kcal se queda congelado en el valor de esa hora todo el día. Es la causa más probable
  de la imprecisión percibida en modo pulsera (ver [06-funcionalidades.md](06-funcionalidades.md)).
- **TZ-1 — El historial de peso usa la zona horaria del servidor.** `ProfileService.kt:111` usa
  `java.time.LocalDate.now()`, que en Render es UTC. Pesarse a la 00:30 en España queda registrado con la
  fecha del día anterior, y la lógica de "un registro por día" se comporta raro en ese margen. Nutrición
  sí lo hace bien (manda la fecha local del dispositivo): es inconsistente entre features.
- **GPS-5 — Se usa `loc.speed` sin comprobar `loc.hasSpeed()`.** El filtro de `GpsScreen.kt:56` descarta
  todo punto bajo 0,8 km/h; cuando una posición no trae velocidad, Android devuelve `0.0` y el filtro lo
  interpreta como "parado". Mismo patrón con `altitude` y `accuracy`: sin dato de precisión,
  `loc.accuracy` vale `0.0` y el filtro `> 25f` la trata como la posición más precisa posible.
- **GPS-6 — La duración incluye el tiempo parado**, porque se calcula como "ahora menos el inicio" al
  guardar, incluyendo el rato que el diálogo estuvo abierto. Cualquier ritmo (min/km) derivado sale mal.
- ✅ **NUT-4 — Arreglado.** No se filtraban los productos sin datos nutricionales: `nutriments = null`
  se mostraba como "0 kcal/100g" y se podía añadir. Ahora se filtran y se distingue "sin resultados" de
  "falló la búsqueda".
- **IA-2 — Las cifras del análisis no son reproducibles.** `OpenAIService.kt` no fija `temperature` (por
  defecto 1.0 → máxima variabilidad) ni usa `response_format: {"type":"json_object"}`, que gpt-4o soporta
  de forma nativa. La misma foto analizada dos veces da porcentajes distintos. Además el prompt instruye
  explícitamente al modelo a no admitir que no puede estimar y a no devolver campos vacíos: **se fuerza
  que siempre salga un número**, aunque la foto no lo permita, sin ningún indicador de confianza.
- **IA-3 — El parseo del análisis es frágil.** Los 5 campos de `BodyAnalysisResponse` son `String` no
  nulables sin valor por defecto: si el modelo omite uno, devuelve números en vez de strings, o se niega
  a responder, el parseo lanza excepción.

### Resiliencia

- **RED-2 — No hay ni un solo `HttpTimeout` configurado** en todo el cliente, ni `expectSuccess`, ni
  retry. Peor: en Android hay **dos motores de Ktor en el classpath a la vez** (`ktor-client-android` y
  `ktor-client-okhttp`) y los clientes se crean sin motor explícito, así que el elegido no es
  determinista. Sus defaults difieren mucho (≈100 s vs 10 s de lectura), de modo que **que la app
  sobreviva a un cold start de Render depende de qué motor cargue**. En Desktop el motor Java puede
  esperar indefinidamente.
- **RED-3 — No hay manejo centralizado de 401.** Cuando la sesión caduca, la app no lleva al login:
  todas las pantallas aparecen vacías o con valores por defecto, sin explicación. Agravante: el
  almacenamiento de cookies guarda solo `nombre=valor`, **descartando expiración, dominio, path y flag
  `secure`**, así que la cookie se sigue enviando indefinidamente.
- **RED-4 — Pantalla en blanco al arrancar.** `NavGraph.kt:71` hace `if (startDestination == null)
  return`: mientras se comprueba la sesión no se dibuja nada —ni splash ni indicador—, lo que con un cold
  start se lee como "la app está colgada".
- **RED-5 — Sin ninguna capacidad offline.** No hay caché, ni base de datos local, ni cola de escrituras
  pendientes; lo único que se persiste es la cookie. En un gimnasio sin cobertura la app no sirve, y al
  registrar una comida sin red **se descarta lo que el usuario había introducido** (flujo "servidor
  primero"), obligándole a buscar el producto y teclear los gramos otra vez. Para una app de conteo de
  kcal que se usa precisamente en el gimnasio y en la calle, es la limitación funcional más grande.
- **ERR-1 — Patrón generalizado de tragarse errores** devolviendo valores vacíos, de modo que el usuario
  no distingue "no hay datos" de "ha fallado": `ProfileScreen.kt:446-450` guarda el perfil sin
  `onFailure` (el spinner se apaga y no pasa nada); `WeightScreen` cerraba el diálogo como si hubiera
  guardado aunque fallara; `getFoodHistory`/`getFavorites` devuelven `emptyList()`;
  `GpsService.deleteRoute` **no comprueba el status** y devuelve éxito siempre (sus hermanas
  `publishRoute`/`unpublishRoute` sí lo comprueban: inconsistencia clara).
- **IA-4 — Todos los fallos del análisis son silenciosos también en el servidor**:
  `BodyAnalysisRoute.kt:37-41` responde `mapOf("error" to ...)` **con HTTP 200**, así que el cliente
  intenta deserializarlo como un análisis válido y falla. Además devuelve `e.message` crudo, que incluye
  la respuesta completa de OpenAI (fuga de detalle interno).

### Validación y estado

- **VAL-1 — `ProfileScreen` y `WeightScreen` no validan la entrada.** Convierten con `toDoubleOrNull() ?:
  0.0` y envían **ceros** si el campo está vacío o mal escrito. Disparador muy probable: el teclado
  español ofrece **coma**, y `"75,5".toDoubleOrNull()` es `null` → se manda 0.0 → el servidor rechaza →
  no se muestra ningún error. Además el campo de peso de `WeightScreen` no fija `keyboardType`.
  **La validación correcta ya existe en `OnboardingScreen.kt:610-630`** (rangos reales y mensajes claros)
  — extraerla a un validador compartido arregla ambas pantallas de golpe.
- **VAL-2 — Guardar los datos personales recalcula los objetivos a escondidas**
  (`ProfileScreen.kt:266-293`) y, al hacerlo, **se salta la validación `rateError`** que sí tiene el
  guardado físico: si el usuario cambió el objetivo a "perder peso" sin elegir ritmo, su déficit se graba
  como 0.0 → objetivos de mantenimiento, en silencio. El `catch (_: Exception) {}` se come cualquier
  error.
- **PERM-1 — Los callbacks de permisos pueden perderse en una recomposición.**
  `MainActivity.kt:54-55` declara `pendingLocationCallback` y `pendingCameraCallback` como `var` locales
  **sin `remember`** dentro de `setContent`: cada recomposición los resetea a `null` mientras el launcher
  sobrevive. El usuario concede el permiso y no se abre nada (hay que volver a pulsar). Afecta a cámara
  **y** a GPS.
- **IOS-1 — `rememberImagePicker` es `TODO()` en iOS, JS y Wasm**, y se invoca sin condiciones al
  componer (`WeightScreen.kt`), no al pulsar el botón → en esas plataformas abrir la pantalla de peso
  lanza `NotImplementedError`. Relevante para el trabajo de iOS: no basta con cablear `App()`.
- **EMAIL-1 — Entrar en "Cambiar contraseña" envía un email automáticamente** sin confirmación
  (`ForgotPasswordScreen.kt:50-56`), gastando cuota de Brevo cada vez que se abre la pantalla por
  curiosidad, y marca `emailSent = true` **sin comprobar el resultado**: si el envío falló, igualmente
  dice "revisa tu correo".
- **WEAR-4 — `hasWearable` se calcula de tres formas contradictorias**: `ProfileScreen.kt:444` lo deriva
  de si Health Connect devolvió pasos en ese instante (aunque el usuario haya elegido "Moderado"),
  `WeightScreen.kt:455` de `activityLevel == "wearable"`, y `OnboardingScreen.kt:163` del toggle
  explícito. Hoy es inofensivo porque el servidor lo deriva él mismo e ignora el campo, pero es un campo
  que viaja con tres significados.
- **HTTP-1 — Proliferación de clientes HTTP que nunca se cierran.** 4 configuraciones distintas y 5
  instanciaciones de `AuthService()` (una por pantalla), cada una con su pool de conexiones y dispatcher,
  y `close()` no aparece en ningún sitio. Es **la causa raíz directa de R1**: con varios clientes es
  fácil coger el que no lleva cookies. Y cualquier arreglo de timeouts/retry/401 hay que aplicarlo hoy en
  4 sitios, lo que garantiza que se vuelva a desincronizar. Es el argumento más fuerte para el cliente
  único que ya estaba en [05-deuda-tecnica.md](05-deuda-tecnica.md).
- **UI-1 — El desplegable de actividad puede quedar incoherente**: la opción "Pulsera" solo se añade si
  Health Connect devolvió pasos, así que un perfil guardado con `activityLevel = "wearable"` y permiso
  revocado deja la selección vacía (`ProfileScreen.kt:384`).
- **UI-2 — El nombre del usuario puede mostrar un mensaje del servidor**: `ProfileScreen.kt:137` hace
  `response.user?.name ?: response.message` → la cabecera puede mostrar literalmente "No hay sesión
  activa" como si fuera el nombre.

---

## 🟡 Bajos

- **Seguridad/configuración**: `usesCleartextTraffic="true"` en el manifest (resto de la época de IPs
  locales; Play Store lo señala) · `allowBackup="true"` choca con el cifrado nuevo de la cookie (la clave
  maestra no se respalda, así que tras restaurar en otro móvil cae al almacenamiento sin cifrar; conviene
  excluir esas preferencias del backup) · `e.message` crudo de OpenAI expuesto al cliente · el email va
  sin codificar en un segmento de path (`NavGraph.kt:32`).
- **Datos de alimentos**: sin paginación (`page_size=20` fijo, lo que esté en el puesto 25 es
  inalcanzable) · sin `lc=es`, así que los nombres llegan en el idioma en que se dio de alta el producto ·
  sin mensaje de "sin resultados" (un error de red se ve igual que una búsqueda vacía) · `search.pl` es el
  endpoint legacy que OFF está retirando en favor de `/api/v2/search` · escanear algo que no es un código
  de producto (QR, ISBN) no produce ninguna reacción, y no hay linterna ni opción de teclear el código.
- **Análisis IA**: el resultado **no se persiste en ningún sitio** (es un `remember`), así que se pierde
  al salir: no hay histórico de composición corporal ni comparación en el tiempo. Siendo la única función
  premium y la más caro por uso, una tabla `body_analyses` es la mejora de producto más evidente ·
  `data:image/jpeg` hardcodeado para imágenes que pueden ser PNG.
- **Código muerto / inconsistencias**: `AuthService.changePassword` no lo llama ninguna pantalla (y por
  tanto `/auth/change-password` se queda sin cliente) · `TrainingScreen` está registrada en el NavGraph
  pero **nada navega a ella**, así que no hace falta "retirarla del menú": ya no está en ninguno ·
  `derivedStateOf` sin `remember` (`ProfileScreen.kt:121,127`) se recrea en cada recomposición, anulando
  su propósito · ruta hardcodeada como string en `MyRoutesScreen.kt:67` en vez de la constante ·
  `FoodSearchResponse`/`Product`/`Nutriments` duplicados entre cliente y servidor (candidatos naturales
  para `shared/`, hoy sin lógica) · la fórmula de TDEE duplicada entre servidor y vista previa de
  onboarding.
- **GPS**: O(n) de copia de lista por punto más recomposición completa del mapa en cada emisión · no se
  muestra tiempo transcurrido ni ritmo (min/km) mientras se graba, que es la métrica clave para correr ·
  cerrar el diálogo de guardado tocando fuera conserva los puntos, así que el siguiente "Iniciar ruta"
  fusiona dos rutas distintas · `formatDouble` pierde el signo con valores entre -1 y 0 · no se impide
  guardar una ruta de 0 puntos.
- **GPS-1b — Mejorar la notificación del foreground service** (decidido por el usuario el 2026-10-27:
  mejora futura, no se hace ahora): añadir botón de "Parar" en la notificación (requiere propagar el
  evento desde `GpsTrackingService` de vuelta al estado de Compose, p. ej. con un `PendingIntent` a un
  `BroadcastReceiver` o a la propia `Activity`), mostrar distancia/tiempo en vivo en el texto de la
  notificación, y pedir `POST_NOTIFICATIONS` explícitamente en runtime (Android 13+) en vez de depender
  del permiso ya concedido o no. Cambio localizado en `GpsTrackingService.kt` y `MainActivity.kt` cuando
  se decida hacerlo.
- **Auth**: reenviar el código de verificación acumula tokens válidos sin invalidar los anteriores ·
  `ProfileScreen.kt:146-148` compara cambios como strings ("75.0" vs "75"), así que marca cambios
  inexistentes · `OnboardingScreen.kt:156-158` usa `toDouble()`/`toInt()` sin red, seguro hoy solo por el
  orden de los pasos · la caducidad de la tarjeta no se valida (irrelevante mientras el pago no sea real).

---

## Lo que está bien hecho (no tocar, y reutilizar)

- **`OnboardingScreen` es el modelo a seguir**: valida rangos reales con mensajes claros y maneja éxito
  **y** fallo de forma visible. La lógica de validación correcta ya existe en la app; el problema es que
  las pantallas que editan los mismos campos en el día a día no la reutilizan.
- La fórmula de TDEE y macros del servidor (Mifflin-St Jeor + ISSN) es correcta y estándar.
- `LocationService` usa `FusedLocationProviderClient` por eventos con filtro de distancia — la base del
  GPS está bien construida; lo que falla es todo lo que la rodea.
- El SQL está 100% parametrizado, las contraseñas usan BCrypt correctamente, y los tokens de
  verificación/reset son de un solo uso con expiración.
- `GpsService` (servidor) comprueba la propiedad por `user_id` en todas sus operaciones.
- El `User-Agent` de Open Food Facts cumple la política de la API.
- `FoodService` sí configura timeouts y sí usa `fields=` para no descargar productos completos.
- El formulario de tarjeta es el mejor validado de la app (irónicamente, el del pago que no funciona).
- El flujo "servidor primero" al registrar comidas evita estado fantasma: no marca como guardado algo que
  no se guardó. Es lo correcto mientras no haya cola de sincronización.

---

## Orden de ataque para la Fase 5 — estado

1. ✅ **Pérdida de datos y funciones muertas** — completo: RED-1, NUT-2, AUTH-1, NUT-1, DB-1.
2. ✅ **Fiabilidad del conteo de kcal (datos)** — NUT-3, NUT-4 y WEAR-1/WEAR-2 arreglados. Queda
   **WEAR-3** (sincronización en segundo plano en vez de lectura puntual) como mejora de arquitectura
   más grande, no un fix puntual — pendiente de decidir si se hace ahora o se deja para más adelante.
3. ✅ **GPS como funcionalidad real** — completo: GPS-1, GPS-2, GPS-3, GPS-4. GPS-1 es el cambio más
   invasivo de todo el backlog (`Service` de Android nuevo) y el que más necesita probarse en un
   dispositivo/emulador real antes de confiar en él — no se pudo acordar el enfoque exacto con el
   usuario de antemano, así que se tomó la decisión más simple y estándar (ver detalle en el hallazgo).
4. ⏳ **Resiliencia transversal** — pendiente: HTTP-1 (cliente único) como base, y encima RED-2
   (timeouts), RED-3 (401 centralizado).
5. ⏳ **Coste y seguridad** — pendiente: SEC-1 (gate premium real), IA-1 (redimensionar imagen antes de
   enviarla a OpenAI), OFF-1 (caché de búsquedas).
6. El resto por severidad.
