# Auditoría del registro de alimentos y macros — comité de 5 paneles (2026-11-27)

> **Qué es esto.** El usuario pidió auditar (no rediseñar) el sistema ya construido de registro de
> alimentos: gramos → kcal y macros. Se lanzaron 5 subagentes en paralelo con mandato adversarial y
> obligación de verificar contra fuente primaria; esta sesión hizo de auditor independiente,
> contrastó los informes entre sí y comprobó de primera mano los hallazgos que condicionan el plan.
>
> **Solo diagnóstico. Nada implementado. Ningún fichero de código tocado.**
>
> Complementa a [12-auditoria-comite-kcal-2026-11-27.md](12-auditoria-comite-kcal-2026-11-27.md),
> que cubrió el **gasto energético** (BMR/TDEE/objetivos) el mismo día. Este cubre el **otro lado de
> la ecuación**: los alimentos que se registran contra ese objetivo. Hay solape en un panel y se
> declara explícitamente en §0.3.

## 0. Resumen ejecutivo

### 0.1 El hallazgo que manda sobre todos los demás

**La búsqueda de alimentos de marca está caída y la app lo oculta.** `cgi/search.pl` de Open Food
Facts devuelve **503 en el 100 % de los intentos**, incluso respetando su límite documentado de
10 req/min. Verificado por el panel de datos (0/12) y **reconfirmado de primera mano por esta sesión
(3/3 → 503)**, con Search-a-licious y la API de producto por EAN respondiendo 200 desde la misma IP.

El cuerpo del 503 dice literalmente: *"the page you requested is not available to anonymous users […]
Registered users are not subject to request limits"*.

Consecuencia exacta, leída en el código: `FoodService.fetchSearch` devuelve `FoodSearchResponse()`
vacío ante cualquier status ≠ 200 **sin lanzar y sin loguear** (`FoodService.kt:146`). `searchFood`
reintenta el índice global (gastando una segunda petición del presupuesto), también 503, y responde
`200 {"products":[]}`. El cliente ve éxito, `branded` queda vacío, y `rankFoodResults` se llama solo
con los 90 genéricos locales.

Hoy, por tanto: buscar algo que no esté en los 90 genéricos muestra **"Sin resultados con datos
nutricionales para X"** — un mensaje falso. Y el servidor **no deja rastro en los logs de Render**,
porque el `catch` que loguea nunca se ejecuta (un 503 no lanza excepción).

`doc/08-backlog-mejoras.md`, hallazgo **OFF-2** (verificado el 2026-11-24), afirma *"El endpoint
responde con normalidad a día de hoy"* y concluye *"Decisión: no migrar ahora"*. **Esa entrada está
obsoleta y hay que reescribirla.**

La tabla de genéricos (tarea H) es lo único que mantiene la app en pie. Era una red de seguridad y
hoy está siendo el suelo.

### 0.2 Lo que está bien y no hay que tocar

Hay que decirlo porque el resto del documento es una lista de problemas:

- **Los 89 `fdcId` de `GenericFoods.kt` existen, son todos SR Legacy, y los 4 valores de los 89
  coinciden con la API oficial de FoodData Central.** Cero errores de transcripción. La tabla se
  generó de verdad desde el volcado.
- **No hay error de redondeo acumulado.** El total que ve el usuario y el
  `SUM(grams * kcal_per_100 / 100.0)` que alimenta el autoajuste son el mismo valor matemático.
  Verificado línea a línea.
- **El "deshacer" de la conversión crudo/cocinado es matemáticamente exacto** y no se aplica dos
  veces en ninguno de los caminos vivos (crear, editar, historial, favoritos, colecciones).
- **Usar el cociente de kcal en vez de un rendimiento de peso es la decisión correcta para kcal**:
  reproduce los valores medidos de USDA a ≤1 kcal/100 g en 20 de 22 parejas.
- **El `DRY_FLOOR_KCAL = 250` aguanta el ataque**: ningún producto real encontrado lo viola.
- **`NaN`/`Infinity` están contenidos** y no son alcanzables por HTTP (kotlinx rechaza los literales).
- **El filtro `estimatedKcalPer100() > 0.0` (NUT-4) funciona**: descarta exactamente el 13,4 % de
  productos sin bloque `nutriments`.
- **El `User-Agent` cumple** la política de OFF.
- `:shared:jvmTest` → **116 tests, 0 fallos**; `BUILD SUCCESSFUL` en los tres targets verificados.

### 0.3 Solape declarado con doc/12 — y lo que es realmente nuevo

El panel de macronutrientes **re-derivó de forma independiente** dos hallazgos que `doc/12` ya tenía:

| Hallazgo | En doc/12 | Veredicto |
|---|---|---|
| Falta el TEF en el modo pulsera | **C-1 (crítico)** | Confirmado por segunda vez, por otra vía y con otra fuente (javadoc de `ActiveCaloriesBurnedRecord`). No es nuevo. |
| Proteína sobre peso total en obesidad | **C-4 (alto)** | Confirmado, y **cuantificado mejor**: ♀160 kg → 320 g/día = 65 % de las kcal = 6,7 g/kg de masa magra. |
| El suelo de DE no resta el gasto de ejercicio | **P2-5** | Confirmado. No es nuevo. |
| No hay suelo absoluto de kcal (riesgo VLCD) | **C-2 (crítico)** | Confirmado con un caso nuevo: ♀ 45 kg / IMC **20,0** (normopeso) → objetivo **1.043 kcal/día**. `lowBmiGuard` exige IMC < 18,5 y no salta. |

Que dos comités independientes, con paneles distintos, lleguen a C-1 y C-4 por caminos distintos
**sube la confianza en esos dos hallazgos**, no la baja. Pero significa que el panel de macros aportó
poco terreno nuevo. Lo genuinamente nuevo de este comité está en los otros cuatro paneles (§1-§4) y
en dos puntos del de macros: el **alcohol/fibra del Anexo XIV** (§3.1) y la **contradicción de
`BudgetCard`** (§3.2).

---

## 1. Dirimientos del auditor — donde los paneles se contradicen

Esto es lo que un solo agente no podía producir. Cinco conflictos reales, resueltos.

### D-1. Los multiplicadores de actividad: doc/12 dice "no tocar", este comité dice "error real"

- **doc/12, P1-6**: *"el multiplicador sedentario (1,2) **no está mal**, está en el extremo
  conservador de las referencias de agua doblemente marcada (≈1,4-1,5 en vida libre) […] corregir
  solo el texto del documento, no la constante."*
- **Panel de macros**: ERROR REAL. 1,2 y 1,375 caen **por debajo** de la banda PAL más baja medida
  por DLW en población libre (FAO/WHO/UNU 2004, tabla 5.3: 1,40-1,69); el 1,21 de la literatura es el
  PAL de sujetos **confinados en cámara metabólica**. Impacto en el perfil real del usuario
  (75,5 kg / 184 / 21 a): **−361 kcal/día**.

**Los dos parten del mismo hecho verificado.** No discrepan en el dato, discrepan en el veredicto.

**Dirimiento.** Ninguno de los dos tiene razón por completo, y la razón es una interacción que
ninguno vio:

1. **No se puede juzgar el 1,2 por separado del TEF.** El C-1 de doc/12 dice que el modo pulsera está
   un 10 % por debajo del modo fórmula y propone **subir el modo pulsera hasta igualarlo**. Pero si
   el modo fórmula está construido sobre un multiplicador por debajo de la banda medida, corregir el
   TEF alinea la pulsera con una referencia que ya es baja. Se arregla la incoherencia **entre las
   dos ramas** y se conserva el sesgo **de las dos**.
2. **Las dos ramas son mutuamente excluyentes**, así que las dos correcciones **no se suman en un
   mismo usuario**: `tdee()` usa `bmr + activas` si el nivel es `wearable`, y `bmr × multiplicador`
   en caso contrario. Quien use pulsera recibe la corrección del TEF; quien no, la del multiplicador.
   Esto hay que decirlo porque leyendo los dos informes seguidos parece que un usuario pudiera
   recibir +600 kcal.
3. **El argumento decisivo a favor de doc/12**: el autoajuste (`reconcile`) es la corrección empírica
   de este sesgo, y su factor está acotado a `0,70..1,30`. El hueco entre 1,2 y 1,4 es un factor de
   **1,167 — dentro del rango**. El sistema *tiene* un lazo de realimentación diseñado para absorber
   exactamente esto.
4. **El argumento decisivo a favor del panel de macros**: ese lazo solo converge si el usuario
   registra ingesta y peso con fidelidad durante semanas. La queja que originó toda esta línea de
   auditorías ("quiero ganar peso y no gano") es la firma de un lazo **que no ha convergido**. Y
   mientras no converge, el punto de partida es el que manda.

**Conclusión operativa:** subir el punto de partida es defendible, **pero no se puede desplegar sin
migrar a la vez el `adaptive_factor` guardado**, que puede llevar años absorbiendo el sesgo con un
factor > 1. Aplicar la corrección sin tocar los factores guardados **duplicaría la corrección**.
doc/12 ya identificó esto para C-1 y lo marcó como *decisión de migración de datos que necesita
confirmación explícita del usuario*. **El mismo requisito se extiende a los multiplicadores.**

→ Los dos comités convergen en la misma conclusión práctica por caminos distintos: **esto no es una
decisión de código, es una decisión del usuario sobre sus propios datos históricos.** Queda
documentado y no se toca. Lo que sí hay que corregir ya es el **texto** de `doc/10` §1.2, que
presenta el error como simétrico ("±350 kcal según el escalón que elijas") cuando es **direccional y
siempre a la baja**.

### D-2. ¿Las kcal se recalculan desde los macros o no? — y por qué el arreglo propuesto está mal

