package com.gymsmart.gymsmart.nutrition

/**
 * Conversión entre peso en crudo y peso cocinado de un alimento.
 *
 * **El problema.** Los datos nutricionales que devuelve Open Food Facts son "tal como se vende": para
 * un alimento fresco eso significa *en crudo* (o en seco, para arroz, pasta y legumbres). Si el usuario
 * pesa el alimento ya cocinado y le aplica esos valores, el resultado está mal, y mucho: 100 g de
 * pechuga de pollo cruda no son 100 g de pechuga hecha, porque al cocinarla pierde agua. Y al revés con
 * el arroz, que absorbe agua. Los dos errores son grandes y van en sentido contrario, que es lo que los
 * hace traicioneros.
 *
 * **Cómo se corrige.** Es el método que la propia USDA documenta: *"nutrient composition of a cooked
 * food may be calculated from the uncooked food by applying cooking yield factors to these data to
 * reflect changes in food weights resulting from moisture and fat losses during cooking"*. Los
 * nutrientes se conservan; lo que cambia es el peso. Así que para pasar los valores por 100 g de la
 * base cruda a la base cocinada basta dividir por el factor de rendimiento ([CookingYield.factor]).
 *
 * **De dónde salen los factores.** Casi todos se derivaron de forma sistemática del volcado completo de
 * **USDA FoodData Central, SR Legacy**: ese dataset nombra los alimentos de forma regular ("…, raw" /
 * "…, cooked, roasted"), lo que permitió emparejar automáticamente las 1.261 parejas crudo/cocido que
 * contiene y quedarse con las relevantes, en vez de ir buscándolas a mano de una en una. El factor de
 * cada pareja es `kcal/100 g en crudo ÷ kcal/100 g cocinado`. Las excepciones (ternera picada, cordero)
 * salen de la *"USDA Table of Cooking Yields for Meat and Poultry"* y están marcadas en su `source`.
 *
 * **Por qué no vale para todos los alimentos.** Un factor solo se aplica cuando se sabe de qué alimento
 * se trata y en qué base están sus datos. Para un producto cualquiera de Open Food Facts no se sabe
 * ninguna de las dos cosas: un plato preparado ya viene cocinado. Por eso [findCookingYield] solo
 * reconoce lo que está en el catálogo y descarta de forma explícita lo que ya viene cocinado o
 * procesado ([GLOBAL_EXCLUDES]). Cuando no reconoce el alimento, la app **no adivina**: ofrece al
 * usuario elegir a mano el alimento de referencia (ver [COOKING_YIELD_FAMILIES]), que es precisamente
 * el dato que faltaba.
 *
 * **Fuentes.** Cada factor está justificado, con sus identificadores de FoodData Central, en
 * `doc/10-fundamento-cientifico.md` §13. No añadir factores aquí sin documentarlos allí.
 */

/** Base sobre la que está pesado un alimento. */
enum class WeighBasis { RAW, COOKED }

/**
 * Un alimento (o familia de alimentos) para el que se conoce el cambio de peso al cocinarlo.
 *
 * @param factor peso cocinado ÷ peso crudo. Menor que 1 si pierde agua (carnes, pescado), mayor que 1
 *   si la absorbe (arroz, pasta, legumbres).
 * @param family grupo para presentarlo agrupado cuando el usuario elige el alimento a mano.
 * @param rawLabel cómo llamar a la base cruda en la interfaz: "Crudo" para una carne, "En seco" para
 *   el arroz, porque "arroz crudo" no es como habla nadie.
 * @param method el método de cocinado al que corresponde el factor. Se muestra al usuario: el factor
 *   no es universal y conviene que se vea de dónde sale.
 * @param keywords frases que identifican el alimento. Una frase coincide si *todas* sus palabras
 *   aparecen como palabra completa en el nombre del producto.
 * @param excludes frases que descartan esta categoría aunque alguna de [keywords] coincida (p. ej.
 *   "atún en escabeche" lleva atún, pero su lata no se pesa en crudo).
 * @param minRawKcalPer100 suelo de cordura: por debajo de estas kcal/100 g, el producto **no puede**
 *   estar en base cruda y se bloquea la conversión (ver [looksAlreadyCooked]). `null` significa que
 *   para este alimento no hay un suelo defendible y no se comprueba nada.
 * @param maxRawKcalPer100 techo de cordura: por encima de estas kcal/100 g el producto tampoco puede
 *   estar en base cruda, pero por el motivo contrario — está frito, curado, desecado o es un snack.
 *   Es la mitad que faltaba: la comprobación solo miraba hacia abajo, y por arriba se colaban
 *   productos reales con errores enormes (ver [looksAlreadyCooked]).
 * @param defaultBasis base que se preselecciona. Las carnes se pesan casi siempre en crudo y los
 *   cereales y legumbres casi siempre ya cocidos, así que el valor por defecto sigue al uso real en
 *   vez de ser el mismo para todo.
 */
data class CookingYield(
    val id: String,
    val label: String,
    val family: String,
    val factor: Double,
    val rawLabel: String,
    val cookedLabel: String,
    val method: String,
    val source: String,
    val keywords: List<String>,
    val excludes: List<String> = emptyList(),
    val minRawKcalPer100: Double? = null,
    val maxRawKcalPer100: Double? = null,
    val defaultBasis: WeighBasis = WeighBasis.RAW,
    /** Ver [Macro]. `null` significa "usa el de la energía". */
    val factorProtein: Double? = null,
    val factorCarbs: Double? = null,
    val factorFat: Double? = null
)

/**
 * Qué se está convirtiendo. Existe porque **un solo factor para los cuatro no es defendible**.
 *
 * El factor de la energía describe cómo cambia la densidad energética al cocinar, y aplicarlo también
 * a la proteína y a la grasa da por supuesto que los tres se comportan igual. No lo hacen: al cocinar
 * una carne la grasa gotea y se va, mientras la proteína se queda. De las 26 parejas crudo/cocinado
 * del catálogo, **15 divergen más del 10 %** entre macros, y algunas muchísimo más:
 *
 *   muslo de pollo   energía 0,676  proteína 0,794  grasa 0,506   (25 % de divergencia)
 *   cordero          energía 0,996  proteína 0,669  grasa 1,191   (33 %)
 *   cebada perlada   energía 2,862  proteína 4,385  grasa 2,636   (53 %)
 *   pasta            energía 2,348  proteína 2,248  grasa 1,624   (31 %)
 *
 * La propia USDA nunca afirma que los nutrientes se conserven: su publicación de rendimientos define
 * `Yield`, `Moisture change` y **`Fat change`** como tres magnitudes separadas. La frase que había en
 * la cabecera de este fichero —"los nutrientes se conservan; lo que cambia es el peso", atribuida a
 * la USDA— no está en la fuente y es falsa para la grasa.
 *
 * Y no se trata de inventar nada: cada factor por macro es el cociente de esa misma pareja de la
 * USDA, así que aplicarlo **reproduce exactamente el valor que la USDA publica** para el alimento
 * cocinado, en los cuatro nutrientes y no solo en las kcal.
 *
 * Donde la entrada cocinada es una **imputación** de la USDA (el pescado blanco: los tres cocientes
 * salen idénticos a tres decimales porque dividió todos los nutrientes por 0,78), los factores por
 * macro no añaden nada y se dejan a `null`.
 */
