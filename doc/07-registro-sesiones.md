# Registro de sesiones

Bitácora corta por sesión de trabajo sobre el endurecimiento del proyecto. Entrada nueva arriba.

---

## 2026-11-27 (cont.) — Auditoría del registro de alimentos y Fase 0: la búsqueda vuelve a funcionar

Comité de 5 paneles sobre el registro de alimentos (informe en
[13-auditoria-nutricion-2026-11-27.md](13-auditoria-nutricion-2026-11-27.md)), y a continuación la
implementación de la Fase 0 y parte de la Fase 1.

- **El hallazgo que mandaba**: `cgi/search.pl` de Open Food Facts devolvía **503 el 100 % de las
  veces**, así que la búsqueda de marcas estaba muerta y la app degradaba en silencio a los 90
  genéricos diciendo "Sin resultados", que es falso. Invisible en los logs porque un 503 no lanza.
- Búsqueda migrada a **Search-a-licious** con hidratación por EAN. Durante la implementación se
  descubrió que el campo `nutriments` del índice **no es fiable** (nulo en leches españolas con
  `completeness` 0,975 que sí tienen datos en la API de producto): corregido en el informe, porque la
  versión que había escrita invitaba a construir la búsqueda sobre ese campo.
- **Filtro de sanidad** en el servidor, en los dos caminos. Antes no había ninguna comprobación de
  rango en toda la cadena: entraban un coco rallado con 105 g de materia por 100 g y un queso fresco
  con 99,6 kcal declaradas frente a 249 reales. Y el **escáner** permitía registrar una botella de
  vino a **0 kcal**, porque filtraba la lista pero no el escaneo.
- Energía estimada con los factores del **Anexo XIV** (fibra 2, polioles 2,4, alcohol 7) en vez de
  4/4/9, que se queda corta en 52 kcal/100 g en un pan de fibra y en un 60 % en una cerveza.
- **Primeros tests del módulo `server`** (19) y 11 nuevos del cliente. `:server:test` estaba en rojo
  permanente por el test esqueleto de la plantilla, que comprueba una ruta `/` que no existe y exige
  `TURSO_DB_URL`: desactivado con su motivo escrito, no borrado.
- Verificado de punta a punta contra la API real: "yogur griego" devuelve 8 productos españoles con
  marca y ración y "pan de molde integral" 6; las dos daban **cero** antes.

## 2026-11-27 — Maquetas de Nutrición y Pesaje; aplicadas N3 y W5

Seis maquetas de cada pantalla en el lienzo de diseño, con los datos reales. El usuario eligió N3
(línea del día, sin horas) y W5 (pestañas Peso / Análisis IA). Aplicadas: solo cambian la cabecera,
el selector de días, el resumen y las comidas (Nutrición) y la organización en pestañas (Pesaje); la
lógica de guardado, búsqueda, escáner, IA y Premium es la de antes. Antes, arreglo de la hoja de
añadir ejercicio en móviles bajos (commit `b03687e`).

## 2026-11-26 (cont. 2) — Melocotón en todas las pantallas; commits y push

Estilo aplicado sin rediseñar a Nutrición, Peso, Onboarding, Login, Registro, contraseña (olvidé /
restablecer), Suscripción, Progreso, arranque y raíz de la app. Tokens de macros nuevos en la paleta;
dos `Canvas` leían la paleta dentro del dibujo (no compila) y se sacó a una variable. Verificado en
JVM, Android, JS y Wasm. A petición del usuario: commits separados por temas y push.

## 2026-11-26 (cont.) — Tarea 7: unir ejercicios entre días, del simulador al código

Se diseñó con un simulador HTML iterado con el usuario (lista de días, día, añadir/unir, apuntar series)
y se pasó al código tal cual: modelo nuevo con `linkId`/`lastFrom` y configuración por serie
(`perSide`/`unilateral` nullable, lectura antigua por tipo si faltan), espejo en el servidor,
`WorkoutScreen.kt` y `WorkoutLogScreen.kt` reescritas en Melocotón, `ProgressScreen` lee la
configuración de cada serie, `WorkoutModelsTest` reescrito (13 tests). Arreglado de paso un
`padding` negativo que habría hecho caer la hoja de nombre. Batería completa + Android en verde.
El usuario aprobó las piezas que el simulador no tenía (se quedan las 4 de la app real); descartó unir ejercicios ya existentes y las sugerencias de ejercicios. Pendiente: redesplegar el servidor y probar en el móvil.

## 2026-11-26 — Tarea 3: estilo elegido con maquetas y primera pantalla aplicada; tarea 5 cerrada

**Tarea 5 (pulsera).** Se exploró con el usuario y su pulsera en la mano. "Compartir FC" existe pero
es un modo de pantalla (al salir se apaga); la monitorización de Mi Fitness tiene 1 min como mínimo y
el modo "inteligente" mide *menos* en reposo, no más en actividad (ficha oficial de la Band 10). El
usuario la dio por cerrada. Detalle y la vía no probada, en `CONTINUAR.md`.

**Tarea 7.** Diseñada pero no implementada: la identidad de un ejercicio se **declara** al añadirlo
("es el mismo que este de otro día") en vez de deducirse del nombre, que fue la objeción del usuario.
Maqueta navegable de cinco pantallas en https://claude.ai/artifact/7VTBXk5NPcDDfGe5WMy4N8.

**Tarea 3 (rediseño).** Todo por maquetas antes de tocar código, en un lienzo de diseño con cuatro
páginas (https://claude.ai/artifact/9vnNXBDdPnrQqqp4qDrhJr): 6 direcciones → 6 variaciones de la que
gustó → 6 estructuras de la elegida → la elegida es "Melocotón y chocolate", estructura M1. Después,
seis versiones del perfil en esa paleta, pendientes de elección.

Aplicado al inicio con una base pensada para el interruptor claro/oscuro: paleta con **tokens por
función** servida por `CompositionLocal` (`Palette.kt`), migración pantalla a pantalla sin tocar el
`GymSmartColors` viejo, DM Sans incluida, iconos de línea con los trazos exactos de la maqueta y una
función de barra de estado para que la hora no desaparezca sobre el crema con el móvil en modo
oscuro. Una diferencia deliberada con la maqueta: la barra no va hacia "10.000 pasos" (objetivo que la
app no tiene) sino hacia el siguiente nivel del baremo, que en el caso de la maqueta da lo mismo.

**Después, el perfil**: el usuario eligió una mezcla de las maquetas P1 y P4 (cabecera a sangre con
sus cifras + pestañas Cuerpo/Cuenta/Pulsera) y pidió aplicarla "tal cual". Hecho con la lógica
intacta; la única desviación es que el deslizador de ritmo conserva su zona verde y su línea de
kcal, que la maqueta simplificaba.

**Verificado**: `:shared:jvmTest` (116), `:composeApp:jvmTest`, `:server:compileKotlin`,
`:composeApp:compileKotlinJvm`, `:composeApp:compileDebugKotlinAndroid`. **Sin verificar en
dispositivo.** **Nada commiteado** (tampoco la tanda 1/4/2/6 del 2026-11-25).

---

## 2026-11-25 — Segunda lista de mejoras (1-7): investigadas las siete, hechas 1, 4, 2 y 6

Mismo patrón que la sesión anterior: el usuario dio 7 mejoras, pidió **no tocar nada** hasta tenerlas
todas investigadas (dentro del código y fuera, con fuentes donde hacían falta) y clasificadas por
esfuerzo y nivel de modelo. Vista la tabla, pidió ejecutar **1, 4, 2 y 6 en ese orden y del tirón**.

Tres decisiones se tomaron sin respuesta del usuario, porque pidió no parar, y quedan marcadas como
reversibles en `CONTINUAR.md`: en la 1, borrar los atajos sin sustituirlos; en la 4, emoji en vez de
iconos vectoriales; en la 6, la convención de las repeticiones en unilateral.

**Lo que salió de la investigación y no se esperaba:**

- **El TEF rompe la comparación obvia (tarea 2).** `BMR × (PAL − 1)` no son calorías de movimiento:
  el PAL se define como `gasto total ÷ BMR` y el gasto total incluye el efecto térmico de los
  alimentos (~10 %). La pulsera reporta solo actividad. Sin descontarlo, la app le habría dicho al
  usuario que su pulsera marca siempre un 10 % menos que su nivel — la misma familia de fallo que ya
  le hizo perder peso queriendo ganarlo. Documentado en §16 del fundamento científico, que es sección
  nueva: la ciencia de los pasos de la tarea F **nunca se había escrito**, y la referencia del código
  a "§14" era falsa (§14 son los invariantes de tests).
- **La pulsera ya hace lo que el usuario quería hackear (tarea 5).** La Band 10 emite FC por el perfil
  BLE estándar; Xiaomi lo documenta. Pero no arregla las kcal: el cuello de botella medido está en
  Mi Fitness → Health Connect, y convertir pulso en calorías en el gimnasio cae fuera del rango de
  validación de Keytel (2005). Queda como spike, no como tarea.
- **El rediseño (tarea 3) es, de largo, lo más caro de la lista**: 955 referencias directas a
  `GymSmartColors.*` en 20 archivos, sobre un `object` de constantes que no puede cambiar en runtime.
  Hoy un interruptor claro/oscuro es imposible sin tocar las 955.

**Dos bugs preexistentes que aparecieron al hacer el trabajo, los dos de la tanda anterior:**

1. **El baremo de pasos de la tarea F no se veía en el onboarding.** `SelectableRow` pintaba
   `parts[0]` y `parts[1]` y tiraba el resto sin avisar; los pasos iban como tercera línea. Solo se
   veían en el perfil. Arreglado.
2. **El conmutador total/por lado ya existía, escondido y amnésico**: un `remember` local dentro del
   teclado numérico que se olvidaba al cerrar la hoja. La tarea 6 lo ha convertido en una preferencia
   guardada del ejercicio, visible en la tarjeta de peso.

**Decisión de diseño que vale la pena recordar (tarea 6):** `entryTotal` es una preferencia *de
entrada*; lo que se guarda en `WorkoutSet.value` sigue siendo la unidad canónica del tipo. Guardar el
total habría hecho que el histórico significase cosas distintas según cuándo se apuntó cada serie. En
cambio `unilateral` **sí** se copia a `SessionExercise`, porque cambia el volumen y el volumen de un
día pasado no puede depender de cómo esté el plan hoy.

**Un fallo propio por el camino**: el test de comprobación cruzada de la tarea 2 usaba 0,04 kcal/kg/km
cuando ese 0,04 es por *paso* (el valor por km es ~0,75). Lo cazó el propio test al fallar; corregido
en el test y en el KDoc. El código nunca llegó a estar mal.

**Verificado**: `:shared:jvmTest` (111), `:composeApp:jvmTest` (23), `:server:compileKotlin`,
`:composeApp:compileKotlinJvm` y `:composeApp:compileDebugKotlinAndroid`, todo en verde. 28 tests
nuevos entre las tres tareas con lógica. **Nada commiteado.** Pendiente de ver en el móvil: el aspecto
de los emoji (tres son de Unicode 13/14) y de los conmutadores nuevos.

---

## 2026-11-24 — Nueva lista de mejoras A-G: hechas A, D, B y G

Sesión de funcionalidades, no de endurecimiento. El usuario pidió 7 mejoras (luego 8) y, antes de tocar
código, una clasificación por complejidad y nivel de modelo, con una regla explícita: **no investigar
nada que no se esté haciendo en ese momento**. Se dio la tabla, se acordó el orden (A+D → B+G → F → E →
C) y se fue ejecutando por tandas con permiso explícito en cada una. Detalle de cada tarea en
`doc/CONTINUAR.md`.

**A** (la búsqueda de comida se borraba al volver atrás) y **D** (la hoja de peso de un ejercicio no se
desplegaba entera) eran arreglos cortos: estado hoisteado fuera del `when` de hojas en el primer caso,
`skipPartiallyExpanded = true` en tres `ModalBottomSheet` en el segundo.

**B — colecciones de comida.** Cuarta pestaña en el buscador; una colección es un grupo de alimentos con
cantidad fijada que se añade de golpe. Decisión de diseño: los `SheetMode` nuevos van **en paralelo** a
los del flujo real de comidas, sin tocar esas ramas, para no arriesgar la función más crítica de la app
por una secundaria; los composables de búsqueda y de ración se reutilizaron tal cual porque ya eran
genéricos.

**G — pesar en crudo o en cocido.** La más delicada, y la que el usuario marcó como "muy muy crucial,
porque la app se basa en medir comidas". El error previo era grande y en los dos sentidos (−28 % en
pollo pesado hecho, +181 % en arroz cocido). Lo relevante del enfoque:

- **La primera pasada estuvo mal planteada y el usuario la cortó bien.** Se entregó un catálogo de 11
  alimentos sacados a mano de uno en uno, sin ni un pescado, y la respuesta fue la pregunta correcta:
  *"si para la mitad de alimentos no puedo poner cocido, de qué sirve?"*. El problema no era la calidad
  de cada factor sino que el método de obtenerlos no escalaba. Lección aplicable más allá de esta
  tarea: cuando hace falta un catálogo de datos, buscar el volcado completo de la fuente antes de
  empezar a extraer entradas a mano.
- **Rehecho de forma sistemática.** Se descargó el volcado de **FoodData Central SR Legacy** (6 MB) y
  se emparejaron automáticamente sus **1.261 parejas crudo/cocinado** aprovechando que el dataset nombra
  los alimentos de forma regular. El catálogo pasó de 11 a 24 alimentos, con pescado incluido. De
  rebote mejoró un dato: la pechuga de pollo pasó de 0,72 (tabla de 1975, pechuga *con piel*) a 0,76
  (SR Legacy, sin piel ni hueso, que es lo que se compra). Todo citado con identificador en
  `doc/10-fundamento-cientifico.md` §13.4.
- **Lo que de verdad cierra el hueco no es el tamaño del catálogo.** Emparejar nombres libres de Open
  Food Facts en español siempre va a fallar en algunos, tenga el catálogo 24 entradas o 200. Así que
  cuando no hay coincidencia automática se le pregunta al usuario a qué alimento se parece. El factor
  sigue saliendo del catálogo con su fuente; lo que se le pide es *qué alimento es*, que es el dato que
  faltaba. No se le deja teclear un factor, que sería avalar cifras inventadas.
- **La respuesta a la pregunta del usuario sobre la API fue que no hacía falta cambiarla.** Open Food
  Facts no da datos de conversión, pero tampoco los necesitaba: el factor es del alimento, no del
  producto, así que vive en un catálogo propio y se aplica sobre los valores que OFF ya devuelve.
- **El catálogo no adivina**: 11 categorías reconocidas y una lista de exclusiones que descarta lo que ya
  viene cocinado o es un preparado. Sin coincidencia, no hay selector y nada cambia.
- **Se encontró y se cerró un agujero por el camino**: el historial de alimentos devuelve los valores ya
  convertidos, así que reutilizar desde ahí un alimento pesado en cocido habría aplicado el factor dos
  veces. Ahora el historial devuelve también la base y el cliente deshace la conversión al leerla.
- **Se dejó fuera lo que no aporta, con el motivo escrito**: verduras y avena. No por falta de datos,
  sino porque los factores de verdura son ruido sobre alimentos de 20–35 kcal/100 g y la avena cocinada
  depende del agua que se le eche, que no es propiedad del alimento (CRU-2).

**Corrección de una afirmación falsa del propio backlog (OFF-2).** Al hablar con el usuario de las
limitaciones frente a una app de macros, se repitió una nota que ya estaba escrita en
`08-backlog-mejoras.md`: que OFF estaba retirando `search.pl` en favor de `/api/v2/search`. El usuario
preguntó por ella y, al verificarla contra la documentación oficial, resultó incorrecta por partida
doble: OFF **no** ha anunciado retirada alguna de `search.pl` (lo llama "legacy", nunca "deprecated", y
no da fecha), y `/api/v2/search` **no es su sustituto** porque no hace búsqueda por texto — migrar ahí
habría roto la búsqueda. El sustituto real es Search-a-licious (`search.openfoodfacts.org`), que se
probó y funciona. De paso apareció el riesgo que sí importa: **10 peticiones/minuto por IP** para todas
las búsquedas, saliendo todas de la única IP de Render; se comprobó que la app no busca al teclear, que
es lo que la doc avisa expresamente de no hacer. Corregido en el backlog como OFF-2, con la nota vieja
citada para que no vuelva a colarse. Lección: una afirmación heredada de la documentación del propio
proyecto no es una fuente verificada, y repetirla sin comprobarla es propagar el error.

**Junta de cuatro agentes sobre la fiabilidad de crudo/cocido.** A petición del usuario se contrastaron
cuatro ángulos en paralelo: competencia (qué hace Fitia de verdad), datos (medición empírica), una
segunda opinión de arquitectura y un red team. Resultados que cambiaron el diseño:

- **El red team denunció 9 agujeros del emparejado por nombre y los 9 se verificaron reales** en el
  código (prefritas, bravas, tarro, vasito, vaporizado, microondas, glaseada, solución añadida, hueso).
  Conclusión: la lista de exclusiones no estaba incompleta, estaba mal de raíz — las palabras no se
  acaban nunca.
- **El de datos midió ~500 productos reales y tumbó el detector numérico general** que se iba a
  construir. Hallazgo clave: el 79 % de los productos de pollo ya cocinados marcan *menos* kcal que el
  pollo crudo (fiambres inyectados con agua), así que un detector de dos polos los daría por crudos y
  fallaría en la dirección peligrosa. Además señaló que el experimento que validaría el detector no se
  puede ejecutar, porque la etiqueta contra la que habría que contrastarlo —la base del producto— es
  justo la que no existe en OFF.
- **El de competencia corrigió una afirmación equivocada que ya se le había dado al usuario**: Fitia no
  duplica entradas crudo/cocido. Su ayuda documenta un solo alimento con selector de tipo de peso, que
  convierte la masa dejando los nutrientes por ración intactos — exactamente la arquitectura ya
  construida. Su ventaja está en la capa de genéricos de la USDA curada, no en el cálculo.
- **El arquitecto aportó el reencuadre útil**: con un autoajuste que integra la ingesta a 28 días, un
  sesgo constante se absorbe solo; lo letal es una discontinuidad súbita en un alimento suelto. Eso es
  lo que define qué hay que arreglar y qué no.

De ahí salió el freno implementado: suelo de cordura de 250 kcal/100 g en áridos (franja físicamente
vacía entre el alimento seco y el cocido), aplicado también a la elección manual, que bloquea y avisa
en vez de corregir en silencio. En carne y pescado no se pone red porque se midió que no hay señal.

78 tests en verde en `shared`, servidor y cliente compilando. La UI de G y D no se pudo probar en
dispositivo (sin Android SDK en este entorno).

---

## 2026-11-23 (continuación) — Bloques D y E: los 5 bloques de la auditoría de kcal quedan completos

Último tramo del plan de `doc/11-auditoria-kcal.md`, sin pasar por delegación a otro modelo (se había
valorado para C/E, pero se hizo todo en la misma sesión).

**Bloque D (honestidad de la interfaz)**: `NutritionMath.targetBand` nueva (banda de incertidumbre, ±150
kcal con fórmula / ±75 con medición, redondeada a 25) mostrada en Onboarding y Perfil junto al objetivo
exacto; la caja "BMR" corregida en ambas pantallas (mostraba `tdee`, ahora muestra `bmr`); aviso en
`NutritionScreen` cuando hay pulsera y no hay datos de hoy (`wearableEnergy` se calculaba y se tiraba);
`Macros.degraded`/`TargetResult.macrosDegraded` nuevos, con aviso cuando el reparto ideal no cupo en el
objetivo; y la pieza más grande, `TargetResult.lowBmiGuard` — IMC < 18,5 con un objetivo que pide más
déficit hace caer el suelo al mantenimiento entero, con su propio texto, sin bloquear a quien en ese
mismo rango pide ganar peso.

**Bloque E (AUD-18, 19, 23, 24)**: la migración SQL del ritmo reescrita para reproducir exactamente
`NutritionMath.clampRate` (antes `MIN(-0.05, goal_rate)` subía magnitudes válidas, el mismo género de
bug que motivó la reescritura del ritmo en primer lugar); el formato de `resolvedAge` corregido de
`"dd/MM/yyyy"` a `"ddMMyyyy"` (el real del cliente), con validación de rango añadida porque arreglar el
formato reactivaba un riesgo de edad absurda que estaba dormido por el mismo bug; el signo recuperado en
`formatOneDecimal`/`formatTwoDecimals`; y `/profile/adaptive` pasado de GET a POST, con
`runAdaptiveAdjustment` serializado por un `Mutex` por usuario y `upsertProfile` haciendo el `INSERT OR
REPLACE` + la restauración del autoajuste en un único `executeBatch` con `BEGIN IMMEDIATE`/`COMMIT`.

**Verificación antes de confiar en el punto de las transacciones**: se probó contra la Turso real (de
solo lectura, `BEGIN`/`SELECT 1`/`COMMIT`) que el pipeline HTTP de libSQL acepta esos statements antes de
construir el arreglo de `upsertProfile` sobre ese supuesto.

49 tests en verde (`:shared:jvmTest`), `:server:compileKotlin` y `:composeApp:compileKotlinJvm` también.
Sin verificar con build real nada androidMain (no se tocó en este tramo). **Sin commitear todavía.**

Fuera de alcance de los 5 bloques, documentado y sin tocar: AUD-11 (snapshot/`calcVersion` del objetivo
histórico) y el ángulo del ciclo menstrual del autoajuste — ambos son cambios de arquitectura mayores.

---

## 2026-11-23 — Eliminación de "recomposición"/cintura y Bloque C de la auditoría de kcal

Dos decisiones directas del usuario, sin pasar por las vías intermedias que proponía el informe:

- **Eliminada la opción de objetivo "Recomposición" (`GOAL_RECOMP`)** en vez de implementarla de verdad
  o renombrarla como "déficit suave". Quedan `lose_fat` / `gain_muscle` / `maintain`. AUD-7 resuelto.
- **Eliminado el seguimiento de cintura** ("es pereza" medirse con cinta): quitado el campo del diálogo
  de peso, su validación, los modelos cliente/servidor, y la tarjeta `CompositionCard` que interpretaba
  peso + cintura. La columna `waist_cm` se deja sin tocar en la BBDD (no es destructivo borrarla, pero
  tampoco hacía falta).

Ambos cambios commiteados en `3d19e2f`. Verificado con `:shared:jvmTest` (40 tests),
`:server:compileKotlin`, `:composeApp:compileKotlinJvm`.

**A continuación, Bloque C de la auditoría** (calidad del dato del autoajuste), los cinco puntos del
plan: mínimos de `ADAPTIVE_MIN_SPAN_DAYS`/`WEIGH_INS` subidos a lo que exige §5.3 (10→14, 4→8), umbral
de "día válido" (`ADAPTIVE_MIN_DAY_KCAL = 1000`), cobertura mínima de ventana (`ADAPTIVE_MIN_COVERAGE =
0.8`, nuevo campo `windowDays` en `AdaptiveInput`), mínimo de días en la media de la pulsera
(`WEARABLE_MIN_DAYS_WITH_DATA`), y `weight_history` escribiéndose siempre en vez de solo cuando cambia
el peso. AUD-9, AUD-10, AUD-12 y AUD-13 marcados resueltos en `11-auditoria-kcal.md`.

El escenario modal del informe original (déficit real del 31-44% informado como 23%, por registro
parcial) ya no pasa el filtro de "día válido". 43 tests en verde, con cuatro nuevos específicos de estos
cambios. **Sin verificar con build real el punto de la pulsera** (`HealthConnectProvider.kt` es
`androidMain`). **Sin commitear todavía.**

Quedan los Bloques D y E.

---

## 2026-11-22 — Auditoría independiente del cálculo de kcal por objetivo (solo diagnóstico)

El usuario pidió una **auditoría objetiva e independiente** de la funcionalidad de kcal en el sentido de
los objetivos (perder/subir/mantener/recomp), con instrucciones explícitas: no asumir que su
implementación es correcta, no darle la razón por defecto, no confundir que el código funcione con que la
solución sea correcta, contrastar contra documentación oficial y estándares, y buscar errores
conceptuales, problemas de arquitectura y casos límite. Pidió **cuatro agentes independientes** con
reparto fijo (investigador / auditor técnico / red team / evaluador) y un informe final con gravedad
clasificada. **Explícitamente: no modificar archivos, solo auditoría y diagnóstico.**