- **Panel de nutrición**: 42 de las 89 filas se desvían > 5 % de la regla 4/4/9 (hasta **+29 % en
  champiñones**, −5,8 % en bacalao). **No es un error de la tabla**: USDA no usa 4/4/9, usa los
  factores específicos de Merrill & Watt. Lo demostró reconstruyendo las kcal de 18 alimentos con
  esos factores, con error < 1,1 %. Veredicto: *las kcal de USDA son mejores que 4/4/9; que ninguna
  pantalla derive kcal de los macros.*
- **Panel de macros**: encontró que `BudgetCard` hace exactamente eso, y propuso guardar
  `otherKcalPer100 = kcal − (4P+4C+9G)` **acotado a ≥ 0** y pintarlo como cuarto tramo.

**Dirimiento: el diagnóstico del panel de macros es correcto y su arreglo está roto**, por lo que
encontró el panel de nutrición. Para los 42 alimentos con factores de Merrill & Watt bajos
(verduras, frutas, frutos secos) el residuo `kcal − 4/4/9` es **negativo** — champiñones: 22 kcal
declaradas frente a 28,5 de 4/4/9. Acotarlo a ≥ 0 no lo arregla: lo **silencia** justo en las filas
donde el problema existe, y la barra seguiría desbordando el número.

Verificado el código: `BudgetCard` (`NutritionScreen.kt:712-714`) calcula
`pk = proteins*4; ck = carbs*4; fk = fat*9` y **solo reescala cuando la suma supera el objetivo**.
Cuando la energía de etiqueta supera la de los macros (el caso normal con fibra y alcohol), el número
grande dice "2.000/2.000" y **la barra se queda al 90 %**. El `scale` solo corrige la dirección
contraria.

→ **Resolución correcta:** gana el panel de nutrición. `kcalPer100` es la única fuente de verdad de
la energía y **la UI no debe reconstruirla nunca**. La barra de macros debe dibujarse con su propio
denominador (la suma de kcal de macros) o como tres barras independientes de gramos contra gramos
objetivo, dejando las kcal como su propio indicador. Y hay que documentar los factores de
Merrill & Watt en la cabecera de `GenericFoods.kt`, **para que ninguna sesión futura "arregle" esas
kcal creyendo que están mal.**

### D-3. El Anexo XIV: crítico en un informe, casi irrelevante al cruzarlo con el otro

- **Panel de macros**: ERROR REAL, prioridad 4 de 15. La rama 4/4/9 no tiene término de fibra
  (2 kcal/g), ni polioles (2,4), ni alcohol (7). Verificado contra el Reglamento (UE) 1169/2011 y con
  productos reales: Wasa Fibres infracuenta **−52 kcal/100 g (−15,6 %)**; un chocolate con 40 g de
  maltitol sobrecuenta **+64 kcal/100 g**.
- **Panel de datos**, midiendo 119 productos españoles reales: la rama 4/4/9 **se usa el 0 % de las
  veces**. Los macros y las kcal en OFF *vienen o faltan juntos*; `energy-kcal_100g` está presente en
  el 86,6 % y el 13,4 % restante no tiene bloque `nutriments` en absoluto.

**Dirimiento:** el cruce **degrada** este hallazgo de crítico a menor. El Anexo XIV solo importa en
una rama que casi nunca se ejecuta. **Pero sobrevive una astilla, y es grave:** el alcohol. Los vinos
del índice español de OFF tienen `alcohol_100g = 12-13,5` y `energy-kcal_100g`, `proteins`, `carbs`
y `fat` **todos `null`**. Ahí `estimatedKcalPer100()` recorre las tres ramas y devuelve **0,0**.

Y aquí el cruce va en la otra dirección y **agrava**: el buscador filtra los 0 kcal (NUT-4), pero
**el escáner de código de barras no filtra nada**. Verificado de primera mano
(`NutritionScreen.kt:278-291`): va directo a `SheetMode.PortionPicker`, y "Añadir" solo exige
`grams > 0`. **Escanear una botella de vino registra la comida a 0 kcal.**

→ Lo que hay que arreglar no es la fórmula completa del Anexo XIV: es **(a)** el filtro del escáner y
**(b)** el campo `alcohol_100g`, para que el alcohol deje de ser invisible. Un botellín de Leffe de
330 ml registra 191 kcal correctas (vienen de la etiqueta) con **114 kcal que no aparecen en ningún
macro**.

### D-4. Factores por macro vs. datos imputados: dos arreglos para conjuntos disjuntos

El panel de bromatología hizo dos hallazgos que parecen el mismo y no lo son:

1. El factor de kcal se aplica igual a proteína, grasa e hidratos → hasta **+54 % de grasa** en carne
   picada, **+53 % de proteína** en cebada perlada. Arreglo propuesto: un factor por macro, calculado
   de las mismas parejas USDA ya citadas.
2. El 0,78 del pescado es **imputado**, no medido: ninguna de las 10 parejas donde USDA midió el
   pescado cocinado da 0,78; las 45 que lo dan son 45 repeticiones de una decisión editorial.

