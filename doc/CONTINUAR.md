# Continuar aquí — estado para la próxima sesión

> **Si eres una sesión nueva de Claude**: lee esto primero, es la foto rápida del estado actual.
> No es un historial — para el detalle de qué se hizo y por qué, mira
> [07-registro-sesiones.md](07-registro-sesiones.md) (bitácora completa) y
> [08-backlog-mejoras.md](08-backlog-mejoras.md) (cada hallazgo con su estado).
> Este documento se actualiza cada vez que se termina algo, no solo al final de la sesión.

**Última actualización**: 2026-11-23 (**los 5 bloques de la auditoría de kcal están completos**: A y B
commiteados el 2026-11-22; eliminación de cintura/recomp y Bloque C commiteados el 2026-11-23
(`3d19e2f`, `d4a1b76`); Bloques D y E hechos, **sin commitear todavía**). Fuera de alcance de los 5
bloques: AUD-11 (snapshot histórico).

---

## ✅ 2026-11-23 — Bloques D y E de la auditoría de kcal: últimos pendientes

**Bloque D — honestidad de la interfaz:**

1. **Banda de incertidumbre** en vez de número único: `NutritionMath.targetBand` (±150 kcal con
   fórmula, ±75 con gasto medido, redondeada a 25), mostrada en el Onboarding y en el resumen de
   `ProfileScreen` junto al objetivo exacto.
2. **Etiqueta "BMR" corregida**: antes mostraba `tdee` en vez de `bmr` en modo pulsera (2166 para un
   BMR de 1805). Ahora cada etiqueta muestra su propio número, en las dos pantallas.
3. **Aviso de pulsera sin datos** en `NutritionScreen`: `wearableEnergy` se calculaba y se tiraba; ahora
   si hay pulsera y no hay datos de hoy, se avisa de que el objetivo cae al respaldo sedentario.
4. **Señal de macros degradados**: `Macros.degraded` nuevo (true cuando el reparto ideal no cupo en el
   objetivo), propagado a `TargetResult.macrosDegraded` y a un aviso en pantalla.
5. **Guardia por IMC y condición de parada**: `TargetResult.lowBmiGuard` — IMC < 18,5 y objetivo con más
   déficit → el suelo pasa a ser el mantenimiento entero, con su propio aviso. No bloquea pedir ganar
   peso estando en bajo peso. No toca el ritmo elegido en el formulario, solo deja de traducirlo en un
   déficit real mientras el IMC siga ahí.

**Bloque E — los 🟡 (AUD-18, 19, 23, 24):**

6. **AUD-18**: la migración SQL del ritmo, reescrita para reproducir exactamente `NutritionMath.clampRate`
   (`-MIN(1.0, ABS(goal_rate))` en vez de `MAX(-1.0, MIN(-0.05, goal_rate))`, que subía magnitudes
   válidas por debajo de la banda).
7. **AUD-19**: el formato de `resolvedAge` pasa de `"dd/MM/yyyy"` (nunca coincidía con el formato real
   del cliente, 8 dígitos sin separadores) a `"ddMMyyyy"`. Como esto reactiva el riesgo de edad absurda
   que estaba dormido por el mismo bug, la edad resuelta se valida contra `14..100` antes de usarla.
8. **AUD-23**: `formatOneDecimal`/`formatTwoDecimals` ya no pierden el signo para `|valor| < 1`.
9. **AUD-24**: `/profile/adaptive` pasa de `GET` a `POST` (cliente y servidor); `runAdaptiveAdjustment`
   serializado con un `Mutex` por usuario; `upsertProfile` combina el `INSERT OR REPLACE` y la
   restauración del autoajuste en un único `executeBatch` con `BEGIN IMMEDIATE`/`COMMIT`. **Verificado
   contra la Turso real** (llamada de prueba de solo lectura: `BEGIN`/`SELECT 1`/`COMMIT` → los tres
   `"type":"ok"`) antes de confiar en que el pipeline soporta transacciones explícitas.

**49 tests en verde** (`:shared:jvmTest`), `:server:compileKotlin` y `:composeApp:compileKotlinJvm`
también. **Nada de esto commiteado todavía.**

---

## ✅ 2026-11-23 — Bloque C de la auditoría de kcal: calidad del dato del autoajuste

Los cinco puntos del plan, con el detalle AUD-9/10/12/13 marcado resuelto en
[11-auditoria-kcal.md](11-auditoria-kcal.md):

1. **`ADAPTIVE_MIN_SPAN_DAYS` 10→14 y `ADAPTIVE_MIN_WEIGH_INS` 4→8** (`NutritionMath`), lo que exige
   §5.3 y el punto donde el Montecarlo del informe deja de ganar el ruido al mecanismo.
2. **Umbral de "día válido"**: `ADAPTIVE_MIN_DAY_KCAL = 1000`, filtrado en
   `ProfileService.runAdaptiveAdjustment` antes de contar `loggedDays` — un día con solo el desayuno
   ya no cuenta como registrado.
3. **Cobertura mínima de ventana**: `ADAPTIVE_MIN_COVERAGE = 0.8`, nuevo campo `windowDays` en
   `AdaptiveInput` (por defecto `= loggedDays`, para no romper nada que no lo use a propósito) y
   comprobación en `reconcile`.
4. **Mínimo de días en la media de la pulsera**: `WEARABLE_MIN_DAYS_WITH_DATA` (mitad de
   `WEARABLE_AVERAGE_DAYS`, 7 de 14), comprobado en `HealthConnectProvider.getEnergySummary`. De
   regalo, `HealthDataProvider.getEnergySummary` ya referencia la constante en vez de duplicar el 14.
5. **`weight_history` se escribe siempre** al guardar el perfil, no solo si el peso cambia — se quitó
   la condición `previous.weightKg != weightKg`.

**El escenario modal del informe original (20 días bien registrados + 8 con solo desayuno → déficit
real del 31-44% informado como 23%) ya no pasa el filtro**: los 8 días de 300 kcal no cuentan como
registrados, así que `loggedDays` cae por debajo del mínimo antes de llegar a la cobertura.

Tests nuevos en `NutritionMathTest`: cobertura insuficiente rechazada, cobertura suficiente aceptada, el
valor por defecto de `windowDays` no rompe nada anterior, y el cálculo de `WEARABLE_MIN_DAYS_WITH_DATA`.
**43 tests en verde**, `:server:compileKotlin` y `:composeApp:compileKotlinJvm` también.

**⚠️ Punto 4 sin verificar con build real**: `HealthConnectProvider.kt` es `androidMain` y este entorno
no tiene Android SDK. Probarlo en dispositivo/emulador antes de confiar en él al 100%.

**Nada de esto commiteado todavía.**

---

## ✅ 2026-11-23 — Eliminado el seguimiento de cintura (`waistCm`)

Decisión del usuario: medirse con cinta era fricción que no quería ("es pereza"). Se quitó de punta a
punta:

- **Servidor**: `ProfileRequest.waistCm`, el parámetro de `upsertProfile`, el `INSERT`/`UPDATE` de
  `weight_history` que lo escribía, el `SELECT` de `getWeightHistory` que lo leía, y la validación de
  rango en `ProfileRoute`.
- **Cliente**: `ProfileRequest.waistCm`, `WeightEntry.waistCm`, `validateWaistCm`, el segundo campo del
  diálogo de añadir peso en `WeightScreen`, y la tarjeta `CompositionCard` entera (la que interpretaba
  peso + cintura para detectar "estás ganando músculo" / "recomposición" / etc. — ver §9.3 del
  fundamento científico).
- **BBDD**: la columna `waist_cm` de `weight_history` **no se ha borrado** — eso es destructivo y no
  hacía falta confirmarlo para esto. Simplemente ya no la escribe ni la lee ningún código; queda inerte.

Verificado con `:shared:jvmTest` (40 tests), `:server:compileKotlin`, `:composeApp:compileKotlinJvm`.
**Commiteado el 2026-11-23** junto con la eliminación de "recomposición" de más abajo (commit `3d19e2f`).

## ✅ 2026-11-23 — Eliminada la opción de objetivo "Recomposición" (`GOAL_RECOMP`)

Decisión directa del usuario, sin pasar por las dos vías que proponía AUD-7 ("implementarla de verdad" o
"renombrarla como déficit suave"): **se quita del menú**. Quedan solo `lose_fat` / `gain_muscle` /
`maintain`.

Tocado: `NutritionMath` (constante y las seis funciones que la mencionaban), `RateSlider`, los tres
selectores de objetivo (`OnboardingScreen`, `ProfileScreen`), y la migración SQL del servidor (un perfil
con `goal = 'recomp'` en BBDD se convierte a `maintain` con ritmo 0 en el próximo arranque). Detalle
completo, con el AUD-7 marcado resuelto, en [11-auditoria-kcal.md](11-auditoria-kcal.md).

**Lo que NO se tocó, y es importante no confundir**: `WeightScreen.kt:575` sigue diciendo
"Recomposición: pierdes grasa sin perder peso" — eso es la interpretación de tendencias peso/cintura de
§9.3, un fenómeno fisiológico que no depende del selector de objetivo. Se queda.