enum class Macro { ENERGY, PROTEIN, CARBS, FAT }

/** El factor que corresponde a [macro], cayendo al de la energía cuando no hay uno propio. */
fun CookingYield.factorFor(macro: Macro): Double = when (macro) {
    Macro.ENERGY -> factor
    Macro.PROTEIN -> factorProtein ?: factor
    Macro.CARBS -> factorCarbs ?: factor
    Macro.FAT -> factorFat ?: factor
}

/**
 * Techos de cordura, en kcal/100 g, para los alimentos en los que **no hay suelo defendible pero sí
 * techo**. Esa asimetría es el hallazgo: la dispersión entre marcas impide poner un suelo en carne y
 * pescado (un fiambre de pollo puede marcar 62 kcal), pero no impide poner un techo, porque ningún
 * producto crudo de esa familia se acerca a él.
 *
 * Cada valor deja un margen amplio sobre el alimento crudo más calórico de su familia en SR Legacy:
 * - **Patata**: la más calórica cruda es la russet, 79 kcal/100 g. Techo 110.
 * - **Aves**: el muslo de pollo con piel ronda 150-160 kcal/100 g en crudo. Techo 200.
 * - **Pescado**: el más graso es el salmón atlántico de piscifactoría, 208. Techo 250.
 * - **Carne roja y picada**: una picada al 30 % de grasa ronda 300. Techo 340.
 *
 * Lo que cierran, comprobado contra el emparejador real con nombres del lineal español:
 * `Patatas paja` (560 kcal → la app calculaba **747 kcal/100 g**), `Patatas noisette` (180),
 * `Patatas gajo congeladas` (150), `Tortilla de patatas` (160), `Mojama de atún` (330 → 423),
 * `Garbanzos tostados` (410 → registraba **89 kcal donde hay 205**) y `Fideos orientales
 * instantáneos` (450). Ninguno llevaba una palabra que la lista de exclusiones pudiera cazar, y esa
 * lista ya reconoce en su propio comentario que es una carrera que no se gana.
 */
const val MAX_RAW_KCAL_TUBER  = 110.0
const val MAX_RAW_KCAL_POULTRY = 200.0
const val MAX_RAW_KCAL_FISH   = 250.0
const val MAX_RAW_KCAL_MEAT   = 340.0

/**
 * Techo para los áridos. El más calórico de los que este catálogo reconoce es el garbanzo en seco,
 * **378 kcal/100 g**; le siguen el cuscús (376) y la pasta (371). Por encima de 400 ya no hay árido
 * en seco: hay un snack tostado, frito o con grasa añadida, como los `Garbanzos tostados` (~410) que
 * la app registraba a 89 kcal donde hay 205.
 *
 * La banda es más estrecha que la del suelo —22 kcal de margen, frente a los ~85 de abajo— y hay que
 * decirlo: un snack de legumbre tostada que marcara 390 se colaría. Aun así cierra el caso medido sin
 * tocar ningún alimento real del catálogo, y la alternativa (no tener techo) deja pasar errores del
 * 57 %.
 */
const val MAX_RAW_KCAL_DRY = 400.0

/**
 * Suelo de cordura para cereales y legumbres en seco, en kcal/100 g.
 *
 * No es un umbral afinado a ojo, es una franja vacía: un árido en seco está entre 333 (alubia blanca)
 * y 378 (garbanzo) kcal/100 g, porque es almidón casi sin agua; el mismo alimento ya cocido está entre
 * 83 (bulgur) y 164 (garbanzo), porque ha absorbido dos o tres veces su peso en agua. Entre 165 y 333
 * no hay productos de esta familia, así que 250 parte esa franja por la mitad y deja ~85 kcal de
 * margen a cada lado. Un "arroz" que marque 158 kcal/100 g no es arroz en seco: es un vasito ya
 * cocido, y convertirlo daría 56 kcal/100 g.
 *
 * Solo se aplica a áridos. En carne y pescado se midió que no hay señal aprovechable (la variación
 * entre marcas supera la diferencia entre crudo y cocinado), así que ahí no se comprueba nada — ver
 * doc/10-fundamento-cientifico.md §13.6.
 */
const val DRY_FLOOR_KCAL = 250.0

const val FAMILY_MEAT = "Carnes y aves"
const val FAMILY_FISH = "Pescado y marisco"
const val FAMILY_GRAIN = "Cereales y pasta"
const val FAMILY_LEGUME = "Legumbres"
const val FAMILY_TUBER = "Tubérculos"

