# Fundamento científico de los cálculos de nutrición

**Creado**: 2026-11-01 · **Motivo**: antes de rehacer el cálculo de objetivos calóricos, fijar por
escrito de dónde sale cada número, con su fuente y su margen de error real.

Este documento es la **fuente de verdad de los números**. Si una constante aparece en el código
(`ProfileService.computeTargets`, `OnboardingScreen.StepSummary`, `NutritionScreen`), tiene que estar
justificada aquí. Si no está aquí, no debería estar en el código.

Sirve además como base del apartado de **preguntas frecuentes** previsto en funcionalidades futuras:
las respuestas de ese FAQ deben salir de aquí, no de la intuición.

## Cómo leer este documento

Cada afirmación lleva una marca de confianza:

| Marca | Significado |
|---|---|
| ✅ **Sólido** | Metaanálisis, revisión sistemática o consenso. Se puede afirmar en la app sin matizar. |
| 🟡 **Razonable** | Estudios primarios o revisiones narrativas, con limitaciones conocidas. Usable, pero con margen. |
| ⚠️ **Convención** | Práctica habitual sin validación en revisión por pares. Usable como punto de partida, **nunca como promesa al usuario**. |

La regla de oro de este diseño: **toda constante ⚠️ debe ser corregible por medición** (ver §5). Lo que
no se puede predecir con fiabilidad, se mide.

---

## 1. Gasto energético

### 1.1 Metabolismo basal (BMR) — Mifflin-St Jeor ✅

```
Hombre:  BMR = 10 × peso_kg + 6.25 × altura_cm − 5 × edad + 5
Mujer:   BMR = 10 × peso_kg + 6.25 × altura_cm − 5 × edad − 161
```

**Por qué esta y no otra**: el metaanálisis de 2023 en *Sports Medicine* sobre precisión de ecuaciones
de metabolismo en reposo la sitúa como la más fiable para población general sin medir composición
corporal, prediciendo dentro del ±10% del valor medido más a menudo que las alternativas.

Katch-McArdle y Cunningham (ambas basadas en masa magra, `370 + 21.6 × MM`) la superan **solo si se
conoce el % de grasa corporal fiable**. GymSmart no lo recoge, así que Mifflin es la elección correcta.
Si algún día se añade estimación fiable de composición corporal, reevaluar.

**Margen de error real**: ese ±10% son **±150-200 kcal/día** en un adulto medio. No es despreciable, y
es la primera de tres razones independientes por las que hace falta el autoajuste de §5.

### 1.2 Gasto total sin pulsera (TDEE) — multiplicadores ⚠️

```
TDEE = BMR × multiplicador
```

| Nivel | Multiplicador |
|---|---|
| Sedentario | 1.2 |
| Ligero | 1.375 |
| Moderado | 1.55 |
| Activo | 1.725 |
| Muy activo | 1.9 |

**Esta es la parte más débil de todo el cálculo.** Los multiplicadores son convención heredada y **el
salto entre dos escalones contiguos es de 250-350 kcal/día**.

> **Corrección (2026-11-28, P1-6)**: antes esta sección decía que los multiplicadores "no proceden de una
> validación sólida", a secas. Eso es inexacto en un punto que importa. Los escalones altos están
> razonablemente alineados con las referencias (FAO/WHO/UNU 2004: sedentario/ligero 1,40-1,69, activo
> 1,70-1,99, vigoroso 2,00-2,40; IOM 2005: poco activo 1,4-1,59, activo 1,6-1,89, muy activo ≥1,9). El
> que está fuera es **el 1,2 de "sedentario": queda por debajo de todas ellas**. En estudios con agua
> doblemente marcada, un adulto sedentario en vida libre casi nunca baja de ~1,4-1,5; un PAL de 1,2
> corresponde más bien a alguien encamado. Son ~430 kcal/día de infraestimación en el perfil de
> referencia.
>
> **No se cambia la constante**, por dos motivos: el factor del autoajuste (acotado a 0,70-1,30) cubre
> de sobra esa distancia (1,2 × 1,3 = 1,56), y la gente sobreestima su propio nivel de actividad en
> sentido contrario (Lichtman 1992, NEJM: +51 % de actividad declarada y −47 % de ingesta declarada), lo
> que compensa en parte. Pero conviene saber que el sesgo del escalón bajo **tiene dirección conocida**:
> es seguro para quien quiere perder y produce déficit encubierto para quien quiere ganar.

Consecuencia práctica: un usuario que elige "moderado" (1.55) cuando en realidad es 1.35 arrastra un
error de ~350 kcal/día **indefinidamente**, y ninguna fórmula lo detecta. Es el equivalente a cuatro
veces el error de Mifflin, y va en una sola dirección.

> **Implicación de diseño**: en el camino sin pulsera, el autoajuste de §5 no es una mejora opcional —
> es lo único que puede corregir un multiplicador mal elegido. Es *más* necesario aquí que en modo
> pulsera, no menos.

### 1.3 Gasto total con pulsera

```
TDEE = (BMR + media_móvil(calorías_activas, 14 días)) / (1 − TEF_FRACTION)
```

`ActiveCaloriesBurnedRecord` de Health Connect **excluye el metabolismo basal** por definición, así que
sumarlo al BMR es semánticamente correcto y no duplica nada.

**Y excluye también la digestión**, que es lo que obliga a dividir por `1 − TEF_FRACTION`. Esta división
**faltaba hasta el 2026-11-28** (C-1 en [12-auditoria-comite-kcal-2026-11-27.md](12-auditoria-comite-kcal-2026-11-27.md)):
el PAL de §1.2 incluye el efecto térmico de los alimentos por definición (DRI cap. 4: PAL = TEE/BMR, y
el TEF está dentro del TEE), la pulsera no lo incluye, y §16.2 ya descontaba el TEF para *mostrar* las
kcal activas de referencia — pero el cálculo del gasto no lo reponía. Resultado: **el modo pulsera daba
un 10 % menos de gasto que el modo fórmula para la misma actividad real** (2396 frente a 2662 en el
perfil de referencia), con las dos caras del fallo que originó toda esta línea de trabajo: en pérdida el
déficit real llegaba al 32,5 % mientras el sistema informaba 25 %, y en ganancia el superávit quedaba en
+43 kcal, o sea mantenimiento disfrazado.

Que la constante sea la correcta es **comprobable algebraicamente**, y hay un test que lo fija: §16.2
genera las activas de un nivel como `BMR × (PAL × (1 − TEF) − 1)`, así que al reinyectarlas aquí
`(BMR + activas) / (1 − TEF) = BMR × PAL` exactamente. Los dos modos coinciden nivel por nivel.

