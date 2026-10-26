# Backlog de bugs y mejoras — resultado de la Fase 4

Inventario priorizado de todo lo encontrado en la investigación exhaustiva de Fase 4 (2026-10-26):
GPS, wearable, análisis por IA, datos de alimentos, resiliencia de red y auditoría de pantallas.

Este documento es **la cola de trabajo de la Fase 5**. Cada entrada tiene severidad, ubicación concreta
y estado. Severidad medida por impacto real sobre el usuario, no por elegancia del código.

Leyenda: 🔴 crítico · 🟠 medio · 🟡 bajo · ✅ arreglado

---

## ✅ Arreglado durante la Fase 4

| # | Qué | Dónde |
|---|---|---|
| R1 | **Regresión introducida por el fix de seguridad de Fase 2**: el análisis corporal por IA usaba un `HttpClient` sin cookies, así que al exigir sesión en `/ai/body-analysis` empezó a devolver 401 y la función quedó muerta, fallando en silencio (`printStackTrace`). Ahora usa el cliente con cookies, se eliminó el cliente duplicado y los fallos muestran un mensaje visible al usuario. | `WeightScreen.kt` |
| R2 | **Crash al abrir la pantalla de Peso sin conexión**: `weightService.getHistory()` era la única llamada de servicio sin `try/catch`, dentro de un `LaunchedEffect` con `try/finally` sin `catch` → excepción no capturada. Ahora va envuelta en `runCatching`. | `WeightScreen.kt` |
| R3 | **El estado premium no se cargaba nunca sin historial de peso**: `subService.getStatus()` estaba solo en la rama `else`, así que un usuario premium sin pesos registrados veía "Solo Premium" y se le mandaba a pagar de nuevo. Ahora se carga siempre. | `WeightScreen.kt` |

Los tres se detectaron porque tres investigaciones independientes señalaron el mismo síntoma. **Lección
registrada en [02-convenciones.md](02-convenciones.md)**: al añadir una comprobación de sesión a un
endpoint, hay que verificar con qué cliente HTTP lo llama el cliente.

---

## 🔴 Críticos abiertos

### GPS-1 — El seguimiento se detiene al bloquear el móvil
No existe ningún `Service` de Android en el proyecto (verificado: 0 coincidencias de `Service()` /
`startForeground`). El tracking vive en un `callbackFlow` dentro del Composable
(`GpsScreen.kt:45-64` + `LocationService.kt`). Con `targetSdk = 36`, cuando la app pasa a segundo plano
Android corta o estrangula las actualizaciones de ubicación.

El manifest declara `FOREGROUND_SERVICE` pero **no hay servicio que lo use**, ni
`FOREGROUND_SERVICE_LOCATION` (obligatorio desde Android 14), ni `POST_NOTIFICATIONS` (Android 13+).

*Impacto*: hay que correr con la pantalla encendida y la app en primer plano, lo que además dispara el
consumo de batería (precisión alta + 1 punto/segundo). Para un tracker de rutas es un fallo de diseño,
no un detalle.

*Arreglo*: foreground service con `android:foregroundServiceType="location"`, los dos permisos que
faltan, y mover la recolección del Flow al servicio.

### GPS-2 — Un hueco de señal mata la grabación para el resto de la sesión
`GpsScreen.kt:57`: si un punto está a más de 100 m del último aceptado, se descarta **sin actualizar el
último punto aceptado**. Ese punto de referencia queda congelado: todos los puntos siguientes también
estarán a >100 m y se descartarán en cascada. La UI sigue mostrando "grabando" sin registrar nada.

*Se combina con GPS-1*: el segundo plano provoca huecos, y los huecos matan la grabación.

*Arreglo*: al detectar un salto >100 m, avanzar el punto de referencia sin sumar esa distancia (tratarlo
como reinicio de tramo), en vez de descartar el punto.

### GPS-3 — La ruta en curso solo vive en memoria
`activeRoute` es un `remember` sin persistencia (`GpsScreen.kt:36`). Si Android mata el proceso —muy
probable en una ruta larga con la pantalla apagada— se pierde la ruta completa. No hay guardado parcial
ni recuperación. Nota: `rememberSaveable` no sirve aquí (miles de puntos exceden el límite del `Bundle`);
hace falta persistencia local real.

