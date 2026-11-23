# Auditoría independiente del cálculo de kcal por objetivo (2026-11-22)

Auditoría pedida por el usuario con cuatro agentes independientes: investigación externa contra fuentes
primarias, auditoría de código con `:shared:jvmTest` ejecutado y réplica numérica, red team adversarial,
y un evaluador que verificó por su cuenta los hallazgos graves. **Solo diagnóstico: no se tocó ni una
línea de código.**

Alcance: toda la funcionalidad de calorías ligada a objetivos de peso (`lose_fat` / `maintain` /
`gain_muscle` / `recomp`), de punta a punta — cálculo, persistencia, API, UI, onboarding, autoajuste
adaptativo y modo pulsera.

Leyenda: 🔴 crítico/alto · 🟠 medio · 🟡 bajo. Se conserva la etiqueta original de la auditoría entre
paréntesis.

---

## Veredicto de fondo: el modelo energético es correcto

Las tres decisiones que más apps fallan están bien resueltas aquí:

1. **Ritmo en % de peso corporal**, no en kg fijos (§3.1 del fundamento científico).
2. **El déficit se deriva de una *velocidad*, no de un *plazo***: se evita
   `(peso − objetivo) × 7700 / días`, que es el error canónico del sector, refutado por Hall 2008/2011/2013.
3. **`BMR + ActiveCaloriesBurnedRecord`**, nunca `TotalCaloriesBurnedRecord` — el único error de esta
   funcionalidad que es verificable contra documentación literal de la plataforma, y está bien puesto.

El uso de 7700 kcal/kg es el único defendible: convertir ritmo semanal → delta diario en un horizonte de
una semana, sin alimentar ninguna proyección de fecha. Y las constantes del código coinciden **todas**
con las tablas de [10-fundamento-cientifico.md](10-fundamento-cientifico.md): no hay ni una inventada.

**"Debería usar el modelo dinámico de Hall" no es una crítica válida aquí.** Hall gana precisión en la
*trayectoria* y en predecir la meseta — funcionalidad que esta app no tiene ni pretende. Para el objetivo
de hoy apenas cambiaría el número, a cambio de un sistema de ecuaciones diferenciales y estimaciones de
composición corporal. El autoajuste por medición es la respuesta correcta y más barata al mismo problema:
mide el resultado en vez de modelar la causa.

## Pero el fallo está en dónde se imponen los invariantes

Tres líneas de código explican alrededor del 70 % de los hallazgos:

1. `val targetKcal = baseTdee + dailyDelta + carryOverKcal` — el arrastre fuera del recorte (AUD-1).
2. `val baseTdee = measuredTdee ?: formulaTdee` — sustitución total en lugar de composición (AUD-3, AUD-4).
3. **La decisión de §7.3 de cambiar un *suelo de ingesta* por un *tope de déficit* sin re-derivar las
   consecuencias.** Son dos objetos de seguridad distintos, y el documento presenta el segundo como
   mejora del primero sin señalar que **elimina la única garantía de mínimo absoluto que existía**. De ahí
   salen en cadena AUD-1, AUD-2, AUD-5 y AUD-6: **cuatro de los hallazgos grandes son un solo error de
   razonamiento.**

El argumento de §7.3 ("el suelo era incompatible con mis propios rangos de ritmo") tenía otras dos
salidas —reducir el ritmo ofrecido a quien tiene poco margen, o estimar masa magra para avisar— y se
eligió la única que conserva la funcionalidad intacta. §7.2 propone el marco correcto (disponibilidad
energética) y lo descarta por "no tenemos masa magra", mientras el sistema **sí** acepta un BMR con
±10 % de error para *fijar* el objetivo. Es rigor asimétrico: laxo para calcular, exigente solo donde la
exigencia bloquearía una funcionalidad.

---

## 🔴 AUD-1 (CRÍTICO) — La capa de seguridad no tiene suelo y su único límite es evadible

Tres defectos del mismo origen:

### (a) `MAX_DEFICIT_FRACTION` es el único límite inferior del sistema

Como el gasto sedentario es `1,2 × BMR`, el objetivo tope-limitado es la identidad exacta **0,90 × BMR**
para cualquier persona sedentaria, sin importar su talla. Reproducido en 5 perfiles por dos agentes por
separado.

### (b) `carryOverKcal` se suma fuera del recorte

`shared/.../nutrition/NutritionMath.kt:281-285`:

```kotlin
val maxDeficit = baseTdee * MAX_DEFICIT_FRACTION
val dailyDelta = if (requestedDelta < -maxDeficit) -maxDeficit else requestedDelta
val limited = dailyDelta != requestedDelta
val targetKcal = baseTdee + dailyDelta + carryOverKcal   // ← el arrastre esquiva el tope
```

Con media 600 kcal activas y arrastre −600: objetivo **1204 kcal = 66,7 % del BMR**, déficit real
**49,9 %** mientras el sistema informa 25 % y `effectiveRate` reporta un ritmo que no es el que aplica.
**El invariante nº5 de §14 queda violado por un factor 2.**

### (c) `carryOverKcal` no está condicionado a `activityLevel`

A diferencia de `avgActiveKcal`, que sí se ignora fuera de modo pulsera (`NutritionMath.kt:75-86`), el
arrastre se suma **sin condición** — y `NutritionScreen.kt:148-151` lo envía mirando solo `hasData` e
`isToday`, nunca `activityLevel` (que además solo se conoce *después* de la respuesta). Un perfil
`sedentary` o `moderate` con Health Connect activo recibe ruido sumado sobre un TDEE **que ya incluye la
actividad en el multiplicador**. Caso verificado: objetivo **1096 kcal con `rateLimited = false`**, es
decir **sin ningún aviso**.

### Por qué ningún test lo vio

El barrido de `NutritionMathTest` itera ~2.000 combinaciones pero **nunca pasa `carryOverKcal` ni
`measuredTdee`** — precisamente los dos parámetros capaces de romper los invariantes. La tabla verde de
§14 es verdadera y a la vez engañosa.

### Decisión del usuario (2026-11-22)

- **El suelo será por disponibilidad energética**: estimar masa magra por población y exigir ≥30 kcal/kg
  MM/día, que es el marco que §7.2 identifica como correcto y descarta por falta de dato. Se aplica
  **al final**, sobre el número que se muestra, no al déficit.
- **`carryOverKcal` se acota dentro del recorte y se condiciona a `activityLevel == "wearable"`**, sin
  cambiar el modelo (se descartaron las alternativas de convertirlo en presupuesto semanal real o
  eliminarlo).

---

## 🔴 AUD-2 (ALTO) — La app afirma al usuario lo contrario de lo que hace

- `composeApp/.../screens/NutritionScreen.kt:389-391` — *"⚠ Tu ritmo se ha limitado por seguridad: no
  bajamos de tu metabolismo basal. Avanzarás a X en vez de Y."*
- `composeApp/.../screens/OnboardingScreen.kt:438` — *"• Nunca por debajo de tu metabolismo basal"*

