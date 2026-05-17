package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.gymsmart.gymsmart.model.BodyAnalysisResponse
import com.gymsmart.gymsmart.model.ProfileRequest
import com.gymsmart.gymsmart.model.WeightEntry
import com.gymsmart.gymsmart.model.ProfileResponse
import com.gymsmart.gymsmart.navigation.Screen
import com.gymsmart.gymsmart.services.AuthService
import com.gymsmart.gymsmart.services.ProfileService
import com.gymsmart.gymsmart.services.SubscriptionService
import com.gymsmart.gymsmart.services.WeightService
import com.gymsmart.gymsmart.services.byteArrayToImageBitmap
import com.gymsmart.gymsmart.services.rememberImagePicker
import com.gymsmart.gymsmart.ui.theme.GymSmartColors
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(
    navController: NavHostController
) {
    val scope = rememberCoroutineScope()

    val client = remember {
        HttpClient {
            install(ContentNegotiation) { json() }
        }
    }

    val authService = remember { AuthService() }
    val profileService = remember { ProfileService(authService.client) }
    val weightService = remember { WeightService(authService.client) }

    var isLoading by remember { mutableStateOf(true) }
    var currentProfile by remember { mutableStateOf<ProfileResponse?>(null) }
    val service = remember { WeightService(client) }
    var entries by remember { mutableStateOf(listOf<WeightEntry>()) }
    var showDialog by remember { mutableStateOf(false) }
    var inputWeight by remember { mutableStateOf("") }
    var previewBytes by remember { mutableStateOf<ByteArray?>(null) }
    var analysis by remember { mutableStateOf<BodyAnalysisResponse?>(null) }
    var selectedImageBase64 by remember { mutableStateOf<String?>(null) }
    var isLoadingAnalysis by remember { mutableStateOf(false) }
    var isPremium by remember { mutableStateOf(false) }
    val subService = remember { SubscriptionService(authService.client) }

    val today = remember {
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date.toString()
    }

    val launchImagePicker = rememberImagePicker { base64, bytes ->
        selectedImageBase64 = base64
        previewBytes = bytes
    }

    LaunchedEffect(Unit) {
        try {
            entries = weightService.getHistory()
            if (entries.isEmpty()) {
                profileService.getProfileResponse().onSuccess { response ->
                    currentProfile = response
                    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                    entries = listOf(WeightEntry(day = today, weightKg = response.profile.weightKg.toFloat()))
                }
            } else {
                profileService.getProfileResponse().onSuccess { currentProfile = it }
                subService.getStatus().onSuccess { isPremium = it.active }
            }
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            "Evolución de peso",
                            fontWeight = FontWeight.Bold,
                            color = GymSmartColors.TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Volver",
                                tint = GymSmartColors.TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = GymSmartColors.Background
                    )
                )
            }
        },
        containerColor = GymSmartColors.Background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(GymSmartColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {

            Spacer(Modifier.height(8.dp))

            // IA CARD
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = GymSmartColors.SurfaceCard,
                tonalElevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = GymSmartColors.PremiumGold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Analizar físico con IA",
                            fontWeight = FontWeight.Bold,
                            color = GymSmartColors.TextPrimary
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (!isPremium) {
                                navController.navigate(Screen.Subscription.route)
                                return@Button
                            }
                            launchImagePicker()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GymSmartColors.Primary,
                            contentColor = GymSmartColors.OnPrimary,
                            disabledContainerColor = GymSmartColors.Outline,
                            disabledContentColor = GymSmartColors.TextDisabled
                        )
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = GymSmartColors.OnPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isPremium) "Subir foto" else "🔒 Solo Premium")
                    }

                    previewBytes?.let { bytes ->
                        Spacer(Modifier.height(16.dp))
                        Image(
                            bitmap = byteArrayToImageBitmap(bytes),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(300.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val image = selectedImageBase64 ?: return@Button
                                scope.launch {
                                    try {
                                        isLoadingAnalysis = true
                                        analysis = service.analyzeBody(image)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    } finally {
                                        isLoadingAnalysis = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GymSmartColors.Primary,
                                contentColor = GymSmartColors.OnPrimary,
                                disabledContainerColor = GymSmartColors.Outline,
                                disabledContentColor = GymSmartColors.TextDisabled
                            )
                        ) {
                            Text("Analizar con IA")
                        }
                    }

                    if (isLoadingAnalysis) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(color = GymSmartColors.Primary)
                    }

                    analysis?.let { result ->
                        Spacer(Modifier.height(20.dp))
                        AnalysisCard(title = "Grasa corporal", value = result.bodyFat)
                        AnalysisCard(title = "Peso estimado", value = result.estimatedWeight)
                        AnalysisCard(title = "Masa muscular", value = result.muscleMass)
                        AnalysisCard(title = "Tipo físico", value = result.physiqueType)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = GymSmartColors.Primary.copy(alpha = 0.12f),
                            tonalElevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Resumen IA", fontWeight = FontWeight.Bold, color = GymSmartColors.TextPrimary)
                                Spacer(Modifier.height(6.dp))
                                Text(result.summary, color = GymSmartColors.TextSecondary)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // CARD PESO
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = GymSmartColors.SurfaceCard,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Hoy: $today", fontWeight = FontWeight.Bold, color = GymSmartColors.TextPrimary)
                    Button(
                        onClick = { showDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GymSmartColors.Primary,
                            contentColor = GymSmartColors.OnPrimary,
                            disabledContainerColor = GymSmartColors.Outline,
                            disabledContentColor = GymSmartColors.TextDisabled
                        )
                    ) {
                        Text("Añadir peso")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // GRÁFICA
            if (entries.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    shape = MaterialTheme.shapes.large,
                    color = GymSmartColors.SurfaceCard,
                    tonalElevation = 0.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Progreso", fontWeight = FontWeight.Bold, color = GymSmartColors.TextPrimary)
                        Spacer(Modifier.height(8.dp))

                        val sorted = remember(entries) { entries.sortedBy { it.day } }
                        val minW = sorted.minOf { it.weightKg }
                        val maxW = sorted.maxOf { it.weightKg }
                        val range = max(1f, maxW - minW)

                        val paddingLeftDp = 40.dp
                        val paddingBotDp = 28.dp
                        val paddingTopDp = 12.dp
                        val paddingRightDp = 8.dp

                        BoxWithConstraints(
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        ) {
                            val totalW = constraints.maxWidth.toFloat()
                            val totalH = constraints.maxHeight.toFloat()

                            val pLeft = with(LocalDensity.current) { paddingLeftDp.toPx() }
                            val pBot = with(LocalDensity.current) { paddingBotDp.toPx() }
                            val pTop = with(LocalDensity.current) { paddingTopDp.toPx() }
                            val pRight = with(LocalDensity.current) { paddingRightDp.toPx() }

                            val chartW = totalW - pLeft - pRight
                            val chartH = totalH - pBot - pTop
                            val stepX = if (sorted.size > 1) chartW / (sorted.size - 1) else chartW

                            fun xPos(index: Int) = pLeft + index * stepX
                            fun yPos(v: Float) = pTop + chartH - ((v - minW) / range) * chartH

                            Canvas(modifier = Modifier.fillMaxSize()) {
                                for (i in 0..3) {
                                    val fraction = i / 3f
                                    val yG = pTop + chartH * (1f - fraction)
                                    drawLine(
                                        color = GymSmartColors.Divider,
                                        start = Offset(pLeft, yG),
                                        end = Offset(pLeft + chartW, yG),
                                        strokeWidth = 1.5f
                                    )
                                }

                                val areaPath = Path()
                                sorted.forEachIndexed { i, e ->
                                    val x = xPos(i); val y = yPos(e.weightKg)
                                    if (i == 0) areaPath.moveTo(x, y)
                                    else {
                                        val px = xPos(i - 1); val py = yPos(sorted[i - 1].weightKg)
                                        val cx = (px + x) / 2
                                        areaPath.cubicTo(cx, py, cx, y, x, y)
                                    }
                                }
                                areaPath.lineTo(xPos(sorted.size - 1), pTop + chartH)
                                areaPath.lineTo(pLeft, pTop + chartH)
                                areaPath.close()
                                drawPath(areaPath, color = GymSmartColors.Primary.copy(alpha = 0.15f))

                                val linePath = Path()
                                sorted.forEachIndexed { i, e ->
                                    val x = xPos(i); val y = yPos(e.weightKg)
                                    if (i == 0) linePath.moveTo(x, y)
                                    else {
                                        val px = xPos(i - 1); val py = yPos(sorted[i - 1].weightKg)
                                        val cx = (px + x) / 2
                                        linePath.cubicTo(cx, py, cx, y, x, y)
                                    }
                                }
                                drawPath(linePath, color = GymSmartColors.Primary, style = Stroke(width = 5f))

                                sorted.forEachIndexed { i, e ->
                                    val x = xPos(i); val y = yPos(e.weightKg)
                                    drawCircle(GymSmartColors.SurfaceCard, radius = 8f, center = Offset(x, y))
                                    drawCircle(GymSmartColors.Primary, radius = 8f, center = Offset(x, y), style = Stroke(width = 3f))
                                }
                            }

                            val density = LocalDensity.current
                            for (i in 0..3) {
                                val fraction = i / 3f
                                val weightVal = minW + fraction * range
                                val yPx = pTop + chartH * (1f - fraction)
                                val yDp = with(density) { yPx.toDp() }
                                val rounded = (weightVal * 10).toInt() / 10f
                                Text(
                                    text = "$rounded",
                                    fontSize = 9.sp,
                                    color = GymSmartColors.TextSecondary,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = 0.dp, y = yDp - 7.dp)
                                        .width(paddingLeftDp - 4.dp),
                                    textAlign = TextAlign.End
                                )
                            }

                            sorted.forEachIndexed { i, e ->
                                val xPx = xPos(i)
                                val xDp = with(density) { xPx.toDp() }
                                val yDp = with(density) { (pTop + chartH + 4f).toDp() }
                                val label = e.day.substring(5).replace("-", "/")
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    color = GymSmartColors.TextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = xDp - 16.dp, y = yDp)
                                        .width(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // DIALOG
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = GymSmartColors.SurfaceElevated,
            titleContentColor = GymSmartColors.TextPrimary,
            textContentColor = GymSmartColors.TextSecondary,
            title = { Text("Añadir peso — $today") },
            text = {
                OutlinedTextField(
                    value = inputWeight,
                    onValueChange = { inputWeight = it },
                    label = { Text("Peso (kg)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GymSmartColors.Primary,
                        unfocusedBorderColor = GymSmartColors.Outline,
                        focusedLabelColor = GymSmartColors.Primary,
                        unfocusedLabelColor = GymSmartColors.TextSecondary,
                        cursorColor = GymSmartColors.Primary,
                        focusedTextColor = GymSmartColors.TextPrimary,
                        unfocusedTextColor = GymSmartColors.TextPrimary,
                        focusedContainerColor = GymSmartColors.SurfaceElevated,
                        unfocusedContainerColor = GymSmartColors.SurfaceElevated
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val weightValue = inputWeight.toFloatOrNull() ?: return@Button
                        scope.launch {
                            try {
                                val profile = currentProfile?.profile ?: return@launch
                                val request = ProfileRequest(
                                    weightKg = weightValue.toDouble(),
                                    heightCm = profile.heightCm,
                                    age = profile.age,
                                    sex = profile.sex,
                                    activityLevel = profile.activityLevel,
                                    goal = profile.goal,
                                    goalRate = profile.goalRate,
                                    hasWearable = profile.activityLevel == "wearable"
                                )
                                profileService.saveProfile(request).onSuccess {
                                    entries = weightService.getHistory()
                                }
                                inputWeight = ""
                                showDialog = false
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GymSmartColors.Primary,
                        contentColor = GymSmartColors.OnPrimary,
                        disabledContainerColor = GymSmartColors.Outline,
                        disabledContentColor = GymSmartColors.TextDisabled
                    )
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancelar", color = GymSmartColors.TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun AnalysisCard(title: String, value: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape = MaterialTheme.shapes.medium,
        color = GymSmartColors.SurfaceElevated,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, fontWeight = FontWeight.Bold, color = GymSmartColors.TextPrimary)
            Text(value, color = GymSmartColors.Primary, fontWeight = FontWeight.Bold)
        }
    }
}