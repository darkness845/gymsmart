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
    val excludes: List<String> = emptyList()
)

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
 * Hacen falta las cuatro formas de cada adjetivo (masculino/femenino, singular/plural) porque la
 * comparación es por palabra completa: "lentejas cocidas" no coincide con "cocido".
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
    "conserva", "lata", "bote", "precocinado", "preparado", "caldo", "sopa", "crema", "pure",
    "salsa", "ensaladilla", "paella", "lasana", "pizza", "croqueta", "croquetas", "albondiga",
    "albondigas", "empanado", "empanada", "empanados", "empanadas",
    "rebozado", "rebozada", "rebozados", "rebozadas",
    "nuggets", "fingers", "san jacobo", "canelones", "escabeche", "salmuera", "relleno", "rellena",
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
        label = "Muslo o contramuslo de pollo",
        family = FAMILY_MEAT,
        factor = 0.69,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "al horno",
        source = "FoodData Central SR Legacy: \"Chicken, broilers or fryers, dark meat, thigh, meat only, raw\" 121 kcal (173627) ÷ \"cooked, roasted\" 176 kcal (172853)",
        keywords = listOf("muslo pollo", "muslos pollo", "contramuslo", "contramuslos", "jamoncito pollo"),
        excludes = listOf("pavo")
    ),
    CookingYield(
        id = "pollo_pechuga",
        label = "Pechuga de pollo",
        family = FAMILY_MEAT,
        factor = 0.76,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "al horno",
        source = "FoodData Central SR Legacy: \"Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw\" 120 kcal (171077) ÷ \"cooked, roasted\" 157 kcal (171140)",
        keywords = listOf("pechuga pollo", "pollo pechuga", "filete pollo", "filetes pollo", "pollo fileteado"),
        excludes = listOf("pavo")
    ),
    CookingYield(
        id = "pavo_pechuga",
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
        label = "Carne picada de ternera",
        family = FAMILY_MEAT,
        factor = 0.73,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "a la plancha",
        source = "USDA Table of Cooking Yields for Meat and Poultry (ARS, 2012): \"Beef, ground, low fat (<12%), patty\", broiled or grilled — 73% (n=40)",
        keywords = listOf("carne picada", "ternera picada", "vacuno picada", "picada vacuno", "picada mixta"),
        excludes = listOf("pavo", "pollo")
    ),
    CookingYield(
        id = "ternera_filete",
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
        label = "Cordero",
        family = FAMILY_MEAT,
        factor = 0.74,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "al horno",
        source = "USDA Table of Cooking Yields for Meat and Poultry (ARS, 2012): \"Lamb, retail cuts, roasts, all samples\", baked or roasted — 74% (rango 53–88%)",
        keywords = listOf("cordero")
    ),

    // ── Pescado y marisco ─────────────────────────────────────────────────────

    CookingYield(
        id = "gambas",
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
        id = "pescado",
        label = "Pescado",
        family = FAMILY_FISH,
        factor = 0.78,
        rawLabel = "Crudo",
        cookedLabel = "Cocinado",
        method = "a la plancha o al horno",
        // Un solo factor para todo el pescado no es una simplificación cómoda: es lo que dice el dato.
        // De las 72 parejas crudo/cocinado-en-seco de pescado que hay en SR Legacy, 45 dan exactamente
        // 0,78, en especies sin relación entre sí (bacalao 82→105, atún 144→184, merluza 90→116,
        // salmón salvaje 142→182). La USDA aplica ese rendimiento de forma uniforme al pescado.
        source = "FoodData Central SR Legacy: 0,78 en 45 de las 72 parejas de pescado cocinado en seco (p. ej. bacalao 171955→171956, atún 173706→173707, merluza 173713→175161, salmón 173686→171998)",
        keywords = listOf(
            "merluza", "pescadilla", "bacalao", "atun", "bonito", "salmon", "lubina", "dorada",
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
        label = "Arroz integral",
        family = FAMILY_GRAIN,
        factor = 2.98,
        rawLabel = "En seco",
        cookedLabel = "Cocido",
        method = "hervido en agua",
        source = "FoodData Central SR Legacy: \"Rice, brown, long-grain, raw\" 367 kcal (169703) ÷ \"cooked\" 123 kcal (169704)",
        keywords = listOf("arroz integral", "arroz basmati integral")
    ),
    CookingYield(
        id = "arroz_blanco",
        label = "Arroz blanco",
        family = FAMILY_GRAIN,
        factor = 2.81,
        rawLabel = "En seco",
        cookedLabel = "Cocido",
        method = "hervido en agua",
        source = "FoodData Central SR Legacy: \"Rice, white, long-grain, regular, raw, unenriched\" 365 kcal (169756) ÷ \"cooked without salt\" 130 kcal (169757)",
        keywords = listOf("arroz")
    ),
    CookingYield(
        id = "pasta",
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
        excludes = listOf("dientes", "dental", "fresca", "fresco")
    ),
    CookingYield(
        id = "quinoa",
        label = "Quinoa",
        family = FAMILY_GRAIN,
        factor = 3.07,
        rawLabel = "En seco",
        cookedLabel = "Cocida",
        method = "hervida en agua",
        source = "FoodData Central SR Legacy: \"Quinoa, uncooked\" 368 kcal (168874) ÷ \"Quinoa, cooked\" 120 kcal (168917)",
        keywords = listOf("quinoa", "quinua")
    ),
    CookingYield(
        id = "couscous",
        label = "Cuscús",
        family = FAMILY_GRAIN,
        factor = 3.36,
        rawLabel = "En seco",
        cookedLabel = "Cocido",
        method = "hidratado en agua",
        source = "FoodData Central SR Legacy: \"Couscous, dry\" 376 kcal (169699) ÷ \"Couscous, cooked\" 112 kcal (169700)",
        keywords = listOf("cuscus", "couscous", "cous cous")
    ),
    CookingYield(
        id = "bulgur",
        label = "Bulgur",
        family = FAMILY_GRAIN,
        factor = 4.12,
        rawLabel = "En seco",
        cookedLabel = "Cocido",
        method = "hervido en agua",
        source = "FoodData Central SR Legacy: \"Bulgur, dry\" 342 kcal (170688) ÷ \"Bulgur, cooked\" 83 kcal (170287)",
        keywords = listOf("bulgur", "burgol")
    ),
    CookingYield(
        id = "cebada",
        label = "Cebada perlada",
        family = FAMILY_GRAIN,
        factor = 2.86,
        rawLabel = "En seco",
        cookedLabel = "Cocida",
        method = "hervida en agua",
        source = "FoodData Central SR Legacy: \"Barley, pearled, raw\" 352 kcal (170284) ÷ \"Barley, pearled, cooked\" 123 kcal (170285)",
        keywords = listOf("cebada", "cebada perlada")
    ),

    // ── Legumbres ─────────────────────────────────────────────────────────────

    CookingYield(
        id = "lentejas",
        label = "Lentejas",
        family = FAMILY_LEGUME,
        factor = 3.03,
        rawLabel = "En seco",
        cookedLabel = "Cocidas",
        method = "hervidas en agua",
        source = "FoodData Central SR Legacy: \"Lentils, raw\" 352 kcal (172420) ÷ \"Lentils, mature seeds, cooked, boiled, without salt\" 116 kcal (172421)",
        keywords = listOf("lenteja", "lentejas")
    ),
    CookingYield(
        id = "garbanzos",
        label = "Garbanzos",
        family = FAMILY_LEGUME,
        factor = 2.30,
        rawLabel = "En seco",
        cookedLabel = "Cocidos",
        method = "hervidos en agua",
        source = "FoodData Central SR Legacy: \"Chickpeas, mature seeds, raw\" 378 kcal (173756) ÷ \"cooked, boiled\" 164 kcal (173799)",
        keywords = listOf("garbanzo", "garbanzos")
    ),
    CookingYield(
        id = "alubia_blanca",
        label = "Alubias blancas",
        family = FAMILY_LEGUME,
        factor = 2.40,
        rawLabel = "En seco",
        cookedLabel = "Cocidas",
        method = "hervidas en agua",
        source = "FoodData Central SR Legacy: \"Beans, white, mature seeds, raw\" 333 kcal (175202) ÷ \"cooked, boiled, without salt\" 139 kcal (175203)",
        keywords = listOf("alubia blanca", "alubias blancas", "judia blanca", "judias blancas", "fabada seca", "pocha", "pochas")
    ),
    CookingYield(
        id = "alubia_roja",
        label = "Alubias rojas",
        family = FAMILY_LEGUME,
        factor = 2.62,
        rawLabel = "En seco",
        cookedLabel = "Cocidas",
        method = "hervidas en agua",
        source = "FoodData Central SR Legacy: \"Beans, kidney, all types, mature seeds, raw\" 333 kcal (175193) ÷ \"cooked, boiled, without salt\" 127 kcal (173740)",
        keywords = listOf("alubia roja", "alubias rojas", "judia roja", "judias rojas", "frijol rojo", "kidney")
    ),
    CookingYield(
        id = "alubia_negra",
        label = "Alubias negras",
        family = FAMILY_LEGUME,
        factor = 2.58,
        rawLabel = "En seco",
        cookedLabel = "Cocidas",
        method = "hervidas en agua",
        source = "FoodData Central SR Legacy: \"Beans, black, mature seeds, raw\" 341 kcal (173734) ÷ \"cooked, boiled, without salt\" 132 kcal (173735)",
        keywords = listOf("alubia negra", "alubias negras", "frijol negro", "frijoles negros", "judia negra")
    ),
    CookingYield(
        id = "habas",
        label = "Habas secas",
        family = FAMILY_LEGUME,
        factor = 3.10,
        rawLabel = "En seco",
        cookedLabel = "Cocidas",
        method = "hervidas en agua",
        source = "FoodData Central SR Legacy: \"Broadbeans (fava beans), mature seeds, raw\" 341 kcal (175205) ÷ \"cooked, boiled, without salt\" 110 kcal (173753)",
        keywords = listOf("haba", "habas")
    ),

    // ── Tubérculos ────────────────────────────────────────────────────────────

    CookingYield(
        id = "patata",
        label = "Patata",
        family = FAMILY_TUBER,
        factor = 0.75,
        rawLabel = "Cruda",
        cookedLabel = "Cocinada",
        method = "al horno, con piel",
        source = "FoodData Central SR Legacy: \"Potatoes, white, flesh and skin, raw\" 69 kcal (170028) ÷ \"baked\" 92 kcal (170434)",
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

/** true si todas las palabras de [phrase] aparecen como palabra completa en [normalized]. */
private fun phraseMatches(normalized: String, phrase: String): Boolean =
    normalizeFoodName(phrase).split(' ')
        .filter { it.isNotEmpty() }
        .all { normalized.contains(" $it ") }

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
fun CookingYield.per100For(valuePer100Raw: Double, basis: WeighBasis): Double = when (basis) {
    WeighBasis.RAW -> valuePer100Raw
    WeighBasis.COOKED -> valuePer100Raw / factor
}

/**
 * Peso en crudo equivalente a [grams] pesados en la base [basis]. Sirve para la interfaz ("150 g
 * cocido ≈ 197 g en crudo"), que es la referencia con la que el usuario compra y congela.
 */
fun CookingYield.rawWeightFor(grams: Double, basis: WeighBasis): Double = when (basis) {
    WeighBasis.RAW -> grams
    WeighBasis.COOKED -> grams / factor
}