Verificado con `:shared:jvmTest` (40 tests), `:server:compileKotlin`, `:composeApp:compileKotlinJvm`.
**Commiteado el 2026-11-23** (commit `3d19e2f`, junto con la eliminación de cintura de arriba).

---

## ✅ Los 5 bloques de la auditoría de kcal están completos

Quedan solo dos cosas, nunca incluidas en el plan de bloques A-E porque son cambios de arquitectura
mayores, no arreglos puntuales (detalle en [11-auditoria-kcal.md](11-auditoria-kcal.md)):

- **AUD-11**: no hay snapshot/`calcVersion` del objetivo por día, así que el historial de adherencia se
  reescribe retroactivamente al cambiar peso/objetivo/constantes.
- El ángulo del **ciclo menstrual** mencionado en AUD-9: no hay bandera ni ventana forzada para la
  retención premenstrual en el autoajuste.

---

## 🔴 LO PRIMERO (2026-11-22): auditoría del cálculo de kcal — diagnóstico hecho, arreglo SIN EMPEZAR

El usuario pidió una **auditoría objetiva e independiente** de la funcionalidad de kcal por objetivo
(perder/mantener/ganar/recomp), con la instrucción explícita de no dar por buena la implementación. Se
hizo con cuatro agentes independientes: investigación externa contra fuentes primarias, auditoría de
código con `:shared:jvmTest` ejecutado y réplica numérica, red team adversarial, y un evaluador que
verificó los hallazgos graves por su cuenta y recalibró gravedades.

**Resultado completo: [11-auditoria-kcal.md](11-auditoria-kcal.md)** (AUD-1 a AUD-24). Resumen en
[08-backlog-mejoras.md](08-backlog-mejoras.md). **No se tocó ni una línea de código** — el usuario pidió
primero solo diagnóstico.

**Veredicto**: el modelo energético es **correcto** y mejor que el de la mayoría de apps comerciales
(ritmo en % de peso, déficit derivado de una velocidad y no de un plazo, `BMR + ActiveCalories` sin doble
conteo). Las constantes del código coinciden **todas** con `10-fundamento-cientifico.md`. **El fallo está
en dónde se imponen los invariantes**: AUD-1, AUD-2, AUD-5 y AUD-6 son **un solo error de razonamiento** —
§7.3 cambió un *suelo de ingesta* por un *tope de déficit* sin re-derivar las consecuencias, dejando el
sistema sin ningún mínimo absoluto. Ver la corrección añadida a §7.3 y a §14 de ese documento.

### ✅ AUD-16 resuelto (2026-11-22): la BBDD está bien, se arranca por el Bloque A

Consultado el esquema real con `PRAGMA table_info(user_profiles)` contra Turso (con permiso del usuario,
solo lectura): **`has_wearable` existe**. El guardado de perfil y el registro de peso **no están rotos**.

Detalle que explica por qué: la columna aparece **después** de `updated_at`, cuando el `CREATE TABLE` la
declara antes — firma inequívoca de un `ALTER TABLE ADD COLUMN`, que no existe en el repo. En `dc1b216`
se usaba en el `INSERT` sin estar en el `CREATE TABLE` (se añadió ahí en `eff1e2a`), así que **se añadió
a mano en Turso** en su día. Gravedad rebajada a 🟡. **Decisión**: quitarla del `INSERT` y quitar
`hasWearable` de `ProfileRequest`, porque ningún `SELECT` la lee — eliminar la causa, no parchear el
síntoma.

**Nota de entorno útil**: la app del móvil apunta a `http://100.91.80.89:8080` (Tailscale, servidor
local), y hay un **único `.env` con un solo `TURSO_DB_URL`**, así que servidor local y Render comparten
base de datos. Probar en local equivale a probar contra producción para cualquier cosa de esquema.

### Decisiones del usuario ya tomadas (2026-11-22) — no re-preguntar

1. **El suelo calórico será por disponibilidad energética** (§7.2): estimar masa magra por población y
   exigir **≥30 kcal/kg MM/día**, aplicado **al final del cálculo, sobre el número que se muestra**, no al
   déficit. Se descartaron: suelo duro de 1000 kcal, "nunca por debajo del BMR", y no poner suelo.
2. **`carryOverKcal` se acota dentro del recorte y se condiciona a `activityLevel == "wearable"`**, sin
   cambiar el modelo. Se descartaron: convertirlo en presupuesto semanal real, y eliminarlo.
3. **Documentar antes de implementar** (lo que se hizo en esta sesión).

### ✅ Bloque A — COMPLETO (2026-11-22), SIN COMMITEAR

Verificado tras cada paso con `:shared:jvmTest` (**40 tests en verde**), `:server:compileKotlin` y
`:composeApp:compileKotlinJvm` (ambos `BUILD SUCCESSFUL`). Nada de `androidMain` tocado, así que no hay
nada pendiente de build real en este bloque.

| # | Punto | Estado |
|---|---|---|
| 1 | Suelo por disponibilidad energética | ✅ `energyAvailabilityFloor` + `bodyFatPctEstimate` (Deurenberg) + `leanMassEstimate` en `NutritionMath` |
| 2 | `carryOverKcal` dentro del recorte y solo en modo pulsera | ✅ y además solo con datos reales de pulsera (`avgActive > 0`) |
| 3 | Validar `avgActiveKcal`/`carryOverKcal` | ✅ `asFiniteInRange` en `ProfileRoute`, 400 en vez de 500; tope `MAX_ACTIVE_KCAL = 8000` |
| 4 | Pasar el TDEE a `RateSlider` | ✅ `effectiveMaxRateFor` en `NutritionMath`; el deslizador recibe `tdee` y `energyFloor`, su extremo se redondea **hacia abajo** a la rejilla, y si no queda margen muestra el motivo en vez de un control inerte. De paso cierra AUD-22 (el valor se encaja en la rejilla) |
| 5 | Tope de superávit | ✅ `MAX_SURPLUS_FRACTION = 0.20` (§10.3), `rateLimited` ya funciona en `gain_muscle` |
| 6 | Corregir los textos del BMR | ✅ `NutritionScreen` (aviso según el límite que de verdad mordió) y `OnboardingScreen` (metodología + fuera "tiempo real") |
| 7 | `carryOverKcal` y `measuredTdee` en el barrido | ✅ ~21.000 combinaciones; **encontró 3 fallos reales en el primer intento** |

**Los 3 fallos que destapó el barrido ampliado** (ninguno los había reportado la auditoría con este
detalle, y los tres estaban vivos):

1. **`measuredTdee` podía quedar por debajo del BMR** y el invariante de §7.4 (`gasto > BMR`) dejaba de
   cumplirse en esa rama: el único filtro de `reconcile` es `raw ∈ [800, 6000]`, que no sabe nada del
   cuerpo. Arreglado acotándolo al mismo respaldo sedentario (1,2 × BMR) que ya usa la rama de fórmula.
2. **Un arrastre positivo convertía `lose_fat` en superávit** (objetivo 1788 con gasto 1563). Arreglado
   con una guarda direccional: el arrastre puede reducir el déficit hasta el mantenimiento, no pasarse.
3. **Simétrico**: un arrastre negativo mandaba a `gain_muscle` a comer por debajo del mantenimiento. La
   misma guarda lo cubre. **Las dos son la queja original del usuario, una en cada dirección.**

**Error propio corregido durante la implementación, anotado por si reaparece la tentación**: el primer
suelo restaba `avgActiveKcal` como "gasto de ejercicio", siguiendo la fórmula de §7.2 al pie de la letra.
Está mal: `ActiveCaloriesBurnedRecord` es **toda** la energía no basal (andar, moverse por casa), no el
entrenamiento, así que el término salía inflado y el suelo acababa **por encima del mantenimiento** —
otra vez "perder grasa" con superávit. El suelo ya no resta nada: es `umbral × masa magra`, una cota
inferior estable. Queda documentado en el KDoc de `energyAvailabilityFloor`.

**Campos nuevos en `TargetResult`** (propagados al JSON de `ProfileRoute` y a `NutritionTargets` del
cliente): `energyFloorHit`, `energyFloor`, `appliedCarryOver`, `noSafeDeficit`. Y `RATE_LIMIT_DEADBAND_KCAL
= 10.0`, que cierra AUD-20: un recorte de 3 kcal/día ya no dispara un aviso alarmante.

**Estado nuevo que la interfaz ya contempla**: `noSafeDeficit` — cuando el suelo alcanza el
mantenimiento, no hay déficit seguro y el objetivo se queda ahí, diciéndolo. Es el caso en que lo que
toca es subir el gasto, no comer menos.

**Hallazgos cerrados en el Bloque A**: AUD-1 (los tres defectos), AUD-2, AUD-5, AUD-6, AUD-15, AUD-20,
AUD-22, y la mitad de AUD-21 (el "tiempo real"; la caja etiquetada "BMR" que muestra el TDEE sigue
abierta, es Bloque D).

