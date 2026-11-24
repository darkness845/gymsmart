package com.gymsmart.gymsmart.nutrition

/**
 * Alimentos genericos: la capa de datos que Open Food Facts no tiene.
 *
 * OFF es una base de **productos envasados de marca**. Buscar "arroz" alli devuelve Hacendado,
 * Carrefour o Brillante, nunca "arroz" a secas, porque el concepto de alimento generico no existe en
 * su modelo. Esa es la unica diferencia real de fondo con una app como Fitia, que documenta que "la
 * mayoria de nuestros valores vienen de la USDA" y los revisa a mano.
 *
 * Esta tabla replica esa capa con la misma fuente: el volcado completo de **USDA FoodData Central,
 * SR Legacy**. Cada entrada se resolvio contra ese volcado y lleva su identificador para poder
 * comprobarla. Los nombres en espanol son curacion manual; los numeros no se tocan.
 *
 * Viaja **dentro de la app**, no es una segunda API: se consulta en memoria, sin red, sin limites de
 * peticiones y sin depender de que OFF este en pie. En ejecucion sigue habiendo una sola API de
 * alimentos, que es OFF para los productos de marca.
 *
 * Los valores son por 100 g y estan en **base cruda** (o en seco para arroz, pasta y legumbres), que
 * es justo lo que la conversion crudo/cocinado necesita saber y que de un producto de marca no se
 * puede saber. Por eso un generico es tambien el dato mas fiable para pesar en cocido.
 *
 * Generado desde el volcado SR Legacy; para anadir alimentos, ampliar la lista curada y regenerar.
 */
data class GenericFood(
    val name: String,
    val kcalPer100: Double,
    val proteinsPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    /** Identificador en FoodData Central, para poder verificar cualquier fila. */
    val fdcId: String
)

/** Etiqueta que se muestra donde los productos de marca muestran su marca. */
const val GENERIC_BRAND = "Generico"

