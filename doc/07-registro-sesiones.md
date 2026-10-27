# Registro de sesiones

Bitácora corta por sesión de trabajo sobre el endurecimiento del proyecto. Entrada nueva arriba.

---

## 2026-10-26 (continuación 5) — Fase 5: bloque 1 completo + parte del bloque 2

Usuario pidió commitear lo de Fase 4 y empezar la Fase 5 por el orden de ataque propuesto. Se atacó el
bloque 1 completo ("pérdida de datos y funciones muertas") más dos ítems del bloque 2 (fiabilidad del
conteo de kcal):

- **DB-1**: `TursoService.execute()` ahora comprueba el status HTTP y el campo `error` de Turso, y
  lanza `TursoException` en vez de devolver silenciosamente "sin datos". Se añadió el plugin
  `StatusPages` (`plugins/ErrorHandling.kt`, nuevo) — antes no existía ningún manejo global de errores
  en el servidor.
- **NUT-1**: el cliente ya comprueba el status HTTP antes de parsear la respuesta de `/food/barcode/`,
  así que un 404 ya no se interpreta como un producto válido vacío.
- **NUT-2**: `/nutrition/add` ahora devuelve `{"id": "..."}`; el cliente usa ese id real en vez de
  `""`, así que una comida recién añadida ya se puede editar/borrar sin recargar la pantalla.
- **AUTH-1**: `LoginScreen` ahora muestra el mismo paso de verificación (código + reenviar) que ya
  existía en `RegisterScreen` cuando el login responde `EMAIL_NOT_VERIFIED`. Antes era un callejón sin
  salida.
- **RED-1** (el más importante): `AuthResponse` tiene un campo nuevo `networkError`; `me()` lo marca en
  el catch. `hasProfile()` ahora devuelve `Boolean?` (`null` = no se pudo saber) en vez de `Boolean`, y
  en NavGraph/LoginScreen un `null` **nunca** se trata como "no tiene perfil" (eso es lo que mandaba a
  rehacer el onboarding y sobrescribía el perfil real). El arranque de la app (`NavGraph.kt`) ahora
  muestra una pantalla de carga con reintento en vez de una pantalla en blanco indefinida o de adivinar
  el destino — de paso arregla también RED-4.
- **NUT-3**: la pantalla de nutrición ahora muestra un aviso visible si no se pudieron cargar los
  objetivos reales del perfil, en vez de presentar los valores hardcodeados (2000 kcal, etc.) como si
  fueran los del usuario.
- **NUT-4**: la búsqueda de alimentos filtra productos sin datos nutricionales útiles (antes se podían
  añadir como comidas de 0 kcal), y distingue "sin resultados" de "falló la búsqueda".
- Todo verificado con `./gradlew :server:compileKotlin` y `:composeApp:compileKotlinJvm` (`BUILD
  SUCCESSFUL`) tras cada cambio. **No se ha podido verificar el target Android** por falta de Android
  SDK en este entorno — ninguno de los cambios de esta tanda toca código Android puro, así que el
  riesgo es bajo, pero queda pendiente de build real.
- `08-backlog-mejoras.md` actualizado: cada ítem resuelto está marcado con ✅ y referencia al detalle.
- **Pendiente del bloque 2**: WEAR-1/2/3 (recuento del wearable con `aggregate()`).
- **Bloques 3-5 sin empezar**: GPS (foreground service — cambios más invasivos, requieren permisos
  nuevos y un `Service` de Android no verificable en este entorno), cliente HTTP único, gate premium
  real, redimensionado de imagen para IA.
- Usuario confirmó commit de este bloque (commit `893e71c`). Aclaró que trabaja en IntelliJ ejecutando
  Gradle por terminal — mismo flujo que se usa aquí para verificar los cambios.

### 2026-10-26 (continuación 6) — Fase 5: cierra el bloque 2 (wearable)

- **WEAR-1/WEAR-2 arreglados**: `HealthConnectProvider` migrado de `readRecords` (sumaba registros
  crudos, sin deduplicar entre fuentes y sin paginar) a `aggregate()` con `StepsRecord.COUNT_TOTAL` y
  `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`, que resuelve ambos problemas a la vez (Health
  Connect ya deduplica y no pagina en una agregación). **Sin verificar con build real** — este entorno no
  tiene Android SDK instalado, así que no se pudo ejecutar `:composeApp:compileDebugKotlinAndroid`.
  Recomendado compilar/probar en un dispositivo o emulador Android.
