package com.tneff.cyppie.feature.onboarding

import androidx.compose.runtime.Composable

// No-op: Desktop/Web have no FLAG_SECURE equivalent (Web never shows secrets); iOS app-switcher
// overlay is a follow-up. See the expect doc.
@Composable
actual fun SecureScreenEffect() {
}
