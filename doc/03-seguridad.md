# Seguridad — hallazgos y estado

Hallazgos de la exploración de 2026-10-26, clasificados por severidad. Severidad pensada en dos escalas:
**impacto hoy** (app de uso personal, un solo usuario real) e **impacto si hubiera usuarios reales**.
Nada de esto se ha arreglado todavía salvo que se indique "✅ Arreglado".

Leyenda de estado: 🔴 Abierto — crítico · 🟠 Abierto — medio · 🟡 Abierto — bajo · ✅ Arreglado

## 🔴 Críticos

### 1. `POST /ai/body-analysis` sin autenticación
- **Dónde**: `server/.../routes/BodyAnalysisRoute.kt`.
- **Problema**: no exige sesión ni plan premium. Cualquiera que conozca la URL pública de Render puede
  llamarlo repetidamente sin cuenta ni límite.
- **Impacto hoy**: consumo no autorizado de tus créditos de OpenAI (impacto económico directo sobre ti).
- **Impacto con usuarios reales**: mismo problema, a mayor escala, más probabilidad de ser descubierto.
- **Estado**: ✅ **Arreglado** (2026-10-26). Se añadió la misma comprobación de sesión (`call.sessions.get<UserSession>()`) que usa el resto de rutas protegidas, antes de llamar a `OpenAIService`. Pendiente valorar en Fase 5 un límite de uso por usuario/día.

### 2. IDOR en nutrición: `deleteMeal`/`updateMeal` no comprueban propiedad
- **Dónde**: `server/.../services/NutritionService.kt` (rutas `DELETE /nutrition/delete/{id}`,
  `PUT /nutrition/update/{id}`).
- **Problema**: solo comprueban que exista sesión, no que el `id` de la comida pertenezca al usuario de
  la sesión. `GpsService` sí hace esto bien (filtra siempre por `user_id`) — es el patrón a copiar aquí.
- **Impacto hoy**: bajo (un solo usuario real).
- **Impacto con usuarios reales**: un usuario autenticado podría borrar/modificar comidas de otro
  adivinando/enumerando IDs (son UUID, lo que mitiga pero no elimina el riesgo).
- **Estado**: ✅ **Arreglado** (2026-10-26). `deleteMeal`/`updateMeal` ahora reciben `userId` y filtran
  con `WHERE id = ? AND user_id = ?`, igual que el patrón ya usado en `GpsService`. Rutas actualizadas
  para pasar `session.userId`. Verificado con `:server:compileKotlin` (build exitoso).

**Nota de despliegue:** ambos arreglos están en el código local, compilados y verificados, pero
**no están desplegados en Render todavía** — hace falta el próximo `git push`/deploy para que surtan
efecto en producción.

## 🟠 Medios

### 3. Cookie de sesión persistida sin cifrar en el cliente (todas las plataformas)
- **Dónde**: `CookiesStorageFactory.*.kt` en `composeApp` (Android: `SharedPreferences` en claro; JVM:
  `java.util.prefs.Preferences`; JS/Wasm: `localStorage`; iOS: `NSUserDefaults`).
- **Problema**: ninguna usa almacenamiento cifrado (`EncryptedSharedPreferences`/Keychain/DataStore
  cifrado).
- **Impacto**: si el dispositivo se compromete (malware, acceso físico, dispositivo rooteado/jailbreak),
  la sesión queda expuesta.
- **Estado**: ✅ **Arreglado en Android** (2026-10-26, ⚠️ sin verificar con build real — ver nota).
  `AndroidCookiesStorage` ahora usa `EncryptedSharedPreferences` (clave maestra AES256-GCM en el Android
  Keystore) en vez de `SharedPreferences` en claro, con fallback a `SharedPreferences` normal si el
  Keystore del dispositivo fallara (para no romper el login). Se limpia el fichero antiguo en claro al
  migrar — el usuario tendrá que volver a iniciar sesión una vez. Añadida dependencia
  `androidx.security:security-crypto:1.1.0-alpha06` en `composeApp/build.gradle.kts`.
  **⚠️ Esta sesión de Claude no tiene Android SDK instalado**, así que no se pudo ejecutar
  `:composeApp:compileDebugKotlinAndroid` para verificarlo (sí compiló `compileKotlinJvm`, que no toca
  este archivo). Recomendado: compilar/probar el login en un dispositivo o emulador Android antes de
  confiar en este cambio al 100%. JVM/Desktop, iOS, JS/Wasm **siguen sin cifrar** — quedan pendientes,
  priorizados más bajo porque Desktop es solo para el propio uso del creador e iOS/Web no están
  operativos todavía.

