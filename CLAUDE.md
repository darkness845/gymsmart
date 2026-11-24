# GymSmart — instrucciones para Claude

Este proyecto tiene un endurecimiento en marcha (documentación, seguridad, limpieza, validación y
mejora de funcionalidades) repartido en fases, trabajado a lo largo de muchas sesiones.

## Al empezar cualquier sesión en este repo

1. **Lee primero [doc/CONTINUAR.md](doc/CONTINUAR.md).** Es el documento de continuidad: dice en qué
   fase/bloque estamos, qué se acaba de terminar, qué sigue, y qué queda pendiente de verificar o
   commitear. Está pensado para que una sesión nueva (sin memoria de las anteriores) pueda seguir
   exactamente donde se dejó, sin tener que releer todo el historial.
2. Si necesitas contexto más profundo, el resto de `doc/` tiene el resto:
   - `00-roadmap.md` — las fases generales del proyecto y su estado.
   - `01-arquitectura.md` — mapa de módulos, arquitectura cliente/servidor, flujos de datos.
   - `02-convenciones.md` — reglas de código a seguir, con el porqué de cada una.
   - `03-seguridad.md` — hallazgos de seguridad con severidad y estado.
   - `04-produccion-playstore.md` — checklist para cuando (si alguna vez) se salga de uso personal.
   - `05-deuda-tecnica.md` — inventario de código muerto/legacy.
   - `06-funcionalidades.md` — veredicto real de cada funcionalidad (nutrición, GPS, wearable...).
   - `07-registro-sesiones.md` — bitácora histórica detallada, sesión por sesión.
   - `08-backlog-mejoras.md` — la cola de trabajo de la Fase 5, con severidad y estado de cada ítem.
3. **Actualiza `doc/CONTINUAR.md` cada vez que termines algo**, no solo al final de la sesión — si el
   contexto se corta a mitad, ese documento tiene que seguir siendo fiable. Actualiza también
   `07-registro-sesiones.md` (detalle histórico) y el doc específico que corresponda (`08-backlog-mejoras.md`,
   `03-seguridad.md`, etc.) cuando cierres un hallazgo.

## Reglas de esta sesión de trabajo

- Nunca se hacen cambios destructivos (borrar módulos, resetear la BBDD, rotar claves) sin confirmación
  explícita del usuario, aunque el proyecto sea de uso personal.
- Nunca se hace commit ni push sin que el usuario lo pida explícitamente.
- **El Android SDK sí está instalado** (comprobado el 2026-11-24: `local.properties` apunta al SDK del
  usuario, con `android-36`, y `./gradlew :composeApp:compileDebugKotlinAndroid --rerun-tasks` termina
  en BUILD SUCCESSFUL). Esta nota decía antes justo lo contrario y era **falsa**: por culpa de ella
  varias sesiones dieron por imposible compilar `androidMain` y dejaron código sin verificar.
  **Compilar el target Android es obligatorio tras tocar `androidMain`.**
  Lo que sigue sin poder hacerse aquí es *ejecutar* la app en un dispositivo o emulador: el
  comportamiento en tiempo de ejecución (notificaciones, permisos, servicios en segundo plano) lo tiene
  que probar el usuario en su móvil, y eso sí se marca como "sin verificar en dispositivo".
- Verificar siempre con `./gradlew :shared:jvmTest :server:compileKotlin :composeApp:compileKotlinJvm`
  tras cada cambio relevante, añadiendo `:composeApp:compileDebugKotlinAndroid` si se tocó `androidMain`.
