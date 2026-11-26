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
 * **Dos advertencias de honestidad, documentadas en `doc/10-fundamento-cientifico.md` §16.1:**
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
 *
 * ## Calorías activas (añadido despues de los pasos)
 *
 * Los pasos ayudan, pero mucha gente no mira sus pasos: mira las **calorías activas** que le da la
 * pulsera al final del dia. Ese número también sirve para elegir nivel, y de hecho sirve mejor,
 * porque recoge el entrenamiento de fuerza y la bici, que no dejan pasos.
 *
 * **No hay una tabla de kcal por nivel, y no puede haberla.** Las calorías dependen del peso, la
 * altura, la edad y el sexo: "moderado = 500 kcal" sería una cifra inventada. Lo que sí se puede es
 * derivarlas de la misma fórmula que ya usa la app, con el BMR real de la persona. Eso es
 * [activeKcalFor].
 *
 * **La corrección que hace falta para que el número sea comparable con la pulsera.** Esta es la
 * parte delicada y la razón de que esto no sea una resta trivial:
 *
 * - El PAL se define como `gasto total / BMR` (National Academies, *Dietary Reference Intakes*,
 *   cap. 4), y el gasto total incluye el **efecto térmico de los alimentos** (TEF), en torno al
 *   **10 %** del total. Es decir, `BMR x (PAL - 1)` **no son calorías de movimiento**: son
 *   movimiento más digestión.
 * - Health Connect, en cambio, entrega `ActiveCaloriesBurnedRecord`, que es solo actividad: ni
 *   basal ni digestión. Es lo que [NutritionMath.tdee] suma al BMR en modo pulsera.
 *
 * Poner las dos cifras una al lado de la otra sin descontar el TEF le diría al usuario que su
 * pulsera marca sistemáticamente un 10 % menos que el nivel que ha elegido, y la conclusión natural
 * sería bajar de nivel y comer de menos. Eso es exactamente la familia de fallo que ya se documentó
 * en `doc/07-registro-sesiones.md` (objetivo de ganar peso, déficit encubierto de 400-500 kcal/día).
 * Así que **aquí se descuenta el TEF** y lo que se muestra está ya en la escala de la pulsera.
 *
 * **Comprobación cruzada.** El resultado se puede contrastar por un camino independiente: andar
 * cuesta del orden de 0,75 kcal/kg/km brutos, lo que para 75 kg y un paso de ~0,72 m son unas
 * 0,04 kcal por paso. Los 4.000 pasos típicos de un sedentario dan ~150 kcal, y
 * [activeKcalFor] devuelve ~145 para un BMR de 1.800. Los dos caminos coinciden en orden de
 * magnitud, que es todo lo que se les puede pedir.
 *
 * **Honestidad, otra vez.** Esto hereda la incertidumbre del multiplicador, que
 * [NutritionMath.activityMultiplier] documenta como *"el eslabón más débil del calculo"*. Es
 * orientación para elegir nivel, no una medida. Y las fuentes no son unanimes sobre si el TEF está
 * dentro del PAL: la definición académica (TEE/BMR) dice que sí, varias calculadoras comerciales
 * dicen que no. Se sigue la académica, que es la que define el termino.
 */
object ActivityBenchmarks {

    /**
     * Fracción del gasto total que se va en digerir (efecto térmico de los alimentos).
     *
     * El 10 % es la cifra de consenso para una dieta mixta (National Academies, *DRI*, cap. 4:
     * *"typically accounting for ~10% of total daily energy expenditure"*). Varía con la composición
     * de la dieta —la proteína sale más cara de digerir— pero no lo suficiente como para justificar
     * pedirle al usuario datos que no tiene.
     */
    const val TEF_FRACTION = 0.10

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

