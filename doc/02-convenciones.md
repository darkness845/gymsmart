# Convenciones de código — observadas y a seguir

Este documento tiene dos partes: lo que el código **ya hace hoy de forma consistente** (para no romperlo
sin querer) y las **reglas a aplicar de aquí en adelante** para no seguir acumulando inconsistencias.

## Lo que ya es consistente (respetarlo)

- Paquete base `com.gymsmart.gymsmart`, con subpaquetes por responsabilidad: `screens/`, `services/`,
  `model/`, `navigation/`, `config/`, `components/`, `ui/theme/` en el cliente; `routes/`, `services/`,
  `model/`, `plugins/` en el servidor.
- Patrón de servicio cliente: `class XService(private val client: HttpClient)` que envuelve llamadas
  Ktor con try/catch. (Excepción: `TrainingService.kt`, que rompe el patrón — ver
  [05-deuda-tecnica.md](05-deuda-tecnica.md).)
- Patrón de servicio servidor: `class XService` con métodos que hablan directo con `TursoService` via
  SQL parametrizado (`?` placeholders). Mantener siempre parametrizado, nunca concatenar input de
  usuario en SQL.
- Modelos de red como `@Serializable data class` en `model/`. (Excepción: `Product`/`Nutriments` dentro
  de `NutritionScreen.kt` — mover a `model/` al tocar ese archivo.)
- GPS: siempre comprobar `user_id` en el servidor antes de leer/modificar una ruta — es el patrón
  correcto a copiar en cualquier recurso nuevo que pertenezca a un usuario (ver hallazgo IDOR en
  [03-seguridad.md](03-seguridad.md)).
- Colores/tema desde `GymSmartColors` (tema centralizado) — no hardcodear colores en una pantalla nueva.

## Reglas a aplicar de aquí en adelante

1. **No instanciar `HttpClient`/`XService` dentro de cada pantalla.** Mientras no haya un contenedor de
   DI, al menos reutilizar una única instancia por app (p. ej. un objeto `AppServices` con `by lazy`), en
   vez de `remember { AuthService() }` repetido en cada pantalla. Evita el bug de cookies perdidas visto
   en `WeightScreen`. Si se decide introducir Koin, se hace de forma incremental, pantalla por pantalla,
   no como un big-bang.
2. **Todo recurso que pertenezca a un usuario debe filtrarse por `user_id` en el servidor**, sin
   excepciones — copiar el patrón de `GpsService`.
3. **SQL siempre parametrizado.** Nunca construir una query con interpolación de strings que incluya
   input de usuario.
4. **Logging con Logback, no `println`.** Nunca loguear objetos de sesión completos, contraseñas, tokens,
   ni payloads completos con PII (email, teléfono, fecha de nacimiento). Si hace falta depurar, loguear
   solo IDs no sensibles.
5. **Toda tabla nueva o columna nueva en Turso se declara en el `CREATE TABLE IF NOT EXISTS`
   correspondiente**, nunca se añade solo a mano en la base de datos real sin reflejarlo en el código
   (motivo: ver hallazgo de esquema desincronizado en [03-seguridad.md](03-seguridad.md)).
6. **Modelos de red/dominio van en `model/`**, nunca definidos dentro de un archivo de pantalla.
7. **No añadir código "de prueba" permanente** (URLs hardcodeadas con IPs/nombres de personas en
   comentarios, botones "próximamente" sin ticket asociado) sin dejarlo anotado en
   [05-deuda-tecnica.md](05-deuda-tecnica.md) si se va a quedar más de una sesión.
8. **Antes de añadir un endpoint nuevo**, decidir explícitamente si requiere sesión o es público, y
   dejarlo así desde el primer commit (el hallazgo de `/ai/body-analysis` sin auth viene de no haber
   decidido esto a tiempo).
9. **Cambios de limpieza (Fase 3) no deben alterar comportamiento visible.** Si un refactor cambia algo
   que el usuario puede notar al usar la app, no es limpieza, es una mejora de funcionalidad y se trata
   en Fase 5 (con su propio commit/aviso).

## Cómo añadir una pantalla + servicio + endpoint nuevo (receta corta)

1. Servidor: añadir ruta en `routes/XRoute.kt`, decidir auth (regla 8), implementar en
   `services/XService.kt` con SQL parametrizado y filtrado por `user_id` si aplica (regla 2).
2. Si hay tabla/columna nueva, declararla en `initTables()` (regla 5).
3. Cliente: modelo en `model/` (regla 6), servicio `XService(client: HttpClient)` siguiendo el patrón
   existente, pantalla en `screens/` usando `GymSmartColors` del tema.
4. Reutilizar instancias de servicio en vez de crear una nueva por pantalla cuando sea posible (regla 1).