> La forma rigurosa sería `(BMR + activas + 0,10 × Δ) / 0,90`, porque el TEF es el 10 % de lo que se
> *ingiere* y no de lo que se gasta. Son ~60 kcal de diferencia, y no merece la complejidad frente a una
> pulsera con ±20-30 % de error (O'Driscoll 2020, BJSM, 60 estudios).

Este camino aplica además el mismo respaldo que el de fórmula (`≥ BMR × 1,2`): antes era la única rama
sin suelo, así que una pulsera que reportara pocas activas daba un gasto **por debajo** del respaldo
"no tengo datos" de la rama contigua, y empezar a tener datos bajaba el objetivo 193 kcal.

⚠️ **Nunca sumar `TotalCaloriesBurnedRecord` directamente**: incluye el basal, y sumarlo sobre el BMR
duplicaría 1.500-1.800 kcal/día. Si alguna vez hiciera falta usarlo (porque la app compañera no escriba
activas), habría que **restarle el BMR** antes.

**Por qué media móvil y no la lectura del día en curso.** Medido en dispositivo el 2026-11-01 (Xiaomi
Smart Band 10 + Mi Fitness + Redmi Note 11 Pro 5G):

| Fuente | Calorías activas |
|---|---|
| Pulsera | 56 |
| App Mi Fitness | 54 |
| Health Connect (agregado) | 37 |
| GymSmart | 37 |

El desfase **no es aleatorio: siempre va por debajo**, porque Mi Fitness sincroniza por lotes. Un
presupuesto calculado con la lectura en vivo está sistemáticamente infravalorado durante todo el día, lo
que provoca **déficit encubierto**: el usuario come menos de lo que cree. Verificado como causa real de
pérdida de peso con objetivo de ganancia.

Usar días **ya cerrados** elimina ese sesgo, porque a esa altura la sincronización ya ha terminado. Y
permite conocer el objetivo desde que el usuario se levanta, que es un requisito práctico: no se puede
planificar la cena contra un número que todavía se está moviendo.

**Compensación del día real**: la diferencia entre la actividad prevista y la real no se descarta, se
**arrastra al día siguiente**. Si hoy se gastaron 400 kcal más de lo previsto, mañana el presupuesto
sube 400. El balance semanal cuadra exactamente igual que si se hubieran comido hoy, sin perseguir un
número incompleto.

⚠️ El techo de frescura no depende del código: la propia FAQ de Xiaomi reconoce que habrá diferencias
por el ciclo de sincronización. **No es corregible desde la app**, y la interfaz no debe prometer
tiempo real.

---

## 2. La constante energética (7700 kcal/kg)

### 2.1 Para perder peso 🟡

**7700 kcal por kg** de tejido adiposo (origen: Wishnofsky, 1958; 3500 kcal/libra).

Hall y Chow la critican con razón: su error más grave es **no tener en cuenta los cambios dinámicos del
balance energético** — asume que el gasto no cae al adelgazar, y por tanto predice pérdida lineal
indefinida en lugar de la meseta real.

**Dónde sí vale**: es razonablemente fiel **las primeras semanas**, y se vuelve progresivamente
optimista a partir de ahí. Encaja mejor en personas con bastante grasa y **sobreestima en personas
delgadas**, porque una fracción mayor de su pérdida es tejido magro.

**Decisión**: se usa para fijar el objetivo inicial, que opera en horizonte semanal. **Obligatoriamente
corregida por el autoajuste de §5**, que es la respuesta correcta al fallo que Hall describe.

### 2.2 Para ganar peso 🟡 — no es la misma constante

Esto es menos conocido y cambia el cálculo:

| Tejido | Energía |
|---|---|
| Grasa (adiposo) | ~7.700 kcal/kg |
| Músculo (energía almacenada) | ~1.000-1.800 kcal/kg |
| Músculo (coste total de construcción) | ~4.150-4.570 kcal/kg |

El músculo es ~75% agua, así que **almacena mucha menos energía por kg que la grasa**. El coste total
de construirlo (incluyendo la síntesis) sigue siendo bastante menor que el de la grasa.

**Consecuencia**: el coste por kg ganado depende de **qué** se gane. Quien gana sobre todo músculo
(principiante, buen entrenamiento, proteína suficiente) **sube más rápido de lo que predice la
fórmula**; quien gana sobre todo grasa, más lento.

**Decisión**: usar 7700 como estimación de ganancia *mixta* (un estudio clínico usa 7.500 kcal/kg para
ganancia de peso en 10-40 años), marcándolo explícitamente como estimación. Tercera razón independiente
para el autoajuste.

---

## 3. Ritmo de cambio de peso

### 3.1 El ritmo debe expresarse en % del peso corporal, no en kg ✅

**Este es el hallazgo que más condiciona el diseño.**

- **Helms et al. (2014)** recomiendan **0,5-1% del peso corporal por semana** para maximizar la
  retención de masa muscular durante una pérdida.
- **Garthe et al. (2011)**, en atletas de élite con entrenamiento de fuerza, comparó dos ritmos durante
  4-11 semanas:

| Ritmo | Pérdida de grasa | Masa magra |
|---|---|---|
| 0,7 %/semana | −31% | **+2,1%** |
| 1,4 %/semana | −21% | sin cambios |

Misma pérdida total de peso, resultado de composición corporal claramente distinto.

**Por qué un ritmo en kg fijos es inválido**: "1 kg/semana" significa cosas opuestas según el peso.

| Peso | 1 kg/semana equivale a | Veredicto |
|---|---|---|
| 60 kg | 1,67 %/sem | Muy por encima del rango seguro |
| 70 kg | 1,43 %/sem | El brazo *malo* del estudio de Garthe |
| 120 kg | 0,83 %/sem | Dentro del rango razonable |

Una opción de menú no puede ser simultáneamente segura e insegura según quién la elija. **El ritmo se
elige en % y la app traduce a kg y a kcal.**

### 3.2 Rangos admitidos

> **Corrección del 2026-11-01**: la primera versión de este documento trataba 0,5-1 %/semana como un
> rango con **dos** límites, y la app bloqueaba cualquier cosa por debajo de 0,5 %. Eso es una mala
> lectura de la fuente. Helms da esa banda como el rango que **maximiza la retención de masa magra**,
> no como un mínimo de seguridad. Ir más lento no tiene ningún riesgo: se retiene músculo igual o
> mejor, simplemente se tarda más. El límite real está **solo arriba**.

| Objetivo | Tope duro (se bloquea) | Banda recomendada (se sugiere) | Confianza |
|---|---|---|---|
| Perder grasa | 1,0 % PC/semana | 0,5 – 1,0 % | ✅ Helms 2014, Garthe 2011 |
| Ganar músculo | 0,5 % PC/semana | 0,25 – 0,5 % | 🟡 Iraki et al. 2019 |
| Mantener | — | 0 | — |

> **Eliminada la fila "Recomposición" (2026-11-23)**: existía como objetivo seleccionable
> (`GOAL_RECOMP`) pero en el cálculo real no era más que `lose_fat` con el tope bajado a 0,25%
> — sin nada de lo que §8 exige para que sea recomposición de verdad. Detalle en AUD-7 de
> `doc/11-auditoria-kcal.md`. §8 se conserva porque describe un fenómeno real, solo que ya no
> es una opción del menú.

**Por debajo de la banda**: permitido y sin aviso. Un ritmo de 0,2 kg/semana es perfectamente válido.

**Implicación para la interfaz**: el extremo del deslizador **es** el tope duro, calculado con el peso
de cada persona. Así pasarse es imposible por construcción y no hace falta un estado de error.

**Techo de ganancia**: la revisión de Iraki et al. (2019) sitúa la ganancia en 0,25-0,5% PC/semana, con
superávit de +10-20% sobre mantenimiento para principiantes e intermedios y +5-10% para avanzados. Por
encima de eso **lo que se gana es grasa**: el ritmo de síntesis muscular tiene un techo fisiológico que
no se acelera comiendo más.

Por eso ofrecer "ganar 1 kg/semana" está mal por partida doble: la aritmética es falsa **y** el consejo
es malo.

### 3.3 Fórmula de conversión

```
delta_kcal_día = (ritmo_% / 100) × peso_kg × 7700 / 7
```

Negativo para perder, positivo para ganar.

**Ejemplo** (70 kg, perder 0,75 %/semana):
`0,0075 × 70 × 7700 / 7 = 577 kcal/día de déficit` → 0,525 kg/semana.

**Comparar con lo que hacía la app antes** (etiquetas de `ProfileScreen`, incorrectas):

| Opción antigua | Prometía | Daba de verdad | Error |
|---|---|---|---|
| −250 kcal/día | 0,25 kg/sem | 0,23 kg/sem | aceptable |
| −400 kcal/día | 0,5 kg/sem | 0,36 kg/sem | −27% |
| −500 kcal/día | **1 kg/sem** | **0,45 kg/sem** | **−55%** |

---

## 4. Adaptación metabólica (termogénesis adaptativa) ✅

Al perder peso, el gasto cae **más de lo que explica** la reducción de peso y de masa magra. Es un
fenómeno real y medido.

**Magnitudes**:
- Típicamente **50-200 kcal/día**.
- Estudio de Minnesota: ~180 kcal/día independientes del cambio de masa magra (~35% de la caída del
  metabolismo basal).
- Depende del estado de balance energético: ~92 kcal/día al final de la pérdida activa, bajando a
  ~38 kcal/día tras 4 semanas de estabilización.

**Lo decisivo es la variabilidad individual**: en un análisis de 227 sujetos los valores iban de
**−337 a +352 kcal/día**, y aproximadamente **un tercio no mostró ninguna reducción**.

> **Implicación de diseño**: el rango individual es más ancho que el efecto medio, así que **ninguna
> fórmula puede predecir la adaptación de un usuario concreto**. Solo puede medirse a posteriori. Esta
> es la segunda razón independiente para §5.

Esto es también la explicación de "llevo tiempo y ya no bajo igual que al principio": no es falta de
constancia, es fisiología esperable.

---

## 5. Autoajuste por medición (TDEE adaptativo) ⚠️

### 5.1 Por qué existe

Tres fuentes de error **independientes** que ninguna fórmula resuelve:

1. Mifflin: ±150-200 kcal/día (§1.1)
2. Multiplicador de actividad mal elegido: hasta ±350 kcal/día, permanente (§1.2)
3. Adaptación metabólica: ±50-200 kcal/día, impredecible individualmente (§4)

Más dos de medición: la pulsera infravalora (§1.3) y el usuario pesa la comida con error.

Pueden sumarse en la misma dirección. **La única forma de cerrar el círculo es medir el resultado real
en lugar de confiar en la predicción.**

### 5.2 Método

```
gasto_real = media(ingesta_diaria) − (pendiente_peso_kg_por_día × 7700)
```

**Ejemplo**: 14 días, ingesta media 2.600 kcal/día, pérdida de 0,4 kg.
`2600 + (0,4 × 7700 / 14) = 2.820 kcal/día` de gasto real.

Ese número **ya incluye** el metabolismo real, lo que la pulsera no captó y el error de pesaje de la
comida — porque se deriva del resultado, no de la predicción.

### 5.3 Requisitos y salvaguardas

Sin estas condiciones el método amplifica ruido en lugar de corregir error:

| Requisito | Valor | Motivo |
|---|---|---|
| Días de ingesta registrada | ≥ 14 | Menos no distingue señal de ruido |
| Pesajes | ≥ 4, repartidos en ≥ 10 días | Un solo pesaje es agua, no tejido |
| Suavizado del peso | Media móvil exponencial o regresión lineal sobre 14-28 días | El peso diario oscila ±1-2 kg por agua y glucógeno |
| Mezcla con el valor previo | `TDEE_nuevo = 0,3 × medido + 0,7 × previo` | Evita saltos bruscos por una semana atípica |
| Frecuencia de corrección | Cada 1-2 semanas | Más a menudo es perseguir ruido |

Mientras no se cumplan los mínimos, se usa la estimación por fórmula y **se indica que aún no hay datos
suficientes** — nunca se presenta un ajuste como medido si no lo es.

### 5.4 Honestidad sobre el estatus de este método

⚠️ **El método en sí (media móvil + reconciliación) es convención de entrenadores y aplicaciones, no
tiene validación en revisión por pares.** No se encontró ningún estudio que lo valide como tal, y las
fuentes consultadas son herramientas comerciales que además **no coinciden** en la ventana óptima
(21 vs 28 días).

Lo que **sí** está sólidamente respaldado es su premisa: que la predicción estática falla de forma
conocida (§2.1, §4) y que el error individual es impredecible. Medir es la respuesta correcta a ese
problema, aunque los parámetros concretos del suavizado sean una elección de ingeniería y no un
hallazgo científico.

**Cómo debe comunicarse al usuario**: "ajustado según tu progreso real", no "científicamente
calculado".

---

## 6. Macronutrientes

### 6.1 Proteína ✅

| Situación | Recomendación | Fuente |
|---|---|---|
| Balance energético / superávit | **1,6 g/kg** de peso corporal (meseta; IC 95% hasta 2,2) | Morton et al. 2018, metaanálisis de 49 estudios |
| Déficit energético | **2,3 – 3,1 g/kg de masa magra**, escalando con la severidad del déficit y la delgadez | Helms et al. 2014, revisión sistemática |

**Problema de aplicación**: la recomendación en déficit es por **masa magra**, que requiere % de grasa
corporal. GymSmart no lo recoge, así que se aproxima sobre peso total:

| % grasa | 2,0 g/kg peso total equivale a | ¿Dentro del rango de Helms? |
|---|---|---|
| 10% | 2,2 g/kg MM | Ligeramente bajo |
| 20% | 2,5 g/kg MM | ✅ |
| 30% | 2,9 g/kg MM | ✅ |

**Decisión**: mantener **2,0 g/kg de peso total en déficit** (cae dentro del rango para la mayoría) y
**1,8 g/kg en mantenimiento y superávit** (por encima de la meseta de 1,6, con margen de seguridad).
Revisar si algún día se añade estimación de composición corporal.

### 6.2 Grasa ⚠️

**25% de las calorías totales.** Convención razonable para función hormonal, pero **no investigada en
profundidad para este documento**. Marcado como pendiente de fundamentar si alguna vez se cuestiona.

### 6.3 Carbohidratos

**Resto calórico** tras proteína y grasa.

⚠️ **Bug conocido a corregir**: si proteína + grasa superan el objetivo (peso alto + objetivo bajo), los
carbohidratos se recortan a 0 y **los macros dejan de sumar las kcal objetivo**, en silencio. El reparto
debe recalcularse de forma que siempre cuadre, o avisar explícitamente.

---

## 7. Mínimo calórico de seguridad

### 7.1 Los valores actuales no tienen respaldo ⚠️

Los suelos de **1.500 kcal (hombre) / 1.200 kcal (mujer)** que usa el código hoy **no tienen validación
científica localizable**. Son cifras de cultura popular.

### 7.2 El marco correcto: disponibilidad energética

```
DE = (ingesta − gasto_de_ejercicio) / kg de masa magra
```

- **< 30 kcal/kg MM/día** → baja disponibilidad energética, con alteraciones documentadas.
- Ese umbral procede de estudios de laboratorio en **mujeres no atletas**, y para hombres se sugiere más
  bien **20-25 kcal/kg MM/día**.
- Bandas usadas en la práctica: >45 óptimo · 30-45 subóptimo · ≤30 bajo.

**Por qué no se puede implementar hoy**: requiere masa magra, que requiere % de grasa corporal, que la
app no recoge. Un suelo fijo en kcal **no puede derivarse de este marco**, porque ignora el gasto de
ejercicio y la composición corporal.

### 7.3 Decisión: tope de déficit del 25% del gasto ⚠️

**El déficit nunca supera el 25% del gasto total diario.**

> **Corrección del 2026-11-01.** La primera versión usaba "nunca bajar del BMR" como suelo. Lo detectó
> el usuario probando: su objetivo salía **exactamente igual a su BMR**, con el déficit anulado entero.
>
> La causa es que ese suelo es **aritméticamente incompatible con los rangos de ritmo de §3.2**. Una
> persona sedentaria gasta 1,2 × BMR, así que su margen hasta el basal es el 20% de su BMR. Con los
> datos reales del usuario (75,5 kg, 184 cm, 21 años): BMR 1.805, gasto sedentario 2.166, margen de
> **361 kcal/día** = 0,33 %/semana. Pero el ritmo más suave que la app ofrecía pedía 0,5 % = 415 kcal.
> El suelo hacía imposible la opción más conservadora del propio menú.
>
> Además, "nunca bajes de tu BMR" es del mismo género que el "1.200 kcal" que §7.1 descarta por falta
> de respaldo. Comer 1.751 teniendo 1.805 de basal no tiene nada de peligroso.

**Por qué un tope en fracción del gasto es mejor**: escala con cada persona, **nunca puede anular el
déficit** (es una fracción de un número que siempre es mayor que cero), y la dirección está respaldada
— a mayor déficit, mayor pérdida de masa magra, y hay literatura que sugiere que la magnitud del
déficit pesa más que la propia ingesta de proteína para retener masa magra.

El 25% concreto es convención de práctica, no una cifra sacada de un estudio. Marcado ⚠️.

**Se aplica al déficit, no al objetivo.** Recortar el objetivo final puede dejar el déficit en cero sin
que se note; recortar el déficit garantiza que siempre quede algo de lo que el usuario pidió.

**Obligatorio**: cuando el tope recorte el ritmo pedido, **decírselo al usuario**. Recortar en silencio
es lo que hace que alguien crea ir a −500 kcal y vaya realmente a −200.

> ### ⚠️ Corrección del 2026-11-22 — el razonamiento de arriba tiene un hueco
>
> Lo detectó la auditoría independiente de [11-auditoria-kcal.md](11-auditoria-kcal.md). Esta sección
> **cambia un objeto de seguridad por otro distinto y lo presenta como mejora**: un *suelo de ingesta* y
> un *tope de déficit* no son lo mismo. El tope del 25 % resuelve de verdad el problema que motivó el
> cambio (el déficit anulado), pero **elimina la única garantía de mínimo absoluto que existía**, y eso
> no se señaló. Consecuencias medidas:
>
> - **No queda ningún suelo.** Como el gasto sedentario es 1,2 × BMR, el objetivo tope-limitado es la
>   identidad exacta **0,90 × BMR** para cualquier persona sedentaria, sin importar su talla.
> - **El tope es evadible**: `carryOverKcal` se suma *después* del recorte, así que el déficit real puede
>   llegar al 50 % del gasto mientras el sistema informa 25 % — el invariante nº5 de §14 se viola por un
>   factor 2.
> - **Falta el simétrico**: no hay tope de superávit, y §10.3 fija +10-20 % con la misma clase de fuente.
>   La constante de seguridad se puso donde incomodaba comer poco, no donde la fuente marca el límite.
> - El argumento "no se puede implementar el marco de §7.2 porque no tenemos masa magra" es **rigor
>   asimétrico**: el sistema ya acepta un BMR con ±10 % de error para *fijar* el objetivo, así que una
>   estimación igual de cruda es admisible para *avisar*.
>
> **Decisión del usuario (2026-11-22)**: se añade un suelo por **disponibilidad energética** (§7.2) con
> masa magra estimada por población, exigiendo **≥30 kcal/kg MM/día**, aplicado **al final del cálculo,
> sobre el número que se muestra** y no al déficit. Y `carryOverKcal` pasa a aplicarse **dentro** del
> recorte y solo en modo pulsera. El 25 % sigue en pie; lo que se añade es el mínimo absoluto que faltaba.

### 7.4 Un suelo relacionado: el gasto nunca puede ser el BMR

Detectado junto a lo anterior. En modo pulsera el gasto se calculaba como `BMR + calorías activas`, de
modo que **sin datos de Health Connect** (los primeros días, o si la app de la pulsera no ha escrito
nada) quedaba en `BMR + 0`.

Eso equivale a afirmar que la persona está en coma: nadie que se levante de la cama gasta su
metabolismo basal. Mientras no haya histórico se asume **sedentario** (× 1,2), que es la estimación
conservadora, y el autoajuste de §5 corrige después con datos reales.

**Invariante que debe cumplirse siempre**: `gasto > BMR`. Hay un test que lo comprueba en todas las
combinaciones.

---

## 8. Recomposición corporal 🟡

> **Nota (2026-11-23)**: la app tuvo un objetivo `GOAL_RECOMP` seleccionable y se eliminó. En el código
> era, campo por campo, idéntico a `lose_fat` salvo el tope de ritmo (AUD-7 en
> `doc/11-auditoria-kcal.md`): no consultaba entrenamiento de fuerza, no usaba la cintura que §9.3 pide
> para verificarla, y no sugería nada según el perfil — exactamente lo que la "implicación de diseño"
> de abajo ya señalaba como pendiente antes de implementarse. Esta sección se conserva porque el
> fenómeno es real y puede volver a justificar una funcionalidad si algún día se implementa de verdad
> (con las tres condiciones de abajo), no como una tercera dirección del déficit con otro nombre.

Ganar músculo y perder grasa simultáneamente. Está documentado, pero **depende mucho del punto de
partida**:

| Perfil | Probabilidad |
|---|---|
| Principiante, o con sobrepeso/obesidad | **Alta** — es el resultado esperable, no la excepción |
| Entrenado | Posible, pero lento (Barakat et al. 2020) |
| Avanzado o muy definido | Poco probable |

**Mecanismo**: la grasa almacenada aporta energía durante el déficit, de modo que se degrada menos
proteína muscular.

**Condiciones**: entrenamiento de fuerza progresivo + proteína suficiente. No ocurre solo por comer en
mantenimiento.

**Contrapartida honesta**: es **más lento** que una fase dedicada de definición o de volumen. No
maximiza ni la pérdida de grasa ni la ganancia de músculo; optimiza la combinación.

> **Implicación de diseño**: el modelo actual (`lose_fat` / `maintain` / `gain_muscle`) obliga a elegir
> entre subir y bajar. Para un principiante o alguien con grasa que perder, en teoría lo correcto sería
> recomposición, y hoy no hay forma de expresarlo — a propósito, tras retirar la versión que no la
> expresaba bien (ver la nota al principio de esta sección). Si se vuelve a intentar, tiene que
> cumplir las tres condiciones de arriba, no solo recortar el tope de déficit.

---

## 9. Medición: por qué el peso no basta

### 9.1 El problema

La báscula no distingue músculo de grasa. Si alguien con objetivo "ganar músculo" sube 2 kg, el peso
solo **no puede decir si ha acertado o ha engordado**. El objetivo es inverificable.

### 9.2 Estimación de % de grasa por circunferencias: no recomendada ⚠️

El método Navy (cintura, cuello, altura; + cadera en mujeres) se descarta **como estimador de % de
grasa**:

- Contra DXA, **infraestima ~6 puntos porcentuales** en muestras militares (1.407 soldados).
- Una comparación de métodos lo sitúa como **el menos preciso** de los evaluados.
- El ±3-4% que repiten las calculadoras procede de su **validación original contra pesaje
  hidrostático**, no contra DXA, y no debe presentarse como concordancia con DXA.
- Muy sensible a la colocación de la cinta.
- Un estudio advierte explícitamente que puede ser **malo para seguir cambios a corto plazo**.

### 9.3 Lo que sí se propone: seguir la cintura 🟡

> **Eliminado de la app (2026-11-23), decisión del usuario**: medirse la cintura con cinta cada vez era
> fricción que no quería ("es pereza"). Se quitó el campo del diálogo de añadir peso, el guardado y la
> lectura en servidor y cliente, y la tarjeta `CompositionCard` de `WeightScreen` que interpretaba la
> tabla de abajo. **La columna `waist_cm` se deja sin tocar en la BBDD** (no se borra: es destructivo y
> no hacía falta), simplemente ya no la escribe ni la lee ningún código. Esta sección se conserva
> entera porque la ciencia sigue siendo correcta — si algún día se quiere retomar el seguimiento de
> composición corporal sin báscula, es la base a usar.

No como % de grasa, sino como **medida cruda cuya tendencia se compara con la del peso**.

Justificación: la circunferencia abdominal es **el predictor antropométrico individual más fuerte de
grasa corporal en hombres**, solo requiere una cinta métrica, y para seguir *cambios* el sesgo
sistemático se cancela al mirar la diferencia.

| Peso | Cintura | Interpretación |
|---|---|---|
| ↑ | estable | **Ganando músculo** ✅ |
| ↑ | ↑ | Superávit excesivo — reducir |
| ↓ | ↓ | **Perdiendo grasa** ✅ |
| ↓ | estable | Perdiendo músculo ⚠️ subir proteína o reducir déficit |
| estable | ↓ | **Recomposición** ✅ |

Con esto, "ganar músculo" pasa de ser un deseo a ser una afirmación comprobable.

---

## 10. Entrenamiento (base del FAQ)

Esta sección no afecta al cálculo calórico. Está aquí porque alimenta el apartado de preguntas
frecuentes previsto.

### 10.1 ¿Es mejor entrenar un músculo 1 o 2 veces por semana? ✅

**Ninguna de las dos: lo que importa es el volumen semanal total.**

Es una respuesta contraintuitiva con una historia instructiva:

- **Schoenfeld et al. (2016)** encontró ventaja para 2×/semana — pero **estaba confundido por volumen**:
  la mayoría de estudios incluidos no igualaban las series totales.
- **Schoenfeld & Grgic (2019)**, igualando volumen, concluyó que hay **evidencia sólida de que la
  frecuencia no afecta significativa ni relevantemente a la hipertrofia**.
- La meta-regresión de 2025 en *Sports Medicine* lo confirma: la frecuencia **no muestra efecto claro
  sobre hipertrofia** una vez contabilizado el volumen (sí lo muestra sobre fuerza).

**Conclusión**: la frecuencia es la herramienta para **repartir** el volumen, no una variable con efecto
propio. Entrenar 2× puede ayudar a acumular más series con buena calidad, y ahí está su valor real.

### 10.2 ¿Por qué importa tanto el volumen? ✅

**Schoenfeld et al. (2017)** encontró una **relación dosis-respuesta gradual**: más volumen semanal →
más hipertrofia.

La meta-regresión de 2025 matiza que volumen y frecuencia tienen curvas dosis-respuesta **distintas para
hipertrofia y para fuerza**, y que la fuerza muestra rendimientos decrecientes más marcados. Es decir:
para ganar tamaño, el volumen sigue aportando durante más tiempo que para ganar fuerza.

### 10.3 ¿Cómo hacer bien un superávit? ✅🟡

Ver §3.2. Resumen: **+0,25-0,5% del peso corporal por semana**, con superávit de +10-20% sobre
mantenimiento (principiante/intermedio) o +5-10% (avanzado). Más no acelera el músculo, solo añade
grasa.

---

## 11. Resumen de constantes para implementar

| Constante | Valor | §  | Confianza |
|---|---|---|---|
| BMR hombre | `10×kg + 6.25×cm − 5×edad + 5` | 1.1 | ✅ |
| BMR mujer | `10×kg + 6.25×cm − 5×edad − 161` | 1.1 | ✅ |
| Multiplicadores actividad | 1.2 / 1.375 / 1.55 / 1.725 / 1.9 | 1.2 | ⚠️ |
| Ventana media móvil pulsera | 14 días | 1.3 | ⚠️ |
| Energía por kg (pérdida) | 7.700 kcal/kg | 2.1 | 🟡 |
| Energía por kg (ganancia mixta) | 7.700 kcal/kg (estimación) | 2.2 | 🟡 |
| Tope de ritmo (pérdida) | 1,0 % PC/sem | 3.2 | ✅ |
| Tope de ritmo (ganancia) | 0,5 % PC/sem | 3.2 | 🟡 |
| Banda recomendada (pérdida) | 0,5 – 1,0 % PC/sem | 3.2 | ✅ |
| Banda recomendada (ganancia) | 0,25 – 0,5 % PC/sem | 3.2 | 🟡 |
| Proteína en déficit | 2,0 g/kg peso total | 6.1 | ✅ |
| Proteína en mantenimiento/superávit | 1,8 g/kg peso total | 6.1 | ✅ |
| Grasa | 25% de kcal | 6.2 | ⚠️ |
| Tope de déficit | 25% del gasto total | 7.3 | ⚠️ |
| Gasto sin datos de pulsera | BMR × 1,2 (sedentario) | 7.4 | ⚠️ |
| Mínimo datos para autoajuste | ≥14 días ingesta, ≥4 pesajes en ≥10 días | 5.3 | ⚠️ |
| Factor de mezcla del autoajuste | 0,3 medido / 0,7 previo | 5.3 | ⚠️ |

---

## 12. Lo que NO está bien establecido

Para que nadie —ni un FAQ, ni la interfaz— afirme más de lo que se puede sostener:

1. **El coste energético de construir músculo.** Las fuentes van de 1.000 a 4.570 kcal/kg según cuenten
   solo energía almacenada o incluyan la síntesis. No está resuelto en la literatura.
2. **El método de autoajuste.** Su premisa está respaldada; los parámetros concretos (ventana,
   suavizado, factor de mezcla) son ingeniería, no ciencia.
3. **Los multiplicadores de actividad.** Convención heredada sin validación sólida.
4. **El 25% de grasa.** No investigado en profundidad aquí.
5. **Los umbrales de disponibilidad energética en hombres.** El 30 kcal/kg MM procede de mujeres no
   atletas; para hombres se discute 20-25 y no hay consenso.
6. **La recomposición en entrenados.** Apoyada sobre todo en una revisión (Barakat et al. 2020)
   consultada a través de fuentes secundarias.

7. **El tope de déficit del 25%.** La dirección está respaldada; la cifra concreta es convención.

**Regla**: cuando la app muestre un número de esta lista, debe presentarse como estimación ajustable,
nunca como dato cerrado.

---

## 13. Peso en crudo vs. peso cocinado ✅

Implementado en `shared/src/commonMain/.../nutrition/CookingYield.kt`, verificado en
`CookingYieldTest.kt`. **Ningún factor de esta sección es una estimación propia**: todos salen de una
publicación de la USDA que se cita entera, con su número de muestras cuando lo da.

### 13.1 El problema

Los datos de Open Food Facts son "tal como se vende": para un alimento fresco, eso es **en crudo**; para
arroz, pasta y legumbres, **en seco**. Si el usuario pesa el alimento ya hecho y le aplica esos valores,
el error no es marginal:

| Alimento | Pesado | Lo que registraba | Lo correcto | Error |
|---|---|---|---|---|
| Pechuga de pollo | 150 g hechos | 180 kcal | 250 kcal | **−28 %** |
| Arroz blanco | 200 g cocidos | 730 kcal | 260 kcal | **+181 %** |

Los dos van en sentido contrario, que es lo que lo hace traicionero: la carne pierde agua al cocinarse
y se concentra, el arroz la absorbe y se diluye.

### 13.2 El método

Es el que documenta la propia USDA: *"nutrient composition of a cooked food may be calculated from the
uncooked food by applying cooking yield factors to these data to reflect changes in food weights
resulting from moisture and fat losses during cooking"*.

> ⚠️ **Corregido el 2026-11-27.** Aquí decía, a continuación de esa cita, *"los nutrientes se
> conservan; lo que cambia es el peso"*, dando a entender que lo afirmaba la USDA. **No lo afirma, y
> es falso para la grasa.** La propia publicación de rendimientos define *tres* magnitudes separadas
> —`Yield`, `Moisture change` y **`Fat change`**— y dice que el NDL aplica *"cooking yields **and/or**
> nutrient retention factors"*. Al cocinar una carne la grasa gotea y se va; solo el agua se comporta
> como suponía esa frase. De ahí que desde el 2026-11-27 cada macro tenga su propio factor (§13.9).

Siendo `f` el cociente de densidad energética (kcal/100 g en crudo ÷ kcal/100 g cocinado):

```
valor por 100 g en base cocinada = valor por 100 g en base cruda ÷ f
```

Lo guardado en BBDD son los gramos que el usuario pesó de verdad y los valores por 100 g **ya
convertidos**, así que el total se sigue calculando igual que siempre y nada del autoajuste ni de los
agregados diarios cambia.

### 13.3 Cómo se obtuvieron los factores

La primera versión de este catálogo tenía 11 alimentos, sacados a mano de una en una, y se quedó corta:
sin ni un pescado, no servía para la mitad de las comidas reales. Se rehizo de forma sistemática.

Se descargó el **volcado completo de USDA FoodData Central, SR Legacy** (6 MB, 7.793 alimentos). Ese
dataset nombra los alimentos de forma regular —`"…, raw"` frente a `"…, cooked, roasted"`— así que el
tronco del nombre identifica el mismo alimento en sus dos formas. Emparejando por ese tronco salen
**1.261 parejas crudo/cocinado** automáticamente, de las que se seleccionaron las relevantes para una
dieta española. El factor de cada una es `kcal/100 g en crudo ÷ kcal/100 g cocinado`.

Se usa la razón de kcal y no un rendimiento de peso a propósito: así los valores por 100 g en cocinado que
calcula la app **coinciden con los que la USDA midió** en el alimento ya cocinado, que es exactamente lo
que se le quiere mostrar al usuario. Un rendimiento de peso puro no lo conseguiría, porque al hervir y
escurrir se pierden nutrientes en el agua.

Dos factores (ternera picada y cordero) no tienen pareja en SR Legacy y vienen de la *"USDA Table of
Cooking Yields for Meat and Poultry"* (ARS, 2012); están marcados como tales en su `source`.

### 13.4 El catálogo

Factores derivados de FoodData Central salvo donde se indica. Los números entre paréntesis son los
identificadores de FoodData Central, para poder comprobar cualquier fila.

**Carnes y aves**

| Alimento | Factor | Pareja |
|---|---|---|
| Muslo de pollo | **0,68** | thigh, meat only: 121 (173627) → **roasted 179 (172388)** |
| Pechuga de pollo | **0,73** | breast, meat only: 120 (171077) → **roasted 165 (171477)** |
| Pechuga de pavo fresca | 0,83 | turkey breast, meat and skin: 157 (171093) → roasted 189 (171492) |
| Carne picada de pavo | 0,73 | ground turkey: 148 (171505) → cooked 203 (171506) |
| Carne picada de ternera | **0,86** | **ground beef 85/15: 215 (171796) → patty broiled 250 (174032)** |
| Filete/solomillo de ternera | 0,79 | tenderloin roast lean: 139 (171765) → roasted 177 (174004) |
| Lomo/solomillo de cerdo | 0,76 | pork tenderloin lean: 109 (168249) → roasted 143 (168250) |
| Cordero | **1,00** | **ground lamb: 282 (174370) → broiled 283 (172544)** |

> ⚠️ **Cuatro de estas filas estaban mal y se corrigieron el 2026-11-27** (detalle en
> [13-auditoria-nutricion-2026-11-27.md](13-auditoria-nutricion-2026-11-27.md) §2.2):
>
> - **Pollo y muslo citaban la entrada equivocada.** 171140 y 172853 se describían aquí como
>   *"roasted"* y en el volcado son **`cooked, braised`**: estofado. El asado es 171477 y 172388. El
>   método que la app mostraba al usuario ("al horno") venía de un dato de estofado.
> - **La picada y el cordero mezclaban dos magnitudes distintas.** La *Tabla de rendimientos* publica
>   **rendimiento de peso** (de 100 g crudos quedan 73 cocinados), pero el factor se usa como
>   **cociente de densidad energética**. En una carne grasa no coinciden ni de lejos, porque la grasa
>   gotea mientras el agua se evapora: la picada va de 0,81 (90/10) a 1,20 (70/30) según el nivel de
>   grasa, y el cordero picado da 0,996. Con 0,73, una hamburguesa de 150 g ya hecha se registraba en
>   522 kcal donde la USDA mide 405.
> - De paso, el *"n=40"* que se citaba para la picada era **n=4** en la fila real de esa tabla.

**Pescado y marisco**

| Alimento | Factor | Pareja |
|---|---|---|
| Pescado (todas las especies) | 0,78 | ver nota abajo |
| Gambas y langostinos | 0,86 | shrimp: 85 (175179) → cooked 99 (175180) |

> ⚠️ **Reescrito el 2026-11-27: el razonamiento estaba invertido.** Aquí se decía que un único factor
> para todo el pescado *"no es una simplificación por comodidad: es lo que dice el dato"*, porque 45
> de las 72 parejas dan exactamente 0,78. **La cifra es correcta y la conclusión no.**
>
> Al separar las parejas según si la USDA **midió** la entrada cocinada o la **imputó**, se ve que en
> las de 0,78 los tres macros dan el mismo cociente a tres decimales —bacalao 0,781/0,780/0,779,
> lubina 0,782/0,780/0,779, trucha 0,779/0,780/0,780—, que es la huella de haber dividido *todos* los
> nutrientes por un único número. **El dato no dice 0,78: el dato asume 0,78.**
>
> Donde la USDA sí midió el pescado cocinado, el rango real es **0,685 a 1,05**: eglefino 0,822,
> fletán 0,820, abadejo de Alaska 0,685, caballa española 0,880, salmón coho de piscifactoría 0,899.
>
> Se conserva el 0,78 para el pescado blanco magro, porque las parejas medidas quedan cerca y es lo
> mejor que hay, pero **es una estimación de la USDA, no una medición**. El **salmón** se salió a una
> entrada propia por quedar fuera de rango: el de piscifactoría, que es el que se vende en España, da
> 208 → 206 kcal (**1,01**), y con 0,78 se le metía un +29 % en kcal y un +39 % en grasa.
>
> Y no hay base utilizable para pescado **hervido o al vapor**, que es como se hace a diario la
> merluza en España: en todo SR Legacy hay una sola pareja de calor húmedo emparejable, y también
> está imputada.

**Cereales y pasta**

| Alimento | Factor | Pareja |
|---|---|---|
| Pasta | 2,35 | dry 371 (168927) → cooked sin sal 158 (168928) |
| Arroz blanco | 2,81 | raw 365 (169756) → cooked sin sal 130 (169757) |
| Cebada perlada | 2,86 | raw 352 (170284) → cooked 123 (170285) |
| Arroz integral | 2,98 | raw 367 (169703) → cooked 123 (169704) |
| Quinoa | 3,07 | uncooked 368 (168874) → cooked 120 (168917) |
| Cuscús | 3,36 | dry 376 (169699) → cooked 112 (169700) |
| Bulgur | 4,12 | dry 342 (170688) → cooked 83 (170287) |

**Legumbres**

| Alimento | Factor | Pareja |
|---|---|---|
| Garbanzos | 2,30 | raw 378 (173756) → boiled 164 (173799) |
| Alubias blancas | 2,40 | raw 333 (175202) → boiled sin sal 139 (175203) |
| Alubias negras | 2,58 | raw 341 (173734) → boiled sin sal 132 (173735) |
| Alubias rojas | 2,62 | raw 333 (175193) → boiled sin sal 127 (173740) |
| Lentejas | 3,03 | raw 352 (172420) → boiled sin sal 116 (172421) |
| Habas secas | 3,10 | raw 341 (175205) → boiled sin sal 110 (173753) |

**Tubérculos**

| Alimento | Factor | Pareja |
|---|---|---|
| Patata | 0,75 | white, flesh and skin: raw 69 (170028) → baked 92 (170434) |

**Lo que se dejó fuera a propósito.** Las verduras (brócoli, espinaca, zanahoria, calabacín…) tienen
parejas en el dataset, pero sus factores se mueven entre 0,8 y 1,2 sobre alimentos de 20–35 kcal/100 g:
el efecto en el recuento es ruido, y la dispersión entre variedades es alta (la "espinaca" del dataset
da 1,17 o 1,38 según la especie). Añadirlas metería 10 categorías más de riesgo de falso positivo para
no cambiar ninguna cifra. El boniato se descartó por lo mismo: 0,96 al horno, es decir, sin cambio. La
avena no tiene pareja utilizable, porque sus kcal en cocinado dependen por completo de cuánta agua o
leche se le eche, que es decisión de quien cocina y no una propiedad del alimento.

### 13.5 Cuando la app no reconoce el alimento

Un factor solo se puede aplicar si se sabe **qué** alimento es y **en qué base** están sus datos. De un
producto cualquiera de Open Food Facts no se sabe ninguna de las dos cosas. De ahí tres reglas:

- Si el nombre dice que ya viene cocinado ("arroz cocido", "pollo asado") o que es un derivado o
  preparado ("bebida de arroz", "nuggets de pollo", "hummus", "atún en escabeche"), **no se ofrece
  nada**: sus datos ya están en base cocinada, y convertirlos sería aplicar el factor dos veces.
- Si el nombre coincide con el catálogo, se ofrece la conversión directamente.
- Si no se reconoce pero nada indica que esté cocinado, **se le pregunta al usuario a qué se parece** y
  se usa el factor del alimento que elija, que queda guardado con la comida.

Esa tercera regla es la que hace que la función sirva para cualquier alimento y no solo para los que el
catálogo identifica por el nombre. Lo que no se le pide nunca al usuario es el factor: si se le dejara
teclear un número, la app estaría avalando cifras inventadas. El dato que falta es *qué alimento es*, y
eso sí lo sabe él.

La base por defecto sigue siendo **crudo**, el comportamiento de siempre. Nada cambia salvo que el
usuario elija explícitamente lo contrario.

### 13.6 El suelo de cordura: detectar productos que ya vienen cocinados

**Por qué hizo falta.** La primera defensa era una lista de palabras ("cocido", "nuggets"…). Se
comprobó una a una y tenía agujeros reales: *patatas **prefritas*** no coincide con "fritas" porque la
comparación es por palabra completa; *garbanzos en **tarro***, *arroz en **vasito***, *arroz
**vaporizado***, *merluza **glaseada*** y *pechuga con **solución añadida*** pasaban todas. El enfoque
no estaba incompleto, estaba **mal de raíz**: nunca se acaban las palabras.

**Qué se hace en su lugar.** Comprobar si las kcal del producto son físicamente posibles para ese
alimento en seco. Un árido en seco está entre 333 y 378 kcal/100 g (almidón casi sin agua); el mismo
alimento cocido, entre 83 y 164 (ha absorbido dos o tres veces su peso en agua). El suelo se pone en
**250**, que deja ~85 kcal de margen por abajo y ~83 por arriba.

> ⚠️ **Corregido el 2026-11-27.** Aquí se afirmaba que *"entre 165 y 333 no existe ningún producto de
> esta familia"* y que por tanto 250 partía *"una franja que la física deja libre"*. **La franja no
> está vacía**: barriendo SR Legacy aparecen pasta fresca sin cocer a **288 kcal/100 g** (169727) y
> alubias crudas a **329-330** (royal red y california red). El valor 250 **sobrevive** —ningún
> producto real encontrado lo cruza— pero su margen por arriba es de 38 kcal, no de 85, y el
> argumento de la "franja vacía" era falso.

**El techo, que faltaba (añadido el 2026-11-27).** El suelo solo mira hacia abajo, y por arriba se
colaban productos reales con errores enormes: *patatas paja* (560 kcal/100 g) se convertían a **747**,
*mojama de atún* (330) a 423, y unos *garbanzos tostados* (410) registraban **89 kcal donde hay 205**.
Ninguno llevaba una palabra que la lista de exclusiones pudiera cazar. Ahora hay un techo por familia
—patata 110, aves 200, pescado 250, carne 340, áridos 400— y la asimetría con el suelo no es
arbitraria: la dispersión entre marcas impide poner un **suelo** en carne y pescado, pero no impide
poner un **techo**, porque ningún producto crudo de esas familias se le acerca.

Fuera del rango, la conversión **se bloquea y se avisa**; nunca se corrige el número en silencio.

> ⚠️ Esa promesa de *"y se avisa"* era falsa hasta el 2026-11-27: la conversión se bloqueaba y el
> conmutador de base **desaparecía sin explicación**, porque la variable que lo detectaba se calculaba
> y no se usaba en ningún sitio. Ahora el aviso existe y distingue el motivo.

**Dónde no se aplica, y por qué.** Solo en cereales y legumbres. Se midieron ~500 productos reales de
Open Food Facts y en carne, pescado y patata **no hay señal aprovechable**: la dispersión entre marcas
supera la separación entre crudo y cocinado. Dos hallazgos concretos de esa medición:

- **El 79 % de los productos de pollo ya cocinados marcan *menos* kcal que el pollo crudo** (62–86
  frente a 120): son fiambres inyectados con agua y almidón, donde la dilución del procesado gana a la
  concentración del cocinado. Un detector de dos polos los clasificaría como crudos — fallaría en la
  dirección peligrosa.
- La categoría `pescado` agrupa especies cuyo valor en crudo va de 51 a 208 kcal/100 g (4,08× de
  dispersión) bajo una separación de 1,28×: un salmón crudo está más cerca del "bacalao cocido" que del
  "bacalao crudo". Ningún suelo único puede significar nada ahí.

Esto deja la carne sin red, y es una limitación asumida a conciencia: ahí el error máximo es de ~1,3×
en vez de los 2,3×–4,1× de los áridos, y la carne fresca del supermercado viene cruda prácticamente
siempre. Se prefiere no bloquear a bloquear sin fundamento.

**El suelo se aplica también cuando el usuario elige el alimento a mano**, que si no sería justamente
la puerta por la que colar un producto ya cocinado.

### 13.7 Base por defecto

Carnes y pescado se preseleccionan en **crudo**; cereales y legumbres, en **cocido**. Sigue al uso
real: nadie pesa la pasta en seco después de hervirla. Coincide con lo que hace Fitia, cuyos planes
usan peso cocido salvo en carnes y avena.

### 13.8 Limitación conocida: el supuesto de la base de origen

La conversión da por hecho que los valores del producto están en base cruda. Es cierto para alimentos
frescos, que es lo que el catálogo reconoce, pero **no se puede verificar**: Open Food Facts no tiene
ningún campo que diga en qué base está su tabla nutricional. Las exclusiones por nombre son la
mitigación, no una garantía; un producto mal nombrado puede colarse. Queda anotado como CRU-1 en
`doc/08-backlog-mejoras.md`.

### 13.9 Un factor por macronutriente (2026-11-27)

Hasta esta fecha el factor se aplicaba igual a kcal, proteína, grasa e hidratos. Eso es el supuesto
que §13.2 daba por bueno —"los nutrientes se conservan"— y es falso para la grasa.

Calculando el cociente de cada macro por separado en las 26 parejas del catálogo, **15 divergen más
del 10 %**:

| Alimento | energía | proteína | grasa | divergencia |
|---|---|---|---|---|
| Muslo de pollo | 0,676 | 0,794 | **0,506** | 25 % |
| Cordero picado | 0,996 | **0,669** | 1,191 | 33 % |
| Pasta | 2,348 | 2,248 | **1,624** | 31 % |
| Cebada perlada | 2,862 | **4,385** | 2,636 | 53 % |
| Gambas | 0,859 | 0,838 | **1,821** | 112 % |
| Lomo de cerdo | 0,762 | 0,801 | **0,618** | 19 % |
| Carne picada 85/15 | 0,860 | **0,717** | 0,973 | 17 % |

No se inventa ningún número: cada factor por macro es el cociente de **la misma pareja de la USDA**
que ya cita la entrada, así que aplicarlo reproduce exactamente el valor que la USDA publica para el
alimento cocinado, en los cuatro nutrientes y no solo en las kcal. En el muslo de pollo la grasa pasa
de las 6,1 g/100 g que daba el factor único a las **8,15 medidas**.

Donde la entrada cocinada es una **imputación** de la USDA (el pescado blanco) los cocientes por macro
son idénticos por construcción y no se definen: separarlos sería reproducir la imputación con más
decimales.

**Consecuencia en la persistencia.** Reutilizar un alimento del historial exige devolver sus valores a
base cruda, y eso se hacía multiplicando por el factor. Con uno por macro deja de ser exacto, así que
la fila guarda ahora **los cuatro valores en la base de origen** y deshacer es leer en vez de
calcular. Eso además elimina la última dependencia del catálogo: ni el factor ni el `id` tienen que
seguir existiendo para que el historial sea exacto.

### 13.10 Lo que sigue sin resolver

- **La banda, no el número.** El rendimiento depende del cocinero (tiempo, agua, método) y ningún
  factor único lo captura. Las bandas reales son arroz 2,52-3,81, cuscús 2,0-3,4, patata 0,73-0,92,
  cordero 0,53-0,88. El §12 de este documento ya establece que *"cuando la app muestre un número de
  esta lista, debe presentarse como estimación ajustable, nunca como dato cerrado"*, y **aquí no se
  está aplicando**: la app muestra un número cerrado.
- **Separar por método de cocinado** (patata hervida frente a al horno, arroz al dente frente a
  pasado) exige un selector que no existe. Se intentó resolverlo con entradas separadas y no funciona:
  las palabras "hervida", "cocida" y "vapor" están en las exclusiones globales —correctamente, porque
  un producto que las lleva en el nombre ya tiene sus datos en base cocida— así que una entrada con
  esas palabras clave no se encontraría nunca.
- **No hay factores de retención de macronutrientes en la USDA.** Se comprobó: la *Table of Nutrient
  Retention Factors, Release 6* cubre 26 vitaminas, minerales y alcohol, y **cero macros**. La vía
  "usar retention factors" está cerrada.

---

## 14. Invariantes verificados por tests

`shared/src/commonTest/.../NutritionMathTest.kt` barre **sexo × 6 niveles de actividad × 4 objetivos ×
6 complexiones × 6 ritmos × con y sin pulsera** (unas 2.000 combinaciones) y comprueba en todas:

| # | Invariante | Por qué está |
|---|---|---|
| 1 | `objetivo > 0`, `BMR > 0`, `gasto > 0` | Cordura básica |
| 2 | **`gasto > BMR` siempre** | §7.4 — fue un fallo real |
| 3 | **Un déficit pedido nunca queda en cero** | §7.3 — fue el fallo que destapó el usuario |
| 4 | El objetivo va en la dirección del objetivo elegido | Perder come por debajo, ganar por encima |
| 5 | El déficit nunca supera el 25% del gasto | §7.3 |
| 6 | El ritmo conseguido nunca supera al pedido | Y si no hubo recorte, coinciden exactamente |
| 7 | Los macros siempre suman las kcal objetivo (±15) | §6.3 |
| 8 | El IMC y su categoría son coherentes | — |
| 9 | Lo anunciado en kg coincide con lo anunciado en kcal | El fallo original: etiquetas que mentían |

Los invariantes 2 y 3 **no existían** en la primera versión, y por eso el fallo llegó hasta el usuario.
Cualquier cambio futuro de estas fórmulas debe mantener la tabla verde.

> ### ⚠️ Corrección del 2026-11-22 — esta tabla es verdadera y a la vez engañosa
>
> La auditoría de [11-auditoria-kcal.md](11-auditoria-kcal.md) encontró que **el barrido nunca pasa
> `carryOverKcal` ni `measuredTdee`**: los dos se quedan en su valor por defecto (0 y `null`). Es decir,
> los nueve invariantes están verificados **exclusivamente sobre el subconjunto de parámetros que no
> puede romperlos**. Con esos dos parámetros activos se violan al menos tres:
>
> - **Invariante 5** (déficit ≤ 25 % del gasto): con arrastre negativo el déficit real llega al 50 %.
> - **Invariante 4** (dirección del objetivo): con `gain_muscle` y arrastre negativo, el objetivo cae
>   *por debajo* del mantenimiento.
> - **Invariante 2** (`gasto > BMR`): no se cumple en la rama de TDEE medido, porque el único filtro de
>   `measuredTdee` es `raw ∈ [800, 6000]` y no hay ningún suelo relativo al BMR.
>
> Y la afirmación "unas 2.000 combinaciones" es cierta pero mide menos de lo que parece: el barrido va de
> 18 a 60 años (nunca 14 ni 100, que son los límites que valida el servidor) y `sex` solo toma
> `"male"`/`"female"` (un valor desconocido cae en la rama femenina en silencio, −166 kcal de BMR).
>
> **Regla que se deriva de esto**: un barrido solo respalda los invariantes sobre los parámetros que
> realmente varía. Al añadir un parámetro nuevo a `computeTargets`, **añadirlo al barrido es parte del
> cambio**, no una mejora opcional. Está en el Bloque A del plan de arreglo.

---

## 15. Fuentes

**Metabolismo y gasto energético**
- [Accuracy of Resting Metabolic Rate Prediction Equations in Athletes: A Systematic Review with Meta-analysis (*Sports Medicine*, 2023)](https://link.springer.com/article/10.1007/s40279-023-01896-z)
- [Changes in Energy Expenditure with Weight Gain and Weight Loss in Humans](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5097076/)

**La constante energética**
- [Why is the 3500 kcal per pound weight loss rule wrong? (Hall & Chow)](https://www.researchgate.net/publication/239943510_Why_is_the_3500_kcal_per_pound_weight_loss_rule_wrong)
- [The Energetic Cost of Building Human Skeletal Muscle (preprint)](https://www.biorxiv.org/content/10.64898/2026.08.17.745156v1.full)
- [Stored Energy Increases Body Weight and Skeletal Muscle Mass in Older, Underweight Patients after Stroke](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8465419/)

**Ritmo de pérdida y ganancia**
- [Evidence-based recommendations for natural bodybuilding contest preparation (Helms et al., 2014)](https://pmc.ncbi.nlm.nih.gov/articles/PMC4033492/)
- [Effect of Two Different Weight-Loss Rates on Body Composition and Strength and Power-Related Performance in Elite Athletes (Garthe et al., 2011)](https://www.researchgate.net/publication/51113664_Effect_of_Two_Different_Weight-Loss_Rates_on_Body_Composition_and_Strength_and_Power-Related_Performance_in_Elite_Athletes)

**Adaptación metabólica**
- [Adaptive thermogenesis with weight loss in humans](https://onlinelibrary.wiley.com/doi/full/10.1002/oby.20027)
- [On adaptive thermogenesis: just another weight-loss tale? (*AJCN*)](https://ajcn.nutrition.org/article/S0002-9165(22)00885-1/fulltext)

**Proteína y macronutrientes**
- [Achieving an Optimal Fat Loss Phase in Resistance-Trained Athletes: A Narrative Review](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC8471721/)
- [Effect of Dietary Protein on Fat-Free Mass in Energy Restricted, Resistance-Trained Individuals (*Strength & Conditioning Journal*)](https://journals.lww.com/nsca-scj/fulltext/9900/effect_of_dietary_protein_on_fat_free_mass_in.179.aspx)

**Disponibilidad energética**
- [Energy Availability and RED-S Risk Factors in Competitive, Non-elite Male Endurance Athletes](https://pubmed.ncbi.nlm.nih.gov/34296227/)
- [A real-life snapshot: Evaluating exposures to low energy availability in male athletes](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC11194298/)

**Recomposición corporal**
- [Can You Lose Fat and Gain Muscle at the Same Time? (revisión de Barakat et al., 2020)](https://macrofactor.com/recomposition/)

**Composición corporal y medición**
- [Circumference-Based Predictions of Body Fat Revisited: US Marine Corps Body Composition Survey](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC9008774/)
- [Comparing Alternate Percent Body Fat Estimation Techniques for US Navy Body Composition Assessment](https://www.researchgate.net/publication/331846116_Comparing_Alternate_Percent_Body_Fat_Estimation_Techniques_for_United_States_Navy_Body_Composition_Assessment)

**Entrenamiento**
- [The Resistance Training Dose-Response: Meta-Regressions on Volume and Frequency (*Sports Medicine*, 2025)](https://link.springer.com/article/10.1007/s40279-025-02344-w)
- [How many times per week should a muscle be trained? (Schoenfeld & Grgic, 2019)](https://www.semanticscholar.org/paper/How-many-times-per-week-should-a-muscle-be-trained-Schoenfeld-Grgic/5f3b9845fdfd15200bdac0f7b9b36da457c48025)
- [Dose-response relationship between weekly resistance training volume and increases in muscle mass (Schoenfeld et al., 2017)](https://www.semanticscholar.org/paper/Dose-response-relationship-between-weekly-training-Schoenfeld-Ogborn/0d34206f962394983054451cddd8a3b91818f732)

**Nivel de actividad: pasos y calorías activas (§16)**
- [Dietary Reference Intakes: Energy — cap. 4, "Factors Affecting Energy Expenditure and Requirements" (National Academies)](https://www.nationalacademies.org/read/26818/chapter/6)
- [Human energy requirements (FAO/WHO/UNU, 2001)](https://openknowledge.fao.org/server/api/core/bitstreams/65875dc7-f8c5-4a70-b0e1-f429793860ae/content)
- [A step-defined sedentary lifestyle index (*Appl Physiol Nutr Metab*)](https://cdnsciencepub.com/doi/10.1139/apnm-2012-0235)
- [How much locomotive activity is needed for an active physical activity level (calorimetría de 24 h)](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC3245484/)

**Pulsera / Health Connect**
- [ActiveCaloriesBurnedRecord — Android Developers](https://developer.android.com/reference/android/health/connect/datatypes/ActiveCaloriesBurnedRecord)
- [TotalCaloriesBurnedRecord — Android Developers](https://developer.android.com/reference/android/health/connect/datatypes/TotalCaloriesBurnedRecord)
- [Xiaomi FAQ: calorías, pasos y de pie en la pulsera](https://www.mi.com/uk/support/faq/details/KA-171111/)

> **Sobre la calidad de las fuentes**: las de `developer.android.com`, `link.springer.com`,
> `pmc.ncbi.nlm.nih.gov`, `ajcn.nutrition.org` y `journals.lww.com` son primarias o revisadas por pares.
> Las de `researchgate.net` y `semanticscholar.org` son registros de artículos revisados por pares. La
> de `macrofactor.com` es un resumen divulgativo de una revisión científica, usada porque no se pudo
> acceder al original — **marcada como secundaria**. El preprint de bioRxiv **no ha pasado revisión por
> pares**.

---

## 16. Nivel de actividad: pasos y calorías activas ✅🟡

Elegir entre "ligero" y "moderado" a ojo es **la mayor fuente de error de todo el cálculo**: entre dos
escalones contiguos hay 250-350 kcal/día, y las descripciones clásicas ("1-3 días de ejercicio por
semana") no dicen nada de lo que uno hace las otras 23 horas. Esta sección recoge las dos referencias
comprobables que la app pone debajo de cada nivel para que la elección deje de ser una corazonada.

Implementado en `shared/.../nutrition/ActivityBenchmarks.kt` y verificado en
`ActivityBenchmarksTest.kt`.

### 16.1 Pasos diarios ✅🟡

Rangos de la clasificación por podómetro de **Tudor-Locke y Bassett, "How many steps/day are enough?
Preliminary pedometer indices for public health", *Sports Medicine* 2004; 34(1):1-8**, que es la
referencia estándar en salud pública.

| Nivel en la app | Pasos/día | Banda original |
|---|---|---|
| Sedentario | < 5.000 | *sedentary* |
| Ligero | 5.000–7.499 | *low active* |
| Moderado | 7.500–9.999 | *somewhat active* |
| Activo | 10.000–12.499 | *active* |
| Muy activo | > 12.500 | *highly active* |

**Dos advertencias de honestidad:**

1. 🟡 El texto original **no se pudo leer directamente** (PDF y PubMed tras verificación anti-robot).
   Los cortes vienen de varias fuentes secundarias independientes que coinciden entre sí. Las bandas
   bajas son unánimes; en la frontera superior hay literatura posterior (la revisión de 2008) que
   mueve el último corte a 12.000.
2. 🟡 **La correspondencia entre estas bandas y los multiplicadores 1,2 / 1,375 / 1,55 / 1,725 / 1,9
   es nuestra, no está publicada.** Son dos escalas ordinales de cinco niveles del mismo concepto y
   alinearlas es razonable, pero ningún trabajo dice que "7.500-9.999 pasos" equivalga a 1,55.

Por eso esto se presenta como **orientación para elegir**, nunca como el dato del que sale el gasto.

### 16.2 Calorías activas equivalentes ✅

Mucha gente no mira sus pasos: mira las **calorías activas** del final del día. Ese número además
recoge lo que no deja pasos (fuerza, bici), así que para elegir nivel sirve incluso mejor.

**No hay tabla fija y no puede haberla**: las calorías dependen del peso, la altura, la edad y el
sexo. "Moderado = 500 kcal" sería una cifra inventada. Se derivan del BMR real de la persona.

#### El ajuste que hace que la cifra sea comparable con la pulsera

Esta es la parte que no es una resta trivial, y la razón de que esto esté documentado aquí:

- El **PAL** se define como `gasto total ÷ BMR` (National Academies, *Dietary Reference Intakes*,
  cap. 4), y el gasto total incluye el **efecto térmico de los alimentos** (TEF), en torno al **10 %
  del total**. Por tanto `BMR × (PAL − 1)` **no son calorías de movimiento**: son movimiento más
  digestión.
- Health Connect entrega `ActiveCaloriesBurnedRecord`, que es **solo actividad**: ni basal ni
  digestión. Es lo que `NutritionMath.tdee` suma al BMR en modo pulsera.

Poner las dos cifras lado a lado sin descontar el TEF le diría al usuario que su pulsera marca
sistemáticamente un ~10 % menos que el nivel que eligió, y la conclusión natural sería bajar de nivel
y comer de menos. **Es la misma familia de fallo que ya llegó al usuario** (objetivo de ganar peso,
déficit encubierto de 400-500 kcal/día; ver `07-registro-sesiones.md`). Así que se descuenta:

```
activas = gasto − BMR − TEF
        = BMR × PAL − BMR − 0,10 × BMR × PAL
        = BMR × (PAL × 0,90 − 1)
```

Resultado para un BMR de 1.800 kcal:

| Nivel | PAL | Activas (escala pulsera) |
|---|---|---|
| Sedentario | 1,200 | ~145 kcal/día |
| Ligero | 1,375 | ~430 kcal/día |
| Moderado | 1,550 | ~710 kcal/día |
| Activo | 1,725 | ~995 kcal/día |
| Muy activo | 1,900 | ~1.280 kcal/día |

Se muestran **redondeadas a la decena** y con un `~` delante: escribir "711 kcal" afirmaría tres
cifras significativas sobre una estimación cuyo eslabón más débil tiene 250-350 kcal de margen.

#### Comprobación cruzada por un camino independiente

Andar cuesta del orden de **0,75 kcal/kg/km brutos**; con un paso de ~0,72 m eso son ~0,04 kcal por
paso para 75-80 kg. Los ~4.000 pasos típicos de un sedentario dan **~170 kcal**, frente a las ~145
que devuelve la fórmula. Los dos caminos, que no comparten ni una constante, coinciden en orden de
magnitud. Hay un test que lo fija (`el_orden_de_magnitud_coincide_con_el_coste_de_andar`).

#### 🟡 Limitación conocida

Las fuentes **no son unánimes** sobre si el TEF está dentro del PAL: la definición académica
(TEE/BMR, National Academies) dice que sí; varias calculadoras comerciales dicen que no. Se sigue la
académica, que es la que define el término. Si esa decisión se revisara, el único sitio que hay que
tocar es `ActivityBenchmarks.TEF_FRACTION`.

Y todo esto hereda la incertidumbre del multiplicador, que `NutritionMath.activityMultiplier`
documenta como *"el eslabón más débil del cálculo"*. **Es orientación para elegir nivel, no una
medida.**