Ese suelo fue **retirado** en §7.3 y sustituido por el tope de déficit; el texto no se actualizó. Y no es
solo obsoleto: **es falso justo cuando aparece**. Para el perfil del propio usuario (75,5 kg / 184 cm /
21 años), sedentario, ritmo por defecto −0,75 %: objetivo **1624 = 90 % de su BMR de 1805**, con ese
aviso en pantalla.

Es el único hallazgo de la auditoría que consiste en **decirle algo no verdadero al usuario**.

Agravante: §7.3 argumenta expresamente que *"comer 1.751 teniendo 1.805 de basal no tiene nada de
peligroso"*. Si el documento defiende comer por debajo del BMR, la app no puede prometer que nunca lo hará.

---

## 🔴 AUD-3 (ALTO) — `measuredTdee` sustituye en lugar de componerse

`val baseTdee = measuredTdee ?: formulaTdee` (`NutritionMath.kt:273`). Una vez existe `adaptive_tdee`:

- **`activityLevel` deja de influir en el objetivo** — el desplegable que se le ofrece al usuario no hace
  nada y nada en la interfaz lo explica.
- Las kcal activas de la pulsera **se calculan y se descartan**.
- El peso nuevo deja de bajar el gasto (el BMR ya no participa).
- **No hay ningún camino de invalidación**: ni por cambio de objetivo, ni de actividad, ni por salto de
  peso. No existe ningún `SET adaptive_tdee = NULL` en todo el servidor (único punto de escritura:
  `saveAdaptiveTdee`), y sobrevive deliberadamente a cada guardado de perfil
  (`ProfileService.kt:157-160`).
- Si el usuario deja de registrar comidas (`NotEnoughData`), el valor **queda congelado indefinidamente**.

Agravante: el cooldown es de 7 días y la mezcla solo mueve un 30 % por revisión, así que recuperarse de
un error de 600 kcal exige ~6-8 visitas semanales consecutivas a la pantalla. Dos meses sin abrirla son
dos meses con el objetivo equivocado.

---

## 🔴 AUD-4 (ALTO) — El autoajuste en modo pulsera se ancla al valor equivocado y comunica el signo contrario

`server/.../services/ProfileService.kt:338-342` calcula `previousTdee` **sin pasar `avgActiveKcal`**:

```kotlin
previousTdee = profile.adaptiveTdee
    ?: NutritionMath.tdee(
        NutritionMath.bmr(profile.sex, profile.weightKg, profile.heightCm, profile.age),
        profile.activityLevel          // ← sin avgActiveKcal
    )
```

Con `activityLevel == "wearable"` eso cae en la rama de fallback `bmr * 1.2`. El ancla de la primera
mezcla es el "no tengo datos", no el gasto con el que el usuario llevaba semanas comiendo.

Verificado numéricamente (cuerpo de referencia, media 600 kcal activas, gasto medido real 2500):

| | valor |
|---|---|
| Gasto con el que estaba comiendo | 2405 |
| `previousTdee` que usa el servidor | **2166** (= 1805 × 1,2) |
| Mezcla resultante | 2266 |
| Cambio real del objetivo | **−139 kcal/día** |
| Lo que muestra `AdaptiveCard` | **"+100 kcal/día"** |

Doble error: el ajuste va en dirección contraria a la medición, y el número comunicado tiene el **signo
opuesto** al cambio real. Y una vez escrito, AUD-3 hace que la pulsera quede desconectada del cálculo.

---

## 🔴 AUD-5 (ALTO) — La mitad superior del `RateSlider` es decorativa

`RateSlider(goal, weightKg, ratePct, onRateChange)` **no recibe el TDEE**, así que es estructuralmente
incapaz de aplicar `MAX_DEFICIT_FRACTION`: sus etiquetas salen de `describeRate`, que solo conoce ritmo y
peso. Anuncia un déficit y unos kg/semana que el servidor recorta después.

Mujer 60/160/40 sedentaria, perder:

| Posición del slider | Lo que anuncia | Objetivo real | Ritmo real |
|---|---|---|---|
| −0,55 % | −471 kcal/d · 0,33 kg/sem | 1115 | −0,563 % |
| −0,75 % | −643 kcal/d · 0,45 kg/sem | **1115 (idéntico)** | −0,563 % |
| −1,00 % | −857 kcal/d · 0,60 kg/sem | **1115 (idéntico)** | −0,563 % |

**Y lo que ninguno de los tres primeros agentes vio: el ritmo por defecto de la app (−0,75 %) ya está
recortado en `sedentary` y en `light`** — el valor por defecto es inalcanzable en el 40 % de los niveles
de actividad ofrecidos.

