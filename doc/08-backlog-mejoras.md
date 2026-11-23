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

### ✅ SEC-1 — El gate premium no existe en el servidor — Arreglado
`ProfileService.isPremium()` existía pero no se llamaba desde ningún sitio (verificado con grep: solo
aparecía su propia definición). El único control era de cliente (`WeightScreen.kt:173`), que no protege
nada real — una petición directa a la API lo salta sin problema. Ahora `bodyAnalysisRoute` comprueba
`profileService.isPremium(session.userId)` antes de llamar a OpenAI y responde `403 Forbidden` si no lo
es. **Sigue sin haber** cuota diaria, rate limit ni registro de consumo para usuarios premium — eso
limitaría el gasto incluso de un usuario legítimo que abusara del botón; no se hizo porque no estaba en
el alcance pedido ("gate premium real") y añade una tabla/lógica nueva — candidato a backlog futuro si
el uso real lo justifica. Verificado con `:server:compileKotlin`. | `BodyAnalysisRoute.kt`,
`plugins/Routing.kt`

*Impacto*: tus créditos de OpenAI. Ver también [03-seguridad.md](03-seguridad.md).

---

## 🟠 Medios

### Rendimiento y coste

- ✅ **GPS-4 — Arreglado.** Guardar una ruta hacía una petición HTTP por punto (N+1). Ahora se manda en
  lotes de 200 statements por llamada a `/v2/pipeline`. Nota: sigue sin haber transacción real (Turso vía
  HTTP no la expone de forma sencilla aquí) — si falla a mitad de un lote grande, puede quedar una ruta
  con puntos incompletos; impacto bajo porque ahora son pocas llamadas en vez de cientos.
- ✅ **IA-1 — Arreglado.** La imagen se mandaba a OpenAI a resolución completa sin ninguna ganancia: una
  foto de 12 MP son ~4 MB → ~5,3 MB en base64, y gpt-4o reescala internamente a ≤768px de lado corto antes
  de tokenizarla, así que esa resolución extra se descartaba. Ahora `OpenAIService.resizeIfNeeded()`
  decodifica la imagen (`javax.imageio`, sin dependencias nuevas) y la reescala a un máximo de 1024px de
  lado largo con `Image.SCALE_SMOOTH` antes de mandarla a OpenAI; si la decodificación falla (formato raro,
  imagen corrupta) se manda la original en vez de romper el análisis por un paso que es puramente de
  optimización. **Importante — alcance real de este fix**: reduce el payload que llega a OpenAI y el heap
  de esa llamada, pero **no** reduce el payload cliente→servidor (el cliente sigue mandando la imagen a
  resolución completa en el `POST /ai/body-analysis`; reducir eso necesitaría tocar el `ImagePicker` de
  cada plataforma, fuera de alcance de este cambio — solo Android tiene implementación hoy, ver IOS-1).
  Verificado con `:server:compileKotlin`. | `OpenAIService.kt`
- **IMG-1 — Decodificación de bitmaps sin `inSampleSize`** (`ImageBitmapUtils.android.kt:12-14`): una foto
  de 12 MP se decodifica completa en ARGB_8888 (~48 MB) para mostrarla en 300 dp → riesgo real de OOM en
  móviles modestos. Además `decodeByteArray` devuelve `null` con un archivo corrupto y se llama
  `.asImageBitmap()` sin comprobarlo → NPE.
- ✅ **OFF-1 — Arreglado (parcial).** No había ninguna caché, y todas las peticiones salían de la única
  IP de Render, compartida por todos los usuarios; OFF limita `search.pl` de forma agresiva por IP, así
  que si la bloquea, la búsqueda de alimentos muere para todos a la vez. Ahora `FoodService` tiene una
  caché en memoria con TTL de 24h (`TtlCache`, sin dependencias nuevas) para `searchFood()` y
  `lookupBarcode()`: la misma búsqueda o el mismo código de barras, repetidos por cualquier usuario
  dentro de ese plazo, no generan una petición nueva a OFF. Un resultado vacío o un fallo **nunca se
  cachea** (podría ser un fallo transitorio de OFF, no "este alimento no existe" — cachearlo congelaría
  ese falso negativo 24h). Límite de 500 entradas por caché para no crecer sin control; al llenarse,
  limpia caducadas primero y si no basta se vacía entera (no es LRU real, innecesario a esta escala).
  **Sigue sin arreglar** la segunda mitad del hallazgo: cada búsqueda sin caché previa puede seguir
  disparando 2 peticiones (español + fallback global), porque son casos distintos (no se investigó si
  son idénticas de verdad); y `search.pl` sigue siendo el endpoint legacy que OFF está retirando — ambos
  quedan en 🟡 Bajos, de menor impacto ahora que existe la caché. Verificado con
  `:server:compileKotlin`. | `FoodService.kt`

