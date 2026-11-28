package com.gymsmart.gymsmart.nutrition

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Única implementación del cálculo nutricional, compartida por servidor y cliente.
 *
 * Vive en `shared` a propósito: antes esta fórmula estaba duplicada en tres sitios
 * (`ProfileService.computeTargets` en el servidor, `OnboardingScreen.StepSummary` y `NutritionScreen`
 * en el cliente) y ya habían divergido — las pantallas ofrecían ritmos distintos entre sí. El cliente
 * necesita calcular en local para la vista previa del onboarding, antes de que exista perfil que
 * mandar al servidor, así que no basta con dejarlo solo en el servidor.
 *
 * **Cada constante de este archivo está justificada en `doc/10-fundamento-cientifico.md`**, con su
 * fuente y su nivel de confianza. No añadir números aquí sin documentarlos allí.
 */
object NutritionMath {

    // ── Constantes ────────────────────────────────────────────────────────────

    /**
     * Densidad energética del tejido adiposo (§2.1). Válida en horizonte de semanas; sobreestima a
     * medio plazo porque ignora la adaptación metabólica, motivo por el que [measuredTdee] existe.
     * Para ganancia (§2.2) es una estimación de tejido mixto, no una constante exacta.
     */
    const val KCAL_PER_KG = 7700.0

    /**
     * Fracción del gasto total que se va en digerir (efecto térmico de los alimentos).
     *
     * El 10% es la cifra de consenso para una dieta mixta (National Academies, *DRI*, cap. 4:
     * *"typically accounting for ~10% of total daily energy expenditure"*). Varía con la composición de
     * la dieta —la proteína sale más cara de digerir— pero no lo suficiente como para justificar pedirle
     * al usuario datos que no tiene.
     *
     * **Vivía en [ActivityBenchmarks] y se movió aquí al arreglar C-1**, y el motivo es el propio
     * hallazgo: el proyecto afirmaba en un archivo que el PAL incluye el TEF y que la pulsera no, y en
     * el otro construía el gasto de la pulsera como si no existiera. Teniendo la constante un solo
     * dueño —el que calcula el gasto— esa divergencia no puede volver a abrirse en silencio. Además
     * `ActivityBenchmarks` ya dependía de `NutritionMath`, así que la referencia inversa dejaba las dos
     * dependiendo la una de la otra.
     */
    const val TEF_FRACTION = 0.10

    const val GOAL_LOSE     = "lose_fat"
    const val GOAL_GAIN     = "gain_muscle"
    const val GOAL_MAINTAIN = "maintain"

    // GOAL_RECOMP ("recomp") se eliminó (decisión del usuario, 2026-11-23). La auditoría (AUD-7 en
    // doc/11-auditoria-kcal.md) encontró que en el código no era un objetivo distinto: su única
    // diferencia con GOAL_LOSE era el tope de ritmo, sin nada de lo que la recomposición corporal
    // de verdad exige (entrenamiento de fuerza, seguimiento de cintura). En vez de implementarla o
    // de dejarla como un déficit con otro nombre, se retira. Un perfil antiguo con goal = "recomp" en
    // BBDD cae en la rama `else` de los `when` exhaustivos (equivalente a mantenimiento, sin ritmo),
    // y la migración de ProfileService.initTable() lo reescribe a "maintain" en el próximo arranque.
    val ALL_GOALS = listOf(GOAL_LOSE, GOAL_GAIN, GOAL_MAINTAIN)

    const val ACTIVITY_WEARABLE = "wearable"

    val ALL_ACTIVITY_LEVELS =
        listOf("sedentary", "light", "moderate", "active", "very_active", ACTIVITY_WEARABLE)

    /** Ventana de días completos usada para la media de calorías activas de la pulsera (§1.3). */
    const val WEARABLE_AVERAGE_DAYS = 14

    /**
     * Fracción mínima de [WEARABLE_AVERAGE_DAYS] con datos antes de confiar en la media de la pulsera.
     *
     * Antes el único guard era "si hay al menos un día, la media es ese día": quien instalara la app
     * el día después de una excursión fijaba su presupuesto sobre esa única jornada atípica hasta que
     * la ventana se llenara (AUD-13 en doc/11-auditoria-kcal.md). Con 0,5 hacen falta al menos 7 de los
     * 14 días para que `hasData` sea `true`; por debajo de eso el modo pulsera cae al respaldo
     * sedentario de [tdee], que es la estimación conservadora mientras no haya histórico suficiente.
     */
    const val WEARABLE_MIN_COVERAGE = 0.5

    /** Días mínimos con dato, ya redondeado hacia arriba: lo que de verdad comprueba el código. */
    val WEARABLE_MIN_DAYS_WITH_DATA: Int =
        kotlin.math.ceil(WEARABLE_AVERAGE_DAYS * WEARABLE_MIN_COVERAGE).toInt()

    // ── Metabolismo ───────────────────────────────────────────────────────────

    /** Mifflin-St Jeor (§1.1): la más fiable sin conocer composición corporal. Error típico ±10%. */
    fun bmr(sex: String, weightKg: Double, heightCm: Double, age: Int): Double =
        if (sex == "male") 10.0 * weightKg + 6.25 * heightCm - 5.0 * age + 5.0
        else               10.0 * weightKg + 6.25 * heightCm - 5.0 * age - 161.0

    /**
     * Multiplicadores de actividad (§1.2). **Es el eslabón más débil del cálculo**: convención sin
     * validación sólida, y entre dos escalones contiguos hay 250-350 kcal/día. Por eso el autoajuste
     * de [measuredTdee] importa más en este camino que en el de pulsera.
     */
    fun activityMultiplier(activityLevel: String): Double = when (activityLevel) {
        "sedentary"   -> 1.2
        "light"       -> 1.375
        "moderate"    -> 1.55
        "active"      -> 1.725
        "very_active" -> 1.9
        else          -> 1.2
    }

    /**
     * Gasto total diario.
     *
     * En modo pulsera se parte del BMR y se suma la **media de días completos** de calorías activas,
     * no la lectura del día en curso: esa va sistemáticamente por debajo porque la app de la pulsera
     * sincroniza por lotes, y comer contra ella produce déficit encubierto (§1.3).
     *
     * `ActiveCaloriesBurnedRecord` excluye el basal, así que sumarlo al BMR no duplica nada.
     */
    fun tdee(bmr: Double, activityLevel: String, avgActiveKcal: Double = 0.0): Double =
        if (activityLevel == ACTIVITY_WEARABLE) {
            // Sin datos de la pulsera, `bmr + 0` equivaldría a afirmar que la persona está en coma:
            // nadie que se levante de la cama gasta su metabolismo basal. Eso hacía que el objetivo
            // colapsara al BMR y el déficit desapareciera entero. Mientras no haya histórico (los
            // primeros días, o si Health Connect no devuelve nada) se asume sedentario, que es la
            // estimación conservadora, y el autoajuste corrige después con datos reales.
            if (avgActiveKcal > 0) {
                // **El TEF (C-1).** `bmr + activas` omitía la digestión y daba un gasto un 10% más bajo
                // que el modo fórmula **para la misma actividad real**: 2396 frente a 2662 en el perfil
                // de referencia. Las consecuencias eran las dos caras del fallo que originó toda esta
                // línea de trabajo: en pérdida, el tope del 25% se aplicaba sobre un gasto bajo y el
                // déficit real llegaba al 32,5%; en ganancia, el superávit quedaba en +43 kcal, o sea
                // mantenimiento disfrazado ("quiero ganar y no gano").
                //
                // `ActiveCaloriesBurnedRecord` es gasto por movimiento: no incluye basal (por eso se
                // suma al BMR sin duplicar) **ni digestión**. El PAL del modo fórmula sí la incluye, por
                // definición (DRI cap. 4: PAL = TEE/BMR, con el TEF dentro del TEE). Así que para que
                // los dos modos midan lo mismo hay que devolver el TEF que a la pulsera le falta.
                //
                // La comprobación de que la constante es la correcta es algebraica, no empírica, y hay
                // un test que la fija: `ActivityBenchmarks.activeKcalFor` genera las activas de un nivel
                // como `BMR × (PAL × (1 − TEF) − 1)`, así que al reinyectarlas aquí
                // `(BMR + activas) / (1 − TEF) = BMR × PAL` **exactamente**. Los dos modos coinciden.
                //
                // La forma rigurosa sería `(BMR + activas + 0,10 × Δ)/0,90`, porque el TEF es el 10% de
                // lo que se *ingiere* y no de lo que se gasta; son ~60 kcal de diferencia y no merece
                // la complejidad frente a una pulsera con ±20-30% de error (O'Driscoll 2020).
                //
                // El suelo sedentario se aplica también aquí (P1-2): antes esta rama era la única sin
                // él, así que una pulsera que reportara pocas activas daba un gasto **por debajo** del
                // respaldo "no tengo datos" de la rama de al lado — empezar a tener datos bajaba el
                // objetivo 193 kcal. Tras el arreglo del TEF casi nunca muerde (hace falta que las
                // activas sean menores que el 8% del BMR), pero la incoherencia no debe quedar abierta.
                max(
                    (bmr + avgActiveKcal) / (1.0 - TEF_FRACTION),
                    bmr * activityMultiplier("sedentary")
                )
            } else {
                bmr * activityMultiplier("sedentary")
            }
        } else {
            bmr * activityMultiplier(activityLevel)
        }