Esto es, con precisión, **la misma clase de defecto que motivó toda la reescritura del 2026-11-01**.
Antes la etiqueta mentía respecto al ritmo; ahora la etiqueta es coherente con el ritmo y el *ritmo*
miente respecto al objetivo. El error pasó de −55 % a −39/−54 %: se redujo, **no se eliminó, se mudó un
eslabón**. El comentario de `RateSlider.kt:30-31` ("el extremo del deslizador *es* el tope duro, así que
pasarse es imposible") es falso para todo perfil sedentario o ligero: el tope efectivo es un número que
el deslizador no conoce.

**Arreglo**: derivar el extremo como `min(maxRateFor(goal), rateForDailyDelta(0.25 × TDEE, peso))`.
Convierte AUD-2 y AUD-5 en no-problemas de golpe y devuelve el ritmo por defecto al rango alcanzable.

---

## 🟠 AUD-6 (MEDIO) — No hay tope de superávit

`NutritionMath.kt:282` solo acota por abajo, así que `rateLimited` **nunca puede ser `true` en
`gain_muscle`**:

| Caso (ganar 0,5 %, sedentario) | Gasto | Objetivo | Superávit |
|---|---|---|---|
| Hombre 200 kg / 190 cm / 35 | 3621 | 4721 | **+30,4 %** |
| Mujer 140 kg / 175 cm / 60 | 2439 | 3209 | **+31,6 %** |

§10.3 fija +10-20 % (principiante/intermedio) o +5-10 % (avanzado) citando Iraki 2019, y afirma que por
encima "lo que se gana es grasa". El código permite hasta el triple del techo documentado, **sin aviso**.

Simétrico de AUD-1: el mismo razonamiento que justifica el 25 % de déficit no se aplicó al superávit. La
constante de seguridad existe donde el autor se sintió incómodo (comer poco) y no donde su propia fuente
marca un límite igual de claro.

Agravante conceptual: aplicar 7700 kcal/kg a la ganancia es, por admisión propia (§2.2), usar la densidad
energética de la **grasa** para presupuestar tejido **mixto** cuyo coste real oscila entre 1.000 y 4.570
kcal/kg — y el error va en la dirección de **comer de más**.

*Para el perfil del usuario (75,5 kg, moderado, +0,5 %): +14,8 %, dentro de lo documentado.* El mecanismo
es real pero no muerde en el único perfil que existe hoy — de ahí 🟠 y no 🔴.

---

## ✅ AUD-7 (MEDIO) — `recomp` es `lose_fat` bit a bit — RESUELTO eliminando la opción (2026-11-23)

> **Decisión del usuario, no implementación**: entre las dos vías que proponía el informe original
> ("implementarla de verdad" o "presentarla honestamente como déficit suave"), el usuario eligió una
> tercera: quitarla del menú. Queda `lose_fat` / `gain_muscle` / `maintain`. `GOAL_RECOMP` se borró de
> `NutritionMath` (constante, `maxRateFor`, `recommendedBandFor`, `signFor`, `defaultRateFor`,
> `isRateValid`, `macrosFor`), de los tres selectores de objetivo (Onboarding, Perfil) y del
> `RateSlider`. La migración SQL del servidor convierte cualquier perfil con `goal = 'recomp'` que
> quedara en BBDD a `maintain` con ritmo 0, que es el objetivo más cercano a lo que de verdad hacía (un
> déficit del 0-0,25%, casi mantenimiento). Verificado con `:shared:jvmTest` (40 tests),
> `:server:compileKotlin` y `:composeApp:compileKotlinJvm`.

Salida de `computeTargets` **idéntica campo por campo** para el mismo cuerpo (hombre 80/180/30, moderado)
y el mismo ritmo (−0,15 %): gasto 2759, objetivo 2627, macros 160/333/73, `rateLimited = false`.

`recomp` difiere de `lose_fat` en **exactamente una cosa**: `maxRateFor` devuelve 0,25 en vez de 1,0.
Mismo signo, misma proteína (2,0 g/kg), misma banda efectiva. Y como `isRateValid` permite ritmos por
debajo de la banda en `lose_fat` (decisión correcta de §3.2), **el conjunto de objetivos alcanzables con
`recomp` es un subconjunto estricto del de `lose_fat`**. En el otro extremo, `recomp` con ritmo 0,0 es
válido y produce `objetivo = gasto`, es decir `maintain`, diferenciándose solo en la proteína.

**Los cuatro objetivos del menú son, en el modelo real, dos direcciones, un ritmo y un interruptor de
proteína.**

Nada de lo que §8 y §9.3 especifican está implementado:

- §8: *"no ocurre solo por comer en mantenimiento"* — el objetivo no consulta ni exige nada de
  entrenamiento de fuerza; no hay ningún enlace entre `WorkoutModels`/`WorkoutLogScreen` y el cálculo.
- §8: *"la app debería poder sugerirlo según el perfil"* — no existe; el IMC se calcula y nunca se usa.
- §9.3: la verificación es **peso estable + cintura ↓**. `waistCm` se recoge y se guarda en
  `weight_history.waist_cm`, pero **no hay ninguna lógica que compare las tendencias de peso y cintura**
  ni que produzca la tabla interpretativa de §9.3.

En su favor: el texto del onboarding sobre recomposición (`OnboardingScreen.kt:321-335`) es honesto y
calibrado. El problema es que ese texto es *lo único* que distingue la funcionalidad: la promesa vive en
la cadena de caracteres, no en el modelo.

---

## ✅ AUD-8 (MEDIO) — El IMC se calcula, se pinta y nunca se usa como guardia — RESUELTO (2026-11-23)

> Nuevo `TargetResult.lowBmiGuard`: se calcula el IMC **antes** del suelo (no al final, como antes), y
> si `bmi < 18.5` **y** el objetivo elegido implica más déficit (`signFor(goal) < 0`), el suelo pasa a
> ser el mantenimiento entero — mismo mecanismo que `noSafeDeficit` (que también se activa en este
> caso), con su propio texto en la interfaz explicando el motivo real. **No bloquea el ritmo elegido en
> el formulario**: lo que deja de ocurrir es que ese ritmo se traduzca en un déficit real mientras el
> IMC siga por debajo de 18,5. No se activa para quien pide ganar peso estando ya en bajo peso (no
> tendría sentido bloquear un superávit). "Condición de parada" queda resuelta vía este mecanismo, no
> vía un peso objetivo con fecha — ver la nota de encuadre de abajo, que sigue siendo válida.

`bmiCategoryFor` (`NutritionMath.kt:349-356`) clasifica hasta "Obesidad grado III" y "Bajo peso", y
`BmiCard` lo pinta. **No existe ninguna rama de código que use `bmi` o `bmiCategory` para condicionar
objetivo, ritmo o aviso** (grep exhaustivo).

No hay condición de parada ni transición automática a `maintain`: un perfil `lose_fat` mantiene su
déficit indefinidamente, y alguien que baje a IMC 16 sigue recibiendo −25 %. Tampoco hay límite al
*tiempo* en déficit (ni noción de fase ni diet breaks), aunque §4 explique la adaptación metabólica con
detalle.

**Nota de encuadre importante**: el hueco **no** es "falta un peso objetivo". No tener fecha-objetivo es
precisamente lo correcto — es el error canónico del sector (`(peso − objetivo) × 7700 / días`) y esta app
lo evita a propósito. Pedir un peso objetivo con proyección de fecha sería un **retroceso conceptual**.
Lo que falta es una **condición de parada** y una **guardia por IMC**.

---

## ✅ AUD-9 (MEDIO) — Los mínimos del autoajuste son más laxos que los del propio documento — RESUELTO (2026-11-23)

> `ADAPTIVE_MIN_SPAN_DAYS` 10→14 y `ADAPTIVE_MIN_WEIGH_INS` 4→8, que es justo la combinación que el
> Montecarlo de abajo identifica como el punto donde la desviación del gasto persistido (154 kcal/d)
> cae por debajo del error ya aceptado del multiplicador de actividad (~350 kcal/d). Más una cobertura
> mínima de ventana del 80% (ver AUD-10), que ataca el sesgo del guard desde el otro lado: con pocos
> días válidos muy concentrados, ya no basta con superar el mínimo absoluto si están lejos en el
> tiempo. No se tocó el guard `[800, 6000]` en sí — bajarlo o hacerlo asimétrico queda fuera de este
> bloque, ver el ángulo del ciclo menstrual más abajo (sigue sin cubrirse, no aplica al usuario actual).

`ADAPTIVE_MIN_SPAN_DAYS = 10` y `ADAPTIVE_MIN_WEIGH_INS = 4`, frente a la *"regresión lineal sobre 14-28
días"* que exige §5.3. Y el guard `raw < 800 || raw > 6000` **trunca asimétricamente la cola inferior del
ruido** → sesgo sistemático al alza.

Montecarlo (4.000 trayectorias × 12 semanas, TDEE real 2500):

| Configuración | SD del TDEE crudo | SD del persistido | Media |
|---|---|---|---|
| 4 pesajes / 10 días / σ=1,0 kg | 1132 kcal/d | **476 kcal/d** | **2789 (+289 de sesgo)** |
| 4 pesajes / 10 días / σ=0,5 kg | 731 | 300 | 2543 |
| 8 pesajes / 28 días / σ=1,0 kg | 364 | 154 | 2499 |
| 14 pesajes / 28 días / σ=0,5 kg | 128 | 54 | 2499 |

**En la configuración mínima que el código acepta, la desviación del gasto persistido (±476 kcal/d) es
mayor que el déficit que pretende gestionar (≈440 kcal/d).** El mecanismo que existe para corregir los
±350 kcal de error del multiplicador puede introducir ±476 por su cuenta. La mezcla 0,7/0,3 amortigua el
salto visible, no la varianza acumulada (el ruido estacionario de un EWMA con α=0,3 es ≈0,42·σ_crudo).

Y el sesgo del guard tiene una consecuencia concreta: gasto persistido demasiado alto → objetivo
demasiado alto → el usuario no baja → "mi metabolismo es raro". Es el fallo **opuesto** al de AUD-10, y
conviven en el mismo sistema.

Recomendación: subir `SPAN_DAYS` a 14-21 y `WEIGH_INS` a 8, o escalar la mezcla por la incertidumbre de la
pendiente del OLS (lo que haría un filtro de Kalman, y es poco más de código).

Ángulo no cuantificado: **ciclo menstrual**. No hay nada en el modelo (ni bandera, ni ventana forzada de
28 días, ni aviso). Con retención premenstrual de 1-2 kg al final de una ventana de 10-14 días, el sesgo
entra directo en la pendiente. No aplica al usuario actual.

---

## ✅ AUD-10 (MEDIO) — El registro parcial endurece el déficit progresivamente — RESUELTO (2026-11-23)

> Dos cosas, no una: **(a)** `NutritionMath.ADAPTIVE_MIN_DAY_KCAL = 1000`, filtrado en
> `ProfileService.runAdaptiveAdjustment` antes de contar `loggedDays` — un día con 300 kcal ya no pasa
> como día registrado. **(b)** `NutritionMath.ADAPTIVE_MIN_COVERAGE = 0.8`, nuevo campo `windowDays` en
> `AdaptiveInput` y comprobación en `reconcile`: hacen falta comidas completas en el 80% de la ventana,
> no solo superar el mínimo absoluto. El escenario modal del informe (20 días bien registrados + 8 con
> solo desayuno) ahora falla en `loggedDays` (los 8 días de 300 kcal no cuentan) antes de llegar
> siquiera a la cobertura. `windowDays` tiene valor por defecto `= loggedDays` (cobertura 100%) para no
> romper nada que no pase este parámetro a propósito — ver el test correspondiente.

`NutritionService.getDailyIntake` agrupa por día **sin mínimo de kcal**: un día con solo el desayuno
cuenta como día registrado y pasa `ADAPTIVE_MIN_LOGGED_DAYS`. La ingesta se promedia sobre **días con
registro**; la pendiente de peso se mide sobre **días naturales**. Dos poblaciones distintas, y la
diferencia se interpreta como metabolismo.

Escenario modal (28 días, TDEE real 2500, registra bien 20 días y en 8 solo el desayuno de 300 kcal
mientras come 2400):

- Media registrada: 1514 kcal/d · ingesta real: 2114 kcal/d → pérdida real 1,40 kg
- `measuredTdee` = 1514 + 1,40 × 7700/28 = **1900** → 600 kcal por debajo del real
- El guard `raw ∈ [800, 6000]` **no se dispara**
- Punto fijo: objetivo **1460 kcal** → **déficit real 772 kcal/d = 31 % del TDEE real** (≈0,70 %/sem en
  vez del 0,5 % elegido). En la variante más agresiva llega al **33-44 %**, por encima del tope duro del
  1 %/sem que §3.2 declara infranqueable.

Mientras tanto la app muestra `effectiveRate = −0,5 %`, `rateLimited = false` y "déficit del 23 %".
**Todas las salvaguardas quedan satisfechas y todas miden el número equivocado.**

**Importante, y es mérito real del diseño**: el subregistro *uniforme* **no** produce espiral
descendente. Se autocancela, porque `measuredTdee` se deriva del resultado y no de la predicción: si el
usuario subdeclara siempre U kcal, el punto fijo deja el déficit real **exactamente en el pedido**. Lo
que rompe el método es el registro **intermitente**, que es un fenómeno distinto. Corolario de
comunicación: el TDEE adaptativo **no es "tu metabolismo real"**, es el gasto expresado en las unidades
del propio registro del usuario — y `AdaptiveCard` ya lo comunica bien.

Mitigaciones que no existen: umbral de "día válido" (≥1000 kcal o ≥3 comidas), cobertura mínima de la
ventana (≥80 %), o suelo de `measuredTdee` relativo al BMR.

---

## 🟠 AUD-11 (MEDIO) — Los días pasados se juzgan con el perfil de hoy

No existe ninguna persistencia del objetivo diario: `targetKcal` solo es un campo calculado al vuelo y
**no hay columna de objetivo en ninguna tabla**. `NutritionScreen.kt:143-151` recarga el perfil para cada
fecha seleccionada y solo neutraliza `carryOverKcal` cuando no es hoy (eso sí está bien resuelto).

Consecuencias:

- El historial de adherencia es **reescribible retroactivamente**. Si el usuario pasa de "perder" a
  "ganar", todos sus días pasados pasan a parecer déficits incumplidos.
- Cambiar `MAX_DEFICIT_FRACTION`, `KCAL_PER_KG` o un multiplicador **reescribe el pasado entero**. No hay
  `calcVersion`, así que no es posible saber con qué reglas se generó un objetivo histórico ni reproducir
  un número que se cuestione.

Para un proyecto que ya ha migrado unidades una vez (`goal_rate` de kcal/día a %/semana), es una omisión
con precedente conocido.

---

## ✅ AUD-12 (MEDIO) — `weight_history` solo se escribe si el peso *cambia* — RESUELTO (2026-11-23)

> Se quitó la condición `previous.weightKg != weightKg`: ahora `upsertProfile` escribe siempre una fila
> para hoy (o actualiza la existente, el "evitar duplicado del mismo día" ya lo hacía). Un día de peso
> estable deja rastro en vez de desaparecer de la muestra. Efecto colateral aceptado a propósito: como
> `/profile/save` también lo usan `ProfileScreen` (editar objetivo/actividad) y `OnboardingScreen`, no
> solo el diálogo de pesaje de `WeightScreen`, cualquier guardado de perfil ahora también registra el
> peso del día — es el comportamiento que pide el hallazgo, no un efecto no buscado. El punto 2 de
> abajo ("recomposición se queda sin autoajuste") ya no aplica: `GOAL_RECOMP` se eliminó el mismo día
> (ver AUD-7).

`ProfileService.kt:163`: `if (previous == null || previous.weightKg != weightKg || waistCm != null)`. Un
pesaje que coincide con el peso almacenado **no genera fila**. Dos consecuencias:

1. **Muestra no aleatoria para la regresión**: los días de peso estable se eliminan sistemáticamente y
   solo entran los días de cambio, lo que infla `|pendiente|` y distorsiona `measuredTdee` directamente
   (el término de corrección *es* la pendiente).
2. **Ironía estructural**: el objetivo cuyo rasgo definitorio es que la báscula no se mueve
   (recomposición; §9.3 lo define como "peso estable, cintura ↓") es el que menos filas genera, y por
   tanto el que más probablemente cae en `NotEnoughData("Necesito al menos 4 pesajes")` **habiéndose
   pesado todos los días**. El mecanismo de medición está desalineado con uno de los cuatro objetivos que
   ofrece el producto.

Nota colateral: no existe ruta dedicada de pesaje — `WeightScreen.kt:496-510` registra el peso con un
`POST /profile/save` completo. Eso tiene un efecto bueno (el % de peso corporal siempre usa el peso
actual, y decrece al adelgazar) y uno malo: el peso del perfil es el de **un día concreto**, con su ruido
de agua, y de él dependen BMR, delta diario, macros e IMC a la vez.

---

## ✅ AUD-13 (MEDIO) — Media de la pulsera sin mínimo de días — RESUELTO (2026-11-23, ⚠️ sin build real)

> Nuevo `NutritionMath.WEARABLE_MIN_DAYS_WITH_DATA` (mitad de `WEARABLE_AVERAGE_DAYS`, redondeado hacia
> arriba: 7 de 14), comprobado en `HealthConnectProvider.getEnergySummary` antes de confiar en la
> media — por debajo cae a `WearableEnergy.EMPTY`, que ya hace que el gasto use el respaldo sedentario.
> De regalo, el `days: Int = 14` duplicado de `HealthDataProvider.getEnergySummary` ahora referencia
> `NutritionMath.WEARABLE_AVERAGE_DAYS` en vez de repetir el número a mano. **Sin verificar con build
> real**: `HealthConnectProvider.kt` es `androidMain` y este entorno no tiene Android SDK (ver
> CLAUDE.md) — probarlo en dispositivo/emulador antes de confiar en él al 100%.

`androidMain/.../HealthConnectProvider.kt:89-98`: los días sin dato se descartan con `mapNotNull`, y el
único guard es `if (perDay.isEmpty()) return WearableEnergy.EMPTY`. **Con un solo día en la ventana de
14, ese día *es* la media**, y `hasData = true` hace que se trate como histórico consolidado. Quien
instale la app el día después de una excursión fija su presupuesto sobre ese único día hasta que la media
se diluya.

Además `NutritionMath.WEARABLE_AVERAGE_DAYS = 14` **no se referencia desde ningún archivo** (grep
completo); el 14 vive duplicado como valor por defecto en `HealthDataProvider.kt:44`
(`getEnergySummary(days: Int = 14)`). La constante "documentada" no es la que se usa.

---

## ✅ AUD-14 (MEDIO) — Fallo silencioso de la pulsera — RESUELTO (2026-11-23)

> Nuevo aviso en `NutritionScreen`, condicionado a `hasWearable && isToday && !wearableEnergy.hasData`
> (la variable que se calculaba y se tiraba, ahora sí se lee): *"No tengo datos recientes de tu
> pulsera. Tu objetivo de hoy se calcula como si fueras sedentario... Sincroniza la app de tu pulsera y
> vuelve a entrar."* No se cambió el comportamiento del cálculo (seguir cayendo al respaldo sedentario
> sigue siendo lo correcto, ver §1.3) — solo se dejó de ocultarlo.

