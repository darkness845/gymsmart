# Continuar aquí — estado para la próxima sesión

> **Si eres una sesión nueva de Claude**: lee esto primero, es la foto rápida del estado actual.
> No es un historial — para el detalle de qué se hizo y por qué, mira
> [07-registro-sesiones.md](07-registro-sesiones.md) (bitácora completa) y
> [08-backlog-mejoras.md](08-backlog-mejoras.md) (cada hallazgo con su estado).
> Este documento se actualiza cada vez que se termina algo, no solo al final de la sesión.

**Última actualización**: 2026-11-28 (**fases 1 a 5 de la auditoría de alimentos implementadas**: 177 tests, verde en los cinco targets, sin commitear al remoto todavía. **El servidor hay que redesplegarlo en Render.** Ver la sección inmediatamente siguiente). Antes: 2026-11-27 (**segunda auditoría del día: comité de 5 paneles sobre el registro de alimentos y macros**). Antes: 2026-11-27 (auditoría del comité multidisciplinar sobre el cálculo de kcal — solo diagnóstico, nada implementado). Antes: 2026-11-27 (N3/W5 y limpieza del tema viejo + AUD-16, sin commitear).

## 🔴 2026-11-27 — Auditoría del registro de alimentos: comité de 5 paneles (sin implementar)

El usuario pidió auditar (no rediseñar) el sistema de registro de alimentos, con el objetivo de
llegar al nivel de una app profesional de macros (tipo Fitia) con producto español. Se lanzaron 5
subagentes en paralelo (composición de alimentos, bromatología, macronutrientes, bases de datos
nutricionales, ingeniería del cálculo) y esta sesión hizo de auditor independiente. Informe completo
en [13-auditoria-nutricion-2026-11-27.md](13-auditoria-nutricion-2026-11-27.md).

**Lo que manda sobre todo lo demás — `search.pl` de Open Food Facts devuelve 503 el 100 % de las
veces.** La búsqueda de alimentos de marca está **caída**, y la app degrada en silencio a los 90
genéricos locales mostrando "Sin resultados", que es un mensaje falso. El servidor no deja rastro en
los logs porque un 503 no lanza excepción. Verificado por el panel de datos (0/12 al ritmo legal de
10 req/min) y **reconfirmado de primera mano por esta sesión (3/3 → 503)**, con Search-a-licious y la
API de producto por EAN respondiendo 200 desde la misma IP. **`OFF-2` en `doc/08` está obsoleto**:
afirma que el endpoint funciona.

**Lo que está verificado como correcto** (no tocar): los 89 `fdcId` de `GenericFoods.kt` existen, son
todos SR Legacy y los 4 valores de los 89 coinciden con la API oficial de FoodData Central. No hay
error de redondeo acumulado. El "deshacer" de la conversión crudo/cocinado es exacto. Las 42
desviaciones de la tabla frente a 4/4/9 **son correctas** (USDA usa Merrill & Watt, no 4/4/9).

**Solape con la auditoría de kcal de hoy** (declarado en §0.3 del informe): el panel de
macronutrientes **re-derivó de forma independiente** el C-1 (falta el TEF) y el C-4 (proteína sobre
peso total) de `doc/12`. Eso sube la confianza en esos dos hallazgos; no son nuevos. Y en los
multiplicadores de actividad **los dos comités se contradicen**: `doc/12` P1-6 dice "no tocar la
constante, corregir solo el texto", este comité lo clasifica como error real de −361 kcal/día.
Dirimido en §D-1: los dos parten del mismo hecho y convergen en la misma conclusión práctica —
**no se puede corregir sin migrar a la vez el `adaptive_factor` guardado**, que necesita decisión
explícita del usuario.

**Nuevo y no cubierto por ninguna auditoría anterior**: toda la capa de datos (§2.1), la conversión
crudo/cocinado (§2.2), la curación de la tabla de genéricos (§2.3) y 10 hallazgos de implementación
(§2.4). Entre lo más grave: un solo factor de cocción aplicado a los cuatro macros (**+54 % de grasa**
en carne picada), el 0,78 del pescado que es una **imputación de USDA y no un dato** (salmón: +29 %),
el ranking que **no plega plurales** ("huevos" pierde el genérico; "pechugas de pollo" devuelve lista
vacía), **cero validación de rangos** (entran productos con P+C+F = 100,6 g/100 g), el escáner que
**no filtra los 0 kcal** (escanear un vino registra 0 kcal), y el **doble conteo invisible** cuando
falla la red tras un INSERT.

**Estado**: el usuario pidió implementarlo. **Fase 0 completa y Fase 1 a medias** (commit aparte):

- Búsqueda migrada a **Search-a-licious** con hidratación por EAN (hasta 12 por búsqueda, en tandas
  de 4, compartiendo la caché de EAN con el escáner). `search.pl` queda de respaldo y **cualquier
  status ≠ 200 se loguea**. Nueva bandera `upstreamFailed`: la app ya dice "no se ha podido consultar
  la base de marcas" en vez de "Sin resultados".
- ⚠️ **Al implementarlo se corrigió un error del informe**: el campo `nutriments` del índice de
  búsqueda **sí existe**, pero es `null` en muchos productos españoles que sí tienen datos (tres
  leches medidas, una con `completeness` 0,975). Por eso hay que hidratar — la conclusión del panel
  era correcta, el motivo que daba no. Está marcado en §3 Fase 0 del informe.
- **Filtro de sanidad** en servidor para búsqueda y escáner: techo de 902 kcal, macros en 0..100,
  P+C+G ≤ 101, contraste cruzado kcal↔Anexo XIV y descarte por `data_quality_errors_tags`.
  **El escáner ya no deja registrar un vino a 0 kcal.** Deduplicación por (nombre, marca).
- Energía estimada con los factores del **Anexo XIV** (fibra, polioles, alcohol) en lugar de 4/4/9.
- Base **100 ml** rotulada como tal, y `roundToInt` en vez de `toInt` en la lista de resultados.
- **30 tests nuevos** (19 en `server`, 11 en `composeApp`). `:server:test` está verde por primera
  vez: el test esqueleto de la plantilla llevaba fallando desde siempre (comprueba una ruta `/` que
  no existe y exige `TURSO_DB_URL`) y se ha desactivado con su motivo escrito, **sin borrarlo**.
  → **Ya se puede añadir `:server:test` a la lista de verificación de CLAUDE.md.**

**2026-11-28 — las fases 1 a 5 están hechas.** El usuario pidió ejecutarlo todo. Resumen por fase en
`doc/07` y detalle en el informe; lo que hay que saber para seguir:

- **Fase 1** ✅ idempotencia del guardado (`client_id` + `INSERT OR IGNORE`) y validación de entrada.
- **Fase 2** ✅ plegado de singular/plural en la búsqueda, aviso al bloquear la conversión, redondeos.
- **Fase 3** ✅ cuatro factores corregidos contra el volcado de SR Legacy, techo de cordura por
  familia, y **un factor por macro** con los valores de origen persistidos.
- **Fase 4** ✅ catálogo de 89 → **137 genéricos**, generador versionado en `tools/`, medidas caseras
  y alias. Falta solo llegar a los 300-400 que proponía el informe: **se amplía editando
  `tools/genericos.csv` y regenerando**, no a mano.
- **Fase 5** 🟡 atribución ODbL hecha. CIQUAL (Licence Ouverte, redistribuible) sin integrar; BEDCA
  descartada por no tener licencia publicada.

**Lo único que NO se ha tocado, y a propósito**: los multiplicadores de actividad y el TEF del modo
pulsera (§D-1 del informe y C-1 de `doc/12`). Corregirlos sin migrar a la vez el `adaptive_factor` ya
guardado **duplicaría la corrección**, y eso es una decisión sobre los datos históricos del usuario.
Está pendiente de que él decida.

**Tres cosas del informe resultaron inexactas al implementarlas** y están marcadas en él: el campo
`nutriments` del índice de búsqueda (existe pero no es fiable), el umbral del filtro de sanidad (el
aceite de 100,6 g se acepta a propósito) y la separación de la patata por método (imposible sin un
selector que no existe).

**Pendiente de verificar en el móvil**: nada de esto se ha ejecutado en un dispositivo. Y sobre todo,
**el servidor hay que redesplegarlo en Render**: sin eso la búsqueda sigue caída en el móvil, porque
todo el arreglo de Open Food Facts vive en el servidor.

**Decisión pequeña que puede querer revertir**: el selector de ración muestra ahora un botón con la
ración del propio alimento ("1 unidad grande · 50 g"). No son los chips de 50/100/150/200 g que pidió
quitar —aquellos eran cantidades inventadas e iguales para todo— pero están en el mismo sitio. Antes: 2026-11-26 (tarea 7 cerrada en código; **estilo Melocotón aplicado a todas las pantallas en uso**; commits por temas y push hechos a petición del usuario. Pendiente: redesplegar el servidor, probar en el móvil, variante oscura de Melocotón).

## 🔎 2026-11-27 — Segunda auditoría del cálculo de kcal: comité de 3 paneles (sin implementar)

El usuario pidió auditar (no rediseñar) el sistema de cálculo energético con un comité de expertos.
Se lanzaron 3 subagentes en paralelo (metabolismo/gasto, nutrición/macros, peso/autoajuste) y esta
sesión hizo de auditor crítico para contrastarlos. Resultado completo en
[12-auditoria-comite-kcal-2026-11-27.md](12-auditoria-comite-kcal-2026-11-27.md).

**Veredicto de fondo: el modelo sigue siendo correcto** (confirma lo ya resuelto en
[11-auditoria-kcal.md](11-auditoria-kcal.md)). Lo nuevo no son errores de aritmética (barrido de
~30.000 perfiles sin ninguno) sino: una incoherencia interna real en modo pulsera, falta de
individualización en los extremos de población (mayores, adolescentes, obesidad severa), y sesgos
fisiológicos que el autoajuste no filtra (glucógeno/agua al cambiar de objetivo, ciclo menstrual).

**Hallazgos críticos/altos (C-1 a C-6 en el doc), sin implementar, por orden de bloque A propuesto**:
1. **C-1** 🔴 Modo pulsera no descuenta el TEF (`NutritionMath.kt:97-108`): mismo gasto real sale un
   10% más bajo que en modo fórmula → en "ganar músculo" el superávit real es ~0 kcal. **Ojo**: tocarlo
   exige decidir qué hacer con los `adaptive_factor` ya guardados en producción (pueden haber absorbido
   el sesgo) — necesita confirmación explícita, no solo código.
2. **C-2** 🔴 Sin suelo absoluto en kcal: mujeres mayores/pequeñas con normopeso pueden caer en rango
   VLCD (ej. 806 kcal). Propuesta: suelo duro 800 + aviso bajo 1200♀/1500♂.
3. **C-4** 🟠 Proteína calculada sobre peso total también en obesidad (160 kg → 320 g, 57% de las kcal).
4. **C-5** 🟠 El autoajuste lee el glucógeno/agua del cambio de objetivo como si fuera gasto real
   (sesgo simulado +278 kcal/d la primera ventana). Propuesta: `goal_changed_at` + 14 días de gracia.