    /**
     * Tope de déficit como fracción del gasto total.
     *
     * Sustituye al suelo anterior ("nunca por debajo del BMR"), que era **incompatible con los propios
     * rangos de ritmo**: una persona sedentaria tiene un gasto de solo 1,2 × BMR, así que el margen
     * hasta su basal es el 20% de su BMR — menos de lo que pide el ritmo más suave que la app ofrece.
     * El resultado era que el suelo se comía el déficit entero y el objetivo salía igual que el BMR.
     *
     * Un tope en fracción del gasto escala con cada persona y nunca puede anular el déficit. La
     * dirección está respaldada (a mayor déficit, más masa magra se pierde); el 25% concreto es
     * convención de práctica, no una cifra de un estudio.
     */
    const val MAX_DEFICIT_FRACTION = 0.25

    /**
     * Tope de **superávit** como fracción del gasto total.
     *
     * Simétrico de [MAX_DEFICIT_FRACTION], y faltaba: la auditoría de `doc/11-auditoria-kcal.md` (AUD-6)
     * encontró que `rateLimited` no podía ser `true` en ganancia y que el superávit llegaba al +32% en
     * perfiles pesados y sedentarios. §10.3 fija +10-20% sobre mantenimiento citando Iraki 2019, y
     * afirma que por encima lo que se gana es grasa: se toma el extremo alto de esa banda.
     *
     * Mismo estatus que su homólogo: la dirección está respaldada, el 20% concreto es convención.
     */
    const val MAX_SURPLUS_FRACTION = 0.20

    // ── Suelo por disponibilidad energética (§7.2) ────────────────────────────

    /**
     * Umbral de disponibilidad energética, en kcal por kg de masa magra y día.
     *
     * §7.2 identifica este marco como **el correcto** para un mínimo de ingesta, y lo descartaba por no
     * tener masa magra. La auditoría (AUD-1) señaló que ese descarte era rigor asimétrico: el sistema ya
     * acepta un BMR con ±10% de error para **fijar** el objetivo, así que una estimación igual de cruda
     * es admisible para **avisar**. Sin este suelo el objetivo colapsaba a `0,90 × BMR` en sedentarios.
     *
     * El umbral de 30 procede de estudios en **mujeres no atletas**; para hombres §7.2 sugiere 20-25, y
     * se toma el extremo alto (25) por ser el más protector de esa banda.
     */
    const val EA_FLOOR_FEMALE = 30.0
    const val EA_FLOOR_MALE   = 25.0

    /**
     * Suelo **absoluto** de ingesta, en kcal/día, por debajo del cual no se emite ningún objetivo.
     *
     * 800 kcal es el umbral de las dietas de muy bajas calorías (VLCD), que NICE CG189 reserva a
     * supervisión médica. La app no tiene forma de ofrecer esa supervisión, así que no debe emitir un
     * objetivo ahí jamás, por mucho que la disponibilidad energética lo permita.
     *
     * **Es una red, no el arreglo de fondo** (C-2 en doc/12-auditoria-comite-kcal-2026-11-27.md). Solo
     * muerde donde el propio suelo de disponibilidad energética cae por debajo de 800, es decir con una
     * masa magra estimada por debajo de ~26,7 kg en mujeres y ~32 kg en hombres: cuerpos diminutos, o
     * datos mal introducidos. Los casos que motivaron el hallazgo (mujer de 80 años y 42 kg → 806 kcal;
     * de 75 años y 45 kg → 866) quedan **por encima** de este suelo y los sigue gobernando
     * [energyAvailabilityFloor]; lo que les llega es el aviso de [SOFT_FLOOR_FEMALE].
     *
     * La causa de fondo de esos objetivos bajos es que el suelo de disponibilidad energética no resta el
     * gasto de ejercicio y que [TargetResult.lowBmiGuard] usa un umbral de adulto (IMC 18,5) también en
     * mayores — las dos están en el Bloque B del plan, sin abordar todavía.
     */
    const val ABSOLUTE_FLOOR_KCAL = 800.0

    /**
     * Umbrales de **aviso** (no bloqueantes) para un objetivo de pérdida, en kcal/día.
     *
     * Vienen de AHA/ACC/TOS 2013 (Jensen et al.), que sitúa las dietas de pérdida en 1200-1500 kcal en
     * mujeres y 1500-1800 en hombres. **Es un criterio distinto** al de [EA_FLOOR_FEMALE]: ese marco
     * protege la función endocrina y mide kcal por kg de masa magra; este mide si es realista cubrir los
     * micronutrientes con tan poca comida. Son compatibles y pueden discrepar, y §7.1 de
     * doc/10-fundamento-cientifico.md descartaba este marco confundiéndolo con el otro.
     *
     * Avisan en vez de bloquear a propósito: por debajo de 1200 kcal un objetivo puede seguir siendo
     * razonable (la mujer de 50 kg sedentaria sale a 1126 con un déficit de solo el 21% y disponibilidad
     * energética de 30), así que recortarlo sería peor que advertirlo. Lo que no es aceptable es emitirlo
     * **en silencio**, que es lo que pasaba hasta ahora.
     */
    const val SOFT_FLOOR_FEMALE = 1200
    const val SOFT_FLOOR_MALE   = 1500

    /** Umbral de aviso que corresponde a este sexo. Ver [SOFT_FLOOR_FEMALE]. */
    fun softFloorFor(sex: String): Int = if (sex == "male") SOFT_FLOOR_MALE else SOFT_FLOOR_FEMALE

    /**
     * % de grasa corporal estimado a partir del IMC (Deurenberg 1991).
     *
     * ⚠️ **Convención, con error grande en el individuo** (±5 puntos de grasa es normal). No sirve para
     * mostrarlo al usuario ni para calcular el BMR — §9.2 descarta expresamente estimar grasa por
     * antropometría para esos usos. Aquí solo alimenta un **suelo de seguridad**, donde un error de esa
     * magnitud mueve el suelo unas decenas de kcal y sigue siendo infinitamente mejor que no tener suelo.
     */
    fun bodyFatPctEstimate(sex: String, weightKg: Double, heightCm: Double, age: Int): Double {
        val heightM = heightCm / 100.0
        if (heightM <= 0.0) return 0.0
        val bmi = weightKg / (heightM * heightM)
        val sexTerm = if (sex == "male") 1.0 else 0.0
        return (1.20 * bmi + 0.23 * age - 10.8 * sexTerm - 5.4).coerceIn(3.0, 70.0)
    }

    /** Masa magra estimada, en kg. Derivada de [bodyFatPctEstimate], con su mismo nivel de confianza. */
    fun leanMassEstimate(sex: String, weightKg: Double, heightCm: Double, age: Int): Double =
        weightKg * (1.0 - bodyFatPctEstimate(sex, weightKg, heightCm, age) / 100.0)