### ✅ Bloque B — COMPLETO (2026-11-22), SIN COMMITEAR

**La idea que lo resuelve: el autoajuste se guarda como *factor* sobre la fórmula, no como kcal
absolutas.** `adaptive_factor = medido / fórmula`, y el objetivo se calcula como `fórmula_de_hoy × factor`.

Eso hace lo que pedía AUD-3 y además **vuelve innecesaria la invalidación**: un factor se reescala solo.
Si el usuario adelgaza 10 kg, su BMR baja y el objetivo baja con él; si pasa de sedentario a muy activo,
sube; si la pulsera registra más actividad, también. Lo que el factor captura es solo lo que la fórmula no
puede saber: que para este cuerpo la predicción se queda corta o larga en un tanto por ciento. No hay que
borrar nada al cambiar de actividad o de objetivo.

| Punto | Estado |
|---|---|
| Componer en vez de sustituir | ✅ columna `adaptive_factor` + `ALTER TABLE`; `computeTargets` compone `formulaTdeeFor(perfil) × factor` |
| Invalidar/re-anclar al cambiar actividad u objetivo | ✅ **innecesario por diseño** — el factor se reescala solo. Documentado en `UserProfile.adaptiveFactor` |
| Ancla del `previousTdee` | ✅ `runAdaptiveAdjustment` recibe `avgActiveKcal` y ancla con la fórmula corregida por el factor vigente, en vez de caer al respaldo `BMR × 1,2`. El cliente se la pasa (`ProfileScreen` ya la tenía calculada) |
| Signo de `AdaptiveCard` | ✅ `deltaKcal` es ahora el cambio real **del objetivo** (se calcula el objetivo antes y después del ajuste), no la diferencia entre gastos mezclados. Nuevo campo `newTargetKcal` para decir a cuánto queda |

**Salvaguarda nueva**: `NutritionMath.ADAPTIVE_FACTOR_RANGE = 0.70..1.30`. Mifflin tiene ±10% de error y
el multiplicador puede fallar un escalón, así que una corrección real difícilmente pasa del ±30%; por
encima lo que hay es registro incompleto de comidas. Acota el mecanismo que AUD-10 demostró que podía
endurecer el déficit progresivamente.

**Compatibilidad con perfiles antiguos**: los que ya tenían `adaptive_tdee` en kcal derivan su factor en
la primera lectura (`adaptiveFactorFor`), dividiendo por la fórmula de hoy. No es el factor exacto que se
midió entonces, pero es la mejor conversión sin guardar historial, y a partir de la siguiente
reconciliación se mide limpio. `adaptive_tdee` se conserva **solo para mostrarlo**.

**Nota**: el `max(measured, 1,2 × BMR)` del Bloque A sigue en pie como red de seguridad en el punto de
consumo. Ahora es redundante con el rango del factor en el camino normal, pero cubre el caso de un factor
escrito a mano en la BBDD.

**Lo siguiente: Bloques C, D y E.** Se pueden hacer con Sonnet (C y E nivel normal, D nivel alto) — están
especificados en `11-auditoria-kcal.md`. **El Bloque B era el que no convenía delegar** y ya está hecho.

### Cambio extra de robustez para el uso diario (2026-11-22)

`asFiniteInRange` en `ProfileRoute` ahora **recorta** los valores fuera de rango en vez de devolver 400.
Razón: `avgActiveKcal`/`carryOverKcal` son telemetría de la pulsera, no una intención del usuario, así que
si Health Connect devuelve una burrada es mucho mejor degradar el objetivo que dejar la pantalla de
nutrición sin cargar. Lo no numérico sí se sigue rechazando, porque eso es un cliente roto.

### Plan de arreglo acordado (detalle en 11-auditoria-kcal.md)

- **Bloque A — deshacer el error de §7.3.** Lo único que urge, porque los cuatro hallazgos grandes son el
  mismo error: suelo por disponibilidad energética · `carryOverKcal` dentro del recorte y gateado por
  modo pulsera · validar `avgActiveKcal`/`carryOverKcal` (rango e `isFinite`) · **pasar el TDEE a
  `RateSlider`** y derivar su extremo como `min(maxRateFor(goal), rateForDailyDelta(0.25 × TDEE, peso))` ·
  tope de superávit simétrico · corregir los dos textos del BMR · **añadir `carryOverKcal` y
  `measuredTdee` al barrido de tests** (esto es lo que impide que vuelva a pasar). Todo en `shared/` y
  `server/`, así que compila y se testea sin Android SDK.
- **Bloque B — TDEE adaptativo**: componer en vez de sustituir · invalidar/re-anclar al cambiar
  `activityLevel` o `goal` · pasar `avgActiveKcal` al `previousTdee` del `reconcile` · arreglar el signo
  de `AdaptiveCard`.
- **✅ Bloque C — calidad del dato (hecho, 2026-11-23)**: umbral de "día válido" · cobertura mínima de
  ventana · `ADAPTIVE_MIN_SPAN_DAYS`/`WEIGH_INS` subidos a lo que exige §5.3 · mínimo de días en la
  media de la pulsera · escribir en `weight_history` siempre. Detalle arriba.
- **✅ Bloque D — honestidad de la interfaz (hecho, 2026-11-23)**: bandas en vez de número único ·
  etiqueta "BMR" corregida · aviso cuando la pulsera no tiene datos · señal cuando los macros degeneran
  · guardia por IMC y condición de parada. Detalle arriba.
- **✅ Bloque E — los 🟡 (hecho, 2026-11-23)**: AUD-18, 19, 23, 24. Detalle arriba.

**Dos avisos de encuadre para quien implemente esto**: (1) **no** añadir peso objetivo con fecha — no
tenerlo es precisamente lo correcto, y la auditoría lo declara un retroceso conceptual; lo que falta es
una *condición de parada* y una *guardia por IMC* (AUD-8). (2) Varios hallazgos de los agentes se
declararon **FALSOS o exagerados** (el titular de "576 kcal", el "doble conteo del 7 %", el `NaN` como
crítico, "15 kcal/año" de BMR por edad): está todo en la sección correspondiente de
`11-auditoria-kcal.md`, leerla antes de arreglar nada para no perseguir fantasmas.

---

**Nota del usuario sobre pruebas (2026-10-27)**: el usuario no va a probar nada en dispositivo real
hasta que haga falta con urgencia — lo probará todo de golpe cuando se terminen las mejoras de la Fase 5.
Esto es intencional, no un olvido: no insistir en que pruebe cada bloque, solo mantener la lista de
"Pendiente de verificación por el usuario" (más abajo) actualizada y completa para cuando llegue ese
momento.

**Ese momento ya llegó (2026-10-27)**: se creó [09-plan-de-pruebas.md](09-plan-de-pruebas.md), el
checklist completo para esta ronda de pruebas (Desktop + Android, en local vía Tailscale, sin Render
todavía). El usuario está probando ahora mismo — si vuelve con resultados, revisar primero ese documento
para ver qué ítem corresponde a lo que reporte.

**Progreso de la ronda de pruebas en Desktop (2026-10-29)**:
- Encontrado y resuelto, fuera del código: la recuperación de contraseña no enviaba el correo porque la
  API key de Brevo (pestaña "Claves API y MCP", no "SMTP") estaba desactivada en el dashboard de Brevo —
  nada que ver con el código, el usuario la reactivó y ya funciona. De paso, se mejoró `EmailService.kt`
  para loggear a nivel `WARN` (antes `debug`, invisible) cualquier rechazo de Brevo — cambio sin commitear
  todavía, pendiente de que el usuario confirme que lo quiere conservar.
- Sección 1 "Arranque y sesión" del plan de pruebas: los 3 ítems ✅. El tercero (sesión caducada → redirige
  a Login) se intentó verificar en vivo reiniciando el servidor, pero **reiniciar el servidor no caduca
  nada** porque las sesiones Ktor son "by value" (el contenido va cifrado en la propia cookie, no en
  memoria del servidor) — fallo de diseño de la prueba, no de la app. Se intentó de nuevo bajando
  `cookie.maxAgeInSeconds` a 15s temporalmente (revertido ya a 7 días tras la prueba): tampoco caducó,
  porque se confirmó en vivo que **ninguna de las 5 implementaciones de `CookiesStorage` por plataforma
  respeta expiración/Max-Age** (guardan solo `nombre=valor`) — esto ya estaba documentado como la "segunda
  mitad pendiente" de RED-3 en `08-backlog-mejoras.md`, no es un hallazgo nuevo, solo queda confirmado en
  vivo que el gap es real en Desktop y Android.
- Secciones 2 a 5 del plan de pruebas: cubiertas (ver detalle en `07-registro-sesiones.md`).

**🔴 Cambio de prioridad del usuario (2026-11-01) — leer antes de seguir**