- Queda **WEAR-3** (sincronización en segundo plano en vez de lectura puntual por pantalla) como mejora
  de arquitectura más grande, pendiente de decidir si se hace ahora.
- Con esto, los bloques 1 y 2 del orden de ataque de `08-backlog-mejoras.md` quedan completos.
- Usuario confirmó commit de este bloque (commit `3275443`).

### 2026-10-27 — Sistema de continuidad entre sesiones + bloque 3 (GPS) casi completo

- **Usuario pidió un sistema de continuidad**: al llenarse el contexto de una sesión, cambiará a otra
  sesión de Claude, y necesita que esa sesión nueva pueda seguir exactamente donde se dejó sin perder
  el ritmo. Se creó:
  - `CLAUDE.md` en la raíz del repo (Claude Code lo lee automáticamente al empezar) — instrucciones
    cortas que apuntan a `doc/CONTINUAR.md` como primera lectura obligatoria.
  - `doc/CONTINUAR.md` — documento de estado (no histórico): fase/bloque actual, lo último hecho, lo
    siguiente, pendientes de verificación, decisiones ya tomadas por el usuario. Se actualiza cada vez
    que se termina algo, no solo al cerrar sesión.
- **Bloque 3 (GPS) — GPS-2, GPS-3 y GPS-4 arreglados**:
  - GPS-4: `TursoService.executeBatch()` nuevo (varios statements en una sola llamada a
    `/v2/pipeline`); `GpsService.saveRoute` guarda la ruta + puntos en lotes de 200 en vez de N+1
    peticiones. Verificado con `:server:compileKotlin`.
  - GPS-2: en `GpsScreen.kt`, un hueco de señal >100m ya no deja el punto de referencia congelado
    (se actualiza sin sumar esa distancia al total), así que no descarta en cascada el resto de la
    grabación. Verificado con `:composeApp:compileKotlinJvm`.
  - GPS-3: `RouteRecoveryStore` nuevo (expect/actual — real en Android con `SharedPreferences`, no-op
    en jvm/ios/js/wasmJs). `GpsScreen.kt` persiste la ruta en curso cada 10 puntos y al parar; al abrir
    la pantalla, si hay una ruta sin guardar se ofrece recuperarla o descartarla. Verificado con
    `:composeApp:compileKotlinJvm`, `compileKotlinJs` y `compileKotlinWasmJs` (los tres
    `BUILD SUCCESSFUL`). El `actual` de Android no se pudo compilar aquí (sin SDK).
  - **Descubrimiento colateral**: `:composeApp:compileKotlinIosSimulatorArm64` sí funciona en este
    entorno sin Xcode. Al probarlo (para verificar el `actual` de iOS de `RouteRecoveryStore`) salió
    exactamente el error ya documentado del wiring de `App()` — confirma el diagnóstico de
    `01-arquitectura.md §5` y abre la puerta a poder verificar más trabajo de iOS en el futuro de lo
    que se pensaba.
- **Queda GPS-1** (foreground service) como único pendiente del bloque 3 — es el cambio más invasivo
  (Service de Android, permisos nuevos), no verificable aquí, y se decidió dejarlo para acordar el
  enfoque exacto con el usuario antes de escribirlo.
- **Nada de esta tanda se ha commiteado todavía.**
- Usuario, al leer el resumen, preguntó cómo funciona exactamente el sistema de continuidad y qué
  contiene `CONTINUAR.md` — se le explicó (lectura automática vía `CLAUDE.md`, contenido = estado
  rápido con enlaces al historial completo, no duplica `07-registro-sesiones.md`) y pidió seguir con
  GPS-1.