Cuando Health Connect no devuelve nada, `hasData = false`, el cliente manda `avgActiveKcal = null` y el
gasto cae a `BMR × 1,2`. Para alguien que quema 700 kcal activas eso son **~300 kcal/día de recorte
invisible**, y **no hay ningún aviso**: `wearableEnergy` se asigna en `NutritionScreen.kt:145` y **no se
lee nunca más** en toda la pantalla (variable efectivamente muerta).

El KDoc de `WearableEnergy.hasData` dice que distinguirlo de "0 kcal" sirve para *"evitar presentar un
objetivo basado en un cero que en realidad significa 'no lo sé'"*. La distinción se calcula
correctamente y luego **se tira**. El usuario ve un objetivo más bajo sin explicación — que es
exactamente el "déficit encubierto" que §1.3 dice haber resuelto, y el síntoma original que motivó toda
esta línea de trabajo.

---

## 🟠 AUD-15 (MEDIO) — `avgActiveKcal` y `carryOverKcal` sin validar

`server/.../routes/ProfileRoute.kt:92-93` los lee con `toDoubleOrNull() ?: 0.0`, **sin rango y sin
`isFinite`**, en contraste con el resto del endpoint, que sí valida peso/altura/edad/sexo/goal/ritmo
(líneas 137-157).