El usuario **ha aparcado GPS deliberadamente** ("no quiero probar GPS ahora, lo aparto de momento") y ha
redirigido el trabajo a **la precisión del cálculo nutricional**, que es ahora la prioridad declarada.
No retomar la sección 6 (GPS) del plan de pruebas salvo que él lo pida.

**Motivo real**: el usuario puso objetivo de *ganar* peso, siguió las kcal que marcaba la app, y
**perdió peso**. Es un fallo de producto, no una impresión suya — está diagnosticado y explicado abajo.

**Investigación de la pulsera: cerrada, no es arreglable desde el código.** Se midió en dispositivo
(Xiaomi Smart Band 10 + Mi Fitness + Redmi Note 11 Pro 5G): pulsera 56 kcal · Mi Fitness 54 · Health
Connect 37 · GymSmart 37. La banda sincroniza bien con Mi Fitness; **lo que falla es Mi Fitness → Health
Connect**, por lotes y con retraso. Comprobado y descartado: batería/autoinicio de MIUI (ya estaban
bien), permisos de Health Connect (correctos), y no existe ningún ajuste de frecuencia de sincronización
en Mi Fitness. La propia FAQ de Xiaomi admite el desfase. **No insistir en esta vía.**

De paso, esto **resuelve WEAR-5** del anexo A de `revision-tecnica-claude.md`, que estaba "pendiente de
medir": **Mi Fitness sí escribe calorías activas** (solo activas y cadencia, no totales). El diseño
semántico de la app es correcto y **no hay que tocar `HealthConnectProvider` para leer totales**.