### Exactitud de los datos

- ✅ **WEAR-1/WEAR-2 — Arreglado.** El recuento del wearable podía contar de más (varias fuentes
  escribiendo los mismos pasos, sin deduplicar) y de menos a la vez (`readRecords` pagina y se ignoraba
  el `pageToken`, truncando en la primera página de 1000 registros). `HealthConnectProvider` ahora usa
  `aggregate()` con `StepsRecord.COUNT_TOTAL` y `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`, que
  deduplica solapes entre fuentes y no pagina. **⚠️ Sin verificar con build real** (este entorno no tiene
  Android SDK) — recomendado compilar/probar en un dispositivo o emulador Android antes de confiar al
  100%.
- ✅ **WEAR-3 — Arreglado, con un enfoque más simple que el planteado originalmente.** Cada pantalla leía
  una sola vez al entrar (`LaunchedEffect(Unit)`/`LaunchedEffect(selectedDate)`, con un `delay(500)`
  arbitrario en Dashboard). Si se abría la app por la mañana, el presupuesto de kcal se quedaba
  congelado en el valor de esa hora todo el día — causa más probable de la imprecisión percibida en modo
  pulsera (ver [06-funcionalidades.md](06-funcionalidades.md)).
  **Decisión de diseño**: el análisis original de esta misma entrada proponía mover la lectura a
  `WorkManager` periódico con caché local — arquitectura más grande, Android-only, y con el mismo
  problema de no poder verificarse en este entorno que `GPS-1`. Revisando el síntoma real descrito (el
  número se queda obsoleto *mientras el usuario está mirando la pantalla*, no solo con la app cerrada),
  un refresco periódico **dentro de la pantalla ya abierta** resuelve exactamente ese síntoma con mucho
  menos riesgo: no toca código Android, no añade dependencias, y es 100% verificable en este entorno
  (vive entero en `commonMain`). Se implementó así: `NutritionScreen.kt` y `DashboardScreen.kt` ahora
  relanzan la lectura de pasos/calorías activas cada `WEARABLE_POLL_INTERVAL_MS` (5 minutos, nueva
  constante en `HealthDataProvider.kt`) mientras la pantalla está visible, en vez de leer una sola vez;
  el bucle se cancela solo al salir de la pantalla (ciclo de vida normal de `LaunchedEffect`). Leer
  Health Connect es una consulta local sin coste de red/cuota, así que un intervalo corto no tiene
  contraindicación. **Lo que esto NO resuelve** (y si en el uso real resulta insuficiente, ahí sí
  compensaría el `WorkManager`): el número sigue sin actualizarse si la app está completamente cerrada o
  en segundo plano sin esa pantalla abierta — pero ese es un caso mucho menos común que "tengo la
  pantalla abierta y el número no se mueve", que era el síntoma descrito originalmente. Verificado con
  `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs` (los tres `BUILD SUCCESSFUL`,
  sin ninguna limitación de Android SDK porque no se tocó código Android). | `HealthDataProvider.kt`,
  `NutritionScreen.kt`, `DashboardScreen.kt`
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
- ✅ **IA-2 — Arreglado.** `OpenAIService.kt` no fijaba `temperature` (por defecto 1.0 → máxima
  variabilidad) ni usaba `response_format: {"type":"json_object"}`, que gpt-4o soporta de forma nativa —
  la misma foto analizada dos veces podía dar porcentajes distintos. Ahora la petición fija
  `temperature = 0.2` (bajo pero no 0: sigue siendo una estimación, no busca ser robótica, pero reduce
  mucho la variabilidad) y `response_format: json_object`. Además el prompt obligaba al modelo a no
  admitir nunca que no podía estimar algo y a no devolver campos vacíos — **se suavizó**: los 4 campos
  numéricos/categóricos siguen siendo obligatorios (si se dejan abiertos, rompe el contrato con el
  cliente), pero ahora se le permite explícitamente usar el campo `summary` (texto libre) para avisar
  cuando la estimación es poco fiable, en vez de fingir una certeza que no tiene. Verificado con
  `:server:compileKotlin`. | `OpenAIService.kt`
