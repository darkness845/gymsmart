package com.gymsmart.gymsmart.nutrition

/**
 * Icono de cada alimento, para poder distinguirlos de un vistazo en las listas.
 *
 * **Por qué existe.** Una lista de comida es una pared de texto: quince filas con el mismo aspecto en
 * las que hay que leer el nombre entero para saber qué es cada cosa. Un icono delante se reconoce
 * antes de leer, que es justo lo que hace falta cuando se registra una comida con el móvil en una
 * mano. Es la diferencia visual más barata que se puede conseguir.
 *
 * **Por qué emoji y no iconos dibujados.** Pesan cero (no hay assets, ni descargas, ni licencias),
 * funcionan igual en Android, iOS, escritorio y web sin escribir nada por plataforma, y es la
 * convención que la app ya usa en los tipos de ejercicio ([com.gymsmart.gymsmart.model.WorkoutKind])
 * y en los tipos de comida. Un juego de ~25 iconos vectoriales se vería algo mejor y costaría
 * dibujarlos, mantenerlos en cuatro densidades y revisar que se vean en tema claro y oscuro.
 *
 * **Por qué una sola tabla para genéricos y marcas.** Los 90 alimentos de [GENERIC_FOODS] tienen el
 * nombre curado a mano, así que podrían llevar su icono escrito al lado; los productos de Open Food
 * Facts no, porque llegan de una API con nombres libres. Tener dos mecanismos sería tener dos sitios
 * donde el pollo puede dejar de ser pollo. Aquí hay uno solo, y [FoodIconsTest] fija que **los 90
 * genéricos resuelven a un icono concreto**: la curación está, pero vive en el test, no duplicada.
 *
 * **Cómo se busca.** Igual que el catálogo de cocción: palabra completa sobre el nombre normalizado
 * (minúsculas y sin tildes), reutilizando [normalizeFoodName] y [phraseMatches]. Esto no es un
 * detalle: comparando por subcadena, **"repollo" contendría "pollo"** y una verdura saldría con un
 * muslo al lado. Esa trampa ya mordió una vez en [CookingYield] y está fijada por test en los dos
 * sitios.
 *
 * **El orden de la tabla importa.** Gana la primera regla que coincide, así que lo que identifica al
 * alimento va antes que cómo está preparado: "crema de cacahuete" tiene que ser un fruto seco y no
 * una crema, y "patatas fritas" un snack y no un tubérculo. Cada caso de esos está comentado donde
 * toca y comprobado en el test.
 *
 * **Nota sobre compatibilidad.** Tres iconos son de Unicode 13/14 (🫒 aceite, 🫘 legumbres,
 * 🫑 pimiento) y necesitan Android 12L o superior; se han usado solo donde no hay alternativa clásica
 * razonable. El resto son de Unicode 12 o anterior y se ven en cualquier dispositivo. Si alguno sale
 * como un cuadrado, se cambia aquí y afecta a toda la app.
 */

/** Lo que se pinta cuando el alimento no se reconoce. Nunca se devuelve nulo: la fila no debe bailar. */
const val FOOD_ICON_DEFAULT = "🍽" // 🍽

/**
 * Una regla de la tabla.
 *
 * [keywords] se busca **por palabra completa**, que es el modo seguro y el que usa todo lo demas:
 * asi "pollo" no aparece dentro de "repollo".
 *
 * [stems] se busca **por fragmento**, y existe solo porque las marcas de suplementos pegan la
 * palabra clave al nombre comercial: "Evowhey", "Isowhey", "Whey Protein 100%". Por palabra completa
 * ninguno de esos tres se reconoce. Es una excepcion peligrosa — buscar "cola" por fragmento
 * convertiria la coliflor en un refresco — asi que se usa **unicamente** con raices inglesas que no
 * pueden aparecer dentro de una palabra espanola, y solo en la regla de suplementos.
 */
private class IconRule(
    val icon: String,
    val keywords: List<String> = emptyList(),
    val stems: List<String> = emptyList()
)

/**
 * La tabla, en orden de prioridad. Gana la primera coincidencia.
 *
 * Los bloques están ordenados de lo más específico a lo más genérico:
 * suplementos → carnes → pescado → huevos → lácteos → cereales → legumbres → verduras → tubérculos →
 * frutas → frutos secos → grasas → dulces → bebidas → preparaciones → conservas y condimentos.
 */
