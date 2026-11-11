package com.example.hyundaipleos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.decorView.systemUiVisibility = (4 or 1024 or 4096)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF126BFF),
                onPrimary = Color.White,
                secondary = Color(0xFF0B8F87),
                tertiary = Color(0xFF5C6BC0),
                error = Color(0xFFB3261E)
            )) {
                Surface(color = Color(0xFFF5F6F7)) { CarCheckApp() }
            }
        }
    }
}
