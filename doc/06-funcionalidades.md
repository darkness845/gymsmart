# Estado de las funcionalidades existentes

La sección de nutrición se validó a fondo en Fase 4 (2026-10-26). GPS, wearable, training y
suscripción siguen con la valoración preliminar de Fase 1 — pendientes de su propia pasada de Fase 4.

## Nutrición / kcal / objetivos — validado en Fase 4 (2026-10-26)

**Veredicto: la fórmula de TDEE/macros es correcta y estándar. El modo "wearable" NO tiene el bug que se
sospechaba inicialmente — su diseño es razonable. El problema real de precisión está en otro sitio: cómo
y cuándo se lee el dato de la pulsera, y en un caso de datos de Open Food Facts ambiguos.**

### TDEE y macros (fórmula base)

`ProfileService.computeTargets` (servidor) usa Mifflin-St Jeor correctamente (confirmado leyendo el
código línea a línea), con multiplicadores de actividad estándar (1.2–1.9) y reparto de macros acorde a
guías ISSN 2017 (proteína 2.0 g/kg en déficit / 1.8 g/kg resto, grasa 25% de kcal, carbos por
diferencia, mínimos de seguridad 1500/1200 kcal). **No hay nada que corregir aquí.**

### Modo "wearable" — hipótesis inicial descartada tras investigar más a fondo

La Fase 1 planteó como hipótesis que el modo `activityLevel == "wearable"` estaba mal porque el servidor
usa el BMR puro como `targetKcal`, sin multiplicador de actividad. Al investigar en profundidad (Fase 4)
se confirmó que **esto no es un bug**: es un diseño deliberado tipo "calorías ganadas" (el mismo patrón
que usan Fitbit/Garmin/MyFitnessPal con pulsera conectada):

- El servidor manda `targetKcal = BMR + ajuste de objetivo` (sin multiplicador), etiquetado como "BMR"
  en la UI (`ProfileScreen.kt:612`, `OnboardingScreen.kt:372`).
- El cliente (`NutritionScreen.kt`) ya suma por su cuenta las kcal activas reales del día
  (`healthDataProvider.getTodayActiveCalories()`, que en Android lee `ActiveCaloriesBurnedRecord` de
  Health Connect desde medianoche hasta el momento de la consulta) al objetivo del servidor:
  `realGoal = kcalGoal + activeCalories` (`NutritionScreen.kt:330`). Por definición, Health Connect
  calcula las "calorías activas" excluyendo el metabolismo basal, así que `BMR + activeCalories` **sí
  aproxima el TDEE real del día**, sin necesidad de un multiplicador adicional (que de hecho duplicaría
  la actividad ya contada).
- **El problema real es operativo, no de fórmula**: `getTodayActiveCalories()` se lee una sola vez por
  cada apertura de pantalla (`LaunchedEffect(Unit)`), no de forma continua. Si el usuario abre
  Nutrition/Dashboard a primera hora, `activeCalories` es ~0 y el objetivo del día aparece igual al BMR
  puro (muy restrictivo); si no vuelve a abrir la pantalla, ese número desactualizado se queda fijo todo
  el día aunque haya hecho mucha actividad real. Esto coincide con la sensación de "los números no van
  muy allá": el presupuesto de kcal que ve el usuario suele estar desfasado respecto a su actividad real
  del día.
- **Esto ya estaba identificado como mejora de Fase 5** en la sección de wearable más abajo (sincronizar
  en segundo plano en vez de consulta puntual) — se confirma aquí que es, con alta probabilidad, la causa
  principal de la imprecisión percibida en el modo pulsera, más que la fórmula en sí.
- Nota menor: la fórmula de TDEE está duplicada (servidor `ProfileService.kt` + vista previa de
  onboarding en `OnboardingScreen.kt:345-356`). Es una duplicación necesaria hoy (el onboarding necesita
  una vista previa antes de guardar, cuando todavía no hay respuesta del servidor), pero es un riesgo de
  que ambas copias diverjan con el tiempo. Candidato a mover a `shared/` si ese módulo llega a usarse
  como capa de dominio común (ver [01-arquitectura.md](01-arquitectura.md) §1) — no se toca ahora por ser
  bajo riesgo y fuera del alcance de esta limpieza.

### Registro de comidas — bug de datos encontrado y arreglado

Al revisar cómo se calculan las kcal de cada comida registrada (`NutritionScreen.kt`), se encontró un
problema real de precisión: cuando un producto de Open Food Facts no tenía el campo estándar
`energy-kcal_100g`, el código usaba el campo `energy-kcal` (sin sufijo) **como si fuera por 100g** — pero
ese campo no está garantizado a serlo en los datos de OFF (puede representar la ración, no 100g). Esto
podía guardar kcal incorrectas para productos con datos incompletos, de forma silenciosa, y ese valor
quedaba persistido para siempre en el historial/favoritos del usuario.

**Arreglado (2026-10-26)**: se añadió `Nutriments?.estimatedKcalPer100()` en
`model/NutritionModels.kt`, que prioriza `energy-kcal_100g`; si falta, estima kcal/100g a partir de los
macros por 100g (regla 4/4/9: proteína×4 + carbos×4 + grasa×9), más fiable que asumir que un campo
ambiguo es por 100g; solo si tampoco hay macros, usa `energy-kcal` como último recurso. Aplicado en los 4
sitios de `NutritionScreen.kt`/`NutritionService.kt` que calculaban esto de forma inconsistente (uno de
ellos ni siquiera tenía el fallback ambiguo, simplemente mostraba 0). Verificado con
`:composeApp:compileKotlinJvm` → `BUILD SUCCESSFUL`.

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