**Cómo se hizo**: los tres primeros agentes en paralelo; el evaluador como fork de la sesión, para que
tuviera los tres informes completos y pudiera verificar por su cuenta en lugar de resumir. Además se
verificaron a mano, desde la sesión principal, los cinco hallazgos más graves.

**Resultado: [11-auditoria-kcal.md](11-auditoria-kcal.md)** (nuevo), con AUD-1 a AUD-24. Resumen en
`08-backlog-mejoras.md`, dos entradas nuevas en `03-seguridad.md` (15 y 16), y correcciones a §7.3 y §14
de `10-fundamento-cientifico.md`.

**Veredicto**: el modelo energético es **correcto**. Las tres decisiones que más apps fallan están bien
(ritmo en % de peso corporal, déficit derivado de una *velocidad* y no de un *plazo*, y
`BMR + ActiveCaloriesBurnedRecord` sin doble conteo), el uso de 7700 kcal/kg es el único defendible, y
las constantes del código coinciden **todas** con las tablas del fundamento científico — no hay ninguna
inventada. Se descartó explícitamente la crítica "debería usar el modelo dinámico de Hall": gana
precisión en la *trayectoria*, que es funcionalidad que esta app no tiene ni pretende.

**El fallo está en dónde se imponen los invariantes, no en el modelo.** Tres líneas explican ~70 % de los
hallazgos: `targetKcal = baseTdee + dailyDelta + carryOverKcal` (el arrastre fuera del recorte),
`baseTdee = measuredTdee ?: formulaTdee` (sustitución en vez de composición), y la decisión de §7.3 de
cambiar un *suelo de ingesta* por un *tope de déficit* sin re-derivar las consecuencias. **AUD-1, AUD-2,
AUD-5 y AUD-6 son un solo error de razonamiento.**

Lo más destacable de cada rol:

| Agente | Lo que aportó que los demás no vieron |
|---|---|
| Investigador | 44 requisitos verificables contra fuentes primarias abiertas (FAO/WHO/UNU 2004, Health Connect, NIH Body Weight Planner, guías de Apple/Play). El dato que debería gobernar toda la comunicación: **el error estándar publicado del BMR estimado es de 110-170 kcal/día**, del mismo orden que el déficit que se busca. Y que **los multiplicadores 1,2-1,9 no tienen fuente primaria localizable**, con 1,2 por debajo del mínimo sedentario de FAO (1,40). |
| Auditor técnico | Ejecutó `:shared:jvmTest` (31 tests en verde) y demostró que **todos los hallazgos conviven con la suite verde**. Encontró la migración ausente de `has_wearable` y el desajuste de formato de `resolvedAge`. |
| Red team | **Las ocho refutaciones son su parte más valiosa**: demostró que el subregistro *uniforme* se autocancela (mérito real del diseño), que no hay doble conteo BMR↔activas, y que el % usa el peso actual. También el hallazgo más afilado y exclusivo: `weight_history` solo se escribe si el peso cambia, así que **`recomp` se queda sin autoajuste habiéndose pesado a diario**. |
| Evaluador | Recalibró gravedades con el criterio de "app personal de un solo usuario", **declaró falsos o exagerados cinco hallazgos** de los otros dos, y encontró lo que ninguno vio: **el ritmo por defecto (−0,75 %) ya está recortado en `sedentary` y en `light`**, es decir inalcanzable en el 40 % de los niveles ofrecidos. |

**Hallazgo con más impacto clínico (AUD-1)**: no hay suelo calórico absoluto — el objetivo colapsa a la
identidad exacta `0,90 × BMR` en sedentarios — y el único límite que existe es **evadible**, porque
`carryOverKcal` se suma fuera del recorte y además **no está condicionado a `activityLevel`** (a
diferencia de `avgActiveKcal`). Verificado: objetivo de 1096 kcal con `rateLimited = false`, es decir sin
ningún aviso. **Ningún test lo cubría porque el barrido de ~2.000 combinaciones nunca pasa
`carryOverKcal` ni `measuredTdee`** — los dos únicos parámetros capaces de romper los invariantes de §14.

**Hallazgo más incómodo (AUD-2/AUD-5)**: la app promete *"nunca por debajo de tu metabolismo basal"* en
dos pantallas cuando ese suelo fue retirado, y el objetivo está al 90 % del BMR justo cuando ese aviso
aparece. Y el defecto del `RateSlider` (su tercio superior no cambia el objetivo mientras promete +54 %
de pérdida) **es de la misma naturaleza exacta que el que motivó toda la reescritura del 2026-11-01**: el
error pasó de −55 % a −39/−54 %, se redujo pero se mudó un eslabón.

**Decisiones del usuario al cerrar la sesión**: suelo por **disponibilidad energética** (≥30 kcal/kg
MM/día con masa magra estimada, aplicado al final sobre el número mostrado), `carryOverKcal` **acotado
dentro del recorte y gateado por modo pulsera**, y **documentar antes de implementar**. Las tres están en
`CONTINUAR.md` para no re-preguntarlas.

**Pendiente y bloqueado**: la verificación de 30 segundos de AUD-16 (¿guarda perfil la app en
producción?), que decide si es un defecto latente o si el guardado de perfil y el registro de peso llevan
rotos desde `dc1b216`. No se puede comprobar sin acceso a la BBDD desplegada.

---

## 2026-11-22 — Rediseño de la maqueta de la pestaña "Entreno" (registro de series)

El usuario trajo su propia maqueta HTML (`~/Downloads/GYMTRACK.html`, un registrador de series de
gimnasio) para meterla como pestaña nueva del menú principal, pero pidió rediseñarla antes: que se pueda
apuntar cualquier peso **sin abrir el teclado del sistema**, que lo entienda cualquiera y que tenga un
aire más profesional. No se tocó código Kotlin en esta sesión.

**Entregado**: `~/Downloads/GYMTRACK-v2.html` — maqueta interactiva de un solo archivo, con los colores,
tipografía, radios y espaciados de `GymSmartColors`/`GymSmartTheme`. Verificada con jsdom simulando el
flujo completo de un usuario (cargar discos, teclado propio, guardar, editar, borrar, deshacer, modo
mancuerna, terminar entreno): sin excepciones, y el reparto de discos probado contra 12 casos
(incluidos pesos no montables y el tope de 500 kg).

**Fallos encontrados en la maqueta original** (los importantes, por los que se rediseñó):

| # | Fallo | Por qué importa |
|---|---|---|
| 1 | Los discos sumaban su valor **una sola vez** | Un disco de 20 se pone a cada lado: el peso real es +40. La maqueta apuntaba la mitad de lo levantado. |
| 2 | El peso de la barra (20 kg) estaba fijo y oculto | No se podía usar barra de 15, barra Z ni máquina sin barra. |
| 3 | No se podía quitar un disco ni deshacer | Si te pasabas, solo quedaba bajar de 1,25 en 1,25. |
| 4 | De 102,5 a 20 kg = 66 pulsaciones | El ± tenía paso único y sin pulsación mantenida. |
| 5 | Cambiar de ejercicio reseteaba a 20 kg / 10 reps | Se perdía el peso que estabas usando; nadie empieza todos los ejercicios en 20 kg. |
| 6 | No se veía qué hiciste la última vez | Es el dato que decides *antes* de cargar la barra. |
| 7 | No se podía editar ni borrar **una** serie | Solo "reiniciar series", y además sin confirmación ni deshacer. |
| 8 | En modo máquina, pulsar "0,5 kg" tiraba el peso de 100 a 0,5 | Pérdida de datos a un toque, sin aviso. |
| 9 | Botones de 24 px y textos de 7-9 px | Inusable con las manos sudadas; muy por debajo del mínimo táctil de ~48 px. |
| 10 | Tres sitios discutiendo lo mismo ("SERIE 1", "GUARDAR SERIE 1", "0 series registradas") | Ruido. |
| 11 | Sin descanso entre series, sin volumen, sin RIR, sin terminar entreno | Falta lo que de verdad se usa en el gimnasio. |
| 12 | Iconos con caracteres raros (♧ ▣ ▤ ◉ ☷) y nada se persistía | Se ven distintos en cada dispositivo y se perdía todo al recargar. |

**Soluciones del rediseño**: modo "barra y discos" con el total calculado (barra + discos por lado ×2) y
discos quitables uno a uno; modo "peso directo" para máquina/polea/mancuerna, con los pesos que ya has
usado como atajos y un conmutador "por mancuerna ×2"; **teclado numérico propio** en hoja inferior como
vía universal (en modo barra reparte el total en discos, y si no es montable avisa de cuánto falta en vez
de mentir); paso ±2,5/5/10 con pulsación mantenida acelerada; cada ejercicio arranca en su último peso;
"repetir serie anterior"; editar/borrar serie con deshacer; temporizador de descanso al guardar;
cronómetro de sesión, volumen y serie top; progreso por ejercicio en el carrusel.

**Segunda iteración tras el feedback del usuario (la que vale): `~/Downloads/GYMTRACK-v3.html`.**
Rechazó la v2 con dos objeciones que son, en realidad, el concepto del producto:

- *"me guio por lo que meto en los lados… en press banca tiro 80 kg en total pero yo siempre recuerdo
  que meto una de 30 a los lados"* → el número grande pasa a ser **el peso por lado**, y el total se
  muestra calculado debajo (`barra + 2 × lado`). Se acabó la acumulación de discos uno a uno: se apunta
  directamente lo que pones a cada lado.
- *"alomejor el concepto se podria definir antes… decidir de antes si yo hago este ejercicio en maquina
  o en libre o en mancuernas"* → el **tipo de ejercicio** (barra libre / mancuernas / máquina / polea /
  peso corporal) se elige al crear el ejercicio y es lo que determina qué número pide la app, qué unidad
  enseña y el paso del ±. En máquina y polea no se habla de lados en ningún momento.

Efecto: fuera la rejilla de 7 discos, el selector de barra, el selector de paso, el conmutador de modo
y el RIR de la pantalla principal. La tarjeta de peso pasa de ~20 botones a 7. El teclado propio sigue
siendo la vía universal y, en barra y mancuernas, ofrece un enlace pequeño "escribir el total" que hace
la conversión inversa (100 totales → 40 por lado). Verificado otra vez con jsdom: los cinco tipos, el
teclado en ambos sentidos, cambiar el tipo de un ejercicio ya creado y el alta de ejercicio nuevo.

**Tercera ronda de feedback**, dos correcciones más sobre la v3:

- *"aumentas y desaumentas de 5 en 5 por la cara, hay pesos entre medias eh"* → el salto del `-`/`+`
  pasa a ser **configuración del ejercicio** (0,5 / 1 / 1,25 / 2,5 / 5 / 10, expresado en la unidad de su
  tipo: "2,5 kg/lado", "1 kg"...), elegible en la misma hoja que el tipo. Valores de partida bajados:
  2,5 por lado en barra, 1 en mancuernas y **2,5 en máquina y polea** (antes 5, que era el fallo).
- *"y el rir?"* → **vuelve**. Lo había quitado yo de la v3 para reducir botones, sin que él lo pidiera.
  Vuelve como un chip al lado de "Repeticiones" (un botón en pantalla, no seis), que abre una hoja con
  las opciones explicadas; recuerda el último valor del ejercicio, se guarda en cada serie, se muestra
  en la fila de la serie y se recupera al editarla.

- *"que el rir salga como antes"* (con captura de la fila de la v2) → el RIR vuelve a ser la **fila de
  botones en pantalla** (`— 0 1 2 3 4+`) bajo las repeticiones rápidas, no el chip con hoja que había
  propuesto: apuntarlo es cosa de cada serie y escondérselo detrás de dos toques no valía. Cada botón
  lleva su explicación como `title`.

**Cuarta ronda** (tres cambios que afectan al modelo de datos, importantes para la implementación):

- **El peso se pone solo, serie a serie, contra la sesión anterior.** Fuera los chips de "tus pesos".
  El ejercicio guarda las series de la última sesión (`prev`), y al ir a registrar la serie N la app
  propone `prev[N-1]` (peso y reps). Al guardar, pasa sola a proponer `prev[N]`. Si aquel día no llegaste
  a esa serie, se queda lo que acabas de meter. La primera vez de un ejercicio: **0**, no se inventa nada.
  Al terminar el entreno, lo de hoy pasa a ser el `prev` de mañana.
