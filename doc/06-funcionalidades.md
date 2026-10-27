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

## GPS / rutas — validado en Fase 4 (2026-10-26)

**Veredicto: la base de captura de ubicación está bien construida, pero todo lo que la rodea tiene
fallos graves. Hoy el GPS no es fiable para una ruta real.**

- ✅ Lo que está bien: `LocationService` (Android) usa `FusedLocationProviderClient` por eventos (no
  polling), con filtro de distancia; el servidor persiste rutas y puntos comprobando propiedad por
  `user_id`; osmdroid tiene el `userAgentValue` configurado, así que los tiles de OSM no se bloquean.
- ✅ **Arreglado (GPS-2)**: un hueco de señal >100 m ya no mata la grabación en cascada — se actualiza el
  punto de referencia aunque no se sume esa distancia al total.
- ✅ **Arreglado (GPS-3)**: la ruta en curso se guarda periódicamente en almacenamiento local
  (`RouteRecoveryStore`); si Android mata el proceso, se ofrece recuperarla al reabrir la pantalla. Sin
  verificar con build real en Android (sin SDK en este entorno).
- ✅ **Arreglado (GPS-4)**: guardar una ruta ya no hace una petición HTTP por punto — se manda en lotes
  de 200 statements por llamada a Turso.
- ✅ **Arreglado (GPS-1)**: el seguimiento ya no se detiene al bloquear el móvil. `GpsTrackingService`
  nuevo mantiene un foreground service con notificación mientras se grava. Es el cambio más invasivo de
  toda la Fase 5 (`Service` de Android nuevo) y el que más necesita probarse en un dispositivo real
  antes de confiar en él — sin verificar con build real en este entorno (sin Android SDK). La
  notificación es mínima a propósito (sin botón de "Parar", sin stats en vivo); mejorarla queda anotada
  como mejora futura (GPS-1b en [08-backlog-mejoras.md](08-backlog-mejoras.md)), confirmado por el
  usuario.
- 🟠 **Pendiente (GPS-5)**: se usan `loc.speed`/`accuracy` sin comprobar `hasSpeed()`/`hasAccuracy()`, lo
  que descarta puntos válidos y acepta posiciones sin precisión como si fueran perfectas.
- 🟠 **Pendiente (GPS-6)**: la duración incluye el tiempo parado, así que cualquier ritmo derivado sale
  mal.
- No funciona en Desktop (esperado, no hay GPS) ni en iOS/Web (no implementado, ver
  [01-arquitectura.md §5](01-arquitectura.md)).
- Pendiente de medir en campo (requiere dispositivo real): precisión efectiva y consumo de batería.

## Wearable / Health Connect (pasos y kcal) — validado en Fase 4 (2026-10-26)

**Veredicto: funciona, pero el recuento tiene dos fallos opuestos y la recolección está mal planteada.
Respuesta directa a "¿se puede hacer mejor la recolecta tal como la hice?": sí, y el arreglo principal
cabe en un solo archivo.**

- ✅ **Arreglado (WEAR-1/WEAR-2)**: `HealthConnectProvider` sumaba registros crudos (`readRecords`) sin
  deduplicar entre fuentes (podía contar de más: app de la pulsera + Google Fit + sensor del móvil
  escribiendo los mismos pasos) y sin paginar (podía contar de menos: se truncaba en los primeros 1000
  registros del día). Ahora usa `aggregate()` con `StepsRecord.COUNT_TOTAL` y
  `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`, que Health Connect calcula ya deduplicado y sin
  necesidad de paginar. **⚠️ Sin verificar con build real** (sin Android SDK en este entorno) — probar en
  un dispositivo/emulador Android antes de confiar al 100%.
- ✅ **Arreglado (WEAR-3)**: cada pantalla leía una sola vez al entrar, así que si se abría la app por la
  mañana el presupuesto de kcal se quedaba congelado en el valor de esa hora todo el día. Ahora
  `NutritionScreen` y `DashboardScreen` refrescan los datos de la pulsera cada 5 minutos mientras la
  pantalla está visible, en vez de leer una sola vez. Se eligió este enfoque (más simple que el
  `WorkManager` en segundo plano que se había planteado inicialmente) porque resuelve el síntoma real
  —el número obsoleto mientras se mira la pantalla— sin tocar código Android ni añadir algo imposible de
  verificar en este entorno; ver el detalle de la decisión en
  [08-backlog-mejoras.md](08-backlog-mejoras.md).

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

## Entrenamiento — nota de Fase 4

`TrainingScreen` está registrada en el NavGraph pero **nada navega a ella**: no aparece en el menú del
Dashboard ni en ningún otro sitio. Es decir, la decisión de "retirarla del menú" ya es innecesaria, el
usuario nunca puede llegar a ella. Queda como código inerte hasta que se decida su alcance real.

## Análisis corporal por IA — validado en Fase 4 (2026-10-26)

**Veredicto: funcionaba, pero mal planteado en coste y fiabilidad; y durante esta fase estuvo roto por
una regresión (ya arreglada).**

- ✅ Arreglado en esta fase: la llamada usaba un `HttpClient` sin cookies, así que al exigir sesión en el
  endpoint devolvía 401 y fallaba en silencio (R1 en [08-backlog-mejoras.md](08-backlog-mejoras.md)).
- ✅ **Arreglado (SEC-1): el gate premium ahora existe en el servidor.** `bodyAnalysisRoute` comprueba
  `isPremium()` y responde 403 si no lo es — antes cualquier usuario registrado podía consumir créditos
  de OpenAI sin límite, con el único control (de cliente) saltable con una petición directa a la API.
- ✅ **Arreglado (IA-1): la imagen ya no se envía a OpenAI a resolución completa.** Se redimensiona a
  máximo 1024px de lado largo en el servidor antes de mandarla (gpt-4o reescala internamente a ≤768px,
  así que la resolución extra se descartaba sin ninguna ganancia). Nota: esto reduce el payload hacia
  OpenAI, no el payload cliente→servidor — el cliente sigue mandando la imagen completa al endpoint.
- ✅ **Arreglado (IA-2): las cifras son ahora mucho más reproducibles.** `temperature = 0.2` y
  `response_format: json_object` fijos. El prompt ya no obliga a fingir certeza: puede avisar en el
  campo `summary` cuando la foto no permite una estimación fiable, sin dejar de dar una estimación en
  los campos obligatorios.
- ✅ **Arreglado (IA-3): el parseo ya no revienta por un detalle de formato.** Se decodifica con un
  `Json` tolerante (`isLenient = true`) en vez del estricto por defecto — antes, si el modelo devolvía
  un número donde se esperaba texto, se perdía el análisis entero.
- 🟡 **El resultado no se persiste**: se pierde al salir de la pantalla. No hay histórico de composición
  corporal ni comparación en el tiempo — siendo la única función premium y la más caro por uso, es la
  mejora de producto más evidente.