**Nuevo documento: [10-fundamento-cientifico.md](10-fundamento-cientifico.md)** — la fuente de verdad de
todos los números de nutrición, con fuente y nivel de confianza (✅ sólido / 🟡 razonable / ⚠️ convención)
para cada constante. Creado antes de tocar código, a petición expresa del usuario ("quiero que te bases
en la ciencia"). También es la base del FAQ científico previsto en funcionalidades futuras.

**Fallos de cálculo encontrados** (detalle y números en `10-fundamento-cientifico.md`):

| # | Fallo | Gravedad |
|---|---|---|
| 1 | Modo pulsera: se come contra un presupuesto que solo está completo a medianoche, y la lectura en vivo va siempre por debajo → **déficit encubierto** | 🔴 Causa del problema del usuario |
| 2 | Las etiquetas de kg/semana en `ProfileScreen` son falsas (−500 kcal/día prometía 1 kg/sem, da 0,45) | 🔴 |
| 3 | El suelo calórico recorta el ritmo **en silencio** | 🟠 |
| 4 | `OnboardingScreen` y `ProfileScreen` ofrecen ritmos distintos (−300/−400/−500 vs −250/−400/−500) | 🟠 |
| 5 | La fórmula está duplicada en 3 sitios (servidor + 2 pantallas) | 🟠 |
| 6 | Calorías activas de hoy se suman a días pasados (= BUG-N7 de la revisión) | 🟡 |
| 7 | Los macros no suman las kcal objetivo en casos extremos (`carbsG.coerceAtLeast(0)`) | 🟡 |
| 8 | Los mínimos de 1.500/1.200 kcal **no tienen respaldo científico** | 🟠 |
| 9 | El ritmo en kg fijos es inválido: "1 kg/sem" es 1,4%/sem a 70 kg (inseguro) y 0,83% a 120 kg | 🔴 |

**✅ LOS 4 BLOQUES ESTÁN IMPLEMENTADOS (2026-11-01).** Ver "Lo último que se hizo". Lo que sigue es el
plan tal como se acordó, para referencia:

1. **Base del cálculo**: ritmo en **% del peso corporal** (0,5-1% perder / 0,25-0,5% ganar), kcal
   calculadas y no etiquetadas a ojo, presets unificados entre Onboarding y Perfil, aviso cuando el suelo
   recorte el ritmo, suelo basado en BMR en vez de 1.500 plano.
2. **Modo pulsera**: presupuesto con **media móvil de 14 días** de calorías activas
   (`aggregateGroupByPeriod`, que Health Connect ya soporta — el histórico está ahí, la limitación era
   nuestra), estable desde la mañana, **arrastrando al día siguiente** la diferencia entre actividad
   prevista y real. Arreglar el punto 6.
3. **Autoajuste**: reconciliar el gasto real medido (tendencia de peso + ingesta) con el objetivo cada
   1-2 semanas. Es lo único que puede corregir un multiplicador de actividad mal elegido, que es el mayor
   error del camino **sin** pulsera.
4. **Limpieza**: una sola implementación de la fórmula, macros que cuadren.

**Dos decisiones de producto, ya resueltas por el usuario (2026-11-01): las dos van.**
- **Cintura** → ✅ **implementada ya** (ver más abajo, en los cambios sin commitear). Se hizo aparte
  porque es independiente de los bloques 1-4. Descartado el método Navy como estimador de % de grasa
  (infraestima ~6 puntos vs DXA); se guarda la circunferencia cruda y se interpreta su tendencia.
- **Recomposición** → aprobada, pero **se implementará dentro del bloque 1**, no antes: toca exactamente
  el mismo código que el bloque 1 va a reescribir (selector de objetivo, selector de ritmo,
  `computeTargets`). Hacerla ahora obligaría a escribirla contra el sistema de ritmos roto y rehacerla
  acto seguido. Valores previstos: `goal = "recomp"`, ritmo de 0 a −0,25 % PC/sem, proteína alta como en
  déficit. **Recordar añadir "recomp" a la lista de objetivos válidos de `ProfileRoute.kt:96`.**

---

## Dónde estamos

Proyecto en **Fase 5** (mejora de funcionalidades existentes) de 6, según
[00-roadmap.md](00-roadmap.md). Fases 1-4 completas. Dentro de la Fase 5, se sigue el "orden de
ataque" de [08-backlog-mejoras.md](08-backlog-mejoras.md):

| Bloque | Qué | Estado |
|---|---|---|
| 1 | Pérdida de datos y funciones muertas (RED-1, NUT-1, NUT-2, AUTH-1, DB-1) | ✅ Completo |
| 2 | Fiabilidad del conteo de kcal (NUT-3, NUT-4, WEAR-1, WEAR-2) | ✅ Completo |
| 3 | GPS como funcionalidad real (GPS-1, GPS-2, GPS-3, GPS-4) | ✅ Completo |
| 4 | Resiliencia transversal (cliente HTTP único, timeouts, 401 centralizado) | ✅ Completo |
| 5 | Coste y seguridad (gate premium real, redimensionar imagen IA, caché OFF) | ✅ Completo |
| — | WEAR-3 (refresco periódico de datos de la pulsera) | ✅ Completo |
| — | VAL-1/VAL-2 (validación de peso/altura/edad/ritmo) e IA-2/IA-3 (reproducibilidad y parseo del análisis) | ✅ Completo |

**Importante para la próxima sesión**: el usuario y Claude acordaron explícitamente (2026-10-27) que,
tras cerrar este backlog pequeño, **toca parar de tocar código y que el usuario pruebe la app de verdad**
en su máquina (IntelliJ, con Android SDK) antes de seguir. Hay bastante acumulado sin verificar en este
entorno (ver la sección de abajo). Si el usuario vuelve pidiendo seguir con más desarrollo sin haber
mencionado una prueba, está bien seguir — pero no hay que insistir en bloquear el trabajo esperando esa
prueba; es una recomendación acordada, no una regla dura. Lo que sí hay que evitar es seguir
amontonando más cambios Android-only sin probar los que ya hay, salvo que el usuario lo pida.

## Lo último que se hizo

### 2026-11-01 — Reescritura del cálculo nutricional (bloques 1-4) — SIN COMMITEAR

Todo verificado: `:shared:jvmTest` (18 pruebas, 0 fallos), `:server:compileKotlin`,
`:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs`.

**Pieza central: `shared/.../nutrition/NutritionMath.kt` (nuevo).** La fórmula vive ahora en el módulo
`shared`, del que dependen **servidor y cliente**, así que es literalmente la misma implementación en
los dos lados — no una copia. Esto cierra el bloque 4 de verdad: antes estaba triplicada en
`ProfileService.computeTargets`, `OnboardingScreen.StepSummary` y `NutritionScreen`, y ya había
divergido. Cada constante remite a su sección de `10-fundamento-cientifico.md`.

**Bloque 1 — base del cálculo**
- `goalRate` pasa de **kcal/día con signo** a **% de peso corporal por semana**. Es un cambio de
  unidades sobre datos reales, con migración automática al arrancar (ver abajo).
- Rangos validados por objetivo: perder 0,5-1 %/sem, ganar 0,25-0,5 %/sem, recomposición 0 a -0,25 %.
  Validado **en el servidor** (`ProfileRoute`), que antes no comprobaba el ritmo en absoluto.
- Las etiquetas se **derivan** del número con el peso real del usuario, así que no pueden mentir.
- Suelo de seguridad = **BMR del usuario** en vez de 1.500/1.200 fijos (que no tienen respaldo), y
  **se avisa en Nutrición cuando el suelo recorta el ritmo** en lugar de callarlo.
- **Recomposición añadida** como objetivo (`recomp`), con tarjeta explicativa en el Onboarding sobre
  para quién funciona realmente.
- Onboarding y Perfil ofrecen ahora exactamente las mismas opciones, desde `NutritionMath.presetsFor`.

**Bloque 2 — modo pulsera**
- `HealthDataProvider.getEnergySummary(days)` (nuevo) devuelve media de días completos + ayer + hoy.
  En Android usa `aggregateGroupByPeriod`, que da un total por día: el histórico ya estaba en Health
  Connect, la limitación de "solo sabemos leer hoy" era de nuestra interfaz.
- El presupuesto se calcula con la **media de 14 días**, estable desde que el usuario se levanta. La
  lectura del día en curso pasa a ser **solo informativa** — era la causa del déficit encubierto.
- **Arrastre**: la diferencia entre la actividad de ayer y la media se suma al objetivo de hoy, así que
  un día atípico no se pierde sin tener que perseguir un número que aún se mueve.
- Arreglado **BUG-N7**: el arrastre no se aplica a días pasados.

**Bloque 3 — autoajuste**
- `GET /profile/adaptive` mide el gasto real (`ingesta media − cambio de peso × 7700 / días`) con
  **regresión por mínimos cuadrados** sobre los pesajes, no "último menos primero".
- Puertas de entrada: ≥14 días con comidas, ≥4 pesajes en ≥10 días, y se rechaza un resultado absurdo
  (<800 o >6000 kcal), que casi siempre significa registro incompleto y no un metabolismo raro.
- **Límite de una vez por semana** (`adaptive_updated_at`): sin él, la mezcla 0,3/0,7 se aplicaría en
  cada carga de pantalla y el suavizado desaparecería, que es justo lo que viene a aportar.
- Se muestra en Perfil (`AdaptiveCard`), redactado como "ajustado según tu progreso real" y nunca como
  "científicamente calculado", porque el método es convención y no evidencia revisada por pares (§5.4).

**Bloque 4 — limpieza**
- Una sola fórmula (arriba). `NutritionScreen` ya no reescala macros por su cuenta.
- Macros que **siempre cuadran**: antes `carbsG.coerceAtLeast(0)` dejaba el reparto descuadrado en
  silencio; ahora el exceso se absorbe bajando primero la grasa (hasta un 15% de las kcal) y solo
  después la proteína. Hay un test que lo comprueba en 16 combinaciones de peso y kcal.

**⚠️ Migraciones automáticas de base de datos — requieren reiniciar el servidor**

Corren en `initTable()`/`initWeightHistoryTable()`, dentro de `runCatching` para que el error de
"columna duplicada" del segundo arranque no tumbe el servidor:
1. `weight_history.waist_cm` (cintura).
2. `user_profiles.adaptive_tdee` y `adaptive_updated_at`.
3. **Conversión de unidades de `goal_rate`**: kcal/día → % semanal. Discrimina por `ABS(goal_rate) > 10`
   (un % nunca pasa de 1; un valor en kcal nunca bajaba de 250), así que es idempotente.
4. Encaje de `goal_rate` en el rango seguro del objetivo. Idempotente y además autorreparador si
   alguien deja un valor inválido a mano.

**Pendiente de verificar por el usuario**: `HealthConnectProvider.getEnergySummary` es código Android y
**no se ha podido compilar aquí** (sin SDK). Es el cambio de este bloque que más conviene probar en el
móvil.

### Corrección posterior (mismo día) — tres fallos que encontró el usuario probando

El usuario puso "perder grasa" + ritmo suave y **el objetivo le salió exactamente igual a su BMR**, con
el déficit anulado. Tres fallos encadenados, los tres míos:

1. **En modo pulsera, "sin datos" se trataba como "sin actividad"**: el gasto quedaba en `BMR + 0`, que
   equivale a decir que la persona está en coma. Ahora, sin histórico, se asume **sedentario (× 1,2)**
   y el autoajuste corrige después. Invariante nuevo verificado en test: **`gasto > BMR` siempre**.
2. **`/profile/save` no recibía la media de la pulsera**, así que el resumen tras guardar se calculaba
   con 0 de actividad y no coincidía con lo que luego se veía en Nutrición. Arreglado.
3. **El suelo en el BMR era aritméticamente incompatible con los propios rangos de ritmo.** Un
   sedentario gasta 1,2 × BMR, o sea un margen del 20% de su BMR hasta el basal — menos de lo que pedía
   el ritmo *más suave* que la app ofrecía. Sustituido por un **tope de déficit del 25% del gasto**,
   que escala y nunca puede anular el déficit. Ver §7.3 de `10-fundamento-cientifico.md`, con la
   aritmética del caso real.

**Además, corregido un error de lectura de la fuente**: 0,5-1 %/semana se estaba tratando como un rango
con dos límites, bloqueando ritmos más lentos. Helms da esa banda como la que **maximiza la retención de
masa magra**, no como mínimo de seguridad. Ir más lento es seguro, solo más lento. Ahora hay **tope duro
arriba** (1 % perder / 0,5 % ganar) y **banda recomendada** que solo se sugiere.

**Rediseño del selector de ritmo** (decidido por el usuario): deslizador con zonas de color, en
`components/RateSlider.kt`, compartido por Onboarding y Perfil. Trabaja en % internamente pero enseña
**kg/semana**, que es como piensa la gente. El extremo del deslizador *es* el tope duro, así que
pasarse es imposible por construcción. Por debajo de la banda no se avisa de nada.

**Tests ampliados a 31**, con un barrido de ~2.000 combinaciones (sexo × actividad × objetivo ×
complexión × ritmo × con/sin pulsera) que comprueba 9 invariantes. Los dos que habrían pillado este
fallo —`gasto > BMR` y "un déficit pedido nunca queda en cero"— no existían antes.

**La migración de `goal_rate` se ha suavizado**: ahora solo limita la magnitud máxima, no sube los
ritmos lentos hacia la banda recomendada. Un `-400 kcal/día` a 75,5 kg se queda en `-0,36 %/sem` tal
cual, en vez de subirse a `-0,5 %`.


**Backlog residual cerrado: VAL-1, VAL-2, IA-2, IA-3.** Tras cerrar los 5 bloques + WEAR-3, se acordó
con el usuario rematar estos cuatro hallazgos (rápidos, sin tocar Android) antes de parar a probar:

- **VAL-1 (validación de peso/altura/edad/ritmo)**: `ProfileScreen` y `WeightScreen` convertían con
  `toDoubleOrNull() ?: 0.0` y mandaban ceros en silencio si el campo estaba vacío o mal escrito. La
  validación correcta ya existía, pero solo en `OnboardingScreen`. Se extrajo a
  `validation/ProfileValidation.kt` (nuevo: `validateWeightKg`, `validateHeightCm`, `validateAge`,
  `validateGoalRate`) y las tres pantallas la reutilizan ahora. `WeightScreen` además ganó
  `keyboardType = Decimal` y un error visible en el propio campo (antes un valor inválido no hacía nada
  al pulsar "Guardar", sin explicación).
- **De rebote, se arregló también una instancia de ERR-1**: al tocar el diálogo de añadir peso de
  `WeightScreen` para VAL-1, se vio que se cerraba y vaciaba el campo sin comprobar si `saveProfile()`
  había tenido éxito. Ahora solo se cierra dentro de `onSuccess`; si falla, se queda abierto con el peso
  introducido y un error visible. El resto de instancias de ERR-1 sigue abierto.
- **VAL-2 (guardado de datos personales saltaba la validación del ritmo)**: guardar nombre/teléfono/etc.
  recalculaba la edad desde la fecha de nacimiento y, si procedía, reguardaba todo el perfil físico a
  escondidas con `goalRate.toDoubleOrNull() ?: 0.0` — si el usuario había elegido "perder peso" sin
  ritmo, ese reguardado derivado grababa un déficit de 0.0 en silencio. Ahora ese reguardado solo se hace
  si pasa la misma validación que el botón físico; si no, se omite en silencio (no se le muestra un error
  al usuario por un efecto derivado de un botón que no pulsó — simplemente ya no corrompe el dato).
- **IA-2 (análisis no reproducible)**: `OpenAIService` no fijaba `temperature` ni `response_format`.
  Ahora fija `temperature = 0.2` y `response_format: json_object`. El prompt ya no obliga a fingir
  certeza — puede avisar en `summary` cuando la foto no permite una estimación fiable.
- **IA-3 (parseo frágil)**: se decodifica ahora con un `Json { isLenient = true }` dedicado en vez del
  estricto por defecto, así que un número donde se esperaba texto ya no revienta el análisis entero.
- Los cuatro compilan limpio en los targets verificables aquí (`:composeApp:compileKotlinJvm`,
  `compileKotlinJs`, `compileKotlinWasmJs`, `:server:compileKotlin`) — **ninguno toca código Android**,
  así que no añaden nada a la lista de "sin verificar".
- Pendiente de que el usuario confirme el commit.

### Resumen de lo hecho en WEAR-3 (refresco de datos de la pulsera), ya commiteado antes de esto (commit `5419f2c`)

Arreglado con un enfoque deliberadamente más simple que el que se había esbozado en el hallazgo
original:

- El análisis original proponía mover la lectura de Health Connect a un `WorkManager` periódico con
  caché local — arquitectura más grande, Android-only, con el mismo problema de no poder verificarse en
  este entorno que tuvo GPS-1. Revisando el síntoma real descrito ("si abres la app por la mañana, el
  presupuesto se queda congelado todo el día" — es decir, obsoleto **mientras la pantalla está abierta**,
  no solo con la app cerrada), un refresco periódico dentro de la propia pantalla resuelve exactamente
  eso con mucho menos riesgo.
- Se implementó así: nueva constante `WEARABLE_POLL_INTERVAL_MS` (5 minutos) en `HealthDataProvider.kt`
  (común). `NutritionScreen.kt` separó la lectura de calorías activas en su propio `LaunchedEffect(
  hasWearable)` con un bucle `while(true) { leer; delay(...) }` en vez de una lectura única dentro del
  efecto que también carga perfil/comidas. `DashboardScreen.kt` convirtió su lectura única de
  pasos/calorías en el mismo patrón de bucle. El bucle se cancela solo al salir de la pantalla (ciclo de
  vida normal de `LaunchedEffect`), sin código nuevo de gestión de lifecycle.
- **Esto vive entero en `commonMain` — no se tocó ni una línea de código Android.** A diferencia de
  GPS-1/WEAR-1/WEAR-2, no hay ninguna limitación de "sin verificar con build real" aquí: se compiló y
  verificó al 100% con `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs` y
  `:server:compileKotlin` (los cuatro `BUILD SUCCESSFUL`).
- **Lo que esto no resuelve** (y donde sí compensaría el `WorkManager` si en el uso real hiciera falta):
  el número no se actualiza si la app está completamente cerrada o en segundo plano sin esa pantalla
  abierta. Se consideró un caso mucho menos común que el síntoma real descrito, así que no se implementó
  — queda anotado por si el usuario nota que no es suficiente.
- `ProfileScreen.kt` también lee pasos/calorías, pero ahí es solo un dato informativo al editar el
  perfil (no alimenta ningún presupuesto que se vea todo el día) — se dejó como lectura única a
  propósito, para no añadir complejidad donde no cambia nada visible.

### Resumen de lo hecho en el bloque 5 (coste y seguridad), ya commiteado antes de esto (commit `45879a9`)

Cerrado el bloque 5 (coste y seguridad) entero: SEC-1, IA-1, OFF-1. Todos son cambios de servidor,
verificados con `:server:compileKotlin` (sin limitación de Android SDK en ninguno de los tres).

- **SEC-1 (gate premium real)**: `bodyAnalysisRoute` ahora comprueba `profileService.isPremium(session.
  userId)` y responde `403 Forbidden` si no lo es, antes de llamar a OpenAI. Antes `isPremium()` existía
  pero no se llamaba desde ningún sitio — cualquier usuario logueado podía gastar créditos de OpenAI sin
  límite con una petición directa a la API (el control de cliente no protege nada real). **Sigue sin
  haber** cuota diaria/rate limit para usuarios premium — fuera del alcance pedido, candidato a futuro.
- **IA-1 (redimensionar imagen antes de OpenAI)**: `OpenAIService.resizeIfNeeded()` decodifica la imagen
  (`javax.imageio`, sin dependencias nuevas) y la reescala a máximo 1024px de lado largo antes de
  mandarla a OpenAI (que de todas formas reescala a ≤768px internamente, así que la resolución extra se
  descartaba sin ganancia). Si falla la decodificación, se manda la original en vez de romper el
  análisis. **Importante**: reduce el payload hacia OpenAI, no el payload cliente→servidor — el cliente
  sigue mandando la imagen completa al endpoint (arreglarlo necesitaría tocar el `ImagePicker` de cada
  plataforma, con el añadido de que solo Android lo tiene implementado hoy, ver IOS-1).
- **OFF-1 (caché de Open Food Facts)**: `FoodService` tiene ahora una caché en memoria con TTL de 24h
  (clase `TtlCache` interna, sin dependencias nuevas) para `searchFood()` y `lookupBarcode()`. Un
  resultado vacío o un fallo nunca se cachea (podría ser un fallo transitorio de OFF, no "no existe").
  Reduce el riesgo de que OFF bloquee la única IP de Render por exceso de peticiones repetidas.
- Al implementar SEC-1/IA-1/OFF-1 no hubo errores de compilación — los tres pasaron a la primera.

### Resumen de lo hecho en el bloque 4 (resiliencia transversal), ya commiteado antes de esto (commit `1199fd6`)

- **HTTP-1 (cliente único)**: había 5 instancias de `AuthService()`/`HttpClient` (una por pantalla:
  `NavGraph`, `WeightScreen`, `DashboardScreen`, `ProfileScreen`, `SubscriptionScreen`). Ahora `NavGraph`
  crea el único `AuthService()` de toda la app y lo pasa como parámetro a las 4 pantallas que antes
  creaban el suyo. `ProfileScreen` además reutiliza el `profileService` ya compartido de `NavGraph` en
  vez de crear uno nuevo. Las pantallas que construyen un servicio de dominio propio (`WeightService`,
  `SubscriptionService`) lo siguen haciendo, pero a partir de `authService.client` compartido.
- **RED-2 (timeouts + motor de red determinista)**: se quitó `ktor-client-android` del classpath de
  Android (quedaba junto a `ktor-client-okhttp`, y cuál cargaba Ktor no era determinista). Se instaló
  `HttpTimeout` en el `HttpClient` único: `requestTimeoutMillis = 45_000`, `connectTimeoutMillis =
  15_000`, `socketTimeoutMillis = 45_000` — generosos a propósito por los cold starts de Render.
  **Ojo**: el análisis corporal por IA ahora también tiene este límite; si en uso real se corta antes de
  que OpenAI responda, es un ajuste de una línea (timeout específico para esa llamada).
- **RED-3 (401 centralizado)**: `AuthService` tiene un `HttpResponseValidator` que detecta cualquier 401
  fuera de las rutas `/auth` (que gestionan su propio 401 como parte del flujo normal) y expone
  `sessionExpired: StateFlow<Boolean>`. `NavGraph` lo observa y, si se activa, limpia la pila de
  navegación y manda a `LoginScreen` con un aviso ("Tu sesión caducó, inicia sesión de nuevo."). Antes,
  una sesión caducada a mitad de uso dejaba cada pantalla con datos vacíos sin explicación.
- **Sigue abierto a propósito** (menor impacto ahora que el 401 ya es centralizado): el almacenamiento de
  cookies sigue guardando solo `nombre=valor`, sin expiración/dominio/path/`secure` — es un cambio más
  grande (afecta a los 5 `CookiesStorage` por plataforma) que no se hizo en este bloque.
- Verificado con `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs` y
  `:server:compileKotlin` (todos `BUILD SUCCESSFUL`); también se repitió
  `:composeApp:compileKotlinIosSimulatorArm64` y sigue fallando solo por el error ya documentado de
  `MainViewController.kt` (no por este cambio). **Sin verificar con build real de Android** — el cambio
  de motor de red (quitar `ktor-client-android`) es justo el tipo de cosa que conviene comprobar antes de
  confiar en los tiempos de carga reales en un dispositivo.
- Todo esto, junto con el cierre del bloque 3 (GPS, ver detalle más abajo), **ya está commiteado**.

### Resumen de lo hecho en el bloque 3 (GPS), ya commiteado antes de esto

- `GpsTrackingService.kt` (nuevo, Android): foreground service con tipo `location` y una notificación
  simple ("Grabando ruta GPS"). Su único trabajo es mantener el proceso con prioridad elevada — no
  recolecta ubicación él mismo, eso sigue siendo `LocationService` desde `GpsScreen`. Sin esto, Android
  cortaba el GPS en cuanto la pantalla se apagaba.
- `BackgroundTrackingController.kt` (nuevo, común + 5 `actual`): arranca/para ese servicio desde
  `GpsScreen.kt` (código común, no puede llamar a APIs de Android directamente). No-op fuera de
  Android.
- Manifest: permisos nuevos `FOREGROUND_SERVICE_LOCATION` y `POST_NOTIFICATIONS`, declaración del
  `<service>`. Dependencia nueva `androidx.core:core-ktx` (para `NotificationCompat`).
- **Decisión de diseño tomada sin poder confirmarla con el usuario a tiempo**, y resuelta después: la
  notificación del foreground service es mínima (sin botón de "Parar", sin distancia/tiempo en vivo,
  `POST_NOTIFICATIONS` sin flujo de permiso en runtime). El 2026-10-27 el usuario confirmó: **esto queda
  como mejora futura, no se hace ahora** (anotado como GPS-1b en
  [08-backlog-mejoras.md](08-backlog-mejoras.md) 🟡 Bajos). No volver a preguntar por esto — solo
  retomarlo si el usuario lo pide explícitamente.
- Verificado con `:composeApp:compileKotlinJvm`, `:composeApp:compileKotlinJs`,
  `:composeApp:compileKotlinWasmJs` (los tres `BUILD SUCCESSFUL`). El `Service` y su `actual` de
  Android **no se han podido compilar en este entorno** (sin Android SDK) — es, de lejos, el cambio
  que más necesita probarse en un dispositivo/emulador real antes de confiar en él.
- Con esto, **el bloque 3 (GPS) queda completo**. Ya commiteado (commit `ff98ae7`), igual que el
  bloque 4 (commit `1199fd6`) y el bloque 5 (commit `45879a9`).

## Lo siguiente que hay que hacer

**(2026-11-01) Lo inmediato: implementar los bloques 1-4 del plan de cálculo nutricional** descrito al
principio de este documento, con `10-fundamento-cientifico.md` como referencia de cada número. Antes de
empezar, conviene tener la respuesta del usuario a las dos decisiones de producto pendientes (cintura y
objetivo de recomposición), porque ambas afectan al modelo de datos.

Esto **sustituye** como prioridad a la recomendación anterior de parar a probar: el usuario ya probó, y
lo que encontró al probar es justamente lo que ahora hay que arreglar.

### (2026-11-22) Nueva funcionalidad pedida por el usuario: pestaña "Entreno" (registro de series)

El usuario trajo una maqueta HTML propia (`~/Downloads/GYMTRACK.html`) para convertirla en **una nueva
pestaña del menú principal** (= una `AnimatedMiniCard` más en `DashboardScreen` + ruta nueva en
`NavGraph`). Pidió rediseñarla antes de implementarla, con dos requisitos explícitos:

1. **Apuntar el peso sin abrir el teclado del sistema** — tiene que ser rápido, en el gimnasio.
2. Que la entienda "una persona normal", y con aire más profesional.

**Iteración 2 (la buena): `~/Downloads/GYMTRACK-v3.html`.** El usuario rechazó la v2 por dos razones
concretas, y las dos cambian el modelo de datos, así que la implementación debe seguir la v3:

1. **El número grande es el que él mira, no el total.** Él no piensa "80 kg": piensa "30 a cada lado".
   Así que en barra libre se apunta **el peso por lado** y el total se muestra calculado debajo
   (`barra + 2 × lado`). En mancuernas se apunta una mancuerna; en máquina y polea, el número de la
   placa y nada más (ahí los "lados" no existen y no deben aparecer).
2. **El tipo de ejercicio se decide una vez, al crearlo** (barra libre / mancuernas / máquina / polea /
   peso corporal), no con un conmutador en cada serie. El tipo es el que decide qué número te pide la
   app, qué unidad muestra y el paso del ±. Se cambia desde un chip discreto bajo el nombre.

Esto elimina la rejilla de 7 discos, el selector de barra, el selector de paso y el conmutador de modo
de la pantalla principal: de ~20 botones a 7 en la tarjeta de peso (±, 4 atajos del propio ejercicio y
el número, que abre el teclado). También se quitó el RIR de la pantalla principal por ruido.
El teclado propio sigue siendo la vía universal, y en barra/mancuernas tiene un enlace pequeño
"escribir el total" que hace la conversión al revés.

**Ajustes tras la segunda ronda de feedback (mismo día)**: el salto del `-`/`+` **no puede ser fijo**
("de 5 en 5 por la cara, hay pesos entre medias") — ahora es un ajuste por ejercicio
(0,5 / 1 / 1,25 / 2,5 / 5 / 10, en la unidad de su tipo), con un valor de partida por tipo
(2,5 por lado en barra, 1 en mancuernas, 2,5 en máquina y polea). Y el **RIR vuelve**: se había
quitado por reducir ruido y el usuario lo reclamó; está como **fila de botones en pantalla**
(`— 0 1 2 3 4+`) debajo de las repeticiones rápidas, recuerda el último valor del ejercicio y se guarda
en cada serie. (Se probó a meterlo en un chip con hoja para ahorrar botones y el usuario lo rechazó:
el RIR se apunta serie a serie, tiene que estar a un toque.)

**Iteración 1 (descartada, solo para historial)**: `~/Downloads/GYMTRACK-v2.html` (un solo archivo, se abre en el
navegador; usa los colores/tipografía de `GymSmartColors`). Verificada con jsdom simulando el flujo
completo (cargar discos, teclado propio, guardar/editar/borrar/deshacer serie, modo mancuerna, terminar).
**Estado (2026-11-22): implementado en la app y compilando.** Entra por la tarjeta **"Entreno"**, la
primera del menú del Dashboard. Ficheros: `model/WorkoutModels.kt`, `screens/WorkoutScreen.kt`,
`screens/WorkoutLogScreen.kt`, `services/WorkoutService.kt`, `commonTest/.../WorkoutModelsTest.kt` y,
en el servidor, `model/WorkoutModels.kt` + `services/WorkoutService.kt` + `routes/WorkoutRoute.kt`
(`GET`/`PUT /workouts`). Compilan jvm, js, wasmJs y servidor; 13 tests en verde.

⚠️ **Hay que reiniciar el servidor** para que se cree la tabla `workout_plans`.
⚠️ **Sin probar en vivo ni en Android** (aquí no hay SDK ni servidor levantado): pendiente de que el
usuario lo compile y lo use.

**La maqueta tiene ya tres pantallas** (todas en `GYMTRACK-v3.html`): lista de días → definir un día →
apuntar. El modelo es `DAYS = [{name, ex:[{name, kind, bar, step, prev:[{v,r}], sets:[]}]}]`; la pantalla
de apuntar no tiene datos propios, opera sobre los ejercicios del día abierto. El cronómetro arranca al
pulsar "Entrenar" y "Terminar entrenamiento" vuelca las series de hoy a `prev`.

Decisiones de diseño que la implementación debe conservar (son la respuesta a los fallos de la maqueta
original, listados en `07-registro-sesiones.md`):

- **Dos modos de peso**: "barra y discos" (el peso total se *calcula* a partir de la barra + los discos
  por lado, ×2 — la maqueta original sumaba el disco una sola vez, lo que da la mitad del peso real) y
  "peso directo" para máquina/polea/mancuerna.
- **Teclado numérico propio** (hoja inferior) como vía universal para cualquier peso; en modo barra, el
  total escrito se reparte en discos automáticamente y, si no es montable con los discos existentes,
  se avisa de cuánto falta en vez de mentir.
- **El peso se pone solo, serie contra serie**: cada ejercicio guarda las series de su última sesión;
  al registrar la serie N se propone la serie N de aquel día (peso y reps), y al guardar pasa a la N+1.
  La primera vez, 0. Esto sustituye a cualquier atajo de "pesos recientes".
- **Nunca suponer cuántas series va a hacer**: el progreso de cada ejercicio son las series hechas
  (una rayita cada una, tope 8), no un objetivo de 4.
- Discos quitables uno a uno, paso de ±2,5/5/10 con pulsación mantenida acelerada, "repetir serie
  anterior", editar/borrar serie individual con deshacer, y temporizador de descanso al guardar.

### Cambios hechos esta sesión y SIN COMMITEAR

Todos compilan (`:composeApp:compileKotlinJvm` y/o `:server:compileKotlin` `BUILD SUCCESSFUL`). El
usuario no ha pedido commit todavía:

- `EmailService.kt` — log a nivel `WARN` de los rechazos de Brevo (antes `debug`, invisible).
- `ProfileValidation.kt` + `WeightScreen.kt` + `ProfileScreen.kt` + `OnboardingScreen.kt` — nuevo
  `parseDecimal()` que acepta **coma decimal** (el teclado español ofrece coma y la app rechazaba
  "75,5"). Cierra INC-2 de `revision-tecnica-claude.md`. Propagado a todos los `.toDouble()` que antes
  se apoyaban en que la validación ya había rechazado la coma.
- `NutritionScreen.kt` — `goalsLoaded` renombrado a `goalsFailed` y corregida su semántica: el aviso rojo
  de "objetivos no cargados" parpadeaba en **cada** entrada a la pantalla, porque el estado inicial
  (`false`) era indistinguible de "falló". Ahora solo se pone a `true` en el `onFailure`.
- `NutritionScreen.kt` + `DashboardScreen.kt` — **botón de refresco manual** de los datos de la pulsera,
  para no esperar al ciclo de 5 minutos tras sincronizar Mi Fitness a mano.
- `AppConfig.kt` — lo editó **el usuario**, no Claude: tres URLs comentadas (Render / Tailscale / local),
  activa la de Tailscale.
- **Circunferencia de cintura (hecho 2026-11-01, aprobado por el usuario)**: campo opcional en el diálogo
  de "Añadir peso". Toca `ProfileService.kt` (columna `waist_cm` + migración), `ProfileRoute.kt`
  (`waistCm` en `ProfileRequest`, validación 40-200 cm), `Profilemodels.kt`, `WeightEntry.kt`,
  `ProfileValidation.kt` (`validateWaistCm`, en blanco = válido) y `WeightScreen.kt` (campo + nueva
  `CompositionCard`, que cruza tendencia de peso y de cintura para decir si lo ganado es músculo o
  grasa). Compilado en jvm, js y wasmJs. **⚠️ Requiere reiniciar el servidor** para que corra el
  `ALTER TABLE weight_history ADD COLUMN waist_cm REAL` (va dentro de `runCatching`: a partir del
  segundo arranque falla por columna duplicada y se ignora a propósito).

### Backlog residual (sin orden obligatorio, ver `08-backlog-mejoras.md`)

- GPS-5/GPS-6 (filtros de velocidad/precisión sin comprobar `hasSpeed()`/`hasAccuracy()`; duración que
  incluye el tiempo parado) — toca `GpsScreen.kt`, común, sí verificable aquí.
- PERM-1 (callbacks de permisos de cámara/GPS que pueden perderse en una recomposición de
  `MainActivity.kt` — Android-only, sin poder verificar aquí).
- El resto de 🟡 Bajos: seguridad/configuración menor (`usesCleartextTraffic`, `allowBackup`), datos de
  alimentos (paginación, `lc=es`), análisis IA sin persistir histórico, código muerto/inconsistencias
  menores, UI-1/UI-2.
- Cabos sueltos de Fase 2/3 (fuera de la Fase 5): logging del cliente, estrategia de DI mínima, y
  decidir cuándo desplegar todo lo acumulado a Render (hoy sigue todo solo en local).

Ninguno de estos requiere decisiones de producto tan grandes como GPS-1 — se puede proceder sin
bloquear en una respuesta del usuario, salvo que él indique otra prioridad.

## Pendiente de verificación por el usuario (código Android no compilable aquí)

Este entorno no tiene Android SDK instalado. Estos cambios están hechos y con la lógica revisada, pero
**sin verificar con un build real**. El usuario ha decidido no probar nada hasta terminar toda la Fase 5
— esta lista es el checklist a usar entonces, de una sola vez, en IntelliJ con un dispositivo o emulador
real. Se mantiene al día conforme se añaden más cambios Android-only:

- `CookiesStorageFactory.android.kt` — cifrado de la cookie de sesión con `EncryptedSharedPreferences`.
- `HealthConnectProvider.kt` — migración a `aggregate()`.
- **`HealthConnectProvider.getEnergySummary()`** (2026-11-01, lo más reciente y lo más importante de
  este bloque): usa `aggregateGroupByPeriod` para la media de 14 días de calorías activas, que es la
  base del nuevo presupuesto en modo pulsera. Si falla, el objetivo cae al BMR. Comprobar que la media
  es un número razonable y, sobre todo, que **el objetivo diario ya no se mueve a lo largo del día**.
- `RouteRecoveryStore.android.kt` — persistencia de la ruta GPS en curso.
- **`GpsTrackingService.kt` + `BackgroundTrackingController.android.kt`** (el más importante de
  probar: foreground service nuevo, permisos nuevos, notificación) — confirmar que una ruta grabada
  con la pantalla apagada sigue avanzando, y que la notificación aparece y desaparece correctamente.
- **Cambio de motor de red en Android** (se quitó `ktor-client-android`, queda solo `ktor-client-okhttp`)
  — confirmar que login, carga de pantallas y, sobre todo, un cold start de Render (primera petición
  tras inactividad del servidor) siguen funcionando con normalidad y que el timeout de 45s es suficiente
  en la práctica.
- **401 centralizado**: forzar una sesión caducada (borrar la cookie en el servidor o esperar a que
  expire) y confirmar que la app manda sola al login con el aviso, en vez de quedarse con pantallas
  vacías.

## Decisiones que ya tomó el usuario (no re-preguntar)

- `backend/` y `frontend/` (código muerto) → eliminados, confirmado y hecho.
- `TrainingScreen`/`TrainingService` → se dejan como están por ahora, no se tocan (además se descubrió
  que ni siquiera es alcanzable desde el menú, así que no hay urgencia).
- CORS del servidor (solo `localhost`) → no se toca; el cliente web no está publicado en ningún dominio
  real, solo se prueba en local.
- Cifrado de cookie de sesión → sí, al menos en Android (hecho). JVM/Desktop/iOS/Web quedan sin cifrar,
  prioridad baja.
- Commits: el usuario los pide explícitamente cada vez ("comitea", "sigue"); nunca hacer commit sin que
  lo pida. Nunca se ha pedido push — todo sigue solo en local, no desplegado a Render.
- El usuario trabaja normalmente en IntelliJ, ejecutando Gradle por terminal — mismo flujo que se usa
  en estas sesiones para verificar cambios.
- Sistema de continuidad (`CLAUDE.md` + este archivo) pedido explícitamente por el usuario para poder
  cambiar de sesión sin perder el ritmo cuando el contexto se llene.
- GPS-1b (notificación con botón de "Parar", stats en vivo, permiso de notificaciones en runtime) →
  mejora futura, no se hace ahora (confirmado 2026-10-27).
- El usuario no probará nada en dispositivo real hasta terminar toda la Fase 5 — no insistir en pruebas
  por bloque, mantener la lista de verificación pendiente completa y al día.
- Acordado (2026-10-27): antes de entrar en Fase 6 (funcionalidades nuevas) hace falta que el usuario
  pruebe la app de verdad — por eso, una vez cerrado el backlog pequeño de la Fase 5, toca parar de
  acumular más cambios sin verificar y dejar que pruebe, en vez de seguir ofreciendo más desarrollo.
  **(2026-11-01: ya cumplido — el usuario probó, y de ahí salió la prioridad actual de nutrición.)**
- **GPS aparcado (2026-11-01)**: el usuario no quiere probar ni tocar GPS por ahora. No retomar la
  sección 6 del plan de pruebas ni los hallazgos GPS-5/GPS-6 salvo que él lo pida explícitamente.
- **La pulsera no se arregla desde el código (2026-11-01)**: investigado a fondo y agotadas todas las
  vías (ajustes de MIUI, permisos, ajustes de Mi Fitness). Es una limitación de cómo Mi Fitness exporta
  a Health Connect. **No volver a proponer ajustes del móvil ni de la app de la pulsera.** Lo que sí se
  hace es compensarlo en el diseño (media móvil + arrastre + autoajuste), no perseguirlo.
- **Rigor científico exigido (2026-11-01)**: el usuario pidió explícitamente que los cálculos se basen en
  evidencia y que "si eliges algo, se cumpla". Todo número nuevo de nutrición va documentado en
  `10-fundamento-cientifico.md` con su fuente y su nivel de confianza. **No introducir constantes sin
  justificar ahí.**
- Previsto para el futuro: apartado de **preguntas frecuentes basadas en ciencia** (tipo "¿frecuencia 1 o
  2 veces por semana?", "¿cómo hacer bien un superávit?"). Las respuestas salen de
  `10-fundamento-cientifico.md` §10, no de la intuición.

## Descubrimiento útil para el futuro

`:composeApp:compileKotlinIosSimulatorArm64` **sí compila en este entorno sin Xcode** (el toolchain de
Kotlin/Native para iOS no necesita macOS para la fase de compilación, solo para enlazar/ejecutar la app
final). Al probarlo salió exactamente el error ya documentado en `01-arquitectura.md §5`
(`App()` sin argumentos en `MainViewController.kt`). Esto significa que una sesión futura que trabaje
en soporte real de iOS (Fase 6) **puede verificar bastante más de lo que se pensaba** sin necesitar un
Mac — solo la fase final de construir el `.xcframework`/ejecutar en simulador necesitaría eso.

## Reglas de trabajo específicas de esta fase

- Verificar cada cambio con `./gradlew :server:compileKotlin` y/o `:composeApp:compileKotlinJvm` antes
  de darlo por bueno (no necesitan Android SDK). Para cambios en `commonMain`, también sirven
  `:composeApp:compileKotlinJs`, `:composeApp:compileKotlinWasmJs` y (con las salvedades de arriba)
  `:composeApp:compileKotlinIosSimulatorArm64` — ninguno necesita Android SDK.
- Cuando un cambio toque código `androidMain`, marcarlo explícitamente como "sin verificar con build
  real" en la documentación — no dar a entender que está probado si no lo está.
- Si una decisión de diseño necesita confirmación del usuario y no se puede obtener a tiempo, tomar la
  opción más simple/estándar, avisar explícitamente que se tomó sin confirmar, y documentar cómo
  cambiarla si el usuario quiere otra cosa (no bloquear el trabajo esperando).
- Cada hallazgo cerrado se marca con ✅ en `08-backlog-mejoras.md` (y en `06-funcionalidades.md`/
  `03-seguridad.md` si también aparece ahí), con una frase de qué se hizo y dónde.
- Commit solo cuando el usuario lo pida; nunca push.