5. **C-6** 🟠 Guardar perfil reenvía el peso cargado y se graba como pesaje de hoy (LOCF), inflando el
   recuento de pesajes del autoajuste sin dato nuevo.

Bloque B (edad en los extremos C-3, ventanas de datos C-7) y Bloque C (tendencia de peso EWMA C-8,
texto del autoajuste, filtro de Kalman) son de menor prioridad — todo el detalle y las fórmulas
propuestas están en el doc 12.

**Decisiones del usuario (2026-11-28)**: resetear los `adaptive_factor` guardados al corregir C-1;
aplazar la política de menores (C-3); implementar **solo el Bloque A**.

### ✅ Fase 1 del Bloque A — C-2, suelo absoluto y aviso de objetivo bajo (2026-11-28, sin commitear)

- **`ABSOLUTE_FLOOR_KCAL = 800`**: suelo duro, combinado con el de disponibilidad energética por `max`
  (manda el más restrictivo) en el nuevo helper `NutritionMath.safetyFloor()`. Toda la lógica posterior
  (acotación al mantenimiento, `noSafeDeficit`, el `max` final) no cambia por tener dos suelos.
- **Aviso `belowSoftFloor` + `softFloorKcal`** (1200♀/1500♂, AHA/ACC/TOS 2013): no bloquea, solo informa.
  Se mide sobre el objetivo **sostenido** (no el del día, que el arrastre de la pulsera mueve) y **solo
  con déficit real** — avisar en mantenimiento sería ruido sobre un plan que no recorta nada.
- **`effectiveMaxRateFor` aprende el suelo nuevo**: si no, el extremo del `RateSlider` volvería a
  prometer un ritmo que el cálculo no entrega, que es AUD-5 otra vez. Se aplica **dentro** de la
  función, así que las pantallas siguen pasando solo el suelo de disponibilidad energética.
- **`energyFloor` pasa a informar del suelo aplicado**, no del crudo: antes la UI podía escribir "no
  bajamos de 2025" junto a un objetivo de 1925. Ahora `energyFloor <= targetKcal` siempre, con test.
- **El aviso de la UI distingue qué suelo mordió**: atribuir el suelo absoluto a "tu masa magra
  estimada" sería nombrar un límite que no es el que actuó (la familia de AUD-2).
- **Alcance real del suelo absoluto, medido**: solo muerde con masa magra estimada < ~26,7 kg (mujeres).
  Los casos del informe quedan por encima y los sigue gobernando la disponibilidad energética (mujer de
  80 a/42 kg → 806 kcal, verificado en test). **Es una red para la cola, no el arreglo de los objetivos
  bajos** — la causa de fondo (suelo sin restar ejercicio, `lowBmiGuard` con umbral de adulto) está en
  el Bloque B, sin abordar.
- **7 tests nuevos** (55 en `NutritionMathTest`): suelo absoluto en el barrido, `energyFloor` nunca por
  encima del objetivo, el caso donde muerde (40 kg/85 a → 800) y el que no (42 kg/80 a → 806), el aviso
  con y sin déficit, y que el tope del deslizador coincide con lo que entrega el cálculo.
- **Verificado**: `:shared:jvmTest :server:compileKotlin :composeApp:compileKotlinJvm
  :composeApp:jvmTest :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinWasmJs
  :composeApp:compileKotlinJs`, todo verde. Sin verificar en dispositivo (el texto del aviso nuevo).

### ✅ Fase 2 del Bloque A — C-4, proteína acotada por masa magra (2026-11-28, sin commitear)

- **`proteinTargetG(peso, objetivo, magra)`** nueva: `min(perKg × peso, perKg/0,77 × max(magra, 0,40 ×
  peso))`. En déficit eso es `min(2,0 × peso, 2,6 × magra)`, y ese 2,6 **no es un número elegido a
  mano**: sale de hacer explícito el supuesto que la cifra por peso total ya llevaba dentro (§6 justifica
  los 2,0 g/kg como la traducción de los 2,3-3,1 g/kg de masa magra de Helms "para un 20% de grasa").
- **Por qué 0,77 y no 0,80**: a 0,80 el tope muerde con cualquier grasa estimada >20% —media población
  adulta— y recortaría 22 g a un hombre de 95 kg sin ningún problema. C-4 es la cola de obesidad severa.
- **Por qué no el tope por IMC 25 que proponía el panel**: el IMC no distingue músculo de grasa y esta es
  una app de gimnasio. A un hombre de 90 kg/180 cm (IMC 27,8) le recortaría de 180 a 162 g tratándolo de
  obeso; por masa magra se queda en 178 g. Verificado con números.
- **Suelo `PROTEIN_MIN_LEAN_FRACTION = 0,40`**, solo en el camino de la proteína: Deurenberg está
  extrapolado (válido hasta IMC 41) y satura en 70% de grasa, afirmando 30% de masa magra, que es
  imposible. Sin el suelo, una mujer de 150 kg recibiría 117 g (0,78 g/kg); con él, 156 g.
  **No se corrige `bodyFatPctEstimate` en su origen a propósito**: subiría también el suelo de
  disponibilidad energética de esa mujer de 1350 a 2025 kcal, por encima de su tope del 25%, recortando
  su déficit de 642 a 542 kcal/día y rompiendo los 500-750 kcal que AHA 2013 recomienda en obesidad —
  que la propia auditoría dio por bueno. Medido antes de decidirlo.
- **Resultados medidos** (perder, ritmo por defecto): hombre 160 kg 320 g → **184 g** (2,6 g/kg de magra,
  32,7% de kcal, 238 g de carbohidratos); mujer 150 kg → **156 g**; hombre 90 kg musculado → **178 g**
  (sin castigo); usuario real 75,5 kg → **151 g, sin cambio**.
- **Se descartaron las dos banderas que la auditoría pedía añadir a `macrosDegraded`**, después de
  implementarlas y medirlas: el techo del 35% de kcal marcaba como "reparto no realista" **el perfil real
  del usuario** (1625 kcal, 151 g = 37,2%, que es un corte de libro) — la fracción de kcal mide el
  déficit, no la proteína — y el mínimo de 130 g de carbohidratos daba falso positivo en la mujer de
  50 kg (114 g, razonable para su tamaño). Ninguna aportaba un verdadero positivo: **el tope aguas arriba
  ya impide la patología**, así que no hay nada que detectar. Decisión fijada con un test.
- **5 tests nuevos** (60 en `NutritionMathTest`): los cuatro perfiles de arriba, el invariante de barrido
  "la proteína nunca pasa de 3,1 g/kg de masa magra" (el que de verdad cierra C-4, sustituyendo a la
  bandera), que el tope nunca *sube* la proteína, y que un corte normal no dispara el aviso de degradado.
- **Verificado**: los siete targets de siempre, todo verde.

### ✅ Fase 3 del Bloque A — C-6, no grabar pesos arrastrados como pesajes (2026-11-28, sin commitear)

- **`ProfileRequest.recordWeighIn: Boolean = true`** en cliente y servidor. `upsertProfile` solo escribe
  en `weight_history` si viene `true`.
- **Solo uno de los cuatro caminos cambia.** Localizados todos los que construyen un `ProfileRequest`:
  onboarding (`OnboardingScreen.kt:181`), guardado físico (`ProfileScreen.kt:285`) y alta de peso
  (`WeightScreen.kt:497`) **sí** son pesajes y se quedan en `true` — en los tres el usuario está
  confirmando su peso, aunque no lo cambie, y registrar los días sin cambio es deliberado (AUD-12). El
  que pasa a `false` es el reguardado derivado de **editar los datos personales**
  (`ProfileScreen.kt:257`), donde el peso que viaja es el que estaba cargado y puede tener días.
- **AUD-12 queda intacto**: lo que se distingue no es "el peso cambió o no", es "el usuario se ha pesado
  o no". El arreglo que proponía el panel ("escribir solo si el valor difiere") sí habría deshecho
  AUD-12, y por eso se descartó al planificar.
- **El `true` por defecto es la parte delicada**: el JSON del servidor es `Json.Default` (estricto), y las
  apps ya instaladas no mandan el campo. Si el defecto fuera `false` dejarían de registrar pesajes **en
  silencio** y el autoajuste se quedaría sin datos sin avisar. **3 tests nuevos** en
  `server/.../routes/ProfileRequestTest.kt` (primeros tests de la capa de rutas) fijan exactamente eso:
  cuerpo de app vieja → `true`, campo respetado cuando se manda, y cuerpo sin `hasWearable` tampoco falla.
- **Lo que NO está verificado**: que al editar los datos personales deje de aparecer una fila en
  `weight_history`. `upsertProfile` necesita Turso real y `TursoService` es una clase concreta sin
  interfaz, así que cubrirlo exigiría introducir una abstracción solo para el test — más cambio que el
  arreglo. **Queda pendiente de comprobar en el móvil.**
- **Verificado**: `:shared:jvmTest :server:test :composeApp:compileKotlinJvm :composeApp:jvmTest
  :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinWasmJs :composeApp:compileKotlinJs`.

### ✅ Fase 5 del Bloque A — C-5, periodo de gracia tras cambiar de objetivo (2026-11-28, sin commitear)

- **Columna `goal_changed_at`** (`ALTER TABLE` idempotente, igual que las de arriba) + campo en
  `UserProfile`. **Solo la mueven `goal` y `goalRate`**: cambiar peso, altura o actividad no desplaza
  glucógeno, así que descartar medidas por eso sería tirar datos buenos. El ritmo se compara **con
  epsilon**, no con `!=`: es un Double que viaja como texto, y una diferencia de redondeo que disparara
  una gracia espuria dejaría al usuario sin autoajuste dos semanas sin motivo.
- **`null` = sin gracia**: los perfiles anteriores a la columna se miden como antes, en vez de bloquear
  el autoajuste dos semanas a quien ya llevaba tiempo con su objetivo puesto.
- **La columna va DENTRO del `INSERT OR REPLACE`**, no en un `restoreStatement`. Es la misma trampa que
  obliga a existir a `restoreStatement` (el REPLACE borra la fila y toda columna fuera de la lista vuelve
  a NULL) y la que habría roto el centinela de la Fase 4 si se hubiera puesto en esta tabla. Como los
  args de Turso no admiten NULL, cuando no hay valor se elige la variante de SQL que no menciona la
  columna — mismo patrón que `saveAdaptiveTdee`.
- **`ADAPTIVE_GOAL_CHANGE_GRACE_DAYS = 14`** (no los 10 del informe: coherencia con el resto de
  constantes `ADAPTIVE_*` y extremo prudente de lo medido).
- **La cobertura se mide sobre la ventana efectiva**, no sobre los 28 nominales. Sin esto, un cambio hace
  16 días dejaría 2 días útiles de 28 → cobertura 7% → el mensaje culparía al usuario de no registrar
  comidas que sí registró.
- **`AdaptiveWindow` nuevo** (`server/.../services/AdaptiveWindow.kt`): la aritmética de fechas extraída
  a una función pura para poder testearla sin Turso. **7 tests**, incluidos los dos bordes (gracia que
  vence hoy, cambio hoy mismo), el caso nulo y un barrido de 0 a 120 días.