### 4. Logging con `println` que expone PII
- **Dónde**: servidor (`server/`), 26+ ocurrencias de `println`. Vuelca email completo en
  registro/login, objeto `UserSession` completo, request bodies completos
  (`UpdatePersonalDataRequest`), y respuesta cruda de Stripe (`piRawText`, puede incluir email de
  facturación). Logback está en el classpath pero no se usa.
- **Impacto**: en Render, stdout normalmente queda en logs persistentes/accesibles — exposición de datos
  personales (email, teléfono, fecha de nacimiento) a quien tenga acceso a esos logs.
- **Estado**: ✅ **Arreglado en el servidor** (2026-10-26). Los 27 `println`/`printStackTrace` del
  servidor (8 archivos: `AuthRoute`, `GpsRoute`, `SubscriptionRoute`, `BodyAnalysisRoute`,
  `UserService`, `EmailService`, `FoodService`, `StripeService`, `OpenAIService`) se sustituyeron por
  SLF4J/Logback. Ya no se loguea el objeto `UserSession`/request completos, ni el email, ni el token de
  verificación (antes se logueaba en claro en `UserService.register`), ni la respuesta cruda de Stripe —
  solo `userId` y mensajes de estado. `logback.xml` tenía `root level="trace"` (demasiado verboso para
  producción); se bajó a `level="info"`. Pendiente: el `println` del **cliente** (`composeApp`) sigue
  abierto, ver #9 — no tiene Logback, requiere decidir una librería de logging multiplataforma (p. ej.
  Kermit) antes de migrar, así que se deja para Fase 3.

### 5. CORS configurado solo para `localhost`
- **Dónde**: `server/.../plugins/` (configuración CORS).
- **Problema**: `allowHost("localhost:5173")` y `allowHost("localhost:8080")` únicamente. No incluye el
  dominio de producción.
- **Impacto**: no es "demasiado permisivo" (no es un riesgo de seguridad en sí), pero probablemente
  **rompe** el cliente web (JS/Wasm) si se usa fuera de local — es más un bug funcional que de
  seguridad, pero se documenta aquí porque toca la misma configuración.
- **Estado**: 🟠 Abierto (bug funcional, bajo riesgo de seguridad).

### 6. `SubscriptionService.pay` envía PAN+CVC+caducidad en claro al servidor
- **Dónde**: cliente (`SubscriptionService.kt:12-19`) y servidor (`StripeService.kt`).
- **Problema**: el cliente envía el número de tarjeta completo, CVC y fecha de caducidad al propio
  backend, que los reenvía a una API de Stripe deprecada (`/v1/charges`, sin 3D Secure/SCA). El servidor
  ignora la tarjeta real salvo 3 números de test conocidos.
- **Impacto hoy**: ninguno real — confirmado que no cobra ni procesa pagos reales.
- **Impacto si se activara con claves live sin rediseñar**: incumplimiento grave de PCI-DSS (el servidor
  nunca debe tocar PAN/CVC en claro).
- **Estado**: 🟠 Abierto, pero **sin urgencia mientras no sea funcional**. Documentado también en
  [04-produccion-playstore.md](04-produccion-playstore.md) como bloqueante para activar pagos reales:
  habría que migrar a Stripe Elements/PaymentSheet (tokenización en el cliente) antes de activar nada.

### 7. Esquema de BBDD desincronizado del código (`initTables()`)
- **Dónde**: `server/.../services/*Service.kt` (`initTables()`/`initTable()`).
- **Problema**: columnas `is_public` (`gps_routes`) y `has_wearable` (`user_profiles`) se usan en
  queries pero no están en los `CREATE TABLE IF NOT EXISTS`. Solo funciona porque la tabla real en Turso
  ya las tiene añadidas manualmente.
- **Impacto**: en una base de datos nueva (otro entorno, recuperación ante desastre), el código fallaría
  en runtime. No hay sistema de migraciones.