| entrada | resultado |
|---|---|
| `carryOverKcal=-3000` | objetivo **−1010 kcal**, grasa **−17 g**, macros suman −153 |
| `carryOverKcal=NaN` | `NaN.roundToInt()` lanza `IllegalArgumentException` → 500 |
| `carryOverKcal=Infinity` | `roundToInt()` satura → objetivo 2 147 483 647 kcal |
| `avgActiveKcal=50000` | TDEE 51805, objetivo 51390 kcal |

`String.toDoubleOrNull()` **acepta** `"NaN"` e `"Infinity"`, y `macrosFor` no tiene ninguna guarda para
`targetKcal` negativo (`fatKcal = targetKcal * 0.25` sale negativo y las ramas correctoras no se
disparan).

**Los endpoints exigen sesión**, así que no es una vulnerabilidad: el único que puede mandar esto es el
propio autor o un bug del cliente (p. ej. una pulsera que devuelva basura). Sigue importando porque es la
misma puerta que debería acotar AUD-1. Ver el hallazgo 15 de [03-seguridad.md](03-seguridad.md).

---

## 🟠 AUD-16 (MEDIO, condicional) — Falta la migración `ALTER TABLE` de `has_wearable`

La columna entró en `dc1b216` metida **solo dentro del `CREATE TABLE IF NOT EXISTS`**, que por definición
no se ejecuta sobre una tabla que ya existe; la tabla se creó en `25cf3cc` sin ella. `adaptive_tdee`,
`adaptive_updated_at` y `waist_cm` **sí** tienen su `runCatching { ALTER TABLE ... ADD COLUMN }` —
`initWeightHistoryTable` incluso tiene un comentario que explica exactamente este problema.

Si la BBDD desplegada es anterior a `dc1b216`: `no such column: has_wearable` → `TursoException` →
**500 en todo `POST /profile/save`**, y como `WeightScreen` registra el peso por esa misma ruta, el
registro de peso cae con él. Rotura total y silenciosa en upgrade.

> ### ✅ Resuelto el 2026-11-22 — gravedad rebajada a 🟡
>
> Consultado el esquema real con `PRAGMA table_info(user_profiles)` contra la Turso de producción (con
> permiso del usuario; solo lectura). **La columna `has_wearable` existe**, así que el guardado de perfil
> y el registro de peso **no están rotos**.
>
> Pero aparece **después** de `updated_at`, mientras que el `CREATE TABLE` la declara **antes**. En SQLite
> eso solo ocurre con un `ALTER TABLE ADD COLUMN`, que la añade al final — y en el repo **no existe ningún
> `ALTER` para ella**. El historial lo explica: en `dc1b216` la columna se usaba en el `INSERT` **sin
> estar en el `CREATE TABLE`** (se añadió ahí mucho más tarde, en `eff1e2a`). Es decir, el guardado se
> rompió en su día y **la columna se añadió a mano en Turso**; nunca hubo migración en el código.
>
> Riesgo residual casi nulo: una BBDD creada hoy sí tiene la columna, y la desplegada ya está parcheada.
> El único escenario malo que queda es restaurar un backup anterior a `dc1b216`.
>
> **Decisión**: en vez de añadir un `ALTER` para una columna muerta, **se quita `has_wearable` del
> `INSERT`** (y el campo `hasWearable` de `ProfileRequest`), porque nada la lee. Eso cierra el hallazgo y
> elimina la causa en lugar del síntoma.