- **GPS-1 implementado sin respuesta a las preguntas de diseño planteadas** (notificación con/sin botón
  de parar, stats en vivo o no): se tomó la decisión más simple y estándar para no bloquear el trabajo:
  - `GpsTrackingService.kt` (nuevo): foreground service tipo `location`, notificación fija "Grabando
    ruta GPS", sin botón de acción, sin stats en vivo. Su único trabajo es mantener el proceso con
    prioridad elevada; la recolección de ubicación sigue en `LocationService`/`GpsScreen` como antes.
  - `BackgroundTrackingController.kt` (nuevo, común + 5 `actual`): arranca/para el servicio desde
    `GpsScreen.kt` (código común). Patrón igual al de `rememberImagePicker` (usa `LocalContext.current`
    dentro del `actual` de Android).
  - Manifest: permisos `FOREGROUND_SERVICE_LOCATION` y `POST_NOTIFICATIONS` (esta última sin flujo de
    petición en runtime todavía), declaración de `<service>`. Dependencia nueva
    `androidx.core:core-ktx`.
  - `GpsScreen.kt`: arranca el servicio al empezar a grabar, lo para al parar, y con un
    `DisposableEffect` como red de seguridad si la pantalla se destruye mientras grababa.
  - Verificado con `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs` (los tres
    `BUILD SUCCESSFUL`). Se intentó también `:composeApp:compileKotlinIosSimulatorArm64` — compila el
    `actual` de iOS sin problema, y falla solo por el bug ya conocido de `MainViewController.kt` (sin
    relación con este cambio). **Descubrimiento**: ese target de iOS compila en este entorno sin
    Xcode, lo que da más margen de verificación del esperado para trabajo futuro de iOS.
  - **El `Service`/`actual` de Android no se ha podido compilar** (sin SDK) — es el cambio que más
    necesita probarse en un dispositivo/emulador real de todo lo hecho hasta ahora.
- Con esto, **el bloque 3 (GPS) queda completo** (GPS-1, GPS-2, GPS-3, GPS-4).
- `08-backlog-mejoras.md`, `06-funcionalidades.md`, `00-roadmap.md` y `CONTINUAR.md` actualizados con
  el cierre del bloque 3 y las decisiones de diseño tomadas sin confirmar.
- **Nada de esta tanda se ha commiteado todavía.**
- **Siguiente sesión**: confirmar commit de todo el bloque 3; decidir entre bloque 4 (cliente HTTP
  único, timeouts, 401), bloque 5 (gate premium, redimensionar imagen IA, caché OFF) o WEAR-3 — ninguno
  requiere una decisión de producto tan grande como GPS-1, se puede avanzar sin bloquear.

---

## 2026-10-26 (continuación 4) — Fase 4 completa: investigación exhaustiva

Sesión de investigación profunda a petición del usuario ("investiga bien todo", con el objetivo explícito
de encontrar fallos y mejoras). Se repartió en cuatro frentes en paralelo: GPS + wearable + fechas
(directo), análisis IA + datos de alimentos, resiliencia/offline/cold start, y auditoría de pantallas.

**Resultado: ~60 hallazgos, inventariados y priorizados en [08-backlog-mejoras.md](08-backlog-mejoras.md)**
(documento nuevo, que pasa a ser la cola de trabajo de la Fase 5).

- **Regresión propia detectada y arreglada.** El fix de seguridad de Fase 2 (exigir sesión en
  `/ai/body-analysis`) **rompió el análisis corporal**: `WeightScreen` lo llamaba con uno de sus tres
  `HttpClient`, justo el que no tenía el plugin `HttpCookies`. Antes funcionaba solo porque el endpoint
  era público, así que el fix destapó un bug latente, y el `catch` con solo `printStackTrace()` lo hacía
  fallar en silencio. Lo señalaron **las tres investigaciones de forma independiente**. Arreglado: usa el
  cliente con cookies, se eliminó el cliente duplicado, y los fallos ahora muestran un mensaje visible.
  Lección añadida como regla 10 en [02-convenciones.md](02-convenciones.md).
- También arreglado en el acto: crash al abrir la pantalla de Peso sin conexión (`getHistory()` sin
  `try/catch` dentro de un `LaunchedEffect` sin `catch`), y el estado premium que no se cargaba nunca si
  el usuario no tenía historial de peso. Verificado con `:composeApp:compileKotlinJvm` → `BUILD
  SUCCESSFUL`.
- **Hallazgos críticos abiertos más relevantes**: el GPS se para al bloquear el móvil (no hay foreground
  service); un hueco de señal >100 m mata la grabación en cascada; la ruta en curso solo vive en memoria;
  un fallo de red puede llevar a rehacer el onboarding y **sobrescribir el perfil real** (único camino de
  pérdida de datos encontrado); un código de barras no encontrado se puede guardar como comida de 0 kcal;
  las comidas recién añadidas no se pueden editar ni borrar; una cuenta sin verificar queda bloqueada sin
  salida; la pantalla de nutrición muestra **2000 kcal hardcodeadas** como si fueran los objetivos reales
  cuando falla la carga del perfil; y los errores de Turso se reportan como éxito.