- **Dos cosas que aparecieron al revisar la integración:**
  1. **La gracia va antes del enfriamiento.** Al revés, el usuario veía "ya ajustado esta semana" (7
     días) y el mensaje le cambiaba a mitad de la espera por otro motivo. La gracia dura 14 y explica
     mejor lo que pasa, así que cubre todo el periodo con un solo mensaje.
  2. **`recalculating: Boolean`** nuevo en `NotEnoughData` y en la respuesta. La tarjeta del perfil
     remataba *todo* "no hay ajuste" con "registra tus comidas y pésate con regularidad", que durante la
     gracia es un **consejo falso**: el usuario puede estar registrando todo y la pausa seguiría igual.
     Es la familia de AUD-2 (decirle algo que contradice lo que hace el cálculo), así que ese consejo se
     suprime cuando la pausa es deliberada.
- **Consecuencia que conviene saber**: tras cambiar de objetivo pasan ~28 días hasta que el autoajuste
  puede volver a ajustar (14 de gracia + 14 de ventana mínima). Es el precio de no medir agua.
- **Verificado**: los siete targets. Sin verificar en dispositivo: el texto de la tarjeta en gracia.

### ✅ Fase 4 del Bloque A — C-1, TEF en modo pulsera + reseteo del factor (2026-11-28, sin commitear)

**Bloque A completo.** Esta era la fase que más kcal/día mueve.

- **`tdee()` en modo pulsera**: `(bmr + activas) / (1 − TEF_FRACTION)`, con el suelo `bmr × 1,2` que
  antes solo tenía la rama de al lado (P1-2: sin él, empezar a tener datos de pulsera podía *bajar* el
  objetivo 193 kcal respecto al respaldo "no tengo datos").
- **`TEF_FRACTION` se movió de `ActivityBenchmarks` a `NutritionMath`**. No es cosmético: el hallazgo
  existía precisamente porque dos archivos opinaban sobre el TEF y uno lo descontaba para *mostrar* la
  cifra mientras el otro no lo reponía al *calcular* el gasto. Con un solo dueño —el que calcula— esa
  divergencia no puede reabrirse en silencio. Además `ActivityBenchmarks` ya dependía de
  `NutritionMath`, así que la referencia inversa dejaba las dos dependiendo la una de la otra.
- **La corrección es comprobable algebraicamente, no a ojo**: `activeKcalFor` genera las activas como
  `BMR × (PAL × (1 − TEF) − 1)`, así que reinyectarlas da `(BMR + activas)/(1 − TEF) = BMR × PAL`
  **exactamente**. Hay un test nuevo que lo verifica **nivel por nivel**: si alguien cambia una de las
  dos fórmulas sin la otra, se cae.
- **Un test existente afirmaba el fallo** (`con datos de pulsera el gasto es basal mas actividad media`,
  `assertClose(bmr + 600, …)`). Reescrito, con el motivo del cambio de expectativa escrito dentro.
- **Efecto medido** (perfil real del usuario, 450 kcal activas): gasto estimado **2255 → 2505**.
  - *Ganar músculo*: el objetivo viejo (2566) era un superávit real de **+60 kcal** frente al gasto
    verdadero de 2505, no los +311 que se pedían. Es literalmente "quiero ganar y no gano". Ahora: 2817,
    +311 reales.
  - *Perder grasa*: el objetivo viejo (1691, ya recortado por el tope) era un déficit real del **32,5 %**
    mientras el sistema informaba 25 %. Ahora: 1883, un 24,8 % real. **Reproduce exactamente las dos
    cifras que afirmaba la auditoría**, calculadas por un camino independiente.
- **Migración con tabla `schema_migrations`** (`id` + `applied_at`) y helper `applyOnce`, que mete los
  cambios y el marcador en **una sola transacción** `BEGIN IMMEDIATE`/`COMMIT`: si la red falla a mitad,
  no se confirma ninguno de los dos y el próximo arranque reintenta. Al revés, una migración podría
  quedar marcada sin haberse aplicado.
- **Por qué NO un centinela en `user_profiles`** (era el plan inicial, y lo habría roto): `upsertProfile`
  usa `INSERT OR REPLACE`, que borra la fila, así que el centinela volvería a NULL en el primer guardado
  de perfil y el reseteo se dispararía otra vez en el siguiente arranque — borrando un factor legítimo de
  semanas. Es la misma trampa que obliga a existir a `restoreStatement`.
- **Validado contra SQLite local** (no contra producción): reseteo en el primer arranque → el autoajuste
  guarda un factor nuevo → **segundo arranque en frío y el factor sobrevive**. Comprobado en la misma
  prueba que `goal_changed_at` sobrevive al `INSERT OR REPLACE` (por ir dentro de la lista) y que el
  marcador sobrevive porque vive en otra tabla.
- **Documentación científica actualizada**: §1.3 de `doc/10` ahora lleva la fórmula correcta con el
  porqué, y §1.2 corrige la afirmación inexacta que señaló P1-6 (los multiplicadores altos **sí** están
  alineados con FAO/WHO e IOM; el que queda fuera es el 1,2 de sedentario, por debajo de todas las
  referencias de agua doblemente marcada — se deja igual porque el factor del autoajuste lo cubre, pero
  con su dirección de sesgo escrita).
- **Verificado**: los siete targets, todo verde.

## ⚠️ Pendiente antes de dar el Bloque A por terminado

1. **Desplegar las fases 1, 2 y 4 juntas.** Las tres mueven el objetivo en kcal y el reseteo del factor
   tiene que caer con el último cambio de fórmula; si no, el factor pasa un tiempo compensando una
   fórmula que ya no existe. Las fases 3 y 5 son independientes.
2. **La migración corre sola en el primer arranque tras desplegar** y borra `adaptive_factor` y
   `adaptive_tdee` de producción (autorizado por el usuario el 2026-11-28). Tras eso, el autoajuste
   necesita ~2 semanas de datos para volver a tener una corrección.
3. **Probar en el móvil** lo que no se puede verificar aquí: el aviso de objetivo bajo (Fase 1), que
   editar los datos personales ya no cree una fila en `weight_history` (Fase 3), y el texto de la tarjeta
   durante el periodo de gracia (Fase 5).
4. **Nada está commiteado**, y siguen los 11 commits previos sin subir.
Las fases 1, 2 y 4 van en el **mismo despliegue**: las tres mueven el objetivo en kcal y el reseteo del
factor tiene que caer con el último cambio de fórmula.

---

## ✅ 2026-11-27 — Aviso de descanso como notificación real + insignia de unión (sin commitear)

Pedido por el usuario antes de GPS-5/6, PERM-1 y ERR-1 (que siguen siendo lo siguiente).

- **Insignia "Unido con"** (lista de ejercicios del día): era una píldora naranja que con nombres de
  día largos se partía en dos líneas. Ahora es una sola línea sin fondo (icono + texto melocotón
  oscuro, cortada con "…"); con más de un día, "Unido con N días".
- **Aviso de fin de descanso**: no salía nunca. Causas encontradas:
  1. La app **nunca pedía `POST_NOTIFICATIONS`** (Android 13+ las trae apagadas): `notify` fallaba en
     silencio. Ahora se pide al abrir la sesión de entreno.
  2. `SCHEDULE_EXACT_ALARM` viene denegado por defecto en Android 14+ → alarma inexacta, minutos de
     retraso. Se añadió `USE_EXACT_ALARM` (concedido al instalar). **Ojo Play Store**: ver doc 04.
  3. Con la app delante, la pantalla cancelaba la alarma y pintaba un aviso propio. El usuario no
     quiere aviso dentro de la app: ahora siempre sale la notificación (en Android; en escritorio/web
     sigue el aviso de pantalla porque no hay otro).
  - Canal nuevo `rest_timer_v2` (vibración, visible en bloqueo; el viejo se borra porque los ajustes
    de un canal no se pueden cambiar), icono monocromo `ic_stat_rest`, tocarla vuelve a la app, y el
    aviso anterior se quita de la barra al empezar otro descanso.
- **Sin verificar en dispositivo.** En Xiaomi/HyperOS puede hacer falta activar a mano en Ajustes de
  la app "Pantalla de bloqueo" y "Notificaciones flotantes".

## ✅ 2026-11-27 — Limpieza: tema viejo fuera y AUD-16 cerrado (sin commitear)

- **Borrado `ui/theme/AppTheme.kt`** (`GymSmartColors`, `GymSmartTheme`, `GymSmartSpacing`,
  `GymSmartElevation`), con confirmación del usuario. Su último uso era `RateSliderColors.Legacy`;
  ahora el valor por defecto es `RateSliderColors.melocoton()`, y perfil y onboarding ya no copian los
  mismos siete colores cada uno.
- **AUD-16 cerrado.** La columna ya se había comprobado en Turso el 22/11 (existe). Faltaba aplicar la
  decisión de entonces: `has_wearable` fuera del `INSERT` y del `CREATE TABLE`, `hasWearable` fuera del
  `ProfileRequest` del cliente. **El servidor sigue aceptando el campo**: su `json()` es estricto y la
  app instalada lo manda, así que quitarlo daría 400 al guardar perfil y peso. La columna sigue en
  producción, sin uso; no se ha borrado nada de la BBDD.
- Verificado: `:shared:jvmTest :server:compileKotlin :composeApp:compileKotlinJvm :composeApp:jvmTest
  :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinWasmJs :composeApp:compileKotlinJs`, verde.
- Siguiente propuesto (Fase 5, punto 8 del orden de ataque, todos comprobados como abiertos en el código
  hoy): GPS-5, GPS-6, PERM-1, resto de ERR-1.


## 🟡 Segunda lista de mejoras (2026-11-25) — tareas 1-7

El usuario pidió 7 mejoras nuevas y, como la vez anterior, primero una investigación de todas (dentro
y fuera del código, con fuentes para las que lo necesitaban) y una clasificación por esfuerzo. Luego
pidió ejecutar **1, 4, 2 y 6 en ese orden, del tirón**. Las otras tres siguen sin empezar.

| # | Tarea | Modelo | Esfuerzo | Estado |
|---|---|---|---|---|
| 1 | Quitar los chips de 50/100/150/200 g del selector de ración | Sonnet 5 | Bajo | ✅ Hecho |
| 4 | Iconos en los alimentos, como Fitia | Sonnet 5 | Medio | ✅ Hecho |
| 2 | kcal activas estimadas bajo cada nivel de actividad | Opus 5 | Medio | ✅ Hecho |
| 6 | Conmutadores total/por lado y unilateral al registrar una serie | Opus 5 | Medio | ✅ Hecho |
| 7 | Recordar el peso de un ejercicio entre días distintos | Opus 5 | Alto | ✅ Hecho (falta probar en móvil y redesplegar servidor) |
| 5 | Pulsera: pulsaciones cada 5 s en vez de cada minuto | Opus 5 | Alto | ❌ Cerrada: no viable (ver abajo) |
| 3 | Rediseño general + tema claro/oscuro | Opus 5 | Alto | 🟡 En marcha: inicio hecho, perfil en maquetas |

