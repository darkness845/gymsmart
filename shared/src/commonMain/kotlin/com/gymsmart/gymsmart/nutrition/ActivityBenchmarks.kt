package com.gymsmart.gymsmart.nutrition

/**
 * Baremo de pasos diarios para ayudar a elegir el nivel de actividad.
 *
 * **Por qué existe.** Elegir entre "ligero" y "moderado" a ojo es la mayor fuente de error del
 * cálculo: entre dos escalones contiguos hay 250-350 kcal/día, y las descripciones ("1-3 días de
 * ejercicio por semana") no dicen nada sobre lo que uno hace el resto del día. Un número de pasos sí
 * es comprobable: el móvil ya lo cuenta.
 *
 * **De dónde salen los rangos.** De la clasificación por podómetro de **Tudor-Locke y Bassett,
 * "How many steps/day are enough? Preliminary pedometer indices for public health", Sports Medicine
 * 2004; 34(1):1-8**, que es la referencia estándar en salud pública y la citan sistemáticamente otros
 * trabajos.
 *
 * **Dos advertencias de honestidad, documentadas en `doc/10-fundamento-cientifico.md` §14:**
 *
 * 1. El texto original no se pudo leer directamente: tanto el PDF como las versiones en PubMed están
 *    tras verificación anti-robot. Los cortes están tomados de varias fuentes secundarias
 *    independientes que coinciden entre sí; las bandas bajas son unánimes y en la frontera superior
 *    hay literatura posterior (la revisión de 2008) que mueve el último corte a 12.000.
 * 2. **La correspondencia entre estas bandas y los multiplicadores 1,2 / 1,375 / 1,55 / 1,725 / 1,9
 *    es nuestra, no está publicada.** Son dos escalas ordinales de cinco niveles del mismo concepto y
 *    alinearlas es razonable, pero ningún trabajo dice que "7.500-9.999 pasos" equivalga a 1,55. Por
 *    eso esto se presenta como **orientación para elegir**, nunca como el dato del que sale el gasto.
 *
 * Si hay pulsera, nada de esto hace falta: se mide y punto ([NutritionMath.ACTIVITY_WEARABLE]).
 */
object ActivityBenchmarks {

    /**
     * Pasos diarios orientativos del nivel [activityLevel], ya formateados para la interfaz, o `null`
     * si ese nivel no tiene baremo (el modo pulsera, que mide en vez de estimar).
     */
    fun stepsFor(activityLevel: String): String? = when (activityLevel) {
        "sedentary"   -> "menos de 5.000 pasos/día"
        "light"       -> "5.000–7.499 pasos/día"
        "moderate"    -> "7.500–9.999 pasos/día"
        "active"      -> "10.000–12.499 pasos/día"
        "very_active" -> "más de 12.500 pasos/día"
        else          -> null
    }

    /** El nombre que la clasificación original le da a cada banda, por si se quiere mostrar. */
    fun bandName(activityLevel: String): String? = when (activityLevel) {
        "sedentary"   -> "sedentary"
        "light"       -> "low active"
        "moderate"    -> "somewhat active"
        "active"      -> "active"
        "very_active" -> "highly active"
        else          -> null
    }
}