- **Las rayitas del carrusel son las series que hizo, no un objetivo.** Se eliminó `TARGET = 4` y el
  "0/4": ahora se pinta una rayita por serie guardada (4 por fila, tope de 8) y el contador va vacío
  hasta que hay algo. No suponer nunca cuántas series va a hacer.
- **El salto del −/+ sale a la pantalla principal**, en una sola línea de chips (0,5 / 1 / 1,25 / 2,5 /
  5 / 10) donde estaban los atajos, con la frase que explica para qué sirve. Ya no está en la hoja del
  ejercicio.

Lección para la implementación: no quitar cosas del usuario por iniciativa propia al simplificar; lo que
sobraba eran los botones redundantes, no las funciones. Y el RIR, en pantalla, no escondido.

**Quinta ronda: la pantalla de antes.** El usuario pidió la pantalla previa, la que da sentido al
cronómetro de la cabecera: **definir sus días de entrenamiento**. Prototipada dentro del mismo archivo,
que pasa a tener tres vistas:

1. **Mis entrenamientos** — lista de días (uno de ejemplo ya hecho: "Pecho · Hombro · Tríceps" con sus 6
   ejercicios). Cada tarjeta enseña los ejercicios con el icono de su tipo y tiene "Entrenar" y "Editar".
   Un día con una sesión abierta se marca en verde y el botón pasa a "Seguir entrenando".
2. **Definir el día** — nombre (campo de texto + nombres sugeridos) y lista de ejercicios: añadir
   (con la misma hoja de tipo que ya existía, más un campo para escribir uno que no esté en la lista),
   reordenar con ↑↓, quitar con deshacer, y tocar uno para cambiarle el tipo/barra. Guardar o eliminar.
3. **Apuntar** — la de siempre, sin cambios de concepto. Ahora trabaja sobre los ejercicios del día que
   abriste, el cronómetro arranca al pulsar "Entrenar" y "Terminar entrenamiento" vuelca las series de
   hoy a `prev` (de donde sale el peso autorrellenado la próxima vez) y vuelve a la lista de días.

Modelo de datos resultante, que es lo que hay que llevar a Kotlin: `DAYS = [{name, ex:[Ejercicio]}]`,
donde cada ejercicio guarda `{name, kind, bar, step, prev:[{v,r}], sets:[]}`. La pantalla de apuntar no
tiene datos propios: opera sobre `DAYS[dayIdx].ex`.

**Implementado en la app (misma sesión).** El usuario dio el visto bueno y pidió meterlo tal cual.
Ficheros nuevos:

- `composeApp/.../model/WorkoutModels.kt` — modelo y toda la aritmética (`totalKg`, `valueFromTotal`,
  `setLabel`, `volume`, `formatKg`) más el catálogo de tipos `WorkoutKind`.
- `composeApp/.../screens/WorkoutScreen.kt` — lista de días + editor + hojas (añadir ejercicio, tipo).
- `composeApp/.../screens/WorkoutLogScreen.kt` — apuntar series, con el teclado numérico propio.
- `composeApp/.../services/WorkoutService.kt` — GET/PUT del plan.
- `composeApp/src/commonTest/.../WorkoutModelsTest.kt` — 13 tests de la aritmética (pasan).
- `server/.../model/WorkoutModels.kt`, `services/WorkoutService.kt`, `routes/WorkoutRoute.kt`.

Tocado: `NavGraph.kt` (ruta `workout`), `DashboardScreen.kt` (tarjeta "Entreno" la primera del menú;
las otras cuatro se reordenan en tres filas), `Application.kt` y `plugins/Routing.kt` (alta del servicio).

Decisiones tomadas sin preguntar, por no bloquear (se pueden revertir):

- **Persistencia en el servidor**, como el resto de la app, en `workout_plans`: una fila por usuario con
  el plan entero en JSON. No se normaliza en tablas porque siempre se lee y se escribe completo y el
  servidor no consulta nada por dentro; si algún día hace falta estadística por ejercicio en el
  servidor, habrá que normalizarlo. **Requiere reiniciar el servidor** para que corra el `CREATE TABLE`.
- **La sesión en curso también se guarda** (campo `session` del mismo documento), para que cerrar la app
  a mitad de entrenamiento no borre las series apuntadas. Al volver a entrar, la pantalla se abre
  directamente en el entrenamiento a medias.
- Las tres vistas son **estado interno de un composable**, no rutas de navegación: comparten el plan en
  memoria y el ida y vuelta es constante.
- Si falla la red al guardar, se avisa con una franja ámbar y **no se pierde nada**: el estado sigue en
  memoria y el siguiente guardado manda el plan entero otra vez.

**Ajuste tras la primera prueba del usuario en Desktop**: el estado vacío era un `Surface` que se
ceñía a su contenido y quedaba colgando arriba a la izquierda, y en una ventana ancha todo se estiraba
de lado a lado. Dos arreglos: estado vacío propio (tarjeta centrada, con icono, qué es un "día" con un
ejemplo y un único botón "Crear mi primer día"), y `Modifier.workoutPage()` — ancho máximo de 620 dp
centrado — aplicado a la cabecera, la lista, el editor, la pantalla de apuntar y su barra inferior.
En móvil no cambia nada; en Desktop deja de verse estirado. **Recordatorio: esto se ve también en
Desktop, no solo en Android.**

Verificado: `:composeApp:compileKotlinJvm`, `:compileKotlinJs`, `:compileKotlinWasmJs` y
`:server:compileKotlin` en verde, y `:composeApp:jvmTest` 13/13. **Sin probar en Android ni en vivo**
(este entorno no tiene SDK de Android y no se ha levantado el servidor): falta que el usuario lo
compile y lo use.

---

## 2026-11-01 — Ronda de pruebas en Android + giro a la precisión del cálculo nutricional

Sesión larga, en dos mitades: primero pruebas en dispositivo real, después un cambio de prioridad del
usuario hacia la exactitud de los cálculos de nutrición.

**Pruebas (Desktop y Android vía Tailscale)**

- Montado el acceso desde el móvil: Tailscale + depuración inalámbrica por ADB (`adb tcpip 5555` +
  `adb connect`), para poder probar sin cable. `AppConfig.kt` lo dejó el usuario con tres URLs
  comentadas (Render / Tailscale / local).
- Secciones 2 a 5 del plan de pruebas cubiertas. Saltadas a petición del usuario las pruebas que
  dependen de OpenAI (sin créditos en la cuenta) y la sección 6 (GPS), que aparcó deliberadamente.
- Arreglos salidos de las pruebas, **sin commitear**: coma decimal aceptada en peso/altura
  (`parseDecimal`, cierra INC-2 de la revisión técnica), aviso rojo de "objetivos no cargados" que
  parpadeaba en cada entrada a Nutrición (`goalsLoaded` → `goalsFailed`, semántica corregida), log a
  `WARN` de los rechazos de Brevo, y botón de refresco manual de los datos de la pulsera en Nutrición y
  Dashboard.

**Investigación de la pulsera: cerrada como limitación externa**

Medido en dispositivo (Xiaomi Smart Band 10 / Mi Fitness / Redmi Note 11 Pro 5G): pulsera 56 kcal,
Mi Fitness 54, Health Connect 37, GymSmart 37. La banda sincroniza bien con su app; el cuello de botella
es **Mi Fitness → Health Connect**, por lotes y con retraso, y el desfase va **siempre en el mismo
sentido** (por debajo). Descartados uno a uno: batería/autoinicio de MIUI (ya correctos), permisos de
Health Connect (correctos), ajustes de sincronización dentro de Mi Fitness (no existen). La FAQ de
Xiaomi reconoce el desfase. **No es arreglable desde el código.**

Efecto colateral valioso: esto **resuelve WEAR-5** del anexo A de `revision-tecnica-claude.md`, que
estaba marcado como "pendiente de medir en dispositivo" y del que dependía el resto del anexo.
**Mi Fitness sí escribe calorías activas** (activas y cadencia, nada de totales), así que el diseño
semántico de la app es correcto y no hay que leer `TotalCaloriesBurnedRecord` ni restarle el BMR.

**El fallo de producto que motivó el giro**

El usuario puso objetivo de *ganar* peso, siguió las kcal de la app y **perdió peso**. Diagnosticado:
en modo pulsera el presupuesto es `BMR + ajuste + calorías activas acumuladas hasta ese instante`, así
que solo está completo a medianoche — y la lectura en vivo va sistemáticamente por debajo. Quien come
mirando ese número se queda corto todos los días. Con cifras reales: objetivo mostrado ~2.200 contra una
necesidad real de ~2.689, es decir 400-500 kcal/día de déficit **cuando se pretendía superávit**.

**Auditoría del cálculo: 9 fallos**

Entre ellos, los tres graves: el déficit encubierto de arriba; etiquetas de kg/semana falsas en
`ProfileScreen` (−500 kcal/día prometía 1 kg/sem y da 0,45); y el propio concepto de ritmo en kg fijos,
que es inválido porque "1 kg/semana" es 1,4%/sem a 70 kg (inseguro) y 0,83% a 120 kg. Más: el suelo
calórico recorta el ritmo en silencio, Onboarding y Perfil ofrecen ritmos distintos, la fórmula está
duplicada en tres sitios, las calorías de hoy contaminan días pasados (= BUG-N7), los macros no cuadran
en casos extremos, y los mínimos de 1.500/1.200 kcal no tienen respaldo científico localizable.

**Documento nuevo: `10-fundamento-cientifico.md`**

A petición expresa del usuario ("quiero que te bases en la ciencia... que si eliges algo se cumpla"), se
investigó la evidencia **antes** de tocar código y se escribió la fuente de verdad de todos los números
de nutrición: cada constante con su fuente y su nivel de confianza (✅ sólido / 🟡 razonable / ⚠️
convención), más una sección explícita de lo que **no** está bien establecido.

Hallazgos que cambiaron el diseño previsto:
- **El ritmo debe ir en % del peso corporal** (Helms 2014: 0,5-1%/sem; Garthe 2011: a 0,7%/sem se ganó
  2,1% de masa magra, a 1,4%/sem nada).
- **La ganancia tiene techo** en 0,25-0,5% PC/sem (Iraki et al. 2019); más superávit solo añade grasa.
- **El 7700 kcal/kg no vale igual para ganar**: el músculo almacena ~1.000-1.800 kcal/kg y cuesta
  ~4.150-4.570 construirlo, frente a ~7.700 de la grasa.
- **La adaptación metabólica es impredecible individualmente** (rango −337 a +352 kcal/día, un tercio
  sin reducción) → solo se puede medir, no predecir.
- **Los multiplicadores de actividad son el eslabón más débil** (±350 kcal/día entre escalones
  contiguos), lo que hace el autoajuste **más** necesario sin pulsera que con ella.
- **El método Navy de % de grasa se descarta** (infraestima ~6 puntos vs DXA); se propone seguir
  cintura como medida cruda.
- Mifflin-St Jeor **confirmada** como la elección correcta sin composición corporal.

**Implementación: los 4 bloques, completos en esta misma sesión**

El usuario aprobó las dos decisiones de producto ("añádelas si va") y luego pidió los cuatro bloques
("hazlos todos"). Resultado, todo sin commitear:

- **Cintura**: campo opcional en el diálogo de peso + `CompositionCard`, que cruza la tendencia de peso
  con la de cintura para decir si lo ganado es músculo o grasa. Umbrales de 1 cm y 0,5 kg, por debajo
  de los cuales se considera estable, porque es el orden de magnitud del ruido de medición.
- **`shared/.../nutrition/NutritionMath.kt`**: la pieza que lo sostiene todo. Como servidor y cliente
  dependen ambos de `shared`, la fórmula es ahora **una sola implementación real**, no una copia
  sincronizada a mano. El Onboarding necesita calcular en local (la vista previa existe antes de que
  haya perfil), así que dejarla solo en el servidor no era opción.
- **Ritmo en % de peso corporal** con rangos validados en servidor, etiquetas derivadas del número,
  suelo en el BMR y aviso cuando ese suelo recorta el ritmo. **Recomposición** añadida como objetivo.
