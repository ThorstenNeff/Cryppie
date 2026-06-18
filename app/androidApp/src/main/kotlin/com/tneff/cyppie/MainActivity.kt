package com.tneff.cyppie

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import com.tneff.cyppie.storage.AndroidStoragePaths

// FragmentActivity (not bare ComponentActivity) so ONB-9's BiometricPrompt has a valid host (KAN-33).
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // KAN-89/95: give :storage the app-private dir once so it can resolve the default seed file
        // (used by the launch check + onboarding persistence) without holding a Context.
        AndroidStoragePaths.init(applicationContext.filesDir.path)
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