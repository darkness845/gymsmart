package com.gymsmart.gymsmart.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gymsmart.gymsmart.model.TrainingItem

@Composable
fun TrainingTable(data: List<TrainingItem>) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TableCell("Fecha", isHeader = true, modifier = Modifier.weight(1f))
            TableCell("Duración", isHeader = true, modifier = Modifier.weight(1f))
            TableCell("Estado", isHeader = true, modifier = Modifier.weight(1f))
        }
        data.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                TableCell(item.date, modifier = Modifier.weight(1f))
                TableCell("${item.duration} min", modifier = Modifier.weight(1f))
                TableCell(item.status, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TableCell(text: String, isHeader: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, Color(0xFF444444))
            .padding(8.dp)
    ) {
        Text(
            text,
            fontSize = if (isHeader) 13.sp else 12.sp,
            color = if (isHeader) Color.White else Color(0xFFCCCCCC),
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal
        )
    }
}