**Dirimiento: el arreglo (1) no sirve para el problema (2), y hay que aplicarlos a conjuntos
disjuntos.** Donde USDA **imputó** la entrada cocinada, los cocientes por macro son idénticos **por
construcción** — la huella es la que el propio panel encontró en el bacalao: 0,781 / 0,780 / 0,779,
los tres a tres decimales, con `derivation = Calculated` y `data_points = 0`. Calcular factores por
macro ahí no añade información: **reproduce la imputación con más decimales.**

→ Regla operativa, que el propio panel propuso y que confirmo: **factores por macro solo donde ambas
entradas tengan `data_points ≥ 5` y la discrepancia supere el 10 %** (picada, pollo, cerdo, cebada).
Donde la entrada cocinada es imputada (pescado, pavo) el arreglo no es más factores: es **otro
factor** — salmón de piscifactoría ≈1,0 medido frente a 0,78 aplicado, abadejo 0,878.

### D-5. ¿SR Legacy o FNDDS? — la respuesta es "las dos, sin mezclar dentro de una fila"

- **Panel de datos**: SR Legacy está **congelado desde 04/2018 por decisión oficial** (*"the final
  release […] These data will not be updated"*). Recomienda migrar a FNDDS 2021-2023 (más alimentos,
  platos preparados, y **pesos por medida casera**).
- **Paneles de nutrición y bromatología**: todo el catálogo de genéricos y **los 25 factores de
  cocción** están construidos sobre parejas crudo/cocido de SR Legacy con su `fdcId`.

**Dirimiento:** no es una migración, es una partición. **SR Legacy es el único dataset con el
nombrado regular `", raw"` / `", cooked, roasted"`** que hace posible emparejar automáticamente las
1.261 parejas crudo/cocido. FNDDS no tiene esa estructura. Por tanto:

- **Factores de cocción → SR Legacy, se queda.** Congelado no es un problema: la relación entre el
  peso crudo y el cocido de un pollo no caduca.
- **Ampliación del catálogo y medidas caseras → FNDDS.** Es la fuente correcta de `foodPortions`.
- **Nunca mezclar dentro de una fila**, y añadir una columna `dataType` a cada entrada.

Hay además un requisito que el panel de nutrición sacó y que esta partición hace urgente: en cuanto
entre el primer alimento español (queso de Burgos, manchego) con fuente que no sea USDA, el campo
`fdcId: String` **deja de poder representar la procedencia** y la promesa de la cabecera —"lleva su
identificador para poder comprobarla"— se rompe. Hace falta un campo `source` antes de añadir la
primera fila no-USDA.

---

## 2. Errores reales por panel

### 2.1 Capa de datos (panel de datos)

| # | Hallazgo | Severidad |
|---|---|---|
| N-1 | **`cgi/search.pl` → 503 el 100 % de las veces.** Búsqueda de marcas muerta; degradación silenciosa a los 90 genéricos; el mensaje "Sin resultados" miente. Reconfirmado por esta sesión. | **Crítico** |
| N-2 | **Un 503 no se loguea** (`FoodService.kt:146` devuelve vacío sin lanzar). La caída es invisible en los logs de Render. | Alto |
| N-3 | **El ranking no plega singular/plural.** Verificado en código: `"huevos"` contra `"Huevo entero"` no casa ni como substring → score **0** → `rankFoodResults` lo descarta. `huevos`, `platanos`, `tomates`, `yogures`, `nuez` **pierden el genérico**; `pechugas de pollo` devuelve **lista vacía**; `huevo` pone `Huevos`(44) **por detrás de** `Lacasitos huevo`(385). 11 tests y ninguno cubre un plural. | Alto |
| N-4 | **Cero validación de rangos** en toda la cadena. Entran productos españoles reales con P+C+F = **100,6 g/100 g** (imposible), "Caramelos" con 238 kcal declaradas frente a 416 reales (**error 75 %**), "Queso fresco de cabra" con 99,6 frente a 249 (**150 %**). 486 productos ES superan 105 g/100 g y **17.136 tienen errores que OFF ya marca** en `data_quality_errors_tags`, campo que el servidor no pide. | Alto |
| N-5 | **Sin deduplicación.** 34 % de nombres repetidos en la muestra; "Pan de molde" existe **74 veces**; "pollo" devuelve **6 filas idénticas** empatadas a 995 puntos, sin marca, indistinguibles. `completeness` y `unique_scans_n` están en el índice y no se usan. | Medio |
| N-6 | **No se lee `nutrition_data_per`**: el **11,8 %** de productos está en base **100 ml** (toda la leche, los caldos) y la app los rotula y los pide en **gramos**. Error del 3 % en leche, 9 % en aceite. | Medio |
| N-7 | **Sin atribución a Open Food Facts en la UI**, exigida por ODbL/DbCL. Irrelevante para uso personal; **bloqueante para Play Store**. Pertenece a `doc/04`. | Medio (legal) |

