package com.gymsmart.gymsmart.services

import com.gymsmart.gymsmart.nutrition.NutritionMath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

// ── Modelos internos del servidor ─────────────────────────────────────────────

data class UserProfile(
    val userId:        String,
    val weightKg:      Double,
    val heightCm:      Double,
    val age:           Int,
    val sex:           String,
    val activityLevel: String,
    val goal:          String,
    /** Ritmo en **% de peso corporal por semana**, con signo (antes eran kcal/día — ver migración). */
    val goalRate:      Double,
    val updatedAt:     Long,
    /**
     * Gasto real medido por el autoajuste, en kcal/día absolutas. `null` mientras no haya datos.
     *
     * Se conserva **solo para mostrarlo** ("tu gasto medido es X"). **No se usa para calcular el
     * objetivo**: para eso está [adaptiveFactor]. Antes sí se usaba, y sustituía por completo a la
     * fórmula, con lo que `activityLevel`, el peso nuevo y las calorías activas de la pulsera dejaban de
     * influir en el objetivo en cuanto existía un valor medido (AUD-3).
     */
    val adaptiveTdee:  Double? = null,
    /** Cuándo se aplicó el último ajuste, para no re-mezclar varias veces al día. */
    val adaptiveUpdatedAt: Long = 0L,
    /**
     * Corrección del autoajuste expresada como **factor** sobre la estimación por fórmula
     * (`medido / fórmula`), no como kcal absolutas.
     *
     * Es la pieza que arregla AUD-3. Un factor se **compone** con la fórmula en vez de sustituirla, así
     * que sigue reaccionando a todo lo que la fórmula sabe: si el usuario adelgaza 10 kg su BMR baja y el
     * objetivo baja con él; si cambia de sedentario a muy activo, sube; si la pulsera registra más
     * actividad, también. Lo que el factor captura es precisamente lo que la fórmula **no** puede saber:
     * que para este cuerpo concreto la predicción se queda corta o larga en un tanto por ciento.
     *
     * Y hace innecesaria la invalidación: no hay que borrarlo al cambiar de actividad u objetivo, porque
     * se reescala solo.
     */
    val adaptiveFactor: Double? = null,
    /**
     * Cuándo se cambió por última vez el **objetivo o el ritmo**, para el periodo de gracia del
     * autoajuste (C-5, [NutritionMath.ADAPTIVE_GOAL_CHANGE_GRACE_DAYS]).
     *
     * Solo lo mueven `goal` y `goalRate`: cambiar el peso, la altura o el nivel de actividad no desplaza
     * glucógeno ni agua, así que no hay motivo para descartar medidas por eso.
     *
     * `null` en los perfiles que existían antes de esta columna. Se trata como **"sin gracia"**: se mide
     * como se medía hasta ahora en vez de bloquear el autoajuste durante dos semanas a quien ya llevaba
     * tiempo con su objetivo puesto.
     */
    val goalChangedAt: Long? = null
)

typealias NutritionTargets = NutritionMath.TargetResult

// ── Servicio ──────────────────────────────────────────────────────────────────

class ProfileService(private val turso: TursoService) {

    // AUD-24: `getProfile → cooldown → reconcile → saveAdaptiveTdee` no era atómico, así que dos
    // llamadas simultáneas (doble apertura de pantalla, reintento de red) podían superar el cooldown
    // las dos y aplicar la mezcla dos veces — justo lo que el comentario de `ADAPTIVE_COOLDOWN_MS`
    // avisa que rompería el suavizado. Un `Mutex` por usuario serializa esa secuencia sin bloquear a
    // otros usuarios entre sí; para una app de un solo usuario real es más que suficiente, y es mucho
    // más simple que una transacción distribuida contra Turso.
    private val adaptiveLocks = ConcurrentHashMap<String, Mutex>()
    private fun adaptiveLockFor(userId: String): Mutex = adaptiveLocks.computeIfAbsent(userId) { Mutex() }

