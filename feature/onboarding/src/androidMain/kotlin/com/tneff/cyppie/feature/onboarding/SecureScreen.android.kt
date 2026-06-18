package com.tneff.cyppie.feature.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

// Reference count of currently-composed secure screens (Compose runs on the main thread, so a plain
// Int is safe). KAN-84: on a secure→secure navigation the new screen's addFlags and the old screen's
// onDispose/clearFlags race; counting keeps FLAG_SECURE set as long as ≥1 secure screen is composed,
// so the flag is never net-cleared during the transition (regardless of enter/dispose order).
private var secureScreenCount = 0

@Composable
actual fun SecureScreenEffect() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        secureScreenCount++
        if (secureScreenCount == 1) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            secureScreenCount--
            if (secureScreenCount == 0) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx: Context = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