/**
 * Palabras que descartan cualquier conversión, se haya reconocido el alimento o no.
 *
 * Dos motivos distintos, los dos importantes:
 * - El producto **ya viene cocinado** ("arroz cocido", "pollo asado"): sus datos están en base
 *   cocinada, así que convertirlos sería aplicar el factor dos veces.
 * - El producto es un **derivado o un preparado** ("bebida de arroz", "nuggets de pollo", "hummus"):
 *   el factor del alimento base no dice nada sobre él.
 *
 * Hacen falta las dos formas de género de cada adjetivo porque la comparación es por palabra completa.
 * El **número** ya no hay que escribirlo: [phraseMatches] plega singular y plural, así que "cocido"
 * caza también "cocidos" (antes faltaba el plural en 51 de estas 134 entradas, y entre las que
 * faltaban estaban `rellenos`/`rellenas`, por las que se colaban los pimientos rellenos de carne
 * picada). Las formas en plural que quedan escritas son inofensivas y se dejan por legibilidad.
 *
 * ## Palabras que se han quitado a propósito (2026-11-27)
 *
 * Bloqueaban productos **crudos o en seco de altísima rotación** en el lineal español, y el caso
 * cocinado que pretendían cazar ya lo cubren las comprobaciones numéricas, que no dependen de acertar
 * con el vocabulario (ver [looksAlreadyCooked]):
 *
 * - **`vaporizado`/`vaporizada`** bloqueaba el *arroz vaporizado*, líder de su categoría y que se
 *   vende **en seco** (~350 kcal/100 g). El vasito de microondas que motivó la exclusión lo caza el
 *   suelo de 250 kcal, porque ya cocido marca ~158.
 * - **`preparado`** bloqueaba *"Preparado de carne picada de vacuno y cerdo"*, que es el etiquetado
 *   legal habitual de la carne picada cruda en Mercadona.
 * - **`paella`** bloqueaba *"Arroz especial para paella"* y *"Arroz bomba para paella"*, arroz en
 *   seco. Una paella ya hecha ronda 150 kcal/100 g y la caza el suelo.
 * - **`sopa`** bloqueaba *"Fideos finos para sopa"*, pasta en seco. Una sopa hecha ronda 40 kcal y la
 *   caza el suelo.
 * - **`glaseado`** y sus formas bloqueaban *prácticamente todo el pescado congelado español*, que
 *   declara el glaseado en la etiqueta. El glaseado es una capa de agua que se aplica al pescado
 *   **crudo** al congelarlo, así que la palabra no indica cocinado.
 *
 * `plancha` y `horno` **se conservan** aunque también producen falsos negativos ("pechuga fileteada
 * para plancha", "pollo entero para horno"): en aves y carne no hay suelo defendible, así que un
 * "pollo a la plancha" ya hecho (~165 kcal/100 g) pasaría el techo de 200 y se convertiría mal. El
 * coste de dejarlas es que a veces no se ofrece el conmutador, que es inocuo; el de quitarlas sería
 * contar mal, que no lo es.
 */
private val GLOBAL_EXCLUDES = listOf(
    // Ya cocinado
    "cocido", "cocida", "cocidos", "cocidas",
    "cocinado", "cocinada", "cocinados", "cocinadas",
    "hervido", "hervida", "hervidos", "hervidas",
    "asado", "asada", "asados", "asadas",
    "frito", "frita", "fritos", "fritas",
    "guisado", "guisada", "guisados", "guisadas",
    "estofado", "estofada", "estofados", "estofadas",
    "braseado", "braseada", "braseados", "braseadas",
    "salteado", "salteada", "salteados", "salteadas",
    "plancha", "horno", "vapor", "cooked", "boiled",
    // Conservas y platos preparados
    "conserva", "lata", "bote", "precocinado", "caldo", "crema", "pure",
    "salsa", "ensaladilla", "lasana", "pizza", "croqueta", "croquetas", "albondiga",
    "albondigas", "empanado", "empanada", "empanados", "empanadas",
    "rebozado", "rebozada", "rebozados", "rebozadas",
    "nuggets", "fingers", "san jacobo", "canelones", "escabeche", "salmuera", "relleno", "rellena",
    // "empanadilla" no es el plural de "empanada" sino un diminutivo, así que el plegado de número no
    // lo cubre y hace falta la palabra aparte.
    "empanadilla",
    // Verificadas una a una como agujeros reales del emparejado (2026-11-24): todas estas pasaban el
    // filtro y acababan convertidas. "prefritas" es el ejemplo de por qué la comparación por palabra
    // completa no basta sola: no coincide con "fritas".
    "prefrito", "prefrita", "prefritos", "prefritas", "bravas", "deluxe",
    "tarro", "vasito", "vasitos", "microondas", "solucion anadida", "solucion",
    // Embutidos y fiambres (ya curados o cocidos)
    "fiambre", "loncheado", "loncheada", "lonchas", "pate", "jamon", "chorizo", "salchichon",
    "salchicha", "salchichas", "mortadela", "bacon", "panceta", "embutido", "surimi", "palitos",
    "ahumado", "ahumada", "ahumados", "ahumadas", "curado", "curada", "curados", "curadas",
    // Derivados y snacks
    "harina", "bebida", "leche", "batido", "barrita", "galleta", "tortita", "tortitas", "snack",
    "hummus", "pan", "cereales", "copos", "proteina", "suplemento", "aperitivo", "chips"
)

/**
 * Catálogo de factores de rendimiento.
 *
 * El orden importa: [findCookingYield] devuelve la primera coincidencia, así que lo específico va antes
 * que lo genérico (arroz integral antes que arroz; muslo de pollo antes que pechuga, para que "muslo"
 * no caiga en la categoría equivocada si un nombre menciona las dos cosas).
 */