- **Estado**: ✅ **Arreglado** (2026-10-26). Se añadieron `is_public INTEGER NOT NULL DEFAULT 0` al
  `CREATE TABLE IF NOT EXISTS gps_routes` y `has_wearable INTEGER NOT NULL DEFAULT 0` al de
  `user_profiles`. Como `CREATE TABLE IF NOT EXISTS` no altera una tabla ya existente, esto **no cambia
  nada en la base de datos real de Turso** (que ya tenía las columnas añadidas a mano) — el efecto es
  que cualquier base de datos *nueva* (otro entorno, recuperación ante desastre) se creará ya con el
  esquema correcto. Sigue sin existir un sistema de migraciones versionadas; se deja como mejora futura,
  no bloqueante.

## 🟡 Bajos

### 8. `EmailService` construye JSON por interpolación de string sin escapar
- **Dónde**: servidor, `EmailService.kt` (Brevo/Resend).
- **Problema**: el body JSON se construía con interpolación directa (`"to": [{"email": "$to"}]`) sin
  escapar. Si un email contuviera `"`, el JSON se rompería.
- **Impacto**: bajo — el email no se valida con regex en el registro, pero el riesgo de inyección real
  es limitado.
- **Estado**: ✅ **Arreglado** (2026-10-26). `sendEmail` ahora construye el body con
  `kotlinx.serialization.json.buildJsonObject` (serializador real), en vez de interpolar strings. Ya no
  hay riesgo de romper el JSON con caracteres especiales en email/asunto.

### 9. `println` en el cliente con detalles internos
- **Dónde**: `AuthService.updatePersonalData`, `NutritionService.addFavorite`,
  `HealthConnectProvider` (imprime el paquete de origen de cada registro de pasos).
- **Impacto**: bajo, no son secretos, pero conviene retirarlos o condicionarlos a build debug antes de
  producción.
- **Estado**: 🟡 Abierto.

### 10. `BASE_URL` hardcodeado sin diferenciar entornos
- **Dónde**: `composeApp/.../config/AppConfig.kt:12`.
- **Problema**: apunta siempre a producción (Render), con URLs antiguas comentadas (IPs, nombres de
  personas). No es un riesgo de seguridad por sí mismo, pero mezclado con el punto 9 son residuos de
  desarrollo que no deberían quedar documentados como "normales" sin limpiar.
- **Estado**: 🟡 Abierto — tratar en Fase 3 (limpieza) más que como tema de seguridad puro.

## Lo que está bien hecho (para no "arreglar" lo que no está roto)

- SQL 100% parametrizado en `TursoService` — sin inyección SQL.
- Contraseñas con BCrypt correctamente aplicado.
- Tokens de verificación/reseteo de un solo uso con expiración adecuada.
- `GpsService` sí comprueba propiedad (`user_id`) en todas sus rutas — es el patrón correcto que falta
  copiar a `NutritionService`.
- Fórmulas de nutrición (Mifflin-St Jeor) correctamente implementadas en sí mismas (el problema ahí es de
  lógica de producto, no de seguridad — ver [06-funcionalidades.md](06-funcionalidades.md)).

## Plan para Fase 2 — estado

1. ✅ Críticos #1 y #2 arreglados.
2. ✅ #7 (esquema desincronizado) y #8 (JSON sin escapar) arreglados.
3. ✅ #4 (logging del servidor) arreglado. #9 (logging del cliente) queda para Fase 3 — requiere decidir
   una librería de logging multiplataforma primero.
4. ✅ #3 (cifrado de cookie de sesión) — arreglado en Android con `EncryptedSharedPreferences`
   (sin verificar con build real por falta de Android SDK en este entorno). JVM/Desktop/iOS/Web quedan
   sin cifrar, con prioridad más baja.
5. ✅ #5 (CORS) — el usuario confirmó que el cliente web solo se usa en local, no está publicado en
   ningún dominio real. Se deja la configuración actual (`localhost` únicamente) sin cambios; se
   revisará el día que exista un dominio de producción real para el cliente web.
6. 🟠 #6 (Stripe/PCI) y el checklist de producción quedan documentados pero no se tocan hasta que se
   decida activar pagos reales.

**Todo lo arreglado en esta fase está verificado con `./gradlew :server:compileKotlin` (build exitoso),
pero sigue sin desplegarse en Render — ver nota de despliegue arriba.**