- **Modo pulsera**: media de 14 días vía `aggregateGroupByPeriod` + arrastre del día anterior. La
  lectura en curso pasa a informativa. Arreglado de paso BUG-N7.
- **Autoajuste**: `GET /profile/adaptive`, con regresión por mínimos cuadrados sobre los pesajes,
  puertas de entrada de datos mínimos, rechazo de valores absurdos y **límite de una vez por semana**
  (sin él la mezcla 0,3/0,7 se aplicaría en cada carga de pantalla y anularía el suavizado, que es
  precisamente lo que aporta).
- **18 pruebas** en `shared/src/commonTest` que verifican lo que la app le promete al usuario: que cada
  preset anuncia los kg/semana que de verdad produce, que el ritmo escala con el peso, que el recorte
  del suelo se reporta, que los macros cuadran en 16 combinaciones de peso y kcal, y que el autoajuste
  ni salta ni se traga datos imposibles.

**Cuatro migraciones automáticas de base de datos**, todas idempotentes y dentro de `runCatching`: la
columna de cintura, las dos del autoajuste, la **conversión de unidades de `goal_rate`** (kcal/día → %
semanal, discriminando por `ABS(goal_rate) > 10`) y el encaje de `goal_rate` en el rango seguro. Esta
última hizo falta porque un perfil convertido queda en un valor arbitrario (-400 kcal a 70 kg son
-0,36 %/sem) que no cae en ningún rango válido, y el usuario no habría podido guardar su perfil.

Sin verificar con build real: `HealthConnectProvider.getEnergySummary()`, que es `androidMain`.

---

## 2026-10-26 (continuación 5) — Fase 5: bloque 1 completo + parte del bloque 2

Usuario pidió commitear lo de Fase 4 y empezar la Fase 5 por el orden de ataque propuesto. Se atacó el
bloque 1 completo ("pérdida de datos y funciones muertas") más dos ítems del bloque 2 (fiabilidad del
conteo de kcal):

- **DB-1**: `TursoService.execute()` ahora comprueba el status HTTP y el campo `error` de Turso, y
  lanza `TursoException` en vez de devolver silenciosamente "sin datos". Se añadió el plugin
  `StatusPages` (`plugins/ErrorHandling.kt`, nuevo) — antes no existía ningún manejo global de errores
  en el servidor.
- **NUT-1**: el cliente ya comprueba el status HTTP antes de parsear la respuesta de `/food/barcode/`,
  así que un 404 ya no se interpreta como un producto válido vacío.
- **NUT-2**: `/nutrition/add` ahora devuelve `{"id": "..."}`; el cliente usa ese id real en vez de
  `""`, así que una comida recién añadida ya se puede editar/borrar sin recargar la pantalla.
- **AUTH-1**: `LoginScreen` ahora muestra el mismo paso de verificación (código + reenviar) que ya
  existía en `RegisterScreen` cuando el login responde `EMAIL_NOT_VERIFIED`. Antes era un callejón sin
  salida.
- **RED-1** (el más importante): `AuthResponse` tiene un campo nuevo `networkError`; `me()` lo marca en
  el catch. `hasProfile()` ahora devuelve `Boolean?` (`null` = no se pudo saber) en vez de `Boolean`, y
  en NavGraph/LoginScreen un `null` **nunca** se trata como "no tiene perfil" (eso es lo que mandaba a
  rehacer el onboarding y sobrescribía el perfil real). El arranque de la app (`NavGraph.kt`) ahora
  muestra una pantalla de carga con reintento en vez de una pantalla en blanco indefinida o de adivinar
  el destino — de paso arregla también RED-4.
- **NUT-3**: la pantalla de nutrición ahora muestra un aviso visible si no se pudieron cargar los
  objetivos reales del perfil, en vez de presentar los valores hardcodeados (2000 kcal, etc.) como si
  fueran los del usuario.
- **NUT-4**: la búsqueda de alimentos filtra productos sin datos nutricionales útiles (antes se podían
  añadir como comidas de 0 kcal), y distingue "sin resultados" de "falló la búsqueda".
- Todo verificado con `./gradlew :server:compileKotlin` y `:composeApp:compileKotlinJvm` (`BUILD
  SUCCESSFUL`) tras cada cambio. **No se ha podido verificar el target Android** por falta de Android
  SDK en este entorno — ninguno de los cambios de esta tanda toca código Android puro, así que el
  riesgo es bajo, pero queda pendiente de build real.
- `08-backlog-mejoras.md` actualizado: cada ítem resuelto está marcado con ✅ y referencia al detalle.
- **Pendiente del bloque 2**: WEAR-1/2/3 (recuento del wearable con `aggregate()`).
- **Bloques 3-5 sin empezar**: GPS (foreground service — cambios más invasivos, requieren permisos
  nuevos y un `Service` de Android no verificable en este entorno), cliente HTTP único, gate premium
  real, redimensionado de imagen para IA.
- Usuario confirmó commit de este bloque (commit `893e71c`). Aclaró que trabaja en IntelliJ ejecutando
  Gradle por terminal — mismo flujo que se usa aquí para verificar los cambios.

### 2026-10-26 (continuación 6) — Fase 5: cierra el bloque 2 (wearable)

- **WEAR-1/WEAR-2 arreglados**: `HealthConnectProvider` migrado de `readRecords` (sumaba registros
  crudos, sin deduplicar entre fuentes y sin paginar) a `aggregate()` con `StepsRecord.COUNT_TOTAL` y
  `ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL`, que resuelve ambos problemas a la vez (Health
  Connect ya deduplica y no pagina en una agregación). **Sin verificar con build real** — este entorno no
  tiene Android SDK instalado, así que no se pudo ejecutar `:composeApp:compileDebugKotlinAndroid`.
  Recomendado compilar/probar en un dispositivo o emulador Android.
- Queda **WEAR-3** (sincronización en segundo plano en vez de lectura puntual por pantalla) como mejora
  de arquitectura más grande, pendiente de decidir si se hace ahora.
- Con esto, los bloques 1 y 2 del orden de ataque de `08-backlog-mejoras.md` quedan completos.
- Usuario confirmó commit de este bloque (commit `3275443`).

### 2026-10-27 — Sistema de continuidad entre sesiones + bloque 3 (GPS) casi completo

- **Usuario pidió un sistema de continuidad**: al llenarse el contexto de una sesión, cambiará a otra
  sesión de Claude, y necesita que esa sesión nueva pueda seguir exactamente donde se dejó sin perder
  el ritmo. Se creó:
  - `CLAUDE.md` en la raíz del repo (Claude Code lo lee automáticamente al empezar) — instrucciones
    cortas que apuntan a `doc/CONTINUAR.md` como primera lectura obligatoria.
  - `doc/CONTINUAR.md` — documento de estado (no histórico): fase/bloque actual, lo último hecho, lo
    siguiente, pendientes de verificación, decisiones ya tomadas por el usuario. Se actualiza cada vez
    que se termina algo, no solo al cerrar sesión.
- **Bloque 3 (GPS) — GPS-2, GPS-3 y GPS-4 arreglados**:
  - GPS-4: `TursoService.executeBatch()` nuevo (varios statements en una sola llamada a
    `/v2/pipeline`); `GpsService.saveRoute` guarda la ruta + puntos en lotes de 200 en vez de N+1
    peticiones. Verificado con `:server:compileKotlin`.
  - GPS-2: en `GpsScreen.kt`, un hueco de señal >100m ya no deja el punto de referencia congelado
    (se actualiza sin sumar esa distancia al total), así que no descarta en cascada el resto de la
    grabación. Verificado con `:composeApp:compileKotlinJvm`.
  - GPS-3: `RouteRecoveryStore` nuevo (expect/actual — real en Android con `SharedPreferences`, no-op
    en jvm/ios/js/wasmJs). `GpsScreen.kt` persiste la ruta en curso cada 10 puntos y al parar; al abrir
    la pantalla, si hay una ruta sin guardar se ofrece recuperarla o descartarla. Verificado con
    `:composeApp:compileKotlinJvm`, `compileKotlinJs` y `compileKotlinWasmJs` (los tres
    `BUILD SUCCESSFUL`). El `actual` de Android no se pudo compilar aquí (sin SDK).
  - **Descubrimiento colateral**: `:composeApp:compileKotlinIosSimulatorArm64` sí funciona en este
    entorno sin Xcode. Al probarlo (para verificar el `actual` de iOS de `RouteRecoveryStore`) salió
    exactamente el error ya documentado del wiring de `App()` — confirma el diagnóstico de
    `01-arquitectura.md §5` y abre la puerta a poder verificar más trabajo de iOS en el futuro de lo
    que se pensaba.
- **Queda GPS-1** (foreground service) como único pendiente del bloque 3 — es el cambio más invasivo
  (Service de Android, permisos nuevos), no verificable aquí, y se decidió dejarlo para acordar el
  enfoque exacto con el usuario antes de escribirlo.
- **Nada de esta tanda se ha commiteado todavía.**
- Usuario, al leer el resumen, preguntó cómo funciona exactamente el sistema de continuidad y qué
  contiene `CONTINUAR.md` — se le explicó (lectura automática vía `CLAUDE.md`, contenido = estado
  rápido con enlaces al historial completo, no duplica `07-registro-sesiones.md`) y pidió seguir con
  GPS-1.
- **GPS-1 implementado sin respuesta a las preguntas de diseño planteadas** (notificación con/sin botón
  de parar, stats en vivo o no): se tomó la decisión más simple y estándar para no bloquear el trabajo:
  - `GpsTrackingService.kt` (nuevo): foreground service tipo `location`, notificación fija "Grabando
    ruta GPS", sin botón de acción, sin stats en vivo. Su único trabajo es mantener el proceso con
    prioridad elevada; la recolección de ubicación sigue en `LocationService`/`GpsScreen` como antes.
  - `BackgroundTrackingController.kt` (nuevo, común + 5 `actual`): arranca/para el servicio desde
    `GpsScreen.kt` (código común). Patrón igual al de `rememberImagePicker` (usa `LocalContext.current`
    dentro del `actual` de Android).
  - Manifest: permisos `FOREGROUND_SERVICE_LOCATION` y `POST_NOTIFICATIONS` (esta última sin flujo de
    petición en runtime todavía), declaración de `<service>`. Dependencia nueva
    `androidx.core:core-ktx`.
  - `GpsScreen.kt`: arranca el servicio al empezar a grabar, lo para al parar, y con un
    `DisposableEffect` como red de seguridad si la pantalla se destruye mientras grababa.
  - Verificado con `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs` (los tres
    `BUILD SUCCESSFUL`). Se intentó también `:composeApp:compileKotlinIosSimulatorArm64` — compila el
    `actual` de iOS sin problema, y falla solo por el bug ya conocido de `MainViewController.kt` (sin
    relación con este cambio). **Descubrimiento**: ese target de iOS compila en este entorno sin
    Xcode, lo que da más margen de verificación del esperado para trabajo futuro de iOS.
  - **El `Service`/`actual` de Android no se ha podido compilar** (sin SDK) — es el cambio que más
    necesita probarse en un dispositivo/emulador real de todo lo hecho hasta ahora.
- Con esto, **el bloque 3 (GPS) queda completo** (GPS-1, GPS-2, GPS-3, GPS-4).
- `08-backlog-mejoras.md`, `06-funcionalidades.md`, `00-roadmap.md` y `CONTINUAR.md` actualizados con
  el cierre del bloque 3 y las decisiones de diseño tomadas sin confirmar.
- **Nada de esta tanda se ha commiteado todavía.**
- **Siguiente sesión**: confirmar commit de todo el bloque 3; decidir entre bloque 4 (cliente HTTP
  único, timeouts, 401), bloque 5 (gate premium, redimensionar imagen IA, caché OFF) o WEAR-3 — ninguno
  requiere una decisión de producto tan grande como GPS-1, se puede avanzar sin bloquear.

---

## 2026-10-26 (continuación 4) — Fase 4 completa: investigación exhaustiva

Sesión de investigación profunda a petición del usuario ("investiga bien todo", con el objetivo explícito
de encontrar fallos y mejoras). Se repartió en cuatro frentes en paralelo: GPS + wearable + fechas
(directo), análisis IA + datos de alimentos, resiliencia/offline/cold start, y auditoría de pantallas.

