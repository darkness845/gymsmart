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
- 🔴 **No hay foreground service**: el seguimiento se detiene al bloquear el móvil. Hay que correr con la
  pantalla encendida (ver GPS-1 en [08-backlog-mejoras.md](08-backlog-mejoras.md)).
- 🔴 **Un hueco de señal >100 m mata la grabación** para el resto de la sesión, en cascada (GPS-2).
- 🔴 **La ruta en curso solo vive en memoria**: si Android mata el proceso, se pierde entera (GPS-3).
- 🟠 Guardar una ruta hace N+1 peticiones HTTP secuenciales a Turso: entre 45 s y varios minutos para una
  ruta normal, con riesgo de timeout (GPS-4).
- 🟠 Se usan `loc.speed`/`accuracy` sin comprobar `hasSpeed()`/`hasAccuracy()`, lo que descarta puntos
  válidos y acepta posiciones sin precisión como si fueran perfectas (GPS-5).
- 🟠 La duración incluye el tiempo parado, así que cualquier ritmo derivado sale mal (GPS-6).
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
- 🟠 **Pendiente (WEAR-3) — lectura puntual, no sincronización**: cada pantalla sigue leyendo una sola vez
  al entrar. Si abres la app por la mañana, el presupuesto de kcal se queda congelado en el valor de esa
  hora todo el día. Es la causa más probable de la imprecisión restante en modo pulsera, y es un cambio
  de arquitectura (mover a `WorkManager` periódico con caché local), no un fix puntual — pendiente de
  decidir si se hace ahora o se deja para más adelante.

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
- 🔴 **El gate premium no existe en el servidor**: `isPremium()` está implementado pero no se llama desde
  ningún sitio; el único control es de cliente. Cualquier usuario registrado puede consumir créditos de
  OpenAI sin límite (SEC-1).
- 🟠 **La imagen se envía a resolución completa** (~5,3 MB en base64, 30-45 MB de heap por petición en una
  instancia de 512 MB) **sin ninguna ganancia**: gpt-4o reescala internamente a ≤768 px antes de
  tokenizar, así que esa resolución se descarta. Redimensionar a ~1024 px recorta el payload ~95% (IA-1).
- 🟠 **Las cifras no son reproducibles**: sin `temperature` fijada (por defecto 1.0) ni
  `response_format: json_object`. La misma foto da porcentajes distintos en dos análisis. Y el prompt
  fuerza al modelo a no admitir incertidumbre y a no dejar campos vacíos, así que **siempre devuelve un
  número**, aunque la foto no permita estimarlo, sin indicador de confianza (IA-2).
- 🟡 **El resultado no se persiste**: se pierde al salir de la pantalla. No hay histórico de composición
  corporal ni comparación en el tiempo — siendo la única función premium y la más caro por uso, es la
  mejora de producto más evidente.
