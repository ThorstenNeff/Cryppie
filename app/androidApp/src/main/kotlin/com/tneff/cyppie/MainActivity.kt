package com.tneff.cyppie

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity

// FragmentActivity (not bare ComponentActivity) so ONB-9's BiometricPrompt has a valid host (KAN-33).
// :storage paths are initialized in CyppieApplication.onCreate (KAN-89/95 L1).
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App()
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}