val COOKING_YIELDS: List<CookingYield> = listOf(

    // ── Carnes y aves ─────────────────────────────────────────────────────────

    CookingYield(
        id = "pollo_muslo",
        factorProtein = 0.794,
        factorFat = 0.506,
        maxRawKcalPer100 = MAX_RAW_KCAL_POULTRY,
        label = "Muslo o contramuslo de pollo",
        family = FAMILY_MEAT,
        factor = 0.68,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "al horno",
        // Corregido el 2026-11-27. La cita anterior era 172853 descrito como "cooked, roasted", y
        // comprobado contra el volcado de SR Legacy ese identificador es **"cooked, braised"**:
        // estofado, no asado, que es además el método que la app mostraba al usuario. El asado de
        // verdad es 172388.
        source = "FoodData Central SR Legacy: \"Chicken, broilers or fryers, dark meat, thigh, meat only, raw\" 121 kcal (173627) ÷ \"Chicken, broilers or fryers, thigh, meat only, cooked, roasted\" 179 kcal (172388)",
        keywords = listOf("muslo pollo", "muslos pollo", "contramuslo", "contramuslos", "jamoncito pollo"),
        excludes = listOf("pavo")
    ),
    CookingYield(
        id = "pollo_pechuga",
        factorProtein = 0.725,
        factorFat = 0.734,
        maxRawKcalPer100 = MAX_RAW_KCAL_POULTRY,
        label = "Pechuga de pollo",
        family = FAMILY_MEAT,
        factor = 0.73,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "al horno",
        // Corregido el 2026-11-27, mismo error que en el muslo: 171140 no es "roasted" sino
        // **"cooked, braised"**. La pechuga asada es 171477, 165 kcal. Y es un caso bonito: los tres
        // macros dan el mismo cociente (kcal 0,727, proteína 0,725, grasa 0,734), porque asar una
        // pechuga magra pierde sobre todo agua y casi nada de grasa.
        source = "FoodData Central SR Legacy: \"Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw\" 120 kcal (171077) ÷ \"Chicken, broilers or fryers, breast, meat only, cooked, roasted\" 165 kcal (171477)",
        keywords = listOf("pechuga pollo", "pollo pechuga", "filete pollo", "filetes pollo", "pollo fileteado"),
        excludes = listOf("pavo")
    ),
    CookingYield(
        id = "pavo_pechuga",
        factorProtein = 0.762,
        factorFat = 0.947,
        maxRawKcalPer100 = MAX_RAW_KCAL_POULTRY,
        label = "Pechuga de pavo (fresca)",
        family = FAMILY_MEAT,
        factor = 0.83,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "al horno",
        source = "FoodData Central SR Legacy: \"Turkey, all classes, breast, meat and skin, raw\" 157 kcal (171093) ÷ \"cooked, roasted\" 189 kcal (171492)",
        // En España "pechuga de pavo" es casi siempre fiambre, que ya viene cocido. Las exclusiones
        // globales cogen la mayoría ("fiambre", "lonchas", "braseada"), pero no todas: de ahí que el
        // alimento se llame "fresca" en la etiqueta, para que se vea a qué se está aplicando.
        keywords = listOf("pechuga pavo", "pavo pechuga", "filete pavo", "filetes pavo"),
        excludes = listOf("finas", "extrafinas", "finitas")
    ),
    CookingYield(
        id = "pavo_picado",
        factorProtein = 0.718,
        factorFat = 0.737,
        maxRawKcalPer100 = MAX_RAW_KCAL_POULTRY,
        label = "Carne picada de pavo",
        family = FAMILY_MEAT,
        factor = 0.73,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "a la plancha",
        source = "FoodData Central SR Legacy: \"Turkey, Ground, raw\" 148 kcal (171505) ÷ \"Turkey, Ground, cooked\" 203 kcal (171506)",
        keywords = listOf("pavo picado", "picada pavo", "picado pavo")
    ),
    CookingYield(
        id = "ternera_picada",
        factorProtein = 0.717,
        factorFat = 0.973,
        maxRawKcalPer100 = MAX_RAW_KCAL_MEAT,
        label = "Carne picada de ternera",
        family = FAMILY_MEAT,
        factor = 0.86,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "a la plancha",
        // Corregido el 2026-11-27, y es el error más grande que encontró la auditoría.
        //
        // El 0,73 anterior venía de la *USDA Table of Cooking Yields*, que publica **rendimiento de
        // peso**: de 100 g de carne cruda quedan 73 g cocinados. Pero este campo se usa como
        // **cociente de densidad energética** (`per100For` divide los valores por él), y en una carne
        // grasa las dos magnitudes no coinciden ni de lejos, porque la grasa gotea mientras el agua se
        // evapora. Comprobado en el volcado de SR Legacy:
        //
        //   90/10 MG: 176 → 217 kcal  ⇒ 0,81      80/20 MG: 254 → 270 kcal  ⇒ 0,94
        //   85/15 MG: 215 → 250 kcal  ⇒ 0,86      70/30 MG: 332 → 277 kcal  ⇒ 1,20 (!)
        //
        // Con 0,73 una hamburguesa de 150 g ya hecha se registraba en 522 kcal cuando la USDA mide
        // 405: **+117 kcal, un +29 %**. Se adopta 0,86, el de la picada al 15 % de grasa, que es la
        // franja de la picada mixta española corriente. Y nótese que por encima del 25 % de grasa el
        // cociente pasa de 1: la carne cocinada es *menos* densa que la cruda.
        //
        // Además el 0,73 se citaba con "n=40" y la fila real de esa tabla dice **n=4**.
        source = "FoodData Central SR Legacy: \"Beef, ground, 85% lean meat / 15% fat, raw\" 215 kcal (171796) ÷ \"patty, cooked, broiled\" 250 kcal (174032). Rango medido según el nivel de grasa: 0,81 (90/10) a 1,20 (70/30)",
        keywords = listOf("carne picada", "ternera picada", "vacuno picada", "picada vacuno", "picada mixta"),
        excludes = listOf("pavo", "pollo")
    ),
    CookingYield(
        id = "ternera_filete",
        factorProtein = 0.798,
        factorFat = 0.766,
        maxRawKcalPer100 = MAX_RAW_KCAL_MEAT,
        label = "Filete o solomillo de ternera",
        family = FAMILY_MEAT,
        factor = 0.79,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "al horno",
        source = "FoodData Central SR Legacy: \"Beef, loin, tenderloin roast, boneless, separable lean only, trimmed to 0\\\" fat, all grades, raw\" 139 kcal (171765) ÷ \"roasted\" 177 kcal (174004)",
        keywords = listOf(
            "filete ternera", "solomillo ternera", "entrecot", "chuleton", "bistec",
            "ternera lomo", "lomo ternera", "solomillo vacuno"
        ),
        excludes = listOf("cerdo", "pavo", "pollo")
    ),
    CookingYield(
        id = "cerdo_lomo",
        factorProtein = 0.801,
        factorFat = 0.618,
        maxRawKcalPer100 = MAX_RAW_KCAL_MEAT,
        label = "Lomo o solomillo de cerdo",
        family = FAMILY_MEAT,
        factor = 0.76,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "al horno",
        source = "FoodData Central SR Legacy: \"Pork, fresh, loin, tenderloin, separable lean only, raw\" 109 kcal (168249) ÷ \"cooked, roasted\" 143 kcal (168250)",
        keywords = listOf("lomo cerdo", "cinta lomo", "solomillo cerdo", "chuleta cerdo", "secreto cerdo", "presa iberica"),
        excludes = listOf("adobado", "adobada")
    ),
    CookingYield(
        id = "cordero",
        factorProtein = 0.669,
        factorFat = 1.191,
        maxRawKcalPer100 = MAX_RAW_KCAL_MEAT,
        label = "Cordero",
        family = FAMILY_MEAT,
        factor = 1.00,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "a la plancha o al horno",
        // Corregido el 2026-11-27 por el mismo motivo que la carne picada: el 0,74 anterior era un
        // **rendimiento de peso** usado como cociente de densidad energética. El dato medido de SR
        // Legacy para cordero picado es 282 → 283 kcal/100 g, es decir **0,996**: la densidad no
        // cambia prácticamente nada, porque lo que se pierde de grasa compensa lo que se pierde de
        // agua. Con 0,74, 180 g de cordero hecho se registraban en 686 kcal donde hay ~508.
        //
        // Un factor de 1,00 significa "pesarlo crudo o hecho da casi lo mismo", que es la conclusión
        // honesta para esta carne y no una renuncia a convertir.
        source = "FoodData Central SR Legacy: \"Lamb, ground, raw\" 282 kcal (174370) ÷ \"Lamb, ground, cooked, broiled\" 283 kcal (172544)",
        keywords = listOf("cordero")
    ),

    // ── Pescado y marisco ─────────────────────────────────────────────────────

    CookingYield(
        id = "gambas",
        factorProtein = 0.838,
        factorFat = 1.821,
        maxRawKcalPer100 = MAX_RAW_KCAL_FISH,
        label = "Gambas o langostinos",
        family = FAMILY_FISH,
        factor = 0.86,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "hervidos o a la plancha",
        source = "FoodData Central SR Legacy: \"Crustaceans, shrimp, raw\" 85 kcal (175179) ÷ \"Crustaceans, shrimp, cooked\" 99 kcal (175180)",
        keywords = listOf("gamba", "gambas", "langostino", "langostinos", "camaron", "camarones")
    ),
    CookingYield(
        id = "salmon",
        factorProtein = 0.924,
        factorFat = 1.087,
        maxRawKcalPer100 = MAX_RAW_KCAL_FISH,
        label = "Salmón",
        family = FAMILY_FISH,
        factor = 1.01,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "a la plancha o al horno",
        // El salmón **no se concentra al cocinarse**, y por eso necesita salir del factor genérico del
        // pescado. Va antes que `pescado` a propósito: el orden decide, y "salmon" está también entre
        // las palabras de aquél.
        //
        // El salmón que se vende en España es de piscifactoría, y ahí la USDA **midió** las dos
        // entradas: 208 kcal/100 g en crudo y 206 cocinado. Pierde grasa por goteo casi en la misma
        // proporción en que pierde agua, así que la densidad energética se queda igual. Aplicarle el
        // 0,78 del pescado blanco era el mayor error del catálogo de pescado: 150 g de salmón hecho se
        // registraban en 400 kcal donde hay 309 (**+29 %**) y en 25,8 g de grasa donde hay 18,5
        // (**+39 %**). Mismo patrón, medido, en el coho de piscifactoría (0,899).
        source = "FoodData Central SR Legacy: \"Fish, salmon, Atlantic, farmed, raw\" 208 kcal (175167) ÷ \"cooked, dry heat\" 206 kcal (175168). El coho de piscifactoría da 0,899 (173715→173716)",
        keywords = listOf("salmon"),
        excludes = listOf("ahumado", "ahumada", "noruego ahumado", "pate", "salvaje")
    ),
    CookingYield(
        id = "pescado",
        maxRawKcalPer100 = MAX_RAW_KCAL_FISH,
        label = "Pescado blanco y azul (salvo salmón)",
        family = FAMILY_FISH,
        factor = 0.78,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "a la plancha o al horno",
        // **Aviso sobre de dónde sale este 0,78, corregido el 2026-11-27.** Antes aquí se afirmaba que
        // "no es una simplificación cómoda: es lo que dice el dato", porque 45 de las 72 parejas de
        // pescado de SR Legacy dan exactamente 0,78. La cifra es cierta y el razonamiento estaba
        // invertido: al separar las parejas según si la USDA **midió** la entrada cocinada o la
        // **imputó**, se ve que en las de 0,78 los tres macros dan el mismo cociente a tres decimales
        // —bacalao 0,781/0,780/0,779, lubina 0,782/0,780/0,779, trucha 0,779/0,780/0,780—, que es la
        // huella de haber dividido todos los nutrientes por un único número. El dato no dice 0,78: el
        // dato **asume** 0,78.
        //
        // Donde la USDA sí midió el pescado cocinado, el rango real es 0,685 a 1,05. Se conserva el
        // 0,78 porque para pescado blanco magro las parejas medidas quedan cerca (eglefino 0,822,
        // fletán 0,820, abadejo de Alaska 0,685) y es lo mejor que hay; pero es una estimación de la
        // USDA, no una medición, y el salmón ya tiene entrada propia por quedar fuera de rango.
        //
        // Para pescado **hervido o al vapor** —como se hace a diario la merluza en España— no hay base
        // utilizable: en todo SR Legacy hay una sola pareja de calor húmedo emparejable, y también es
        // imputada.
        source = "FoodData Central SR Legacy: 0,78 es el valor que la USDA imputa al pescado cocinado en seco sin medición propia (45 de 72 parejas). Parejas con medición: eglefino 0,822 (171964→174198), fletán 0,820 (174200→174201), abadejo de Alaska 0,685 (173725→173681)",
        keywords = listOf(
            "merluza", "pescadilla", "bacalao", "atun", "bonito", "lubina", "dorada",
            "trucha", "tilapia", "panga", "perca", "mero", "rape", "lenguado", "caballa",
            "sardina", "sardinas", "boqueron", "boquerones", "emperador", "pez espada", "corvina",
            "rodaballo", "abadejo", "halibut", "fletan", "filete pescado", "lomo bacalao"
        ),
        // El atún y las sardinas se venden sobre todo en lata, cuyos datos ya son del producto
        // envasado. Las exclusiones globales cogen "lata", "conserva" y "escabeche"; estas cogen las
        // formas que quedan y que son muy frecuentes en el lineal español.
        excludes = listOf("aceite", "girasol", "oliva", "desmigado", "migas", "rallado", "desalado", "salazon")
    ),

    // ── Cereales y pasta ──────────────────────────────────────────────────────

    CookingYield(
        id = "arroz_integral",
        factorProtein = 2.752,
        factorCarbs = 2.981,
        factorFat = 3.299,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Arroz integral",
        family = FAMILY_GRAIN,
        factor = 2.98,
        rawLabel = "En seco",
        cookedLabel = "Cocido",
        method = "hervido en agua",
        source = "FoodData Central SR Legacy: \"Rice, brown, long-grain, raw\" 367 kcal (169703) ÷ \"cooked\" 123 kcal (169704)",
        keywords = listOf("arroz integral", "arroz basmati integral"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "arroz_vaporizado",
        factorProtein = 2.581,
        factorCarbs = 3.105,
        factorFat = 2.784,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Arroz vaporizado",
        family = FAMILY_GRAIN,
        factor = 3.04,
        rawLabel = "En seco",
        cookedLabel = "Cocido",
        method = "hervido en agua",
        // Entrada nueva (2026-11-27). El arroz vaporizado es líder de su categoría en España y
        // absorbe más agua que el blanco corriente: con 2,81 una ración de 250 g cocidos se infla en
        // +25 kcal (+8 %) y +6,8 g de hidratos (+11 %).
        //
        // Va antes que `arroz_blanco` porque el orden decide. Y nótese que la palabra "vaporizado"
        // estaba hasta hoy en las exclusiones globales, así que este alimento **no se podía
        // convertir en absoluto**: la entrada y esa retirada van juntas.
        source = "FoodData Central SR Legacy: \"Rice, white, long-grain, parboiled, unenriched, dry\" 374 kcal (169758) ÷ \"cooked\" 123 kcal (169759)",
        keywords = listOf("arroz vaporizado", "arroz sabroz", "arroz parboiled"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "arroz_blanco",
        factorProtein = 2.651,
        factorCarbs = 2.838,
        factorFat = 2.357,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Arroz blanco",
        family = FAMILY_GRAIN,
        factor = 2.81,
        rawLabel = "En seco",
        cookedLabel = "Cocido",
        method = "hervido en agua",
        source = "FoodData Central SR Legacy: \"Rice, white, long-grain, regular, raw, unenriched\" 365 kcal (169756) ÷ \"cooked without salt\" 130 kcal (169757)",
        keywords = listOf("arroz"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "pasta",
        factorProtein = 2.248,
        factorCarbs = 2.42,
        factorFat = 1.624,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Pasta",
        family = FAMILY_GRAIN,
        factor = 2.35,
        rawLabel = "En seco",
        cookedLabel = "Cocida",
        method = "hervida en agua",
        source = "FoodData Central SR Legacy: \"Pasta, dry, unenriched\" 371 kcal (168927) ÷ \"Pasta, cooked, unenriched, without added salt\" 158 kcal (168928)",
        keywords = listOf(
            "pasta", "macarrones", "espaguetis", "espagueti", "spaghetti", "fideos",
            "tallarines", "penne", "fusilli", "lazos", "helices", "tiburones", "plumas",
            "coditos", "farfalle", "tagliatelle", "noodles"
        ),
        excludes = listOf("dientes", "dental", "fresca", "fresco"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "quinoa",
        factorProtein = 3.209,
        factorCarbs = 3.012,
        factorFat = 3.161,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Quinoa",
        family = FAMILY_GRAIN,
        factor = 3.07,
        rawLabel = "En seco",
        cookedLabel = "Cocida",
        method = "hervida en agua",
        source = "FoodData Central SR Legacy: \"Quinoa, uncooked\" 368 kcal (168874) ÷ \"Quinoa, cooked\" 120 kcal (168917)",
        keywords = listOf("quinoa", "quinua"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "couscous",
        factorProtein = 3.367,
        factorCarbs = 3.335,
        factorFat = 4.0,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Cuscús",
        family = FAMILY_GRAIN,
        factor = 3.36,
        rawLabel = "En seco",
        cookedLabel = "Cocido",
        method = "hidratado en agua",
        source = "FoodData Central SR Legacy: \"Couscous, dry\" 376 kcal (169699) ÷ \"Couscous, cooked\" 112 kcal (169700)",
        keywords = listOf("cuscus", "couscous", "cous cous"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "bulgur",
        factorProtein = 3.99,
        factorCarbs = 4.083,
        factorFat = 5.542,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Bulgur",
        family = FAMILY_GRAIN,
        factor = 4.12,
        rawLabel = "En seco",
        cookedLabel = "Cocido",
        method = "hervido en agua",
        source = "FoodData Central SR Legacy: \"Bulgur, dry\" 342 kcal (170688) ÷ \"Bulgur, cooked\" 83 kcal (170287)",
        keywords = listOf("bulgur", "burgol"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "cebada",
        factorProtein = 4.385,
        factorCarbs = 2.754,
        factorFat = 2.636,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Cebada perlada",
        family = FAMILY_GRAIN,
        factor = 2.86,
        rawLabel = "En seco",
        cookedLabel = "Cocida",
        method = "hervida en agua",
        source = "FoodData Central SR Legacy: \"Barley, pearled, raw\" 352 kcal (170284) ÷ \"Barley, pearled, cooked\" 123 kcal (170285)",
        keywords = listOf("cebada", "cebada perlada"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),

    // ── Legumbres ─────────────────────────────────────────────────────────────

    CookingYield(
        id = "lentejas",
        factorProtein = 2.731,
        factorCarbs = 3.147,
        factorFat = 2.789,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Lentejas",
        family = FAMILY_LEGUME,
        factor = 3.03,
        rawLabel = "En seco",
        cookedLabel = "Cocidas",
        method = "hervidas en agua",
        source = "FoodData Central SR Legacy: \"Lentils, raw\" 352 kcal (172420) ÷ \"Lentils, mature seeds, cooked, boiled, without salt\" 116 kcal (172421)",
        keywords = listOf("lenteja", "lentejas"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "garbanzos",
        factorProtein = 2.31,
        factorCarbs = 2.296,
        factorFat = 2.332,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Garbanzos",
        family = FAMILY_LEGUME,
        factor = 2.30,
        rawLabel = "En seco",
        cookedLabel = "Cocidos",
        method = "hervidos en agua",
        source = "FoodData Central SR Legacy: \"Chickpeas, mature seeds, raw\" 378 kcal (173756) ÷ \"cooked, boiled\" 164 kcal (173799)",
        keywords = listOf("garbanzo", "garbanzos"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "alubia_blanca",
        factorProtein = 2.401,
        factorCarbs = 2.402,
        factorFat = 2.429,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Alubias blancas",
        family = FAMILY_LEGUME,
        factor = 2.40,
        rawLabel = "En seco",
        cookedLabel = "Cocidas",
        method = "hervidas en agua",
        source = "FoodData Central SR Legacy: \"Beans, white, mature seeds, raw\" 333 kcal (175202) ÷ \"cooked, boiled, without salt\" 139 kcal (175203)",
        keywords = listOf("alubia blanca", "alubias blancas", "judia blanca", "judias blancas", "fabada seca", "pocha", "pochas"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "alubia_roja",
        factorProtein = 2.72,
        factorCarbs = 2.632,
        factorFat = 1.66,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Alubias rojas",
        family = FAMILY_LEGUME,
        factor = 2.62,
        rawLabel = "En seco",
        cookedLabel = "Cocidas",
        method = "hervidas en agua",
        source = "FoodData Central SR Legacy: \"Beans, kidney, all types, mature seeds, raw\" 333 kcal (175193) ÷ \"cooked, boiled, without salt\" 127 kcal (173740)",
        keywords = listOf("alubia roja", "alubias rojas", "judia roja", "judias rojas", "frijol rojo", "kidney"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "alubia_negra",
        factorProtein = 2.438,
        factorCarbs = 2.63,
        factorFat = 2.63,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Alubias negras",
        family = FAMILY_LEGUME,
        factor = 2.58,
        rawLabel = "En seco",
        cookedLabel = "Cocidas",
        method = "hervidas en agua",
        source = "FoodData Central SR Legacy: \"Beans, black, mature seeds, raw\" 341 kcal (173734) ÷ \"cooked, boiled, without salt\" 132 kcal (173735)",
        keywords = listOf("alubia negra", "alubias negras", "frijol negro", "frijoles negros", "judia negra"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),
    CookingYield(
        id = "habas",
        factorProtein = 3.437,
        factorCarbs = 2.966,
        factorFat = 3.825,
        maxRawKcalPer100 = MAX_RAW_KCAL_DRY,
        label = "Habas secas",
        family = FAMILY_LEGUME,
        factor = 3.10,
        rawLabel = "En seco",
        cookedLabel = "Cocidas",
        method = "hervidas en agua",
        source = "FoodData Central SR Legacy: \"Broadbeans (fava beans), mature seeds, raw\" 341 kcal (175205) ÷ \"cooked, boiled, without salt\" 110 kcal (173753)",
        keywords = listOf("haba", "habas"),
        minRawKcalPer100 = DRY_FLOOR_KCAL,
        defaultBasis = WeighBasis.COOKED
    ),

    // ── Tubérculos ────────────────────────────────────────────────────────────

    CookingYield(
        id = "patata",
        factorProtein = 1.096,
        factorCarbs = 0.869,
        factorFat = 0.9,
        maxRawKcalPer100 = MAX_RAW_KCAL_TUBER,
        label = "Patata",
        family = FAMILY_TUBER,
        factor = 0.89,
        rawLabel = "Cruda",
        cookedLabel = "Cocida",
        method = "hervida",
        // Corregido el 2026-11-27: antes 0,75, el rendimiento **al horno**.
        //
        // El de la patata es el abanico más amplio del catálogo, un 26 % de recorrido según el método
        // (todo de SR Legacy, partiendo de 77 kcal/100 g en crudo):
        //
        //   hervida sin piel 0,92 (170440) · hervida con piel 0,89 (170438)
        //   al horno 0,83 (170093) · al horno, variedad blanca 0,75 (170028→170434)
        //   al microondas con piel 0,73 (170094)
        //
        // Se elige el de la hervida porque en España se hierve mucho más de lo que se hornea, y
        // porque el error que corrige es el más grande: con 0,75, una ración de 250 g de patata
        // cocida se registraba en 257 kcal donde hay 215 (**+19 %**) y con 8,3 g de hidratos de más.
        // Quien pese patata asada se queda ahora un 16 % corto, que es menos daño y menos frecuente.
        //
        // **Por qué no hay dos entradas, una por método.** Se intentó, y no puede funcionar: las
        // palabras "hervida", "cocida" y "vapor" están en las exclusiones globales —correctamente,
        // porque un producto que las lleva en el nombre ya tiene sus datos en base cocida—, así que
        // una entrada con esas palabras clave no se encontraría nunca. Y elegirla a mano tampoco es
        // posible: esa interfaz no existe todavía. Separar por método exige antes ese selector.
        source = "FoodData Central SR Legacy: \"Potatoes, flesh and skin, raw\" 77 kcal (170026) ÷ \"Potatoes, boiled, cooked in skin, flesh, without salt\" 87 kcal (170438). Banda real por método: 0,73 (microondas) a 0,92 (hervida sin piel)",
        keywords = listOf("patata", "patatas")
    )
)

/** El catálogo agrupado por familia, en el orden en que se presenta al usuario. */
val COOKING_YIELD_FAMILIES: List<Pair<String, List<CookingYield>>> =
    listOf(FAMILY_MEAT, FAMILY_FISH, FAMILY_GRAIN, FAMILY_LEGUME, FAMILY_TUBER)
        .map { family -> family to COOKING_YIELDS.filter { it.family == family } }

// ── Búsqueda y conversión ─────────────────────────────────────────────────────

/**
 * Normaliza un nombre de producto para poder comparar palabras: minúsculas, sin tildes y con cualquier
 * cosa que no sea letra o número convertida en espacio. Queda rodeado de espacios para poder buscar
 * palabras completas (" pollo " no coincide con "repollo").
 *
 * La normalización se hace a mano porque `java.text.Normalizer` no existe en `commonMain`.
 */
internal fun normalizeFoodName(name: String): String {
    val sb = StringBuilder(name.length + 2)
    sb.append(' ')
    for (raw in name.lowercase()) {
        val c = when (raw) {
            'á', 'à', 'ä', 'â', 'ã' -> 'a'
            'é', 'è', 'ë', 'ê' -> 'e'
            'í', 'ì', 'ï', 'î' -> 'i'
            'ó', 'ò', 'ö', 'ô', 'õ' -> 'o'
            'ú', 'ù', 'ü', 'û' -> 'u'
            'ñ' -> 'n'
            'ç' -> 'c'
            else -> raw
        }
        if (c.isLetterOrDigit()) sb.append(c) else if (sb.last() != ' ') sb.append(' ')
    }
    if (sb.last() != ' ') sb.append(' ')
    return sb.toString()
}

/**
 * Formas con las que una palabra puede casar con su singular o su plural.
 *
 * **Por qué hace falta aquí.** La comparación es por palabra completa, y el KDoc de [GLOBAL_EXCLUDES]
 * prometía que por eso estaban "las cuatro formas de cada adjetivo (masculino/femenino,
 * singular/plural)". No estaban: de 134 entradas, **51 no tenían plural**. Y las que faltaban no eran
 * inocuas — `relleno` y `rellena` estaban sin `rellenos`/`rellenas`, que es exactamente el fallo que
 * el comentario decía evitar: `"Pimientos rellenos de carne picada"` pasaba el filtro y se convertía
 * como si fuera carne picada cruda. Igual `pasta.excludes`, con `fresca`/`fresco` pero sin sus
 * plurales, que dejaba pasar `"Tagliatelle frescos al huevo"`.
 *
 * Plegar el número en la comparación lo arregla de una vez y para siempre, en vez de añadir 51
 * palabras y volver a quedarse corto a la siguiente — que es la conclusión a la que el propio §13.6
 * del fundamento científico llegaba al admitir que "nunca se acaban las palabras".
 *
 * **Por qué un conjunto y no una forma canónica.** No hay una sola regla que sirva: quitar la "s"
 * resuelve "tomates"→"tomate" pero no "yogures"→"yogur", y quitar "es" resuelve el segundo pero
 * convierte "tomates" en "tomat". Así que se generan las variantes plausibles y dos palabras casan si
 * comparten alguna. No es un lematizador: cubre la formación regular del plural español.
 *
 * El mínimo de longitud evita plegar palabras cortas donde la "s" es parte de la raíz ("gas", "mes").
 */
internal fun pluralStems(word: String): Set<String> {
    if (word.length < 4 || !word.endsWith("s")) return setOf(word)
    val stems = mutableSetOf(word, word.dropLast(1))          // platanos → platano, tomates → tomate
    if (word.length >= 5 && word.endsWith("es")) {
        stems.add(word.dropLast(2))                           // yogures → yogur, flores → flor
        if (word.endsWith("ces")) stems.add(word.dropLast(3) + "z")  // nueces → nuez, peces → pez
    }
    return stems
}

/** true si las dos palabras son la misma salvo por el número gramatical. */
internal fun mismaPalabra(a: String, b: String): Boolean =
    a == b || pluralStems(a).any { it in pluralStems(b) }

/**
 * true si todas las palabras de [phrase] aparecen como palabra completa en [normalized], **tolerando
 * singular y plural** (ver [pluralStems]).
 */
internal fun phraseMatches(normalized: String, phrase: String): Boolean {
    val palabras = normalized.split(' ').filter { it.isNotEmpty() }
    return normalizeFoodName(phrase).split(' ')
        .filter { it.isNotEmpty() }
        .all { termino -> palabras.any { mismaPalabra(it, termino) } }
}

/**
 * true si el nombre del producto indica que ya viene cocinado o que es un derivado o un preparado, en
 * cuyo caso no se le puede aplicar ningún factor, ni automático ni elegido a mano.
 */
fun isExcludedFromCooking(productName: String?): Boolean {
    val name = productName?.takeIf { it.isNotBlank() } ?: return false
    val normalized = normalizeFoodName(name)
    return GLOBAL_EXCLUDES.any { phraseMatches(normalized, it) }
}

/**
 * Devuelve el factor aplicable al producto llamado [productName], o `null` si no se reconoce o si el
 * nombre indica que ya viene cocinado o procesado. `null` significa "no aplicar nada automáticamente",
 * nunca "aplicar 1,0": no es lo mismo no saber que saber que no cambia.
 */
fun findCookingYield(productName: String?): CookingYield? {
    val name = productName?.takeIf { it.isNotBlank() } ?: return null
    if (isExcludedFromCooking(name)) return null
    val normalized = normalizeFoodName(name)
    return COOKING_YIELDS.firstOrNull { cookingYield ->
        cookingYield.keywords.any { phraseMatches(normalized, it) } &&
            cookingYield.excludes.none { phraseMatches(normalized, it) }
    }
}

/**
 * true si las kcal del producto son imposibles para este alimento en base cruda, es decir, si el
 * producto **ya viene cocinado** aunque su nombre no lo diga.
 *
 * Esta es la comprobación que atrapa lo que el nombre no atrapa: el vasito de arroz de microondas
 * llamado solo "Arroz basmati" (158 kcal/100 g) o los garbanzos de tarro llamados solo "Garbanzos"
 * (~140). Se verificó que la lista de exclusiones por nombre no puede cubrir estos casos — siempre
 * falta una palabra ("prefritas", "tarro", "vasito", "vaporizado"…) — mientras que el número no
 * engaña.
 *
 * Cuando salta, la conversión se bloquea y se avisa; **nunca se corrige el número en silencio**. Y
 * cuando no hay suelo definido ([CookingYield.minRawKcalPer100] nulo) devuelve false: no saber no es
 * lo mismo que saber que está bien, pero bloquear sin fundamento sería peor que no bloquear.
 */
fun CookingYield.looksAlreadyCooked(productKcalPer100: Double): Boolean {
    if (productKcalPer100 <= 0.0) return false
    minRawKcalPer100?.let { if (productKcalPer100 < it) return true }
    // La comprobación por arriba: un producto demasiado calórico para estar crudo está frito, curado,
    // desecado o es un snack. Donde no hay suelo defendible sí puede haber techo — ver los constantes
    // MAX_RAW_KCAL_*, que es donde está explicado por qué esa asimetría no es arbitraria.
    maxRawKcalPer100?.let { if (productKcalPer100 > it) return true }
    return false
}

/** Busca un alimento del catálogo por su [CookingYield.id]. Para recuperar el que se eligió a mano. */
fun cookingYieldById(id: String?): CookingYield? =
    id?.let { wanted -> COOKING_YIELDS.firstOrNull { it.id == wanted } }

/**
 * Convierte un valor por 100 g desde la base cruda (la de los datos de origen) a la base [basis].
 *
 * Dividir por el factor es lo correcto, no multiplicar: si 100 g de crudo se quedan en 76 g al
 * cocinarse, esos 76 g siguen conteniendo las mismas kcal, así que por cada 100 g de cocinado hay
 * más kcal que por cada 100 g de crudo.
 */
fun CookingYield.per100For(
    valuePer100Raw: Double,
    basis: WeighBasis,
    macro: Macro = Macro.ENERGY
): Double = when (basis) {
    WeighBasis.RAW -> valuePer100Raw
    WeighBasis.COOKED -> valuePer100Raw / factorFor(macro)
}

/**
 * Peso en crudo equivalente a [grams] pesados en la base [basis]. Sirve para la interfaz ("150 g
 * cocido ≈ 197 g en crudo"), que es la referencia con la que el usuario compra y congela.
 */
fun CookingYield.rawWeightFor(grams: Double, basis: WeighBasis): Double = when (basis) {
    WeighBasis.RAW -> grams
    WeighBasis.COOKED -> grams / factor
}