- ✅ **IA-3 — Arreglado.** Los 5 campos de `BodyAnalysisResponse` son `String` no nulables sin valor por
  defecto: si el modelo devolvía un número en vez de un string para alguno (p. ej. `"bodyFat": 15` en vez
  de `"15"`), el `Json` global por defecto (estricto con los tipos) lanzaba excepción y se perdía el
  análisis entero por un detalle de formato que no afecta al contenido real de la respuesta. Ahora se
  decodifica con una instancia `Json { isLenient = true; ignoreUnknownKeys = true }` dedicada, que tolera
  esa clase de discrepancias de tipo. **No cubre** el caso de que el modelo omita un campo entero o se
  niegue a responder del todo — ahí sigue lanzando excepción (correcto: no hay nada razonable que
  inventar en el cliente para un campo que no vino). Verificado con `:server:compileKotlin`. |
  `OpenAIService.kt`

### Resiliencia

- ✅ **RED-2 — Arreglado (parcial).** No había ningún `HttpTimeout` configurado, y en Android había dos
  motores de Ktor a la vez en el classpath (`ktor-client-android` y `ktor-client-okhttp`), así que el
  elegido no era determinista. Se quitó `ktor-client-android` del classpath (queda solo OkHttp, el más
  fiable de los dos) y se instaló `HttpTimeout` en el único `HttpClient` de la app (ver HTTP-1):
  `requestTimeoutMillis = 45_000`, `connectTimeoutMillis = 15_000`, `socketTimeoutMillis = 45_000` —
  generosos a propósito por los cold starts de Render. **Nota a vigilar**: el análisis corporal por IA
  (IA-1 sigue sin redimensionar la imagen) ahora también tiene este límite de 45s; si en el uso real
  llega a cortarse por timeout antes de que OpenAI responda, es un ajuste de una línea (timeout más alto
  solo para esa llamada). Verificado con `:composeApp:compileKotlinJvm`, `compileKotlinJs`,
  `compileKotlinWasmJs` (los tres `BUILD SUCCESSFUL`); sin verificar con build real de Android (sin SDK
  en este entorno) — el cambio de motor es exactamente el tipo de cosa que conviene probar antes de
  confiar en los tiempos de carga reales. | `composeApp/build.gradle.kts`, `AuthService.kt`
- ✅ **RED-3 — Arreglado (parcial).** No había manejo centralizado de 401: cuando la sesión caducaba a
  mitad de uso, cada pantalla se quedaba con datos vacíos o valores por defecto, sin explicación. Ahora
  el `HttpClient` único tiene un `HttpResponseValidator` que detecta cualquier 401 fuera de las rutas de
  `/auth` (esas ya gestionan su propio 401 como parte del flujo normal — no hay sesión todavía, no es que
  haya caducado) y pone a `true` `AuthService.sessionExpired`; `NavGraph` lo observa y manda al login con
  un aviso ("Tu sesión caducó, inicia sesión de nuevo."), limpiando la pila de navegación. **Sigue
  pendiente** la segunda mitad de este hallazgo: el almacenamiento de cookies guarda solo
  `nombre=valor`, descartando expiración, dominio, path y flag `secure` — no se tocó en este cambio por
  ser una refactorización más grande (afecta a los 5 `CookiesStorage` por plataforma) y de impacto menor
  ahora que el 401 ya se maneja de forma centralizada. | `AuthService.kt`, `NavGraph.kt`, `LoginScreen.kt`
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
  `onFailure` (el spinner se apaga y no pasa nada); ~~`WeightScreen` cerraba el diálogo como si hubiera
  guardado aunque fallara~~ **arreglado de rebote al implementar VAL-1** (ver más abajo: ahora el diálogo
  de añadir peso solo se cierra dentro de `onSuccess`, y si falla se queda abierto con un error visible);
  `getFoodHistory`/`getFavorites` devuelven `emptyList()`; `GpsService.deleteRoute` **no comprueba el
  status** y devuelve éxito siempre (sus hermanas `publishRoute`/`unpublishRoute` sí lo comprueban:
  inconsistencia clara). El resto de instancias de este patrón sigue sin arreglar.
