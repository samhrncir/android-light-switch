package com.samhrncir.lightswitch

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.samhrncir.lightswitch.ui.LightSwitchApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LightSwitchApp()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // uiMode is declared in the manifest, so the activity survives a theme flip.
        // Re-run edge-to-edge so the status/navigation bar icons pick the right contrast.
        enableEdgeToEdge()
    }
}