- **Respuesta a la duda del usuario sobre la recolecta del wearable**: sí se puede hacer mejor, y el
  arreglo principal cabe en un archivo. El código suma registros crudos (`readRecords`), lo que puede
  **contar de más** (varias apps escribiendo los mismos pasos, sin la deduplicación que sí hace
  `aggregate()`) y **contar de menos** a la vez (ignora el `pageToken`, truncando en la primera página de
  1000 registros).
- Se actualizaron `03-seguridad.md` (4 hallazgos nuevos: gate premium inexistente en servidor, errores de
  BBDD silenciosos, `usesCleartextTraffic`, conflicto de `allowBackup` con el cifrado nuevo),
  `06-funcionalidades.md` (veredictos reales de GPS, wearable, IA y training) y `00-roadmap.md` (Fase 4
  cerrada).
- **Nada de esta tanda está commiteado todavía.**
- **Siguiente paso**: empezar Fase 5 por el orden de ataque propuesto al final del backlog (pérdida de
  datos y funciones muertas primero, luego fiabilidad del conteo de kcal, luego GPS).

---

## 2026-10-26 — Fase 0 y arranque de Fase 1

- Se define el roadmap de 6 fases en [00-roadmap.md](00-roadmap.md) (documentación → seguridad →
  limpieza → validación de fiabilidad → mejora de funcionalidades → nuevas funcionalidades).
- Se crea la carpeta `doc/` con el índice y la checklist de producción
  ([04-produccion-playstore.md](04-produccion-playstore.md)).
- Exploración inicial de la estructura del repo: módulos `composeApp`, `shared`, `server` (Ktor),
  `iosApp`, y dos carpetas sospechosas de ser legacy: `backend/` (solo `.env` + `alembic/versions` vacío +
  `venv/`, sin `.py` propio visible) y `frontend/` (solo `node_modules`, sin proyecto aparente).
- Lanzadas 3 exploraciones en paralelo para construir la primera versión de `01-arquitectura.md` y
  `05-deuda-tecnica.md`: cliente (`composeApp`), servidor (`server`), y módulos legacy + estado real de
  iOS.
- Las 3 exploraciones terminaron y se consolidaron en documentación definitiva:
  `01-arquitectura.md`, `02-convenciones.md`, `03-seguridad.md`, `05-deuda-tecnica.md`,
  `06-funcionalidades.md` (primera versión).
- **Hallazgos clave de esta sesión:**
  - Causa raíz exacta de que iOS no compile: `LocationProvider`/`HealthDataProvider` no son
    `expect/actual`, no tienen implementación iOS, y `App()` los exige como parámetros obligatorios sin
    valor por defecto; `MainViewController.kt` llama a `App()` sin argumentos. Lo mismo afecta a Web
    (JS/Wasm).
  - `backend/` (Python/Alembic) y `frontend/` (solo `node_modules`) confirmados como código muerto.
  - Seguridad: 2 hallazgos críticos — `/ai/body-analysis` sin autenticación (riesgo de agotar créditos
    de OpenAI) y IDOR en `deleteMeal`/`updateMeal` de nutrición (no filtran por `user_id`, a diferencia
    de GPS que sí lo hace bien). Detalle completo con severidades en `03-seguridad.md`.
  - Pista sobre por qué los objetivos de peso "no van muy allá": en modo `activityLevel == "wearable"`,
    el servidor usa el BMR puro como TDEE sin sumar el gasto real de la pulsera.
  - Health Connect usa consulta puntual por pantalla (`LaunchedEffect`), no sincronización en segundo
    plano — mejora concreta propuesta para Fase 5.
  - Stripe no es una pasarela real (API vieja sin 3DS, ignora tarjetas reales salvo tokens de test) —
    confirmado que no cobra, documentado el camino para activarlo con seguridad en el futuro.
  - `TrainingScreen`/`TrainingService` son un placeholder sin terminar, sin endpoint real en el servidor.
- **Decisiones del usuario tras revisar lo anterior**: aplicar ya los 2 fixes críticos; borrar
  `backend/`/`frontend/` si tras verificar son código muerto; dejar `TrainingScreen` como está por ahora.