### RED-1 — Un fallo de red puede sobrescribir el perfil del usuario (pérdida de datos)
Cadena completa del problema:
1. `AuthService.me()` captura cualquier excepción y devuelve `success = false` → **"sin red" es
   indistinguible de "sin sesión"**.
2. `ProfileService.hasProfile()` devuelve `false` ante cualquier excepción (incluida una página de error
   502 de Render, que además parsea a mano).
3. `NavGraph.kt:60-69` interpreta ese `false` como "este usuario no tiene perfil" y lo manda a
   **Onboarding**.
4. Rehacer el onboarding llama a `/profile/save`, que en el servidor es `INSERT OR REPLACE INTO
   user_profiles` → **sobrescribe peso, altura, edad y objetivos reales**, y además escribe en
   `weight_history`.

*Impacto*: un parpadeo de red o un cold start de Render en el momento justo puede destruir el perfil.
Es el único camino de pérdida de datos encontrado en toda la auditoría.

*Arreglo*: que `me()` y `hasProfile()` distingan fallo de red de respuesta negativa (devolver `Result`),
y que ante fallo de red la app muestre carga/reintento **en vez de decidir destino**. Nunca derivar "no
tiene perfil" de una excepción. El mismo patrón está en `LoginScreen.kt:129`.

### NUT-1 — Un código de barras no encontrado se guarda como comida de 0 kcal
`NutritionScreen.kt:156-166` no comprueba el código de estado. Cuando el producto no existe, el servidor
responde 404 con `{"error":"..."}` (`FoodRoute.kt:32`), pero como `Product` tiene los tres campos
nulables con valor por defecto y se usa `ignoreUnknownKeys = true`, **ese JSON de error se deserializa
sin excepción** en un `Product` vacío. No se entra en el `catch`, así que el aviso "Producto no
encontrado" nunca aparece: se abre el selector de porción con "Sin nombre" y 0 kcal, y el usuario puede
guardarlo en su diario.

### NUT-2 — Una comida recién añadida no se puede editar ni borrar
`NutritionScreen.kt:185` crea la entrada con `id = ""` porque `saveMealRemote` solo devuelve `Boolean` y
el UUID que genera el servidor nunca vuelve al cliente. Al intentar editarla o borrarla se llama a
`/nutrition/update/` o `/nutrition/delete/` sin id → error genérico. Solo se arregla al recargar la
pantalla. Agravante: con varias comidas nuevas (todas con `id = ""`), las búsquedas por id casan con la
entrada equivocada.