Agravante independiente: `has_wearable` **se escribe y nunca se lee** — ningún `SELECT` del repo la
menciona. Es una columna que solo puede causar daño; la alternativa al `ALTER` es quitarla del `INSERT`.
En el mismo saco, el campo `hasWearable` de `ProfileRequest` se declara y nunca se usa (el servidor
deriva `has_wearable` de `activityLevel == "wearable"`), y la lógica del cliente que lo calcula es
incorrecta: `hasWearable = steps != null` es **siempre `true`** porque `getTodaySteps()` devuelve `0L`,
no `null`, incluso sin Health Connect — por eso el desplegable ofrece "🏃 Pulsera (automático)" en un
escritorio sin pulsera.

---

## 🟡 AUD-17 a AUD-24 (BAJO) — todos RESUELTOS (2026-11-23)

| ID | Hallazgo | Resolución |
|---|---|---|
| AUD-17 | **Macros degenerados sin señal**: con objetivo bajo y peso alto la proteína llega al **85 % de las kcal** y los carbohidratos a **0 g** (200 kg/1600 kcal → 340 P / 0 C / 27 G), y la rama `proteinKcal = max(0, targetKcal - fatKcal)` la degrada a **1,49 g/kg** —por debajo de la meseta de 1,6 de Morton 2018 que cita §6.1— **sin devolver ninguna bandera**, al contrario de lo que sí se hizo con `rateLimited`. El reparto siempre cuadra (eso está bien resuelto y hay test), pero **cuadrar no es ser comestible**. Perfil inalcanzable para el usuario actual (75,5 kg / 1800 kcal → 34 % de kcal, 2,0 g/kg, sano). | ✅ `Macros.degraded` nuevo, true cuando el reparto ideal (proteína por kg + 25% de grasa) no cabía en el objetivo y hubo que recortar. Propagado a `TargetResult.macrosDegraded` y a un aviso en `NutritionScreen`. Tests con el caso 200kg/1600kcal (degradado) y el perfil real del usuario (no degradado). |
| AUD-18 | **La migración SQL *sube* el ritmo**, contradiciendo su propio comentario ("solo limita la magnitud máxima… no se sube nada hacia arriba") y §3.2 ("por debajo de la banda: permitido y sin aviso"): `MIN(-0.05, goal_rate)` convierte −0,02 en −0,05. Magnitud ínfima (≈25 kcal/d), pero corre en **cada arranque** del servidor. `NutritionMath.clampRate` sí respeta el contrato; la divergencia es solo del SQL. | ✅ Reescrito a `-MIN(1.0, ABS(goal_rate))` / `MIN(0.5, ABS(goal_rate))`, que reproduce exactamente `clampRate`: acota por arriba, nunca sube una magnitud válida, y de paso corrige cualquier fila con el signo equivocado. |
| AUD-19 | **`resolvedAge` es código muerto por desajuste de formato**: el cliente guarda 8 dígitos (`"01012000"`, `ProfileScreen.kt:253`, con las barras solo como `VisualTransformation`), el servidor parsea `"dd/MM/yyyy"` (`ProfileRoute.kt:159-170`) → siempre lanza → `catch { req.age }`. El mecanismo que debía mantener la edad autoritativa **nunca se ejecuta**; la edad solo cambia si se teclea a mano (5 kcal/año de BMR, ~7,75 de TDEE). **Nota**: esto *desactiva* el riesgo teórico de `birthDate` sin validar (edades de 126 años o negativas), porque el formato que lo explotaría nunca llega a parsear — los dos hallazgos se anulan mutuamente. | ✅ Formatter cambiado a `"ddMMyyyy"` (el formato real del cliente). Como esto reactiva el riesgo que estaba dormido por el mismo bug, la edad resuelta se valida contra `14..100` antes de usarla: si no cuadra, se descarta y cae a `req.age` en vez de persistir una edad absurda. |
| AUD-20 | **`rateLimited` sin banda muerta**: en `light` con el ritmo por defecto el recorte es de −0,750 % a −0,747 % y aun así dispara el aviso falso de AUD-2. Un clip numéricamente irrelevante produce un mensaje alarmante y falso. | ✅ Ya resuelto en el Bloque A (`RATE_LIMIT_DEADBAND_KCAL`). |
| AUD-21 | **Textos que contradicen el diseño**: `OnboardingScreen.kt:417` y `:432` prometen que la pulsera suma *"en tiempo real"* y *"automáticamente cada día"*, cuando §1.3 cierra con *"la interfaz **no debe prometer tiempo real**"* y el diseño hace lo contrario a propósito (media de días completos). Y la caja etiquetada **"BMR"** con subtítulo "base sin actividad" (`OnboardingScreen.kt:394-400`, `ProfileScreen.kt:660-663`) muestra `targets.tdee`: **2166 para un BMR de 1805**. El campo `TargetResult.bmr` existe, está en el payload y en el modelo cliente, y no se usa. | ✅ "Tiempo real" ya se quitó en el Bloque A. La caja "BMR" ahora muestra `result.bmr`/`targets.bmr` en vez de `tdee` en ambas pantallas, con `sub` ajustado a "sin actividad". |
| AUD-22 | `defaultRateFor(GOAL_GAIN) = 0.375` **fuera de la rejilla del deslizador**: `steps = ((0.5−0.05)/0.05).roundToInt() − 1 = 8` → 10 posiciones de 0,05 en 0,05. Al tocar el deslizador el valor salta a 0,35 o 0,40 y ya no se puede recuperar (±20 kcal/d). Solo falla `gain`; −0,75 y −0,15 sí están en rejilla. | ✅ Ya resuelto en el Bloque A (el valor se encaja en la rejilla con `roundToInt()` sobre el paso). |
| AUD-23 | `formatOneDecimal`/`formatTwoDecimals` **pierden el signo para \|valor\| < 1**: `(-0.4 * 10).roundToInt() = -4`, y la división entera trunca hacia cero → `-4 / 10 == 0` → `"0.4"`. Funciona para `\|valor\| ≥ 1`, así que falla justo en el tramo donde viven todos los ritmos. Latente: todos los llamadores pasan `abs()` y ponen el signo a mano, pero el KDoc no advierte nada y el test solo usa positivos. | ✅ Reescrito para trabajar con la magnitud y anteponer el signo aparte. Tests con `-0.4`, `-0.05`, y los casos `|valor| ≥ 1` que ya funcionaban, para no perder cobertura. |
| AUD-24 | **`GET /profile/adaptive` escribe en BBDD sin transacción** (y es un GET con efectos secundarios): la secuencia `getProfile → cooldown → reconcile → saveAdaptiveTdee` no es atómica, así que dos llamadas concurrentes pueden aplicar la mezcla dos veces — justo contra lo que avisa el comentario de `ProfileService.kt:266-273`, y el disparador es automático al entrar en la pantalla. Y `upsertProfile` tampoco es atómico: `INSERT OR REPLACE` borra `adaptive_tdee` y una **segunda** llamada lo restaura; si esa segunda falla, se pierde sin rastro después de haber escrito ya el perfil. | ✅ Tres piezas: (1) la ruta pasa de `GET` a `POST`, cliente y servidor; (2) `runAdaptiveAdjustment` serializado con un `Mutex` por usuario; (3) `upsertProfile` combina el `INSERT OR REPLACE` y la restauración del autoajuste en un único `executeBatch` envuelto en `BEGIN IMMEDIATE`/`COMMIT` — verificado que Turso acepta `BEGIN`/`COMMIT` dentro del pipeline con una llamada de prueba real (solo lectura) antes de confiar en ello. |

