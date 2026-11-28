# Auditoría del comité multidisciplinar sobre el cálculo energético (2026-11-27)

Auditoría pedida por el usuario con un comité de siete roles (endocrinólogo, nutricionista clínico,
fisiólogo del ejercicio, especialista en composición corporal, investigador de gasto energético,
científico de datos, auditor crítico independiente), trabajados como tres paneles de dos expertos en
debate + esta síntesis final de contraste. **Solo diagnóstico: no se ha tocado ni una línea de código.**

Es la **segunda** auditoría de esta línea de trabajo — la primera es
[11-auditoria-kcal.md](11-auditoria-kcal.md) (2026-11-22/23, casi todo ✅ resuelto). Esta no repite lo
ya cerrado: parte de ese código ya corregido y busca lo que quedó fuera de su alcance — sobre todo
criterio clínico en poblaciones de borde y sesgos fisiológicos finos del autoajuste.

Alcance de los tres paneles:
- **P1** — Metabolismo y gasto energético (BMR, TDEE, multiplicadores, modo pulsera, densidad
  energética, adaptación metabólica).
- **P2** — Calorías y macros por objetivo (ritmos, suelo de seguridad, reparto de macros,
  individualización por edad/sexo/composición).
- **P3** — Seguimiento del peso y autoajuste (interpretación de fluctuaciones, algoritmo de
  reconciliación, calidad estadística).

Perfil de referencia usado por los tres paneles salvo que se diga lo contrario: **hombre 75 kg/178
cm/30 años**, BMR 1718, TDEE moderado 2662.

Leyenda: 🔴 crítico/alto · 🟠 medio · 🟡 bajo.

---

## Veredicto de fondo (sin cambios respecto a la primera auditoría)

El modelo sigue siendo correcto en sus decisiones de fondo, y los tres paneles lo confirman de forma
independiente:

- Mifflin-St Jeor como fórmula por defecto, con el factor adaptativo **componiéndose** con ella en vez
  de sustituirla (AUD-3 de la auditoría anterior sigue bien resuelto).
- Ritmo en % de peso corporal, no en kcal fijas ni en fecha-objetivo.
- Tope de déficit y de superávit como fracción del gasto, con un suelo por disponibilidad energética
  que nunca empuja por encima del mantenimiento.
- Pendiente de peso por mínimos cuadrados en el autoajuste, con mínimos de cobertura y un enfriamiento
  semanal — nada de "último pesaje menos primero".
- El factor adaptativo absorbe el infraregistro de comidas sin entrar en espiral: simulado con
  Montecarlo, un 20% de infraregistro constante converge a un factor estable (0,76), no a cero.

Lo que han encontrado los tres paneles **no son errores de aritmética** (el barrido de ~30.000 perfiles
del panel 2 no encontró ninguno) sino tres familias de problema:

1. Una **incoherencia interna real**, cuantificable y corregible con una línea: el modo pulsera no
   aplica el mismo ajuste por TEF que el propio código documenta en otro archivo.
2. **Falta de individualización en los extremos** de la población: mayores pequeños, adolescentes,
   obesidad severa. El modelo central (adultos sanos de peso medio) funciona bien; en los bordes, no.
3. **Sesgos fisiológicos conocidos** que el autoajuste no filtra: glucógeno/agua al cambiar de objetivo,
   ciclo menstrual, ventanas de datos mal alineadas.

---

## ✅ C-1 (CRÍTICO) — Modo pulsera: falta el TEF — RESUELTO (Fase 4, 2026-11-28)

> Implementado como se propone abajo, más tres cosas que la propuesta no contemplaba:
>
> 1. **`TEF_FRACTION` se movió a `NutritionMath`**, porque tener dos archivos opinando sobre el TEF es
>    lo que permitió el fallo. Con un solo dueño no puede reabrirse.
> 2. **La migración NO puede usar un centinela en `user_profiles`** (que era el plan): `INSERT OR
>    REPLACE` lo borraría en el primer guardado de perfil y el reseteo volvería a dispararse, matando un
>    factor legítimo. Se hizo con una tabla `schema_migrations` propia y un `applyOnce` transaccional,
>    validado contra SQLite local (reseteo → nueva medición → segundo arranque en frío → el factor
>    sobrevive).
> 3. **El suelo `bmr × 1,2` se aplica también en esta rama** (P1-2), que era la única sin él.
>
> **Verificación independiente de las cifras del informe**: con el perfil real del usuario y 450 kcal
> activas, el objetivo viejo de ganar (2566) era un superávit real de +60 kcal frente al gasto verdadero
> (2505) en vez de los +311 pedidos, y el de perder (1691) era un déficit real del 32,5 % informado como
> 25 %. Las dos cifras del informe se reproducen exactamente por un camino de cálculo distinto.