*Impacto*: rompe el flujo más común de la funcionalidad principal ("me he pasado con los gramos, lo
borro").

*Arreglo*: que `/nutrition/add` devuelva el id creado y propagarlo a la entrada local.

### NUT-3 — La pantalla de nutrición muestra objetivos inventados como si fueran reales
`NutritionScreen.kt:108-111` inicializa los objetivos hardcodeados (**2000 kcal**, 150 g proteína, 200 g
carbos, 65 g grasa) y solo los sobrescribe en el `onSuccess` del perfil, **sin `onFailure`**. Si la carga
del perfil falla (cold start, 401, sin red), el usuario cuenta todo el día contra objetivos ficticios sin
ninguna señal de que no son los suyos.

*Impacto*: en una app cuyo propósito es contar calorías con fiabilidad, es el peor fallo de confianza del
inventario, y encaja con la sensación de que "los números no van muy allá".

### AUTH-1 — Cuenta registrada sin verificar = callejón sin salida permanente
El paso de verificación por código vive solo en el estado local de `RegisterScreen`. Si el usuario cierra
la app ahí: al volver, el login responde `EMAIL_NOT_VERIFIED` y `LoginScreen.kt:137-138` **solo muestra
un texto**, sin campo para el código ni botón de reenviar. Registrarse otra vez da "email ya
registrado", y el reset de contraseña no toca `email_verified`. El servidor **sí** expone
`/auth/resend-verification` y `AuthService` tiene el método, pero ninguna pantalla accesible lo ofrece.

*Impacto*: cuenta inutilizable desde la app, sin recuperación posible por el usuario.

### SEC-1 — El gate premium no existe en el servidor
`ProfileService.isPremium()` **existe pero no se llama desde ningún sitio** (verificado con grep: solo
aparece su propia definición). El único control es de cliente (`WeightScreen.kt:173`). Tras el fix de
sesión, cualquier usuario registrado y logueado —gratis— puede llamar a `/ai/body-analysis` sin límite:
no hay cuota diaria, ni rate limit, ni caché, ni registro de consumo.

*Impacto*: tus créditos de OpenAI. Ver también [03-seguridad.md](03-seguridad.md).

### DB-1 — Todos los errores de base de datos se convierten en silencio en "éxito" o "sin datos"
`TursoService.execute()` no comprueba el código de estado HTTP, y Ktor sin `expectSuccess` no lanza
excepción con 4xx/5xx. `extractRows()` busca la clave `results` y, al no encontrarla en un cuerpo de
error, devuelve `emptyList()`. Para las escrituras el resultado se ignora por completo.

*Consecuencias*: un token de Turso caducado, una cuota superada o una violación de constraint hacen que
las lecturas devuelvan "no hay datos" y **que las escrituras fallidas se reporten como correctas**. Por
ejemplo, `/nutrition/add` responde `201 Created "Guardado en Turso"` aunque el `INSERT` haya fallado: el
usuario cree que registró la comida y no está.

*Arreglo*: comprobar el status y la presencia de `error` en la respuesta de Turso, y propagar el fallo.

---

## 🟠 Medios

### Rendimiento y coste

- **GPS-4 — Guardar una ruta hace N+1 peticiones HTTP secuenciales a Turso.**
  `GpsService.kt:58-74` ejecuta un `turso.execute` por punto, y cada uno es un POST independiente. Una
  carrera de 30 min son ~500-900 puntos; una ruta en bici puede pasar de 3000 → **entre 45 s y varios
  minutos** para guardar, con riesgo alto de timeout. La API `/v2/pipeline` que ya se usa acepta varias
  sentencias por llamada; el código solo mete una. Además no hay transacción: si falla a mitad, queda una
  ruta con puntos incompletos.
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

- **WEAR-1 — El recuento del wearable puede contar de más.** Se leen registros crudos con `readRecords` y
  se suman a mano. Health Connect agrega datos de varias apps a la vez (app de la pulsera, Google Fit,
  sensor del móvil): sumando registros crudos, **lo que escriben dos fuentes se cuenta dos veces**.
  `aggregate()` con `COUNT_TOTAL` aplica la deduplicación que `readRecords` no hace.
- **WEAR-2 — …y también puede contar de menos.** `readRecords` devuelve resultados paginados y el código
  **ignora el `pageToken`**: se queda con la primera página (1000 registros por defecto). Hay apps que
  escriben un registro por minuto (1440/día), y con varias fuentes se multiplica → truncado silencioso.
  Verificado: no se usa `aggregate`, ni `pageToken`, ni `dataOriginFilter` en ningún sitio.
  **`aggregate()` resuelve WEAR-1 y WEAR-2 a la vez**, y es un cambio en un solo archivo.
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
- **NUT-4 — No se filtran los productos sin datos nutricionales.** `NutritionScreen.kt:608-611` pinta
  todos los que devuelve OFF, incluidos los de `nutriments = null`, como "0 kcal/100g", y **se pueden
  añadir**: comidas de 0 kcal que no suman nada al diario.
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

## Orden de ataque sugerido para la Fase 5

1. **Pérdida de datos y funciones muertas primero**: RED-1 (sobrescritura del perfil), NUT-2 (editar/
   borrar comidas), AUTH-1 (cuenta bloqueada), NUT-1 (código de barras), DB-1 (errores de BBDD
   silenciosos).
2. **Fiabilidad del conteo de kcal**, que es el núcleo del producto: NUT-3 (objetivos inventados), NUT-4
   (productos sin datos), WEAR-1/2/3 (recuento del wearable).
3. **GPS como funcionalidad real**: GPS-1 (foreground service), GPS-2 (huecos), GPS-3 (persistencia),
   GPS-4 (guardado por lotes).
4. **Resiliencia transversal**: HTTP-1 (cliente único) como base, y encima RED-2 (timeouts), RED-3 (401),
   RED-4 (pantalla de arranque).
5. **Coste y seguridad**: SEC-1 (gate premium real), IA-1 (redimensionar imagen), OFF-1 (caché).
6. El resto por severidad.