- **Verificación independiente** (no solo confiar en el informe del fork) de `backend/`/`frontend/`:
  confirmado que no están rastreados por git (`git ls-files` → 0 archivos) y que no hay ninguna
  referencia a ellos en `settings.gradle.kts`, ningún `build.gradle.kts` ni el `Dockerfile`.
- **Aplicado**:
  - `BodyAnalysisRoute.kt`: ahora exige `call.sessions.get<UserSession>()` antes de llamar a
    `OpenAIService`, igual que el resto de rutas protegidas.
  - `NutritionService.deleteMeal`/`updateMeal` y las rutas correspondientes en `NutritionRoutes.kt`:
    ahora reciben y filtran por `userId` (`WHERE id = ? AND user_id = ?`), cerrando el IDOR.
  - Verificado con `./gradlew :server:compileKotlin` → `BUILD SUCCESSFUL`.
  - `backend/` eliminado por completo. `frontend/node_modules/` eliminado; la carpeta `frontend/` vacía
    quedó bloqueada por Windows ("en uso por otro proceso") — pendiente de borrar manualmente.
- **Pendiente importante**: todos los fixes de seguridad están en el working tree local, **no
  desplegados en Render** — hace falta commit + push/deploy para que surtan efecto real.

### 2026-10-26 (continuación) — resto de Fase 2

- Preguntó "¿y la fase 2?" → se continuó cerrando hallazgos de `03-seguridad.md` en la misma sesión:
  - **#7 esquema desincronizado**: añadidas `is_public` (gps_routes) y `has_wearable` (user_profiles)
    a los `CREATE TABLE IF NOT EXISTS`. No afecta a la BBDD real de Turso (ya las tenía a mano), solo a
    entornos nuevos.
  - **#8 JSON sin escapar en EmailService**: reescrito `sendEmail` con `buildJsonObject` en vez de
    interpolación de strings.
  - **#4 logging con `println` en el servidor**: migrados los 27 `println`/`printStackTrace` de 8
    archivos del servidor a SLF4J/Logback, quitando del log el objeto de sesión completo, el email, el
    token de verificación (antes en claro) y la respuesta cruda de Stripe. `logback.xml` bajado de
    `root level="trace"` a `"info"`.
  - Verificado todo con `./gradlew :server:compileKotlin` → `BUILD SUCCESSFUL` tras cada tanda de
    cambios.
- **Quedan abiertos de Fase 2** (documentados en `03-seguridad.md`): #3 cifrado de cookie de sesión
  (trabajo por plataforma, no se ha hecho), #9 logging del cliente (requiere elegir librería
  multiplataforma, se deja para Fase 3), #5 CORS (necesita que el usuario confirme si hay dominio de
  producción para el cliente web), #6 Stripe/PCI (diferido a propósito, no es funcional).
- Usuario confirmó: el cliente web de GymSmart no está publicado en ningún dominio real (solo se
  prueba en local) → **#5 CORS se deja tal cual**, documentado para cuando exista un dominio real.
- Usuario pidió cifrar la cookie de sesión al menos en Android → **#3 arreglado**:
  `AndroidCookiesStorage` migrado a `EncryptedSharedPreferences` (clave en Android Keystore, AES256-GCM),
  con fallback a `SharedPreferences` normal si el Keystore fallara. Se limpia el residuo en claro del
  almacenamiento antiguo al migrar. Añadida dependencia `androidx.security:security-crypto:1.1.0-alpha06`.
  **Importante**: esta sesión no tiene Android SDK instalado, así que este cambio concreto no se pudo
  compilar/verificar (`compileKotlinJvm` sí pasó, pero no toca este archivo) — recomendado probarlo en
  un dispositivo/emulador Android antes de confiar en él al 100%.
- Usuario pidió hacer commit ahora (sin push) de todo el trabajo de seguridad de esta sesión.
- **Estado de Fase 2 al cierre de esta sesión**: 7 de 10 hallazgos resueltos. Quedan #9 (logging del
  cliente, movido a Fase 3 porque requiere elegir librería de logging multiplataforma) y #6
  (Stripe/PCI, diferido a propósito mientras no sea funcional).
### 2026-10-26 (continuación 2) — Fase 3: limpieza de código muerto

