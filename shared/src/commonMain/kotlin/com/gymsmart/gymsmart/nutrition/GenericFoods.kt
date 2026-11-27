package com.gymsmart.gymsmart.nutrition

// ─────────────────────────────────────────────────────────────────────────────────────────────────
//  FICHERO GENERADO — NO EDITAR A MANO.
//
//  Para anadir o cambiar un alimento se edita `tools/genericos.csv` y se regenera:
//      python tools/gen_genericos.py <directorio_del_volcado_de_SR_Legacy>
//
//  Cualquier cambio hecho aqui directamente se pierde en la siguiente regeneracion.
// ─────────────────────────────────────────────────────────────────────────────────────────────────

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
 * peticiones y sin depender de que OFF este en pie.
 *
 * Los valores son por 100 g y estan en **base cruda** (o en seco para arroz, pasta y legumbres), que
 * es justo lo que la conversion crudo/cocinado necesita saber y que de un producto de marca no se
 * puede saber. Por eso un generico es tambien el dato mas fiable para pesar en cocido.
 *
 * ## Sobre las kcal: no cuadran con 4/4/9, y es correcto
 *
 * En 137 alimentos hay unos cuantos cuyas kcal se desvian de `P*4 + HC*4 + G*9`, hasta un **+29 %**
 * en champinones o espinacas. **No son errores y no hay que "arreglarlos".** La USDA no calcula la
 * energia con 4/4/9, sino con los factores especificos de Merrill & Watt, distintos para cada
 * alimento, que es su forma de descontar la fibra y los acidos organicos no disponibles. Se verifico
 * reconstruyendo las kcal de 18 alimentos con esos factores: reproducen el valor de la USDA con un
 * error menor del 1,1 %.
 *
 * La consecuencia de diseno esta en doc/13 §D-2: **`kcalPer100` es la unica fuente de verdad de la
 * energia y ninguna pantalla debe reconstruirla desde los macros.**
 */
data class FoodPortion(
    /** Como se dice en la interfaz: "1 rebanada", "1 unidad grande". */
    val label: String,
    /** Lo que pesa esa medida, en gramos, segun `food_portion.csv` de la USDA. */
    val grams: Int,
)

data class GenericFood(
    val name: String,
    val kcalPer100: Double,
    val proteinsPer100: Double,
    val carbsPer100: Double,
    val fatPer100: Double,
    /** Identificador en FoodData Central, para poder verificar cualquier fila. */
    val fdcId: String,
    /**
     * De donde salen los numeros. Existe porque el `fdcId` solo sabe hablar de la USDA: en cuanto
     * entre un alimento con fuente espanola (BEDCA, Moreiras) hara falta decirlo aqui, o la promesa
     * de "lleva su identificador para poder comprobarla" se rompe en silencio.
     */
    val source: String = "USDA SR Legacy",
    /**
     * Otras formas de buscarlo. Resuelve lo que el plegado de singular/plural no puede: sinonimos
     * ("beicon" por "bacon"), regionalismos ("judias" por "alubias") y nombres comerciales.
     */
    val aliases: List<String> = emptyList(),
    /**
     * Medidas caseras con su peso, de `food_portion.csv`. Es la diferencia mas visible frente a una
     * app como Fitia: saber que un huevo grande son 50 g o una rebanada de pan 32 g sin pesarlo.
     */
    val portions: List<FoodPortion> = emptyList(),
)

/** Etiqueta que se muestra donde los productos de marca muestran su marca. */
const val GENERIC_BRAND = "Generico"

