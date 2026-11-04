package com.sanjith.betterlife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sanjith.betterlife.ui.theme.BetterLifeTheme

/** The single Activity. Every screen is a Composable inside it. */
public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge is enforced from Android 15; screens handle insets through Scaffold.
        enableEdgeToEdge()
        setContent {
            BetterLifeTheme {
                AppNavHost()
            }
        }
    }
}
