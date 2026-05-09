package com.gymsmart.gymsmart.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable
fun MetricBox(label: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 11.sp, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
    }
}

fun formatDouble(value: Double, decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = round(value * factor) / factor
    val intPart = rounded.toLong()
    return if (decimals == 0) "$intPart"
    else {
        val decPart = ((rounded - intPart) * factor).toLong()
            .let { abs(it).toString().padStart(decimals, '0') }
        "$intPart.$decPart"
    }
}

fun formatFloat(value: Float, decimals: Int): String =
    formatDouble(value.toDouble(), decimals)

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m ${s}s"
}