**Resultado: ~60 hallazgos, inventariados y priorizados en [08-backlog-mejoras.md](08-backlog-mejoras.md)**
(documento nuevo, que pasa a ser la cola de trabajo de la Fase 5).

- **Regresión propia detectada y arreglada.** El fix de seguridad de Fase 2 (exigir sesión en
  `/ai/body-analysis`) **rompió el análisis corporal**: `WeightScreen` lo llamaba con uno de sus tres
  `HttpClient`, justo el que no tenía el plugin `HttpCookies`. Antes funcionaba solo porque el endpoint
  era público, así que el fix destapó un bug latente, y el `catch` con solo `printStackTrace()` lo hacía
  fallar en silencio. Lo señalaron **las tres investigaciones de forma independiente**. Arreglado: usa el
  cliente con cookies, se eliminó el cliente duplicado, y los fallos ahora muestran un mensaje visible.
  Lección añadida como regla 10 en [02-convenciones.md](02-convenciones.md).
- También arreglado en el acto: crash al abrir la pantalla de Peso sin conexión (`getHistory()` sin
  `try/catch` dentro de un `LaunchedEffect` sin `catch`), y el estado premium que no se cargaba nunca si
  el usuario no tenía historial de peso. Verificado con `:composeApp:compileKotlinJvm` → `BUILD
  SUCCESSFUL`.
- **Hallazgos críticos abiertos más relevantes**: el GPS se para al bloquear el móvil (no hay foreground
  service); un hueco de señal >100 m mata la grabación en cascada; la ruta en curso solo vive en memoria;
  un fallo de red puede llevar a rehacer el onboarding y **sobrescribir el perfil real** (único camino de
  pérdida de datos encontrado); un código de barras no encontrado se puede guardar como comida de 0 kcal;
  las comidas recién añadidas no se pueden editar ni borrar; una cuenta sin verificar queda bloqueada sin
  salida; la pantalla de nutrición muestra **2000 kcal hardcodeadas** como si fueran los objetivos reales
  cuando falla la carga del perfil; y los errores de Turso se reportan como éxito.
- **Respuesta a la duda del usuario sobre la recolecta del wearable**: sí se puede hacer mejor, y el
  arreglo principal cabe en un archivo. El código suma registros crudos (`readRecords`), lo que puede
  **contar de más** (varias apps escribiendo los mismos pasos, sin la deduplicación que sí hace
  `aggregate()`) y **contar de menos** a la vez (ignora el `pageToken`, truncando en la primera página de
  1000 registros).
- Se actualizaron `03-seguridad.md` (4 hallazgos nuevos: gate premium inexistente en servidor, errores de
  BBDD silenciosos, `usesCleartextTraffic`, conflicto de `allowBackup` con el cifrado nuevo),
  `06-funcionalidades.md` (veredictos reales de GPS, wearable, IA y training) y `00-roadmap.md` (Fase 4
  cerrada).
- **Nada de esta tanda está commiteado todavía.**
- **Siguiente paso**: empezar Fase 5 por el orden de ataque propuesto al final del backlog (pérdida de
  datos y funciones muertas primero, luego fiabilidad del conteo de kcal, luego GPS).

---

## 2026-10-26 — Fase 0 y arranque de Fase 1

- Se define el roadmap de 6 fases en [00-roadmap.md](00-roadmap.md) (documentación → seguridad →
  limpieza → validación de fiabilidad → mejora de funcionalidades → nuevas funcionalidades).
- Se crea la carpeta `doc/` con el índice y la checklist de producción
  ([04-produccion-playstore.md](04-produccion-playstore.md)).
- Exploración inicial de la estructura del repo: módulos `composeApp`, `shared`, `server` (Ktor),
  `iosApp`, y dos carpetas sospechosas de ser legacy: `backend/` (solo `.env` + `alembic/versions` vacío +
  `venv/`, sin `.py` propio visible) y `frontend/` (solo `node_modules`, sin proyecto aparente).
- Lanzadas 3 exploraciones en paralelo para construir la primera versión de `01-arquitectura.md` y
  `05-deuda-tecnica.md`: cliente (`composeApp`), servidor (`server`), y módulos legacy + estado real de
  iOS.
- Las 3 exploraciones terminaron y se consolidaron en documentación definitiva:
  `01-arquitectura.md`, `02-convenciones.md`, `03-seguridad.md`, `05-deuda-tecnica.md`,
  `06-funcionalidades.md` (primera versión).
- **Hallazgos clave de esta sesión:**
  - Causa raíz exacta de que iOS no compile: `LocationProvider`/`HealthDataProvider` no son
    `expect/actual`, no tienen implementación iOS, y `App()` los exige como parámetros obligatorios sin
    valor por defecto; `MainViewController.kt` llama a `App()` sin argumentos. Lo mismo afecta a Web
    (JS/Wasm).
  - `backend/` (Python/Alembic) y `frontend/` (solo `node_modules`) confirmados como código muerto.
  - Seguridad: 2 hallazgos críticos — `/ai/body-analysis` sin autenticación (riesgo de agotar créditos
    de OpenAI) y IDOR en `deleteMeal`/`updateMeal` de nutrición (no filtran por `user_id`, a diferencia
    de GPS que sí lo hace bien). Detalle completo con severidades en `03-seguridad.md`.
  - Pista sobre por qué los objetivos de peso "no van muy allá": en modo `activityLevel == "wearable"`,
    el servidor usa el BMR puro como TDEE sin sumar el gasto real de la pulsera.
  - Health Connect usa consulta puntual por pantalla (`LaunchedEffect`), no sincronización en segundo
    plano — mejora concreta propuesta para Fase 5.
  - Stripe no es una pasarela real (API vieja sin 3DS, ignora tarjetas reales salvo tokens de test) —
    confirmado que no cobra, documentado el camino para activarlo con seguridad en el futuro.
  - `TrainingScreen`/`TrainingService` son un placeholder sin terminar, sin endpoint real en el servidor.
- **Decisiones del usuario tras revisar lo anterior**: aplicar ya los 2 fixes críticos; borrar
  `backend/`/`frontend/` si tras verificar son código muerto; dejar `TrainingScreen` como está por ahora.
- **Verificación independiente** (no solo confiar en el informe del fork) de `backend/`/`frontend/`:
  confirmado que no están rastreados por git (`git ls-files` → 0 archivos) y que no hay ninguna
  referencia a ellos en `settings.gradle.kts`, ningún `build.gradle.kts` ni el `Dockerfile`.
- **Aplicado**:
  - `BodyAnalysisRoute.kt`: ahora exige `call.sessions.get<UserSession>()` antes de llamar a
    `OpenAIService`, igual que el resto de rutas protegidas.
  - `NutritionService.deleteMeal`/`updateMeal` y las rutas correspondientes en `NutritionRoutes.kt`:
    ahora reciben y filtran por `userId` (`WHERE id = ? AND user_id = ?`), cerrando el IDOR.
  - Verificado con `./gradlew :server:compileKotlin` → `BUILD SUCCESSFUL`.
  - `backend/` eliminado por completo. `frontend/node_modules/` eliminado; la carpeta `frontend/` vacía
    quedó bloqueada por Windows ("en uso por otro proceso") — pendiente de borrar manualmente.
- **Pendiente importante**: todos los fixes de seguridad están en el working tree local, **no
  desplegados en Render** — hace falta commit + push/deploy para que surtan efecto real.

### 2026-10-26 (continuación) — resto de Fase 2

- Preguntó "¿y la fase 2?" → se continuó cerrando hallazgos de `03-seguridad.md` en la misma sesión:
  - **#7 esquema desincronizado**: añadidas `is_public` (gps_routes) y `has_wearable` (user_profiles)
    a los `CREATE TABLE IF NOT EXISTS`. No afecta a la BBDD real de Turso (ya las tenía a mano), solo a
    entornos nuevos.
  - **#8 JSON sin escapar en EmailService**: reescrito `sendEmail` con `buildJsonObject` en vez de
    interpolación de strings.
  - **#4 logging con `println` en el servidor**: migrados los 27 `println`/`printStackTrace` de 8
    archivos del servidor a SLF4J/Logback, quitando del log el objeto de sesión completo, el email, el
    token de verificación (antes en claro) y la respuesta cruda de Stripe. `logback.xml` bajado de
    `root level="trace"` a `"info"`.
  - Verificado todo con `./gradlew :server:compileKotlin` → `BUILD SUCCESSFUL` tras cada tanda de
    cambios.
- **Quedan abiertos de Fase 2** (documentados en `03-seguridad.md`): #3 cifrado de cookie de sesión
  (trabajo por plataforma, no se ha hecho), #9 logging del cliente (requiere elegir librería
  multiplataforma, se deja para Fase 3), #5 CORS (necesita que el usuario confirme si hay dominio de
  producción para el cliente web), #6 Stripe/PCI (diferido a propósito, no es funcional).
- Usuario confirmó: el cliente web de GymSmart no está publicado en ningún dominio real (solo se
  prueba en local) → **#5 CORS se deja tal cual**, documentado para cuando exista un dominio real.
- Usuario pidió cifrar la cookie de sesión al menos en Android → **#3 arreglado**:
  `AndroidCookiesStorage` migrado a `EncryptedSharedPreferences` (clave en Android Keystore, AES256-GCM),
  con fallback a `SharedPreferences` normal si el Keystore fallara. Se limpia el residuo en claro del
  almacenamiento antiguo al migrar. Añadida dependencia `androidx.security:security-crypto:1.1.0-alpha06`.
  **Importante**: esta sesión no tiene Android SDK instalado, así que este cambio concreto no se pudo
  compilar/verificar (`compileKotlinJvm` sí pasó, pero no toca este archivo) — recomendado probarlo en
  un dispositivo/emulador Android antes de confiar en él al 100%.
- Usuario pidió hacer commit ahora (sin push) de todo el trabajo de seguridad de esta sesión.
- **Estado de Fase 2 al cierre de esta sesión**: 7 de 10 hallazgos resueltos. Quedan #9 (logging del
  cliente, movido a Fase 3 porque requiere elegir librería de logging multiplataforma) y #6
  (Stripe/PCI, diferido a propósito mientras no sea funcional).
### 2026-10-26 (continuación 2) — Fase 3: limpieza de código muerto

- Usuario dijo "sigue" → se continuó con la limpieza listada en `05-deuda-tecnica.md`:
  - Verificado (grep, sin referencias externas) y eliminado: `AndroidGpsTracker.kt`,
    `PermissionHelper.kt`.
  - Eliminado `WeightService.addWeight` (método muerto y roto, nadie lo llamaba).
  - Movidos `Product`/`Nutriments`/`FoodSearchResponse` de `NutritionScreen.kt` a
    `model/NutritionModels.kt`; actualizado el import en `NutritionService.kt`.
  - Unificada la duplicación de `GpsService.getRoutesByUser`/`getCommunityRoutes` en un método privado
    `queryRoutes(whereClause, params)`.
  - Limpiados los comentarios con IPs y nombres de personas (`DANI`, `JONY`) en `AppConfig.kt`.
  - Verificado con `./gradlew :composeApp:compileKotlinJvm` y `:server:compileKotlin` tras cada tanda →
    `BUILD SUCCESSFUL`. No se tocó Android directamente en esta tanda (ya se había avisado que no hay
    SDK Android en este entorno).
  - **No se tocó** `TrainingScreen`/`TrainingService` (decisión del usuario de dejarlo como está) ni la
    estrategia de múltiples `HttpClient`/DI (requiere decidir enfoque antes de tocarlo).
- **Nada de esta tanda se ha commiteado todavía** — queda en el working tree, pendiente de que el
  usuario confirme (como hizo con la tanda de seguridad).
- Usuario confirmó commit de la limpieza de Fase 3 (commit `4a81cba`).

### 2026-10-26 (continuación 3) — Fase 4: validación de nutrición

- Usuario dijo "sigue" de nuevo. Los dos puntos restantes de Fase 3 (logging del cliente, estrategia de
  DI) requieren decisiones de arquitectura que no se tomaron por iniciativa propia; se dejaron
  documentados como pendientes y se pasó a Fase 4, empezando por nutrición (el dolor que más mencionó
  el usuario).