    /**
     * Suelo absoluto de ingesta diaria, por disponibilidad energética.
     *
     * El marco de §7.2 es `DE = (ingesta − gasto_de_ejercicio) / kg MM`, lo que sugeriría un suelo de
     * `umbral × MM + gasto_de_ejercicio`. **Deliberadamente no se resta el gasto de ejercicio**, por dos
     * razones:
     *
     * 1. No lo conocemos. En modo fórmula el multiplicador de actividad mezcla entrenamiento con el
     *    resto del movimiento diario. Y en modo pulsera, `ActiveCaloriesBurnedRecord` es **toda** la
     *    energía no basal (andar, subir escaleras, moverse por casa), no el entrenamiento: tratarla como
     *    "gasto de ejercicio" sobreestima el término, y con él el suelo.
     * 2. Hacerlo producía un suelo **por encima del mantenimiento** en perfiles activos, así que un
     *    objetivo de perder grasa salía con superávit. Es exactamente el fallo que originó toda esta
     *    línea de trabajo, en espejo. (Lo detectó el test `el objetivo va en la direccion elegida`.)
     *
     * **Consecuencia honesta: así el suelo es una cota inferior, queda del lado poco protector.** Es un
     * respaldo contra objetivos absurdos, no el límite que gobierna el día a día — de eso se ocupa
     * [MAX_DEFICIT_FRACTION]. Mejor una cota conservadora y estable que una precisión falsa.
     */
    fun energyAvailabilityFloor(sex: String, weightKg: Double, heightCm: Double, age: Int): Double {
        val threshold = if (sex == "male") EA_FLOOR_MALE else EA_FLOOR_FEMALE
        return threshold * leanMassEstimate(sex, weightKg, heightCm, age)
    }

    /**
     * El suelo de seguridad que de verdad gobierna: **el más restrictivo** de los dos que existen, el
     * de disponibilidad energética y el absoluto de [ABSOLUTE_FLOOR_KCAL].
     *
     * Se combinan con `max` porque los dos son cotas inferiores por motivos distintos (función
     * endocrina y suficiencia de micronutrientes), así que respetar los dos significa respetar el mayor.
     *
     * **Sigue siendo un suelo "en crudo"**: no está acotado al mantenimiento. Quien lo use para fijar un
     * objetivo tiene que aplicarle después el `min(…, gasto)` que impide que un suelo alto convierta una
     * pérdida en superávit — ver [computeTargets].
     */
    fun safetyFloor(sex: String, weightKg: Double, heightCm: Double, age: Int): Double =
        max(energyAvailabilityFloor(sex, weightKg, heightCm, age), ABSOLUTE_FLOOR_KCAL)

    // ── Ritmo ─────────────────────────────────────────────────────────────────

    /**
     * **Tope duro** del ritmo, en % de peso corporal por semana (magnitud, sin signo).
     *
     * Este es el único límite real: la evidencia acota el ritmo **por arriba**, no por abajo. Pasar de
     * 1 %/semana en déficit cuesta masa magra (Garthe 2011: a 1,4 %/sem no se ganó nada de magra,
     * frente a +2,1 % a 0,7 %/sem), y pasar de 0,5 %/semana en superávit solo añade grasa, porque la
     * síntesis muscular tiene un techo que no se acelera comiendo más (Iraki et al. 2019).
     *
     * Devuelve `null` para mantenimiento, que no tiene ritmo.
     */
    fun maxRateFor(goal: String): Double? = when (goal) {
        GOAL_LOSE -> 1.0
        GOAL_GAIN -> 0.5
        else      -> null
    }

    /**
     * Banda **recomendada** (magnitud, sin signo). Helms 2014 sitúa 0,5-1 %/sem como el rango que
     * maximiza la retención de masa magra en un déficit.
     *
     * Ojo con la diferencia respecto a [maxRateFor]: ir **más lento** que esta banda no tiene ningún
     * problema — se retiene músculo igual o mejor, solo se tarda más. Por eso el mínimo de la banda se
     * dibuja como sugerencia y no se bloquea. Tratarlo como un límite (que es lo que hacía la primera
     * versión) impedía ritmos perfectamente razonables como 0,2 kg/semana.
     */
    fun recommendedBandFor(goal: String): ClosedFloatingPointRange<Double>? = when (goal) {
        GOAL_LOSE -> 0.5..1.0
        GOAL_GAIN -> 0.25..0.5
        else      -> null
    }

    /** Signo que corresponde al objetivo: negativo para déficit, positivo para superávit. */
    fun signFor(goal: String): Double = when (goal) {
        GOAL_LOSE -> -1.0
        GOAL_GAIN -> 1.0
        else      -> 0.0
    }

    /** Ritmo por defecto: el punto medio de la banda recomendada, ya con su signo. */
    fun defaultRateFor(goal: String): Double = when (goal) {
        GOAL_LOSE -> -0.75
        GOAL_GAIN -> 0.375
        else      -> 0.0
    }

    /**
     * Valida contra el tope duro y el signo. No exige llegar a la banda recomendada: cualquier ritmo
     * más lento es válido.
     */
    fun isRateValid(goal: String, ratePct: Double): Boolean {
        val max = maxRateFor(goal) ?: return ratePct == 0.0
        if (abs(ratePct) > max + 1e-9) return false
        val sign = signFor(goal)
        // Un ritmo de 0 en perder/ganar significaría que el objetivo elegido no hace nada: solo
        // mantenimiento (que no llega a maxRateFor) tiene ritmo 0 como válido.
        if (ratePct == 0.0) return false
        return ratePct * sign > 0
    }

    /** True si el ritmo cae dentro de la banda que maximiza la retención de masa magra. */
    fun isRateRecommended(goal: String, ratePct: Double): Boolean {
        val band = recommendedBandFor(goal) ?: return ratePct == 0.0
        return abs(ratePct) in band
    }

    /** Convierte un ritmo en % de peso corporal por semana al ajuste calórico diario (§3.3). */
    fun dailyDeltaFor(ratePct: Double, weightKg: Double): Double =
        ratePct / 100.0 * weightKg * KCAL_PER_KG / 7.0

    /** Inversa de [dailyDeltaFor]: qué ritmo semanal implica un ajuste calórico diario dado. */
    fun rateForDailyDelta(dailyDelta: Double, weightKg: Double): Double =
        if (weightKg <= 0.0) 0.0 else dailyDelta * 7.0 / (weightKg * KCAL_PER_KG) * 100.0

    /** Kg por semana que implica un ritmo porcentual, para poder etiquetarlo con la verdad. */
    fun kgPerWeekFor(ratePct: Double, weightKg: Double): Double = ratePct / 100.0 * weightKg

    /** Lo que implica un ritmo concreto, para el texto bajo el deslizador. */
    data class RateInfo(
        val ratePct:       Double,
        val kgPerWeek:     Double,
        val kcalPerDay:    Int,
        val isRecommended: Boolean
    ) {
        /** "0.4 kg/semana", siempre en positivo: la dirección ya la indica el objetivo. */
        val kgLabel: String get() = "${formatOneDecimal(abs(kgPerWeek))} kg/semana"

        /** "−415 kcal/día". */
        val kcalLabel: String
            get() = "${if (kcalPerDay < 0) "−" else "+"}${abs(kcalPerDay)} kcal/día"

        /** "0.53% de tu peso". */
        val pctLabel: String get() = "${formatTwoDecimals(abs(ratePct))}% de tu peso"
    }

    /**
     * Traduce un ritmo a kg/semana y kcal/día con el peso real de la persona.
     *
     * Antes la interfaz ofrecía kcal fijas con una etiqueta de kg/semana escrita a mano que no
     * cuadraba: "−500 kcal/día ≈ 1 kg/sem" prometía más del doble de lo que da (0,45 kg/sem). Aquí
     * todo se deriva del mismo número, así que no puede mentir.
     */
    fun describeRate(goal: String, ratePct: Double, weightKg: Double): RateInfo = RateInfo(
        ratePct       = ratePct,
        kgPerWeek     = kgPerWeekFor(ratePct, weightKg),
        kcalPerDay    = dailyDeltaFor(ratePct, weightKg).roundToInt(),
        isRecommended = isRateRecommended(goal, ratePct)
    )

    /** Ritmo máximo en kg/semana para este objetivo y peso, que es el final del deslizador. */
    fun maxKgPerWeekFor(goal: String, weightKg: Double): Double? =
        maxRateFor(goal)?.let { kgPerWeekFor(it, weightKg) }

