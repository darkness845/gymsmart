# Deuda técnica e inventario de código muerto

Generado en la exploración de 2026-10-26. Cada ítem tiene un veredicto y una acción propuesta; ninguna
acción se ejecuta sin confirmación del usuario, aunque el riesgo sea bajo.

## Carpetas/módulos completos candidatos a eliminar

| Ítem | Veredicto | Evidencia | Acción propuesta |
|---|---|---|---|
| `backend/` | Código muerto | Solo `backend/.env`, `alembic/versions/` vacío (sin `alembic.ini` ni `env.py`), y `venv/`. Ningún `.py` de aplicación. Prototipo Python/Alembic anterior al servidor Ktor, nunca materializado. El server (`server/`) lee su `.env` de la **raíz** del repo, no de `backend/.env`. | ✅ **Eliminado** (2026-10-26): no estaba rastreado por git, no tenía referencias en ningún `build.gradle.kts`/`settings.gradle.kts`/`Dockerfile`. |
| `frontend/` | Código muerto / residuo | Solo contiene `node_modules/`, sin `package.json` ni `src/`. No hay manifiesto que la referencie. | ✅ **Contenido eliminado** (2026-10-26). La carpeta vacía en sí quedó bloqueada por Windows ("en uso por otro proceso", probablemente un editor con el directorio abierto) — pendiente borrarla manualmente o reintentar en otra sesión. |
| `shared/` | Vestigial, no muerto | Boilerplate del wizard de KMP (`Platform.kt` + `Greeting.kt`). `server` depende de él pero apenas lo usa. | No eliminar (el `server` lo referencia), pero no añadir lógica de negocio aquí salvo que se decida convertirlo en una capa de dominio compartida real (decisión de arquitectura para fases futuras, no limpieza). |

## Código muerto dentro de módulos activos

| Archivo | Veredicto | Evidencia | Acción propuesta |
|---|---|---|---|
| `composeApp/src/androidMain/.../AndroidGpsTracker.kt` | Código muerto | Implementación alternativa de GPS no referenciada desde ningún sitio; postea a `/gps` (endpoint que no es el real `/gps/routes/*`). | Eliminar en Fase 3. |
| `composeApp/src/androidMain/.../PermissionHelper.kt` | Código muerto | No referenciado; `MainActivity` gestiona permisos directamente con `rememberLauncherForActivityResult`. | Eliminar en Fase 3. |
| `composeApp/.../services/WeightService.kt` función `addWeight` | Código muerto y roto | `client.post(".../profile/save") { }` con bloque vacío — no fija `contentType` ni `setBody`, nunca envía el peso. Nadie lo llama; `WeightScreen` usa `profileService.saveProfile()` en su lugar. | Eliminar el método en Fase 3. |
| `composeApp/.../screens/TrainingScreen.kt` + `TrainingService.kt` | Placeholder sin terminar | Mensaje de error fijo, botón deshabilitado "próximamente", colores hardcodeados, llama a `"$BASE_URL/"` (raíz) porque no existe `TrainingRoute` en el servidor. | Decidir con el usuario: ¿se completa como funcionalidad real (Fase 5/6) o se retira del menú hasta entonces? No borrar sin decidir alcance. |

## Inconsistencias de arquitectura (no son "código muerto", pero generan confusión)

- **Modelos fuera de `model/`**: `Product`/`Nutriments` están definidos dentro de `NutritionScreen.kt` en
  vez de en la carpeta `model/` del cliente.
- **`TrainingService.kt` rompe el patrón del resto**: usa una función suelta + `HttpClient` top-level
  propio, en vez de la clase `XService(client: HttpClient)` que usan todos los demás servicios.
- **Múltiples `HttpClient`/`AuthService` por pantalla**: al no haber DI, cada pantalla crea sus propias
  instancias (ver detalle en [01-arquitectura.md](01-arquitectura.md)). No es código muerto, pero es la
  raíz de bugs como la posible pérdida de cookie de sesión en `WeightScreen`.
- **`GpsService.getRoutesByUser` y `getCommunityRoutes`** (servidor) son prácticamente la misma query con
  distinto `WHERE` — se puede unificar.
- **Comentarios con URLs/IPs y nombres de personas** en `AppConfig.kt` (residuo de desarrollo entre
  compañeros/pruebas) — limpiar en Fase 3.
- **Esquema de BBDD desincronizado del código** (`is_public`, `has_wearable` usados pero no declarados en
  `CREATE TABLE IF NOT EXISTS`) — no es código muerto, es un riesgo de integridad; ver
  [03-seguridad.md](03-seguridad.md) y tratarlo junto con la limpieza del servidor.

## Qué NO se toca sin decisión explícita del usuario

- Borrado de `backend/` y `frontend/` (aunque el veredicto es claro, borrar carpetas completas se
  confirma siempre).
- Cualquier decisión sobre el alcance final de `TrainingScreen` (completar vs. retirar).
