#!/usr/bin/env python3
"""Genera shared/.../nutrition/GenericFoods.kt desde el volcado de USDA FoodData Central.

POR QUE EXISTE
--------------
La cabecera de GenericFoods.kt decia "Generado desde el volcado SR Legacy; para anadir alimentos,
ampliar la lista curada y regenerar" y el script no estaba en el repositorio, asi que cada ampliacion
era trabajo manual irrepetible y los numeros no se podian volver a comprobar. La auditoria del
2026-11-27 (doc/13 §3, fase 4) lo marco como lo primero que habia que materializar.

COMO SE USA
-----------
1. Descargar el volcado (una vez, no va al repositorio porque son 6 MB de datos de terceros):

     https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip

   y descomprimirlo en un directorio propio.

2. Editar `tools/genericos.csv`, que es **lo unico que se toca a mano**: la curacion humana
   (que nombre espanol corresponde a que identificador de la USDA) queda separada de los numeros.

3. Regenerar:

     python tools/gen_genericos.py <directorio_del_volcado>

QUE HACE Y QUE NO
-----------------
- Los numeros NO se tocan: salen tal cual del volcado. Si una fila no tiene los cuatro nutrientes,
  el script falla en vez de rellenar con ceros.
- Avisa cuando las kcal de la USDA se desvian mucho de la regla 4/4/9. **Eso no es un error**: la
  USDA calcula la energia con los factores especificos de Merrill & Watt, no con 4/4/9, y por eso
  champinones o espinacas se desvian hasta un 29 % (doc/13 §D-2). El aviso esta para detectar una
  fila mal copiada, no para "corregir" las kcal.
- Imprime la descripcion inglesa de cada fila para que la curacion se pueda revisar de un vistazo:
  es como se detectaron los tres errores de mapeo que arrastraba la tabla (un "Queso fresco" que era
  cottage cheese, un "Queso curado" que era cheddar en lonchas y una "Merluza" que era whiting).
"""
import csv
import os
import sys
from collections import defaultdict

AQUI = os.path.dirname(os.path.abspath(__file__))
CSV_CURADO = os.path.join(AQUI, "genericos.csv")
DESTINO = os.path.join(
    AQUI, "..", "shared", "src", "commonMain", "kotlin", "com", "gymsmart", "gymsmart",
    "nutrition", "GenericFoods.kt",
)

# Identificadores de nutriente en FoodData Central.
NUTRIENTES = {"1008": "kcal", "1003": "proteinas", "1005": "carbos", "1004": "grasas"}

# Medidas caseras que significan algo en Espana, con su etiqueta. Lo que no este aqui se descarta:
# las medidas en "cup" son estadounidenses y en una cocina espanola no dicen nada.
MEDIDAS = {
    "large": "1 unidad grande",
    "medium": "1 unidad mediana",
    "small": "1 unidad pequena",
    "extra large": "1 unidad muy grande",
    "slice": "1 rebanada",
    "slice (1 oz)": "1 loncha",
    "tbsp": "1 cucharada",
    "tablespoon": "1 cucharada",
    "tsp": "1 cucharadita",
    "teaspoon": "1 cucharadita",
    "fruit": "1 pieza",
    "fruit, without skin and seeds": "1 pieza",
    "egg": "1 huevo",
    "clove": "1 diente",
    "head, medium": "1 unidad mediana",
    "fillet": "1 filete",
    "piece": "1 pieza",
    "unit": "1 unidad",
}
MAX_RACIONES = 3


def fallo(mensaje):
    print(f"ERROR: {mensaje}", file=sys.stderr)
    sys.exit(1)


def leer_volcado(directorio):
    def ruta(nombre):
        p = os.path.join(directorio, nombre)
        if not os.path.exists(p):
            fallo(f"no encuentro {nombre} en {directorio}. "
                  f"Se espera el contenido descomprimido del volcado de SR Legacy.")
        return p

    descripciones = {}
    with open(ruta("food.csv"), encoding="utf-8-sig", newline="") as f:
        for fila in csv.DictReader(f):
            descripciones[fila["fdc_id"]] = fila["description"]

    valores = defaultdict(dict)
    with open(ruta("food_nutrient.csv"), encoding="utf-8-sig", newline="") as f:
        for fila in csv.DictReader(f):
            nombre = NUTRIENTES.get(fila["nutrient_id"])
            if not nombre:
                continue
            try:
                valores[fila["fdc_id"]][nombre] = float(fila["amount"])
            except (TypeError, ValueError):
                pass

    raciones = defaultdict(list)
    with open(ruta("food_portion.csv"), encoding="utf-8-sig", newline="") as f:
        for fila in csv.DictReader(f):
            etiqueta = MEDIDAS.get((fila.get("modifier") or "").strip().lower())
            if not etiqueta:
                continue
            try:
                gramos = float(fila["gram_weight"])
            except (TypeError, ValueError):
                continue
            if not (1.0 <= gramos <= 1500.0):
                continue
            raciones[fila["fdc_id"]].append((etiqueta, round(gramos)))

    return descripciones, valores, raciones