    /**
     * Calorías **activas** diarias que corresponden al nivel [activityLevel] para alguien con este
     * [bmr], en la misma escala que las que reporta la pulsera.
     *
     * Devuelve `null` si el nivel no tiene multiplicador (el modo pulsera, que mide en vez de
     * estimar) o si el BMR no es un número utilizable.
     *
     * El desarrollo, partiendo de `gasto = BMR x PAL`:
     * ```
     * activas = gasto - BMR - TEF
     *         = BMR x PAL - BMR - TEF_FRACTION x BMR x PAL
     *         = BMR x (PAL x (1 - TEF_FRACTION) - 1)
     * ```
     * Se recorta a cero por abajo: un multiplicador suficientemente pequeño daria negativo, y decir
     * "quemas -20 kcal" sería peor que no decir nada.
     */
    fun activeKcalFor(activityLevel: String, bmr: Double): Double? {
        if (stepsFor(activityLevel) == null) return null   // pulsera o nivel desconocido
        if (bmr <= 0.0 || !bmr.isFinite()) return null
        val pal = NutritionMath.activityMultiplier(activityLevel)
        return (bmr * (pal * (1.0 - TEF_FRACTION) - 1.0)).coerceAtLeast(0.0)
    }

    /**
     * Lo mismo que [activeKcalFor] pero ya formateado para la interfaz ("~710 kcal activas/día"), o
     * `null` si no aplica.
     *
     * Se redondea a la **decena** a propósito. Escribir "711 kcal" afirmaría una precisión de tres
     * cifras sobre una estimación cuyo eslabón más débil tiene un margen de 250-350 kcal entre dos
     * niveles contiguos; es el mismo criterio de redondeo honesto que ya aplica el resto de la app.
     * El símbolo ~ delante termina de decir que es una orientación.
     */
    fun activeKcalLabelFor(activityLevel: String, bmr: Double): String? {
        val kcal = activeKcalFor(activityLevel, bmr) ?: return null
        val decenas = (kotlin.math.round(kcal / 10.0) * 10).toInt()
        return "~$decenas kcal activas/día"
    }

    /**
     * La línea de ayuda completa de un nivel: pasos y, si se conoce el BMR, calorías activas.
     *
     * Es lo que pintan el perfil y el onboarding, y está aquí y no allá para que las dos pantallas
     * no puedan decir cosas distintas del mismo nivel.
     */
    fun hintFor(activityLevel: String, bmr: Double? = null): String? {
        val pasos = stepsFor(activityLevel) ?: return null
        val kcal = bmr?.let { activeKcalLabelFor(activityLevel, it) } ?: return pasos
        return "$pasos · $kcal"
    }

    // ── Del día de hoy al nivel (pantalla de inicio) ─────────────────────────

    /**
     * Límite inferior de pasos de cada nivel, en orden. Es la misma tabla que [stepsFor] pero en
     * número, para poder clasificar un día concreto. Si se cambia un corte, se cambia en los dos
     * sitios: `ActivityBenchmarksTest` comprueba que coinciden.
     */
    private val LOWER_BOUNDS: List<Pair<String, Long>> = listOf(
        "sedentary"   to 0L,
        "light"       to 5_000L,
        "moderate"    to 7_500L,
        "active"      to 10_000L,
        "very_active" to 12_500L
    )

    /**
     * El nivel al que corresponden los [steps] de **un día**.
     *
     * Ojo con lo que esto es y lo que no: un día suelto no es tu nivel de actividad, que es una
     * media. La pantalla de inicio lo usa para decir "hoy vas en ritmo de Moderado", no para cambiar
     * el nivel del perfil. Pasos negativos (un dato roto) se tratan como cero.
     */
    fun levelForSteps(steps: Long): String =
        LOWER_BOUNDS.last { steps.coerceAtLeast(0L) >= it.second }.first

    /**
     * Pasos a partir de los cuales empieza el nivel siguiente al de [steps], o `null` si ya está en
     * el máximo. Es la meta que tiene sentido enseñar en el inicio: la app no tiene un objetivo de
     * pasos configurado, y en vez de inventarse uno (los famosos 10.000) usa el siguiente corte del
     * mismo baremo que ya usa el perfil.
     */
    fun nextLevelThreshold(steps: Long): Long? =
        LOWER_BOUNDS.firstOrNull { steps.coerceAtLeast(0L) < it.second }?.second

    /** Nombre en español del nivel, para la interfaz. `null` si no es un nivel con baremo. */
    fun labelFor(activityLevel: String): String? = when (activityLevel) {
        "sedentary"   -> "Sedentario"
        "light"       -> "Ligero"
        "moderate"    -> "Moderado"
        "active"      -> "Activo"
        "very_active" -> "Muy activo"
        else          -> null
    }
}
