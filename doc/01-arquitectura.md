# Arquitectura actual — GymSmart

Este documento describe la app **tal y como está hoy** (no cómo debería estar — eso va en
[02-convenciones.md](02-convenciones.md) y en las fases de limpieza). Generado a partir de una
exploración exhaustiva del código en 2026-10-26 (ver [07-registro-sesiones.md](07-registro-sesiones.md)).

## 1. Mapa de módulos

| Módulo | Qué es | Estado |
|---|---|---|
| `composeApp/` | Cliente Compose Multiplatform: toda la UI y la lógica de presentación. Targets: Android, Desktop (JVM), iOS, Web (JS/Wasm). | Android y Desktop **completos y funcionales**. iOS/Web **no arrancan** (ver §5). |
| `shared/` | Módulo generado por el wizard de KMP. Solo contiene `Platform.kt` (expect/actual trivial) y un `Greeting.kt` de ejemplo. | Vestigial — no tiene lógica de negocio. Lo usa `server` como dependencia pero apenas lo necesita. |
| `server/` | Servidor Ktor (Kotlin/JVM) desplegado en Render. Contiene toda la lógica de negocio real del backend y el acceso a Turso. | Funcional, en producción personal. |
| `iosApp/` | Scaffold por defecto del wizard de Compose Multiplatform (SwiftUI mínimo que monta el framework `ComposeApp`). | Sin personalizar; no es el problema de que iOS no funcione (ver §5). |
| `backend/` | Solo `backend/.env` + `alembic/versions` vacío + `venv/`. Sin un `.py` de aplicación. | **Código muerto.** Prototipo Python/Alembic nunca materializado, reemplazado por `server/`. Ver [05-deuda-tecnica.md](05-deuda-tecnica.md). |
| `frontend/` | Solo contiene `node_modules/`, sin `package.json` ni código propio. | **Código muerto / residuo.** Ver [05-deuda-tecnica.md](05-deuda-tecnica.md). |

## 2. Arquitectura del cliente (`composeApp`)

**No hay ViewModels ni patrón MVVM/MVI.** Todo el estado de cada pantalla vive directamente en los
`@Composable` con `remember`/`mutableStateOf` (p. ej.
[DashboardScreen.kt:41-53](../composeApp/src/commonMain/kotlin/com/gymsmart/gymsmart/screens/DashboardScreen.kt),
[WeightScreen.kt:64-104](../composeApp/src/commonMain/kotlin/com/gymsmart/gymsmart/screens/WeightScreen.kt)).

**No hay contenedor de inyección de dependencias** (ni Koin, ni Hilt, ni un service locator propio).
Cada pantalla instancia sus propios servicios con `remember { AuthService() }`,
`remember { ProfileService(authService.client) }`, etc. Consecuencia real: en
[WeightScreen.kt:54-66](../composeApp/src/commonMain/kotlin/com/gymsmart/gymsmart/screens/WeightScreen.kt)
coexisten **tres** `HttpClient` distintos en la misma pantalla (uno manual sin cookies, el de
`authService`, y el de `WeightService`, que es justo el que usa `analyzeBody` y por tanto puede perder
la cookie de sesión). Esto es la causa raíz de buena parte de la deuda técnica del cliente.

### Navegación

`navigation/NavGraph.kt` usa Navigation-Compose con un `sealed class Screen` de rutas string, más una
ruta tipada (`RouteDetailArgs`, `@Serializable`) vía `composable<RouteDetailArgs>`. Sin deep links. Al
arrancar, un `LaunchedEffect` decide el `startDestination` llamando a `authService.me()` y
`profileService.hasProfile()` (NavGraph.kt:60-69); mientras se resuelve, no hay pantalla de carga
explícita.

### Red y autenticación

- Cliente HTTP: Ktor `HttpClient` con `ContentNegotiation` (JSON) y plugin `HttpCookies`. **Autenticación
  por cookie de sesión, no JWT.**
- `AppConfig.BASE_URL` está **hardcodeado** a la URL de Render
  ([AppConfig.kt:12](../composeApp/src/commonMain/kotlin/com/gymsmart/gymsmart/config/AppConfig.kt)), sin
  diferenciar debug/release. Hay URLs antiguas comentadas con IPs y nombres de personas.