    /**
     * Ritmo máximo **realmente alcanzable** (magnitud, sin signo), que es el que debe terminar el
     * deslizador.
     *
     * [maxRateFor] es el tope por composición corporal; [MAX_DEFICIT_FRACTION] / [MAX_SURPLUS_FRACTION]
     * y el suelo de disponibilidad energética son topes por magnitud del ajuste. **Son límites distintos
     * que compiten, y en la mayoría de perfiles sedentarios muerde primero el segundo.**
     *
     * Hasta ahora el deslizador solo conocía el primero, así que su tercio superior no cambiaba el
     * objetivo mientras las etiquetas prometían hasta un 54% más de pérdida semanal (AUD-5). Ese es
     * exactamente el género de fallo que motivó la reescritura del 2026-11-01: una etiqueta que promete
     * lo que el cálculo no entrega. Con esto, el extremo del deslizador vuelve a ser el tope de verdad.
     *
     * @param tdee gasto con el que se va a calcular (ya sea de fórmula o medido).
     * @param energyFloor suelo de [energyAvailabilityFloor] para este cuerpo. **El suelo absoluto de
     *   [ABSOLUTE_FLOOR_KCAL] se aplica aquí dentro**, así que quien llame no tiene que conocerlo: las
     *   pantallas pasan solo el de disponibilidad energética y siguen obteniendo el tope correcto. Si
     *   este parámetro no absorbiera el suelo absoluto, el extremo del deslizador volvería a prometer un
     *   ritmo que [computeTargets] no entrega — que es exactamente AUD-5.
     * @return la magnitud máxima, o `null` en mantenimiento (que no tiene ritmo). Puede ser **0.0** si
     *   no queda margen ninguno, que es el caso de `noSafeDeficit`.
     */
    fun effectiveMaxRateFor(
        goal:        String,
        weightKg:    Double,
        tdee:        Double,
        energyFloor: Double
    ): Double? {
        val hard = maxRateFor(goal) ?: return null
        if (tdee <= 0.0 || weightKg <= 0.0) return hard

        val maxDelta = if (signFor(goal) > 0) {
            tdee * MAX_SURPLUS_FRACTION
        } else {
            // El suelo puede morder antes que el tope porcentual, así que manda el más restrictivo.
            val floor = max(energyFloor, ABSOLUTE_FLOOR_KCAL)
            min(tdee * MAX_DEFICIT_FRACTION, max(0.0, tdee - floor))
        }
        return min(hard, abs(rateForDailyDelta(maxDelta, weightKg)))
    }

    /** Encaja un ritmo dentro de lo permitido, por si llega un valor fuera de rango. */
    fun clampRate(goal: String, ratePct: Double): Double {
        val max = maxRateFor(goal) ?: return 0.0
        val sign = signFor(goal)
        val magnitude = abs(ratePct).coerceAtMost(max)
        return magnitude * sign
    }

    // ── Objetivo diario y macros ──────────────────────────────────────────────

    data class TargetResult(
        val bmr:            Int,
        val tdee:           Int,
        val targetKcal:     Int,
        val proteinG:       Int,
        val carbsG:         Int,
        val fatG:           Int,
        val bmi:            Double,
        val bmiCategory:    String,
        /**
         * Un límite de seguridad recortó el ritmo pedido: hay que decírselo al usuario (§7.3).
         *
         * Tiene **banda muerta** ([RATE_LIMIT_DEADBAND_KCAL]): un recorte de 3 kcal/día no es
         * información, es ruido, y disparaba un aviso alarmante por nada (AUD-20).
         */
        val rateLimited:    Boolean,
        /** Ritmo pedido, en % de peso corporal por semana. */
        val requestedRate:  Double,
        /**
         * Ritmo **sostenido** realmente alcanzable tras aplicar los límites. Igual al pedido si no se
         * recortó. No incluye el arrastre del día, que es puntual y no un ritmo.
         */
        val effectiveRate:  Double,
        /** True si el gasto usado viene de medición real y no de la fórmula (§5). */
        val tdeeIsMeasured: Boolean,
        /** Un suelo de seguridad mordió: el objetivo es ese suelo. Ver [energyFloor]. */
        val energyFloorHit: Boolean,
        /**
         * El suelo que **de verdad gobernó** el objetivo, para poder nombrarlo en la interfaz.
         *
         * Antes devolvía el suelo en crudo, sin acotar al mantenimiento, así que la interfaz podía
         * decir "no bajamos de 2025 kcal" junto a un objetivo de 1925: un suelo por encima del número
         * al que acompaña no es información, es una contradicción a la vista del usuario. Ahora es el
         * valor ya acotado, el mismo que entra en el `max` final del objetivo, de modo que
         * `energyFloor <= targetKcal` se cumple siempre.
         */
        val energyFloor:    Int,
        /** Arrastre del día realmente aplicado, ya acotado y ya filtrado por modo pulsera. */
        val appliedCarryOver: Int,
        /**
         * El suelo por disponibilidad energética alcanza o supera el mantenimiento: **no hay déficit
         * seguro posible** para este cuerpo y este gasto, y el objetivo se queda en mantenimiento.
         *
         * Hay que decírselo al usuario: es la diferencia entre "voy despacio" y "no voy". Si aparece de
         * forma estable, lo que toca no es comer menos, es aumentar el gasto.
         */
        val noSafeDeficit:  Boolean,
        /**
         * Ya está en bajo peso (IMC < 18,5) y el objetivo elegido pide más déficit (AUD-8). El objetivo
         * se cae a mantenimiento igual que con [noSafeDeficit] — es el mismo mecanismo con un motivo
         * distinto que explicar: aquí no es que falte margen de gasto, es que seguir recortando no
         * tiene cobertura clínica sin supervisión. No bloquea el ritmo que el usuario eligió en la
         * interfaz; solo deja de traducirlo en un déficit real mientras siga en este rango.
         */
        val lowBmiGuard:    Boolean,
        /**
         * El reparto de macros no pudo cumplir el objetivo por kg de proteína con el objetivo
         * calórico dado: tuvo que recortar primero la grasa y, si no bastaba, también la proteína por
         * debajo del gramaje objetivo (AUD-17). Cuadrar las kcal no es lo mismo que ser un reparto
         * razonable — esto avisa cuando deja de serlo.
         */
        val macrosDegraded: Boolean,
        /**
         * El objetivo de pérdida queda por debajo del umbral de aviso de su sexo
         * ([SOFT_FLOOR_FEMALE] / [SOFT_FLOOR_MALE]): es emitible, pero hay que decirlo (C-2).
         *
         * **No bloquea nada** y no implica que el objetivo esté mal: por debajo de 1200 kcal puede
         * haber un déficit perfectamente razonable en un cuerpo pequeño. Lo que señala es que cubrir
         * los micronutrientes con esa cantidad de comida es difícil, lo que es una conversación que
         * merece tener en vez de un número emitido en silencio.
         *
         * Solo se activa cuando hay **déficit real**: avisar a quien está en mantenimiento de que su
         * mantenimiento es bajo no es útil, es ruido sobre un plan que no recorta nada.
         */
        val belowSoftFloor: Boolean,
        /**
         * El umbral de aviso contra el que se comparó, en kcal ([SOFT_FLOOR_FEMALE] / [SOFT_FLOOR_MALE]).
         *
         * Viaja en el resultado, igual que [energyFloor], para que la interfaz pueda nombrar la cifra
         * sin conocer el sexo ni volver a derivarla. Una interfaz que recalcula un umbral por su cuenta
         * es una segunda fuente de verdad que puede divergir del cálculo que de verdad se aplicó.
         */
        val softFloorKcal:  Int
    )

    /**
     * Por debajo de este recorte no se avisa de ritmo limitado: a 75 kg, 10 kcal/día son 0,012 %/semana,
     * una diferencia que no cambia ninguna decisión y que solo serviría para asustar.
     */
    const val RATE_LIMIT_DEADBAND_KCAL = 10.0