- Usuario dijo "sigue" → se continuó con la limpieza listada en `05-deuda-tecnica.md`:
  - Verificado (grep, sin referencias externas) y eliminado: `AndroidGpsTracker.kt`,
    `PermissionHelper.kt`.
  - Eliminado `WeightService.addWeight` (método muerto y roto, nadie lo llamaba).
  - Movidos `Product`/`Nutriments`/`FoodSearchResponse` de `NutritionScreen.kt` a
    `model/NutritionModels.kt`; actualizado el import en `NutritionService.kt`.
  - Unificada la duplicación de `GpsService.getRoutesByUser`/`getCommunityRoutes` en un método privado
    `queryRoutes(whereClause, params)`.
  - Limpiados los comentarios con IPs y nombres de personas (`DANI`, `JONY`) en `AppConfig.kt`.
  - Verificado con `./gradlew :composeApp:compileKotlinJvm` y `:server:compileKotlin` tras cada tanda →
    `BUILD SUCCESSFUL`. No se tocó Android directamente en esta tanda (ya se había avisado que no hay
    SDK Android en este entorno).
  - **No se tocó** `TrainingScreen`/`TrainingService` (decisión del usuario de dejarlo como está) ni la
    estrategia de múltiples `HttpClient`/DI (requiere decidir enfoque antes de tocarlo).
- **Nada de esta tanda se ha commiteado todavía** — queda en el working tree, pendiente de que el
  usuario confirme (como hizo con la tanda de seguridad).
- Usuario confirmó commit de la limpieza de Fase 3 (commit `4a81cba`).

### 2026-10-26 (continuación 3) — Fase 4: validación de nutrición

- Usuario dijo "sigue" de nuevo. Los dos puntos restantes de Fase 3 (logging del cliente, estrategia de
  DI) requieren decisiones de arquitectura que no se tomaron por iniciativa propia; se dejaron
  documentados como pendientes y se pasó a Fase 4, empezando por nutrición (el dolor que más mencionó
  el usuario).
- **Investigación a fondo del modo "wearable"**: se leyó `ProfileService.computeTargets` línea a línea,
  `NutritionScreen.kt` (cómo se usa `activeCalories`) y `HealthConnectProvider.kt` (semántica exacta de
  `ActiveCaloriesBurnedRecord`, que por definición de Health Connect excluye el metabolismo basal).
  **Conclusión: la hipótesis de la Fase 1 era incorrecta.** El diseño BMR (servidor) + kcal activas
  reales (cliente, sumadas aparte) es un patrón válido y deliberado (igual que Fitbit/Garmin/MFP), no un
  bug. El problema real de precisión es que la lectura de kcal activas es puntual por pantalla, no
  continua — ya estaba identificado como mejora de Fase 5, ahora confirmado como la explicación más
  probable de la imprecisión percibida. **Se corrigió `06-funcionalidades.md`** para reflejar esta
  conclusión más sólida en vez de la hipótesis preliminar.
- **Bug real encontrado y arreglado**: al revisar el cálculo de kcal por comida, 4 sitios en
  `NutritionScreen.kt`/`NutritionService.kt` usaban el campo ambiguo `energy-kcal` de Open Food Facts
  como si fuera por 100g cuando faltaba el campo correcto `energy-kcal_100g` (uno de los 4 ni siquiera
  tenía ese fallback, mostraba 0 directamente). Se añadió `Nutriments?.estimatedKcalPer100()` en
  `model/NutritionModels.kt` que estima kcal/100g a partir de los macros (regla 4/4/9) cuando falta el
  campo correcto, en vez de asumir que un campo ambiguo es por 100g. Aplicado en los 4 sitios.
  Verificado con `:composeApp:compileKotlinJvm` → `BUILD SUCCESSFUL`.
- **No se tocó** la fórmula de TDEE del servidor (confirmada correcta) ni el patrón de lectura de Health
  Connect (la mejora de fondo pertenece a Fase 5, es un cambio de arquitectura — background sync — no
  una corrección puntual).
- **Nada de esta tanda se ha commiteado todavía.**
- **Siguiente sesión**: confirmar commit de los cambios de nutrición; decidir si Fase 4 continúa con
  GPS/wearable (medir precisión real, considerar la mejora de sincronización en segundo plano) o se
  pasa directamente a Fase 5; decidir cuándo desplegar todo lo acumulado a Render; probar en un
  dispositivo real el cambio de cookie cifrada de Android.