**Verificado con** `:shared:jvmTest` (111 tests), `:composeApp:jvmTest` (23), `:server:compileKotlin`,
`:composeApp:compileKotlinJvm` y **`:composeApp:compileDebugKotlinAndroid`**, todo en verde.
**Nada commiteado.** Sin verificar en dispositivo: el aspecto real de los emoji de alimentos y de los
conmutadores nuevos.

### ✅ Tarea 1 — Fuera los atajos de gramos

`listOf(50, 100, 150, 200)` aparecía dos veces en `NutritionScreen.kt`: en el selector de ración y en
el editor de una entrada ya guardada. Se quitaron los dos, con su `Spacer` sobrante (−31 líneas). Se
comprobó que `BorderStroke` y `OutlinedButton` siguen usándose en el archivo, así que no quedan
imports huérfanos.

**Decisión tomada sin preguntar** (el usuario no contestó y pidió no parar): solo borrar, sin
sustituir. Queda anotado que Fitia no los quita sino que pone **raciones reales** ("1 taza (200 g)",
"1 huevo"), que sí tendría valor, pero eso es otra tarea: hay que meter raciones caseras en los 90
alimentos genéricos.

### ✅ Tarea 4 — Iconos en los alimentos

`shared/.../nutrition/FoodIcons.kt` (nuevo): una tabla de reglas palabra → emoji y `foodIconFor()`.
Se pintan en los resultados de búsqueda, en las comidas del día, en la cabecera del selector de
ración y en los alimentos de una colección.

**Por qué emoji y no iconos dibujados**: pesan cero, funcionan en las cuatro plataformas sin escribir
nada por plataforma, y es la convención que la app ya usaba en `WorkoutKind` y en los tipos de comida.
Un juego vectorial se vería algo mejor a cambio de dibujar y mantener ~25 assets.

**Por qué una sola tabla para genéricos y marcas**: los 90 alimentos de `GENERIC_FOODS` podrían llevar
su icono escrito al lado, pero entonces habría dos mecanismos y dos sitios donde el pollo puede dejar
de ser pollo. Hay uno solo, y un test exige que **los 90 genéricos resuelvan a un icono concreto**: la
curación existe, pero vive en el test en vez de duplicada.

**Dos cosas que se aprendieron por el camino:**
- La búsqueda es **por palabra completa**, reutilizando `normalizeFoodName`/`phraseMatches` de
  `CookingYield` (que pasó de `private` a `internal`). Por subcadena, "repollo" contendría "pollo" y
  una verdura saldría con un muslo al lado — la misma trampa que ya mordió en el catálogo de cocción.
- **Las marcas de suplementos rompen esa regla**: "Evowhey 2.0" es una sola palabra, así que por
  palabra completa no se reconoce. Se añadió un segundo modo de búsqueda **por fragmento** (`stems`),
  usado *solo* en la regla de suplementos y *solo* con raíces inglesas que no pueden aparecer dentro
  de una palabra española (`whey`, `protein`, `proteic`, `casein`, `isolat`, `creatin`). Buscar "cola"
  por fragmento convertiría la coliflor en un refresco: por eso es una excepción y está acotada.
  (`proteic` hace falta aparte porque "proteico" no contiene "protein" — le falta la n.)

**Nota de compatibilidad**: tres iconos son de Unicode 13/14 (🫒 aceite, 🫘 legumbres, 🫑 pimiento) y
piden Android 12L o superior. Se usaron solo donde no había alternativa clásica razonable. Si alguno
sale como un cuadrado en el móvil, se cambia en un sitio y afecta a toda la app.

10 tests nuevos en `FoodIconsTest.kt`.

### ✅ Tarea 2 — Calorías activas bajo cada nivel de actividad

Amplía lo que hizo la tarea F con los pasos. Ahora cada nivel enseña también las **calorías activas**
que le corresponderían al usuario, calculadas con su BMR real del formulario (se actualizan en cuanto
corrige peso o edad). Si el perfil está a medias, se queda en los pasos: media ayuda es mejor que una
cifra inventada.

**No hay tabla fija de kcal por nivel y no puede haberla**: dependen del peso, la altura, la edad y el
sexo. "Moderado = 500 kcal" sería inventado.

**El hallazgo que justifica que esto lo hiciera Opus y no un modelo pequeño.** La resta evidente
(`gasto − BMR`) **está mal para este uso**. El PAL se define como `gasto total ÷ BMR` (National
Academies, *DRI*, cap. 4) y el gasto total incluye el **efecto térmico de los alimentos**, ~10 % del
total. O sea, `BMR × (PAL − 1)` son movimiento **más digestión**. La pulsera, en cambio, reporta
`ActiveCaloriesBurnedRecord`, que es solo actividad. Enseñar las dos cifras juntas sin descontar el TEF
le diría al usuario que su pulsera marca siempre un ~10 % menos que el nivel que eligió, y la reacción
natural sería bajar de nivel y comer de menos. **Es exactamente la familia de fallo que ya llegó al
usuario** (objetivo de ganar peso → déficit encubierto de 400-500 kcal/día). Así que se descuenta:
`activas = BMR × (PAL × 0,90 − 1)`, y lo que se muestra está ya en la escala de la pulsera.

**Comprobación cruzada**: andar cuesta ~0,75 kcal/kg/km brutos → ~0,04 kcal/paso para 75-80 kg → los
~4.000 pasos de un sedentario son ~170 kcal, frente a las ~145 que da la fórmula. Dos caminos que no
comparten ni una constante, mismo orden de magnitud. Hay un test que lo fija.

Se redondea a la decena y con `~` delante: "711 kcal" afirmaría tres cifras significativas sobre una
estimación cuyo eslabón más débil tiene 250-350 kcal de margen.

**Un bug que apareció al hacerlo**: en el onboarding, `SelectableRow` pintaba `parts[0]` y `parts[1]` y
**descartaba el resto en silencio**. El baremo de pasos de la tarea F se añadía como tercera línea, así
que **nunca llegó a verse en el onboarding** — solo en el perfil. Arreglado: ahora pinta todas las
líneas que reciba.

**Cambio de interfaz en el perfil**: el baremo iba dentro del texto de cada opción del desplegable, y
el campo cerrado es de una sola línea, así que al elegir un nivel el nombre salía cortado. `DropdownField`
acepta ahora una línea de ayuda opcional bajo cada opción: la ayuda se ve donde sirve (mientras se
compara) y el campo cerrado dice solo "Moderado".

Documentado en `10-fundamento-cientifico.md` **§16** (sección nueva). De paso se descubrió que la
referencia del código a "§14" era **falsa**: §14 son los invariantes de tests y la ciencia de los pasos
no estaba escrita en ninguna parte. Ahora sí, y la referencia apunta a §16.1. 9 tests nuevos.

### ✅ Tarea 6 — Total / por lado, y unilateral

Dos conmutadores nuevos en la tarjeta de peso, justo bajo la píldora del tipo de ejercicio. Se guardan
en el **ejercicio**, no en la serie: se eligen una vez y no en cada repetición del flujo.

**Lo de "total / por lado" ya existía a medias y mal colocado**: había un `asTotal` dentro del teclado
numérico, con `remember` local, que se olvidaba al cerrar la hoja y había que volver a pulsar en cada
serie. Ahora es `WorkoutExercise.entryTotal`, persistente, y el botón del teclado y el conmutador de la
tarjeta son el mismo ajuste.

**La invariante que sostiene el cambio**: `entryTotal` es una preferencia **de entrada**. Lo que se
guarda en `WorkoutSet.value` sigue siendo la unidad canónica del tipo, siempre. Si se guardase el
total, dos series idénticas apuntadas en modos distintos divergirían y el histórico pasaría a
significar cosas distintas según cuándo se registró cada serie. Hay un test que lo fija.

Solo se ofrece donde hay algo que convertir (barra y mancuernas, `canSwitchTotal`). En máquina, polea
y peso corporal el número de la placa **es** el total. Para una máquina cargada con discos a los lados,
la forma correcta sigue siendo "Barra libre" con el peso del carro en `barKg`.

Detalle que se cuidó: el ± sigue moviendo un disco por lado, pero en modo total el número que se ve se
mueve el doble, así que el rótulo del botón lo dice (`displayStep()`, derivado de `totalKg` para que no
pueda desincronizarse).

**Unilateral — la convención, que era lo que había que decidir**: las repeticiones que se apuntan son
**las de un lado** ("10 por brazo", que es como lo cuenta todo el mundo) y el **volumen cuenta los
dos**, porque el trabajo se hizo dos veces. Comprobación de que es la correcta y no una inflación: un
remo a dos manos con 40 kg × 10 y uno a una mano con 20 kg × 10 por lado dan **el mismo volumen**, que
es lo que debe pasar — es el mismo trabajo repartido de otra forma. Hay un test exactamente para eso.

`SessionExercise` también guarda `unilateral`, por la misma razón que ya guardaba el nombre y el tipo:
si el volumen de una sesión pasada se calculase mirando el plan de hoy, desmarcar la casilla cambiaría
retroactivamente un entrenamiento de hace un mes.

Los dos campos son `false` por defecto, así que **todo lo guardado antes se lee exactamente igual**.
Espejo de los dos en el modelo del servidor; al ser booleanos no hacen falta validaciones nuevas.
9 tests nuevos en `WorkoutModelsTest.kt`.

### 🟡 Tarea 3 — Rediseño: estilo elegido y primera pantalla aplicada

**Lienzo de diseño** (privado del usuario): https://claude.ai/artifact/9vnNXBDdPnrQqqp4qDrhJr — cuatro
páginas: seis direcciones iniciales, seis variaciones de "Estudio", seis estructuras de "Melocotón" y
seis versiones del perfil. Ahí está todo lo que se le enseñó y lo que eligió.

**Cómo se eligió**: seis direcciones (3 oscuras, 3 claras) → le gustó la 2 "Estudio" (claro, cálido,
editorial) → seis variaciones de esa línea → eligió **2e "Melocotón y chocolate"** "sin ninguna duda"
→ seis estructuras con esos colores → se quedó con la **M1, la 2e original**.

**La paleta** (exacta de la maqueta, contrastes comprobados contra WCAG AA): fondo `#F7EEE6`,
tarjeta `#FFFFFF`, borde `#EADCCF`, texto `#2B1D17` / `#6E5D53`, bloque protagonista chocolate
`#3E2A20` (con `#5A3F31` dentro y textos `#FBF4EE` / `#D8C3B4` / `#F8D3B6`), acento melocotón de
relleno `#E8955E` y **acento para texto `#A85A2A`** (el melocotón no llega a 4,5:1 como texto pequeño
sobre blanco, por eso hay dos). Tipografía **DM Sans**.

**Lo que ya está en el código** (verificado: `:shared:jvmTest` 116 tests, `:composeApp:jvmTest`,
`:server:compileKotlin`, `:composeApp:compileKotlinJvm` y `:composeApp:compileDebugKotlinAndroid`,
todo en verde):

