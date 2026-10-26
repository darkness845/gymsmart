# Registro de sesiones

Bitácora corta por sesión de trabajo sobre el endurecimiento del proyecto. Entrada nueva arriba.

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
- **Siguiente sesión**: confirmar commit de esta limpieza; decidir cuándo desplegar a Render todo lo de
  Fase 2; probar el cambio de cookie cifrada de Android en un dispositivo/emulador real; seguir con lo
  que queda de Fase 3 (logging del cliente con una librería multiplataforma, estrategia de DI mínima) o
  pasar a Fase 4 (validación de fiabilidad de nutrición/GPS/wearable).
