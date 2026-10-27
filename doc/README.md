# Documentación interna – GymSmart

Esta carpeta es la base de conocimiento del proyecto, pensada principalmente para que Claude (y cualquier
colaborador futuro) pueda trabajar en la app sin tener que releer todo el código fuente en cada sesión, y
para que el propio creador tenga un mapa claro de cómo está hecha la app, qué es seguro tocar y qué falta
antes de pensar en producción.

**Contexto del proyecto:** GymSmart es un TFG funcional (no un producto comercial). Hay base de datos real
en Turso, servidor en Render, y uso de servicios externos (Brevo/Resend para email, Stripe en modo no
funcional, OpenAI con créditos limitados). Es de uso personal del creador, así que no hay restricciones de
acceso a credenciales reales durante el desarrollo — pero sí queremos dejar documentado qué habría que
cambiar si algún día la app sale a producción real (ver [04-produccion-playstore.md](04-produccion-playstore.md)).

## Índice

| Doc | Contenido |
|---|---|
| [CONTINUAR.md](CONTINUAR.md) | **Léelo PRIMERO en cada sesión nueva.** Estado actual, qué se acaba de terminar, qué sigue. Se actualiza constantemente, no solo al cerrar sesión. |
| [00-roadmap.md](00-roadmap.md) | Plan de fases del proyecto y su estado. |
| [01-arquitectura.md](01-arquitectura.md) | Mapa de módulos, patrones usados, flujos de datos clave (auth, nutrición, GPS, wearable). |
| [02-convenciones.md](02-convenciones.md) | Reglas de código, naming, estructura de paquetes, cómo añadir una pantalla/endpoint nuevo sin romper el patrón. |
| [03-seguridad.md](03-seguridad.md) | Hallazgos de seguridad (auth, SQL, secretos, CORS, logging) y su estado (abierto/arreglado). |
| [04-produccion-playstore.md](04-produccion-playstore.md) | Checklist de lo que falta para salir de "uso personal" a producción real (Play Store, pagos reales, GDPR, etc.). |
| [05-deuda-tecnica.md](05-deuda-tecnica.md) | Inventario de código muerto, duplicado o legacy (p.ej. `backend/`, `frontend/`) y decisiones tomadas sobre cada cosa. |
| [06-funcionalidades.md](06-funcionalidades.md) | Estado real de cada funcionalidad (nutrición/kcal, objetivos, GPS, wearable) y plan de mejora de cada una. |
| [07-registro-sesiones.md](07-registro-sesiones.md) | Bitácora corta por sesión: qué se hizo, qué se decidió, qué queda pendiente. |
| [08-backlog-mejoras.md](08-backlog-mejoras.md) | **Cola de trabajo**: todos los bugs y mejoras encontrados en la Fase 4, priorizados por impacto real, con ubicación y estado. |
| [09-plan-de-pruebas.md](09-plan-de-pruebas.md) | Checklist de pruebas manuales (Desktop + Android) antes de pasar a Fase 6, con el criterio de qué debería pasar tras cada arreglo. |

## Reglas para usar esta documentación

1. **Antes de tocar código en una sesión nueva**, lee `00-roadmap.md` para saber en qué fase estamos y
   `01-arquitectura.md` / `02-convenciones.md` para el módulo que vayas a tocar. Evita releer con grep/Read
   todo el repo si el doc ya tiene la respuesta.
2. **Si el código y el doc no coinciden**, manda el código — el doc está desactualizado y hay que
   corregirlo en el mismo cambio.
3. **Cada fase que se cierre** debe dejar una entrada en `07-registro-sesiones.md` y actualizar el estado
   en `00-roadmap.md`.
4. Estos documentos viven en el repo (se pueden commitear) salvo que el usuario diga lo contrario. No
   contienen secretos ni valores de `.env`, solo nombres de variables y referencias.
