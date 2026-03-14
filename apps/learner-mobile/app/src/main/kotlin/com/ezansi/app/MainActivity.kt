package com.ezansi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ezansi.app.ui.theme.EzansiTheme

/**
 * Single-activity entry point for eZansiEdgeAI.
 * All feature screens are hosted via Compose Navigation within this activity.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EzansiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // TODO: Replace with NavHost wiring feature screens
                    EzansiPlaceholder(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun EzansiPlaceholder(modifier: Modifier = Modifier) {
    Text(
        text = "eZansiEdgeAI — Ready to learn!",
        modifier = modifier
    )
}