---

## Lo que la auditoría declara FALSO o exagerado

Para no gastar esfuerzo arreglando lo que no está roto:

- **"Objetivo de 576 kcal" (red team): INFLADO.** `HealthConnectProvider.kt:102` hace
  `yesterdayActiveKcal = perDay.firstOrNull { it.first == yesterday }?.second ?: average`, así que **si
  ayer no hay dato el arrastre sale 0 por diseño**. Es una salvaguarda buena y bien comentada que el red
  team pasó por alto. La magnitud realista del arrastre es −200 a −500, que da los 1096-1504 kcal sí
  verificados. El mecanismo sin tope es real; el titular no es la cifra a citar.
- **"Doble conteo del 7 % en el arrastre": FALSO.** Si la media incluye ayer,
  `y − avg = 13(y−m)/14`: el arrastre sale **atenuado al 93 %**, no amplificado. Es conservador.
- **"El `NaN` es CRÍTICO": EXAGERADO.** Endpoints autenticados por sesión, sin vector externo. Es
  robustez, no vulnerabilidad (ver AUD-15).
- **"15 kcal/año de BMR por edad": NUMÉRICAMENTE FALSO.** El coeficiente de Mifflin es **5** kcal/año.
- **"Falta un peso objetivo": MAL ENCUADRADO.** Ver AUD-8: añadir peso objetivo con fecha sería un
  retroceso conceptual. Lo que falta es condición de parada y guardia por IMC.
- **Riesgo de conducta alimentaria desordenada**: los mecanismos están demostrados, pero el perfil que
  los dispara (mujer de 45 kg, IMC 17, menor de edad) no existe en una app de un solo usuario varón en
  IMC 22,3. Legítimo como crítica de diseño, no como riesgo vivo.
- **"`NutritionMath` debería vivir solo en el servidor": REFUTADO.** El onboarding necesita calcular antes
  de que exista perfil, y la alternativa (endpoint de previsualización sin sesión) tiene su propio coste y
  su propia superficie.

**Ocho ataques quedaron refutados por el propio red team** y confirmados por el evaluador: la
autocancelación del subregistro uniforme, la ausencia de doble conteo BMR↔activas, el uso del peso actual
en el %, la corrección real del bug del suelo original, los macros que ya cuadran, la seguridad del
discriminador `ABS(goal_rate) > 10`, el desbloqueo de ritmos lentos, y el ritmo en % frente a kg fijos.

---

## Lo que NO hay que tocar

- **El ritmo en % de peso corporal y toda §3.1.** Es la mejor decisión del proyecto y la que más apps
  fallan.
- **`NutritionMath` en `shared` como única fuente.** Elimina de verdad la triplicación anterior; no queda
  ninguna copia residual de la fórmula.
- **No persistir `targetKcal`, recalcularlo.** Cierra de raíz toda una clase de bugs de invalidación. Es
  la razón de que AUD-3 destaque: es la única excepción a una regla por lo demás correcta.
- **`describeRate` / `RateInfo` con todas las etiquetas derivadas del mismo número.** La etiqueta ya no
  puede mentir sobre el ritmo.
- **La cascada de macros proteína → grasa → carbohidratos como resto**, con la garantía de que siempre
  cuadran. Orden correcto y propiedad verificada por test.
- **`ActiveCaloriesBurnedRecord` y la advertencia contra `TotalCaloriesBurnedRecord`.** Correcto contra la
  documentación literal de Health Connect, que es el único punto verificable sin margen de opinión.
- **`aggregate()` / `aggregateGroupByPeriod()` en vez de `readRecords()`.** Resuelve deduplicación entre
  fuentes y paginación a la vez.
- **Regresión por mínimos cuadrados** para la tendencia de peso, con la guarda `den == 0.0`.
- **`yesterdayActiveKcal ?: average`** — la salvaguarda que hace que un día sin datos dé arrastre 0.
- **`hasData` distinguido de "0 kcal"** y el fallback a sedentario en lugar de `BMR + 0`. Modelado
  correcto de "no lo sé" (aunque luego no se comunique: AUD-14).
- **La media de días completos, no la lectura en vivo**, con medición propia en dispositivo que la
  justifica (56/54/37 kcal).
- **El cooldown de 7 días del autoajuste** y el razonamiento de por qué la mezcla 0,3/0,7 se degradaría
  sin él.
- **El registro comunicativo de `AdaptiveCard`** ("ajustado según tu progreso real", nunca
  "científicamente calculado"). Es un requisito que casi ninguna app cumple.
- **Banda recomendada ≠ tope duro**, permitiendo ritmos lentos.
- **El discriminador `ABS(goal_rate) > 10`** de la migración kcal→%: rangos sin solape, idempotente.
- **`goalsFailed`** en `NutritionScreen`: no confundir "cargando" con "falló".
- **La honestidad epistémica de [10-fundamento-cientifico.md](10-fundamento-cientifico.md)** (marcas
  ✅/🟡/⚠️, §12, la confesión de §5.4). Contrastadas las constantes del código contra las tablas del
  documento: **coinciden todas**. Las divergencias son de *comportamiento*, no de números.

---

## Cumplimiento de la especificación de referencia

El agente investigador produjo 44 requisitos verificables (R1-R44) a partir de fuentes primarias:
FAO/WHO/UNU 2004, Health Connect, NIH Body Weight Planner, guías de Apple/Play, Mifflin 1990,
Frankenfield 2005, Garthe 2011, Helms 2014, Iraki 2019, Hall 2008/2011/2013, AMDR del IOM.

**Cumple (16)**: R1, R2, R6, R7, R9, R12, R14 (salvo con `adaptive_tdee` activo), R15, R16, R17, R30,
R31, R34, R37, R40, y R44 en la capa de datos (el payload expone `bmr`, `tdee`, `rateLimited`,
`requestedRate`, `effectiveRate`, `tdeeIsMeasured`).

