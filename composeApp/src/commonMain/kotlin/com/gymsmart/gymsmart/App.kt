package com.gymsmart.gymsmart

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.gymsmart.gymsmart.navigation.NavGraph

@Composable
fun App() {
    MaterialTheme {
        NavGraph()
    }
}