**Hipótesis descartadas con datos** (no son errores): `energy-kcal` == `energy-kcal_100g` en **103/103**
casos — OFF normaliza a `_100g` aunque el producto declare por ración, así que el riesgo que el KDoc
temía no se materializa. **0 productos** con energía solo en kJ: no se pierde nada por no leer kJ.

**Cobertura real de España en OFF**, medida con facetas: **362.650** productos, 76,6 % con ficha
nutricional, **6,6 %** con `completeness` ≥ 0,75. Francia tiene 1.270.513. Ése es el techo.

### 2.2 Conversión crudo/cocinado (panel de bromatología)

| # | Hallazgo | Impacto medido |
|---|---|---|
| C-1 | **Un solo factor para los cuatro macros.** Picada 20 % MG, 150 g hechos: **+117 kcal (+29 %) y +14,4 g de grasa (+54 %)**. El balance de masa demuestra que **8,1 g de los 20 g de grasa se quedan en la plancha**; la app asume retención del 100 %. USDA publica la pérdida de grasa como **columna separada**. | +54 % grasa |
| C-2 | **El factor de la picada es de la fila equivocada**: 0,73 es `ground beef, low fat (<12 % MG)`. La picada española corriente (15-20 %) rinde **69 %**; la de >22 %, **63 %**. | — |
| C-3 | **El 0,78 del pescado es una imputación de USDA, no un dato.** Ninguna de las 10 parejas medidas lo da (rango real 0,65-1,05, mediana 0,87). Salmón de piscifactoría: **208 → 206 kcal** medido (factor ≈1,0). Ración de 150 g: **+91 kcal (+29 %) y +7,3 g de grasa (+39 %)**. | +29 % |
| C-4 | **Cuscús 3,36.** El balance de masa sobre las aguas de USDA da 2,33 g de agua por gramo seco; las instrucciones españolas son **1:1 en volumen** (≈1,4 g/g → factor ≈2,4). 200 g preparados: la app registra **224 kcal donde hay 300-340**. Error **−26 a −35 %**, y en la dirección peligrosa. | −35 % |
| C-5 | **Patata 0,75 ("al horno")** aplicada a la patata hervida, que rinde **0,90-0,92**. 250 g: **+42 kcal (+19 %)**. En España se hierve más que se hornea. | +19 % |
| C-6 | **`looksAlreadyCooked` solo mira hacia abajo.** Ejecutado contra un puerto del matcher real: `Patatas paja` → **747 kcal/100 g**; `Mojama de atún` → 423; `Garbanzos tostados` → registra **89 kcal donde hay 205 (−57 %)**; `Tortilla de patatas` → convierte. Un `maxRawKcalPer100` (patata ~110, pollo ~200, pescado ~250) cierra los cuatro **sin añadir ni una palabra a la lista negra**. | hasta −57 % |
| C-7 | **`GLOBAL_EXCLUDES` falla en los dos sentidos.** Bloquea productos **crudos de alta rotación**: `Arroz vaporizado` (líder de categoría, se vende en seco), `Preparado de carne picada de vacuno y cerdo` (el etiquetado legal de Mercadona), `Arroz para paella`, `Fideos para sopa`. Y deja pasar `Pimientos rellenos de carne picada` y `Patatas rellenas` porque **faltan los plurales** `rellenos`/`rellenas` que el propio KDoc promete: **51 de 134 entradas no tienen plural**. | — |
| C-8 | **`phraseMatches` no exige adyacencia**, de ahí que `Ensalada de pasta con atún` se convierta **como atún crudo** (el catálogo pone pescado antes que pasta) → 205 kcal/100 g. | — |
| C-9 | **Siete errores de citación verificables.** `pollo_pechuga` cita el fdcId 171140 como *"roasted"* y es **braised**; igual el muslo (172853) — y `method = "al horno"` **se muestra al usuario**. `ternera_picada` cita *"73 % (n=40)"* y la fila real dice **n=4**. `pescado` cita "merluza (173713)" y es **whiting**; en SR Legacy **no hay merluza**. `pavo_pechuga` 0,83 deriva de **dos entradas imputadas** (medido: 0,72). | — |
| C-10 | **Dos afirmaciones del KDoc son falsas.** *"Los nutrientes se conservan; lo que cambia es el peso"* se atribuye a USDA: **no está en la fuente** y es falsa para la grasa (USDA define `Yield`, `Moisture change` y **`Fat change`** como tres magnitudes separadas). Y *"entre 165 y 333 no hay productos de esta familia"*: la **pasta fresca está a 288** y las alubias royal red a 329. | — |

**Vía descartada con fundamento:** los *USDA Nutrient Retention Factors* **no sirven**. La Release 6
contiene 26 factores de *"vitamins, minerals, and alcohol"* y **cero de macronutrientes** (buscado
"protein", "lipid", "carbohydrate" en el PDF completo: ninguna aparición). No es una opción.

### 2.3 Tabla de genéricos (panel de nutrición)