    /**
     * Migraciones de **datos** que no son autolimitadas, con su registro de aplicadas.
     *
     * Las migraciones de esquema de [initTable] son idempotentes *por construcción*: un
     * `ALTER TABLE ADD COLUMN` falla a partir del segundo arranque y el `runCatching` se come el error,
     * y los `UPDATE` que hay son convergentes (tras convertir, la fila deja de entrar en el `WHERE`).
     * Por eso ninguna necesitaba registro.
     *
     * Una migración de datos como "pon `adaptive_factor` a NULL una vez" **no es de esa clase**: se
     * ejecuta con éxito todas las veces. `initTable()` corre en cada arranque del servidor
     * (`Application.kt:48`) y en el plan gratuito de Render el servicio se duerme y arranca en frío
     * constantemente, así que sin registro el reseteo borraría cada pocas horas un factor recién medido
     * y el autoajuste quedaría desactivado para siempre, **sin que nada avisara**.
     *
     * **El registro no puede ser una columna de `user_profiles`**: `upsertProfile` usa
     * `INSERT OR REPLACE`, que borra la fila y la reinserta, así que cualquier columna fuera de su lista
     * vuelve a NULL — es la trampa que obliga a existir a `restoreStatement`. Un centinela ahí se
     * reiniciaría en el primer guardado de perfil y el reseteo volvería a dispararse en el siguiente
     * arranque. De ahí una tabla propia, que además le sirve a la siguiente migración no convergente.
     */
    private suspend fun applyOnce(id: String, statements: List<Pair<String, List<String>>>) {
        val yaAplicada = turso.execute(
            "SELECT id FROM schema_migrations WHERE id = ?",
            listOf(id)
        )
        if (turso.extractRows(yaAplicada).isNotEmpty()) return

        // El marcador se inserta **en la misma transacción** que los cambios: si la red falla a mitad,
        // no se confirma ni el cambio ni el marcador, y el próximo arranque lo reintenta. Al revés
        // (marcar primero, o marcar aparte) una migración podría quedar marcada sin haberse aplicado.
        turso.executeBatch(
            listOf("BEGIN IMMEDIATE" to emptyList<String>()) +
                statements +
                listOf(
                    "INSERT INTO schema_migrations (id, applied_at) VALUES (?, ?)" to
                        listOf(id, System.currentTimeMillis().toString()),
                    "COMMIT" to emptyList()
                )
        )
    }