- **IA-4 — Todos los fallos del análisis son silenciosos también en el servidor**:
  `BodyAnalysisRoute.kt:37-41` responde `mapOf("error" to ...)` **con HTTP 200**, así que el cliente
  intenta deserializarlo como un análisis válido y falla. Además devuelve `e.message` crudo, que incluye
  la respuesta completa de OpenAI (fuga de detalle interno).

### Validación y estado

- ✅ **VAL-1 — Arreglado.** `ProfileScreen` y `WeightScreen` convertían con `toDoubleOrNull() ?: 0.0` y
  enviaban **ceros** si el campo estaba vacío o mal escrito (p. ej. coma decimal en vez de punto). La
  validación correcta ya existía, pero solo en `OnboardingScreen.kt`. Ahora está extraída a
  `validation/ProfileValidation.kt` (`validateWeightKg`, `validateHeightCm`, `validateAge`,
  `validateGoalRate` — mismos rangos: peso 30–300kg, altura 100–250cm, edad 14–100) y las tres pantallas
  la reutilizan: `OnboardingScreen` (se quitó la duplicación), `ProfileScreen` (el botón de guardar datos
  físicos ahora muestra un error concreto para cualquiera de los 4 campos, antes solo existía para el
  ritmo), `WeightScreen` (el diálogo de añadir peso ahora valida el rango, muestra el error con
  `isError`/`supportingText` en el propio campo, y fija `keyboardType = Decimal`; antes un valor inválido
  simplemente no hacía nada al pulsar "Guardar", sin explicación). Verificado con
  `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs` (los tres `BUILD
  SUCCESSFUL`). | `validation/ProfileValidation.kt` (nuevo), `OnboardingScreen.kt`, `ProfileScreen.kt`,
  `WeightScreen.kt`
- ✅ **VAL-2 — Arreglado.** Guardar los datos personales (`ProfileScreen.kt`) recalculaba la edad desde
  la fecha de nacimiento y, si procedía, reguardaba todo el perfil físico a escondidas — pero con
  `goalRate.toDoubleOrNull() ?: 0.0`, saltándose la validación que sí tiene el botón de guardado físico:
  si el usuario había cambiado el objetivo a "perder peso" sin elegir ritmo, ese reguardado derivado
  grababa un déficit de 0.0 (objetivo de mantenimiento) en silencio. Ahora ese reguardado solo se hace si
  peso/altura/ritmo pasan la misma validación que usa el botón físico (reutilizando
  `validation/ProfileValidation.kt`); si no pasan, se omite el reguardado en silencio **en vez de** mandar
  ceros — es un efecto derivado de un botón distinto al que el usuario pulsó, así que no se le muestra un
  error por algo que no pidió hacer explícitamente, simplemente no se corrompe el dato. El
  `catch (_: Exception) {}` que protege el parseo de la fecha se deja igual a propósito (protege un
  cálculo auxiliar no crítico, no el guardado de datos personales en sí, que ya tiene su propio manejo de
  éxito/fallo). Verificado con `:composeApp:compileKotlinJvm`. | `ProfileScreen.kt`
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
- ✅ **HTTP-1 — Arreglado.** Había 5 instanciaciones de `AuthService()` (una por pantalla: `NavGraph`,
  `WeightScreen`, `DashboardScreen`, `ProfileScreen`, `SubscriptionScreen`), cada una con su propio
  `HttpClient`/pool de conexiones, y `close()` no aparecía en ningún sitio — era la causa raíz directa de
  R1 (fácil coger el cliente que no lleva cookies) y garantizaba que cualquier arreglo de
  timeouts/401 se desincronizara entre pantallas. Ahora `NavGraph` crea el único `AuthService()` de toda
  la app y lo pasa como parámetro a las 4 pantallas que antes creaban el suyo; `ProfileScreen` además
  reutiliza el `profileService` ya compartido en vez de crear uno nuevo. Las pantallas que construían su
  propio servicio de dominio (`WeightService`, `SubscriptionService`, etc.) siguen haciéndolo, pero ya a
  partir de `authService.client` compartido, no de un cliente nuevo. | `NavGraph.kt`,
  `DashboardScreen.kt`, `ProfileScreen.kt`, `SubscriptionScreen.kt`, `WeightScreen.kt`, `LoginScreen.kt`
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