val GENERIC_FOODS: List<GenericFood> = listOf(
    // Chicken, broiler or fryers, breast, skinless, boneless, meat only, raw
    GenericFood(
        name = "Pechuga de pollo",
            kcalPer100 = 120.0,
            proteinsPer100 = 22.50,
            carbsPer100 = 0.00,
            fatPer100 = 2.62,
            fdcId = "171077",
            portions = listOf(FoodPortion("1 pieza", 272))
    ),
    // Chicken, broilers or fryers, dark meat, thigh, meat only, raw
    GenericFood(
        name = "Muslo de pollo",
            kcalPer100 = 121.0,
            proteinsPer100 = 19.66,
            carbsPer100 = 0.00,
            fatPer100 = 4.12,
            fdcId = "173627"
    ),
    // Chicken, broilers or fryers, wing, meat only, raw
    GenericFood(
        name = "Alitas de pollo",
            kcalPer100 = 126.0,
            proteinsPer100 = 21.97,
            carbsPer100 = 0.00,
            fatPer100 = 3.54,
            fdcId = "173632"
    ),
    // Turkey, whole, breast, meat only, raw
    GenericFood(
        name = "Pechuga de pavo",
            kcalPer100 = 114.0,
            proteinsPer100 = 23.66,
            carbsPer100 = 0.14,
            fatPer100 = 1.48,
            fdcId = "171098"
    ),
    // Turkey, Ground, raw
    GenericFood(
        name = "Carne picada de pavo",
            kcalPer100 = 148.0,
            proteinsPer100 = 19.66,
            carbsPer100 = 0.00,
            fatPer100 = 7.66,
            fdcId = "171505"
    ),
    // Beef, ground, 90% lean meat / 10% fat, raw
    GenericFood(
        name = "Carne picada de ternera",
            kcalPer100 = 176.0,
            proteinsPer100 = 20.00,
            carbsPer100 = 0.00,
            fatPer100 = 10.00,
            fdcId = "174030"
    ),
    // Beef, tenderloin, steak, separable lean only, trimmed to 1/8" fat, select, raw
    GenericFood(
        name = "Solomillo de ternera",
            kcalPer100 = 148.0,
            proteinsPer100 = 22.06,
            carbsPer100 = 0.00,
            fatPer100 = 5.93,
            fdcId = "171803"
    ),
    // Beef, top sirloin, steak, separable lean only, trimmed to 1/8" fat, select, raw
    GenericFood(
        name = "Filete de ternera",
            kcalPer100 = 127.0,
            proteinsPer100 = 22.27,
            carbsPer100 = 0.00,
            fatPer100 = 3.54,
            fdcId = "171804"
    ),
    // Pork, fresh, loin, top loin (chops), boneless, separable lean only, raw
    GenericFood(
        name = "Lomo de cerdo",
            kcalPer100 = 127.0,
            proteinsPer100 = 22.41,
            carbsPer100 = 0.00,
            fatPer100 = 3.42,
            fdcId = "168251"
    ),
    // Pork, fresh, loin, tenderloin, separable lean only, raw
    GenericFood(
        name = "Solomillo de cerdo",
            kcalPer100 = 109.0,
            proteinsPer100 = 20.95,
            carbsPer100 = 0.00,
            fatPer100 = 2.17,
            fdcId = "168249"
    ),
    // Pork, fresh, belly, raw
    GenericFood(
        name = "Panceta de cerdo",
            kcalPer100 = 518.0,
            proteinsPer100 = 9.34,
            carbsPer100 = 0.00,
            fatPer100 = 53.01,
            fdcId = "167812"
    ),
    // Lamb, loin, separable lean only, trimmed to 1/4" fat, choice, raw
    GenericFood(
        name = "Cordero",
            kcalPer100 = 143.0,
            proteinsPer100 = 20.88,
            carbsPer100 = 0.00,
            fatPer100 = 5.94,
            fdcId = "172491"
    ),
    // Game meat, rabbit, domesticated, composite of cuts, raw
    GenericFood(
        name = "Conejo",
            kcalPer100 = 136.0,
            proteinsPer100 = 20.05,
            carbsPer100 = 0.00,
            fatPer100 = 5.55,
            fdcId = "172521"
    ),
    // Fish, whiting, mixed species, raw
    GenericFood(
        name = "Merluza (pescadilla)",
            kcalPer100 = 90.0,
            proteinsPer100 = 18.31,
            carbsPer100 = 0.00,
            fatPer100 = 1.31,
            fdcId = "173713",
            aliases = listOf("pescadilla", "merluza"),
            portions = listOf(FoodPortion("1 filete", 92))
    ),
    // Fish, cod, Atlantic, raw
    GenericFood(
        name = "Bacalao",
            kcalPer100 = 82.0,
            proteinsPer100 = 17.81,
            carbsPer100 = 0.00,
            fatPer100 = 0.67,
            fdcId = "171955",
            portions = listOf(FoodPortion("1 filete", 231))
    ),
    // Fish, tuna, fresh, bluefin, raw
    GenericFood(
        name = "Atun fresco",
            kcalPer100 = 144.0,
            proteinsPer100 = 23.33,
            carbsPer100 = 0.00,
            fatPer100 = 4.90,
            fdcId = "173706"
    ),
    // Fish, salmon, Atlantic, farmed, raw
    GenericFood(
        name = "Salmon",
            kcalPer100 = 208.0,
            proteinsPer100 = 20.42,
            carbsPer100 = 0.00,
            fatPer100 = 13.42,
            fdcId = "175167",
            portions = listOf(FoodPortion("1 filete", 198))
    ),
    // Fish, sea bass, mixed species, raw
    GenericFood(
        name = "Lubina",
            kcalPer100 = 97.0,
            proteinsPer100 = 18.43,
            carbsPer100 = 0.00,
            fatPer100 = 2.00,
            fdcId = "175142",
            portions = listOf(FoodPortion("1 filete", 129))
    ),
    // Fish, trout, rainbow, farmed, raw
    GenericFood(
        name = "Trucha",
            kcalPer100 = 141.0,
            proteinsPer100 = 19.94,
            carbsPer100 = 0.00,
            fatPer100 = 6.18,
            fdcId = "173717",
            portions = listOf(FoodPortion("1 filete", 79))
    ),
    // Crustaceans, shrimp, raw
    GenericFood(
        name = "Gambas",
            kcalPer100 = 85.0,
            proteinsPer100 = 20.10,
            carbsPer100 = 0.00,
            fatPer100 = 0.51,
            fdcId = "175179"
    ),
    // Mollusks, mussel, blue, raw
    GenericFood(
        name = "Mejillones",
            kcalPer100 = 86.0,
            proteinsPer100 = 11.90,
            carbsPer100 = 3.69,
            fatPer100 = 2.24,
            fdcId = "174216",
            portions = listOf(FoodPortion("1 unidad grande", 20), FoodPortion("1 unidad mediana", 16), FoodPortion("1 unidad pequena", 10))
    ),
    // Mollusks, squid, mixed species, raw
    GenericFood(
        name = "Calamar",
            kcalPer100 = 92.0,
            proteinsPer100 = 15.58,
            carbsPer100 = 3.08,
            fatPer100 = 1.38,
            fdcId = "174223"
    ),
    // Mollusks, octopus, common, raw
    GenericFood(
        name = "Pulpo",
            kcalPer100 = 82.0,
            proteinsPer100 = 14.91,
            carbsPer100 = 2.20,
            fatPer100 = 1.04,
            fdcId = "174218"
    ),
    // Egg, whole, raw, fresh
    GenericFood(
        name = "Huevo entero",
            kcalPer100 = 143.0,
            proteinsPer100 = 12.56,
            carbsPer100 = 0.72,
            fatPer100 = 9.51,
            fdcId = "171287",
            portions = listOf(FoodPortion("1 unidad grande", 50), FoodPortion("1 unidad muy grande", 56), FoodPortion("1 unidad mediana", 44))
    ),
    // Egg, white, raw, fresh
    GenericFood(
        name = "Clara de huevo",
            kcalPer100 = 52.0,
            proteinsPer100 = 10.90,
            carbsPer100 = 0.73,
            fatPer100 = 0.17,
            fdcId = "172183",
            portions = listOf(FoodPortion("1 unidad grande", 33))
    ),
    // Milk, whole, 3.25% milkfat, with added vitamin D
    GenericFood(
        name = "Leche entera",
            kcalPer100 = 61.0,
            proteinsPer100 = 3.15,
            carbsPer100 = 4.80,
            fatPer100 = 3.25,
            fdcId = "171265",
            portions = listOf(FoodPortion("1 cucharada", 15))
    ),
    // Milk, nonfat, fluid, with added vitamin A and vitamin D (fat free or skim)
    GenericFood(
        name = "Leche desnatada",
            kcalPer100 = 34.0,
            proteinsPer100 = 3.37,
            carbsPer100 = 4.96,
            fatPer100 = 0.08,
            fdcId = "171269"
    ),
    // Yogurt, plain, whole milk
    GenericFood(
        name = "Yogur natural",
            kcalPer100 = 61.0,
            proteinsPer100 = 3.47,
            carbsPer100 = 4.66,
            fatPer100 = 3.25,
            fdcId = "171284"
    ),
    // Yogurt, Greek, plain, whole milk
    GenericFood(
        name = "Yogur griego",
            kcalPer100 = 97.0,
            proteinsPer100 = 9.00,
            carbsPer100 = 3.98,
            fatPer100 = 5.00,
            fdcId = "171304"
    ),
    // Cheese, cottage, lowfat, 2% milkfat
    GenericFood(
        name = "Queso batido tipo cottage",
            kcalPer100 = 81.0,
            proteinsPer100 = 10.45,
            carbsPer100 = 4.76,
            fatPer100 = 2.27,
            fdcId = "172182",
            aliases = listOf("cottage", "queso batido")
    ),
    // Cheese, ricotta, part skim milk
    GenericFood(
        name = "Requeson",
            kcalPer100 = 138.0,
            proteinsPer100 = 11.39,
            carbsPer100 = 5.14,
            fatPer100 = 7.91,
            fdcId = "171248"
    ),
    // Cheese, cheddar, sharp, sliced
    GenericFood(
        name = "Queso curado tipo cheddar",
            kcalPer100 = 410.0,
            proteinsPer100 = 24.25,
            carbsPer100 = 2.13,
            fatPer100 = 33.82,
            fdcId = "170899",
            aliases = listOf("cheddar"),
            portions = listOf(FoodPortion("1 loncha", 28))
    ),
    // Cheese, mozzarella, whole milk
    GenericFood(
        name = "Mozzarella",
            kcalPer100 = 299.0,
            proteinsPer100 = 22.17,
            carbsPer100 = 2.40,
            fatPer100 = 22.14,
            fdcId = "170845"
    ),
    // Rice, white, long-grain, regular, raw, unenriched
    GenericFood(
        name = "Arroz blanco",
            kcalPer100 = 365.0,
            proteinsPer100 = 7.13,
            carbsPer100 = 79.95,
            fatPer100 = 0.66,
            fdcId = "169756"
    ),
    // Rice, brown, long-grain, raw (Includes foods for USDA's Food Distribution Program)
    GenericFood(
        name = "Arroz integral",
            kcalPer100 = 367.0,
            proteinsPer100 = 7.54,
            carbsPer100 = 76.25,
            fatPer100 = 3.20,
            fdcId = "169703"
    ),
    // Pasta, dry, unenriched
    GenericFood(
        name = "Pasta",
            kcalPer100 = 371.0,
            proteinsPer100 = 13.04,
            carbsPer100 = 74.67,
            fatPer100 = 1.51,
            fdcId = "168927"
    ),
    // Oats (Includes foods for USDA's Food Distribution Program)
    GenericFood(
        name = "Avena",
            kcalPer100 = 389.0,
            proteinsPer100 = 16.89,
            carbsPer100 = 66.27,
            fatPer100 = 6.90,
            fdcId = "169705"
    ),
    // Quinoa, uncooked
    GenericFood(
        name = "Quinoa",
            kcalPer100 = 368.0,
            proteinsPer100 = 14.12,
            carbsPer100 = 64.16,
            fatPer100 = 6.07,
            fdcId = "168874"
    ),
    // Couscous, dry
    GenericFood(
        name = "Cuscus",
            kcalPer100 = 376.0,
            proteinsPer100 = 12.76,
            carbsPer100 = 77.43,
            fatPer100 = 0.64,
            fdcId = "169699"
    ),
    // Bread, whole-wheat, commercially prepared
    GenericFood(
        name = "Pan integral",
            kcalPer100 = 252.0,
            proteinsPer100 = 12.45,
            carbsPer100 = 42.71,
            fatPer100 = 3.50,
            fdcId = "172688",
            aliases = listOf("pan de molde integral", "pan integral de molde"),
            portions = listOf(FoodPortion("1 rebanada", 32))
    ),
    // Bread, white, prepared from recipe, made with nonfat dry milk
    GenericFood(
        name = "Pan blanco",
            kcalPer100 = 274.0,
            proteinsPer100 = 7.70,
            carbsPer100 = 53.60,
            fatPer100 = 2.60,
            fdcId = "174926",
            portions = listOf(FoodPortion("1 rebanada", 44))
    ),
    // Lentils, raw
    GenericFood(
        name = "Lentejas",
            kcalPer100 = 352.0,
            proteinsPer100 = 24.63,
            carbsPer100 = 63.35,
            fatPer100 = 1.06,
            fdcId = "172420",
            portions = listOf(FoodPortion("1 cucharada", 12))
    ),
    // Chickpeas (garbanzo beans, bengal gram), mature seeds, raw
    GenericFood(
        name = "Garbanzos",
            kcalPer100 = 378.0,
            proteinsPer100 = 20.47,
            carbsPer100 = 62.95,
            fatPer100 = 6.04,
            fdcId = "173756",
            portions = listOf(FoodPortion("1 cucharada", 12))
    ),
    // Beans, white, mature seeds, raw
    GenericFood(
        name = "Alubias blancas",
            kcalPer100 = 333.0,
            proteinsPer100 = 23.36,
            carbsPer100 = 60.27,
            fatPer100 = 0.85,
            fdcId = "175202",
            portions = listOf(FoodPortion("1 cucharada", 13))
    ),
    // Beans, kidney, all types, mature seeds, raw
    GenericFood(
        name = "Alubias rojas",
            kcalPer100 = 333.0,
            proteinsPer100 = 23.58,
            carbsPer100 = 60.01,
            fatPer100 = 0.83,
            fdcId = "175193"
    ),
    // Beans, black, mature seeds, raw
    GenericFood(
        name = "Alubias negras",
            kcalPer100 = 341.0,
            proteinsPer100 = 21.60,
            carbsPer100 = 62.36,
            fatPer100 = 1.42,
            fdcId = "173734",
            portions = listOf(FoodPortion("1 cucharada", 12))
    ),
    // Peas, green, raw
    GenericFood(
        name = "Guisantes",
            kcalPer100 = 81.0,
            proteinsPer100 = 5.42,
            carbsPer100 = 14.45,
            fatPer100 = 0.40,
            fdcId = "170419"
    ),
    // Broadbeans (fava beans), mature seeds, raw
    GenericFood(
        name = "Habas secas",
            kcalPer100 = 341.0,
            proteinsPer100 = 26.12,
            carbsPer100 = 58.29,
            fatPer100 = 1.53,
            fdcId = "175205",
            portions = listOf(FoodPortion("1 cucharada", 9))
    ),
    // Broccoli, raw
    GenericFood(
        name = "Brocoli",
            kcalPer100 = 34.0,
            proteinsPer100 = 2.82,
            carbsPer100 = 6.64,
            fatPer100 = 0.37,
            fdcId = "170379"
    ),
    // Spinach, raw
    GenericFood(
        name = "Espinacas",
            kcalPer100 = 23.0,
            proteinsPer100 = 2.86,
            carbsPer100 = 3.63,
            fatPer100 = 0.39,
            fdcId = "168462"
    ),
    // Tomatoes, red, ripe, raw, year round average
    GenericFood(
        name = "Tomate",
            kcalPer100 = 18.0,
            proteinsPer100 = 0.88,
            carbsPer100 = 3.89,
            fatPer100 = 0.20,
            fdcId = "170457"
    ),
    // Lettuce, cos or romaine, raw
    GenericFood(
        name = "Lechuga",
            kcalPer100 = 17.0,
            proteinsPer100 = 1.23,
            carbsPer100 = 3.29,
            fatPer100 = 0.30,
            fdcId = "169247"
    ),
    // Onions, raw
    GenericFood(
        name = "Cebolla",
            kcalPer100 = 40.0,
            proteinsPer100 = 1.10,
            carbsPer100 = 9.34,
            fatPer100 = 0.10,
            fdcId = "170000",
            portions = listOf(FoodPortion("1 unidad grande", 150), FoodPortion("1 unidad pequena", 70))
    ),
    // Peppers, sweet, red, raw
    GenericFood(
        name = "Pimiento rojo",
            kcalPer100 = 26.0,
            proteinsPer100 = 0.99,
            carbsPer100 = 6.03,
            fatPer100 = 0.30,
            fdcId = "170108",
            portions = listOf(FoodPortion("1 cucharada", 9), FoodPortion("1 unidad pequena", 74))
    ),
    // Peppers, sweet, green, raw
    GenericFood(
        name = "Pimiento verde",
            kcalPer100 = 20.0,
            proteinsPer100 = 0.86,
            carbsPer100 = 4.64,
            fatPer100 = 0.17,
            fdcId = "170427",
            portions = listOf(FoodPortion("1 cucharada", 9), FoodPortion("1 unidad pequena", 74))
    ),
    // Squash, summer, zucchini, includes skin, raw
    GenericFood(
        name = "Calabacin",
            kcalPer100 = 17.0,
            proteinsPer100 = 1.21,
            carbsPer100 = 3.11,
            fatPer100 = 0.32,
            fdcId = "169291",
            portions = listOf(FoodPortion("1 unidad grande", 323), FoodPortion("1 unidad mediana", 196), FoodPortion("1 rebanada", 10))
    ),
    // Eggplant, raw
    GenericFood(
        name = "Berenjena",
            kcalPer100 = 25.0,
            proteinsPer100 = 0.98,
            carbsPer100 = 5.88,
            fatPer100 = 0.18,
            fdcId = "169228"
    ),
    // Carrots, raw
    GenericFood(
        name = "Zanahoria",
            kcalPer100 = 41.0,
            proteinsPer100 = 0.93,
            carbsPer100 = 9.58,
            fatPer100 = 0.24,
            fdcId = "170393",
            portions = listOf(FoodPortion("1 unidad mediana", 61), FoodPortion("1 rebanada", 3))
    ),
    // Mushrooms, white, raw
    GenericFood(
        name = "Champinones",
            kcalPer100 = 22.0,
            proteinsPer100 = 3.09,
            carbsPer100 = 3.26,
            fatPer100 = 0.34,
            fdcId = "169251",
            portions = listOf(FoodPortion("1 unidad grande", 23), FoodPortion("1 unidad mediana", 18), FoodPortion("1 rebanada", 6))
    ),
    // Beans, snap, green, raw
    GenericFood(
        name = "Judias verdes",
            kcalPer100 = 31.0,
            proteinsPer100 = 1.83,
            carbsPer100 = 6.97,
            fatPer100 = 0.22,
            fdcId = "169961"
    ),
    // Cauliflower, raw
    GenericFood(
        name = "Coliflor",
            kcalPer100 = 25.0,
            proteinsPer100 = 1.92,
            carbsPer100 = 4.97,
            fatPer100 = 0.28,
            fdcId = "169986"
    ),
    // Asparagus, raw
    GenericFood(
        name = "Esparragos",
            kcalPer100 = 20.0,
            proteinsPer100 = 2.20,
            carbsPer100 = 3.88,
            fatPer100 = 0.12,
            fdcId = "168389"
    ),
    // Cucumber, with peel, raw
    GenericFood(
        name = "Pepino",
            kcalPer100 = 15.0,
            proteinsPer100 = 0.65,
            carbsPer100 = 3.63,
            fatPer100 = 0.11,
            fdcId = "168409"
    ),
    // Garlic, raw
    GenericFood(
        name = "Ajo",
            kcalPer100 = 149.0,
            proteinsPer100 = 6.36,
            carbsPer100 = 33.06,
            fatPer100 = 0.50,
            fdcId = "169230",
            portions = listOf(FoodPortion("1 cucharadita", 3), FoodPortion("1 diente", 3))
    ),
    // Potatoes, white, flesh and skin, raw
    GenericFood(
        name = "Patata",
            kcalPer100 = 69.0,
            proteinsPer100 = 1.68,
            carbsPer100 = 15.71,
            fatPer100 = 0.10,
            fdcId = "170028"
    ),
    // Sweet potato, raw, unprepared (Includes foods for USDA's Food Distribution Program)
    GenericFood(
        name = "Boniato",
            kcalPer100 = 86.0,
            proteinsPer100 = 1.57,
            carbsPer100 = 20.12,
            fatPer100 = 0.05,
            fdcId = "168482"
    ),
    // Bananas, raw
    GenericFood(
        name = "Platano",
            kcalPer100 = 89.0,
            proteinsPer100 = 1.09,
            carbsPer100 = 22.84,
            fatPer100 = 0.33,
            fdcId = "173944"
    ),
    // Apples, raw, with skin (Includes foods for USDA's Food Distribution Program)
    GenericFood(
        name = "Manzana",
            kcalPer100 = 52.0,
            proteinsPer100 = 0.26,
            carbsPer100 = 13.81,
            fatPer100 = 0.17,
            fdcId = "171688"
    ),
    // Oranges, raw, navels (Includes foods for USDA's Food Distribution Program)
    GenericFood(
        name = "Naranja",
            kcalPer100 = 49.0,
            proteinsPer100 = 0.91,
            carbsPer100 = 12.54,
            fatPer100 = 0.15,
            fdcId = "169917"
    ),
    // Strawberries, raw
    GenericFood(
        name = "Fresas",
            kcalPer100 = 32.0,
            proteinsPer100 = 0.67,
            carbsPer100 = 7.68,
            fatPer100 = 0.30,
            fdcId = "167762"
    ),
    // Grapes, red or green (European type, such as Thompson seedless), raw
    GenericFood(
        name = "Uvas",
            kcalPer100 = 69.0,
            proteinsPer100 = 0.72,
            carbsPer100 = 18.10,
            fatPer100 = 0.16,
            fdcId = "174683"
    ),
    // Pears, raw
    GenericFood(
        name = "Pera",
            kcalPer100 = 57.0,
            proteinsPer100 = 0.36,
            carbsPer100 = 15.23,
            fatPer100 = 0.14,
            fdcId = "169118",
            portions = listOf(FoodPortion("1 unidad pequena", 148), FoodPortion("1 unidad mediana", 178), FoodPortion("1 unidad grande", 230))
    ),
    // Watermelon, raw
    GenericFood(
        name = "Sandia",
            kcalPer100 = 30.0,
            proteinsPer100 = 0.61,
            carbsPer100 = 7.55,
            fatPer100 = 0.15,
            fdcId = "167765"
    ),
    // Melons, cantaloupe, raw
    GenericFood(
        name = "Melon",
            kcalPer100 = 34.0,
            proteinsPer100 = 0.84,
            carbsPer100 = 8.16,
            fatPer100 = 0.19,
            fdcId = "169092"
    ),
    // Kiwifruit, green, raw
    GenericFood(
        name = "Kiwi",
            kcalPer100 = 61.0,
            proteinsPer100 = 1.14,
            carbsPer100 = 14.66,
            fatPer100 = 0.52,
            fdcId = "168153"
    ),
    // Pineapple, raw, all varieties
    GenericFood(
        name = "Pina",
            kcalPer100 = 50.0,
            proteinsPer100 = 0.54,
            carbsPer100 = 13.12,
            fatPer100 = 0.12,
            fdcId = "169124",
            portions = listOf(FoodPortion("1 pieza", 905))
    ),
    // Blueberries, raw
    GenericFood(
        name = "Arandanos",
            kcalPer100 = 57.0,
            proteinsPer100 = 0.74,
            carbsPer100 = 14.49,
            fatPer100 = 0.33,
            fdcId = "171711"
    ),
    // Avocados, raw, all commercial varieties
    GenericFood(
        name = "Aguacate",
            kcalPer100 = 160.0,
            proteinsPer100 = 2.00,
            carbsPer100 = 8.53,
            fatPer100 = 14.66,
            fdcId = "171705"
    ),
    // Nuts, almonds
    GenericFood(
        name = "Almendras",
            kcalPer100 = 579.0,
            proteinsPer100 = 21.15,
            carbsPer100 = 21.55,
            fatPer100 = 49.93,
            fdcId = "170567"
    ),
    // Nuts, walnuts, english
    GenericFood(
        name = "Nueces",
            kcalPer100 = 654.0,
            proteinsPer100 = 15.23,
            carbsPer100 = 13.71,
            fatPer100 = 65.21,
            fdcId = "170187"
    ),
    // Peanuts, all types, raw
    GenericFood(
        name = "Cacahuetes",
            kcalPer100 = 567.0,
            proteinsPer100 = 25.80,
            carbsPer100 = 16.13,
            fatPer100 = 49.24,
            fdcId = "172430"
    ),
    // Nuts, pistachio nuts, raw
    GenericFood(
        name = "Pistachos",
            kcalPer100 = 560.0,
            proteinsPer100 = 20.16,
            carbsPer100 = 27.17,
            fatPer100 = 45.32,
            fdcId = "170184"
    ),
    // Nuts, cashew nuts, raw
    GenericFood(
        name = "Anacardos",
            kcalPer100 = 553.0,
            proteinsPer100 = 18.22,
            carbsPer100 = 30.19,
            fatPer100 = 43.85,
            fdcId = "170162"
    ),
    // Peanut butter, smooth style, without salt
    GenericFood(
        name = "Crema de cacahuete",
            kcalPer100 = 598.0,
            proteinsPer100 = 22.21,
            carbsPer100 = 22.31,
            fatPer100 = 51.36,
            fdcId = "172470",
            portions = listOf(FoodPortion("1 cucharada", 32))
    ),
    // Oil, olive, salad or cooking
    GenericFood(
        name = "Aceite de oliva",
            kcalPer100 = 884.0,
            proteinsPer100 = 0.00,
            carbsPer100 = 0.00,
            fatPer100 = 100.00,
            fdcId = "171413",
            portions = listOf(FoodPortion("1 cucharada", 14), FoodPortion("1 cucharadita", 4))
    ),
    // Butter, without salt
    GenericFood(
        name = "Mantequilla",
            kcalPer100 = 717.0,
            proteinsPer100 = 0.85,
            carbsPer100 = 0.06,
            fatPer100 = 81.11,
            fdcId = "173430",
            portions = listOf(FoodPortion("1 cucharada", 14))
    ),
    // Honey
    GenericFood(
        name = "Miel",
            kcalPer100 = 304.0,
            proteinsPer100 = 0.30,
            carbsPer100 = 82.40,
            fatPer100 = 0.00,
            fdcId = "169640",
            portions = listOf(FoodPortion("1 cucharada", 21))
    ),
    // Chocolate, dark, 70-85% cacao solids
    GenericFood(
        name = "Chocolate negro",
            kcalPer100 = 598.0,
            proteinsPer100 = 7.79,
            carbsPer100 = 45.90,
            fatPer100 = 42.63,
            fdcId = "170273"
    ),
    // Tofu, raw, firm, prepared with calcium sulfate
    GenericFood(
        name = "Tofu",
            kcalPer100 = 144.0,
            proteinsPer100 = 17.27,
            carbsPer100 = 2.78,
            fatPer100 = 8.72,
            fdcId = "172475"
    ),
    // Fish, tuna, light, canned in water, drained solids (Includes foods for USDA's Food Distribution Prog
    GenericFood(
        name = "Atun en lata al natural",
            kcalPer100 = 86.0,
            proteinsPer100 = 19.44,
            carbsPer100 = 0.00,
            fatPer100 = 0.96,
            fdcId = "173709",
            aliases = listOf("atun lata", "atun claro", "atun natural")
    ),
    // Fish, tuna, light, canned in oil, drained solids
    GenericFood(
        name = "Atun en lata en aceite",
            kcalPer100 = 198.0,
            proteinsPer100 = 29.13,
            carbsPer100 = 0.00,
            fatPer100 = 8.21,
            fdcId = "173708",
            aliases = listOf("atun aceite")
    ),
    // Fish, sardine, Atlantic, canned in oil, drained solids with bone
    GenericFood(
        name = "Sardinas en lata",
            kcalPer100 = 208.0,
            proteinsPer100 = 24.62,
            carbsPer100 = 0.00,
            fatPer100 = 11.45,
            fdcId = "175139",
            aliases = listOf("sardina lata")
    ),
    // Fish, anchovy, european, canned in oil, drained solids
    GenericFood(
        name = "Anchoas en aceite",
            kcalPer100 = 210.0,
            proteinsPer100 = 28.89,
            carbsPer100 = 0.00,
            fatPer100 = 9.71,
            fdcId = "174183",
            aliases = listOf("anchoa", "boqueron en aceite")
    ),
    // Fish, anchovy, european, raw
    GenericFood(
        name = "Boquerones frescos",
            kcalPer100 = 131.0,
            proteinsPer100 = 20.35,
            carbsPer100 = 0.00,
            fatPer100 = 4.84,
            fdcId = "174182",
            aliases = listOf("boqueron", "anchoa fresca")
    ),
    // Fish, snapper, mixed species, raw
    GenericFood(
        name = "Dorada",
            kcalPer100 = 100.0,
            proteinsPer100 = 20.51,
            carbsPer100 = 0.00,
            fatPer100 = 1.34,
            fdcId = "173698",
            aliases = listOf("besugo", "pargo"),
            portions = listOf(FoodPortion("1 filete", 218))
    ),
    // Pork, cured, ham, steak, boneless, extra lean, unheated
    GenericFood(
        name = "Jamon cocido",
            kcalPer100 = 122.0,
            proteinsPer100 = 19.56,
            carbsPer100 = 0.00,
            fatPer100 = 4.25,
            fdcId = "167874",
            aliases = listOf("jamon york", "fiambre de jamon"),
            portions = listOf(FoodPortion("1 rebanada", 57))
    ),
    // Pork, cured, ham, low sodium, lean and fat, cooked
    GenericFood(
        name = "Jamon curado",
            kcalPer100 = 172.0,
            proteinsPer100 = 22.30,
            carbsPer100 = 0.30,
            fatPer100 = 8.30,
            fdcId = "168110",
            aliases = listOf("jamon serrano", "jamon iberico")
    ),
    // Sausage, pork, chorizo, link or ground, raw
    GenericFood(
        name = "Chorizo",
            kcalPer100 = 296.0,
            proteinsPer100 = 13.63,
            carbsPer100 = 3.78,
            fatPer100 = 25.10,
            fdcId = "173859"
    ),
    // PIZZA HUT 12" Cheese Pizza, Pan Crust
    GenericFood(
        name = "Salchichon",
            kcalPer100 = 280.0,
            proteinsPer100 = 11.73,
            carbsPer100 = 29.93,
            fatPer100 = 12.56,
            fdcId = "172041",
            aliases = listOf("salami"),
            portions = listOf(FoodPortion("1 rebanada", 100))
    ),
    // Pork, cured, bacon, unprepared
    GenericFood(
        name = "Bacon",
            kcalPer100 = 393.0,
            proteinsPer100 = 13.66,
            carbsPer100 = 0.00,
            fatPer100 = 37.13,
            fdcId = "168277",
            aliases = listOf("panceta ahumada", "beicon")
    ),
    // Fast foods, french toast sticks
    GenericFood(
        name = "Morcilla",
            kcalPer100 = 340.0,
            proteinsPer100 = 6.00,
            carbsPer100 = 41.21,
            fatPer100 = 17.74,
            fdcId = "172035",
            aliases = listOf("morcilla de burgos")
    ),
    // Chickpeas (garbanzo beans, bengal gram), mature seeds, canned, drained solids
    GenericFood(
        name = "Garbanzos de bote",
            kcalPer100 = 139.0,
            proteinsPer100 = 7.05,
            carbsPer100 = 22.53,
            fatPer100 = 2.77,
            fdcId = "173800",
            aliases = listOf("garbanzos cocidos", "garbanzo tarro")
    ),
    // Beans, white, mature seeds, canned
    GenericFood(
        name = "Alubias blancas de bote",
            kcalPer100 = 114.0,
            proteinsPer100 = 7.26,
            carbsPer100 = 21.20,
            fatPer100 = 0.29,
            fdcId = "175204",
            aliases = listOf("judias blancas cocidas", "alubia bote")
    ),
    // Beans, kidney, red, mature seeds, canned, drained solids
    GenericFood(
        name = "Alubias rojas de bote",
            kcalPer100 = 124.0,
            proteinsPer100 = 7.98,
            carbsPer100 = 21.49,
            fatPer100 = 1.05,
            fdcId = "174285",
            aliases = listOf("judias rojas cocidas")
    ),
    // Bread, french or vienna (includes sourdough)
    GenericFood(
        name = "Pan de barra",
            kcalPer100 = 272.0,
            proteinsPer100 = 10.75,
            carbsPer100 = 51.88,
            fatPer100 = 2.42,
            fdcId = "172675",
            aliases = listOf("barra", "baguette", "pan blanco de barra"),
            portions = listOf(FoodPortion("1 rebanada", 139))
    ),
    // Wheat flour, white, all-purpose, enriched, bleached
    GenericFood(
        name = "Harina de trigo",
            kcalPer100 = 364.0,
            proteinsPer100 = 10.33,
            carbsPer100 = 76.31,
            fatPer100 = 0.98,
            fdcId = "168894",
            aliases = listOf("harina")
    ),
    // Milk, reduced fat, fluid, 2% milkfat, with added vitamin A and vitamin D
    GenericFood(
        name = "Leche semidesnatada",
            kcalPer100 = 50.0,
            proteinsPer100 = 3.30,
            carbsPer100 = 4.80,
            fatPer100 = 1.98,
            fdcId = "171267",
            aliases = listOf("leche semi")
    ),
    // Yogurt, plain, skim milk
    GenericFood(
        name = "Yogur desnatado",
            kcalPer100 = 56.0,
            proteinsPer100 = 5.73,
            carbsPer100 = 7.68,
            fatPer100 = 0.18,
            fdcId = "170887",
            aliases = listOf("yogur 0%", "yogur natural desnatado")
    ),
    // Yogurt, plain, low fat
    GenericFood(
        name = "Yogur natural bajo en grasa",
            kcalPer100 = 63.0,
            proteinsPer100 = 5.25,
            carbsPer100 = 7.04,
            fatPer100 = 1.55,
            fdcId = "170886",
            aliases = listOf("yogur semidesnatado")
    ),
    // Cheese, cream
    GenericFood(
        name = "Queso de untar",
            kcalPer100 = 350.0,
            proteinsPer100 = 6.15,
            carbsPer100 = 5.52,
            fatPer100 = 34.44,
            fdcId = "173418",
            aliases = listOf("queso crema", "philadelphia"),
            portions = listOf(FoodPortion("1 cucharada", 14))
    ),
    // Cheese, feta
    GenericFood(
        name = "Queso feta",
            kcalPer100 = 265.0,
            proteinsPer100 = 14.21,
            carbsPer100 = 3.88,
            fatPer100 = 21.49,
            fdcId = "173420"
    ),
    // Cheese, goat, semisoft type
    GenericFood(
        name = "Queso de cabra",
            kcalPer100 = 364.0,
            proteinsPer100 = 21.58,
            carbsPer100 = 0.12,
            fatPer100 = 29.84,
            fdcId = "173433",
            aliases = listOf("rulo de cabra")
    ),
    // Cream, fluid, heavy whipping
    GenericFood(
        name = "Nata para montar",
            kcalPer100 = 340.0,
            proteinsPer100 = 2.84,
            carbsPer100 = 2.84,
            fatPer100 = 36.08,
            fdcId = "170859",
            aliases = listOf("nata", "crema de leche"),
            portions = listOf(FoodPortion("1 cucharada", 15))
    ),
    // Egg, yolk, raw, fresh
    GenericFood(
        name = "Yema de huevo",
            kcalPer100 = 322.0,
            proteinsPer100 = 15.86,
            carbsPer100 = 3.59,
            fatPer100 = 26.54,
            fdcId = "172184",
            aliases = listOf("yema"),
            portions = listOf(FoodPortion("1 unidad grande", 17))
    ),
    // Oil, sunflower, linoleic, (approx. 65%)
    GenericFood(
        name = "Aceite de girasol",
            kcalPer100 = 884.0,
            proteinsPer100 = 0.00,
            carbsPer100 = 0.00,
            fatPer100 = 100.00,
            fdcId = "171025",
            portions = listOf(FoodPortion("1 cucharada", 14), FoodPortion("1 cucharadita", 4))
    ),
    // Salad dressing, mayonnaise, regular
    GenericFood(
        name = "Mayonesa",
            kcalPer100 = 680.0,
            proteinsPer100 = 0.96,
            carbsPer100 = 0.57,
            fatPer100 = 74.85,
            fdcId = "171009",
            portions = listOf(FoodPortion("1 cucharada", 14))
    ),
    // Tomato products, canned, sauce
    GenericFood(
        name = "Tomate frito",
            kcalPer100 = 24.0,
            proteinsPer100 = 1.20,
            carbsPer100 = 5.31,
            fatPer100 = 0.30,
            fdcId = "170054",
            aliases = listOf("salsa de tomate", "tomate triturado")
    ),
    // Olives, ripe, canned (small-extra large)
    GenericFood(
        name = "Aceitunas",
            kcalPer100 = 116.0,
            proteinsPer100 = 0.84,
            carbsPer100 = 6.04,
            fatPer100 = 10.90,
            fdcId = "169094",
            aliases = listOf("olivas", "aceituna"),
            portions = listOf(FoodPortion("1 cucharada", 8), FoodPortion("1 unidad grande", 4), FoodPortion("1 unidad pequena", 3))
    ),
    // Sugars, granulated
    GenericFood(
        name = "Azucar",
            kcalPer100 = 387.0,
            proteinsPer100 = 0.00,
            carbsPer100 = 99.98,
            fatPer100 = 0.00,
            fdcId = "169655",
            portions = listOf(FoodPortion("1 cucharadita", 4))
    ),
    // Artichokes, (globe or french), raw
    GenericFood(
        name = "Alcachofa",
            kcalPer100 = 47.0,
            proteinsPer100 = 3.27,
            carbsPer100 = 10.51,
            fatPer100 = 0.15,
            fdcId = "169205"
    ),
    // Leeks, (bulb and lower leaf-portion), raw
    GenericFood(
        name = "Puerro",
            kcalPer100 = 61.0,
            proteinsPer100 = 1.50,
            carbsPer100 = 14.15,
            fatPer100 = 0.30,
            fdcId = "169246",
            portions = listOf(FoodPortion("1 rebanada", 6))
    ),
    // Chard, swiss, raw
    GenericFood(
        name = "Acelgas",
            kcalPer100 = 19.0,
            proteinsPer100 = 1.80,
            carbsPer100 = 3.74,
            fatPer100 = 0.20,
            fdcId = "169991",
            aliases = listOf("acelga")
    ),
    // Cabbage, raw
    GenericFood(
        name = "Col",
            kcalPer100 = 25.0,
            proteinsPer100 = 1.28,
            carbsPer100 = 5.80,
            fatPer100 = 0.10,
            fdcId = "169975",
            aliases = listOf("repollo", "berza")
    ),
    // Beets, raw
    GenericFood(
        name = "Remolacha",
            kcalPer100 = 43.0,
            proteinsPer100 = 1.61,
            carbsPer100 = 9.56,
            fatPer100 = 0.17,
            fdcId = "169145"
    ),
    // Corn, sweet, yellow, canned, vacuum pack, regular pack
    GenericFood(
        name = "Maiz dulce",
            kcalPer100 = 79.0,
            proteinsPer100 = 2.41,
            carbsPer100 = 19.44,
            fatPer100 = 0.50,
            fdcId = "169216",
            aliases = listOf("maiz")
    ),
    // Tangerines, (mandarin oranges), raw
    GenericFood(
        name = "Mandarina",
            kcalPer100 = 53.0,
            proteinsPer100 = 0.81,
            carbsPer100 = 13.34,
            fatPer100 = 0.31,
            fdcId = "169105",
            aliases = listOf("clementina")
    ),
    // Plums, raw
    GenericFood(
        name = "Ciruela",
            kcalPer100 = 46.0,
            proteinsPer100 = 0.70,
            carbsPer100 = 11.42,
            fatPer100 = 0.28,
            fdcId = "169949"
    ),
    // Cherries, sweet, raw
    GenericFood(
        name = "Cerezas",
            kcalPer100 = 63.0,
            proteinsPer100 = 1.06,
            carbsPer100 = 16.01,
            fatPer100 = 0.20,
            fdcId = "171719",
            aliases = listOf("cereza", "picota")
    ),
    // Figs, raw
    GenericFood(
        name = "Higos",
            kcalPer100 = 74.0,
            proteinsPer100 = 0.75,
            carbsPer100 = 19.18,
            fatPer100 = 0.30,
            fdcId = "173021",
            aliases = listOf("higo", "breva")
    ),
    // Apricots, raw
    GenericFood(
        name = "Albaricoque",
            kcalPer100 = 48.0,
            proteinsPer100 = 1.40,
            carbsPer100 = 11.12,
            fatPer100 = 0.39,
            fdcId = "171697",
            aliases = listOf("damasco")
    ),
    // Dates, medjool
    GenericFood(
        name = "Datiles",
            kcalPer100 = 277.0,
            proteinsPer100 = 1.81,
            carbsPer100 = 74.97,
            fatPer100 = 0.15,
            fdcId = "168191",
            aliases = listOf("datil")
    ),
    // Nuts, hazelnuts or filberts
    GenericFood(
        name = "Avellanas",
            kcalPer100 = 628.0,
            proteinsPer100 = 14.95,
            carbsPer100 = 16.70,
            fatPer100 = 60.75,
            fdcId = "170581",
            aliases = listOf("avellana")
    ),
    // Seeds, chia seeds, dried
    GenericFood(
        name = "Semillas de chia",
            kcalPer100 = 486.0,
            proteinsPer100 = 16.54,
            carbsPer100 = 42.12,
            fatPer100 = 30.74,
            fdcId = "170554",
            aliases = listOf("chia")
    ),
    // Seeds, flaxseed
    GenericFood(
        name = "Semillas de lino",
            kcalPer100 = 534.0,
            proteinsPer100 = 18.29,
            carbsPer100 = 28.88,
            fatPer100 = 42.16,
            fdcId = "169414",
            aliases = listOf("lino", "linaza")
    ),
    // Seeds, sesame seeds, whole, dried
    GenericFood(
        name = "Semillas de sesamo",
            kcalPer100 = 573.0,
            proteinsPer100 = 17.73,
            carbsPer100 = 23.45,
            fatPer100 = 49.67,
            fdcId = "170150",
            aliases = listOf("sesamo", "ajonjoli"),
            portions = listOf(FoodPortion("1 cucharada", 9))
    ),
    // Beverages, Protein powder whey based
    GenericFood(
        name = "Proteina de suero en polvo",
            kcalPer100 = 352.0,
            proteinsPer100 = 78.13,
            carbsPer100 = 6.25,
            fatPer100 = 1.56,
            fdcId = "173180",
            aliases = listOf("proteina whey", "whey", "batido de proteinas")
    ),
    // New Zealand spinach, raw
    GenericFood(
        name = "Tempeh",
            kcalPer100 = 14.0,
            proteinsPer100 = 1.50,
            carbsPer100 = 2.50,
            fatPer100 = 0.20,
            fdcId = "168440"
    ),
)
