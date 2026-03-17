package com.ezansi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ezansi.app.navigation.EzansiBottomBar
import com.ezansi.app.navigation.EzansiNavHost
import com.ezansi.app.ui.theme.EzansiTheme

/**
 * Single-activity entry point for eZansiEdgeAI.
 *
 * All feature screens are hosted via Compose Navigation within this activity.
 * The bottom bar provides access to the 3 primary destinations: Chat, Topics, Profiles.
 * Secondary screens (Preferences, Library) are navigated to from within their parents.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as EzansiApplication).container

        enableEdgeToEdge()
        setContent {
            EzansiTheme {
                val navController = rememberNavController()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        EzansiBottomBar(navController = navController)
                    },
                ) { innerPadding ->
                    EzansiNavHost(
                        navController = navController,
                        container = container,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