val GENERIC_FOODS: List<GenericFood> = listOf(
    GenericFood("Pechuga de pollo", 120.0, 22.50, 0.00, 2.62, "171077"),
    GenericFood("Muslo de pollo", 121.0, 19.66, 0.00, 4.12, "173627"),
    GenericFood("Alitas de pollo", 126.0, 21.97, 0.00, 3.54, "173632"),
    GenericFood("Pechuga de pavo", 114.0, 23.66, 0.14, 1.48, "171098"),
    GenericFood("Carne picada de pavo", 148.0, 19.66, 0.00, 7.66, "171505"),
    GenericFood("Carne picada de ternera", 176.0, 20.00, 0.00, 10.00, "174030"),
    GenericFood("Solomillo de ternera", 148.0, 22.06, 0.00, 5.93, "171803"),
    GenericFood("Filete de ternera", 127.0, 22.27, 0.00, 3.54, "171804"),
    GenericFood("Lomo de cerdo", 127.0, 22.41, 0.00, 3.42, "168251"),
    GenericFood("Solomillo de cerdo", 109.0, 20.95, 0.00, 2.17, "168249"),
    GenericFood("Panceta de cerdo", 518.0, 9.34, 0.00, 53.01, "167812"),
    GenericFood("Cordero", 143.0, 20.88, 0.00, 5.94, "172491"),
    GenericFood("Conejo", 136.0, 20.05, 0.00, 5.55, "172521"),
    GenericFood("Merluza", 90.0, 18.31, 0.00, 1.31, "173713"),
    GenericFood("Bacalao", 82.0, 17.81, 0.00, 0.67, "171955"),
    GenericFood("Atun fresco", 144.0, 23.33, 0.00, 4.90, "173706"),
    GenericFood("Salmon", 208.0, 20.42, 0.00, 13.42, "175167"),
    GenericFood("Lubina", 97.0, 18.43, 0.00, 2.00, "175142"),
    GenericFood("Trucha", 141.0, 19.94, 0.00, 6.18, "173717"),
    GenericFood("Gambas", 85.0, 20.10, 0.00, 0.51, "175179"),
    GenericFood("Mejillones", 86.0, 11.90, 3.69, 2.24, "174216"),
    GenericFood("Calamar", 92.0, 15.58, 3.08, 1.38, "174223"),
    GenericFood("Pulpo", 82.0, 14.91, 2.20, 1.04, "174218"),
    GenericFood("Huevo entero", 143.0, 12.56, 0.72, 9.51, "171287"),
    GenericFood("Clara de huevo", 52.0, 10.90, 0.73, 0.17, "172183"),
    GenericFood("Leche entera", 61.0, 3.15, 4.80, 3.25, "171265"),
    GenericFood("Leche desnatada", 34.0, 3.37, 4.96, 0.08, "171269"),
    GenericFood("Yogur natural", 61.0, 3.47, 4.66, 3.25, "171284"),
    GenericFood("Yogur griego", 97.0, 9.00, 3.98, 5.00, "171304"),
    GenericFood("Queso fresco", 81.0, 10.45, 4.76, 2.27, "172182"),
    GenericFood("Requeson", 138.0, 11.39, 5.14, 7.91, "171248"),
    GenericFood("Queso curado", 410.0, 24.25, 2.13, 33.82, "170899"),
    GenericFood("Mozzarella", 299.0, 22.17, 2.40, 22.14, "170845"),
    GenericFood("Arroz blanco", 365.0, 7.13, 79.95, 0.66, "169756"),
    GenericFood("Arroz integral", 367.0, 7.54, 76.25, 3.20, "169703"),
    GenericFood("Pasta", 371.0, 13.04, 74.67, 1.51, "168927"),
    GenericFood("Avena", 389.0, 16.89, 66.27, 6.90, "169705"),
    GenericFood("Quinoa", 368.0, 14.12, 64.16, 6.07, "168874"),
    GenericFood("Cuscus", 376.0, 12.76, 77.43, 0.64, "169699"),
    GenericFood("Pan integral", 252.0, 12.45, 42.71, 3.50, "172688"),
    GenericFood("Pan blanco", 267.0, 8.20, 49.60, 3.60, "172818"),
    GenericFood("Lentejas", 352.0, 24.63, 63.35, 1.06, "172420"),
    GenericFood("Garbanzos", 378.0, 20.47, 62.95, 6.04, "173756"),
    GenericFood("Alubias blancas", 333.0, 23.36, 60.27, 0.85, "175202"),
    GenericFood("Alubias rojas", 333.0, 23.58, 60.01, 0.83, "175193"),
    GenericFood("Alubias negras", 341.0, 21.60, 62.36, 1.42, "173734"),
    GenericFood("Guisantes", 81.0, 5.42, 14.45, 0.40, "170419"),
    GenericFood("Habas secas", 341.0, 26.12, 58.29, 1.53, "175205"),
    GenericFood("Brocoli", 34.0, 2.82, 6.64, 0.37, "170379"),
    GenericFood("Espinacas", 23.0, 2.86, 3.63, 0.39, "168462"),
    GenericFood("Tomate", 18.0, 0.88, 3.89, 0.20, "170457"),
    GenericFood("Lechuga", 17.0, 1.23, 3.29, 0.30, "169247"),
    GenericFood("Cebolla", 40.0, 1.10, 9.34, 0.10, "170000"),
    GenericFood("Pimiento rojo", 26.0, 0.99, 6.03, 0.30, "170108"),
    GenericFood("Pimiento verde", 20.0, 0.86, 4.64, 0.17, "170427"),
    GenericFood("Calabacin", 17.0, 1.21, 3.11, 0.32, "169291"),
    GenericFood("Berenjena", 25.0, 0.98, 5.88, 0.18, "169228"),
    GenericFood("Zanahoria", 41.0, 0.93, 9.58, 0.24, "170393"),
    GenericFood("Champinones", 22.0, 3.09, 3.26, 0.34, "169251"),
    GenericFood("Judias verdes", 31.0, 1.83, 6.97, 0.22, "169961"),
    GenericFood("Coliflor", 25.0, 1.92, 4.97, 0.28, "169986"),
    GenericFood("Esparragos", 20.0, 2.20, 3.88, 0.12, "168389"),
    GenericFood("Pepino", 15.0, 0.65, 3.63, 0.11, "168409"),
    GenericFood("Ajo", 149.0, 6.36, 33.06, 0.50, "169230"),
    GenericFood("Patata", 69.0, 1.68, 15.71, 0.10, "170028"),
    GenericFood("Boniato", 86.0, 1.57, 20.12, 0.05, "168482"),
    GenericFood("Platano", 89.0, 1.09, 22.84, 0.33, "173944"),
    GenericFood("Manzana", 57.0, 0.28, 13.60, 0.15, "168202"),
    GenericFood("Naranja", 49.0, 0.91, 12.54, 0.15, "169917"),
    GenericFood("Fresas", 32.0, 0.67, 7.68, 0.30, "167762"),
    GenericFood("Uvas", 69.0, 0.72, 18.10, 0.16, "174683"),
    GenericFood("Pera", 57.0, 0.36, 15.23, 0.14, "169118"),
    GenericFood("Sandia", 30.0, 0.61, 7.55, 0.15, "167765"),
    GenericFood("Melon", 34.0, 0.84, 8.16, 0.19, "169092"),
    GenericFood("Kiwi", 61.0, 1.14, 14.66, 0.52, "168153"),
    GenericFood("Pina", 50.0, 0.54, 13.12, 0.12, "169124"),
    GenericFood("Arandanos", 57.0, 0.74, 14.49, 0.33, "171711"),
    GenericFood("Aguacate", 160.0, 2.00, 8.53, 14.66, "171705"),
    GenericFood("Almendras", 579.0, 21.15, 21.55, 49.93, "170567"),
    GenericFood("Nueces", 654.0, 15.23, 13.71, 65.21, "170187"),
    GenericFood("Cacahuetes", 567.0, 25.80, 16.13, 49.24, "172430"),
    GenericFood("Pistachos", 560.0, 20.16, 27.17, 45.32, "170184"),
    GenericFood("Anacardos", 553.0, 18.22, 30.19, 43.85, "170162"),
    GenericFood("Crema de cacahuete", 598.0, 22.21, 22.31, 51.36, "172470"),
    GenericFood("Aceite de oliva", 884.0, 0.00, 0.00, 100.00, "171413"),
    GenericFood("Mantequilla", 717.0, 0.85, 0.06, 81.11, "173430"),
    GenericFood("Miel", 304.0, 0.30, 82.40, 0.00, "169640"),
    GenericFood("Chocolate negro", 598.0, 7.79, 45.90, 42.63, "170273"),
    GenericFood("Tofu", 144.0, 17.27, 2.78, 8.72, "172475")
)
