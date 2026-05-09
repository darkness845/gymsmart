package com.gymsmart.gymsmart

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gymsmart.gymsmart.model.RoutePoint

@Composable
expect fun MapComponent(
    points: List<RoutePoint>,
    completedPoints: List<RoutePoint> = emptyList(),
    userLocation: RoutePoint? = null,
    modifier: Modifier = Modifier
)