**De dónde viene**: P1-1, confirmado sin discrepancia por los dos expertos del panel 1 tras debatirlo.

`NutritionMath.kt:97-108` calcula el gasto en modo pulsera como `bmr + avgActiveKcal`. Pero
`ActivityBenchmarks.kt` y el propio `doc/10-fundamento-cientifico.md` §16.2 afirman, para calcular la
cifra de referencia que se le muestra al usuario, que el PAL (y por tanto el modo fórmula) **incluye un
~10% de efecto térmico de los alimentos (TEF)** y que las "calorías activas" de una pulsera **no lo
incluyen**. El código resta ese 10% para generar el número de referencia y luego no lo vuelve a sumar
al construir el gasto real del modo pulsera. Es una afirmación que el propio proyecto hace en un sitio y
contradice en otro.

**Magnitud** (perfil de referencia, misma actividad real): modo pulsera da 2396 kcal frente a 2662 del
modo fórmula — **10% menos de gasto para la misma persona haciendo lo mismo**.

- En **pérdida de grasa** (−0,75%/sem): el tope del 25% se aplica sobre el gasto ya bajo, y el déficit
  real sube al 32,5% — por encima del tope que el sistema dice respetar.
- En **ganancia de músculo** (+0,375%/sem): el superávit nominal de +43 kcal es, en la práctica,
  mantenimiento. **Es exactamente el patrón que motivó toda esta línea de auditorías** ("quiero ganar
  peso y no gano" → déficit encubierto), ahora localizado en el modo pulsera en vez de en el modo
  fórmula.

**Por qué es crítico y no solo una incoherencia documental**: el sesgo del dispositivo wearable (que
tiende a infraestimar, según O'Driscoll 2020, BJSM) va **en la misma dirección** que el TEF que falta:
no se cancelan, se suman.

**Propuesta** (acordada en el debate del panel, con el matiz del endocrinólogo incorporado):

```kotlin
if (avgActiveKcal > 0) (bmr + avgActiveKcal) / (1.0 - ActivityBenchmarks.TEF_FRACTION)
else                   max(bmr * activityMultiplier("sedentary"), bmr * 1.0) // ya existe
```

y aplicar también aquí el suelo `max(.., bmr * 1.2)` que ya existe en la rama de gasto *medido*
(`measuredFloor`, línea 464) pero no en esta rama de fórmula-pulsera — hoy, un sedentario cuya pulsera
dé pocas kcal activas puede caer **por debajo** del propio respaldo "sin datos" que la rama de al lado
usa como mínimo (−193 kcal/día, P1-2, mismo hallazgo con menor severidad).

**⚠️ Antes de desplegar**: cualquier `adaptive_factor` ya guardado en producción puede haber absorbido
parcialmente este sesgo (compensándolo con un factor >1 sin que nadie lo supiera). Aplicar la corrección
sin tocar los factores guardados los duplicaría. Esto es una decisión de migración de datos, no solo de
código, y necesita tu confirmación explícita antes de tocar la BBDD — por ahora solo lo documentamos.

---

## ✅ C-2 (CRÍTICO) — No hay suelo absoluto en kcal — RESUELTO (Fase 1, 2026-11-28)

> **Resuelto en parte, y conviene saber en qué parte.** Se añadió `ABSOLUTE_FLOOR_KCAL = 800` (suelo
> duro) y el aviso `belowSoftFloor`/`softFloorKcal` (1200♀/1500♂, no bloqueante), más la corrección de
> `energyFloor` para que informe del suelo aplicado y no del crudo, y la propagación del suelo nuevo a
> `effectiveMaxRateFor` para no reintroducir AUD-5.
>
> **Medido tras implementarlo**: el suelo absoluto solo muerde con masa magra estimada por debajo de
> ~26,7 kg (mujeres), así que **los dos casos que motivaron el hallazgo quedan por encima de él** (806 y
> 866 kcal los sigue gobernando la disponibilidad energética). Lo que esos perfiles reciben ahora es el
> **aviso**, no un objetivo distinto. La causa de fondo de esos objetivos bajos sigue abierta y está en
> el Bloque B: el suelo de disponibilidad energética no resta el gasto de ejercicio (P2-5) y
> `lowBmiGuard` usa un umbral de adulto también en mayores (C-3b).


**De dónde viene**: P2-1.

El suelo de seguridad actual (`energyAvailabilityFloor`) es proporcional a la masa magra estimada, y
esa estimación baja con la edad (Deurenberg suma +0,23%/año de grasa). El resultado: mujeres mayores de
complexión pequeña con **normopeso** pueden recibir un objetivo por debajo de 800 kcal/día — rango de
dieta de muy bajas calorías (VLCD), que las guías clínicas (NICE CG189) reservan a supervisión médica.

**Ejemplos del barrido** (IMC normal en los dos casos, no obesidad):
- Mujer 80 años, 42 kg/148 cm (IMC 19,2): **806 kcal**.
- Mujer 75 años, 45 kg/150 cm (IMC 20): **866 kcal**.

**Propuesta** (coincide el nutricionista y el especialista en composición, con el matiz de que uno
pedía un suelo único y el otro distinguió "duro" de "aviso"):
- Suelo **duro** (bloqueante, nunca por debajo): 800 kcal.
- Aviso **no bloqueante** por debajo de 1200 kcal (mujeres) / 1500 kcal (hombres) — criterio de
  suficiencia de micronutrientes (AHA/ACC/TOS 2013), un marco distinto al de disponibilidad energética
  que ya existe, y que el código actual no cubre.
- Test de barrido que exija `targetKcal ≥ 800` para cualquier perfil válido.

---

## 🟠 C-3 (ALTO, consolidado) — Sin salvaguardas específicas de edad en los extremos

**De dónde viene**: P1-5, P2-2 y P2-3 — los tres paneles llegaron al mismo área de forma independiente,
así que se consolidan aquí en un único hallazgo con dos caras.

**(a) Adolescentes** (`ProfileRoute.kt` acepta edad 14-100; `NutritionMath.kt` no distingue):
Mifflin-St Jeor está validada en adultos de 19-78 años. Una chica de 14 años, 45 kg/155 cm (IMC 18,7,
normal para su edad) recibe hoy un objetivo con **24,5% de déficit real**, sin ningún aviso de que el
crecimiento adolescente necesita un criterio distinto (FAO/WHO/UNU 2004 usa ecuaciones separadas para
10-18 años; la AAP 2023 no avala dietas hipocalóricas en adolescentes sin supervisión).

**(b) Mayores**: la guardia de bajo peso usa IMC < 18,5 para todas las edades, pero el umbral clínico de
desnutrición en ≥70 años es IMC < 22 (GLIM/ESPEN), y 20 entre 65-69. Las dos mujeres mayores del
hallazgo C-2 (IMC 19-20) reciben hoy déficit pleno porque no caen bajo el umbral de adulto.

**Propuesta mínima**:
- `lowBmiGuard`: usar 22 si edad ≥70, 20 si edad 65-69, 18,5 en el resto.
- Para edad <18: no bloquear el registro, pero sí capar el déficit disponible al 10% (o desactivar
  "perder grasa" con un aviso) hasta que exista una vía de supervisión — es una decisión de producto,
  no solo de cálculo, y queda señalada para que la decidas.

---

## ✅ C-4 (ALTO) — La proteína se calcula sobre peso total también en obesidad — RESUELTO (Fase 2, 2026-11-28)

> **Resuelto, pero no como proponía este informe.** El tope es por **masa magra**
> (`min(2,0 × peso, 2,6 × max(magra, 0,40 × peso))`), no por peso-a-IMC-25: el IMC no distingue músculo
> de grasa, y el tope por IMC recortaba 18 g a un hombre de 90 kg/180 cm que es el usuario objetivo de la
> app, no un obeso. El 2,6 sale de hacer explícito el supuesto de composición que los 2,0 g/kg ya
> llevaban dentro, así que cae dentro de Helms por construcción. Resultados: 160 kg 320 → 184 g,
> 150 kg → 156 g, 90 kg musculado → 178 g, usuario real sin cambio.
>
> **Las dos banderas que este informe pedía añadir a `macrosDegraded` se descartaron tras medirlas**: el
> techo del 35% de kcal marca como degradado el perfil real del usuario (37,2% con un corte normal,
> porque la fracción de kcal mide el déficit y no la proteína) y el mínimo de 130 g de carbohidratos da
> falso positivo en cuerpos pequeños. Y sobre todo, ya no hay patología que detectar: el tope aguas
> arriba la impide. El invariante de barrido "nunca más de 3,1 g/kg de masa magra" sustituye a la bandera.


**De dónde viene**: P2-4.

`macrosFor` usa `weightKg * 2.0` (déficit) o `* 1.8` g/kg sobre el **peso total**, sin tope. En obesidad
severa esto da cifras que no son un reparto razonable aunque "cuadren":

- Hombre 160 kg → **320 g de proteína, el 57% de las kcal**, 102 g de carbohidratos.
- Hombre 300 kg → 600 g de proteína (68% de las kcal), 65 g de carbohidratos.

Las guías de referencia (ISSN 2017, ASPEN) coinciden en que en obesidad la proteína se calcula sobre
**peso objetivo o ajustado**, no sobre el peso real. El banderín `macrosDegraded` que ya existe (AUD-17
de la auditoría anterior) **no se activa** en estos casos porque los carbohidratos siguen siendo
positivos — detecta cuando el reparto deja de cuadrar, no cuando deja de ser razonable.

**Propuesta** (acordada en el debate, eligiendo la opción más simple de las dos que se discutieron):

```kotlin
val proteinBaseKg = min(weightKg, 25.0 * (heightCm / 100.0).let { it * it })
```

y ampliar `degraded` para que también salte si la proteína supera el 35% de las kcal o los carbohidratos
caen por debajo de 130 g.

---

## ✅ C-5 (ALTO) — El autoajuste lee el glucógeno/agua del cambio de objetivo como gasto — RESUELTO (Fase 5, 2026-11-28)

> Implementado con 14 días de gracia (no los 10 propuestos), columna `goal_changed_at` que solo mueven
> `goal` y `goalRate`, y `null` tratado como "sin gracia" para no castigar a los perfiles existentes. La
> aritmética de fechas se extrajo a `AdaptiveWindow` para poder testearla sin Turso (7 tests, con los
> dos bordes). Dos cosas que no estaban en el informe y aparecieron al integrarlo: la gracia tiene que
> comprobarse **antes** del enfriamiento (si no, el mensaje cambia a mitad de la espera), y la tarjeta
> del perfil daba el consejo "registra tus comidas" también durante la gracia, donde es falso — de ahí
> el flag `recalculating`.


**De dónde viene**: P1-3 y P3-1 — mismo fenómeno fisiológico visto desde dos ángulos (el endocrinólogo
lo acepta como limitación en la dirección de pérdida; el estadístico lo cuantifica y confirma que en
ganancia es igual de dañino), y la simulación Montecarlo del panel 3 lo mide con precisión.

Al empezar o cambiar un objetivo, 1-1,5 kg de glucógeno+agua se mueven con muy poca energía real
(Hall 2008). El autoajuste (`ProfileService.kt:436-467`) no excluye los primeros días tras un cambio de
objetivo, así que esa agua se interpreta como gasto:

- Simulado: una caída de 1,5 kg en la primera ventana de 28 días sesga el gasto medido en
  **+278 kcal/d**, y ese sesgo persiste atenuado varias semanas (+261 en la semana 4 con la fórmula ya
  errada un 10%, **peor que no ajustar nada**).
- En ganancia el signo se invierte (glucógeno y contenido intestinal suben con más carbohidratos): el
  superávit sale recortado tras varias mezclas, reproduciendo otra vez el síntoma "no gano".

**Propuesta** (ambos paneles convergen en la misma cifra, 10-14 días; se toma 14 por consistencia con
las otras constantes `ADAPTIVE_*` que ya usan ese número):

```kotlin
val ADAPTIVE_GOAL_CHANGE_GRACE_DAYS = 14
// weighIns e intake se filtran a partir de max(since, profile.goalChangedAt + 14 días)
```

Requiere una columna nueva `goal_changed_at`, actualizada al cambiar objetivo o ritmo (no al editar solo
peso/altura/edad). Con esto, la simulación baja el sesgo de la semana 4 de +261 a +186 (y a +7 si la
fórmula de partida ya era exacta).

---

## ✅ C-6 (MEDIO) — Pesos "arrastrados" se graban como pesajes reales — RESUELTO (Fase 3, 2026-11-28)

> **Resuelto, pero no con el arreglo que propone este informe.** La propuesta de abajo ("escribir solo
> si el valor difiere del último") habría deshecho AUD-12, que quitó esa condición a propósito porque
> eliminar los días de peso estable de la muestra infla la pendiente de la regresión. La distinción
> correcta no es *si el peso cambió* sino *si el usuario se ha pesado*: `ProfileRequest.recordWeighIn`,
> `true` en onboarding, guardado físico y alta de peso, `false` solo en el reguardado derivado de editar
> los datos personales. Pendiente de comprobar en el móvil (no hay forma de testear `upsertProfile` sin
> Turso real).


**De dónde viene**: P3-6.

Al guardar cualquier cambio de perfil (edad, altura, objetivo) se reenvía el último `weightKg` cargado,
y `upsertProfile` lo escribe en `weight_history` como el pesaje de **hoy**, aunque el usuario no se haya
pesado. Esto infla artificialmente el recuento de pesajes hacia el mínimo de 8 que exige el autoajuste,
y aplana la pendiente real con valores repetidos (LOCF) que no son información nueva.

Es un uso no previsto de la decisión tomada en AUD-12 de la auditoría anterior ("escribir aunque el
peso no cambie", pensado para "no ha cambiado pero sigue pesándose"), que ahora también cubre "no se ha
vuelto a pesar en absoluto".

**Propuesta**: escribir en `weight_history` solo desde el flujo explícito de "añadir peso", o solo
cuando el valor difiera del último registrado — no desde el guardado general de perfil.

---

## 🟡 C-7 (MEDIO) — Ventanas de datos del autoajuste mal alineadas

**De dónde viene**: P3-2 y P3-3, dos sesgos del mismo mecanismo.

1. La ventana de **ingesta** (28 días desde hoy, incluyendo el día de hoy a medias) no coincide con el
   tramo real que cubren los **pesajes**. Un día parcial de hoy con pocas kcal registradas aún (pero por
   encima del umbral) sesga el promedio a la baja.
2. El umbral fijo de "día válido" (`ADAPTIVE_MIN_DAY_KCAL = 1000`) excluye sistemáticamente días reales
   de objetivos bajos: con un objetivo de 1200 kcal y variabilidad normal de registro, hasta el 25% de
   los días "buenos" quedan fuera del cómputo, sesgando la media al alza y pudiendo bajar la cobertura
   por debajo del 80% exigido — el autoajuste deja de activarse nunca para ese perfil.

**Propuesta**:
- Acotar la ventana de ingesta al tramo `[primer pesaje, último pesaje]`, excluyendo el día de hoy.
- Umbral relativo: `minDayKcal = max(600.0, 0.6 * targetKcal)` en vez de una constante fija.

---

## 🟡 C-8 (BAJO, consolidado) — El peso que se ve y se usa es el crudo, no una tendencia

**De dónde viene**: P3-9 y P3-10.

`WeightScreen.kt` no calcula ninguna tendencia: muestra pesos crudos y un "+x kg vs. ayer" cuyo ruido día
a día (≈0,6 kg de desviación típica) es del mismo orden que un mes entero de pérdida al ritmo más suave
que ofrece la app — ese delta diario no comunica nada útil y puede alarmar sin motivo. El eje X además
se espacia por índice de registro, no por fecha, así que los huecos sin pesarse distorsionan la
pendiente visual. El mismo peso crudo es el que alimenta el BMR y el delta diario del día (impacto
pequeño, ±10-14 kcal/d, pero gratuito de corregir a la vez).

**Propuesta**: una tendencia EWMA simple (α≈0,1/día, como la popularizada por "Hacker's Diet" y usada
por apps como MacroFactor) como línea principal del gráfico, con los pesos crudos como puntos sueltos;
eje X por fecha real; usar esa misma tendencia (no el último crudo) para el BMR y el delta.

---

## 🟡 C-9 y menores (BAJO) — No bloquean nada hoy, quedan documentados

- **P1-4**: la densidad de 7700 kcal/kg tiene un sesgo pequeño y conocido según composición corporal
  (persona delgada en déficit: densidad real ≈6800; ganancia de tejido mixto: ≈7000-7500). Dentro del
  ruido que el autoajuste ya absorbe — **no tocar**.
- **P1-6**: el multiplicador "sedentario" (1,2) **no está mal**, está en el extremo conservador de las
  referencias de agua doblemente marcada (≈1,4-1,5 en vida libre). El `doc/10` lo describe como
  "convención sin validación", lo cual es inexacto — corregir solo el texto del documento, no la
  constante.
- **P1-7**: `doc/10-fundamento-cientifico.md` atribuye la fórmula `370 + 21,6×MM` a Cunningham; es
  Katch-McArdle. Cunningham 1980 es `500 + 22×MM`. Error de cita, sin impacto en el código porque esa
  fórmula no está implementada. Corregir el texto si en algún momento se documenta esa opción.
- **P2-5**: el suelo de disponibilidad energética no descuenta el gasto de ejercicio; en mujeres muy
  activas y delgadas puede quedar algo por debajo del umbral clínico (RED-S) sin que el suelo actual lo
  detecte. Mitigable restando `(multiplicador − 1,4) × BMR` como proxy de "gasto de ejercicio" solo en
  modo fórmula — comprobado en el barrido que no reintroduce el fallo opuesto (suelo por encima del
  mantenimiento).
- **P2-6**: en ganancia no hay ningún aviso para IMC muy alto ni individualización por nivel de
  entrenamiento (un avanzado gana menos rápido que un principiante, Iraki 2019). Mejora de producto, no
  un error de cálculo.
- **P2-7**: Deurenberg se extrapola fuera de su rango de validación (IMC 14-41) en obesidad severa; hoy
  no muerde porque el tope del 25% actúa antes, pero sería crítico si se adoptara C-4 de forma más
  agresiva. Vigilar si se cambia el reparto de proteína más adelante.
- **P3-4**: el autoajuste no tiene banda muerta ni error típico explícito — en estado estable, 1 de cada
  3 ajustes semanales mueve el objetivo >50 kcal sin ningún cambio real (sube a casi la mitad con ciclo
  menstrual). Un filtro de Kalman 1D sobre el factor lo bajaría a 1 de cada 6-7, con una mejora de RMSE
  solo a partir de las 8-12 semanas. Es el cambio de mayor envergadura de todos: requiere persistir una
  varianza junto al factor y cambia la dinámica del ajuste. **Se recomienda dejarlo para después** de
  los Bloques A y B.
- **P3-5**: el ciclo menstrual desplaza la pendiente medida hasta ±236 kcal/d según la fase, sin forma
  de corregirlo sin registrar la fase del ciclo. Limitación inherente; el filtro de Kalman (P3-4) la
  amortigua sin necesidad de ese dato.
- **P3-7**: el texto "Tu gasto real ronda las X kcal" (`ProfileScreen.kt:738`) presenta la medición cruda
  de una ventana como si fuera un hecho medido con precisión, cuando con infraregistro puede estar hasta
  un 24% por debajo del gasto metabólico real (lo que baja es lo registrado, no el metabolismo).
  Reformular a "según tus registros" y mostrar el valor ya filtrado (`newTdee`), no el crudo.
- **P2-9**: `waist_cm` se recoge en BBDD y no se usa (decisión consciente, §9.3 del doc 10). Si se
  retoma, la fórmula RFM (Woolcott 2018) estima mejor la grasa corporal que Deurenberg y destrabaría una
  proteína basada en masa magra real en vez de en el tope de C-4.
- **P2-10**: no hay objetivo de fibra (se podría informar 14 g/1000 kcal, IOM) ni mínimo de
  carbohidratos para quien entrena — hoy no es un problema real porque los perfiles activos ya salen
  dentro del rango ISSN (3-5 g/kg) y los casos bajos se resuelven con C-4.

---

## Plan de arreglo propuesto, por bloques

**Bloque A — crítico, cambios pequeños y acotados, sin riesgo de regresión**
(C-1, C-2, C-4, C-5, C-6 — en ese orden, porque C-1 es el que más kcal/día mueve)

**Bloque B — individualización por población, decisiones de producto además de cálculo**
(C-3 adolescentes/mayores, C-7 ventanas de datos, P2-5 suelo con ejercicio, P2-6 ganancia e IMC alto)

**Bloque C — calidad de interfaz y estadística, mayor esfuerzo**
(C-8 tendencia de peso EWMA, P3-7 texto del autoajuste, P3-4 filtro de Kalman, correcciones de texto en
`doc/10`)

Nada de esto se ha implementado todavía. C-1 en concreto requiere una decisión explícita sobre qué hacer
con los `adaptive_factor` ya guardados en producción antes de tocar código.