- **Investigación a fondo del modo "wearable"**: se leyó `ProfileService.computeTargets` línea a línea,
  `NutritionScreen.kt` (cómo se usa `activeCalories`) y `HealthConnectProvider.kt` (semántica exacta de
  `ActiveCaloriesBurnedRecord`, que por definición de Health Connect excluye el metabolismo basal).
  **Conclusión: la hipótesis de la Fase 1 era incorrecta.** El diseño BMR (servidor) + kcal activas
  reales (cliente, sumadas aparte) es un patrón válido y deliberado (igual que Fitbit/Garmin/MFP), no un
  bug. El problema real de precisión es que la lectura de kcal activas es puntual por pantalla, no
  continua — ya estaba identificado como mejora de Fase 5, ahora confirmado como la explicación más
  probable de la imprecisión percibida. **Se corrigió `06-funcionalidades.md`** para reflejar esta
  conclusión más sólida en vez de la hipótesis preliminar.
- **Bug real encontrado y arreglado**: al revisar el cálculo de kcal por comida, 4 sitios en
  `NutritionScreen.kt`/`NutritionService.kt` usaban el campo ambiguo `energy-kcal` de Open Food Facts
  como si fuera por 100g cuando faltaba el campo correcto `energy-kcal_100g` (uno de los 4 ni siquiera
  tenía ese fallback, mostraba 0 directamente). Se añadió `Nutriments?.estimatedKcalPer100()` en
  `model/NutritionModels.kt` que estima kcal/100g a partir de los macros (regla 4/4/9) cuando falta el
  campo correcto, en vez de asumir que un campo ambiguo es por 100g. Aplicado en los 4 sitios.
  Verificado con `:composeApp:compileKotlinJvm` → `BUILD SUCCESSFUL`.
- **No se tocó** la fórmula de TDEE del servidor (confirmada correcta) ni el patrón de lectura de Health
  Connect (la mejora de fondo pertenece a Fase 5, es un cambio de arquitectura — background sync — no
  una corrección puntual).
- **Nada de esta tanda se ha commiteado todavía.**
- **Siguiente sesión**: confirmar commit de los cambios de nutrición; decidir si Fase 4 continúa con
  GPS/wearable (medir precisión real, considerar la mejora de sincronización en segundo plano) o se
  pasa directamente a Fase 5; decidir cuándo desplegar todo lo acumulado a Render; probar en un
  dispositivo real el cambio de cookie cifrada de Android.

### 2026-10-26 (continuación 4) — Fase 4 completa: investigación exhaustiva de GPS, wearable, IA, OFF y resiliencia

- Usuario pidió investigar a fondo ("dale a tope, investiga bien todo... encontrar mejoras o fallos").
  Se auditó en profundidad: captura y persistencia de rutas GPS (`GpsScreen.kt`, `GpsService.kt`,
  `LocationService`), integración con Health Connect (`HealthConnectProvider.kt`), el flujo completo del
  análisis corporal por IA (cliente → `BodyAnalysisRoute.kt` → `OpenAIService.kt`), el cliente de Open
  Food Facts (`FoodService.kt`), el manejo de errores/resiliencia transversal de todas las pantallas, y
  una pasada por el resto de pantallas no cubiertas todavía (`ProfileScreen`, `WeightScreen`,
  `SubscriptionScreen`, `MainActivity.kt`).
- Resultado: **`doc/08-backlog-mejoras.md`** (nuevo), con ~60 hallazgos priorizados (🔴/🟠/🟡) y un
  "orden de ataque" para la Fase 5. Tres regresiones/bugs reales arreglados en el acto porque los
  encontraron investigaciones independientes señalando el mismo síntoma: R1 (análisis de IA roto por el
  fix de sesión de Fase 2 — `HttpClient` sin cookies en `WeightScreen`), R2 (crash de `WeightScreen` sin
  conexión), R3 (estado premium no se cargaba sin historial de peso). Lección registrada en
  `02-convenciones.md` (verificar qué cliente HTTP usa una llamada antes de exigirle sesión).
- `doc/06-funcionalidades.md` ampliado con el veredicto de GPS, wearable, IA y training.
- Esto cerró la Fase 4 por completo. Usuario pidió commitear y seguir con la Fase 5 si no quedaba nada
  más de la 4.

### 2026-10-26/27 — Fase 5, bloques 1 y 2 (pérdida de datos y fiabilidad de kcal)

- **Bloque 1** (pérdida de datos y funciones muertas): RED-1 (fallo de red podía sobrescribir el perfil
  real vía Onboarding — se añadió `networkError` a `AuthResponse`, `hasProfile()` pasó a `Boolean?`,
  pantalla de arranque con reintento en vez de adivinar destino), NUT-1 (barcode 404 se guardaba como
  comida de 0 kcal), NUT-2 (comida nueva no se podía editar/borrar — el servidor no devolvía el id
  generado), AUTH-1 (cuenta sin verificar era un callejón sin salida — `LoginScreen` no ofrecía
  verificar/reenviar código), DB-1 (errores de Turso se convertían en silencio en "éxito" — se añadió
  `TursoException` y el plugin `StatusPages` que no existía).
- **Bloque 2** (fiabilidad del conteo de kcal): NUT-3 (objetivos inventados mostrados como reales si
  fallaba la carga del perfil), NUT-4 (productos sin datos nutricionales se podían añadir como 0 kcal),
  WEAR-1/WEAR-2 (el wearable podía contar de más —fuentes sin deduplicar— y de menos a la vez —
  `readRecords` sin paginar, truncaba en los primeros 1000 registros—; migrado a `aggregate()`).
- Cada tanda verificada con `:server:compileKotlin`/`:composeApp:compileKotlinJvm` y commiteada tras
  confirmación del usuario.

### 2026-10-27 — Fase 5 bloque 3 (GPS) completo + sistema de continuidad entre sesiones

- El usuario pidió explícitamente un sistema para que una sesión nueva de Claude Code pueda retomar el
  trabajo sin perder el ritmo cuando el contexto se llene. Se creó **`CLAUDE.md`** (raíz, se autocarga al
  iniciar Claude Code) y **`doc/CONTINUAR.md`** (foto rápida del estado, no un historial — se actualiza
  cada vez que se termina algo, no solo al final de la sesión).
- Cerrado el bloque 3 (GPS) entero: GPS-4 (guardado de rutas por lotes en Turso vía
  `TursoService.executeBatch()`, de N+1 peticiones a ~5), GPS-2 (un hueco de señal >100m ya no mata la
  grabación en cascada — se actualiza el punto de referencia aunque no se sume la distancia), GPS-3 (la
  ruta en curso se guarda periódicamente en `RouteRecoveryStore`, nuevo, `expect/actual`, real en Android
  vía `SharedPreferences` — se ofrece recuperarla si Android mata el proceso), y **GPS-1** (el cambio más
  invasivo de todo el backlog: `GpsTrackingService` nuevo, foreground service tipo `location` con
  notificación mínima, para que el tracking no se detenga al bloquear el móvil, controlado desde
  `GpsScreen.kt` vía `BackgroundTrackingController` nuevo).
- Para GPS-1 se preguntó al usuario por el diseño exacto de la notificación (botón de parar, stats en
  vivo) y no hubo respuesta a tiempo — se tomó la decisión más simple (notificación fija, sin botón) para
  no bloquear el trabajo, documentando explícitamente que quedaba sin confirmar.
- Verificado con `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs` (los tres
  `BUILD SUCCESSFUL`); se descubrió que `:composeApp:compileKotlinIosSimulatorArm64` **sí compila en
  este entorno sin Xcode** (solo falla por el bug ya documentado de `MainViewController.kt`) — útil para
  la futura Fase 6 (iOS). El `Service` de Android no se pudo compilar aquí (sin SDK).
- Usuario confirmó commit de todo el bloque 3 + el sistema de continuidad (commit `ff98ae7`).
- A continuación, el usuario confirmó: la mejora de la notificación de GPS-1 (botón de parar, stats en
  vivo, pedir `POST_NOTIFICATIONS` en runtime) **queda como mejora futura** (anotada como GPS-1b), no se
  hace ahora. También confirmó que no probará nada en dispositivo real hasta terminar toda la Fase 5 —
  lo probará todo de una vez al final; mientras tanto, mantener la lista de verificación pendiente
  completa y al día en `CONTINUAR.md`.

### 2026-10-27 (continuación) — Fase 5 bloque 4 (resiliencia transversal) completo

- **HTTP-1 (cliente HTTP único)**: de las 5 instancias de `AuthService()`/`HttpClient` repartidas por
  pantalla (`NavGraph`, `WeightScreen`, `DashboardScreen`, `ProfileScreen`, `SubscriptionScreen` — causa
  raíz directa de la regresión R1 de Fase 4), se pasó a una única instancia creada en `NavGraph` y
  pasada como parámetro a las 4 pantallas que creaban la suya. `ProfileScreen` reutiliza además el
  `profileService` ya compartido en vez de crear uno nuevo.
- **RED-2 (timeouts + motor de red determinista)**: se quitó `ktor-client-android` del classpath de
  Android (coexistía con `ktor-client-okhttp`, hacía que el motor elegido no fuera determinista). Se
  instaló el plugin `HttpTimeout` en el cliente único (`requestTimeoutMillis = 45_000`,
  `connectTimeoutMillis = 15_000`, `socketTimeoutMillis = 45_000`, generosos por los cold starts de
  Render gratuito).
- **RED-3 (401 centralizado)**: `AuthService` instala un `HttpResponseValidator` que detecta cualquier
  401 fuera de las rutas `/auth` (que ya gestionan su propio 401 como parte del flujo normal) y expone
  `sessionExpired: StateFlow<Boolean>`. `NavGraph` lo observa; si se activa, limpia la pila de
  navegación y manda a `LoginScreen` con un aviso visible. Queda pendiente, a propósito, la parte de
  RED-3 sobre metadatos de cookie (expiración/dominio/path/secure) — cambio más grande, menor impacto
  ahora que el 401 ya es centralizado.
- Al implementarlo apareció un bug de sintaxis propio (un comentario KDoc con el literal `/auth/*` abría
  un comentario de bloque anidado sin querer) y dos errores de API de Ktor (`HttpResponseValidator` es
  una función de extensión sobre la config del cliente, no algo que se instale con `install(...)`; y
  `HttpResponse` no tiene `.request` directo, es `.call.request`) — los tres corregidos y vueltos a
  compilar antes de seguir.
- Verificado con `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs`,
  `:server:compileKotlin` (todos `BUILD SUCCESSFUL`) y de nuevo `compileKotlinIosSimulatorArm64` (mismo
  fallo ya documentado, no relacionado). **Sin verificar con build real de Android** — el cambio de
  motor de red es justo lo que más conviene probar antes de confiar en los tiempos reales.
- Con esto se cierra el bloque 4 de la Fase 5. Siguiente: bloque 5 (SEC-1 gate premium, IA-1 redimensionar
  imagen, OFF-1 caché) o WEAR-3 (sync del wearable en segundo plano) — ninguno requiere una decisión de
  producto grande como GPS-1.
- Usuario respondió "con el que te venga mejor" — se eligió el bloque 5 por ser todo de servidor
  (verificable al 100% en este entorno, sin la limitación de Android SDK que sí afecta a WEAR-3).

### 2026-10-27 (continuación 2) — Fase 5 bloque 5 (coste y seguridad) completo — Fase 5 cierra todos sus bloques planeados

- **SEC-1 (gate premium real)**: `ProfileService.isPremium()` existía pero no se llamaba desde ningún
  sitio — cualquier usuario logueado podía gastar créditos de OpenAI sin límite, el único control era de
  cliente (saltable con una petición directa). Ahora `bodyAnalysisRoute(profileService)` comprueba
  `isPremium(session.userId)` y responde `403 Forbidden` si no lo es, antes de llamar a OpenAI.
  `profileService` ya se construía en `Application.kt` y se pasaba a `configureRouting` — solo hubo que
  enchufarlo a esta ruta.
