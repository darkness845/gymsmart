package com.gymsmart.gymsmart.nutrition

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifica los números que la app le promete al usuario.
 *
 * Existe porque el motivo de reescribir el cálculo fue justamente que las etiquetas mentían: la
 * interfaz prometía "1 kg/sem" donde la aritmética daba 0,45. Y porque la primera versión de esta
 * reescritura tenía un suelo de seguridad incompatible con sus propios rangos de ritmo, que anulaba
 * el déficit entero sin avisar — eso lo encontró el usuario probando, no un test. De ahí el barrido
 * exhaustivo de [todas las combinaciones][sweep].
 */
class NutritionMathTest {

    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.01) {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "Esperaba ~$expected pero fue $actual (tolerancia $tolerance)"
        )
    }

    // ── Barrido exhaustivo ────────────────────────────────────────────────────

    private data class Body(val weightKg: Double, val heightCm: Double, val age: Int)

    private val bodies = listOf(
        Body(45.0, 150.0, 18),    // muy pequeña
        Body(55.0, 160.0, 25),
        Body(75.5, 184.0, 21),    // el caso real que destapó el fallo del suelo
        Body(95.0, 180.0, 45),
        Body(140.0, 175.0, 60),
        Body(200.0, 190.0, 35)    // extremo por arriba
    )

    /** Ritmos como fracción del tope de cada objetivo, para cubrir todo el recorrido. */
    private val rateFractions = listOf(0.05, 0.1, 0.25, 0.5, 0.75, 1.0)

    /**
     * Recorre sexo × actividad × objetivo × complexión × ritmo y ejecuta [check] en cada combinación.
     * Son casi 2.000 casos: es la forma de que un fallo como el del suelo no vuelva a colarse.
     */
    private fun sweep(check: (Params, NutritionMath.TargetResult) -> Unit) {
        var casos = 0
        for (sex in listOf("male", "female")) {
            for (activity in NutritionMath.ALL_ACTIVITY_LEVELS) {
                for (goal in NutritionMath.ALL_GOALS) {
                    for (body in bodies) {
                        for (rate in ratesFor(goal)) {
                            // Con y sin datos de pulsera: el caso "sin datos" fue el que rompió.
                            for (avgActive in listOf(0.0, 450.0)) {
                            // `carryOverKcal` y `measuredTdee` **no se barrían**, así que los nueve
                            // invariantes estaban verificados solo sobre los parámetros incapaces de
                            // romperlos: la tabla verde era cierta y a la vez engañosa (AUD-1, AUD-3).
                            // Con el arrastre negativo el déficit real llegaba al 50% del gasto, y con
                            // `measuredTdee` el invariante `gasto > BMR` dejaba de cumplirse.
                            for (carry in listOf(0.0, -400.0, 250.0)) {
                            for (measured in listOf(null, 2400.0)) {
                                val p = Params(sex, activity, goal, body, rate, avgActive, carry, measured)
                                val r = NutritionMath.computeTargets(
                                    sex = sex,
                                    weightKg = body.weightKg,
                                    heightCm = body.heightCm,
                                    age = body.age,
                                    activityLevel = activity,
                                    goal = goal,
                                    ratePct = rate,
                                    avgActiveKcal = avgActive,
                                    carryOverKcal = carry,
                                    measuredTdee = measured
                                )
                                check(p, r)
                                casos++
                            }
                            }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(casos > 1000, "El barrido debería cubrir más casos, solo cubrió $casos")
    }

    private data class Params(
        val sex: String, val activity: String, val goal: String,
        val body: Body, val ratePct: Double, val avgActive: Double,
        val carryOver: Double = 0.0, val measuredTdee: Double? = null
    )

    private fun ratesFor(goal: String): List<Double> {
        val max = NutritionMath.maxRateFor(goal) ?: return listOf(0.0)
        val sign = NutritionMath.signFor(goal)
        return rateFractions.map { it * max * sign }
    }

    @Test
    fun `ninguna combinacion produce un objetivo absurdo`() = sweep { p, r ->
        assertTrue(r.targetKcal > 0, "Objetivo no positivo en $p")
        assertTrue(r.bmr > 0, "BMR no positivo en $p")
        assertTrue(r.tdee > 0, "Gasto no positivo en $p")
    }

    @Test
    fun `el gasto nunca es igual al metabolismo basal`() = sweep { p, r ->
        // Este es el fallo que destapó el usuario: en modo pulsera sin datos, el gasto colapsaba al
        // BMR, con lo que cualquier déficit quedaba por debajo del suelo y se anulaba entero.
        assertTrue(
            r.tdee > r.bmr,
            "El gasto (${r.tdee}) no puede igualar al BMR (${r.bmr}) en $p — nadie despierto gasta su basal"
        )
    }

    @Test
    fun `un deficit pedido nunca se queda en cero`() = sweep { p, r ->
        if (p.goal == NutritionMath.GOAL_MAINTAIN || p.ratePct == 0.0) return@sweep
        assertTrue(
            abs(r.effectiveRate) > 0.0,
            "Se pidió ritmo ${p.ratePct} y quedó en ${r.effectiveRate} (objetivo ${r.targetKcal}, gasto ${r.tdee}) en $p"
        )
        // Y el signo tiene que seguir siendo el que corresponde al objetivo.
        assertTrue(
            r.effectiveRate * NutritionMath.signFor(p.goal) > 0,
            "El signo del ritmo no corresponde al objetivo en $p"
        )
    }

    @Test
    fun `el objetivo va en la direccion del objetivo elegido`() = sweep { p, r ->
        // El invariante es del **plan sostenido**, no del número de un día concreto: el arrastre es una
        // corrección puntual y legítimamente mueve el objetivo de hoy. Se descuenta para juzgar el plan.
        val sostenido = r.targetKcal - r.appliedCarryOver
        when {
            p.goal == NutritionMath.GOAL_MAINTAIN ->
                assertEquals(r.tdee, sostenido, "Mantener debería igualar el gasto en $p")
            p.ratePct == 0.0 ->
                assertEquals(r.tdee, sostenido, "Ritmo 0 debería igualar el gasto en $p")
            p.goal == NutritionMath.GOAL_GAIN ->
                assertTrue(sostenido > r.tdee, "Ganar debería comer por encima del gasto en $p")
            // Si no hay déficit seguro posible, el objetivo es el mantenimiento a propósito.
            r.noSafeDeficit ->
                assertEquals(r.tdee, sostenido, "Sin déficit seguro debería quedarse en mantenimiento en $p")
            else ->
                assertTrue(sostenido < r.tdee, "Perder/recomponer debería comer por debajo del gasto en $p")
        }
    }

    @Test
    fun `el arrastre nunca cruza el mantenimiento en contra del objetivo`() = sweep { p, r ->
        // Un día de mucha actividad convertía "perder grasa" en superávit, y un día de reposo mandaba a
        // quien quería ganar músculo a comer por debajo de su mantenimiento. Las dos cosas son la queja
        // original del usuario, una en cada dirección.
        when {
            NutritionMath.signFor(p.goal) < 0 ->
                assertTrue(r.targetKcal <= r.tdee, "Perder acabó por encima del gasto en $p")
            NutritionMath.signFor(p.goal) > 0 ->
                assertTrue(r.targetKcal >= r.tdee, "Ganar acabó por debajo del gasto en $p")
        }
    }

    @Test
    fun `el deficit nunca supera el tope de seguridad`() = sweep { p, r ->
        val deficit = r.tdee - r.targetKcal
        if (deficit <= 0) return@sweep
        val tope = r.tdee * NutritionMath.MAX_DEFICIT_FRACTION
        assertTrue(
            deficit <= tope + 1,
            "Déficit de $deficit supera el tope de $tope en $p"
        )
    }

    @Test
    fun `el ritmo conseguido nunca supera al pedido`() = sweep { p, r ->
        assertTrue(
            abs(r.effectiveRate) <= abs(r.requestedRate) + 1e-6,
            "El ritmo conseguido (${r.effectiveRate}) supera al pedido (${r.requestedRate}) en $p"
        )
        if (!r.rateLimited) {
            // `rateLimited` tiene banda muerta: un recorte de hasta RATE_LIMIT_DEADBAND_KCAL no se
            // anuncia, porque un aviso alarmante por 3 kcal/día es ruido, no información (AUD-20).
            // Así que aquí la tolerancia es esa banda expresada en %/semana para este cuerpo.
            val banda = abs(
                NutritionMath.rateForDailyDelta(NutritionMath.RATE_LIMIT_DEADBAND_KCAL, p.body.weightKg)
            )
            assertClose(r.requestedRate, r.effectiveRate, tolerance = banda + 0.001)
        }
    }

    @Test
    fun `los macros siempre suman las kcal objetivo`() = sweep { p, r ->
        val suma = r.proteinG * 4.0 + r.carbsG * 4.0 + r.fatG * 9.0
        assertTrue(
            abs(suma - r.targetKcal) < 15,
            "Macros descuadrados en $p: suman $suma para un objetivo de ${r.targetKcal}"
        )
        assertTrue(r.proteinG > 0 && r.carbsG >= 0 && r.fatG > 0, "Macro no positivo en $p")
    }

    @Test
    fun `el IMC y su categoria son coherentes`() = sweep { p, r ->
        assertTrue(r.bmi > 0, "IMC no positivo en $p")
        assertEquals(NutritionMath.bmiCategoryFor(r.bmi), r.bmiCategory, "Categoría de IMC incoherente en $p")
    }

    // ── Regresión del fallo concreto que encontró el usuario ──────────────────

    @Test
    fun `regresion - pulsera sin datos no anula el deficit`() {
        // 75,5 kg / 184 cm / 21 años, modo pulsera, sin histórico en Health Connect.
        // Antes: gasto = BMR = 1805, objetivo = 1805 - 415 = 1390 → suelo lo subía a 1805 = BMR.
        val r = NutritionMath.computeTargets(
            sex = "male", weightKg = 75.5, heightCm = 184.0, age = 21,
            activityLevel = NutritionMath.ACTIVITY_WEARABLE,
            goal = NutritionMath.GOAL_LOSE, ratePct = -0.5,
            avgActiveKcal = 0.0
        )
        assertEquals(1805, r.bmr)
        assertTrue(r.targetKcal < r.tdee, "Tiene que haber déficit real")
        assertTrue(r.targetKcal != r.bmr, "El objetivo no puede quedar clavado en el BMR")
        assertTrue(abs(r.effectiveRate) > 0.0, "El ritmo no puede quedar en cero")
    }

    @Test
    fun `regresion - un ritmo lento es valido y no se bloquea`() {
        // El usuario pidió poder bajar 0,2 kg/semana. A 75,5 kg son 0,26 %/sem, por debajo de la
        // banda recomendada. Es seguro: retiene músculo igual o mejor, solo tarda más.
        val ritmo = -0.2 / 75.5 * 100.0
        assertTrue(NutritionMath.isRateValid(NutritionMath.GOAL_LOSE, ritmo))
        assertFalse(NutritionMath.isRateRecommended(NutritionMath.GOAL_LOSE, ritmo))
    }

    @Test
    fun `pasarse del tope si se bloquea`() {
        // 1,2 kg/semana a 75,5 kg son 1,59 %/sem: por encima del 1% donde se pierde masa magra.
        val ritmo = -1.2 / 75.5 * 100.0
        assertFalse(NutritionMath.isRateValid(NutritionMath.GOAL_LOSE, ritmo))
        // Y encajarlo lo deja justo en el tope.
        assertClose(-1.0, NutritionMath.clampRate(NutritionMath.GOAL_LOSE, ritmo))
    }

    // ── La promesa de la etiqueta debe cumplirse ──────────────────────────────

    @Test
    fun `el ritmo elegido produce el deficit que corresponde`() {
        // 70 kg al 0,75 %/sem = 0,525 kg/sem = 4.042,5 kcal/sem = 577,5 kcal/día
        assertClose(-577.5, NutritionMath.dailyDeltaFor(-0.75, 70.0), tolerance = 0.5)
    }

    @Test
    fun `convertir ritmo a kcal y volver da el mismo ritmo`() {
        val peso = 82.4
        val ritmo = -0.6
        val kcal = NutritionMath.dailyDeltaFor(ritmo, peso)
        assertClose(ritmo, NutritionMath.rateForDailyDelta(kcal, peso))
    }

    @Test
    fun `lo que se anuncia en kg coincide con lo que se anuncia en kcal`() {
        for (goal in listOf(NutritionMath.GOAL_LOSE, NutritionMath.GOAL_GAIN)) {
            for (body in bodies) {
                for (rate in ratesFor(goal)) {
                    val info = NutritionMath.describeRate(goal, rate, body.weightKg)
                    val kgImplicados = info.kcalPerDay * 7.0 / NutritionMath.KCAL_PER_KG
                    assertClose(info.kgPerWeek, kgImplicados, tolerance = 0.02)
                }
            }
        }
    }

    // ── El ritmo debe escalar con el peso, que fue el fallo de diseño ─────────

    @Test
    fun `el mismo ritmo porcentual da mas kg a mas peso`() {
        val ligero = NutritionMath.kgPerWeekFor(-0.75, 60.0)
        val pesado = NutritionMath.kgPerWeekFor(-0.75, 120.0)
        assertTrue(abs(pesado) > abs(ligero), "El ritmo en % debe escalar con el peso corporal")
        assertClose(-0.45, ligero)
        assertClose(-0.90, pesado)
    }

    @Test
    fun `un kilo por semana queda fuera del tope a 70 kg pero dentro a 120`() {
        // 1 kg/sem es 1,43 %/sem a 70 kg (el brazo de Garthe que no conservó masa magra) y 0,83 % a
        // 120 kg. La misma opción en kilos no puede valer para los dos.
        assertFalse(NutritionMath.isRateValid(NutritionMath.GOAL_LOSE, -1.0 / 70.0 * 100.0))
        assertTrue(NutritionMath.isRateValid(NutritionMath.GOAL_LOSE, -1.0 / 120.0 * 100.0))
    }

    // ── Macros ────────────────────────────────────────────────────────────────

    @Test
    fun `los macros cuadran en los casos extremos`() {
        for (peso in listOf(45.0, 80.0, 140.0, 200.0)) {
            for (kcal in listOf(1000.0, 1800.0, 2500.0, 5000.0)) {
                val m = NutritionMath.macrosFor(kcal, peso, NutritionMath.GOAL_LOSE)
                val suma = m.proteinG * 4.0 + m.carbsG * 4.0 + m.fatG * 9.0
                assertTrue(abs(suma - kcal) < 15, "Macros descuadrados a $peso kg / $kcal kcal: $suma")
                assertTrue(m.carbsG >= 0 && m.proteinG >= 0 && m.fatG >= 0)
            }
        }
    }

    // ── Modo pulsera ──────────────────────────────────────────────────────────

    @Test
    fun `con datos de pulsera el gasto es basal mas actividad media mas digestion`() {
        // **Este test afirmaba `bmr + 600` y era el fallo C-1 escrito como expectativa.** Las calorías
        // activas de la pulsera no incluyen el efecto térmico de los alimentos, pero el PAL del modo
        // fórmula sí (DRI cap. 4), así que sumarlas sin más daba un gasto un 10% más bajo que el modo
        // fórmula para la misma actividad real: déficit del 32,5% donde el sistema informaba 25%, y
        // superávit de +43 kcal donde se pedía ganar músculo.
        val bmr = NutritionMath.bmr("male", 70.0, 175.0, 22)
        assertClose(
            (bmr + 600.0) / (1.0 - NutritionMath.TEF_FRACTION),
            NutritionMath.tdee(bmr, NutritionMath.ACTIVITY_WEARABLE, 600.0)
        )
        // Y sigue siendo más que la suma cruda, que es lo que se corrigió.
        assertTrue(NutritionMath.tdee(bmr, NutritionMath.ACTIVITY_WEARABLE, 600.0) > bmr + 600.0)
    }

    @Test
    fun `los dos modos dan el mismo gasto para la misma actividad real`() {
        // La comprobación de que el factor del TEF es exactamente el correcto, y no un número puesto a
        // ojo, es algebraica: `activeKcalFor` genera las activas de un nivel descontando el TEF, así que
        // al reinyectarlas en `tdee` el resultado tiene que reproducir `BMR × PAL` **exactamente**. Si
        // alguien cambia una de las dos fórmulas sin la otra, este test se cae.
        val bmr = NutritionMath.bmr("male", 75.0, 178.0, 30)
        for (nivel in NutritionMath.ALL_ACTIVITY_LEVELS) {
            val activas = ActivityBenchmarks.activeKcalFor(nivel, bmr) ?: continue  // pulsera: no aplica
            val porFormula = NutritionMath.tdee(bmr, nivel)
            val porPulsera = NutritionMath.tdee(bmr, NutritionMath.ACTIVITY_WEARABLE, activas)
            assertClose(
                porFormula, porPulsera, tolerance = 0.5
            )
        }
    }

    @Test
    fun `sin datos de pulsera se asume sedentario y no basal`() {
        val bmr = NutritionMath.bmr("male", 70.0, 175.0, 22)
        val sinDatos = NutritionMath.tdee(bmr, NutritionMath.ACTIVITY_WEARABLE, 0.0)
        assertClose(bmr * 1.2, sinDatos)
        assertTrue(sinDatos > bmr)
    }

    @Test
    fun `el arrastre del dia anterior se suma al objetivo, pero dentro de los topes`() {
        fun result(carry: Double, activity: String = NutritionMath.ACTIVITY_WEARABLE) =
            NutritionMath.computeTargets(
                sex = "male", weightKg = 70.0, heightCm = 175.0, age = 22,
                activityLevel = activity, goal = NutritionMath.GOAL_GAIN,
                ratePct = 0.375, avgActiveKcal = 600.0, carryOverKcal = carry
            )

        val base = result(0.0)

        // Un arrastre que cabe en el margen que deja el tope se suma entero.
        assertEquals(base.targetKcal + 100, result(100.0).targetKcal)

        // Uno que no cabe se recorta: antes se sumaba al final y esquivaba el tope por completo (AUD-1a).
        val grande = result(400.0)
        val surplus = grande.targetKcal - grande.tdee
        assertTrue(
            surplus <= grande.tdee * NutritionMath.MAX_SURPLUS_FRACTION + 1,
            "El arrastre esquivó el tope de superávit: +$surplus sobre ${grande.tdee}"
        )
        assertTrue(
            grande.targetKcal < base.targetKcal + 400,
            "Se esperaba que el tope recortara el arrastre de 400"
        )
    }

    @Test
    fun `el arrastre se ignora fuera del modo pulsera`() {
        // Fuera de modo pulsera el gasto ya incluye la actividad en el multiplicador, así que sumar el
        // arrastre es contarla dos veces — y llegaba a 1.096 kcal sin disparar ningún aviso (AUD-1c).
        fun target(carry: Double) = NutritionMath.computeTargets(
            sex = "male", weightKg = 80.0, heightCm = 180.0, age = 30,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE,
            ratePct = -0.5, carryOverKcal = carry
        ).targetKcal

        assertEquals(target(0.0), target(-600.0))
        assertEquals(target(0.0), target(600.0))
    }

    @Test
    fun `nunca se emite un objetivo por debajo del suelo de disponibilidad energetica`() = sweep { p, r ->
        // El suelo se acota al mantenimiento, así que el invariante real es "no por debajo del menor de
        // los dos". Sin suelo, el objetivo colapsaba a 0,90 × BMR en cualquier sedentario (AUD-1a).
        val suelo = minOf(r.energyFloor, r.tdee)
        assertTrue(
            r.targetKcal >= suelo - 1,
            "Objetivo ${r.targetKcal} por debajo del suelo $suelo (gasto ${r.tdee}) en $p"
        )
    }

    @Test
    fun `el suelo nunca convierte un deficit en superavit`() = sweep { p, r ->
        // Restar las calorías activas como "gasto de ejercicio" ponía el suelo por encima del
        // mantenimiento y "perder grasa" salía comiendo de más: el fallo original, en espejo.
        if (NutritionMath.signFor(p.goal) >= 0) return@sweep
        assertTrue(
            r.targetKcal <= r.tdee,
            "Perder/recomponer acabó por encima del gasto (${r.targetKcal} > ${r.tdee}) en $p"
        )
        if (r.noSafeDeficit) {
            assertEquals(r.tdee, r.targetKcal, "Sin déficit seguro el objetivo debe ser el mantenimiento en $p")
            assertClose(0.0, r.effectiveRate)
        }
    }

    // ── Suelo absoluto y aviso de objetivo bajo (C-2) ─────────────────────────

    @Test
    fun `ningun objetivo baja del suelo absoluto`() = sweep { p, r ->
        // El único test de "objetivo absurdo" que había solo exigía `targetKcal > 0`, que acepta 1 kcal.
        // Un objetivo por debajo de 800 es rango de VLCD y no debe emitirse nunca sin supervisión.
        // Se acota al gasto por el mismo motivo que el resto de suelos: en un cuerpo cuyo mantenimiento
        // ya esté por debajo de 800, el suelo no puede inventarse un superávit.
        val minimo = minOf(NutritionMath.ABSOLUTE_FLOOR_KCAL, r.tdee.toDouble())
        assertTrue(
            r.targetKcal >= minimo - 1,
            "Objetivo ${r.targetKcal} por debajo del suelo absoluto $minimo (gasto ${r.tdee}) en $p"
        )
    }

    @Test
    fun `el suelo informado nunca queda por encima del objetivo`() = sweep { p, r ->
        // `energyFloor` devolvía el suelo en crudo, sin acotar al mantenimiento, así que la interfaz
        // podía escribir "no bajamos de X" junto a un objetivo menor que X.
        assertTrue(
            r.energyFloor <= r.targetKcal,
            "Suelo informado ${r.energyFloor} por encima del objetivo ${r.targetKcal} en $p"
        )
    }

    @Test
    fun `el suelo absoluto muerde solo en cuerpos diminutos`() {
        // Mujer de 85 años y 40 kg: la disponibilidad energética daría 756 kcal, por debajo de 800.
        val diminuta = NutritionMath.computeTargets(
            sex = "female", weightKg = 40.0, heightCm = 145.0, age = 85,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE, ratePct = -1.0
        )
        assertEquals(800, diminuta.targetKcal, "El suelo absoluto debería gobernar este objetivo")
        assertTrue(diminuta.energyFloorHit, "Y debería señalarse que un suelo mordió")

        // Mujer de 80 años y 42 kg (el caso del informe): aquí gobierna la disponibilidad energética,
        // 806 kcal, que ya está por encima del suelo absoluto. Es la prueba de que el suelo absoluto es
        // una red para la cola y no el arreglo de estos objetivos bajos.
        val delInforme = NutritionMath.computeTargets(
            sex = "female", weightKg = 42.0, heightCm = 148.0, age = 80,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE, ratePct = -1.0
        )
        assertEquals(806, delInforme.targetKcal, "Debería gobernar la disponibilidad energética, no el suelo absoluto")
    }

    @Test
    fun `avisa de un objetivo de perdida por debajo del umbral clinico`() {
        val pequena = NutritionMath.computeTargets(
            sex = "female", weightKg = 42.0, heightCm = 148.0, age = 80,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE, ratePct = -1.0
        )
        assertTrue(pequena.belowSoftFloor, "806 kcal está por debajo de los 1200 de aviso en mujeres")

        // El perfil real del usuario: 1625 kcal, por encima de los 1500 de aviso en hombres. Este es el
        // caso que no debe generar ruido, porque es el que de verdad usa la app.
        val usuario = NutritionMath.computeTargets(
            sex = "male", weightKg = 75.5, heightCm = 184.0, age = 21,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE, ratePct = -0.75
        )
        assertFalse(usuario.belowSoftFloor, "Un objetivo de ${usuario.targetKcal} kcal no debería avisar")
    }

    @Test
    fun `el aviso de objetivo bajo no se dispara sin deficit`() {
        // Mantenimiento en un cuerpo diminuto: el objetivo es bajo porque su gasto es bajo, no porque se
        // esté recortando nada. Avisar ahí sería ruido sobre un plan que no quita ni una kcal.
        val mantiene = NutritionMath.computeTargets(
            sex = "female", weightKg = 40.0, heightCm = 145.0, age = 85,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_MAINTAIN, ratePct = 0.0
        )
        assertTrue(mantiene.targetKcal < NutritionMath.SOFT_FLOOR_FEMALE, "Este gasto debería ser bajo")
        assertFalse(mantiene.belowSoftFloor, "Mantenimiento no debería avisar de objetivo bajo")
    }

    @Test
    fun `el deslizador no promete mas alla del suelo absoluto`() {
        // Si `effectiveMaxRateFor` no conociera el suelo absoluto, el extremo del deslizador prometería
        // un ritmo que `computeTargets` no entrega — que es exactamente AUD-5.
        val sex = "female"; val peso = 40.0; val altura = 145.0; val edad = 85
        val bmr = NutritionMath.bmr(sex, peso, altura, edad)
        val gasto = NutritionMath.tdee(bmr, "sedentary")
        val suelo = NutritionMath.energyAvailabilityFloor(sex, peso, altura, edad)

        val topeDeslizador = NutritionMath.effectiveMaxRateFor(
            NutritionMath.GOAL_LOSE, peso, gasto, suelo
        )!!
        val alcanzado = abs(
            NutritionMath.computeTargets(
                sex = sex, weightKg = peso, heightCm = altura, age = edad,
                activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE,
                ratePct = -topeDeslizador
            ).effectiveRate
        )
        assertClose(topeDeslizador, alcanzado, tolerance = 0.02)
    }

    @Test
    fun `el superavit nunca supera su tope`() = sweep { p, r ->
        val surplus = r.targetKcal - r.tdee
        if (surplus <= 0) return@sweep
        assertTrue(
            surplus <= r.tdee * NutritionMath.MAX_SURPLUS_FRACTION + 1,
            "Superávit de $surplus supera el tope de ${r.tdee * NutritionMath.MAX_SURPLUS_FRACTION} en $p"
        )
    }

    @Test
    fun `los parametros no finitos no rompen el calculo`() {
        // `String.toDoubleOrNull()` acepta "NaN" e "Infinity", y `NaN.roundToInt()` lanza (AUD-15).
        for (malo in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val r = NutritionMath.computeTargets(
                sex = "male", weightKg = 75.5, heightCm = 184.0, age = 21,
                activityLevel = NutritionMath.ACTIVITY_WEARABLE, goal = NutritionMath.GOAL_LOSE,
                ratePct = -0.75, avgActiveKcal = malo, carryOverKcal = malo, measuredTdee = malo
            )
            assertTrue(r.targetKcal > 0, "Objetivo no positivo con $malo")
            assertTrue(r.proteinG >= 0 && r.carbsG >= 0 && r.fatG >= 0, "Macros negativos con $malo")
        }
    }

    @Test
    fun `el tope efectivo del deslizador no promete mas de lo que entrega`() {
        // Caso de AUD-5: mujer 60/160/40 sedentaria. El deslizador llegaba a -1,0 %/sem mientras el
        // objetivo no se movía de 1115 kcal en todo el tercio superior.
        val sex = "female"; val peso = 60.0; val altura = 160.0; val edad = 40
        val gasto = NutritionMath.tdee(NutritionMath.bmr(sex, peso, altura, edad), "sedentary")
        val suelo = NutritionMath.energyAvailabilityFloor(sex, peso, altura, edad)

        val topeEfectivo = NutritionMath.effectiveMaxRateFor(
            NutritionMath.GOAL_LOSE, peso, gasto, suelo
        )!!

        assertTrue(
            topeEfectivo < NutritionMath.maxRateFor(NutritionMath.GOAL_LOSE)!!,
            "El tope efectivo ($topeEfectivo) debería ser menor que el tope duro en este perfil"
        )

        // Y lo que de verdad importa: pedir el tope efectivo debe entregar ese ritmo, no uno recortado.
        val r = NutritionMath.computeTargets(
            sex = sex, weightKg = peso, heightCm = altura, age = edad,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE,
            ratePct = -topeEfectivo
        )
        assertClose(-topeEfectivo, r.effectiveRate, tolerance = 0.02)
        assertTrue(!r.rateLimited, "Pedir justo el tope efectivo no debería avisar de recorte")
    }

    @Test
    fun `el tope efectivo respeta el tope duro cuando hay gasto de sobra`() {
        // Perfil con gasto alto: aquí manda el tope por composición corporal, no el porcentual.
        val gasto = 4000.0
        assertEquals(
            NutritionMath.maxRateFor(NutritionMath.GOAL_LOSE),
            NutritionMath.effectiveMaxRateFor(NutritionMath.GOAL_LOSE, 70.0, gasto, 1200.0)
        )
    }

    @Test
    fun `un objetivo negativo o absurdo no produce macros negativos`() {
        val m = NutritionMath.macrosFor(-3000.0, 80.0, NutritionMath.GOAL_LOSE)
        assertTrue(m.proteinG >= 0 && m.carbsG >= 0 && m.fatG >= 0, "Macros negativos: $m")
    }

    // ── Bloque D: honestidad de la interfaz ─────────────────────────────────────

    @Test
    fun `la banda de incertidumbre es mas estrecha con medicion que con formula`() {
        val formula = NutritionMath.targetBand(1624, tdeeIsMeasured = false)
        val medido  = NutritionMath.targetBand(1624, tdeeIsMeasured = true)
        assertTrue(
            (medido.last - medido.first) < (formula.last - formula.first),
            "La banda medida ($medido) debería ser más estrecha que la de fórmula ($formula)"
        )
        // Redondeada a múltiplos de 25 y centrada aproximadamente en el objetivo.
        assertEquals(0, formula.first % 25)
        assertEquals(0, formula.last % 25)
        assertTrue(formula.first < 1624 && formula.last > 1624)
    }

    @Test
    fun `macros degradados se señalan cuando el reparto ideal no cabe`() {
        // 200 kg / 1600 kcal: el caso extremo del informe (AUD-17), proteína al límite y 0 g de carbos.
        val degenerado = NutritionMath.macrosFor(1600.0, 200.0, NutritionMath.GOAL_LOSE)
        assertTrue(degenerado.degraded, "Debería marcarse degradado: $degenerado")

        // El perfil real del usuario (75,5 kg / 1800 kcal) no debería activarlo.
        val sano = NutritionMath.macrosFor(1800.0, 75.5, NutritionMath.GOAL_LOSE)
        assertTrue(!sano.degraded, "No debería marcarse degradado: $sano")
    }

    // ── Proteína acotada por masa magra (C-4) ─────────────────────────────────

    @Test
    fun `la proteina en obesidad severa se acota por masa magra`() {
        // Hombre de 160 kg / 175 cm: el caso del informe. Antes recibía 2,0 × 160 = 320 g, el 57% de sus
        // kcal, dejando 102 g de carbohidratos. Ahora el tope por masa magra lo deja en ~184 g, que son
        // 2,6 g/kg de su masa magra estimada — dentro de los 2,3-3,1 de Helms 2014.
        val r = NutritionMath.computeTargets(
            sex = "male", weightKg = 160.0, heightCm = 175.0, age = 40,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE, ratePct = -0.75
        )
        assertTrue(r.proteinG in 170..195, "Proteína fuera de lo esperado: ${r.proteinG} g")
        assertTrue(r.carbsG > 150, "Los carbohidratos siguen desplazados: ${r.carbsG} g")
        assertFalse(r.macrosDegraded, "Ya no debería ser un reparto degradado: $r")

        // Y el reparto resultante ya es presentable: antes 57% de las kcal en proteína, ahora ~33%.
        assertTrue(
            r.proteinG * 4.0 / r.targetKcal < 0.40,
            "La proteína sigue ocupando demasiadas kcal: ${r.proteinG} g de ${r.targetKcal} kcal"
        )
    }

    @Test
    fun `el suelo de masa magra evita que Deurenberg saturado hunda la proteina`() {
        // Mujer de 150 kg / 160 cm: Deurenberg satura en 70% de grasa y afirma 45 kg de masa magra, que
        // es imposible. Sin el suelo del 40% la proteína caería a ~117 g (0,78 g/kg); con él sube a ~156.
        val r = NutritionMath.computeTargets(
            sex = "female", weightKg = 150.0, heightCm = 160.0, age = 40,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE, ratePct = -0.75
        )
        assertTrue(r.proteinG in 145..165, "Proteína fuera de lo esperado: ${r.proteinG} g")

        // Y la prueba de que es el suelo lo que actúa. El suelo se aplica dentro de `proteinTargetG`, así
        // que no se puede esquivar llamándola: se reproduce aquí la aritmética sin suelo y se comprueba
        // que el resultado real queda muy por encima.
        val magraSaturada = NutritionMath.leanMassEstimate("female", 150.0, 160.0, 40)
        val sinSuelo = NutritionMath.PROTEIN_PER_KG_DEFICIT /
            NutritionMath.PROTEIN_ASSUMED_LEAN_FRACTION * magraSaturada
        assertClose(45.0, magraSaturada, tolerance = 0.5)   // Deurenberg saturado: 30% de 150 kg
        assertTrue(sinSuelo < 120.0, "Sin suelo debería dar ~117 g, dio $sinSuelo")
        assertTrue(
            r.proteinG > sinSuelo + 30,
            "El suelo debería subir la proteína: ${sinSuelo.roundToInt()} → ${r.proteinG}"
        )
    }

    @Test
    fun `el tope por masa magra no castiga a quien esta musculado ni al usuario real`() {
        // Hombre de 90 kg / 180 cm: IMC 27,8, así que un tope por IMC 25 le recortaría de 180 a 162 g
        // tratándolo como obeso. Por masa magra apenas se le toca: es el usuario objetivo de la app.
        val musculado = NutritionMath.computeTargets(
            sex = "male", weightKg = 90.0, heightCm = 180.0, age = 30,
            activityLevel = "active", goal = NutritionMath.GOAL_LOSE, ratePct = -0.5
        )
        assertTrue(musculado.proteinG >= 172, "Recorte excesivo a un perfil musculado: ${musculado.proteinG} g")

        // El perfil real del usuario: el tope no debe moverlo ni un gramo.
        val usuario = NutritionMath.computeTargets(
            sex = "male", weightKg = 75.5, heightCm = 184.0, age = 21,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE, ratePct = -0.75
        )
        assertEquals(151, usuario.proteinG, "El usuario real no debería notar el tope")
    }

    @Test
    fun `el tope nunca sube la proteina por encima del valor por peso total`() = sweep { p, r ->
        val porPeso = NutritionMath.proteinTargetG(p.body.weightKg, p.goal)
        assertTrue(
            r.proteinG <= porPeso.roundToInt(),
            "El tope subió la proteína (${r.proteinG} g > ${porPeso.roundToInt()} g) en $p"
        )
    }

    @Test
    fun `la proteina nunca pasa del extremo de Helms por masa magra`() = sweep { p, r ->
        // Este es el invariante que de verdad cierra C-4, y sustituye a la bandera que se descartó: la
        // patología (4,5 g/kg de masa magra) ya no puede ocurrir, así que no hace falta detectarla.
        // El suelo del 40% es parte del tope, así que el divisor tiene que ser el mismo que usa el
        // cálculo, no la estimación cruda de Deurenberg.
        val magra = maxOf(
            NutritionMath.leanMassEstimate(p.sex, p.body.weightKg, p.body.heightCm, p.body.age),
            NutritionMath.PROTEIN_MIN_LEAN_FRACTION * p.body.weightKg
        )
        val porMagra = r.proteinG / magra
        assertTrue(
            porMagra <= 3.1,
            "Proteína a ${"%.2f".format(porMagra)} g/kg de masa magra, por encima de Helms, en $p"
        )
    }

    @Test
    fun `el aviso de reparto degradado no se dispara en un corte normal`() {
        // Se probó marcar degradado cuando la proteína pasa del 35% de las kcal (techo del AMDR) y se
        // descartó: el perfil real del usuario sale al 37,2% con un corte de libro, y el aviso habría
        // aparecido en la pantalla que más se mira. Este test fija esa decisión para que no vuelva.
        val usuario = NutritionMath.computeTargets(
            sex = "male", weightKg = 75.5, heightCm = 184.0, age = 21,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE, ratePct = -0.75
        )
        assertTrue(
            usuario.proteinG * 4.0 / usuario.targetKcal > 0.35,
            "Este perfil debería pasar del 35% de kcal en proteína: $usuario"
        )
        assertFalse(usuario.macrosDegraded, "Y aun así no es un reparto degradado: $usuario")
    }

    @Test
    fun `la guardia por IMC cae a mantenimiento en bajo peso pidiendo mas deficit`() {
        // Mujer 45 kg / 162 cm, IMC ≈ 17,1 (bajo peso), pidiendo perder al 1%/sem.
        val r = NutritionMath.computeTargets(
            sex = "female", weightKg = 45.0, heightCm = 162.0, age = 30,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE, ratePct = -1.0
        )
        assertTrue(r.bmi < 18.5, "El cuerpo de prueba debería estar en bajo peso: IMC ${r.bmi}")
        assertTrue(r.lowBmiGuard, "Debería activarse la guardia por IMC")
        assertTrue(r.noSafeDeficit, "lowBmiGuard también implica noSafeDeficit")
        assertEquals(r.tdee, r.targetKcal, "Sin déficit seguro el objetivo debe ser el mantenimiento")
        assertClose(0.0, r.effectiveRate)
    }

    @Test
    fun `la guardia por IMC no se activa para quien quiere ganar estando en bajo peso`() {
        // El mismo cuerpo, pero pidiendo ganar músculo: no tiene sentido bloquear un superávit.
        val r = NutritionMath.computeTargets(
            sex = "female", weightKg = 45.0, heightCm = 162.0, age = 30,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_GAIN, ratePct = 0.3
        )
        assertTrue(r.bmi < 18.5)
        assertTrue(!r.lowBmiGuard, "No debería activarse al pedir ganar peso")
        assertTrue(r.targetKcal > r.tdee)
    }

    @Test
    fun `la guardia por IMC no se activa en normopeso`() {
        val r = NutritionMath.computeTargets(
            sex = "male", weightKg = 75.5, heightCm = 184.0, age = 21,
            activityLevel = "sedentary", goal = NutritionMath.GOAL_LOSE, ratePct = -0.75
        )
        assertTrue(r.bmi >= 18.5)
        assertTrue(!r.lowBmiGuard)
    }

    // ── Autoajuste ────────────────────────────────────────────────────────────

    @Test
    fun `el gasto medido sale de la ingesta y del cambio de peso`() {
        // 14 días comiendo 2.600 y perdiendo 0,4 kg → 2600 + (0,4 × 7700 / 14) = 2820
        assertClose(2820.0, NutritionMath.measuredTdee(2600.0, -0.4, 14), tolerance = 1.0)
    }

    @Test
    fun `sin datos suficientes no se ajusta nada`() {
        val r = NutritionMath.reconcile(
            NutritionMath.AdaptiveInput(
                loggedDays = 5, weighIns = 2, spanDays = 4,
                avgDailyIntakeKcal = 2400.0, weightChangeKg = -0.3, previousTdee = 2500.0
            )
        )
        assertTrue(r is NutritionMath.AdaptiveResult.NotEnoughData)
    }

    @Test
    fun `el ajuste se mezcla con el valor previo en vez de saltar`() {
        val previo = 2500.0
        val r = NutritionMath.reconcile(
            NutritionMath.AdaptiveInput(
                loggedDays = 20, weighIns = 8, spanDays = 20,
                avgDailyIntakeKcal = 2800.0, weightChangeKg = 0.0, previousTdee = previo
            )
        )
        r as NutritionMath.AdaptiveResult.Adjusted
        assertClose(2800.0, r.rawMeasured, tolerance = 1.0)
        assertTrue(r.newTdee > previo && r.newTdee < r.rawMeasured)
        assertClose(0.3 * 2800.0 + 0.7 * previo, r.newTdee, tolerance = 1.0)
    }

    @Test
    fun `un gasto medido absurdo se rechaza en vez de destrozar el objetivo`() {
        val r = NutritionMath.reconcile(
            NutritionMath.AdaptiveInput(
                loggedDays = 20, weighIns = 8, spanDays = 20,
                avgDailyIntakeKcal = 600.0, weightChangeKg = 0.0, previousTdee = 2500.0
            )
        )
        assertTrue(r is NutritionMath.AdaptiveResult.NotEnoughData)
    }

    @Test
    fun `dias registrados concentrados en poca ventana no bastan sin cobertura`() {
        // 14 días con buen registro (pasa ADAPTIVE_MIN_LOGGED_DAYS) pero repartidos en una ventana de
        // 90 días: cobertura real del 15,5%, muy por debajo del 80% exigido (AUD-9/AUD-10).
        val r = NutritionMath.reconcile(
            NutritionMath.AdaptiveInput(
                loggedDays = 14, weighIns = 8, spanDays = 20,
                avgDailyIntakeKcal = 2600.0, weightChangeKg = -0.4, previousTdee = 2500.0,
                windowDays = 90
            )
        )
        assertTrue(r is NutritionMath.AdaptiveResult.NotEnoughData)
    }

    @Test
    fun `con cobertura suficiente dentro de la ventana si se ajusta`() {
        val r = NutritionMath.reconcile(
            NutritionMath.AdaptiveInput(
                loggedDays = 24, weighIns = 8, spanDays = 28,
                avgDailyIntakeKcal = 2600.0, weightChangeKg = -0.4, previousTdee = 2500.0,
                windowDays = 28
            )
        )
        assertTrue(r is NutritionMath.AdaptiveResult.Adjusted)
    }

    @Test
    fun `el minimo de dias de la pulsera es la mitad de la ventana, redondeado hacia arriba`() {
        // Con WEARABLE_AVERAGE_DAYS = 14 y cobertura 0,5, hacen falta 7 días con dato (AUD-13): un solo
        // día ya no puede ser "la media".
        assertEquals(7, NutritionMath.WEARABLE_MIN_DAYS_WITH_DATA)
    }

    @Test
    fun `el valor por defecto de windowDays reproduce el comportamiento anterior a AUD-10`() {
        // Sin pasar windowDays explícitamente, la cobertura es siempre 100% (loggedDays / loggedDays):
        // los tests y llamadores anteriores a este cambio no se rompen por el nuevo requisito.
        val input = NutritionMath.AdaptiveInput(
            loggedDays = 14, weighIns = 8, spanDays = 14,
            avgDailyIntakeKcal = 2600.0, weightChangeKg = -0.4, previousTdee = 2500.0
        )
        assertEquals(14, input.windowDays)
        assertTrue(NutritionMath.reconcile(input) is NutritionMath.AdaptiveResult.Adjusted)
    }

    @Test
    fun `el gasto medido sustituye a la formula cuando se aporta`() {
        fun target(measured: Double?) = NutritionMath.computeTargets(
            sex = "male", weightKg = 80.0, heightCm = 180.0, age = 25,
            activityLevel = "moderate", goal = NutritionMath.GOAL_MAINTAIN, ratePct = 0.0,
            measuredTdee = measured
        )
        val conMedicion = target(2900.0)
        assertEquals(2900, conMedicion.targetKcal)
        assertTrue(conMedicion.tdeeIsMeasured)
        assertFalse(target(null).tdeeIsMeasured)
    }

    // ── Validación de ritmos ──────────────────────────────────────────────────

    @Test
    fun `cero solo es valido en mantenimiento`() {
        assertFalse(NutritionMath.isRateValid(NutritionMath.GOAL_LOSE, 0.0))
        assertFalse(NutritionMath.isRateValid(NutritionMath.GOAL_GAIN, 0.0))
        assertTrue(NutritionMath.isRateValid(NutritionMath.GOAL_MAINTAIN, 0.0))
    }

    @Test
    fun `un ritmo con el signo contrario al objetivo se rechaza`() {
        assertFalse(NutritionMath.isRateValid(NutritionMath.GOAL_LOSE, 0.5))
        assertFalse(NutritionMath.isRateValid(NutritionMath.GOAL_GAIN, -0.3))
    }

    @Test
    fun `los ritmos por defecto son validos y recomendados`() {
        for (goal in NutritionMath.ALL_GOALS) {
            val porDefecto = NutritionMath.defaultRateFor(goal)
            assertTrue(
                NutritionMath.isRateValid(goal, porDefecto),
                "El ritmo por defecto de $goal ($porDefecto) no pasa su propia validación"
            )
        }
    }

    @Test
    fun `mantener ignora cualquier ritmo que llegue`() {
        val r = NutritionMath.computeTargets(
            sex = "male", weightKg = 80.0, heightCm = 180.0, age = 25,
            activityLevel = "moderate", goal = NutritionMath.GOAL_MAINTAIN, ratePct = -0.75
        )
        assertEquals(r.tdee, r.targetKcal)
        assertClose(0.0, r.effectiveRate)
    }

    // ── Formato ───────────────────────────────────────────────────────────────

    @Test
    fun `el formateo de decimales es estable entre plataformas`() {
        assertEquals("0.4", NutritionMath.formatOneDecimal(0.3775))
        assertEquals("1.0", NutritionMath.formatOneDecimal(0.96))
        assertEquals("0.53", NutritionMath.formatTwoDecimals(0.5298))
        assertEquals("0.05", NutritionMath.formatTwoDecimals(0.05))
    }

    @Test
    fun `el formateo conserva el signo para magnitudes menores que 1`() {
        // AUD-23: la división entera truncaba hacia cero (`-4 / 10 == 0`), así que un valor negativo
        // menor que 1 en magnitud perdía el signo. Justo el tramo donde viven todos los ritmos.
        assertEquals("-0.4", NutritionMath.formatOneDecimal(-0.4))
        assertEquals("-0.05", NutritionMath.formatTwoDecimals(-0.05))
        // Y sigue funcionando igual que antes para |valor| >= 1, donde el bug no se notaba.
        assertEquals("-1.5", NutritionMath.formatOneDecimal(-1.5))
        assertEquals("-2.30", NutritionMath.formatTwoDecimals(-2.3))
    }
}