    /**
     * Calcula el objetivo diario y el reparto de macros.
     *
     * @param ratePct ritmo en % de peso corporal por semana, con signo.
     * @param avgActiveKcal media de calorías activas de días completos; solo se usa en modo pulsera.
     * @param carryOverKcal arrastre del día anterior en modo pulsera: diferencia entre la actividad
     *   real de ayer y la media prevista. Así un día atípico no se pierde ni obliga a perseguir un
     *   número que todavía se está moviendo (§1.3). **Solo se aplica en modo pulsera, y dentro de los
     *   topes** — antes se sumaba al final, esquivando el tope del 25% y llegando a déficits reales del
     *   50% mientras el sistema informaba 25% (AUD-1).
     * @param measuredTdee gasto real medido por [measuredTdee]. Si se aporta, sustituye a la
     *   estimación por fórmula, que es lo único capaz de corregir un multiplicador mal elegido (§5).
     */
    fun computeTargets(
        sex:            String,
        weightKg:       Double,
        heightCm:       Double,
        age:            Int,
        activityLevel:  String,
        goal:           String,
        ratePct:        Double,
        avgActiveKcal:  Double = 0.0,
        carryOverKcal:  Double = 0.0,
        measuredTdee:   Double? = null
    ): TargetResult {
        // Defensa en profundidad: `NaN`/`Infinity` entraban hasta aquí desde la query (AUD-15) y
        // `NaN.roundToInt()` **lanza** por contrato, convirtiendo un parámetro malo en un 500. El
        // servidor ya los rechaza, pero esta función también la llama el cliente para la vista previa
        // del onboarding, así que no puede confiar en que alguien haya validado antes.
        val avgActive = avgActiveKcal.orZero()
        val carryRaw = carryOverKcal.orZero()
        val measured = measuredTdee?.takeIf { it.isFinite() && it > 0.0 }
        val rate = ratePct.orZero()

        val bmrValue = bmr(sex, weightKg, heightCm, age)
        val formulaTdee = tdee(bmrValue, activityLevel, avgActive)

        // El gasto **medido** también tiene que respetar el invariante de §7.4 (`gasto > BMR`): un gasto
        // total por debajo del basal es fisiológicamente imposible. El único filtro de `reconcile` es
        // `raw ∈ [800, 6000]`, que no sabe nada de este cuerpo, así que un registro incompleto crónico
        // podía dejar un gasto medido por debajo del BMR y el invariante dejaba de cumplirse en esa rama
        // sin que ningún test lo viera (AUD-3). Lo encontró el barrido al incluir `measuredTdee`.
        //
        // Se acota al mismo respaldo sedentario que ya usa la rama de fórmula cuando no tiene datos: la
        // app no ofrece ningún nivel por debajo de sedentario, así que ya afirma en todas partes que
        // nadie gasta menos de 1,2 × BMR. Aplicar aquí otro criterio sería incoherente.
        val measuredFloor = bmrValue * activityMultiplier("sedentary")
        val baseTdee = measured?.let { max(it, measuredFloor) } ?: formulaTdee

        // Se calcula aquí, antes del suelo y del delta, porque la guardia por IMC de abajo lo necesita
        // para decidir si hay déficit en absoluto — no solo para informarlo al final.
        val heightM = heightCm / 100.0
        val bmiValue = if (heightM > 0) weightKg / (heightM * heightM) else 0.0

        // Guardia por IMC (AUD-8): "alguien que baje a IMC 16 sigue recibiendo −25%" era el hueco
        // funcional más grande de la auditoría. No hay condición de parada automática a día de hoy, y
        // esta es la que faltaba: ya en bajo peso y pidiendo más déficit, no hay cobertura clínica para
        // seguir recortando sin supervisión. No se toca el ritmo que el usuario eligió en el formulario
        // (eso sigue siendo su decisión); lo que deja de ocurrir es que ese ritmo se traduzca en un
        // déficit real mientras siga en este rango.
        val lowBmiGuard = bmiValue > 0.0 && bmiValue < 18.5 && signFor(goal) < 0

        val effectiveRatePct = if (goal == GOAL_MAINTAIN) 0.0 else rate
        val requestedDelta = dailyDeltaFor(effectiveRatePct, weightKg)

        // Los topes se aplican al delta, no al objetivo final. Recortar el objetivo (lo que hacía la
        // versión anterior con el suelo en el BMR) puede dejar el déficit en cero sin que se note;
        // recortar el delta garantiza que siempre quede algo de lo que el usuario pidió.
        //
        // El superávit también tiene tope: faltaba, y permitía +32% frente al +10-20% de §10.3 (AUD-6).
        val maxDeficit = baseTdee * MAX_DEFICIT_FRACTION
        val maxSurplus = baseTdee * MAX_SURPLUS_FRACTION
        val cappedDelta = requestedDelta.coerceIn(-maxDeficit, maxSurplus)

        // ── Suelo absoluto, lo último sobre el número que se muestra ──────────
        //
        // Va al final a propósito: un suelo aplicado antes del delta deja caminos por los que el
        // objetivo final acaba por debajo de él.
        // El más restrictivo de los dos suelos de seguridad: disponibilidad energética y el absoluto de
        // 800 kcal (C-2). Siguen siendo *un* suelo desde aquí abajo, así que toda la lógica de
        // acotación al mantenimiento, `noSafeDeficit` y el `max` final no cambia por tener dos.
        val floorRaw = safetyFloor(sex, weightKg, heightCm, age)

        // **El suelo nunca empuja por encima del mantenimiento.** Sin esta guarda, un suelo alto
        // convierte "perder grasa" en un superávit: el usuario pediría adelgazar y la app le mandaría
        // comer de más. Si el suelo llega al mantenimiento, lo honesto es decir que no hay déficit
        // seguro posible y dejar el objetivo en mantenimiento, no inventarse un superávit.
        // Si la guardia por IMC se dispara, manda ella: el suelo pasa a ser el mantenimiento entero,
        // sin importar lo que permitiera la disponibilidad energética.
        val floor = if (lowBmiGuard) baseTdee else min(floorRaw, baseTdee)
        val noSafeDeficit = signFor(goal) < 0 && (floorRaw >= baseTdee - 1e-9 || lowBmiGuard)

        // Ritmo **sostenido**: lo que el plan entrega día tras día, sin contar el arrastre puntual.
        val sustainedTarget = max(baseTdee + cappedDelta, floor)
        val sustainedDelta = sustainedTarget - baseTdee
        val achievedRate =
            if (goal == GOAL_MAINTAIN) 0.0 else rateForDailyDelta(sustainedDelta, weightKg)

        // El arrastre solo tiene sentido en modo pulsera **y con datos reales de pulsera**: fuera de ahí
        // el gasto ya incluye la actividad en el multiplicador, así que sumarlo es contarla dos veces
        // (AUD-1c). Y si no hay media de activas, el gasto ha caído al respaldo sedentario, de modo que
        // no hay ninguna "media prevista" respecto a la que exista un arrastre.
        val hasWearableData = activityLevel == ACTIVITY_WEARABLE && avgActive > 0.0
        val carry = if (hasWearableData) carryRaw else 0.0

        // El arrastre entra **dentro** de los topes, no después: antes los esquivaba por completo.
        val deltaWithCarry = (cappedDelta + carry).coerceIn(-maxDeficit, maxSurplus)

        // El arrastre no puede cruzar el mantenimiento en contra del objetivo elegido. Sin esto, un día
        // de mucha actividad convertía "perder grasa" en superávit, y un día de reposo mandaba al que
        // quería ganar músculo a comer por debajo de su mantenimiento — las dos cosas son la queja
        // original del usuario, una en cada dirección. El arrastre puede reducir el déficit hasta el
        // mantenimiento, que es casi todo su propósito, pero no pasarse de ahí.
        val dayTargetRaw = baseTdee + deltaWithCarry
        val sign = signFor(goal)
        val directional = when {
            sign < 0 -> min(dayTargetRaw, baseTdee)
            sign > 0 -> max(dayTargetRaw, baseTdee)
            else     -> dayTargetRaw
        }
        val targetKcal = max(directional, floor)

        val limited = abs(sustainedDelta - requestedDelta) > RATE_LIMIT_DEADBAND_KCAL
        val flooredHit = directional < floor - 1e-9

        // Aviso de objetivo bajo (C-2). Se mide sobre el objetivo **sostenido**, no sobre el del día:
        // el arrastre de la pulsera puede hundir un día concreto por debajo del umbral sin que el plan
        // lo esté, y avisar por eso sería ruido que aparece y desaparece según la actividad de ayer.
        val belowSoft = signFor(goal) < 0 && sustainedTarget < softFloorFor(sex)

        // La masa magra se aporta para acotar la proteína en obesidad severa (C-4). Es la misma
        // estimación que ya alimenta el suelo de disponibilidad energética, con su mismo nivel de
        // confianza — y aquí, además, con un suelo que impide que su saturación hunda el objetivo.
        val macros = macrosFor(
            targetKcal, weightKg, goal,
            leanMassKg = leanMassEstimate(sex, weightKg, heightCm, age)
        )

        return TargetResult(
            bmr              = bmrValue.roundToInt(),
            tdee             = baseTdee.roundToInt(),
            targetKcal       = targetKcal.roundToInt(),
            proteinG         = macros.proteinG,
            carbsG           = macros.carbsG,
            fatG             = macros.fatG,
            bmi              = (bmiValue * 10.0).roundToInt() / 10.0,
            bmiCategory      = bmiCategoryFor(bmiValue),
            rateLimited      = limited,
            requestedRate    = effectiveRatePct,
            effectiveRate    = achievedRate,
            tdeeIsMeasured   = measuredTdee != null,
            energyFloorHit   = flooredHit,
            energyFloor      = floor.roundToInt(),
            appliedCarryOver = (targetKcal - sustainedTarget).roundToInt(),
            noSafeDeficit    = noSafeDeficit,
            lowBmiGuard      = lowBmiGuard,
            macrosDegraded   = macros.degraded,
            belowSoftFloor   = belowSoft,
            softFloorKcal    = softFloorFor(sex)
        )
    }

