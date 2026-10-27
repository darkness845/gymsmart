# Roadmap de endurecimiento de GymSmart

Objetivo general: **antes de seguir añadiendo funcionalidades nuevas**, dejar la app en un estado
"profesional" — documentada, segura, sin código muerto/duplicado, y con las funcionalidades existentes
(nutrición/kcal, objetivos, GPS, wearable) verificadas como fiables. Solo después de esto se retoma el
desarrollo de funcionalidades nuevas.

Cada fase tiene objetivo, alcance y "criterio de salida" (qué tiene que ser verdad para pasar a la
siguiente). Las fases son secuenciales pero no estrictamente bloqueantes: si en Fase 2 (seguridad)
aparece algo urgente, se arregla aunque todavía no se haya cerrado del todo la Fase 1.

## Estado actual

| Fase | Estado | Notas |
|---|---|---|
| Fase 0 — Preparación | ✅ Hecho | Carpeta `doc/` creada, roadmap definido. |
| Fase 1 — Documentación de arquitectura | ✅ Primera versión completa | `01-arquitectura.md`, `02-convenciones.md`, `05-deuda-tecnica.md` escritos. Se refinarán si aparecen inexactitudes al tocar código. |
| Fase 2 — Seguridad | 🔄 7 de 10 resueltos, 1 diferido a propósito | Solo queda #9 (logging del cliente, para Fase 3) y #6 (Stripe/PCI, diferido hasta activar pagos reales). **Falta desplegar todo a Render.** |
| Fase 3 — Limpieza de código | 🔄 En curso | `backend/`/`frontend/` eliminados; `AndroidGpsTracker.kt`, `PermissionHelper.kt`, `WeightService.addWeight` eliminados; modelos de nutrición movidos a `model/`; duplicación de `GpsService` unificada; comentarios residuales de `AppConfig.kt` limpiados. Pendiente: logging del cliente, estrategia de DI mínima. |
| Fase 4 — Validación de fiabilidad (kcal/objetivos/GPS/wearable) | ✅ Completada | Investigación exhaustiva de GPS, wearable, IA, datos de alimentos, resiliencia y pantallas. ~60 hallazgos inventariados y priorizados en `08-backlog-mejoras.md`; 3 arreglados en el acto (incluida una regresión propia). |
| Fase 5 — Mejora de funcionalidades existentes | 🔄 En curso — bloques 1, 2, 3 y 4 cerrados | Ver estado detallado en `08-backlog-mejoras.md` → "Orden de ataque". Pendiente: bloque 5 (gate premium, redimensionar imagen IA, caché OFF), sync del wearable en segundo plano (WEAR-3). |
| Fase 6 — Nuevas funcionalidades (incluye soporte iOS/Web real) | ⏳ Bloqueada hasta cerrar 1-5 | Causa raíz de que iOS no compile ya diagnosticada en `01-arquitectura.md §5`. |

---

## Fase 0 — Preparación y gobierno del proyecto

- Crear `doc/` con la estructura de este índice.
- Definir estas fases y dejarlas en un documento vivo (este archivo).

**Criterio de salida:** esta estructura existe y el usuario la ha visto. ✅

---

## Fase 1 — Documentación de arquitectura actual

Entender y dejar escrito, sin ambigüedad, cómo está hecha la app *tal y como está hoy* (no cómo debería
estar). Esto evita releer todo el código en cada sesión futura.

- Mapa de módulos: `composeApp` (cliente KMP: Android/Desktop/iOS/Web), `shared`, `server` (Ktor), y
  carpetas legacy (`backend/`, `frontend/`).
- Arquitectura de capas del cliente (UI/estado, servicios, red) y del servidor (rutas/servicios/modelo).
- Flujos de datos clave: autenticación, registro de comidas/kcal, objetivos de peso, rutas GPS, conexión
  con pulsera/Health Connect.
- Convenciones reales observadas (naming, estructura de paquetes, manejo de errores, cómo se instancian
  dependencias).
- Inventario inicial de deuda técnica y de módulos legacy (`backend/`, `frontend/`) con veredicto de si
  están vivos o son restos de iteraciones anteriores.
- Estado real del soporte iOS: qué falta exactamente para que compile/funcione.

**Entregables:** `01-arquitectura.md`, `02-convenciones.md`, `05-deuda-tecnica.md` (primera versión),
sección de iOS dentro de `01-arquitectura.md`.

**Criterio de salida:** Claude puede explicar cualquier flujo de la app (login, registro de comida, GPS,
wearable) citando archivos concretos sin tener que re-explorar el repo.

---

## Fase 2 — Seguridad

Revisión de lo que importa de verdad para una app con datos personales y de salud, aunque hoy sea de uso
personal:

- Autenticación/autorización (JWT o sesiones, expiración, hashing de contraseñas).
- Acceso a Turso: SQL parametrizado vs concatenado (inyección SQL), permisos.
- Gestión de secretos (`.env`, claves de Turso/Brevo/Stripe/OpenAI) — que no acaben en logs, en el cliente,
  ni en el repo.
- CORS del servidor Ktor (¿demasiado permisivo?).
- Validación de inputs en rutas del servidor.
- Logging: que no se registren contraseñas, tokens ni datos médicos/de salud en claro.
- Almacenamiento del token de sesión en el cliente (¿en claro en SharedPreferences/localStorage o cifrado?).

**Entregables:** `03-seguridad.md` con cada hallazgo clasificado (crítico/medio/bajo) y su estado, más los
arreglos aplicados directamente cuando sean seguros y de bajo riesgo (se piden confirmación los cambios
con impacto en producción, como rotar claves o tocar el servidor en Render).

**Criterio de salida:** no quedan hallazgos críticos abiertos; los medios/bajos están documentados con un
plan.

---

## Fase 3 — Limpieza de código

- Eliminar o archivar código muerto (candidatos ya detectados: `backend/` parece un prototipo Python
  anterior sin uso real; `frontend/` parece solo contener `node_modules` sin proyecto — se confirma en
  Fase 1 y se decide con el usuario antes de borrar nada).
- Eliminar duplicación entre pantallas/servicios del cliente y entre rutas/servicios del servidor.
- Unificar convenciones (naming, manejo de errores, estructura) según lo definido en `02-convenciones.md`.
- Revisar dependencias no usadas en los `build.gradle.kts`.

**Regla:** ningún cambio de esta fase debe alterar el comportamiento visible de la app. Son refactors,
no features. Cada lote de limpieza se verifica compilando (`./gradlew build` / ejecutando desktop) antes
de seguir.

**Criterio de salida:** build limpio, sin warnings relevantes, sin carpetas/archivos huérfanos sin
justificar en `05-deuda-tecnica.md`.

---

## Fase 4 — Validación de fiabilidad de las funcionalidades existentes

Antes de mejorar nada, confirmar qué tan bien o mal funciona hoy:

- Auditar las fórmulas de cálculo de kcal/macros/TDEE y de objetivos (pérdida/ganancia/mantenimiento) en
  `NutritionService`/`FoodService` contra fórmulas estándar (Mifflin-St Jeor, Harris-Benedict, factores de
  actividad) y documentar las desviaciones.
- Auditar la recolección de datos de GPS (precisión, consumo de batería, persistencia de rutas).
- Auditar la integración con pulsera/Health Connect (cómo se recolectan pasos/kcal, frecuencia, fiabilidad).
- Añadir tests donde no los haya para la lógica crítica (cálculos de nutrición sobre todo), para poder
  refactorizar después sin miedo.

**Entregable:** `06-funcionalidades.md` con el veredicto de cada funcionalidad ("funciona bien",
"funciona pero es impreciso", "roto") y referencia a los tests añadidos.

**Criterio de salida:** hay un veredicto objetivo (no solo percepción) sobre cada funcionalidad.

---

## Fase 5 — Mejora de funcionalidades existentes

Con el veredicto de la Fase 4, mejorar en orden de impacto (probablemente nutrición primero, por ser el
núcleo de "contador de kcal fiable tipo Fitia"):

- Nutrición/kcal: mejorar precisión de cálculo y UX de registro de comidas.
- Objetivos: ajustar algoritmo de pérdida/ganancia/mantenimiento con las fórmulas correctas.
- GPS/rutas: mejoras de precisión/UX si la auditoría lo justifica.
- Wearable/Health Connect: mejoras en la recolecta si la auditoría lo justifica.

**Criterio de salida:** el usuario confía en los números que da la app (kcal y objetivos) tanto como en
una app profesional del sector.

---

## Fase 6 — Nuevas funcionalidades

Solo se empieza aquí cuando las fases 1-5 están cerradas. Incluye cosas mencionadas pero fuera de alcance
de este endurecimiento inicial, como el soporte real de iOS (hoy no funciona) o nuevos módulos.

---

## Reglas de trabajo para todas las fases

- **Nunca** se hacen cambios destructivos (borrar módulos, resetear la base de datos, rotar claves en
  Render/Turso/Brevo/Stripe) sin confirmación explícita, aunque el proyecto sea de uso personal.
- Los secretos (`.env`, `backend/.env`) se leen para entender cómo se usan, pero sus valores nunca se
  pegan en documentación, commits ni mensajes.
- Cada sesión deja rastro en [07-registro-sesiones.md](07-registro-sesiones.md).
- Si durante cualquier fase se detecta algo que afecta a producción real futura (Play Store, pagos,
  GDPR/LOPD con datos de salud), se anota en
  [04-produccion-playstore.md](04-produccion-playstore.md) aunque no se actúe ahora.