- **La cookie de sesión se persiste sin cifrar en las 4 plataformas**: `SharedPreferences` en claro en
  Android, `java.util.prefs.Preferences` en JVM/Desktop, `localStorage` en JS/Wasm, `NSUserDefaults` en
  iOS (`CookiesStorageFactory.*.kt`). Ninguna usa almacenamiento cifrado. Ver hallazgo de seguridad en
  [03-seguridad.md](03-seguridad.md).

### GPS

- Interfaz común `LocationProvider.getLocationUpdates()` (Flow).
- **Android**: `LocationService.kt` usa `FusedLocationProviderClient` con `callbackFlow`, intervalo 1 s,
  filtro de distancia mínima 5 m — basado en eventos, bien implementado.
- **Desktop**: `DesktopLocationProvider` no implementa nada real (solo `delay` infinito) — no hay GPS en
  un ordenador, es el comportamiento esperado.
- **iOS/JS/Wasm**: no existe implementación concreta de `LocationProvider`. No es un problema de
  `expect/actual` (la interfaz no está declarada como tal), es que simplemente no se ha escrito ninguna
  clase para esas plataformas.

### Salud / wearable (Health Connect)

- Interfaz común `HealthDataProvider.getTodaySteps()/getTodayActiveCalories()`.
- **Android**: `HealthConnectProvider` lee `StepsRecord`/`ActiveCaloriesBurnedRecord` del día actual con
  `readRecords` — es una **consulta puntual, no una suscripción reactiva** (no usa
  `registerForUpdates`/callback).
- **Desktop**: `DesktopHealthDataProvider` devuelve siempre 0 (no hay wearable en un ordenador).
- **iOS/JS/Wasm**: sin implementación.
- **Patrón de recolección actual**: cada pantalla (`DashboardScreen`, `ProfileScreen`, `NutritionScreen`)
  dispara una lectura única en `LaunchedEffect(Unit)` (con un `delay(500)` arbitrario en Dashboard) solo
  al entrar a la pantalla. Si Health Connect tarda en tener datos frescos, o el usuario no reabre la
  pantalla, los datos quedan desactualizados. **Esta es la mejora más directa disponible** para la
  recolecta de pasos/kcal: pasar a sincronización en segundo plano (`WorkManager` + `registerForUpdates`
  o caché con invalidación periódica) en vez de polling puntual por pantalla. Ver
  [06-funcionalidades.md](06-funcionalidades.md).

### Mapa pantalla → servicio → endpoint

| Pantalla(s) | Servicio cliente | Endpoint servidor |
|---|---|---|
| Login/Register/Forgot/Reset | `AuthService` | `/auth/*` |
| Onboarding/Profile/Weight | `ProfileService` | `/profile/has`, `/profile/me`, `/profile/save` |
| Dashboard/Profile (pasos/kcal) | `HealthDataProvider` (local) | — (no hay endpoint propio, es local al dispositivo) |
| Nutrition | `NutritionService` | `/nutrition/add\|mine\|delete\|update\|history\|favorites/*` |
| Gps/MyRoutes/CommunityRoutes/RouteDetail | `GpsService` | `/gps/routes/*` |
| Subscription | `SubscriptionService` | `/subscription/status\|pay` |
| Weight (análisis IA) | `WeightService.analyzeBody` | `/ai/body-analysis` |
| Training | `TrainingService` (función suelta, no clase) | `"$BASE_URL/"` (raíz — **no existe** ruta real de training en el servidor) |

`Product`/`Nutriments` (para mapear la respuesta nutricional) están definidos **dentro de
`NutritionScreen.kt`** en vez de en `model/`, rompiendo la convención del resto del código.

**`TrainingScreen.kt` es la funcionalidad menos terminada de toda la app**: mensaje de error fijo
("Backend no responde en http://localhost:8000"), botón "Importar .GPX/.FIT (próximamente)"
deshabilitado, colores hardcodeados en vez de `GymSmartColors`, y no hay `TrainingRoute` real en el
servidor. Es efectivamente una pantalla placeholder.