    data class Macros(
        val proteinG: Int,
        val carbsG:   Int,
        val fatG:     Int,
        /** El reparto ideal no cabía en el objetivo y hubo que recortar grasa y/o proteína (AUD-17). */
        val degraded: Boolean = false
    )

    // ── Objetivo de proteína (§6, C-4) ────────────────────────────────────────

    /** g/kg de peso total en déficit. Morton 2018 sitúa la meseta en 1,6; este valor la supera. */
    const val PROTEIN_PER_KG_DEFICIT = 2.0

    /** g/kg de peso total en mantenimiento y ganancia. */
    const val PROTEIN_PER_KG_BASE = 1.8

    /**
     * Fracción de masa magra que los valores por peso total asumen implícitamente.
     *
     * [PROTEIN_PER_KG_DEFICIT] no salió de la nada: §6 lo justifica como la traducción de los
     * 2,3-3,1 g/kg **de masa magra** de Helms 2014 "para un 20% de grasa". O sea que la cifra por peso
     * total ya lleva dentro una suposición sobre la composición corporal de quien la recibe. Esta
     * constante la hace explícita (0,77 ≈ 23% de grasa, el extremo prudente de esa suposición) para
     * poder detectar a quién no se le aplica.
     *
     * Con ella, el objetivo en déficit pasa a ser `min(2,0 × peso, 2,0/0,77 × magra)` = `min(2,0 × peso,
     * 2,6 × magra)`, y ese 2,6 cae dentro del rango de Helms en vez de ser un número elegido a mano.
     *
     * **Por qué 0,77 y no 0,80** (que sería el 20% literal de §6): a 0,80 el tope empieza a morder con
     * cualquier grasa estimada por encima del 20%, que es la mitad de la población adulta, y recortaría
     * 22 g a un hombre de 95 kg que no tiene ningún problema. El objetivo de C-4 es la cola de obesidad
     * severa, no reescribir el objetivo de proteína de todo el mundo.
     */
    const val PROTEIN_ASSUMED_LEAN_FRACTION = 0.77

    /**
     * Suelo de masa magra, como fracción del peso, **solo para el cálculo de proteína**.
     *
     * Existe porque [bodyFatPctEstimate] (Deurenberg) está extrapolado fuera de su rango de validación
     * (IMC 14-41) y satura en el 70% de grasa a partir de IMC ~58, lo que afirma que una persona es 30%
     * masa magra — fisiológicamente imposible: ni en obesidad severa se baja del ~40%. Sin este suelo,
     * una mujer de 150 kg recibiría 117 g de proteína (0,78 g/kg) por culpa de una masa magra estimada
     * de 45 kg cuando la real ronda 60-70.
     *
     * **Se aplica solo aquí, a propósito.** Corregir [bodyFatPctEstimate] en su origen subiría también
     * el suelo por disponibilidad energética: en esa misma mujer pasaría de 1350 a 2025 kcal, por encima
     * de su tope del 25%, y le recortaría el déficit de 642 a 542 kcal/día. Eso rompería los 500-750
     * kcal de déficit que AHA/ACC/TOS 2013 recomienda precisamente en obesidad, que la auditoría dio por
     * bueno de forma expresa. Un arreglo que mejora una cifra y empeora otra no es un arreglo.
     */
    const val PROTEIN_MIN_LEAN_FRACTION = 0.40

    /**
     * Objetivo de proteína en gramos.
     *
     * El valor por kg de peso total se acota por masa magra ([PROTEIN_ASSUMED_LEAN_FRACTION]), porque en
     * obesidad severa el peso total produce cifras que cuadran las kcal pero no son un reparto: un
     * hombre de 160 kg recibía **320 g, el 57% de sus calorías**, dejando 102 g de carbohidratos (C-4).
     * Las guías coinciden en que ahí la proteína se calcula sobre peso ajustado o masa magra, no sobre el
     * peso real (ISSN 2017, ASPEN).
     *
     * @param leanMassKg masa magra estimada. Si es `null` no hay tope y se usa solo el peso total, que
     *   es el comportamiento histórico. [computeTargets] siempre la aporta, así que el camino de
     *   producción siempre lleva el tope; el parámetro es opcional para poder usar esta función con
     *   un peso y nada más.
     */
    fun proteinTargetG(weightKg: Double, goal: String, leanMassKg: Double? = null): Double {
        val perKg = if (goal == GOAL_LOSE) PROTEIN_PER_KG_DEFICIT else PROTEIN_PER_KG_BASE
        val byWeight = perKg * weightKg
        val lean = leanMassKg ?: return byWeight
        // El suelo impide que una masa magra imposible (Deurenberg saturado) hunda la proteína.
        val leanFloored = max(lean, PROTEIN_MIN_LEAN_FRACTION * weightKg)
        return min(byWeight, perKg / PROTEIN_ASSUMED_LEAN_FRACTION * leanFloored)
    }