## 🔬 Auditoría del cálculo de kcal por objetivo (2026-11-22) — AUD-1 a AUD-24

Auditoría independiente pedida por el usuario, con cuatro agentes (investigación externa contra fuentes
primarias, auditoría de código con tests ejecutados, red team adversarial, y un evaluador que verificó
los hallazgos graves por su cuenta). **Solo diagnóstico: no se tocó código.**

**El inventario completo vive en [11-auditoria-kcal.md](11-auditoria-kcal.md)** — 24 hallazgos con
gravedad, números reproducibles, lo que se declaró FALSO o exagerado, lo que no hay que tocar, el
cumplimiento de los 44 requisitos de referencia, y el plan de arreglo acordado. Resumen para esta cola:

| ID | Gravedad | Qué |
|---|---|---|
| AUD-1 | 🔴 CRÍTICO | No hay suelo calórico absoluto (el objetivo colapsa a 0,90 × BMR en sedentarios), `carryOverKcal` se suma **fuera** del tope del 25 % y **no está condicionado a `activityLevel`**. Ningún test lo cubre: el barrido nunca pasa `carryOverKcal` ni `measuredTdee`. |
| AUD-2 | 🔴 ALTO | `NutritionScreen.kt:389` y `OnboardingScreen.kt:438` prometen *"nunca por debajo de tu metabolismo basal"*; ese suelo fue retirado en §7.3 y el objetivo está al 90 % del BMR justo cuando ese aviso aparece. |
| AUD-3 | 🔴 ALTO | `measuredTdee ?: formulaTdee` sustituye en lugar de componerse: con `adaptive_tdee` presente, `activityLevel` deja de influir, la pulsera se descarta, y no hay ningún camino de invalidación. |
| AUD-4 | 🔴 ALTO | El `previousTdee` del autoajuste no recibe `avgActiveKcal` → en modo pulsera se ancla a `BMR × 1,2`. `AdaptiveCard` muestra "+100 kcal/día" mientras el objetivo baja 139. |
| AUD-5 | 🔴 ALTO | `RateSlider` no recibe el TDEE, así que su tercio superior no cambia el objetivo mientras promete +54 % de pérdida. El ritmo por defecto (−0,75 %) ya está recortado en `sedentary` **y** en `light`. |
| AUD-6 | 🟠 MEDIO | No hay tope de superávit: hasta +32 % frente al +10-20 % que fija §10.3. `rateLimited` nunca puede ser `true` en `gain_muscle`. |
| AUD-7 | ✅ RESUELTO (2026-11-23) | `recomp` producía salida **idéntica** a `lose_fat`. El usuario decidió eliminar la opción en vez de implementarla o renombrarla: el menú queda en perder/ganar/mantener. |
| AUD-8 | 🟠 MEDIO | El IMC se calcula, se pinta y **nunca se usa como guardia**. No hay condición de parada ni transición a `maintain`. |
| AUD-9 | ✅ RESUELTO (2026-11-23) | `ADAPTIVE_MIN_SPAN_DAYS` 10→14, `ADAPTIVE_MIN_WEIGH_INS` 4→8 (lo que exige §5.3), y nueva cobertura mínima de ventana (80%, ver AUD-10). |
| AUD-10 | ✅ RESUELTO (2026-11-23) | Umbral de "día válido" (`ADAPTIVE_MIN_DAY_KCAL = 1000`) antes de contar un día como registrado, y cobertura mínima del 80% de la ventana (`ADAPTIVE_MIN_COVERAGE`) añadida a `AdaptiveInput`/`reconcile`. |
| AUD-11 | 🟠 MEDIO | Sin snapshot ni `calcVersion`: los días pasados se juzgan con el perfil de hoy, y cambiar una constante reescribe el historial entero. |
| AUD-12 | ✅ RESUELTO (2026-11-23) | `weight_history` ahora se escribe **siempre** al guardar el perfil, no solo si el peso cambia — ya no se pierden los días de peso estable de la muestra. |
| AUD-13 | ✅ RESUELTO (2026-11-23) | Nuevo `WEARABLE_MIN_DAYS_WITH_DATA` (mitad de la ventana, 7 de 14 días): un solo día ya no puede ser "la media". `WEARABLE_AVERAGE_DAYS` ahora sí se referencia (el `days` por defecto de `getEnergySummary`). ⚠️ Sin verificar con build real — `HealthConnectProvider.kt` es `androidMain`. |
| AUD-14 | 🟠 MEDIO | Fallo silencioso de la pulsera: sin datos el gasto cae a `BMR × 1,2` (−300 kcal/d) sin aviso. `wearableEnergy` se asigna y nunca se lee. |
| AUD-15 | 🟠 MEDIO | `avgActiveKcal`/`carryOverKcal` sin rango ni `isFinite`: `NaN` → 500, `Infinity` → `Int.MAX_VALUE`, negativos → objetivo negativo. Ver hallazgo 15 de [03-seguridad.md](03-seguridad.md). |
| AUD-16 | 🟠 MEDIO | **Falta `ALTER TABLE ... ADD COLUMN has_wearable`** (condicional: si la BBDD desplegada es anterior a `dc1b216`, `POST /profile/save` y el registro de peso están rotos). ⏳ **Pendiente de la verificación de 30 s del usuario.** |
| AUD-17..24 | 🟡 BAJO | Macros degenerados sin señal · la migración SQL *sube* el ritmo · `resolvedAge` muerto por formato de fecha · `rateLimited` sin banda muerta · textos de "tiempo real" y caja "BMR" que muestra el TDEE · `defaultRateFor(gain)` fuera de la rejilla del slider · formateo que pierde el signo · escrituras no atómicas. |