private val ICON_RULES: List<IconRule> = listOf(

    // ── Suplementos ───────────────────────────────────────────────────────────
    // Primero del todo a propósito: "batido de proteínas" tiene que ser un suplemento y no un lácteo,
    // y "barrita proteica" un suplemento y no un dulce.
    IconRule("💪", // 💪
        keywords = listOf(
            "suplemento", "suplementos", "concentrado", "aminoacidos", "bcaa", "gainer", "scoop"),
        // Por fragmento: asi "Evowhey 2.0" y "Whey Protein" se reconocen igual que "proteina".
        // Ninguna de estas raices existe dentro de una palabra espanola.
        stems = listOf("whey", "protein", "proteic", "casein", "isolat", "creatin")),

    // ── Carne ─────────────────────────────────────────────────────────────────
    // Preparados antes que la pieza: una hamburguesa no es un filete.
    IconRule("🍔", keywords = listOf("hamburguesa", "burger")),                       // 🍔
    IconRule("🌭", keywords = listOf("salchicha", "salchichas", "frankfurt", "perrito")), // 🌭
    IconRule("🥓", keywords = listOf("bacon", "beicon", "panceta")),                  // 🥓
    IconRule("🍗", keywords = listOf(// 🍗
        "pollo", "pavo", "pechuga", "muslo", "muslos", "contramuslo", "alitas", "alita")),
    IconRule("🍖", keywords = listOf(// 🍖
        "jamon", "chorizo", "salchichon", "lomo embuchado", "embutido", "fuet", "salami",
        "mortadela", "cecina", "costilla", "costillas", "cordero", "chuleta", "chuletas")),
    IconRule("🥩", keywords = listOf(// 🥩
        "ternera", "vacuno", "buey", "solomillo", "entrecot", "cerdo", "lomo", "conejo",
        "carne picada", "carne", "filete", "secreto", "presa iberica")),

    // ── Pescado y marisco ─────────────────────────────────────────────────────
    // Antes que las conservas: "atún en lata" es pescado, no una lata.
    IconRule("🍣", keywords = listOf("sushi", "sashimi", "maki")),                    // 🍣
    IconRule("🦐", keywords = listOf(// 🦐
        "gamba", "gambas", "langostino", "langostinos", "cigala", "cigalas", "cangrejo",
        "marisco", "surimi")),
    IconRule("🦑", keywords = listOf("calamar", "calamares", "pulpo", "sepia", "chipirones")), // 🦑
    IconRule("🦪", keywords = listOf(// 🦪
        "mejillon", "mejillones", "almeja", "almejas", "berberechos", "ostra", "ostras", "vieira")),
    IconRule("🐟", keywords = listOf(// 🐟
        "pescado", "merluza", "bacalao", "atun", "bonito", "salmon", "lubina", "dorada", "trucha",
        "sardina", "sardinas", "caballa", "boqueron", "boquerones", "anchoa", "anchoas", "rape",
        "lenguado", "panga", "tilapia", "emperador", "pez espada")),

    // ── Huevos ────────────────────────────────────────────────────────────────
    // "tortilla" va aquí porque en España es de huevo; la mexicana es el caso raro.
    IconRule("🥚", keywords = listOf("huevo", "huevos", "clara", "claras", "yema", "yemas", "tortilla")), // 🥚

    // ── Lácteos ───────────────────────────────────────────────────────────────
    IconRule("🧀", keywords = listOf(// 🧀
        "queso", "quesos", "mozzarella", "requeson", "parmesano", "cheddar", "burgos", "feta",
        "manchego", "gouda", "emmental", "ricotta")),
    IconRule("🥛", keywords = listOf(// 🥛
        "leche", "yogur", "yogures", "kefir", "nata", "cuajada", "batido")),

    // ── Cereales, pan y pasta ─────────────────────────────────────────────────
    // Antes que las verduras, para que "pan de ajo" sea pan.
    IconRule("🍕", keywords = listOf("pizza")),                                       // 🍕
    IconRule("🥞", keywords = listOf("tortita", "tortitas", "pancake", "pancakes", "crepe", "crepes", "gofre")), // 🥞
    IconRule("🍚", keywords = listOf("arroz", "risotto", "paella")),                  // 🍚
    IconRule("🍝", keywords = listOf(// 🍝
        "pasta", "espagueti", "espaguetis", "macarrones", "tallarines", "fideos", "lasana",
        "noodles", "ramen", "raviolis", "canelones")),
    IconRule("🍞", keywords = listOf(// 🍞
        "pan", "panecillo", "tostada", "tostadas", "biscote", "picos", "baguette", "chapata",
        "molde", "bollo")),
    IconRule("🌾", keywords = listOf(// 🌾
        "avena", "cereal", "cereales", "muesli", "granola", "quinoa", "cuscus", "bulgur", "cebada",
        "centeno", "espelta", "trigo", "harina", "salvado", "semola", "mijo", "maicena")),

    // ── Legumbres ─────────────────────────────────────────────────────────────
    // "judias" a secas no está aquí: sin el apellido, "judías verdes" caería en legumbre y es verdura.
    IconRule("🫘", keywords = listOf(// 🫘
        "lenteja", "lentejas", "garbanzo", "garbanzos", "alubia", "alubias", "judias blancas",
        "judias pintas", "haba", "habas", "soja", "tofu", "tempeh", "edamame", "hummus")),

    // ── Verduras ──────────────────────────────────────────────────────────────
    IconRule("🍅", keywords = listOf("tomate", "tomates", "cherry")),                 // 🍅
    IconRule("🥕", keywords = listOf("zanahoria", "zanahorias")),                     // 🥕
    IconRule("🍆", keywords = listOf("berenjena", "berenjenas")),                     // 🍆
    IconRule("🥒", keywords = listOf("pepino", "pepinos", "calabacin", "calabacines")), // 🥒
    IconRule("🍄", keywords = listOf("champinon", "champinones", "seta", "setas", "boletus", "shiitake")), // 🍄
    IconRule("🌽", keywords = listOf("maiz")),                                        // 🌽
    IconRule("🧅", keywords = listOf("cebolla", "cebollas", "cebolleta", "ajo", "ajos", "puerro", "puerros")), // 🧅
    IconRule("🫑", keywords = listOf("pimiento", "pimientos", "guindilla")),          // 🫑
    IconRule("🥦", keywords = listOf(// 🥦
        "brocoli", "coliflor", "espinaca", "espinacas", "lechuga", "acelga", "acelgas", "col",
        "repollo", "judias verdes", "esparrago", "esparragos", "guisante", "guisantes",
        "alcachofa", "alcachofas", "calabaza", "apio", "rucula", "canonigos", "berza", "kale",
        "verdura", "verduras", "menestra", "pisto")),

    // ── Tubérculos ────────────────────────────────────────────────────────────
    // "patatas fritas" antes que "patata": es un snack, no un tubérculo.
    IconRule("🍟", keywords = listOf("patatas fritas", "patatas chips", "chips", "snack", "nachos")), // 🍟
    IconRule("🥔", keywords = listOf("patata", "patatas", "boniato", "batata", "yuca")),              // 🥔

    // ── Frutas ────────────────────────────────────────────────────────────────
    IconRule("🥑", keywords = listOf("aguacate", "aguacates", "guacamole")),          // 🥑
    IconRule("🍌", keywords = listOf("platano", "platanos", "banana")),               // 🍌
    IconRule("🍎", keywords = listOf("manzana", "manzanas")),                         // 🍎
    IconRule("🍐", keywords = listOf("pera", "peras")),                               // 🍐
    IconRule("🍊", keywords = listOf("naranja", "naranjas", "mandarina", "mandarinas", "clementina")), // 🍊
    IconRule("🍋", keywords = listOf("limon", "limones", "lima")),                    // 🍋
    IconRule("🍓", keywords = listOf("fresa", "fresas", "fresones")),                 // 🍓
    IconRule("🍒", keywords = listOf("arandano", "arandanos", "frambuesa", "frambuesas", "mora", "moras", "cereza", "cerezas")), // 🍒
    IconRule("🍇", keywords = listOf("uva", "uvas", "pasas")),                        // 🍇
    IconRule("🍉", keywords = listOf("sandia")),                                      // 🍉
    IconRule("🍈", keywords = listOf("melon")),                                       // 🍈
    IconRule("🥝", keywords = listOf("kiwi", "kiwis")),                               // 🥝
    IconRule("🍍", keywords = listOf("pina")),                                        // 🍍
    IconRule("🍑", keywords = listOf("melocoton", "nectarina", "albaricoque", "paraguayo")), // 🍑
    IconRule("🥭", keywords = listOf("mango")),                                       // 🥭
    IconRule("🥥", keywords = listOf("coco")),                                        // 🥥
    IconRule("🍏", keywords = listOf("higo", "higos", "ciruela", "ciruelas", "datil", "datiles", "caqui", "granada", "fruta")), // 🍏

    // ── Frutos secos ──────────────────────────────────────────────────────────
    // Antes que las grasas: "crema de cacahuete" es un fruto seco, no una crema ni una mantequilla.
    IconRule("🥜", keywords = listOf(// 🥜
        "almendra", "almendras", "nuez", "nueces", "cacahuete", "cacahuetes", "pistacho",
        "pistachos", "anacardo", "anacardos", "avellana", "avellanas", "pipas", "sesamo",
        "frutos secos")),

    // ── Grasas ────────────────────────────────────────────────────────────────
    IconRule("🫒", keywords = listOf("aceite", "aceituna", "aceitunas", "oliva")),    // 🫒
    IconRule("🧈", keywords = listOf("mantequilla", "margarina", "manteca")),         // 🧈

    // ── Dulces ────────────────────────────────────────────────────────────────
    IconRule("🍯", keywords = listOf("miel")),                                        // 🍯
    IconRule("🍫", keywords = listOf("chocolate", "cacao", "bombon", "bombones", "barrita", "turron", "nocilla", "nutella")), // 🍫
    IconRule("🍨", keywords = listOf("helado", "helados", "polo")),                   // 🍨
    IconRule("🍰", keywords = listOf("tarta", "pastel", "bizcocho", "brownie", "flan", "natillas", "mousse", "donut")), // 🍰
    IconRule("🍪", keywords = listOf("galleta", "galletas", "azucar", "caramelo", "chuches", "magdalena", "croissant")), // 🍪

    // ── Bebidas ───────────────────────────────────────────────────────────────
    IconRule("☕", keywords = listOf("cafe", "cortado", "capuchino", "expreso")),           // ☕
    IconRule("🍵", keywords = listOf("te", "infusion", "manzanilla", "poleo")),       // 🍵
    IconRule("🍺", keywords = listOf("cerveza", "cervezas")),                         // 🍺
    IconRule("🍷", keywords = listOf("vino", "tinto de verano", "sidra", "cava")),    // 🍷
    IconRule("🧃", keywords = listOf("zumo", "nectar")),                              // 🧃
    IconRule("🥤", keywords = listOf("refresco", "cola", "gaseosa", "tonica", "bebida", "isotonica")), // 🥤
    IconRule("💧", keywords = listOf("agua")),                                        // 💧

    // ── Preparaciones ─────────────────────────────────────────────────────────
    // Lo último de la comida: solo se llega aquí si el nombre no dice qué alimento es.
    IconRule("🥗", keywords = listOf("ensalada", "ensaladilla")),                     // 🥗
    IconRule("🥪", keywords = listOf("bocadillo", "sandwich", "wrap", "montadito")),  // 🥪
    IconRule("🍲", keywords = listOf("sopa", "caldo", "crema", "pure", "guiso", "estofado", "cocido")), // 🍲

    // ── Conservas y condimentos ───────────────────────────────────────────────
    IconRule("🧂", keywords = listOf("sal", "pimienta", "especias", "oregano", "comino", "curry", "canela")), // 🧂
    IconRule("🥫", keywords = listOf("salsa", "ketchup", "mayonesa", "mostaza", "vinagre", "conserva", "lata")), // 🥫
)

/**
 * El icono del alimento llamado [foodName], o [FOOD_ICON_DEFAULT] si no se reconoce.
 *
 * Nunca devuelve nulo ni cadena vacía: quien lo pinta no tiene que decidir nada, y una fila sin
 * icono desalinearía la lista entera.
 */
fun foodIconFor(foodName: String?): String {
    val name = foodName?.takeIf { it.isNotBlank() } ?: return FOOD_ICON_DEFAULT
    val normalized = normalizeFoodName(name)
    return ICON_RULES.firstOrNull { rule ->
        rule.keywords.any { phraseMatches(normalized, it) } || rule.stems.any { normalized.contains(it) }
    }?.icon ?: FOOD_ICON_DEFAULT
}