- **IA-1 (redimensionar imagen antes de mandarla a OpenAI)**: se añadió `OpenAIService.resizeIfNeeded()`,
  que decodifica la imagen con `javax.imageio` (parte del JDK, sin dependencias nuevas; el `Dockerfile`
  usa `eclipse-temurin:21-jre`, que incluye `java.desktop` — funciona headless sin problema para
  codificar/decodificar, no necesita display) y la reescala a máximo 1024px de lado largo con
  `Image.SCALE_SMOOTH` si lo excede (gpt-4o reescala igualmente a ≤768px internamente, así que la
  resolución extra se descartaba sin ganancia). Si la decodificación falla, se manda la imagen original
  en vez de romper el análisis por un paso que es puramente de optimización. **Precisión importante
  sobre el alcance**: esto reduce el payload hacia OpenAI (y el heap de esa llamada en el servidor), pero
  no el payload cliente→servidor — el cliente sigue subiendo la imagen a resolución completa al
  endpoint; arreglar eso tocaría el `ImagePicker` de cada plataforma (con el añadido de que solo Android
  lo tiene implementado hoy), fuera de alcance de este cambio puntual.
- **OFF-1 (caché de Open Food Facts)**: se añadió una clase `TtlCache` interna a `FoodService.kt` (sin
  dependencias nuevas, `ConcurrentHashMap` + comprobación de expiración en el `get`) y se cachean
  `searchFood()` y `lookupBarcode()` con TTL de 24h y tope de 500 entradas. Decisión deliberada: un
  resultado vacío o un fallo de red **nunca se cachea**, porque podría ser un fallo transitorio de OFF,
  no "este alimento no existe" — cachear eso congelaría un falso negativo durante todo el TTL. No es LRU
  real (se limpian caducadas al llenarse, y si no basta se vacía entera) — decisión consciente de no
  complicarlo a esta escala (app de uso personal).
- Los tres cambios son de servidor puro — compilaron a la primera, sin necesitar iterar como en el
  bloque 4. Verificado con `:server:compileKotlin` y, para confirmar que no se rompió nada del cliente,
  `:composeApp:compileKotlinJvm` (ambos `BUILD SUCCESSFUL`).
- Con esto **se cierran los 5 bloques planeados de la Fase 5**. Lo que queda ya no son bloques
  estructurados, sino backlog residual por severidad: WEAR-3 (sync del wearable en segundo plano,
  arquitectura mayor, mismo problema de verificación en Android que GPS-1) y el resto de hallazgos
  🟠/🟡 de `08-backlog-mejoras.md` (GPS-5/6, IA-2/3, VAL-1/2, PERM-1, etc.).
- Usuario respondió "wear" — se siguió con WEAR-3.

### 2026-10-27 (continuación 3) — WEAR-3 arreglado con un enfoque más simple que el planteado originalmente

- El hallazgo original (de la investigación de Fase 4) proponía mover la lectura de Health Connect a un
  `WorkManager` periódico con caché local, por ser "un cambio de arquitectura, no un fix puntual". Antes
  de implementarlo tal cual, se reconsideró: ese enfoque es Android-only, añade una dependencia nueva
  (`androidx.work`), un `Worker` nuevo, y tiene el mismo problema de no poder verificarse en este entorno
  que tuvo GPS-1 — acumulando riesgo no verificado justo encima de otro cambio (GPS-1) que ya tiene esa
  misma etiqueta pendiente.
- Releyendo el síntoma real descrito ("si abres la app por la mañana, el presupuesto se queda congelado
  todo el día"): el problema no es que falte sincronización con la app cerrada, es que el número se queda
  obsoleto **mientras la pantalla sigue abierta**, porque la lectura es de una sola vez
  (`LaunchedEffect(Unit)` / `LaunchedEffect(selectedDate)`). Un refresco periódico dentro de la propia
  pantalla resuelve exactamente ese síntoma, con un cambio que vive entero en `commonMain`.
- Implementado: nueva constante `WEARABLE_POLL_INTERVAL_MS` (5 minutos) en `HealthDataProvider.kt`.
  `NutritionScreen.kt`: se separó la lectura de calorías activas del `LaunchedEffect(selectedDate)` que
  carga perfil/comidas (no tenía sentido re-pedir comidas al servidor cada 5 minutos) a su propio
  `LaunchedEffect(hasWearable)` con un bucle `while(true) { leer; delay(...) }`, que arranca en cuanto el
  perfil confirma que el usuario tiene pulsera. `DashboardScreen.kt`: se convirtió su lectura única de
  pasos/calorías en el mismo patrón de bucle, manteniendo el `delay(500)` inicial ya existente (no se
  tocó, es un hallazgo distinto y menor). El bucle se cancela solo al salir de la pantalla — ciclo de
  vida estándar de `LaunchedEffect`, sin código nuevo de gestión de lifecycle.
- `ProfileScreen.kt` también lee pasos/calorías, pero ahí es un dato informativo al editar el perfil, no
  un presupuesto que se mira todo el día — se dejó deliberadamente como lectura única.
- **Diferencia clave con el resto de cambios "wearable" de esta fase (WEAR-1/WEAR-2, que sí son Android
  puro)**: este no toca ni una línea de código Android. Se verificó al 100%, sin ninguna salvedad, con
  `:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs` y `:server:compileKotlin`
  (los cuatro `BUILD SUCCESSFUL`).
- Documentado explícitamente en `08-backlog-mejoras.md` que este es un cambio de alcance deliberadamente
  menor que el `WorkManager` originalmente esbozado, y qué caso sigue sin cubrir (app completamente
  cerrada o en segundo plano sin esa pantalla abierta) — para que quede claro que fue una decisión
  consciente, no un "me olvidé de la parte difícil".
- Con esto, todos los bloques planeados de la Fase 5 **y** WEAR-3 quedan cerrados. Lo que queda es
  backlog residual por severidad (GPS-5/6, IA-2/3, VAL-1/2, PERM-1, y el resto de 🟡).
- El usuario preguntó qué significaba exactamente ese "backlog residual" — se le detalló cada hallazgo
  en lenguaje llano. Luego preguntó qué fases quedan en general — se repasó el roadmap completo
  (Fase 2 con 2 cosas diferidas a propósito más el despliegue a Render pendiente, Fase 3 con logging del
  cliente y estrategia de DI pendientes, Fase 5 con el backlog residual, Fase 6 bloqueada hasta cerrar
  1-5). El usuario preguntó qué hacer antes de entrar en Fase 6, dado que para ese punto él habrá tenido
  que probar la app de verdad. **Se acordó explícitamente**: cerrar el backlog pequeño que no toca
  Android (VAL-1/2, IA-2/3 — rápidos, no añaden más superficie sin probar) y después parar de tocar
  código, para que el usuario haga una pasada de prueba real cubriendo todo lo acumulado sin verificar
  (GPS-1, cambio de motor de red, Health Connect, cookie cifrada, 401 centralizado...) antes de cerrar
  los cabos de Fase 2/3 o entrar en Fase 6. El usuario confirmó ese plan ("vale, me parece bien").

### 2026-10-27 (continuación 4) — Backlog residual: VAL-1, VAL-2, IA-2, IA-3

- **VAL-1**: se extrajo la validación de peso/altura/edad/ritmo de `OnboardingScreen` (que ya la tenía
  bien hecha) a `validation/ProfileValidation.kt` (nuevo, `commonMain`): `validateWeightKg`,
  `validateHeightCm`, `validateAge`, `validateGoalRate`. `OnboardingScreen.validate()` se reescribió para
  usarlas en vez de duplicar la lógica. `ProfileScreen`: el botón de guardar datos físicos antes solo
  validaba el ritmo (`rateError: Boolean`); se generalizó a `profileError: String?` que cubre los 4
  campos con un mensaje concreto. `WeightScreen`: el diálogo de añadir peso no validaba nada
  (`toFloatOrNull() ?: return@Button`, sin explicación si fallaba) ni fijaba `keyboardType` — ahora
  valida con la misma función, muestra el error con `isError`/`supportingText` en el propio campo, y
  pone `KeyboardType.Decimal`.
- **De rebote (no pedido explícitamente, pero imposible de ignorar al tocar esas líneas)**: el botón de
  guardar peso de `WeightScreen` cerraba el diálogo y vaciaba el campo sin comprobar si `saveProfile()`
  había tenido éxito — una instancia exacta de ERR-1. Se corrigió en el mismo cambio: ahora solo se
  cierra dentro de `onSuccess`, y si falla (`onFailure` o excepción) se queda abierto con el peso
  introducido y un error visible. El resto de instancias de ERR-1 (catalogadas en otros archivos) sigue
  sin tocar — no se amplió el alcance más allá de esta coincidencia directa.
- **VAL-2**: en `ProfileScreen`, guardar los datos personales (nombre/teléfono/país/fecha de nacimiento)
  recalculaba la edad desde la fecha de nacimiento y, si procedía, reguardaba el perfil físico entero a
  escondidas — pero con `goalRate.toDoubleOrNull() ?: 0.0`, saltándose la validación que sí tiene el
  botón de guardado físico. Se cambió la condición para reguardar solo si peso/altura/ritmo pasan la
  misma validación del botón físico; si no pasan, se omite el reguardado en silencio (decisión
  deliberada: es un efecto derivado de un botón que el usuario no pulsó para esto, así que no se le
  muestra un error ajeno — simplemente ya no se corrompe el dato con ceros). El `catch (_: Exception) {}`
  que protege el parseo de la fecha se dejó igual, a propósito: protege un cálculo auxiliar no crítico,
  no el guardado de los datos personales en sí (que ya tiene su propio flujo de éxito/fallo).
- **IA-2**: en `OpenAIService.kt` (servidor), se añadió `temperature = 0.2` (bajo pero no 0, sigue siendo
  una estimación) y `response_format: {"type": "json_object"}` (modo JSON nativo de gpt-4o) a la
  petición. Se suavizó el prompt: ya no obliga al modelo a fingir certeza absoluta — puede señalar en el
  campo `summary` cuando la foto no permite una estimación fiable, sin dejar de dar una estimación en los
  4 campos obligatorios (romper ese contrato rompería el parseo en el cliente).
- **IA-3**: se añadió una instancia de `Json` dedicada con `isLenient = true` (y `ignoreUnknownKeys =
  true`) para decodificar la respuesta del modelo, en vez de usar el `Json` global estricto. Antes, si el
  modelo devolvía un número donde se esperaba un string (p. ej. `"bodyFat": 15` en vez de `"15"`), el
  parseo lanzaba excepción y se perdía el análisis entero por un detalle de formato irrelevante para el
  contenido. No cubre el caso de que el modelo omita un campo entero — ahí sigue lanzando excepción,
  correctamente (no hay nada razonable que inventar para un campo que no vino).
- Los cuatro cambios compilan limpio en los cuatro targets verificables aquí
  (`:composeApp:compileKotlinJvm`, `compileKotlinJs`, `compileKotlinWasmJs`, `:server:compileKotlin`) —
  ninguno toca código Android, así que no se añade nada nuevo a la lista de "sin verificar".
- Con esto se cierra el backlog acordado antes de la pausa. **Pendiente de que el usuario confirme el
  commit**; después, según lo acordado, toca que él pruebe la app en su máquina antes de seguir.

### 2026-11-27 — Tema viejo borrado y AUD-16 cerrado

- El usuario confirmó borrar `GymSmartTheme`/`GymSmartColors`. Se borró `AppTheme.kt` entero (nada más
  lo usaba); `RateSlider` toma por defecto `RateSliderColors.melocoton()` y se quitaron las dos copias
  idénticas de colores en perfil y onboarding.
- AUD-16: el usuario recordó que la comprobación de BBDD ya estaba hecha (lo está desde el 22/11, la
  tabla del backlog seguía diciendo "pendiente"). Se aplicó la decisión pendiente: `has_wearable` fuera
  del `INSERT`/`CREATE TABLE` y del modelo del cliente. El campo se sigue aceptando en el servidor por
  compatibilidad con la app instalada (JSON estricto).

### 2026-11-27 — Notificación de descanso que de verdad llega, e insignia de unión compacta

- El usuario (captura del móvil) pidió mejorar la píldora naranja "Unido con …" y que el fin del
  descanso llegue como una notificación tipo WhatsApp, no dentro de la app.
- Diagnóstico del aviso: nunca se pedía `POST_NOTIFICATIONS`; alarma inexacta en Android 14+ por falta
  de `USE_EXACT_ALARM`; y la pantalla cancelaba la notificación si el contador llegaba a cero con la
  app abierta. Los tres arreglados; canal nuevo con vibración y visibilidad en bloqueo.
- Verificado con la batería completa + Android. Sin probar en el móvil.
