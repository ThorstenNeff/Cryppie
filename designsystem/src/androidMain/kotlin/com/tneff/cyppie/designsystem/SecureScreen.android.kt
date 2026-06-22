package com.tneff.cyppie.designsystem

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

// Reference-counted so that on a secure→secure navigation (e.g. Form→Confirm→Status) the new screen's
// addFlags and the old screen's clearFlags don't net-clear FLAG_SECURE during the transition (Compose is
// single-threaded; a plain Int is safe). One count for the whole app — all modules share this seam (KAN-168).
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