| # | Hallazgo | Impacto |
|---|---|---|
| G-1 | **`Queso fresco` apunta a *cottage cheese*.** En España es Burgos: **81 kcal frente a ~203, y 2,3 g de grasa frente a ~15**. El número es correcto para otro alimento. | −60 % kcal |
| G-2 | **`Queso curado` apunta a *cheddar en lonchas*.** Frente a un manchego: **−25 a −30 % de proteína**. En una app de gimnasio, 8-10 g/100 g importan. | −30 % proteína |
| G-3 | **`Merluza` apunta a *whiting*** (pescadilla). Las kcal salvan la papeleta; el reparto es **+15 % proteína, −53 % grasa**. | — |
| G-4 | `Manzana` usa la variedad *golden delicious* (57 kcal) donde existe el genérico (171688, 52 kcal). `Pan blanco` usa la variante *low sodium, no salt*. Mejoras de curación, coste = cambiar el `fdcId`. | +10 % |
| G-5 | **Falta un campo `source`** (§D-5): sin él no se puede añadir ninguna fila española sin romper la trazabilidad que la cabecera promete. | — |

**Huecos del catálogo para España, prioridad 1** (se consumen a diario y hoy no existen): jamón
serrano y cocido; **atún en conserva** (solo hay atún rojo fresco, que casi nadie compra);
**legumbres cocidas de bote**; pan de barra y de molde; **leche semidesnatada** (la más vendida);
queso de Burgos y manchego; aceite de girasol; azúcar, harina y sal.

**No añadir bebidas alcohólicas hasta resolver el modelo**: `GenericFood` no tiene campo de alcohol y
las kcal de una cerveza serían imposibles de cuadrar con P/C/G por definición.

### 2.4 Implementación (panel de ingeniería)

| # | Hallazgo | Severidad |
|---|---|---|
| I-1 | **Doble conteo invisible por fallo de red.** `saveMealRemote` no distingue "el servidor rechazó" de "no me llegó la respuesta" (`catch { null }`). Si el INSERT entra y el móvil pierde la conexión antes del 201: el usuario ve "no se pudo guardar", reintenta, y quedan **dos filas en BBDD y una en pantalla**. Desde ahí el `SUM` que alimenta el autoajuste es **el doble** de lo que el usuario ve. Arreglo: UUID en cliente + `ON CONFLICT(id) DO NOTHING`. | **Media** |
| I-2 | **La conversión se bloquea en silencio.** `CookingYield.kt:565` promete por escrito *"se bloquea **y se avisa**"*. Verificado: `alreadyCooked` aparece **una sola vez** en 1.712 líneas — se declara y no se usa. Registrar un "Arroz basmati" de 158 kcal/100 g hace **desaparecer el selector de base sin explicación**. Los imports `CookingYield` y `rawWeightFor` también están sin usar: la pista *"150 g cocido ≈ 197 g crudo"* del KDoc **no existe**. | Media |
| I-3 | **Riesgo latente de doble aplicación del factor.** El "deshacer" depende de que `COOKING_YIELDS` conserve ese `id` con **el mismo factor**. Si se corrige un factor (y §2.2 propone corregir varios), el historial reconstruye valores distintos de los registrados. Si un `id` desaparece, el `?: 1.0` devuelve **valores cocidos como crudos**. **Esto convierte los arreglos de §2.2 en una migración de datos.** Arreglo: persistir `yield_factor REAL` y que el deshacer sea aritmética pura. | Media (futura) |
| I-4 | **Cero validación de entrada** en `/nutrition/add` y `/update`: `grams` negativo, 0 o 10⁹ se aceptan; `kcalPer100` sin techo; `date` arbitraria; `mealType` sin validar. **Inconsistencia:** `validateCollections` **sí** valida `0 < grams ≤ 5000` — el límite está en la puerta equivocada. Daño acotado aguas abajo por `reconcile` (800..6000) y `ADAPTIVE_FACTOR_RANGE`. | Baja (contenida) |
| I-5 | **Truncamiento en vez de redondeo** (`toInt()`/`toLong()` en 8 sitios): objetivo 2.000 y total 1.500,6 → "1.500 / 2.000" + "499 quedan" (1.500+499 = 1.999); total 2.000,5 → **"0 quedan" estando pasado**. Las filas no suman la cabecera. | Baja (cosmética) |
| I-6 | **Código muerto que da falsa cobertura.** `existingEntry` **nunca se asigna**: toda la rama "reabrir la edición donde se dejó" que documentan `MealEntry.kt:11` y `NutritionModels.kt:245` **no existe**. Y si se conectara, `onConfirm` llama a `/nutrition/add` en vez de `/update` y **duplicaría la fila**. Tampoco existe la UI para elegir el alimento de referencia a mano, que es la razón por la que todo el código justifica guardar `yieldId`. | Baja (trampa) |
| I-7 | `updateMeal` **ignora** `name`, `mealType` y `date` aunque el cliente los manda. Mover una comida de día no persistiría. Hoy inalcanzable. | Baja (trampa) |
| I-8 | `getFoodHistory` usa `HAVING MAX(created_at)`, que **funciona** por una regla de *bare columns* de SQLite (verificado ejecutándolo), pero descarta grupos con `created_at = 0` y se rompe en silencio si se añade un segundo agregado. Fragilidad, no error. | Baja (robustez) |
| I-9 | `TtlCache.map.clear()`: **500 búsquedas distintas vacían la caché entera**, anulando justo la protección contra el rate-limit de OFF que la motiva. Y es en memoria: se pierde en cada arranque en frío de Render. | Baja |
| I-10 | `isFavorite` → `addFavorite` es check-then-act sin `UNIQUE(user_id, name)`: doble tap crea dos filas. Escritura de estado durante la composición en `NutritionScreen.kt:1347`. | Baja |