- `ui/theme/Palette.kt` (nuevo): `GymSmartPalette` con **tokens por función** (`hero`,
  `accentText`, `textSecondary`…), la instancia `MelocotonClaro`, `LocalPalette` + `Palette.current`
  y `MelocotonTheme { }`, que fija paleta, un esquema de color claro coherente (para que menús y
  diálogos no salgan oscuros) y la tipografía DM Sans **sin colores metidos dentro**. Esto es la base
  del interruptor claro/oscuro: con tokens por función, una paleta oscura solo tiene que asignar
  otro color a cada papel. **La migración es pantalla a pantalla**: cada una que se rediseña se
  envuelve en `MelocotonTheme` y deja de leer `GymSmartColors`.
- **DM Sans incluida en la app**: 5 pesos en `composeResources/font/` (subconjunto latino de
  Fontsource, licencia OFL; cubre ñ, tildes, ¿¡, €). Se comprobó la firma TrueType de cada archivo
  antes de meterlo. Ojo: no incluye `≈`; si se usa DM Sans donde aparece ese símbolo, saldrá de la
  fuente de respaldo.
- `ui/icons/LineIcons.kt` (nuevo): iconos de línea como `ImageVector`, con **los mismos trazos SVG
  que la maqueta**, teñibles con la paleta. Sustituyen a los emoji de las secciones del inicio. Los
  emoji de los alimentos (tarea 4) se quedan: ahí sí ayudan.
- `ui/theme/SystemBars.kt` + `actual` en las 5 plataformas: `SystemBarsAppearance(darkIcons)`. Hace
  falta porque la app va de borde a borde y, con el móvil en modo oscuro, la hora y la batería
  saldrían blancas sobre el crema. En Android pone iconos oscuros mientras la pantalla está delante
  y **restaura los anteriores al salir** (las pantallas viejas siguen siendo oscuras).
- `ActivityBenchmarks.levelForSteps` / `nextLevelThreshold` / `labelFor` (+5 tests): el nivel que
  corresponde a los pasos **de hoy** y el siguiente corte.
- **`DashboardScreen.kt` reescrita** con la estructura de M1: cabecera, saludo según la hora, bloque
  de chocolate (fecha, nivel de hoy, pasos, kcal activas, barra), secciones en lista y Planes. Se
  conservan el refresco automático de la pulsera, el botón de refresco manual y el menú de cuenta
  (perfil / cerrar sesión). `AnimatedCard` y `AnimatedMiniCard` desaparecen (no los usaba nadie más).

**Una diferencia deliberada con la maqueta**: la maqueta ponía "78 % de 10.000 pasos", pero la app
**no tiene objetivo de pasos** y 10.000 sería inventado (se le avisó tres veces al enseñar las
maquetas). La barra avanza hacia el **siguiente nivel del baremo** que ya usa el perfil, y el texto
dice "78 % · faltan 2.158 pasos para Activo". Con 7.842 pasos el siguiente corte es justo 10.000, así
que en el caso de la maqueta se ve igual.

**Sin verificar en dispositivo**: el aspecto real (tipografía, iconos, barra de estado con el móvil
en modo oscuro). Las demás pantallas siguen con el tema oscuro antiguo, así que **al entrar desde el
inicio a cualquier sección el salto de claro a oscuro es esperable** hasta que se migren.

**Perfil aplicado (2026-11-26)** — diseño elegido por el usuario: **mezcla de P1 y P4** (página
"Perfil — mezcla P1 + P4" del lienzo). Cabecera de chocolate a sangre detrás de la barra de estado
con las cifras **guardadas** (peso, objetivo, kcal/día — no las que se están tecleando), y tres
pestañas: **Cuerpo** (ajuste adaptativo + cuerpo y objetivo), **Cuenta** (datos personales +
suscripción) y **Pulsera**. La lógica es la de antes línea a línea: los dos guardados, el reguardado
al cambiar la fecha de nacimiento, validaciones, autoajuste y diálogo de resumen (restilado).
Detalles: la pestaña se guarda al girar el móvil; `SystemBarsAppearance` ahora separa iconos de
arriba (claros, sobre chocolate) y de abajo (oscuros, sobre crema); `RateSlider` acepta colores
(`RateSliderColors`, por defecto los antiguos para el onboarding) y **conserva su zona verde
recomendada y su línea de kcal**, que la maqueta simplificaba; formato de miles compartido en
`ui/Format.kt`. Verificado con la batería completa + Android. Sin verificar en dispositivo.

**GPS, Mis rutas, Rutas de la comunidad y Detalle de ruta (2026-11-26)** — solo estilo, sin
rediseñar (petición expresa del usuario): misma estructura y lógica; cada pantalla se envuelve en
`MelocotonTheme` con iconos oscuros arriba, y sus 116 colores viejos pasan a tokens de la paleta.
Regla aplicada, porque el azul viejo hacía de dos cosas: `Primary` como **fondo de botón** → chocolate
(`hero`, texto `onHero`); `Primary` como **texto, icono, foco o indicador** → `accentText`. Diálogos
en tarjeta blanca; snackbar en chocolate; botón de "ruta completada" en melocotón. Los colores de la
línea de la ruta sobre el mapa no se tocan (los dibuja el mapa de cada plataforma). Script de
migración reutilizable: el mismo patrón sirve para las pantallas que quedan.

**Resto de pantallas (2026-11-26)** — solo estilo, mismo script: Nutrición, Peso, Onboarding, Login,
Registro, Olvidé/Restablecer contraseña, Suscripción y Progreso. La paleta gana `macroProtein`,
`macroCarbs` y `macroFat` (más oscuros que los viejos, para leerse sobre blanco). El deslizador del
onboarding usa los mismos colores que el del perfil. La pantalla de arranque (`NavGraph.StartupScreen`)
y la raíz de la app (`App.kt`) pasan a `MelocotonTheme`. **Con esto no queda ninguna pantalla en uso con
el tema oscuro**; solo `TrainingScreen` (inalcanzable, nadie navega a ella; el usuario decidió dejarla
como está) y los colores por defecto de `RateSlider`. `GymSmartColors` / `GymSmartTheme` siguen en
`AppTheme.kt` sin uso real: candidatos a borrar cuando se decida. Verificado: batería completa +
Android + JS + Wasm. Sin verificar en dispositivo.

**Maquetas de Nutrición y Pesaje (2026-11-27)**: seis de cada en el lienzo (páginas "Nutrición — seis
versiones" y "Pesaje — seis versiones"), con los datos reales de cada pantalla. **Elegidas N3 (sin horas: al
usuario le dan igual) y W5, y aplicadas (2026-11-27)**:
- **Nutrición (N3)**: cabecera propia (atrás redondo, fecha del día mirado, refresco redondo si hay
  pulsera), días en baldosas (L M X J V S D; el mirado en chocolate, hoy con borde melocotón, futuros
  apagados), tarjeta "Presupuesto de hoy" (comido / objetivo, lo que queda o "de más", una barra
  partida por macros, leyenda y línea de la pulsera) y las cuatro comidas como línea de tiempo
  (círculo relleno si hay algo, punteado si no; comida vacía con botón punteado "+ Añadir …" y
  escáner al lado). Los alimentos muestran gramos y kcal (ya no P/C/G por alimento). Búsqueda,
  escáner, raciones, colecciones y avisos, sin tocar.
- **Pesaje (W5)**: cabecera propia y dos pestañas (se guardan al girar): **Peso** (último pesaje con
  la diferencia con el anterior, "+ Añadir peso" que abre el mismo diálogo de siempre, y la gráfica de
  siempre con "N días") y **Análisis IA** (la tarjeta de antes, misma lógica de Premium y foto).
- Verificado: batería completa + Android + JS + Wasm. **Sin commitear. Sin verificar en dispositivo.**

**Lo siguiente**:
1. ~~Perfil~~ hecho (arriba). Siguiente pantalla a migrar: la elige el usuario. Pendiente de hablar
   antes: la tarea 7 (el usuario quiere la versión simple: él decide si dos ejercicios son el mismo,
   sin el caso de tipos distintos).
2. **Diseñar la variante oscura de Melocotón** antes del interruptor: no se ha inventado a propósito.
3. Migrar el resto de pantallas (el usuario decide cuáles se rediseñan; el estilo se aplica a todas).

### ✅ Tarea 7 — Unir el mismo ejercicio entre días: hecha (falta probar en móvil)

**Cómo se diseñó**: simulador HTML interactivo iterado con el usuario hasta que dijo "así me gusta
3000 veces mejor" y pidió pasarlo **tal cual** al código. Simulador:
https://claude.ai/artifact/7VTBXk5NPcDDfGe5WMy4N8 (privado). **Es la especificación**: si algo de la
app no cuadra con él, manda el simulador.

**Versión simple (decisión del usuario, no re-preguntar)**: el usuario decide si dos ejercicios son el
mismo. Al escribir un nombre que ya existe en otro día (comparando sin mayúsculas, sin tildes y sin
espacios de más, `normalizeExerciseName`; nada de sinónimos) se pregunta "¿Es el mismo?" → "Sí,
unirlos" / "No, es otro". También se puede unir desde la lista "Ya lo haces en otro día" al añadir.

**Modelo** (`composeApp/.../model/WorkoutModels.kt`, reescrito; espejo en el servidor):
- `WorkoutExercise.linkId` (vacío = sin unir; `groupId = linkId.ifBlank { id }`), `lastFrom` (en qué
  día fue la última vez). Unidos comparten **tipo, barra, salto y última vez**; el nombre es de cada día.
- `WorkoutSet.perSide` / `unilateral` (nullable): **la configuración va en cada serie**, no en el
  ejercicio. `null` = serie vieja → se lee como siempre (barra y mancuernas a cada lado, el resto en
  total: `legacyPerSide`). El ejercicio guarda solo los valores por defecto.
- Al terminar, la sesión se copia como `lastSession` **a todos los ejercicios del grupo**, con `lastFrom`.
- Las comparaciones con la última vez son siempre en **peso total** (`totalKgOf` / `valueForTotal`).
- `entryTotal` y `SessionExercise.unilateral` se conservan como campos obsoletos: el cliente usa JSON
  estricto y el histórico viejo los trae.

**Pantallas**: `WorkoutScreen.kt` (días, día, hojas de crear día / añadir / editar ejercicio / editar
día, pantalla final) y `WorkoutLogScreen.kt` (apuntar series) reescritas en estilo Melocotón, tal cual
el simulador. La tarea 6 queda sustituida: los conmutadores ahora son "Unilateral" y "A cada lado",
en una sola línea junto al tipo, y se guardan en la serie. Se conserva lo que ya había: sesión
guardada en cada cambio, aviso de descanso del sistema (tarea C), teclado numérico propio, histórico.

**Verificado**: `:shared:jvmTest`, `:server:compileKotlin`, `:composeApp:compileKotlinJvm`,
`:composeApp:jvmTest` (`WorkoutModelsTest` reescrito, 13 tests) y `:composeApp:compileDebugKotlinAndroid`,
todo en verde. **Nada commiteado. Sin verificar en dispositivo.**

