# Estado de las funcionalidades existentes

Primera valoración (Fase 1), a partir de lectura de código. La **validación formal** con tests y
comparación rigurosa contra fórmulas de referencia es trabajo de la Fase 4
([00-roadmap.md](00-roadmap.md)) — esto es el punto de partida, no la conclusión final.

## Nutrición / kcal / objetivos

**Veredicto preliminar: la base matemática está bien, falta integrar el gasto real del wearable.**

- `ProfileService.computeTargets` (servidor) usa Mifflin-St Jeor correctamente, con multiplicadores de
  actividad estándar y reparto de macros razonable (ver detalle en
  [01-arquitectura.md](01-arquitectura.md)).
- **Problema identificado**: cuando el nivel de actividad es `"wearable"`, el servidor usa el BMR puro
  como TDEE, sin sumar el gasto calórico real que reporta la pulsera/Health Connect. Esto coincide
  exactamente con la percepción del usuario de que "los números no van muy allá" en el modo pulsera.
  **Hipótesis de causa raíz** a confirmar en Fase 4: falta sumar `activeCaloriesBurnedToday` (o un
  promedio de días anteriores) al BMR antes de calcular el objetivo.
- El registro de comidas en sí (`/nutrition/add`, modelos `Product`/`Nutriments`) depende de datos de
  Open Food Facts — no se confirmó en esta pasada si `NutritionScreen` llama directamente a
  `/food/search|barcode` o si ese flujo está incompleto; **pendiente de confirmar en Fase 4**.

## GPS / rutas

**Veredicto preliminar: la base está bien implementada en Android.**

- `LocationService` (Android) usa `FusedLocationProviderClient` con eventos (no polling), intervalo 1 s,
  filtro de distancia 5 m — es una implementación correcta y eficiente en batería.
- El servidor persiste rutas y puntos correctamente, con comprobación de propiedad (`user_id`) en todas
  las operaciones.
- No funciona en Desktop (esperado, no hay GPS) ni en iOS/Web (no implementado, ver
  [01-arquitectura.md §5](01-arquitectura.md)).
- Pendiente en Fase 4: medir precisión real en campo y consumo de batería en uso prolongado.

## Wearable / Health Connect (pasos y kcal)

**Veredicto preliminar: funciona, pero con un patrón de recolección mejorable — coincide con lo que
planteó el usuario.**

- `HealthConnectProvider` (Android) lee correctamente `StepsRecord`/`ActiveCaloriesBurnedRecord` del
  día actual.
- **Limitación concreta**: la lectura es una consulta puntual (`readRecords`) disparada solo cuando se
  entra a una pantalla (`LaunchedEffect(Unit)`), no una suscripción en segundo plano. Esto significa que
  los datos mostrados pueden estar desactualizados si el usuario no reabre la pantalla, y que no hay una
  fuente única y fresca de este dato para otros cálculos (como el TDEE del modo "wearable" en
  nutrición).
- **Mejora concreta propuesta para Fase 5**: mover la recolección a un mecanismo en segundo plano
  (`WorkManager` periódico, o `registerForUpdates`/callback de Health Connect si está disponible en la
  versión usada) con caché local, de forma que: (a) la UI se actualice sin depender de reabrir pantallas,
  y (b) el cálculo de TDEE en el modo wearable pueda usar un dato fresco en vez de no usarlo en absoluto.

## Entrenamiento (Training)

**Veredicto: no es una funcionalidad real todavía, es un placeholder.**

- `TrainingScreen.kt` muestra un mensaje de error fijo, un botón "Importar .GPX/.FIT (próximamente)"
  deshabilitado, y llama a la raíz del servidor (no existe `TrainingRoute`).
- **Decisión pendiente con el usuario**: ¿se completa esta funcionalidad en la Fase 5/6 con alcance real
  (importar GPX/FIT, histórico de entrenamientos), o se retira del menú hasta entonces para no mostrar
  algo roto al usuario final?

## Suscripción / pagos

**Veredicto: no funcional por diseño actual, confirmado por el usuario.** No requiere mejora funcional
ahora — solo queda documentado el camino para activarlo de forma segura el día que se decida cobrar de
verdad (ver [03-seguridad.md](03-seguridad.md) #6 y [04-produccion-playstore.md](04-produccion-playstore.md)).

## Análisis corporal por IA

**Veredicto: funcional, pero sin control de acceso** (ver hallazgo crítico #1 en
[03-seguridad.md](03-seguridad.md)). La calidad del análisis en sí no se ha evaluado todavía — pendiente
de Fase 4 si se considera relevante.