    suspend fun initTable() {
        turso.execute(
            """
            CREATE TABLE IF NOT EXISTS user_profiles (
                user_id        TEXT    PRIMARY KEY,
                weight_kg      REAL    NOT NULL,
                height_cm      REAL    NOT NULL,
                age            INTEGER NOT NULL,
                sex            TEXT    NOT NULL,
                activity_level TEXT    NOT NULL,
                goal           TEXT    NOT NULL,
                goal_rate      REAL    NOT NULL,
                updated_at     INTEGER NOT NULL,
                adaptive_tdee  REAL,
                adaptive_updated_at INTEGER,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        // Igual que en weight_history: columna nueva sobre una tabla que ya existe. El fallo por
        // columna duplicada a partir del segundo arranque se ignora a propósito.
        runCatching { turso.execute("ALTER TABLE user_profiles ADD COLUMN adaptive_tdee REAL") }
        runCatching { turso.execute("ALTER TABLE user_profiles ADD COLUMN adaptive_updated_at INTEGER") }
        // El autoajuste pasa a guardarse como factor sobre la fórmula en vez de como kcal absolutas,
        // para que se componga con ella en vez de sustituirla (AUD-3). Los perfiles que ya tenían
        // `adaptive_tdee` derivan su factor en la primera lectura, no hace falta migrar el dato aquí.
        runCatching { turso.execute("ALTER TABLE user_profiles ADD COLUMN adaptive_factor REAL") }
        // Periodo de gracia del autoajuste tras cambiar de objetivo (C-5). Idempotente igual que las de
        // arriba: el `ALTER` falla a partir del segundo arranque y el `runCatching` se come el error.
        // Las filas que ya existían se quedan en NULL, que se interpreta como "sin gracia".
        runCatching { turso.execute("ALTER TABLE user_profiles ADD COLUMN goal_changed_at INTEGER") }

        // Migración de unidades de goal_rate (2026-11): antes guardaba kcal/día con signo (-400, +500)
        // y ahora guarda % de peso corporal por semana (-0.75, +0.375). Sin esto, un perfil antiguo
        // se interpretaría como "-400 % semanal" y produciría un objetivo absurdo.
        //
        // El discriminador es seguro porque los dos rangos no se solapan ni de lejos: un ritmo en %
        // nunca pasa de 1 en valor absoluto, y un ajuste en kcal nunca bajaba de 250. Es idempotente:
        // tras convertir, |goal_rate| < 10 y la fila deja de entrar en el WHERE.
        runCatching {
            turso.execute(
                """
                UPDATE user_profiles
                SET goal_rate = goal_rate * 7.0 * 100.0 / (weight_kg * ${NutritionMath.KCAL_PER_KG})
                WHERE ABS(goal_rate) > 10
                """.trimIndent()
            )
        }

        // La opción de "recomposición" se eliminó (decisión del usuario, 2026-11-23): en el cálculo
        // real no era más que un déficit con otro nombre (AUD-7 en doc/11-auditoria-kcal.md). Un
        // perfil que todavía tenga goal = 'recomp' en BBDD se migra a mantenimiento, el objetivo más
        // cercano a lo que de verdad hacía (era un déficit del 0-0,25%, casi mantenimiento).
        // Idempotente: una fila ya migrada no vuelve a entrar en el WHERE.
        runCatching {
            turso.execute(
                "UPDATE user_profiles SET goal = 'maintain', goal_rate = 0.0 WHERE goal = 'recomp'"
            )
        }

        // Encaja el ritmo dentro de lo permitido por su objetivo. Solo limita la **magnitud máxima**:
        // un ritmo más lento que la banda recomendada es perfectamente válido (se retiene músculo
        // igual o mejor, solo se tarda más), así que no se sube nada hacia arriba. Un -400 kcal/día a
        // 75,5 kg se convierte en -0,36 %/sem y se queda tal cual.
        // Idempotente: encajar un valor ya encajado no lo cambia, así que puede correr en cada arranque
        // y además repara cualquier valor fuera de rango escrito a mano en la base de datos.
        // AUD-18: `MIN(-0.05, goal_rate)` no era un tope, era un mínimo encubierto — con goal_rate =
        // -0.02 (válido, más lento que la banda), `MIN(-0.05, -0.02)` elegía -0.05 y **subía** el
        // déficit, justo lo que el comentario de arriba dice que no debe pasar. Reescrito para
        // reproducir exactamente `NutritionMath.clampRate`: toma la magnitud, la acota por arriba al
        // tope del objetivo, y le devuelve el signo correcto — nunca sube una magnitud que ya era
        // válida, y de paso corrige cualquier fila con el signo equivocado.
        runCatching {
            turso.execute(
                """
                UPDATE user_profiles
                SET goal_rate = CASE goal
                    WHEN 'lose_fat'    THEN -MIN(1.0, ABS(goal_rate))
                    WHEN 'gain_muscle' THEN  MIN(0.5, ABS(goal_rate))
                    ELSE 0.0
                END
                WHERE ABS(goal_rate) <= 10
                """.trimIndent()
            )
        }

        // ── Migraciones de datos con registro (ver applyOnce) ─────────────────
        turso.execute(
            """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                id         TEXT    PRIMARY KEY,
                applied_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // C-1: al corregir el TEF en modo pulsera, el gasto estimado sube ~10% para la misma actividad.
        // Un `adaptive_factor` medido con la fórmula vieja ya puede haber absorbido parte de ese sesgo
        // (compensándolo con un factor por encima de 1 sin que nadie lo supiera), así que conservarlo
        // aplicaría la corrección dos veces. Se descarta y se vuelve a medir desde cero — decisión
        // explícita del usuario (2026-11-28), frente a la alternativa de dejarlo converger solo en 4-8
        // semanas. `adaptive_tdee` se borra también porque `adaptiveFactorFor` deriva de él un factor
        // cuando no hay uno guardado: dejarlo vivo reconstruiría la corrección que se quiere soltar.
        //
        // En `runCatching` para que un fallo de red no impida arrancar el servidor. Si falla, el
        // marcador no se escribe y se reintenta en el próximo arranque; mientras tanto el estado es
        // "factor viejo todavía puesto", que es degradado pero no peligroso — está acotado a 0,70-1,30 y
        // se corrige solo con las mezclas semanales.
        runCatching {
            applyOnce(
                "c1_tef_wearable_reset_adaptive_factor",
                listOf(
                    "UPDATE user_profiles SET adaptive_factor = NULL, adaptive_tdee = NULL" to emptyList()
                )
            )
        }
    }

    suspend fun initWeightHistoryTable() {
        turso.execute(
            """
        CREATE TABLE IF NOT EXISTS weight_history (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id    TEXT    NOT NULL,
            weight_kg  REAL    NOT NULL,
            waist_cm   REAL,
            day        TEXT    NOT NULL,
            created_at INTEGER NOT NULL,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
        """.trimIndent()
        )
        // `waist_cm` ya no la escribe ni la lee ningún código (se eliminó el seguimiento de cintura,
        // decisión del usuario, 2026-11-23). Se deja la columna sin DROP: borrarla es destructivo y no
        // hacía falta, es inofensiva ahí sin uso. El ALTER se mantiene también sin cambios porque es
        // el mismo runCatching idempotente de siempre: las bases creadas antes de 2026-11 no tienen la
        // columna y CREATE TABLE IF NOT EXISTS no la añade; SQLite no soporta "ADD COLUMN IF NOT
        // EXISTS", así que el error de columna duplicada se ignora a propósito en cada arranque
        // posterior al primero.
        runCatching { turso.execute("ALTER TABLE weight_history ADD COLUMN waist_cm REAL") }
    }

    suspend fun initSubscriptionTable() {
        turso.execute("""
        CREATE TABLE IF NOT EXISTS subscriptions (
            user_id    TEXT    PRIMARY KEY,
            plan       TEXT    NOT NULL DEFAULT 'free',
            expires_at INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        )
    """.trimIndent())
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    suspend fun upsertProfile(
        userId:        String,
        weightKg:      Double,
        heightCm:      Double,
        age:           Int,
        sex:           String,
        activityLevel: String,
        goal:          String,
        goalRate:      Double,
        recordWeighIn: Boolean = true
    ): UserProfile {
        val now = System.currentTimeMillis()

        // Obtener peso anterior
        val previous = getProfile(userId)

        // INSERT OR REPLACE borra la fila y la reinserta, así que adaptive_tdee/adaptive_factor se
        // perderían en cada guardado del perfil. Se restauran aparte porque los args de Turso viajan
        // todos como texto y no admiten NULL en el propio INSERT. El factor importa más que el valor
        // en kcal: es el que de verdad entra en el cálculo, y perderlo significaría tirar semanas de
        // medición en cada guardado de perfil. No hace falta invalidarlo al cambiar de actividad u
        // objetivo — un factor se reescala solo (ver AUD-3).
        //
        // AUD-24: antes el INSERT y la restauración eran dos llamadas HTTP separadas a Turso. Si la
        // segunda fallaba, el perfil ya había quedado escrito sin el autoajuste, perdido sin rastro.
        // Van en un solo `executeBatch` envuelto en BEGIN/COMMIT: si cualquiera de las dos falla, la
        // transacción no llega a confirmarse y el perfil se queda como estaba antes de esta llamada.
        // ── Un guardado que no es un pesaje no puede cambiar el peso (C-6b) ───
        //
        // `recordWeighIn = false` significa "este guardado no es sobre el peso", y hasta ahora eso solo
        // impedía escribir en `weight_history`: el peso del formulario seguía sobrescribiendo
        // `user_profiles.weight_kg`, que es lo que alimenta el BMR y los objetivos. Media verdad, y
        // media invariante es la clase de hueco que ya costó AUD-1 ("el límite existía pero era
        // evadible").
        //
        // El daño necesita un peso **obsoleto** en el formulario, y navegando por la app no es
        // alcanzable: `ProfileScreen` usa `remember` (no `rememberSaveable`) con `LaunchedEffect(Unit)`,
        // así que al volver a ella se recrea y recarga el perfil. Queda el caso de que el peso cambie
        // desde fuera de esa sesión de pantalla —otro dispositivo, otra pestaña— y el de cualquier
        // pantalla futura que reguarde el perfil sin ser un pesaje. Honrarlo aquí cierra la clase
        // entera en vez de una instancia.
        //
        // **Efecto de conducta aceptado a conciencia**: si alguien teclea un peso nuevo en la tarjeta
        // "Cuerpo y objetivo" y luego pulsa el botón de "Datos personales", su peso se ignora. Es lo
        // correcto — esa tarjeta tiene su propio botón justo al lado — y lo sorprendente era lo de
        // antes, que un botón de datos personales guardara el peso.
        val effectiveWeightKg =
            if (!recordWeighIn && previous != null) previous.weightKg else weightKg

        // ── Periodo de gracia del autoajuste (C-5) ────────────────────────────
        //
        // Solo cuenta como "cambio de objetivo" tocar `goal` o `goalRate`: cambiar peso, altura o nivel
        // de actividad no mueve glucógeno ni agua, así que descartar medidas por eso sería tirar datos
        // buenos. Un perfil nuevo también arranca el periodo: su objetivo se acaba de fijar.
        //
        // El ritmo se compara con epsilon y no con `!=`: es un Double que viaja como texto, y una
        // diferencia de redondeo que disparara un periodo de gracia espurio dejaría al usuario sin
        // autoajuste durante dos semanas sin ningún motivo.
        val goalChanged = previous == null ||
            previous.goal != goal ||
            kotlin.math.abs(previous.goalRate - goalRate) > 1e-9
        val goalChangedAtToWrite: Long? = if (goalChanged) now else previous?.goalChangedAt

        // `INSERT OR REPLACE` borra la fila entera, así que una columna que no esté en la lista vuelve a
        // NULL — es la misma trampa que obliga a existir a `restoreStatement`. Por eso `goal_changed_at`
        // va **dentro** del INSERT y no en una restauración aparte: una segunda sentencia que hay que
        // acordarse de mantener sincronizada es exactamente el tipo de fragilidad que ya costó un bug
        // aquí. Y como los args de Turso no admiten NULL, cuando no hay valor que escribir se elige la
        // variante de SQL que no menciona la columna — mismo patrón que `saveAdaptiveTdee`.
        val goalChangedColumn = if (goalChangedAtToWrite != null) ", goal_changed_at" else ""
        val goalChangedValue  = if (goalChangedAtToWrite != null) ", ?" else ""
        val goalChangedArg    = listOfNotNull(goalChangedAtToWrite?.toString())

        val restoreStatement = previous?.adaptiveTdee?.let { tdee ->
            if (previous.adaptiveFactor != null) {
                "UPDATE user_profiles SET adaptive_tdee = ?, adaptive_updated_at = ?, adaptive_factor = ? WHERE user_id = ?" to
                    listOf(tdee.toString(), previous.adaptiveUpdatedAt.toString(), previous.adaptiveFactor.toString(), userId)
            } else {
                "UPDATE user_profiles SET adaptive_tdee = ?, adaptive_updated_at = ? WHERE user_id = ?" to
                    listOf(tdee.toString(), previous.adaptiveUpdatedAt.toString(), userId)
            }
        }

        turso.executeBatch(
            listOfNotNull(
                "BEGIN IMMEDIATE" to emptyList(),
                """
                INSERT OR REPLACE INTO user_profiles
                    (user_id, weight_kg, height_cm, age, sex, activity_level, goal, goal_rate,
                     updated_at$goalChangedColumn)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?$goalChangedValue)
                """.trimIndent() to listOf(
                    userId, effectiveWeightKg.toString(), heightCm.toString(), age.toString(),
                    sex, activityLevel, goal, goalRate.toString(), now.toString()
                ) + goalChangedArg,
                restoreStatement,
                "COMMIT" to emptyList()
            )
        )

        // Se guarda historial SIEMPRE, no solo cuando cambia el peso. Antes, un pesaje que coincidía
        // con el peso ya guardado no generaba fila: los días de peso estable se eliminaban
        // sistemáticamente de la muestra, lo que infla la pendiente de la regresión y distorsiona el
        // autoajuste directamente, porque el término de corrección *es* esa pendiente (AUD-12 en
        // doc/11-auditoria-kcal.md). El "evitar duplicado del mismo día" de abajo ya hacía un
        // UPSERT-por-día; con esto, un día sin cambio de peso simplemente reescribe la fila de hoy con
        // el mismo número, en vez de no dejar ningún rastro de que ese día se siguió el peso.
        //
        // El seguimiento de cintura se eliminó (decisión del usuario, 2026-11-23: medirla era pereza
        // para el día a día); la columna `waist_cm` se deja sin tocar en la BBDD (no es destructivo
        // borrarla, pero tampoco hace falta) y simplemente ya no se escribe ni se lee desde aquí.
        // C-6: ese "SIEMPRE" era cierto respecto a *si el peso cambió*, pero no respecto a *si el
        // usuario se ha pesado*. El formulario de perfil reenvía el peso que tenía cargado también
        // cuando lo que se edita son los datos personales, y ese valor puede tener días: grabarlo como
        // el pesaje de hoy mete un dato arrastrado (LOCF) que aplana la pendiente del autoajuste y
        // encima cuenta para su mínimo de 8 pesajes sin aportar ninguna medida nueva. `recordWeighIn`
        // distingue los dos casos; AUD-12 sigue intacto para todos los caminos que sí son un pesaje.
        if (recordWeighIn) {
            val today = java.time.LocalDate.now().toString()

            // Evitar duplicado del mismo día
            val existing = turso.execute(
                "SELECT id FROM weight_history WHERE user_id = ? AND day = ?",
                listOf(userId, today)
            )
            if (turso.extractRows(existing).isEmpty()) {
                turso.execute(
                    "INSERT INTO weight_history (user_id, weight_kg, day, created_at) VALUES (?, ?, ?, ?)",
                    listOf(userId, effectiveWeightKg.toString(), today, now.toString())
                )
            } else {
                turso.execute(
                    "UPDATE weight_history SET weight_kg = ?, created_at = ? WHERE user_id = ? AND day = ?",
                    listOf(effectiveWeightKg.toString(), now.toString(), userId, today)
                )
            }
        }

        return UserProfile(
            userId, effectiveWeightKg, heightCm, age, sex, activityLevel, goal, goalRate, now,
            goalChangedAt = goalChangedAtToWrite,
            adaptiveTdee = previous?.adaptiveTdee
        )
    }

    suspend fun getProfile(userId: String): UserProfile? {
        val result = turso.execute(
            "SELECT user_id, weight_kg, height_cm, age, sex, activity_level, goal, goal_rate, updated_at, adaptive_tdee, adaptive_updated_at, adaptive_factor, goal_changed_at FROM user_profiles WHERE user_id = ?",
            listOf(userId)
        )
        val row = turso.extractRows(result).firstOrNull() ?: return null
        return UserProfile(
            userId        = row[0] ?: return null,
            weightKg      = row[1]?.toDoubleOrNull() ?: return null,
            heightCm      = row[2]?.toDoubleOrNull() ?: return null,
            age           = row[3]?.toIntOrNull()    ?: return null,
            sex           = row[4] ?: return null,
            activityLevel = row[5] ?: return null,
            goal          = row[6] ?: return null,
            goalRate      = row[7]?.toDoubleOrNull() ?: return null,
            updatedAt     = row[8]?.toLongOrNull()   ?: 0L,
            adaptiveTdee  = row.getOrNull(9)?.toDoubleOrNull(),
            adaptiveUpdatedAt = row.getOrNull(10)?.toLongOrNull() ?: 0L,
            adaptiveFactor = row.getOrNull(11)?.toDoubleOrNull(),
            goalChangedAt  = row.getOrNull(12)?.toLongOrNull()
        )
    }

    /**
     * Factor del autoajuste listo para usar, con su conversión de los perfiles antiguos.
     *
     * Un perfil que ya tenía `adaptive_tdee` (kcal absolutas) pero todavía no tiene factor deriva el
     * suyo aquí, dividiendo por la fórmula de **hoy**. No es exactamente el factor que se midió en su
     * día —la fórmula de entonces pudo ser otra si cambió el peso— pero es la mejor conversión posible
     * sin guardar historial, y a partir de la siguiente reconciliación ya se mide limpio.
     *
     * Se acota a [NutritionMath.ADAPTIVE_FACTOR_RANGE]: una corrección mayor que eso no es metabolismo,
     * es registro incompleto de comidas, y no debe poder destrozar el objetivo.
     */
    private fun adaptiveFactorFor(profile: UserProfile, avgActiveKcal: Double): Double? {
        val stored = profile.adaptiveFactor
        if (stored != null) return stored.coerceIn(NutritionMath.ADAPTIVE_FACTOR_RANGE)

        val medido = profile.adaptiveTdee ?: return null
        val formula = formulaTdeeFor(profile, avgActiveKcal)
        if (formula <= 0.0) return null
        return (medido / formula).coerceIn(NutritionMath.ADAPTIVE_FACTOR_RANGE)
    }

    /** Gasto estimado por fórmula para este perfil, que es la base que corrige el factor. */
    private fun formulaTdeeFor(profile: UserProfile, avgActiveKcal: Double): Double =
        NutritionMath.tdee(
            NutritionMath.bmr(profile.sex, profile.weightKg, profile.heightCm, profile.age),
            profile.activityLevel,
            avgActiveKcal
        )

    suspend fun hasProfile(userId: String): Boolean = getProfile(userId) != null

    // ── Cálculo de TDEE y macros ──────────────────────────────────────────────
    //
    // La fórmula vive entera en `shared/.../nutrition/NutritionMath.kt`, compartida con el cliente,
    // porque el Onboarding necesita calcular la vista previa en local antes de que exista perfil.
    // Cada constante está justificada en doc/10-fundamento-cientifico.md.

    /**
     * @param avgActiveKcal media de calorías activas de días completos (solo modo pulsera); la aporta
     *   el cliente, que es quien tiene acceso a Health Connect.
     * @param carryOverKcal arrastre del día anterior en modo pulsera.
     */
    fun computeTargets(
        profile:       UserProfile,
        avgActiveKcal: Double = 0.0,
        carryOverKcal: Double = 0.0
    ): NutritionTargets = NutritionMath.computeTargets(
        sex           = profile.sex,
        weightKg      = profile.weightKg,
        heightCm      = profile.heightCm,
        age           = profile.age,
        activityLevel = profile.activityLevel,
        goal          = profile.goal,
        ratePct       = profile.goalRate,
        avgActiveKcal = avgActiveKcal,
        carryOverKcal = carryOverKcal,
        // **Composición, no sustitución** (AUD-3): el factor multiplica a la fórmula de hoy, así que el
        // objetivo sigue reaccionando al peso, al nivel de actividad y a las calorías activas de la
        // pulsera. Antes `adaptive_tdee` sustituía a la fórmula entera y todo eso dejaba de influir.
        measuredTdee  = adaptiveFactorFor(profile, avgActiveKcal)
            ?.let { formulaTdeeFor(profile, avgActiveKcal) * it }
    )

    /**
     * Guarda el resultado del autoajuste.
     *
     * @param tdee gasto medido en kcal absolutas, **solo para mostrarlo** al usuario.
     * @param factor la corrección que de verdad entra en el cálculo. Es lo que se compone con la
     *   fórmula; ver [UserProfile.adaptiveFactor].
     */
    suspend fun saveAdaptiveTdee(
        userId:    String,
        tdee:      Double,
        factor:    Double?,
        updatedAt: Long = System.currentTimeMillis()
    ) {
        // Los args de Turso viajan todos como texto y no admiten NULL, así que se elige la variante de
        // SQL que no menciona la columna — mismo patrón que `waist_cm` en el historial de peso.
        if (factor != null) {
            turso.execute(
                "UPDATE user_profiles SET adaptive_tdee = ?, adaptive_updated_at = ?, adaptive_factor = ? WHERE user_id = ?",
                listOf(tdee.toString(), updatedAt.toString(), factor.toString(), userId)
            )
        } else {
            turso.execute(
                "UPDATE user_profiles SET adaptive_tdee = ?, adaptive_updated_at = ? WHERE user_id = ?",
                listOf(tdee.toString(), updatedAt.toString(), userId)
            )
        }
    }

    /**
     * Cada cuánto se vuelve a mezclar el gasto medido.
     *
     * Importa más de lo que parece: `reconcile` mezcla 30% del valor nuevo con 70% del anterior para
     * que una semana atípica no dé un salto. Si el ajuste corriera en cada carga de pantalla, esa
     * mezcla se aplicaría decenas de veces al día y el resultado convergería al valor crudo medido —
     * es decir, el suavizado desaparecería justo cuando más hace falta.
     */
    private val ADAPTIVE_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000

    /**
     * Autoajuste: mide el gasto real del usuario a partir de su tendencia de peso y de lo que ha
     * comido, y corrige el objetivo si hay datos suficientes (doc/10-fundamento-cientifico.md §5).
     *
     * Es lo único capaz de corregir un multiplicador de actividad mal elegido, que puede estar
     * equivocado en ±350 kcal/día de forma permanente sin que ninguna fórmula lo detecte.
     *
     * @param dailyIntake pares (día, kcal) de [NutritionService.getDailyIntake].
     * @param avgActiveKcal media de calorías activas que aporta el cliente. **Importa**: sin ella, el
     *   ancla de la primera reconciliación en modo pulsera caía al respaldo `BMR × 1,2`, que es el valor
     *   de "no tengo datos" y no el gasto con el que el usuario llevaba semanas comiendo. El ajuste
     *   salía en dirección contraria a la medición y el aviso mostraba el signo opuesto (AUD-4).
     */
    suspend fun runAdaptiveAdjustment(
        userId:        String,
        dailyIntake:   List<Pair<String, Double>>,
        windowDays:    Int = 28,
        avgActiveKcal: Double = 0.0
    ): NutritionMath.AdaptiveResult = adaptiveLockFor(userId).withLock {
        runAdaptiveAdjustmentLocked(userId, dailyIntake, windowDays, avgActiveKcal)
    }

    private suspend fun runAdaptiveAdjustmentLocked(
        userId:        String,
        dailyIntake:   List<Pair<String, Double>>,
        windowDays:    Int,
        avgActiveKcal: Double
    ): NutritionMath.AdaptiveResult {
        val profile = getProfile(userId)
            ?: return NutritionMath.AdaptiveResult.NotEnoughData("Sin perfil")

        val hoy = java.time.LocalDate.now()

        // Periodo de gracia tras cambiar de objetivo (C-5): los primeros días de un déficit nuevo mueven
        // glucógeno y agua, no grasa, y `measuredTdee` los lee como gasto a 7700 kcal/kg (+278 kcal/día
        // de sesgo, medido con Montecarlo) — suficiente para que el primer ajuste empuje en dirección
        // contraria a la correcta. La aritmética vive en `AdaptiveWindow`, que sí se puede testear.
        //
        // **Va antes que el enfriamiento** a propósito: la gracia dura 14 días y el enfriamiento 7, así
        // que al revés el usuario vería "ya ajustado esta semana" y el mensaje le cambiaría a mitad de
        // la espera por otro motivo. El de la gracia explica mejor lo que pasa y cubre todo el periodo.
        val ventana = AdaptiveWindow.compute(hoy, profile.goalChangedAt, windowDays)
        if (ventana is AdaptiveWindow.Result.InGrace) {
            return NutritionMath.AdaptiveResult.NotEnoughData(
                "Has cambiado tu objetivo hace poco, así que estoy volviendo a medir tu gasto. " +
                    "Faltan ${ventana.daysLeft} días: los primeros de un cambio mueven agua y " +
                    "glucógeno, no grasa, y medir ahí daría un resultado falso.",
                recalculating = true
            )
        }
        val medible = ventana as AdaptiveWindow.Result.Measurable
        val since = medible.since
        val effectiveWindowDays = medible.windowDays

        val sinceLast = System.currentTimeMillis() - profile.adaptiveUpdatedAt
        if (profile.adaptiveTdee != null && sinceLast < ADAPTIVE_COOLDOWN_MS) {
            val diasRestantes = ((ADAPTIVE_COOLDOWN_MS - sinceLast) / (24.0 * 60 * 60 * 1000)).toInt() + 1
            return NutritionMath.AdaptiveResult.NotEnoughData(
                "Ya ajustado esta semana — próxima revisión en $diasRestantes días"
            )
        }

        val weighIns = getWeightHistoryRaw(userId)
            .mapNotNull { (day, kg) ->
                runCatching { java.time.LocalDate.parse(day) }.getOrNull()?.let { it to kg }
            }
            .filter { !it.first.isBefore(since) }
            .sortedBy { it.first }

        if (weighIns.size < NutritionMath.ADAPTIVE_MIN_WEIGH_INS)
            return NutritionMath.AdaptiveResult.NotEnoughData(
                "Necesito al menos ${NutritionMath.ADAPTIVE_MIN_WEIGH_INS} pesajes (llevas ${weighIns.size})"
            )

        val spanDays = java.time.temporal.ChronoUnit.DAYS
            .between(weighIns.first().first, weighIns.last().first).toInt()

        // Un día con solo el desayuno contaba como un día completo registrado: la ingesta se
        // promediaba sobre "días con alguna fila" mientras la pendiente de peso se media sobre días
        // naturales, y la diferencia se interpretaba como metabolismo (AUD-10). Se filtra a días por
        // encima de ADAPTIVE_MIN_DAY_KCAL antes de contarlos como registrados.
        val intakeInWindow = dailyIntake.mapNotNull { (day, kcal) ->
            runCatching { java.time.LocalDate.parse(day) }.getOrNull()
                ?.takeIf { !it.isBefore(since) }?.let { kcal }
        }.filter { it >= NutritionMath.ADAPTIVE_MIN_DAY_KCAL }
        if (intakeInWindow.isEmpty())
            return NutritionMath.AdaptiveResult.NotEnoughData("Sin comidas registradas en el periodo")

        // Tendencia por mínimos cuadrados en vez de "último menos primero": un solo pesaje atípico
        // (agua, glucógeno, hora del día) desplazaría el resultado entero si se usaran los extremos.
        val slopeKgPerDay = leastSquaresSlope(
            xs = weighIns.map { ChronoDays(weighIns.first().first, it.first) },
            ys = weighIns.map { it.second }
        )

        // El ancla es el gasto con el que el usuario **ha estado comiendo de verdad**: la fórmula de hoy
        // ya corregida por el factor vigente, y con las calorías activas incluidas. Antes se pasaba
        // `tdee(bmr, activityLevel)` sin `avgActiveKcal`, que en modo pulsera cae al respaldo `× 1,2`:
        // el ajuste se mezclaba contra un valor ~300 kcal por debajo del real y acababa empujando en
        // dirección contraria a la medición (AUD-4).
        val formulaTdee = formulaTdeeFor(profile, avgActiveKcal)
        val anchor = adaptiveFactorFor(profile, avgActiveKcal)
            ?.let { formulaTdee * it }
            ?: formulaTdee

        val result = NutritionMath.reconcile(
            NutritionMath.AdaptiveInput(
                loggedDays         = intakeInWindow.size,
                weighIns           = weighIns.size,
                spanDays           = spanDays,
                avgDailyIntakeKcal = intakeInWindow.average(),
                weightChangeKg     = slopeKgPerDay * spanDays,
                previousTdee       = anchor,
                windowDays         = effectiveWindowDays
            )
        )

        if (result is NutritionMath.AdaptiveResult.Adjusted) {
            // Se persiste el **factor**, no las kcal: así la corrección se reescala sola cuando cambien
            // el peso, el nivel de actividad o la actividad medida, en vez de congelarse.
            val factor = if (formulaTdee > 0.0) {
                (result.newTdee / formulaTdee).coerceIn(NutritionMath.ADAPTIVE_FACTOR_RANGE)
            } else null
            saveAdaptiveTdee(userId, result.newTdee, factor)
        }
        return result
    }

    private fun ChronoDays(from: java.time.LocalDate, to: java.time.LocalDate): Double =
        java.time.temporal.ChronoUnit.DAYS.between(from, to).toDouble()

    private fun leastSquaresSlope(xs: List<Double>, ys: List<Double>): Double {
        val n = xs.size
        if (n < 2) return 0.0
        val meanX = xs.average()
        val meanY = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            num += dx * (ys[i] - meanY)
            den += dx * dx
        }
        return if (den == 0.0) 0.0 else num / den
    }

    /** Historial de peso en crudo (día, kg), para los cálculos internos del autoajuste. */
    private suspend fun getWeightHistoryRaw(userId: String): List<Pair<String, Double>> {
        val result = turso.execute(
            "SELECT day, weight_kg FROM weight_history WHERE user_id = ? ORDER BY day ASC",
            listOf(userId)
        )
        return turso.extractRows(result).mapNotNull { row ->
            val day = row.getOrNull(0) ?: return@mapNotNull null
            val kg = row.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            day to kg
        }
    }

    suspend fun getWeightHistory(userId: String): List<Map<String, String>> {
        val result = turso.execute(
            "SELECT weight_kg, day FROM weight_history WHERE user_id = ? ORDER BY day ASC",
            listOf(userId)
        )
        return turso.extractRows(result).mapNotNull { row ->
            val w = row[0] ?: return@mapNotNull null
            val d = row[1] ?: return@mapNotNull null
            mapOf("weightKg" to w, "day" to d)
        }
    }

    suspend fun getSubscription(userId: String): Pair<String, Long> {
        val result = turso.execute(
            "SELECT plan, expires_at FROM subscriptions WHERE user_id = ?",
            listOf(userId)
        )
        val row = turso.extractRows(result).firstOrNull()
            ?: return Pair("free", 0L)
        return Pair(row[0] ?: "free", row[1]?.toLongOrNull() ?: 0L)
    }

    suspend fun activatePremium(userId: String, durationMs: Long) {
        val expiresAt = System.currentTimeMillis() + durationMs
        turso.execute("""
        INSERT OR REPLACE INTO subscriptions (user_id, plan, expires_at)
        VALUES (?, 'premium', ?)
    """.trimIndent(), listOf(userId, expiresAt.toString()))
    }

    suspend fun isPremium(userId: String): Boolean {
        val (plan, expiresAt) = getSubscription(userId)
        return plan == "premium" && expiresAt > System.currentTimeMillis()
    }
}