def kotlin(texto):
    return texto.replace("\\", "\\\\").replace('"', '\\"')


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        fallo("falta el directorio del volcado")
    directorio = sys.argv[1]

    descripciones, valores, raciones = leer_volcado(directorio)

    with open(CSV_CURADO, encoding="utf-8", newline="") as f:
        curado = list(csv.DictReader(f))
    if not curado:
        fallo(f"{CSV_CURADO} esta vacio")

    vistos_nombre, vistos_fdc, salida, avisos = set(), set(), [], []

    for fila in curado:
        nombre = (fila.get("nombre") or "").strip()
        fdc = (fila.get("fdc_id") or "").strip()
        fuente = (fila.get("fuente") or "USDA SR Legacy").strip()
        alias = [a.strip() for a in (fila.get("alias") or "").split(";") if a.strip()]

        if not nombre:
            fallo("hay una fila sin nombre en genericos.csv")
        if nombre in vistos_nombre:
            fallo(f"nombre repetido: {nombre}")
        if not fdc:
            fallo(f"{nombre}: sin fdc_id. Una fila de fuente espanola necesita soporte explicito; "
                  f"hoy el generador solo sabe leer del volcado de la USDA.")
        if fdc in vistos_fdc:
            avisos.append(f"{nombre}: el identificador {fdc} ya lo usa otro alimento")
        if fdc not in descripciones:
            fallo(f"{nombre}: el identificador {fdc} no existe en el volcado")

        v = valores.get(fdc, {})
        faltan = [k for k in ("kcal", "proteinas", "carbos", "grasas") if k not in v]
        if faltan:
            fallo(f"{nombre} ({fdc}): le faltan nutrientes en el volcado: {', '.join(faltan)}")
        if v["kcal"] <= 0:
            fallo(f"{nombre} ({fdc}): kcal = {v['kcal']}; sin energia no se puede registrar")

        # Aviso, no error: ver la nota sobre Merrill & Watt en la cabecera.
        atwater = v["proteinas"] * 4 + v["carbos"] * 4 + v["grasas"] * 9
        if v["kcal"] > 0 and abs(atwater - v["kcal"]) > 0.30 * v["kcal"] + 25:
            avisos.append(f"{nombre}: {v['kcal']:.0f} kcal frente a {atwater:.0f} por 4/4/9 "
                          f"({(atwater / v['kcal'] - 1) * 100:+.0f} %)")

        vistos_nombre.add(nombre)
        vistos_fdc.add(fdc)
        salida.append({
            "nombre": nombre, "fdc": fdc, "fuente": fuente, "alias": alias,
            "desc": descripciones[fdc], "v": v,
            "raciones": raciones.get(fdc, [])[:MAX_RACIONES],
        })

    with open(DESTINO, "w", encoding="utf-8", newline="\n") as f:
        f.write(cabecera(len(salida)))
        for item in salida:
            f.write(linea(item))
        f.write(")\n")

    print(f"{len(salida)} alimentos escritos en {os.path.normpath(DESTINO)}")
    con_racion = sum(1 for s in salida if s["raciones"])
    print(f"  con medidas caseras: {con_racion}")
    if avisos:
        print(f"\n{len(avisos)} avisos (ninguno bloquea; revisar si alguno no cuadra):")
        for a in avisos:
            print(f"  - {a}")


def linea(item):
    v = item["v"]
    alias = ", ".join(f'"{kotlin(a)}"' for a in item["alias"])
    raciones = ", ".join(f'FoodPortion("{kotlin(e)}", {g})' for e, g in item["raciones"])
    partes = [
        f'        name = "{kotlin(item["nombre"])}"',
        f'kcalPer100 = {v["kcal"]:.1f}',
        f'proteinsPer100 = {v["proteinas"]:.2f}',
        f'carbsPer100 = {v["carbos"]:.2f}',
        f'fatPer100 = {v["grasas"]:.2f}',
        f'fdcId = "{item["fdc"]}"',
    ]
    if item["fuente"] != "USDA SR Legacy":
        partes.append(f'source = "{kotlin(item["fuente"])}"')
    if alias:
        partes.append(f'aliases = listOf({alias})')
    if raciones:
        partes.append(f'portions = listOf({raciones})')
    cuerpo = ",\n            ".join(partes[1:])
    return (f'    // {item["desc"][:100]}\n'
            f'    GenericFood(\n{partes[0]},\n            {cuerpo}\n    ),\n')


def cabecera(n):
    return f'''package com.gymsmart.gymsmart.nutrition

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
 * En {n} alimentos hay unos cuantos cuyas kcal se desvian de `P*4 + HC*4 + G*9`, hasta un **+29 %**
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
'''


if __name__ == "__main__":
    main()