## 3. Arquitectura del servidor (`server/`, Ktor)

Capas: `routes/` (HTTP) → `services/` (lógica + acceso a Turso) → Turso (HTTP API, vía `TursoService`).
Sin capa de repositorio separada de los servicios; cada `*Service` mezcla lógica de negocio y SQL.

### Endpoints

| Ruta | Métodos | Auth |
|---|---|---|
| `HealthRoute` | `GET /health` | Público |
| `AuthRoute` | `POST /auth/register\|login\|logout\|verify-email\|resend-verification\|forgot-password\|verify-reset-token\|reset-password` | Público |
| `AuthRoute` | `GET /auth/me`, `POST /auth/change-password`, `PUT /auth/profile` | Sesión |
| `FoodRoute` | `GET /food/search`, `GET /food/barcode/{code}` | **Público** (proxy a Open Food Facts, sin rate limit) |
| `GpsRoute` | `/gps/routes/*` (save, mine, points, delete, publish/unpublish, community) | Sesión (y comprobación de propiedad por `user_id` en todas) |
| `NutritionRoutes` | `/nutrition/add\|mine\|history\|favorites/*\|delete/{id}\|update/{id}` | Sesión (pero **sin comprobar propiedad** en delete/update — ver §seguridad) |
| `ProfileRoute` | `/profile/has\|me\|save`, `/weight/history` | Sesión |
| `BodyAnalysisRoute` | `POST /ai/body-analysis` | **Sin ninguna comprobación de sesión** — ver [03-seguridad.md](03-seguridad.md) |
| `SubscriptionRoute` | `/subscription/status\|pay` | Sesión |

### Autenticación

Sesiones de servidor con cookie `gymsmart_session` (httpOnly, 7 días, sin flag `secure` explícito). No
usa JWT. Contraseñas con BCrypt (`gensalt()` por defecto, coste 10) — correcto. Registro exige
verificación de email antes de permitir login. Tokens de verificación/reseteo son UUID de un solo uso con
expiración (24h / 1h) — diseño correcto.

### Acceso a Turso (`TursoService`)

100% parametrizado con placeholders `?` — **no hay inyección SQL**. No hay pool de conexiones (crea un
`HttpClient(CIO)` reutilizado durante la vida del proceso, correcto para una API HTTP como la de Turso).
Manejo de errores de red/Turso casi inexistente: si `execute()` falla, la excepción sube sin capturar en
casi todos los servicios.

### Integraciones externas

- **Email (Brevo/Resend)**: body JSON construido por interpolación directa de string, sin escapar — si
  un email contuviera `"` rompería el JSON.
- **OpenAI**: `gpt-4o` con imagen en base64. Endpoint `/ai/body-analysis` sin auth (ver seguridad).
- **Stripe**: no funcional como pasarela real. API antigua `/v1/charges` (deprecada, sin 3D
  Secure/SCA), ignora el número de tarjeta real salvo 3 tarjetas de test conocidas, y el servidor recibe
  PAN+CVC+caducidad en claro en el body. Aceptable solo porque hoy no cobra nada real.

### Fórmulas de nutrición (`ProfileService.computeTargets`)

Usa **Mifflin-St Jeor correctamente** (hombre: `10×kg+6.25×cm-5×edad+5`; mujer: `-161`), multiplicadores
de actividad estándar (1.2–1.9), proteína 2.0 g/kg en déficit / 1.8 g/kg resto (dentro de guías ISSN),
grasa fija al 25% de kcal, carbos por diferencia, mínimos de seguridad (1500/1200 kcal). **No está mal
hecho matemáticamente.** El problema concreto: cuando `activityLevel == "wearable"`, el servidor usa el
**BMR directo como TDEE**, sin sumar el gasto real de pasos/ejercicio que reporta la pulsera. Esto es
probablemente la causa principal de que "los números no vayan muy allá" — ver
[06-funcionalidades.md](06-funcionalidades.md).

### Code smells del servidor