**Incumple (16)**: **R19, R20, R21, R22** (suelo absoluto, déficit máximo, nunca <800, nunca bajo el BMR
— las cuatro caen con AUD-1/AUD-2) · **R5** (el PAL 1,2 queda por debajo del mínimo 1,40 del rango
sedentario de FAO/WHO/UNU) · R8 (sin mínimo de días de pulsera) · R10 (ninguna incertidumbre comunicada)
· R18 (recomp no implementada) · R23/R24/R27 (sin guardia por IMC ni condición de parada) · **R35** (dos
bucles de realimentación acoplados sobre el mismo parámetro) · R36 (sin periodos anómalos) · R38/R39
(proteína por peso total, grasa mínima al 15 % frente al 20 % del AMDR) · R43 (NaN/Infinity alcanzables).

**Parcial (5)**: R4 (el documento reconoce que los multiplicadores son convención, pero **no** señala que
1,2 contradice el rango sedentario oficial de FAO) · R11 (zonas horarias frágiles: `ZoneId.systemDefault()`
y fronteras de día que se mueven al viajar) · R32/R33 (mínimos por debajo de los propios, sin tope por
paso del valor mezclado) · R42 (redondea una vez al final ✅, pero el redondeo de macros puede violar
mínimos).

**No aplican a este caso (7)**: R25 (embarazo/lactancia) · R26 (menores) · R28/R29 (políticas de tienda:
no publicada) · R41 (no hay conversión de unidades, todo en kg/cm) · R13 (no hay proyecciones que
degradar) · R3 (no se usa % de grasa para el BMR).

**Requisitos del investigador mal aplicados a este caso**: R23 y R27 presuponen un modelo de
peso-objetivo-con-fecha que esta app rechaza deliberadamente y con razón (ver AUD-8); la necesidad real
que cubren es legítima pero debe formularse como guardia por IMC. R2/R16 (Ten-Haaf para atletas, meta-
análisis O'Neill 2023) es excesivo: el autor no es atleta de élite y Mifflin es el estándar correcto para
él. Y R19 en su versión "1200/1500 kcal" es **precisamente el suelo que §7.1 descarta con buen criterio**;
lo defendible es el suelo duro de 1000 kcal del NIH Body Weight Planner combinado con el marco de
disponibilidad energética — que es la decisión que se tomó.

---

## Dato que debería gobernar toda la comunicación de esta funcionalidad

El error estándar **publicado** del BMR estimado es de **110-170 kcal/día** (ecuaciones de Schofield,
tabla 5.2 de FAO/WHO/UNU 2004, [fuente primaria abierta](https://www.fao.org/4/y5686e/y5686e07.htm)), y
un déficit de 0,25 kg/semana son ~275 kcal/día.

**La incertidumbre del punto de partida es del mismo orden que el efecto buscado.** Súmese el ±9 % por el
ancho de la categoría PAL y el 15-30 % de error documentado de los wearables para gasto energético, y la
incertidumbre del TDEE estimado a priori ronda ±300-500 kcal/día.

La app muestra "1624 kcal" con cuatro cifras significativas. **Mostrar bandas** ("1550-1700") es la mejora
de honestidad con mejor relación coste/beneficio que no está hecha, y el documento ya tiene toda la
munición para justificarla.

---

## Plan de arreglo acordado con el usuario (2026-11-22)

**Paso 0 — verificación de 30 segundos (pendiente del usuario)**: ¿guarda perfil la app en producción?
Decide si AUD-16 es latente o urgente. Ver AUD-16.

**Bloque A — deshacer el error de razonamiento de §7.3.** Lo único que urge, porque los cuatro hallazgos
grandes son el mismo error y se arreglan juntos:

1. Suelo por **disponibilidad energética** (≥30 kcal/kg MM/día con MM estimada por población), aplicado
   **al final**, sobre el número que se muestra.
2. `carryOverKcal` **dentro del recorte** y **condicionado a `activityLevel == "wearable"`**.
3. Validar `avgActiveKcal` y `carryOverKcal` en `ProfileRoute` (rango e `isFinite`).
4. **Pasar el TDEE a `RateSlider`** y derivar el extremo como
   `min(maxRateFor(goal), rateForDailyDelta(0.25 × TDEE, peso))`.
5. **Tope de superávit** simétrico, con `rateLimited` funcionando también en `gain_muscle`.
6. Corregir los dos textos del BMR (con (4) hecho, el aviso casi no debería aparecer).
7. **Añadir `carryOverKcal` y `measuredTdee` al barrido de tests** — esto es lo que impide que vuelva a
   pasar.

Todo es `shared/` y `server/`, así que se compila y se testea sin Android SDK.

**Bloque B — el TDEE adaptativo**: componer en vez de sustituir · invalidar/re-anclar al cambiar
`activityLevel` o `goal` · pasar `avgActiveKcal` al `previousTdee` del `reconcile` · arreglar el signo de
`AdaptiveCard`.

**✅ Bloque C — calidad del dato (RESUELTO, 2026-11-23)**: umbral de "día válido" (AUD-10) · cobertura
mínima de la ventana (AUD-9/10) · `ADAPTIVE_MIN_SPAN_DAYS`/`WEIGH_INS` subidos a lo que exige §5.3
(AUD-9) · mínimo de días en la media de la pulsera (AUD-13, ⚠️ sin build real) · escribir en
`weight_history` siempre (AUD-12). Detalle en cada hallazgo más arriba. Verificado con `:shared:jvmTest`
(43 tests), `:server:compileKotlin`, `:composeApp:compileKotlinJvm`.

**✅ Bloque D — honestidad de la interfaz (RESUELTO, 2026-11-23)**: `NutritionMath.targetBand` (banda de
incertidumbre, ±150 kcal por fórmula / ±75 medido, redondeada a 25) mostrada en Onboarding y Perfil en
vez del número exacto · etiqueta "BMR" corregida para mostrar `result.bmr`/`targets.bmr` en las dos
pantallas (AUD-21) · aviso de pulsera sin datos en `NutritionScreen` (AUD-14) · señal de macros
degradados (AUD-17) · guardia por IMC con condición de parada, `TargetResult.lowBmiGuard` (AUD-8).
Verificado con `:shared:jvmTest` (49 tests), `:server:compileKotlin`, `:composeApp:compileKotlinJvm`.

**✅ Bloque E — los 🟡 (RESUELTO, 2026-11-23)**: AUD-18 (migración SQL reescrita como `clampRate`),
AUD-19 (formato `ddMMyyyy` + validación de rango en la edad resuelta), AUD-23 (signo en
`formatOneDecimal`/`formatTwoDecimals`), AUD-24 (ruta a `POST`, `Mutex` por usuario, `upsertProfile`
atómico con `BEGIN`/`COMMIT` — verificado contra Turso real antes de confiar en ello). Detalle de cada
uno en la tabla de AUD-17 a AUD-24 más arriba.

**Los cinco bloques de la auditoría están completos.** Quedan solo AUD-11 (sin snapshot/`calcVersion`
del objetivo histórico) y el ángulo del ciclo menstrual mencionado en AUD-9, que nunca entraron en el
plan de bloques A-E — son cambios de arquitectura mayores, no arreglos puntuales, y no se han tocado.
