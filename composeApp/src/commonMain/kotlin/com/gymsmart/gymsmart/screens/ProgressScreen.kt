package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gymsmart.gymsmart.model.WorkoutSession
import com.gymsmart.gymsmart.model.perSideFor
import com.gymsmart.gymsmart.model.sessionTotalKg
import com.gymsmart.gymsmart.services.WorkoutService
import com.gymsmart.gymsmart.ui.theme.MelocotonTheme
import com.gymsmart.gymsmart.ui.theme.Palette
import com.gymsmart.gymsmart.ui.theme.SystemBarsAppearance
import kotlin.time.Clock
import kotlinx.datetime.*

/**
 * Progreso: un calendario con los días entrenados y el detalle de cada uno.
 *
 * El histórico que alimenta esta pantalla no existía: al terminar un entrenamiento solo sobrevivía
 * "la última vez" de cada ejercicio, que es lo que la app usa para proponer peso, y todo lo demás se
 * tiraba. Ahora cada sesión terminada se guarda entera (ver `WorkoutSession`), así que esto no es
 * una vista nueva sobre datos viejos: **los datos empiezan a existir desde que se implementó**. Los
 * entrenamientos anteriores a eso no se pueden recuperar porque nunca llegaron a guardarse.
 */
/**
 * ProgressScreen con el estilo Melocotón (Tarea 3). Solo cambia el aspecto: la estructura y la lógica
 * de la pantalla son las de siempre, en [ProgressScreenContent].
 */