**Veredicto de fondo**: el modelo energético es correcto (ritmo en %, déficit por velocidad y no por
plazo, `BMR + ActiveCalories` sin doble conteo), y las constantes del código coinciden **todas** con
`10-fundamento-cientifico.md`. El fallo está en **dónde se imponen los invariantes**: AUD-1, AUD-2, AUD-5
y AUD-6 son **un solo error de razonamiento** — §7.3 cambió un *suelo de ingesta* por un *tope de
déficit* sin re-derivar las consecuencias.

**Decisiones del usuario (2026-11-22)**: el suelo será por **disponibilidad energética** (≥30 kcal/kg MM/día
con masa magra estimada), y `carryOverKcal` se **acota dentro del recorte y se condiciona a modo pulsera**
(se descartó convertirlo en presupuesto semanal o eliminarlo).

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
4. ✅ **Resiliencia transversal** — completo en lo planeado: HTTP-1 (cliente único), RED-2 (timeouts +
   motor de red determinista en Android), RED-3 (401 centralizado → redirige al login). Queda abierto,
   a propósito y de menor impacto ahora: la parte de RED-3 sobre metadatos de cookie (expiración/
   dominio/path/secure) y RED-4/RED-5 (pantalla de carga ya existe desde RED-1, capacidad offline es
   cambio de arquitectura mayor, no de este bloque).
5. ✅ **Coste y seguridad** — completo en lo planeado: SEC-1 (gate premium real, 403 si no es premium),
   IA-1 (imagen redimensionada a 1024px antes de mandarla a OpenAI — reduce el payload hacia OpenAI, no
   el payload cliente→servidor, ver nota en el hallazgo), OFF-1 (caché de 24h en memoria para búsquedas
   y códigos de barras de Open Food Facts).
6. ✅ **WEAR-3** — arreglado con un enfoque más simple que el planteado originalmente (refresco
   periódico en pantalla en vez de `WorkManager` en segundo plano — ver el hallazgo para el porqué).
7. ✅ **IA-2/IA-3 y VAL-1/VAL-2** — arreglados del backlog residual (temperatura/`response_format` fijos
   y parseo tolerante en el análisis por IA; validación de peso/altura/edad/ritmo extraída y reutilizada
   en `ProfileScreen`/`WeightScreen`/`OnboardingScreen`). De rebote, se arregló también la instancia de
   `WeightScreen` del hallazgo ERR-1 (el diálogo ya no se cierra como si hubiera guardado si falla).
8. El resto por severidad, todavía abierto: GPS-5/6, PERM-1, el resto de ERR-1, y los 🟡 Bajos.
