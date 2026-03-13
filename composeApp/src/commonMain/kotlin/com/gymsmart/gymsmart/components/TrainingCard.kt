package com.gymsmart.gymsmart.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrainingCard(title: String, value: String?, unit: String, color: Color) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        modifier = Modifier.widthIn(min = 150.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Text(title, fontSize = 12.sp, color = Color(0xFFAAAAAA))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value ?: "--",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Spacer(Modifier.width(4.dp))
                Text(unit, fontSize = 14.sp, color = Color.White)
            }
        }
    }
}