**Pistas de esta sesión que los agentes refutaron** (y que no hay que confirmar por complacencia):
los favoritos **no** necesitan `basis` (guardan siempre base cruda, verificado siguiendo el único
productor); **no** hay error de redondeo acumulado; `NaN`/`Infinity` **no** son alcanzables; la
notación científica round-trippea bien por afinidad REAL; **no** hay bug de locale.

---

## 3. Plan propuesto, por fases

Ordenado por daño real al usuario, no por elegancia. **Nada de esto está implementado.**

### Fase 0 — Parar el incendio (horas)

1. **Loguear el status ≠ 200** en `fetchSearch`. Sin esto no hay forma de saber que OFF está caído.
2. **Distinguir "no hay resultados" de "la búsqueda está caída"**: que `searchFood` devuelva el
   motivo y que la UI diga *"La base de marcas no está disponible; se muestran solo genéricos"*.
3. **Migrar la búsqueda a Search-a-licious** (`search.openfoodfacts.org`, 200 verificado sin
   espaciado) **hidratando los nutrientes** por EAN solo para los 8-10 primeros resultados —
   Search-a-licious **no indexa `nutriments`** (verificado), así que no es sustituto directo. Con el
   límite de 15 req/min eso da ~1,5 búsquedas/min en frío; la caché por EAN lo absorbe en días.
4. **Quitar el doble fetch España→global** mientras no se verifique que `countries_tags_en=spain`
   filtra algo en `search.pl` (no está documentado para ese endpoint): si se ignora, se está gastando
   **el doble del presupuesto para nada**.

### Fase 1 — Dejar de registrar datos imposibles (1 día)

5. Pedir a OFF los campos que faltan: `product_name_es`, `nutrition_data_per`, `serving_size`,
   `serving_quantity`, `data_quality_errors_tags`, `completeness`, `unique_scans_n`, `alcohol_100g`.
6. **Filtro de sanidad en el servidor**, en los dos caminos: techo de 902 kcal/100 g (= 100 g de
   grasa pura), cada macro en 0..100, **P+C+F ≤ 101**, y descarte por `data_quality_errors_tags`.
   Medido: 0 falsos positivos sobre 102 productos válidos.
7. **Aplicar el filtro de 0 kcal también al escáner** (D-3). Hoy escanear un vino registra 0 kcal.
8. **Deduplicar** por (nombre, marca) normalizados, quedándose con el de mayor `completeness`.
9. **Base 100 ml**: rotular "por X ml" cuando `nutrition_data_per == "100ml"`.
10. **Validar la entrada** en `/nutrition/add` y `/update` (I-4), moviendo el límite a la puerta
    correcta.
11. **Idempotencia de `/nutrition/add`** (I-1): UUID en cliente + `ON CONFLICT DO NOTHING`.

### Fase 2 — Que la búsqueda encuentre lo que se escribe (medio día)

12. **Plegado singular/plural** en `searchRelevance` + campo `aliases` en `GenericFood`, con tests
    para los seis casos que fallan hoy (N-3).
13. **Avisar cuando la conversión se bloquea** (I-2) y mostrar el equivalente en crudo.
14. **`roundToInt()`** en la presentación (I-5).

### Fase 3 — Precisión de la conversión (1-2 días, y es migración de datos)

15. **`maxRawKcalPer100`** (C-6): un campo, cuatro valores, cierra `Patatas paja`, `Mojama`,
    `Garbanzos tostados` y `Fideos instantáneos` sin tocar la lista negra. Hay que actualizar el test
    `no_se_inventa_un_suelo_donde_no_hay_senal`, que hoy fija el comportamiento actual.
16. **Los 51 plurales que faltan** en `GLOBAL_EXCLUDES`, y **sacar** `vaporizado`, `preparado`,
    `paella` y `sopa` (C-7) — pero solo junto con 17, porque hoy esas palabras son lo único que
    bloquea los vasitos.
17. **Desdoblar por método**: `patata_hervida` 0,90, `arroz_vaporizado` 3,04, picada por nivel de
    grasa (0,73/0,69/0,63), `pavo_pechuga` → 0,74, salmón de piscifactoría ≈1,0.
18. **Factores por macro** solo donde ambas entradas tengan `data_points ≥ 5` y la discrepancia
    supere el 10 % (D-4).