- **Esquema desincronizado con el código**: `is_public` (en `gps_routes`) y `has_wearable` (en
  `user_profiles`) se usan en queries pero no están en los `CREATE TABLE IF NOT EXISTS` de
  `initTables()`. Solo funciona porque la tabla real en Turso ya las tiene añadidas a mano. En una base
  de datos nueva, el código fallaría. No hay sistema de migraciones real.
- Logging por `println` (26 ocurrencias) en vez de Logback (que está en el classpath sin usarse).
- Manejo de errores inconsistente entre servicios (`Result<T>`, excepciones directas, `null`/`false`
  silencioso, según el archivo).
- Duplicación entre `getRoutesByUser` y `getCommunityRoutes` en `GpsService` (misma query, cambia el
  `WHERE`).
- Precios/duraciones de "suscripción" hardcodeados (499 céntimos, 1 hora de premium) — valores de
  prueba, no reales.

## 4. Flujos de datos clave

- **Auth**: cliente → `AuthService` → cookie de sesión `gymsmart_session` ↔ servidor valida contra tabla
  de usuarios en Turso (password con BCrypt).
- **Registro de comida**: `NutritionScreen` → `NutritionService` → `/nutrition/add` → Turso. El origen de
  los datos nutricionales (Open Food Facts vía `/food/search|barcode`) no está confirmado como conectado
  directamente desde `NutritionScreen` en esta pasada — revisar en Fase 4.
- **Objetivos de peso**: `ProfileService.saveProfile` → `/profile/save` → `ProfileService.computeTargets`
  en el servidor (Mifflin-St Jeor + factor de actividad, ver arriba).
- **GPS**: `LocationProvider` (Android: FusedLocation; Desktop: no-op) → `GpsScreen`/`GpsService` →
  `/gps/routes/*` → Turso (`gps_routes` + `gps_points`).
- **Wearable**: `HealthDataProvider` (Android: Health Connect, consulta puntual) → se muestra en
  Dashboard/Profile/Nutrition. **No hay endpoint de servidor para esto**: es puramente local al
  dispositivo, y el servidor no lo recibe salvo que el usuario lo traslade manualmente a un perfil con
  `activityLevel == "wearable"`.

## 5. Estado real del soporte iOS / Web (por qué no funciona)

**Causa raíz concreta, no un problema difuso de "falta pulir":**

- `LocationProvider` y `HealthDataProvider` son interfaces normales en `commonMain` (no son
  `expect`/`actual`). Solo tienen implementaciones concretas para Android y Desktop.
- `App()` (`composeApp/.../App.kt:11-16`) exige `locationProvider`, `healthDataProvider` y
  `onRequestLocationPermission` como **parámetros obligatorios sin valor por defecto**.
- `MainViewController.kt` (iOS) hace `ComposeUIViewController { App() }` — **llama a `App()` sin
  argumentos**.
- Resultado: el target iOS **no compila a nivel de Kotlin**. No genera el framework
  `ComposeApp.xcframework`. No es una cuestión de Xcode/Podfile (el scaffold de `iosApp/` está bien, sin
  CocoaPods, usa el mecanismo moderno de framework embebido del wizard de KMP).
- Lo mismo aplica a Web (JS/Wasm): no hay implementación de `LocationProvider`/`HealthDataProvider` ni un
  punto de entrada que llame a `App(...)` con esos providers.
- Secundario: no hay target `iosX64()` (simulador Intel) declarado — irrelevante en Apple Silicon.
- No se detectó ninguna fuga de API de Android SDK en `commonMain` — el resto del árbol de dependencias
  para iOS parece sano una vez resuelto el wiring de `App()`.

**Para arreglarlo (Fase 6, nuevas funcionalidades, no ahora):** escribir implementaciones iOS de
`LocationProvider` (CoreLocation) y `HealthDataProvider` (HealthKit), y pasarlas desde
`MainViewController.kt`. Es un trabajo acotado, no una reescritura.

## 6. Otros módulos legacy (ver detalle en [05-deuda-tecnica.md](05-deuda-tecnica.md))

- `shared/`: boilerplate del wizard, sin lógica de negocio real.
- `backend/`: prototipo Python/Alembic nunca materializado, sin código de aplicación.
- `frontend/`: carpeta huérfana con solo `node_modules`, sin proyecto.