@Composable
fun ProgressScreen(
    workoutService: WorkoutService,
    onBack: () -> Unit
) {
    MelocotonTheme {
        // Fondo crema hasta arriba: la hora y la batería tienen que ir en oscuro.
        SystemBarsAppearance(darkStatusIcons = true)
        ProgressScreenContent(workoutService, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressScreenContent(
    workoutService: WorkoutService,
    onBack: () -> Unit
) {
    val hoy = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    var mes by remember { mutableStateOf(LocalDate(hoy.year, hoy.month, 1)) }
    var sesiones by remember { mutableStateOf<List<WorkoutSession>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var diaElegido by remember { mutableStateOf<LocalDate?>(null) }

    // Un mes cada vez: el servidor filtra por rango de fechas y no se traen años enteros al móvil.
    LaunchedEffect(mes) {
        cargando = true
        error = null
        val ultimo = mes.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1))
        workoutService.getSessions(mes.toString(), ultimo.toString())
            .onSuccess { sesiones = it; cargando = false }
            .onFailure { error = "No se pudo cargar el progreso."; cargando = false }
    }

    val porDia = remember(sesiones) { sesiones.groupBy { it.date } }

    Scaffold(
        containerColor = Palette.current.background,
        topBar = {
            TopAppBar(
                title = { Text("Progreso", color = Palette.current.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = Palette.current.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Palette.current.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            MonthHeader(
                mes = mes,
                onPrev = { mes = mes.minus(DatePeriod(months = 1)); diaElegido = null },
                onNext = { mes = mes.plus(DatePeriod(months = 1)); diaElegido = null }
            )

            Spacer(Modifier.height(12.dp))

            MonthSummary(sesiones)

            Spacer(Modifier.height(16.dp))

            when {
                cargando -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                    CircularProgressIndicator(color = Palette.current.accentText)
                }
                error != null -> Text(
                    error!!,
                    color = Palette.current.danger,
                    modifier = Modifier.padding(16.dp)
                )
                else -> MonthGrid(
                    mes = mes,
                    hoy = hoy,
                    diasEntrenados = porDia.keys,
                    diaElegido = diaElegido,
                    onDayClick = { d ->
                        // Un día sin entreno no se puede abrir: no hay nada que enseñar.
                        if (porDia.containsKey(d.toString())) {
                            diaElegido = if (diaElegido == d) null else d
                        }
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            val delDia = diaElegido?.let { porDia[it.toString()] }.orEmpty()
            if (diaElegido != null && delDia.isNotEmpty()) {
                delDia.forEach { SessionDetail(it) }
            } else if (!cargando && error == null) {
                Text(
                    if (sesiones.isEmpty()) "Ningún entrenamiento registrado este mes."
                    else "Toca un día marcado para ver qué hiciste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.current.textSecondary,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MonthHeader(mes: LocalDate, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.KeyboardArrowLeft, "Mes anterior", tint = Palette.current.textPrimary)
        }
        Text(
            "${nombreMes(mes.monthNumber)} ${mes.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Palette.current.textPrimary
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.KeyboardArrowRight, "Mes siguiente", tint = Palette.current.textPrimary)
        }
    }
}

@Composable
private fun MonthSummary(sesiones: List<WorkoutSession>) {
    val dias = sesiones.map { it.date }.distinct().size
    val series = sesiones.sumOf { it.totalSets }
    val volumen = sesiones.sumOf { it.volumeKg }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Palette.current.surfaceSunken,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            SummaryItem("$dias", if (dias == 1) "día" else "días")
            SummaryItem("$series", if (series == 1) "serie" else "series")
            // El volumen en toneladas se lee mejor que en kilos a partir de unos pocos entrenos.
            SummaryItem(
                if (volumen >= 1000) "${(volumen / 1000).toInt()}t" else "${volumen.toInt()}kg",
                "volumen"
            )
        }
    }
}

@Composable
private fun SummaryItem(valor: String, etiqueta: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            valor,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Palette.current.accentText
        )
        Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = Palette.current.textSecondary)
    }
}

@Composable
private fun MonthGrid(
    mes: LocalDate,
    hoy: LocalDate,
    diasEntrenados: Set<String>,
    diaElegido: LocalDate?,
    onDayClick: (LocalDate) -> Unit
) {
    val diasEnMes = mes.plus(DatePeriod(months = 1)).minus(DatePeriod(days = 1)).dayOfMonth
    // La semana empieza en lunes, como el resto de la app.
    val huecoInicial = mes.dayOfWeek.isoDayNumber - 1

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { d ->
                Text(
                    d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.current.textSecondary
                )
            }
        }

        var dia = 1
        while (dia <= diasEnMes) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0..6) {
                    val esHueco = (dia == 1 && col < huecoInicial)
                    if (esHueco || dia > diasEnMes) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val fecha = LocalDate(mes.year, mes.month, dia)
                        DayCell(
                            numero = dia,
                            entrenado = diasEntrenados.contains(fecha.toString()),
                            esHoy = fecha == hoy,
                            elegido = fecha == diaElegido,
                            modifier = Modifier.weight(1f),
                            onClick = { onDayClick(fecha) }
                        )
                        dia++
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    numero: Int,
    entrenado: Boolean,
    esHoy: Boolean,
    elegido: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(
                when {
                    elegido -> Palette.current.accentText
                    entrenado -> Palette.current.accentText.copy(alpha = 0.18f)
                    else -> Palette.current.surfaceSunken.copy(alpha = 0.4f)
                }
            )
            .clickable(enabled = entrenado, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$numero",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (esHoy || entrenado) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    elegido -> Palette.current.onHero
                    entrenado -> Palette.current.accentText
                    esHoy -> Palette.current.textPrimary
                    else -> Palette.current.textSecondary
                }
            )
            // Un punto bajo el número: el color de fondo solo ya es ambiguo en pantallas oscuras.
            if (entrenado) {
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (elegido) Palette.current.onHero else Palette.current.accentText)
                )
            }
        }
    }
}

@Composable
private fun SessionDetail(sesion: WorkoutSession) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = Palette.current.surfaceSunken,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        border = BorderStroke(1.dp, Palette.current.border.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                sesion.dayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Palette.current.textPrimary
            )
            Text(
                "${sesion.totalSets} series · ${sesion.totalReps} reps · ${sesion.volumeKg.toInt()} kg movidos",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.current.textSecondary
            )
            Spacer(Modifier.height(12.dp))

            sesion.exercises.forEach { ex ->
                Text(
                    ex.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.current.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                ex.sets.forEachIndexed { i, set ->
                    val total = sessionTotalKg(ex.kind, set.value, ex.barKg, set.perSideFor(ex.kind))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Serie ${i + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.current.textSecondary
                        )
                        Text(
                            buildString {
                                append("${trimNum(set.value)} × ${set.reps}")
                                // El total solo se enseña cuando difiere de lo apuntado (barra,
                                // mancuernas): en máquina serían el mismo número dos veces.
                                if (total != set.value) append("  (${trimNum(total)} kg)")
                                set.rir?.let { append("  RIR $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.current.textPrimary
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

private fun trimNum(v: Double): String =
    if (v == v.toInt().toDouble()) "${v.toInt()}" else "$v"

private fun nombreMes(n: Int): String = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
).getOrElse(n - 1) { "" }