19. **`yield_factor REAL` persistido** (I-3). **Prerrequisito de 17 y 18**: sin él, corregir un
    factor cambia retroactivamente lo que el historial reconstruye.
20. **Corregir las 7 citaciones y las 2 afirmaciones falsas** del KDoc y de `doc/10` §13 (C-9, C-10).

### Fase 4 — La capa curada, en serio (2-4 días)

21. **`tools/genericos.csv` versionado + script generador** con encabezado "GENERADO — no editar a
    mano". Hoy la cabecera dice *"regenerar"* y **el script no está en el repo**: cada ampliación es
    trabajo manual irrepetible.
22. **Campo `source`** (G-5), prerrequisito de cualquier fila no-USDA.
23. **Corregir G-1, G-2, G-3** y subir de 89 a **300-400 genéricos** desde **FNDDS 2021-2023** (D-5),
    priorizando los huecos de prioridad 1.
24. **Medidas caseras.** El hueco más visible frente a Fitia. Dos vías, y la primera es casi gratis:
    **el 57 % de los productos de OFF ya traen `serving_size` y `serving_quantity`** (verificado:
    `"1 vaso (200 ml)"`, `"90 g"`), así que un chip "1 ración (90 g)" sale con ~15 líneas y sin
    fuente nueva. Para los genéricos, `foodPortions` de FNDDS. **La aritmética no cambia** (sigue
    siendo gramos × valor/100).
25. **Documentar Merrill & Watt** en la cabecera de `GenericFoods.kt` y **quitar de `BudgetCard` la
    reconstrucción de kcal desde macros** (D-2).

### Fase 5 — Cobertura española de verdad (opcional)

26. **CIQUAL** (ANSES) como segunda fuente de genéricos: **Licence Ouverte (Etalab)**, confirmada vía
    la API de data.gouv.fr. Es **la única fuente europea redistribuible sin pedir permiso**.
    ~3.185 alimentos, cocina mediterránea.
27. **BEDCA solo tras permiso escrito de AESAN.** Su endpoint XML funciona (verificado: 2.336
    registros, 1.267 nombres únicos, incluye merluza, pulpo, jamón serrano, manchego), pero el footer
    dice *"todos los derechos reservados"*, **no hay condiciones de uso publicadas** y no está en
    datos.gob.es. **Redistribuir sus valores hoy es jurídicamente indefendible.** Consultarla para
    verificar a mano al curar sí es uso legítimo. Si llegara el permiso, es el salto cualitativo
    mayor.
28. **Atribución ODbL visible** (N-7): bloqueante para Play Store, trivial de hacer. → `doc/04`.
29. **EuroFIR descartado**: 2.000-5.500 €/año.

### Lo que NO se toca sin tu decisión explícita

- **Los multiplicadores de actividad y el TEF** (D-1 y C-1 de doc/12): cualquier corrección exige
  migrar el `adaptive_factor` guardado a la vez, o se duplica la corrección. **Es una decisión sobre
  tus datos históricos, no sobre el código.**
- **Las kcal de `GenericFoods.kt`**: las 42 desviaciones frente a 4/4/9 son **correctas** (D-2).

---

## 4. Techo honesto

Dos límites que ningún plan arregla, y conviene tenerlos escritos:

1. **España tiene 362.650 productos en OFF frente a 1.270.513 de Francia.** La cola larga del
   supermercado español no está catalogada. Ninguna arquitectura lo cambia; solo el tiempo y los
   escaneos de la comunidad.
2. **El rendimiento de cocción depende del cocinero** (tiempo, agua, método). Ningún factor único lo
   captura. La respuesta honesta no es un número mejor: es **una banda**. Las bandas reales son
   arroz 2,52-3,81, cuscús 2,0-3,4, patata 0,73-0,92, cordero 0,53-0,88 (este último **escrito en el
   propio `source` del código** y no propagado a la interfaz). `doc/10` §12 ya establece el
   principio: *"cuando la app muestre un número de esta lista, debe presentarse como estimación
   ajustable, nunca como dato cerrado"*. **Aquí no se está aplicando.**

## 5. Documentos que hay que actualizar si se aceptan estos hallazgos

- **`doc/08-backlog-mejoras.md`**: reescribir **OFF-2** (obsoleto, afirma que `search.pl` funciona) y
  abrir ítems para el filtro de sanidad, el plural, la deduplicación y el techo de cordura.
- **`doc/10-fundamento-cientifico.md`**: §13 (7 citaciones + 2 afirmaciones falsas), §1.2 (el sesgo
  de los multiplicadores es direccional, no simétrico), §13.4 (reescribir la justificación del 0,78
  del pescado), §13.6 (la "franja vacía" no está vacía).
- **`doc/04-produccion-playstore.md`**: atribución ODbL.
- **`doc/06-funcionalidades.md`**: el veredicto de nutrición cambia mientras la búsqueda esté caída.
- **`doc/05-deuda-tecnica.md`**: I-6 y I-7 (código muerto y trampas).