    /**
     * Reparto de macronutrientes (§6).
     *
     * Proteína: ver [proteinTargetG] — por kg de peso total, acotada por masa magra cuando se aporta.
     *
     * Antes los carbohidratos se recortaban a 0 con `coerceAtLeast(0)` cuando proteína y grasa se
     * pasaban del objetivo, y **los macros dejaban de sumar las kcal en silencio**. Ahora el exceso se
     * absorbe bajando primero la grasa (hasta un 15% de las kcal, por función hormonal) y solo después
     * la proteína, de modo que el reparto siempre cuadra.
     *
     * @param leanMassKg masa magra estimada, para acotar la proteína. Ver [proteinTargetG].
     */
    fun macrosFor(
        targetKcalRaw: Double,
        weightKg:      Double,
        goal:          String,
        leanMassKg:    Double? = null
    ): Macros {
        // Un objetivo negativo o no finito no tiene reparto posible, y sin esta guarda producía macros
        // negativos (grasa de −17 g) que además dejaban de sumar (AUD-15). Las ramas correctoras de
        // abajo no se disparan con un objetivo negativo, porque `remaining` ya sale negativo de otro modo.
        val targetKcal = if (targetKcalRaw.isFinite()) max(0.0, targetKcalRaw) else 0.0

        var proteinKcal = proteinTargetG(weightKg, goal, leanMassKg) * 4.0
        var fatKcal = targetKcal * 0.25

        var remaining = targetKcal - proteinKcal - fatKcal
        // El reparto ideal (proteína por kg + 25% de grasa) no cabía en el objetivo: hubo que recortar
        // grasa y, si no bastaba, proteína por debajo del gramaje objetivo. El reparto final cuadra
        // siempre (eso ya estaba resuelto y probado), pero cuadrar no es ser razonable — 0 g de
        // carbohidratos y proteína al 85% de las kcal no es un plan que deba pasar sin aviso (AUD-17).
        val didNotFit = remaining < 0.0
        if (remaining < 0) {
            val minFatKcal = targetKcal * 0.15
            fatKcal = max(minFatKcal, targetKcal - proteinKcal)
            remaining = targetKcal - proteinKcal - fatKcal
        }
        if (remaining < 0) {
            proteinKcal = max(0.0, targetKcal - fatKcal)
            remaining = 0.0
        }

        // ── Dos banderas adicionales que se probaron y se DESCARTARON (C-4) ──
        //
        // La auditoría pedía ampliar `degraded` para que también saltara con "proteína por encima del 35%
        // de las kcal" (techo del AMDR) o "carbohidratos por debajo de 130 g" (RDA del IOM), porque los
        // 320 g de un hombre de 160 kg cuadraban las kcal y pasaban sin ninguna bandera. Se implementaron
        // las dos, se midieron contra perfiles reales, y las dos dan **falsos positivos sin aportar
        // ningún verdadero positivo**:
        //
        // - **Techo del 35%**: el perfil real del usuario (75,5 kg, objetivo 1625) sale a 151 g, que son
        //   el **37,2%** de sus kcal. Es un corte de libro, y marcarlo como "reparto no realista" sería
        //   exactamente el aviso alarmante por nada de AUD-20, en la pantalla que más se mira. La causa
        //   es que la fracción de kcal sube cuando las kcal bajan: mide el déficit, no la proteína.
        // - **Mínimo de 130 g de carbohidratos**: una mujer de 50 kg con objetivo de 1126 kcal sale a
        //   114 g, perfectamente razonable para su tamaño. El umbral no escala con el cuerpo.
        //
        // Y sobre todo: **ya no hay patología que detectar**. El tope de [proteinTargetG] acota la
        // proteína a 2,6 g/kg de masa magra, que es el extremo de Helms 2014, así que los 4,5 g/kg que
        // motivaron el hallazgo no pueden volver a ocurrir. La bandera sobraba porque el arreglo está
        // aguas arriba — prevenir es mejor que avisar.

        return Macros(
            proteinG = (proteinKcal / 4.0).roundToInt(),
            carbsG   = (remaining / 4.0).roundToInt(),
            fatG     = (fatKcal / 9.0).roundToInt(),
            degraded = didNotFit
        )
    }

    fun bmiCategoryFor(bmi: Double): String = when {
        bmi < 18.5 -> "Bajo peso"
        bmi < 25.0 -> "Normopeso"
        bmi < 30.0 -> "Sobrepeso"
        bmi < 35.0 -> "Obesidad grado I"
        bmi < 40.0 -> "Obesidad grado II"
        else       -> "Obesidad grado III"
    }

    // ── Incertidumbre del objetivo ─────────────────────────────────────────────

    /**
     * Medio ancho de la banda cuando el gasto viene de **fórmula**, en kcal/día.
     *
     * La incertidumbre a priori del TDEE estimado ronda ±300-500 kcal/día: el error estándar publicado
     * del BMR por Schofield es de 110-170 kcal/día (tabla 5.2 de FAO/WHO/UNU 2004), más ±9% por el
     * ancho de la categoría de actividad, más 15-30% si el dato viene de un wearable. Se muestra la
     * mitad de ese rango (150) para no ser alarmista mostrando el extremo superior, mientras se sigue
     * comunicando que "1624 kcal" no es una cifra exacta — que es justo lo que la app no hacía.
     */
    const val TARGET_BAND_HALFWIDTH_FORMULA = 150

    /**
     * Medio ancho de la banda cuando el gasto viene de **medición** (autoajuste), en kcal/día.
     *
     * Más estrecho que el de fórmula porque ya está calibrado contra el resultado real, pero no es
     * cero: sigue habiendo ruido de registro de comidas y de agua/glucógeno en el peso. 75 es la mitad
     * del medio ancho de fórmula, una proporción razonable sin pretender una precisión que nadie ha
     * medido para este caso concreto.
     */
    const val TARGET_BAND_HALFWIDTH_MEASURED = 75

    /**
     * Banda de incertidumbre alrededor del objetivo, redondeada a múltiplos de 25 kcal.
     *
     * Mostrar "1624 kcal" con cuatro cifras significativas es afirmar una precisión que ningún
     * componente del cálculo tiene. Pensado para mostrarse como "1550-1700 kcal" en vez del número
     * exacto, o junto a él como aclaración.
     */
    fun targetBand(targetKcal: Int, tdeeIsMeasured: Boolean): IntRange {
        val halfWidth = if (tdeeIsMeasured) TARGET_BAND_HALFWIDTH_MEASURED else TARGET_BAND_HALFWIDTH_FORMULA
        fun roundTo25(v: Int) = (v / 25.0).roundToInt() * 25
        return roundTo25(targetKcal - halfWidth)..roundTo25(targetKcal + halfWidth)
    }

    // ── Autoajuste por medición (§5) ──────────────────────────────────────────

    /**
     * Mínimos de datos antes de atreverse a corregir nada (§5.3). Por debajo se amplifica ruido.
     *
     * `SPAN_DAYS` y `WEIGH_INS` eran más laxos que lo que exige el propio documento ("regresión lineal
     * sobre 14-28 días") y que lo que hacía falta para que el ruido no domine: con el mínimo anterior
     * (10 días / 4 pesajes) un Montecarlo de 4.000 trayectorias dio una desviación del gasto persistido
     * de ±476 kcal/d — **mayor que el déficit que el mecanismo pretende gestionar** (AUD-9 en
     * doc/11-auditoria-kcal.md). Con 14 días / 8 pesajes esa desviación baja a ~154 kcal/d, por debajo
     * del error que ya se acepta en el multiplicador de actividad (~350 kcal/d).
     */
    /**
     * Días que se dejan pasar tras cambiar de objetivo o de ritmo antes de volver a medir el gasto (C-5).
     *
     * Al empezar o cambiar un déficit se mueven 1-1,5 kg de **glucógeno y su agua** con muy poca energía
     * real (cada gramo de glucógeno arrastra ~3 g de agua; Hall 2008). [measuredTdee] no distingue esa
     * agua de la grasa: aplica 7700 kcal/kg a todo el cambio de peso, así que la lee como gasto.
     *
     * Medido con Montecarlo sobre 3.000 trayectorias: una caída de 1,5 kg en la primera ventana sesga el
     * gasto medido en **+278 kcal/día**, y el factor guardado queda con +261 kcal de error en la semana 4
     * — es decir, **peor que no ajustar nada**. En ganancia el signo se invierte (el glucógeno sube con
     * los carbohidratos) y el superávit sale recortado, reproduciendo el síntoma "no gano".
     *
     * Con 14 días de gracia, ese sesgo de la semana 4 baja a +186, y a +7 si la fórmula de partida ya
     * era buena. Se eligen 14 y no los 10 del informe por coherencia con el resto de constantes
     * `ADAPTIVE_*`, que ya usan ese número, y porque es el extremo prudente de lo que midió el panel.
     */
    const val ADAPTIVE_GOAL_CHANGE_GRACE_DAYS = 14

    const val ADAPTIVE_MIN_LOGGED_DAYS = 14
    const val ADAPTIVE_MIN_WEIGH_INS   = 8
    const val ADAPTIVE_MIN_SPAN_DAYS   = 14

    /** Peso del valor recién medido frente al anterior, para que una semana atípica no dé un salto. */
    const val ADAPTIVE_BLEND = 0.3

    /**
     * Umbral de kcal para que un día cuente como "registrado" en el autoajuste.
     *
     * Un día con solo el desayuno anotado (300 kcal) pasaba como si fuera un día completo: la ingesta
     * se promediaba sobre "días con alguna fila en la BBDD", mientras la pendiente de peso se medía
     * sobre días naturales. Esas dos poblaciones no son la misma, y la diferencia se interpretaba como
     * metabolismo — un escenario modal (20 días bien registrados, 8 con solo el desayuno) producía un
     * déficit real del 31-44% informado como el 23% elegido (AUD-10). 1000 kcal es conservador: incluso
     * alguien en un día de muy baja actividad no suele quedarse por debajo si registra todo lo comido.
     */
    const val ADAPTIVE_MIN_DAY_KCAL = 1000.0

