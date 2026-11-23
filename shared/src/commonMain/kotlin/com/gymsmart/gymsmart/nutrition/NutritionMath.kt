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
            if (avgActiveKcal > 0) bmr + avgActiveKcal
            else                   bmr * activityMultiplier("sedentary")
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
     * @param energyFloor suelo de [energyAvailabilityFloor] para este cuerpo.
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
            min(tdee * MAX_DEFICIT_FRACTION, max(0.0, tdee - energyFloor))
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
        /** El suelo por disponibilidad energética (§7.2) mordió: el objetivo es ese suelo. */
        val energyFloorHit: Boolean,
        /** El suelo calculado para este cuerpo, para poder explicarlo en la interfaz. */
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
        val macrosDegraded: Boolean
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
        val floorRaw = energyAvailabilityFloor(sex, weightKg, heightCm, age)

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

        val macros = macrosFor(targetKcal, weightKg, goal)

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
            energyFloor      = floorRaw.roundToInt(),
            appliedCarryOver = (targetKcal - sustainedTarget).roundToInt(),
            noSafeDeficit    = noSafeDeficit,
            lowBmiGuard      = lowBmiGuard,
            macrosDegraded   = macros.degraded
        )
    }

    data class Macros(
        val proteinG: Int,
        val carbsG:   Int,
        val fatG:     Int,
        /** El reparto ideal no cabía en el objetivo y hubo que recortar grasa y/o proteína (AUD-17). */
        val degraded: Boolean = false
    )

    /**
     * Reparto de macronutrientes (§6).
     *
     * Proteína: 2,0 g/kg en déficit, 1,8 g/kg en el resto. Ambos valores caen dentro
     * de los rangos respaldados (Morton 2018 sitúa la meseta en 1,6 g/kg; Helms 2014 pide 2,3-3,1 g/kg
     * de masa magra en déficit, que para un 20% de grasa equivale a los 2,0 g/kg de peso total).
     *
     * Antes los carbohidratos se recortaban a 0 con `coerceAtLeast(0)` cuando proteína y grasa se
     * pasaban del objetivo, y **los macros dejaban de sumar las kcal en silencio**. Ahora el exceso se
     * absorbe bajando primero la grasa (hasta un 15% de las kcal, por función hormonal) y solo después
     * la proteína, de modo que el reparto siempre cuadra.
     */
    fun macrosFor(targetKcalRaw: Double, weightKg: Double, goal: String): Macros {
        // Un objetivo negativo o no finito no tiene reparto posible, y sin esta guarda producía macros
        // negativos (grasa de −17 g) que además dejaban de sumar (AUD-15). Las ramas correctoras de
        // abajo no se disparan con un objetivo negativo, porque `remaining` ya sale negativo de otro modo.
        val targetKcal = if (targetKcalRaw.isFinite()) max(0.0, targetKcalRaw) else 0.0

        val proteinPerKg = if (goal == GOAL_LOSE) 2.0 else 1.8
        var proteinKcal = weightKg * proteinPerKg * 4.0
        var fatKcal = targetKcal * 0.25

        var remaining = targetKcal - proteinKcal - fatKcal
        // El reparto ideal (proteína por kg + 25% de grasa) no cabía en el objetivo: hubo que recortar
        // grasa y, si no bastaba, proteína por debajo del gramaje objetivo. El reparto final cuadra
        // siempre (eso ya estaba resuelto y probado), pero cuadrar no es ser razonable — 0 g de
        // carbohidratos y proteína al 85% de las kcal no es un plan que deba pasar sin aviso (AUD-17).
        val degraded = remaining < 0.0
        if (remaining < 0) {
            val minFatKcal = targetKcal * 0.15
            fatKcal = max(minFatKcal, targetKcal - proteinKcal)
            remaining = targetKcal - proteinKcal - fatKcal
        }
        if (remaining < 0) {
            proteinKcal = max(0.0, targetKcal - fatKcal)
            remaining = 0.0
        }

        return Macros(
            proteinG = (proteinKcal / 4.0).roundToInt(),
            carbsG   = (remaining / 4.0).roundToInt(),
            fatG     = (fatKcal / 9.0).roundToInt(),
            degraded = degraded
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
        /** No hay datos suficientes: se sigue usando la fórmula y se dice que es una estimación. */
        data class NotEnoughData(val reason: String) : AdaptiveResult()

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
