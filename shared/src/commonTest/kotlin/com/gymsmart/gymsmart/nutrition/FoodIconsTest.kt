package com.gymsmart.gymsmart.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Tests de [foodIconFor].
 *
 * Lo que se comprueba aquí no es "que salga un icono bonito", sino las tres formas en que esto puede
 * salir mal de verdad: que un alimento curado se quede sin icono, que una palabra se cuele dentro de
 * otra ("repollo" / "pollo") y que el orden de la tabla ponga la preparación por delante del
 * alimento ("crema de cacahuete" saliendo como una sopa).
 */
class FoodIconsTest {

    @Test
    fun `los 90 alimentos genericos resuelven a un icono concreto`() {
        // Esta es la curación: en vez de escribir el icono al lado de cada genérico y duplicar la
        // tabla, se exige aquí que la tabla los cubra a todos. Si se añade un genérico que ninguna
        // regla reconoce, este test lo caza en el acto.
        val sinIcono = GENERIC_FOODS
            .filter { foodIconFor(it.name) == FOOD_ICON_DEFAULT }
            .map { it.name }
        assertTrue(
            sinIcono.isEmpty(),
            "Estos alimentos genéricos se quedan con el icono por defecto: $sinIcono"
        )
    }

    @Test
    fun `repollo no se confunde con pollo`() {
        // La misma trampa que ya mordió en el catálogo de cocción. Aquí daría una verdura con un
        // muslo de pollo al lado.
        assertEquals(foodIconFor("Brócoli"), foodIconFor("Repollo"))
        assertNotEquals(foodIconFor("Pechuga de pollo"), foodIconFor("Repollo"))
    }

    @Test
    fun `panceta no se confunde con pan`() {
        assertNotEquals(foodIconFor("Pan integral"), foodIconFor("Panceta de cerdo"))
    }

    @Test
    fun `el alimento manda sobre como esta preparado`() {
        // Cada uno de estos es un caso en que la regla genérica está más abajo en la tabla a
        // propósito. Si alguien reordena la tabla, estos cuatro saltan.
        assertEquals(foodIconFor("Cacahuetes"), foodIconFor("Crema de cacahuete"))
        assertEquals(foodIconFor("Pan blanco"), foodIconFor("Pan de ajo"))
        assertEquals(foodIconFor("Atun fresco"), foodIconFor("Atún claro en lata"))
        assertNotEquals(foodIconFor("Patata"), foodIconFor("Patatas fritas de bolsa"))
    }

    @Test
    fun `los suplementos no se confunden con lacteos ni con dulces`() {
        // El caso real de la captura del usuario: "Evowhey 2.0" es un suplemento.
        val suplemento = foodIconFor("Evowhey 2.0")
        assertNotEquals(FOOD_ICON_DEFAULT, suplemento)
        assertEquals(suplemento, foodIconFor("Batido de proteínas"))
        assertEquals(suplemento, foodIconFor("Barrita proteica"))
        assertNotEquals(foodIconFor("Leche entera"), foodIconFor("Batido de proteínas"))
        assertNotEquals(foodIconFor("Chocolate negro"), foodIconFor("Barrita proteica"))
    }

    @Test
    fun `las judias verdes son verdura y las blancas legumbre`() {
        assertEquals(foodIconFor("Brócoli"), foodIconFor("Judías verdes"))
        assertEquals(foodIconFor("Lentejas"), foodIconFor("Judías blancas"))
    }

    @Test
    fun `las tildes y las mayusculas dan igual`() {
        val esperado = foodIconFor("platano")
        assertEquals(esperado, foodIconFor("PLÁTANO"))
        assertEquals(esperado, foodIconFor("Plátano de Canarias"))
    }

    @Test
    fun `un nombre desconocido o vacio devuelve el icono por defecto`() {
        assertEquals(FOOD_ICON_DEFAULT, foodIconFor("Zzzxqw"))
        assertEquals(FOOD_ICON_DEFAULT, foodIconFor(""))
        assertEquals(FOOD_ICON_DEFAULT, foodIconFor("   "))
        assertEquals(FOOD_ICON_DEFAULT, foodIconFor(null))
    }

    @Test
    fun `productos de marca reales de Open Food Facts resuelven bien`() {
        // Nombres tal y como los devuelve OFF para el supermercado español.
        assertEquals(foodIconFor("Arroz blanco"), foodIconFor("Arroz Largo Hacendado"))
        assertEquals(foodIconFor("Pechuga de pollo"), foodIconFor("Pechuga de pollo fileteada"))
        assertEquals(foodIconFor("Yogur natural"), foodIconFor("Yogur griego natural Hacendado"))
        assertEquals(foodIconFor("Pasta"), foodIconFor("Macarrones integrales"))
        assertEquals(foodIconFor("Aceite de oliva"), foodIconFor("Aceite de oliva virgen extra"))
    }
}