    /**
     * Cobertura mínima de la ventana: fracción de los días de la ventana de autoajuste que tienen que
     * ser "días registrados" (ver [ADAPTIVE_MIN_DAY_KCAL]) antes de confiar en la media.
     *
     * Sin esto, 14 días sueltos de buen registro repartidos en una ventana de 90 días bastaban para
     * pasar [ADAPTIVE_MIN_LOGGED_DAYS] mientras la mayoría del periodo era puro ruido sin medir. El
     * 80% es el valor que propone AUD-9/AUD-10 de la auditoría.
     */
    const val ADAPTIVE_MIN_COVERAGE = 0.8

    /**
     * Rango admitido para el factor de corrección del autoajuste (`medido / fórmula`).
     *
     * Mifflin-St Jeor tiene un error típico del ±10% y el multiplicador de actividad puede estar mal
     * elegido por un escalón entero, así que una corrección real difícilmente pasa del ±30%. Por encima
     * de eso lo que hay es **registro incompleto de comidas**, no un metabolismo extraordinario — y es
     * el mecanismo del que la auditoría demostró que puede endurecer el déficit progresivamente
     * (AUD-10). Acotarlo evita que un sesgo de registro se convierta en un objetivo imposible.
     */
    val ADAPTIVE_FACTOR_RANGE = 0.70..1.30

    /**
     * Gasto real medido a partir del resultado, no de la predicción.
     *
     * Absorbe de golpe los cinco errores que ninguna fórmula puede resolver: el ±10% de Mifflin, un
     * multiplicador de actividad mal elegido, la adaptación metabólica (cuyo rango individual va de
     * −337 a +352 kcal/día), lo que la pulsera no captó y el error al pesar la comida.
     */
    fun measuredTdee(avgDailyIntakeKcal: Double, weightChangeKg: Double, days: Int): Double {
        require(days > 0) { "days debe ser > 0" }
        return avgDailyIntakeKcal - (weightChangeKg * KCAL_PER_KG / days)
    }

    sealed class AdaptiveResult {
        /**
         * No hay datos suficientes: se sigue usando la fórmula y se dice que es una estimación.
         *
         * @param recalculating `true` cuando la medición está **pausada a propósito** (periodo de gracia
         *   tras cambiar de objetivo, C-5) y no porque al usuario le falten registros. La interfaz tiene
         *   que distinguirlo: su consejo por defecto es "registra tus comidas y pésate con regularidad",
         *   que durante la gracia es un consejo falso — el usuario puede estar registrando todo y la
         *   pausa seguiría. Decirle que haga algo que no cambia nada es la familia de fallo de AUD-2.
         */
        data class NotEnoughData(
            val reason: String,
            val recalculating: Boolean = false
        ) : AdaptiveResult()

        data class Adjusted(
            val newTdee:      Double,
            val rawMeasured:  Double,
            val previousTdee: Double
        ) : AdaptiveResult() {
            val deltaKcal: Int get() = (newTdee - previousTdee).roundToInt()
        }
    }

    data class AdaptiveInput(
        val loggedDays:         Int,
        val weighIns:           Int,
        val spanDays:           Int,
        val avgDailyIntakeKcal: Double,
        val weightChangeKg:     Double,
        val previousTdee:       Double,
        /**
         * Tamaño de la ventana sobre la que se buscaron los datos, para calcular la cobertura
         * ([ADAPTIVE_MIN_COVERAGE]). Por defecto igual a `loggedDays`, lo que da cobertura 100% y
         * reproduce el comportamiento anterior a AUD-10 para quien no pase este parámetro a propósito.
         */
        val windowDays:         Int = loggedDays
    )

    /**
     * Reconcilia el gasto estimado con el medido, si hay datos suficientes.
     *
     * ⚠️ El método (media móvil + mezcla) es convención de entrenadores y aplicaciones, **sin
     * validación en revisión por pares** — ver §5.4. Lo respaldado es su premisa: que la predicción
     * estática falla de forma conocida. Por eso debe presentarse al usuario como "ajustado según tu
     * progreso real", nunca como "científicamente calculado".
     */
    fun reconcile(input: AdaptiveInput): AdaptiveResult {
        if (input.loggedDays < ADAPTIVE_MIN_LOGGED_DAYS)
            return AdaptiveResult.NotEnoughData(
                "Necesito ${ADAPTIVE_MIN_LOGGED_DAYS} días con comidas registradas (llevas ${input.loggedDays})"
            )
        if (input.weighIns < ADAPTIVE_MIN_WEIGH_INS)
            return AdaptiveResult.NotEnoughData(
                "Necesito al menos $ADAPTIVE_MIN_WEIGH_INS pesajes (llevas ${input.weighIns})"
            )
        if (input.spanDays < ADAPTIVE_MIN_SPAN_DAYS)
            return AdaptiveResult.NotEnoughData(
                "Los pesajes deben repartirse en al menos $ADAPTIVE_MIN_SPAN_DAYS días"
            )

        // Cobertura: no basta con el mínimo absoluto de días registrados si están concentrados en una
        // fracción pequeña de la ventana (AUD-9/AUD-10). `loggedDays` ya cuenta solo días por encima de
        // ADAPTIVE_MIN_DAY_KCAL (filtrado en el llamador), así que esta cobertura es sobre días
        // *completos*, no sobre cualquier fila en la BBDD.
        if (input.windowDays > 0) {
            val coverage = input.loggedDays.toDouble() / input.windowDays
            if (coverage < ADAPTIVE_MIN_COVERAGE) {
                val pct = (coverage * 100).roundToInt()
                val minPct = (ADAPTIVE_MIN_COVERAGE * 100).roundToInt()
                return AdaptiveResult.NotEnoughData(
                    "Necesito comidas completas en al menos $minPct% de los últimos ${input.windowDays} " +
                        "días (llevas $pct%)"
                )
            }
        }

        val raw = measuredTdee(input.avgDailyIntakeKcal, input.weightChangeKg, input.spanDays)

        // Un valor medido absurdo suele significar registro incompleto de comidas, no un metabolismo
        // extraordinario. Se descarta en vez de destrozar el objetivo del usuario.
        if (raw < 800 || raw > 6000)
            return AdaptiveResult.NotEnoughData("Los datos no cuadran — revisa que registres todas las comidas")

        val blended = ADAPTIVE_BLEND * raw + (1 - ADAPTIVE_BLEND) * input.previousTdee
        return AdaptiveResult.Adjusted(
            newTdee      = blended,
            rawMeasured  = raw,
            previousTdee = input.previousTdee
        )
    }

    // ── Utilidades ────────────────────────────────────────────────────────────

    /**
     * `NaN`/`Infinity` → 0. Existe porque `String.toDoubleOrNull()` **acepta** las cadenas `"NaN"` e
     * `"Infinity"`, así que llegaban desde la query hasta el cálculo, y `NaN.roundToInt()` lanza por
     * contrato en la JVM: un parámetro mal formado se convertía en un 500 (AUD-15).
     */
    fun Double.orZero(): Double = if (isFinite()) this else 0.0

    /**
     * `toString()` de Double varía entre plataformas en KMP; esto da siempre "1.2".
     *
     * AUD-23: la versión anterior dividía `rounded` (con signo) por 10 directamente. La división
     * entera de Kotlin trunca hacia cero, así que `-4 / 10 == 0`: `formatOneDecimal(-0.4)` daba
     * `"0.4"`, sin el signo — justo en el tramo donde viven todos los ritmos de la app. Funcionaba
     * para `|valor| ≥ 1` porque ahí la parte entera ya no es cero. Ahora se trabaja con la magnitud y
     * el signo se antepone aparte, así que el caso `|valor| < 1` también es correcto.
     */
    fun formatOneDecimal(value: Double): String {
        val rounded = (value * 10.0).roundToInt()
        val sign = if (rounded < 0) "-" else ""
        val magnitude = abs(rounded)
        return "$sign${magnitude / 10}.${magnitude % 10}"
    }

    /** Igual que [formatOneDecimal] pero con dos decimales: "0.53". Mismo arreglo de signo (AUD-23). */
    fun formatTwoDecimals(value: Double): String {
        val rounded = (value * 100.0).roundToInt()
        val sign = if (rounded < 0) "-" else ""
        val magnitude = abs(rounded)
        val decimals = magnitude % 100
        return "$sign${magnitude / 100}.${if (decimals < 10) "0$decimals" else "$decimals"}"
    }
}