**⚠️ Hay que redesplegar el servidor**: el servidor reescribe el plan con su modelo; uno viejo tiraría
`linkId`, `lastFrom`, `perSide` y `unilateral` de cada serie al guardar (no rompe nada, pero se pierden
las uniones).

**Arreglo tras probar en el móvil (2026-11-26)**: en un móvil bajo / con letra grande la hoja
"Añadir ejercicio" no cabía y el botón de añadir quedaba fuera (la hoja no tiene scroll a propósito:
solo se desplaza la lista). La lista "Ya lo haces en otro día" pasa a `weight(1f, fill = false)` con
alto 72–262 dp: se queda con el hueco que sobra. Compilado (incl. Android), **sin commitear**,
sin verificar en dispositivo.

**Decidido por el usuario (2026-11-26, no re-preguntar)** sobre lo que el simulador no tenía
(HTML: https://claude.ai/artifact/7niNEMs6voX2xgagjSRqdM): **se quedan** el botón atrás de la lista de
días, "Ver mi progreso", el aviso "Entrenando · N series", "Editar" día (renombrar / eliminar), las
sugerencias de nombre al crear día y el aviso sin conexión / error al cargar. **No se hace** unir dos
ejercicios que ya existen (se asume: para unirlos hay que borrar uno y volver a añadirlo). **Se quedan
fuera** las sugerencias de ejercicios al añadir. Con esto la tarea 7 queda cerrada en código; solo
falta redesplegar el servidor y que el usuario la pruebe en el móvil.

### ❌ Tarea 5 — Cerrada: no viable

Lo que se comprobó, con el usuario delante y su pulsera (Xiaomi Smart Band 10):
- **"Compartir FC" (emisión BLE estándar a ~1 Hz) existe**, pero es un **modo de pantalla**: solo
  emite mientras esa pantalla está abierta en la pulsera; al salir se desactiva. Es lo mismo que el
  modo entrenamiento, así que no quita el gesto manual que el usuario quería quitar.
- **La monitorización de Mi Fitness**: opciones "inteligente", 1, 10 o 30 min. Según la ficha oficial
  de Xiaomi de la Band 10, "inteligente" mide **cada 1 min en actividad y cada 10-30 en reposo**: no
  mide más a menudo al moverse, mide menos al estar quieto. **1 minuto es el suelo** en todo lo que no
  sea modo entrenamiento. Recomendación dada: dejarlo en "1 min" fijo.
- Firmware propio: no existe para la Band 10 (Gadgetbridge no toca firmware; la Band 10 está "mostly
  supported", con token y protocolo experimental).
- Queda una vía **sin probar** que se le planteó y descartó él: mantener viva la emisión con un
  cliente BLE conectado (prueba con nRF Connect) o pedir mediciones bajo demanda por el protocolo de
  Xiaomi. Si algún día se retoma, se empieza por ahí, no desde cero.

Alternativa que salió y quedó sin decidir: **calcular las kcal de fuerza desde lo que la app ya sabe**
(ejercicio, peso, series, reps, descanso) en vez de desde el pulso, que para fuerza está fuera del
rango de validación de Keytel (2005).

### ⬜ Tareas 3, 5 y 7 — la investigación original (se conserva como referencia)

**7 — recordar el peso entre días distintos.** Diagnóstico confirmado: `lastSession` vive **dentro de
`WorkoutExercise`, que vive dentro de `WorkoutDay`**. El mismo ejercicio en dos días del plan son dos
objetos con ids distintos y memorias independientes, que es justo lo que el usuario describió. La
solución está medio construida: la tarea E creó `WorkoutSession`/`SessionExercise`, un histórico real
por fecha, así que la referencia debe pasar a ser "la última vez que hice **este ejercicio**", mire el
día que mire. Lo que lo hace caro: emparejar por nombre es frágil ("Press banca" vs "press de banca"),
haría falta un identificador de ejercicio compartido entre días; y hay que decidir qué pasa si el mismo
ejercicio se hizo con **tipos distintos** en dos días (máquina el lunes, mancuernas el sábado), porque
ahí los números no son comparables y proponer el peso de uno en el otro sería peor que no proponer nada.

**5 — pulsera cada 5 s.** Tres conclusiones de la investigación:
1. **No hace falta hackear nada.** La Xiaomi Smart Band 10 soporta **emisión de FC por Bluetooth
   estándar** (perfil BLE Heart Rate, el de las bandas pectorales). Xiaomi lo documenta y apps como
   Pulsoid ya lo usan: se activa "Compartir FC" y cualquier app lee el pulso a ~1 Hz.
2. **No arregla lo que se pretende.** La medición del 2026-11-24 (pulsera 56 kcal / Mi Fitness 54 /
   Health Connect 37 / GymSmart 37) ya localizó el cuello de botella en **Mi Fitness → Health
   Connect**. Leer el pulso por BLE abre una vía paralela, no toca ese tramo.
3. **Pulso → kcal en el gimnasio es dudoso.** Keytel et al. (2005), r = 0,913, pero validado sobre
   carrera y bici en estado estable al 57-90 % de FC máx. La fuerza no es nada de eso. Las fuentes
   secundarias que dicen "sobreestima en fuerza" se contradicen entre ellas y ninguna es primaria: lo
   honesto es decir que está **fuera de su rango de validación**, sin saber en qué dirección falla.

   Limitaciones prácticas: la emisión se activa **a mano** (o sea, no es el "automático" que se pedía),
   mientras emite la pulsera suele quedarse con un solo dispositivo, consume batería, y el código BLE
   habría que escribirlo por plataforma. Modificar el firmware está descartado: Gadgetbridge declara
   que no toca el software de la pulsera, la Band 10 está solo "mostly supported", necesita token y el
   protocolo está marcado como experimental.

   **Veredicto: es un spike con prueba en el móvil, no una tarea de desarrollo.** El pulso en vivo sí
   tiene valor por sí mismo (verlo durante la serie, zonas de intensidad); como arreglo de las kcal, no.

**3 — rediseño y tema claro/oscuro.** Medido el acoplamiento, que es lo que manda sobre el coste:
**955 referencias directas a `GymSmartColors.*` fuera del archivo de tema**, en 20 archivos
(`NutritionScreen` 142, `ProfileScreen` 124, `WorkoutLogScreen` 91, `WorkoutScreen` 71...).
`GymSmartColors` es un `object` de `val` constantes, así que **hoy un interruptor claro/oscuro es
imposible sin tocar esas 955 líneas**. La tipografía además fija colores dentro de cada `TextStyle`
(segundo foco de fuga) y no hay `lightColorScheme` ni `isSystemInDarkTheme()` en ningún sitio. Lo bueno:
apenas hay `Color(0x...)` sueltos (13 en total, en `TrainingScreen`, `WorkoutLogScreen`, `TrainingTable`
y `TrainingCard`), así que el problema está concentrado. Plan propuesto en tres pasos: maquetas de la
pantalla principal para elegir paleta → convertir `GymSmartColors` en un proveedor sensible al tema
(`CompositionLocal`) y sacar los colores de la tipografía → rediseñar las pantallas que el usuario elija.
**Va después de 6 y 7**, para no rediseñar `WorkoutLogScreen` dos veces.

---

## 🟡 Nueva lista de mejoras (2026-11-24) — tareas A-G

El usuario pidió 7 mejoras sueltas (luego ampliadas a 8 con la G) y, antes de tocar nada, se le dio una
clasificación por complejidad/nivel de modelo y un orden de ejecución acordado. **Regla explícita del
usuario: no se investiga ni se implementa nada que no se esté haciendo en ese momento.**

| ID | Tarea | Nivel | Estado |
|---|---|---|---|
| A | La búsqueda de comida se borraba al volver atrás del selector de ración | Sonnet, medio | ✅ Hecho |
| D | La hoja de "añadir peso" de un ejercicio no se desplegaba del todo | Sonnet, bajo | ✅ Hecho |
| B | Colecciones de comida para añadir varios alimentos de golpe | Sonnet, alto | ✅ Hecho |
| G | Peso crudo vs cocido para alimentos genéricos (como Fitia) | Opus, alto | ✅ Hecho |
| F | Baremos reales bajo cada nivel de actividad (pasos/kcal) | Opus, medio | ✅ Hecho |
| E | Pestaña "Progreso" con calendario de entrenos | Opus, alto | ✅ Hecho |
| C | Notificación de fin de descanso (sistema + popup in-app) | Opus, alto | ✅ Hecho |
| H | Tabla local de alimentos genéricos, mostrados antes que las marcas | Opus, alto | ✅ Hecho |

**Orden acordado**: A+D juntas → B+G juntas (mismo modelo de datos de comidas) → F → E → C. B y G se
agruparon porque las dos tocan el flujo de añadir alimentos. **Lo siguiente es la tarea F.**

**Tarea H (propuesta, el usuario NO la ha aprobado).** Surgió al comparar la app con Fitia: al buscar
"pechuga de pollo" el genérico no aparece arriba. Se comprobó que **Open Food Facts no tiene alimentos
genéricos** — es una base de productos envasados, y los 12 primeros resultados reales de una búsqueda
son todos marcas (Hacendado, Mercadona, Carrefour…). Fitia resuelve esto teniendo *dos* fuentes: una
base propia de genéricos y, encima, los productos de marca (que son los mismos que los nuestros: su
"Arroz Largo Hacendado 347 kcal" coincide exacto con lo que devuelve OFF). La propuesta es replicar esa
estructura con datos que **ya están descargados y validados** de la Tarea G: el volcado de USDA SR
Legacy tiene 7.793 alimentos genéricos, 4.221 en categorías relevantes; con 200-300 curados se cubre una
dieta española. **No añadiría una segunda API**: igual que los factores de la Tarea G, se procesaría
aquí y viajaría como datos estáticos, así que en ejecución seguiría habiendo una sola API de alimentos
(OFF). El coste real es traducir y curar los nombres al español, que es manual. No sirve para productos
españoles (jamón serrano, chorizo, manchego): esos seguirían saliendo solo de marcas. Un paso intermedio
mucho más barato: **mostrar la marca en cada resultado**, que hoy ni siquiera se pinta.

### ✅ Tarea A — La búsqueda ya no se borra al volver atrás

`FoodSearchSheet` guardaba `query`/`results`/`tab`/`hasSearched` en un `remember` interno. El `when`
de `NutritionScreen` que decide qué hoja mostrar hace que esa rama deje de componerse mientras se ve el
selector de ración (`PortionPickerSheet`), así que al volver Compose recreaba `FoodSearchSheet` desde
cero y el `remember` se perdía. Arreglo: ese estado se sacó a una clase `FoodSearchState` hoisteada en
`NutritionScreen`, que sobrevive a los cambios de `sheetMode`. Se resetea explícitamente solo al pulsar
"Añadir" desde una comida nueva — nunca al volver atrás.

### ✅ Tarea D — La hoja de peso de un ejercicio ya se despliega entera

Faltaba `sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)` en el
`ModalBottomSheet` del teclado numérico (`NumberPadSheet` en `WorkoutLogScreen.kt`), así que Material3
podía dejarla "parcialmente expandida" con el botón "Hecho" cortado y sin scroll para llegar a él. El
usuario pidió revisar si había más hojas con el mismo problema: las otras dos de `WorkoutScreen.kt`
(`AddExerciseSheet`, `KindSheet`) tenían el mismo hueco (mitigado a medias por tener scroll, pero sin
garantía de desplegarse del todo). Las tres arregladas con el mismo patrón que ya usa bien la hoja de
comidas en `NutritionScreen`.

### ✅ Tarea B — Colecciones de comida ("añadir desayuno de una vez")

Nueva 4ª pestaña "Colecciones" en el buscador de alimentos (junto a Buscar/Historial/Favoritos). Una
colección es un grupo de alimentos con su cantidad ya fijada (reutiliza `MealEntry` tal cual para cada
ítem); tocar la tarjeta de una colección añade todos sus alimentos de golpe a la comida que se estaba
editando, sin pasar por el buscador uno por uno.

**Servidor**: `FoodCollection`/`FoodCollections` en `NutritionModels.kt`, tabla `food_collections`
(documento JSON único por usuario, mismo patrón que `workout_plans` — no hace falta consultar dentro
de él desde el servidor), `GET`/`PUT /nutrition/collections` con validación de cordura (máx. 40
colecciones, 30 alimentos por colección, nombre ≤40 caracteres, gramos en (0, 5000]).

**Cliente**: mismo modelo espejo + `getCollections()`/`saveCollections()` en `NutritionService`. En
`NutritionScreen.kt`, tres `SheetMode` nuevos (`CollectionEditor`/`CollectionSearch`/
`CollectionPortionPicker`), **completamente separados** de los `SheetMode` del flujo real de comidas
(`Search`/`Scanner`/`PortionPicker`/`EditEntry`, sin tocar ni una línea de esas ramas) — decisión
deliberada para no arriesgar el flujo más crítico de la app por una función secundaria. Reutilizan los
mismos composables `FoodSearchSheet`/`PortionPickerSheet` del flujo normal (son genéricos: solo
necesitan una etiqueta y un par de callbacks), así que no hubo que escribir una búsqueda ni un selector
de ración nuevos. El editor de una colección (`CollectionEditorSheet`, nuevo) tiene nombre, lista de
alimentos con botón de quitar, "Añadir alimento" (abre la búsqueda en modo colección) y "Guardar".

Verificado con `:shared:jvmTest`, `:server:compileKotlin`, `:composeApp:compileKotlinJvm`, los tres en
verde. **Nada de esto commiteado todavía.**

### ✅ Tarea G — Pesar en crudo o en cocido

El usuario avisó de que esto es "muy muy crucial, porque la app se basa en medir comidas". El error que
había era grande y en los dos sentidos: 150 g de pechuga pesados ya hechos se registraban como 180 kcal
en vez de 237 (−24 %), y 200 g de arroz cocido como 730 kcal en vez de 260 (+181 %). La carne pierde
agua y se concentra; el arroz la absorbe y se diluye.

**Se hizo en dos pasadas, y la primera estaba mal planteada.** La versión inicial tenía 11 alimentos
sacados a mano de uno en uno, sin ni un pescado. El usuario lo cortó con la pregunta correcta: *"si para
la mitad de alimentos no puedo poner cocido, de qué sirve?"*. Tenía razón: el problema no era la calidad
de cada factor, era que el método de obtenerlos no escalaba. Se rehízo:

1. **Extracción sistemática en vez de a mano.** Se descargó el volcado completo de **USDA FoodData
   Central SR Legacy** (6 MB, 7.793 alimentos). Ese dataset nombra los alimentos de forma regular
   ("…, raw" / "…, cooked, roasted"), así que emparejando por el tronco del nombre salen **1.261 parejas
   crudo/cocinado** de golpe. De ahí se seleccionaron las relevantes para una dieta española: el
   catálogo pasó de 11 a **24 alimentos**, con pescado, marisco, quinoa, cuscús, bulgur, cebada, cuatro
   tipos de alubia, habas, pavo y patata. El factor de cada uno es `kcal crudo ÷ kcal cocinado`.
2. **Y lo que de verdad cierra el hueco: si no reconoce el alimento, pregunta.** Aunque el catálogo
   tuviera 200 entradas, emparejar nombres libres de Open Food Facts en español siempre fallaría en
   algunos. Así que cuando no hay coincidencia automática (y nada indica que el producto venga ya
   cocinado), aparece "¿Lo has pesado ya cocinado?" y el usuario elige a qué alimento se parece, de una
   lista agrupada por familias. **El factor sigue saliendo del catálogo, con su fuente**; lo que se le
   pregunta es *qué alimento es*, que era el dato que faltaba y que él sí sabe. Nunca se le deja teclear
   un factor: eso sería que la app avalase cifras inventadas.

**Mejoras de dato que trajo la segunda pasada**: la pechuga de pollo pasó de 0,72 a **0,76**, porque el
0,72 de la tabla de 1975 era de pechuga *con piel* y SR Legacy tiene la entrada de pechuga sin piel ni
hueso, que es lo que se compra. Y el pescado salió mejor de lo esperado: **45 de las 72 parejas de
pescado en calor seco dan exactamente 0,78**, en especies sin relación entre sí, así que un único factor
para todo el pescado no es una simplificación cómoda sino lo que dice el dato.

**Nada inventado.** Todo citado con su identificador de FoodData Central en
`doc/10-fundamento-cientifico.md` §13.4, fila por fila. Dos factores (ternera picada y cordero) no
tienen pareja en el dataset y vienen de la *"USDA Table of Cooking Yields for Meat and Poultry"* (ARS,
2012), marcados como tales.

**Respuesta a la duda del usuario sobre la API: no hacía falta cambiarla.** Open Food Facts no da ningún
dato de conversión crudo/cocido, pero tampoco lo necesitaba — el factor es del *alimento*, no del
producto, así que vive en un catálogo propio y se aplica sobre los valores que OFF ya devuelve.

**Dónde está:**
- `shared/.../nutrition/CookingYield.kt` (nuevo): catálogo de 24 alimentos con su fuente y su familia,
  más `findCookingYield`, `cookingYieldById`, `isExcludedFromCooking`, `per100For` y `rawWeightFor`. En
  `shared` para que sea testable y para que la conversión no se duplique (la usan cliente y servidor).
- `shared/src/commonTest/.../CookingYieldTest.kt` (nuevo): 22 tests. Comprueban que el factor no está
  invertido (cocinar carne sube las kcal/100 g, cocer arroz las baja), que la ración total sale igual se
  pese antes o después de cocinar, que **cada factor reproduce el valor cocinado medido por la USDA**
  (16 alimentos comprobados uno a uno), que "repollo" no se confunde con "pollo" y que el atún en lata
  no se convierte.
- **BBDD**: dos columnas nuevas en `meals`, `basis` ("raw"/"cooked") y `yield_id` (qué alimento del
  catálogo se usó), con el mismo `ALTER TABLE` dentro de `runCatching` que ya usa `ProfileService`. Las
  comidas antiguas quedan sin base y se muestran como siempre. Validadas en la ruta contra el catálogo
  real, porque las manda el cliente. `yield_id` hace falta además de `basis` porque si el alimento de
  referencia se eligió a mano, su nombre no basta para volver a encontrarlo.
- **Lo guardado** son los gramos que el usuario pesó de verdad y los valores por 100 g **ya convertidos**,
  así que el total se calcula igual que siempre y **ni el autoajuste ni los agregados diarios cambian**.
- **UI**: selector "¿Cómo lo has pesado?" en el selector de ración, con la equivalencia ("≈ 197 g crudo")
  y el factor con su fuente debajo; botón "¿Lo has pesado ya cocinado?" + diálogo de elección por
  familias cuando no se reconoce; y la base visible en la lista de comidas y en el editor ("150 g
  cocido"), con los nombres que da el catálogo ("en seco" para el arroz, "crudo" para el pollo).

**Un agujero que se encontró y se cerró por el camino**: el historial de alimentos sale de la tabla
`meals`, donde los valores ya están convertidos. Reutilizar desde el historial un alimento registrado en
cocido y volver a marcarlo como cocido habría aplicado el factor **dos veces** (158 → 208 kcal/100 g en
pollo). Arreglado haciendo que `/nutrition/history` devuelva también `basis` y `yieldId`, y que el
cliente deshaga la conversión al leerla, manteniendo la invariante de que los `nutriments` de un
`Product` están siempre en base cruda. Hay un test que fija que multiplicar por el factor es el inverso
exacto de la conversión.

### ✅ Tarea C — Aviso de fin de descanso

El temporizador de descanso ya existía (90 s, arranca al guardar una serie y sigue contando en
negativo), pero **no avisaba de nada**: vivía en un `LaunchedEffect` de la tarjeta del ejercicio, y
entre series es justo cuando sales de la app o se bloquea el móvil, momento en el que el sistema puede
congelar el proceso.

**Cómo se resolvió.** El aviso se programa en el sistema operativo al guardar la serie, en vez de
depender de que la app siga viva:
- `RestNotifier` (`expect`/`actual`, mismo patrón que `BackgroundTrackingController`): no-op en
  escritorio, web e iOS; en Android usa `AlarmManager` con `ELAPSED_REALTIME_WAKEUP` — inmune a cambios
  de hora, y despierta la pantalla, porque un aviso de descanso que espera a que enciendas el móvil no
  sirve.
- `RestAlarmReceiver`: un `BroadcastReceiver` (no exportado) que lanza la notificación en un canal de
  importancia alta. Al sistema lo despierta él, así que llega con la app congelada.
- **Sin aviso doble**: siempre se programa la alarma; si el contador llega a cero con la app delante,
  la pantalla cancela la alarma y muestra el aviso dentro de la app. Si estaba en segundo plano no se
  cancela nada y salta la notificación.
- Ampliar el descanso reprograma la alarma y saltarlo la cancela, para que no avise a destiempo.
- Permiso `SCHEDULE_EXACT_ALARM` declarado. Desde Android 12 lo concede el usuario a mano, así que si
  no está concedido se cae a una alarma inexacta en vez de fallar — puede retrasarse, pero avisa. Se
  comprueba en cada llamada, porque el permiso se puede revocar con la app abierta.

**Hallazgo colateral importante: `CLAUDE.md` mentía.** Decía que este entorno no tiene Android SDK y
que `androidMain` no se podía compilar. Es falso: el SDK está instalado (android-36) y
`:composeApp:compileDebugKotlinAndroid --rerun-tasks` termina en BUILD SUCCESSFUL. Varias sesiones
anteriores dieron código de Android por "no verificable" por culpa de esa nota. **Corregido en
CLAUDE.md**, y el código de esta tarea sí está compilado contra el target Android de verdad.

Lo que sigue sin poder comprobarse aquí es el comportamiento en ejecución: que la notificación salte
con el móvil bloqueado, el permiso de alarma exacta y el canal hay que verlos en el móvil.

### ✅ Tarea F — Baremo de pasos bajo cada nivel de actividad

Elegir entre "ligero" y "moderado" a ojo es la mayor fuente de error del cálculo (250-350 kcal/día
entre escalones contiguos) y las descripciones de siempre ("1-3 días de ejercicio por semana") no
dicen nada de lo que uno hace el resto del día. Ahora bajo cada nivel aparecen sus pasos diarios,
que sí se pueden comprobar con el móvil.

Rangos de la clasificación por podómetro de **Tudor-Locke y Bassett, Sports Medicine 2004; 34(1):1-8**:
<5.000 sedentario, 5.000-7.499 ligero, 7.500-9.999 moderado, 10.000-12.499 activo, >12.500 muy activo.
En `shared/.../nutrition/ActivityBenchmarks.kt`, mostrado en el onboarding y en el perfil.

**Dos avisos de honestidad que van escritos en el código y en §16.1 del fundamento científico:**
1. El texto original **no se pudo leer**: el PDF y las versiones de PubMed están tras verificación
   anti-robot. Los cortes vienen de varias fuentes secundarias independientes que coinciden; la
   frontera superior varía en la literatura posterior (la revisión de 2008 la mueve a 12.000).
2. **La correspondencia entre esas bandas y los multiplicadores 1,2-1,9 es nuestra, no está
   publicada.** Son dos escalas ordinales de cinco niveles del mismo concepto y alinearlas es
   razonable, pero ningún trabajo dice que "7.500-9.999 pasos" sea 1,55. Por eso se presenta como
   orientación para elegir, nunca como el dato del que sale el gasto.

### ✅ Tarea E — Pestaña "Progreso" con calendario

**Lo primero que apareció al abrirlo: el histórico no existía.** Al terminar un entrenamiento, las
series sobrescribían `lastSession` de cada ejercicio (que es de donde sale el peso que la app propone)
y la sesión se descartaba. No había nada que mostrar en un calendario porque nunca se guardó nada.
Así que la tarea fue sobre todo crear la persistencia:

- **Tabla nueva `workout_sessions`**, con una fila por sesión y no un documento único por usuario como
  el plan: crecen sin límite y el calendario pide un mes, así que conviene filtrar por fecha en la
  consulta. Lleva índice por `(user_id, date)`.
- **`WorkoutSession` guarda una copia**, no referencias: el nombre y el tipo de cada ejercicio van
  dentro del registro. Si mañana se renombra o se borra un ejercicio del plan, lo que pasó aquel día
  se sigue leyendo igual. Un histórico que cambia al editar el plan no es un histórico.
- Rutas `GET/POST/DELETE /workouts/sessions` con validación de rango de fechas y de límites.
- Al terminar el entreno se guarda la sesión **además** de actualizar `lastSession`, que se mantiene
  intacto porque es lo que alimenta la propuesta de peso.

La pantalla (`ProgressScreen.kt`) tiene cabecera de mes con flechas, resumen del mes (días, series y
volumen total en kg o toneladas), rejilla de calendario empezando en lunes con los días entrenados
marcados y con punto, y al tocar un día marcado se despliega el detalle: cada ejercicio con sus series,
peso apuntado, repeticiones, RIR y el peso total entre paréntesis solo cuando difiere de lo apuntado
(en máquina serían el mismo número dos veces). Se entra desde "📅 Ver mi progreso" en la pantalla de
entrenamientos.

**Limitación ineludible**: los entrenamientos anteriores a este cambio **no se pueden recuperar**,
porque nunca llegaron a guardarse. El calendario empieza a llenarse desde el primer entreno que se
termine con esta versión.

93 tests en verde, servidor y cliente compilando. Sin probar en dispositivo.

### ✅ Tarea H — Alimentos genéricos y búsqueda por relevancia

**El problema, confirmado con datos**: Open Food Facts es una base de productos **envasados de marca**
y el concepto de alimento genérico no existe en su modelo — una búsqueda real de "arroz largo" devolvió
12 resultados, los 12 de marca. Además su orden no tiene en cuenta la intención: buscando "arroz"
salían "Tortitas de arroz y legumbres", "Bebida de Coco & Arroz" y "Galletas de arroz inflado", y el
arroz no aparecía.

**Lo implementado:**
- `shared/.../nutrition/GenericFoods.kt` (nuevo, generado): **89 alimentos genéricos** resueltos uno a
  uno contra el volcado de USDA FoodData Central SR Legacy, cada uno con su `fdcId` para poder
  verificarlo. Nombres en español curados a mano; los números no se tocan. Carnes, pescado y marisco,
  huevos y lácteos, cereales, legumbres, verduras, frutas, frutos secos y grasas.
- **No es una segunda API**: viaja dentro de la app, se consulta en memoria, sin red ni límite de
  peticiones. En ejecución sigue habiendo una sola API de alimentos (OFF, para las marcas).
- `shared/.../nutrition/FoodSearchRanking.kt` (nuevo): puntúa cada resultado por su parecido con lo
  buscado — coincidencia exacta (1000) > empieza por (700) > contiene la palabra (400) > contiene
  todas las palabras sueltas (200) > dentro de otra palabra (50) — y penaliza los nombres largos, que
  son los productos compuestos. Los genéricos llevan un empujón de 250, calibrado para que adelanten a
  una marca que solo contiene el término pero **no** a una que coincide exactamente: buscar
  "hacendado" sigue devolviendo Hacendado primero.
- **La fila de resultado se rediseñó** como Fitia: nombre, debajo la marca (o "Generico" en color de
  acento), y a la derecha los gramos por defecto y sus kcal. Fuera los cuatro chips de macros.

**Por qué esto también es fiabilidad y no solo comodidad**: de un genérico **se sabe la base** (cruda o
en seco) por construcción, mientras que de un producto de marca de OFF es una suposición que no se
puede verificar (CRU-1). Así que cuantas más comidas salgan de la tabla, menos veces hay que adivinar
para la conversión crudo/cocinado.

**Lo que no cubre**: productos españoles sin equivalente en la USDA (jamón serrano, chorizo, manchego,
morcilla) siguen saliendo solo de marcas. Ampliar la tabla es añadir filas a la lista curada del
generador y volver a ejecutarlo.

89 tests en verde, servidor y cliente compilando. Sin probar en dispositivo.

### ✅ Tarea G (2ª corrección) — El freno: productos que ya vienen cocinados

Tras una junta de cuatro agentes (competencia, datos, arquitectura y red team), la conclusión fue que
**la base de un producto de OFF no se puede deducir**: ni del nombre (se verificaron 9 agujeros reales,
los 9 fallaban) ni de los números en general (se midieron ~500 productos y en carne/pescado no hay
señal). Lo que sí se sostiene es un caso concreto, y es justo el que más daño hacía.

**Lo implementado:**
- **Suelo de cordura en cereales y legumbres** (`DRY_FLOOR_KCAL = 250`): un árido en seco está entre
  333 y 378 kcal/100 g y cocido entre 83 y 164; entre medias no existe ningún producto de la familia,
  así que por debajo de 250 el producto ya viene cocinado. Bloquea la conversión y avisa; **nunca
  corrige el número en silencio**. Atrapa el vasito de arroz de Brillante (158 kcal) y los garbanzos
  de tarro (~140), que el nombre no delataba. Ver §13.6.
- **Se aplica también a la elección manual**, que era la puerta por la que se colaban estos casos.
- **Ocho agujeros de nombre tapados y fijados en tests**: prefritas, bravas, tarro, vasito,
  vaporizado, microondas, glaseada, solución añadida.
- **Base por defecto según el uso real**: carnes y pescado en crudo, cereales y legumbres en cocido
  (igual que Fitia).

**Lo que NO arregla, a conciencia**: carne y pescado se quedan sin red numérica porque se midió que no
la hay — el 79 % de los productos de pollo ya cocinados marcan *menos* kcal que el pollo crudo (son
fiambres inyectados con agua y almidón), así que un detector de dos polos fallaría en la dirección
peligrosa. Ahí el error máximo es ~1,3× frente a los 2,3×–4,1× de los áridos, y la carne fresca viene
cruda casi siempre.

**Confirmado por la junta**: la arquitectura ya era la de Fitia. Su centro de ayuda documenta un solo
alimento con selector de tipo de peso, convirtiendo la masa y dejando los nutrientes por ración
intactos — exactamente lo construido. Su ventaja no está en el cálculo sino en tener debajo una capa
de genéricos de la USDA curada, que es la tarea H. Y Fitia **no es perfecta**: sus propias reseñas
reportan que los genéricos de carne y verdura fresca casi nunca aparecen al buscar.

**El mayor error pendiente no es de base**: el aceite de cocinar no se modela y son ±200–400 kcal/día.
Riesgo abierto, sin decidir qué hacer.

**Interfaz simplificada a petición del usuario (2026-11-24)**, copiando la pantalla de Fitia: valores
nutricionales, cantidad, y una sola fila "Tipo de peso" con un chip que al pulsarlo alterna
Crudo/Cocinado y recalcula al instante. **Se quitaron todos los textos explicativos** (la cita de la
fuente y el factor, la equivalencia "≈ 197 g crudo" y el aviso de "este producto ya viene cocinado"),
y con ellos el diálogo de elección manual del alimento de referencia, que era otra vía de mensajes.
El usuario fue explícito: "si se puede se puede y si no nada", la interfaz de añadir tiene que estar
limpia. Consecuencia asumida: cuando el suelo de cordura bloquea un producto, el chip simplemente **no
aparece**, sin explicar por qué; y los alimentos que el catálogo no reconoce por el nombre ya no se
pueden convertir eligiendo referencia a mano. La información de la fuente sigue en
doc/10-fundamento-cientifico.md §13.

**Disposición final del selector de ración (boceto del usuario, 2026-11-24)**: bajo el nombre del
producto va "Datos por {g} g - peso {cocido|crudo}" como en Fitia; debajo, una fila con dos elementos
al 50 % — a la izquierda el campo "Cantidad" y a la derecha "Tipo de peso", que **no es un desplegable
sino un botón** con el mismo aspecto que el campo: se pulsa y alterna crudo/cocinado, recalculando al
instante. Si el alimento no tiene factor, el botón no aparece y la cantidad ocupa todo el ancho.

78 tests en verde, servidor y cliente compilando. Sin probar en dispositivo.

**Lo que se dejó fuera, y por qué** (CRU-2): verduras y avena. No es falta de datos — las parejas están
en el dataset — sino que los factores de verdura se mueven entre 0,8 y 1,2 sobre alimentos de 20–35
kcal/100 g (ruido en el recuento, con dispersión alta entre variedades: la "espinaca" del dataset da
1,17 o 1,38 según especie) y la avena cocinada depende del agua o leche que se le eche, que no es una
propiedad del alimento. El boniato se descartó por dar 0,96, o sea sin cambio.

Verificado con `:shared:jvmTest` (72 tests, 0 fallos), `:server:compileKotlin` y
`:composeApp:compileKotlinJvm`, los tres en verde. **Sin commitear.** Sin probar en dispositivo real
(este entorno no tiene Android SDK): la parte de UI necesita que el usuario la vea en el móvil.

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
