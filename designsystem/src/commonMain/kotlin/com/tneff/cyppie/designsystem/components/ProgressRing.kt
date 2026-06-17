package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * Progress ring (HANDOFF §3 inline patterns; used by ONB-8 "Wallet wird eingerichtet"). Pass
 * [progress] for a determinate ring (0f..1f) or leave it null for the indeterminate spinner. Track
 * and indicator bind to `surface-variant` / `primary`. Token-bound; the diameter is a component spec.
 */
@Composable
fun ProgressRing(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    diameter: androidx.compose.ui.unit.Dp = 48.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 4.dp,
) {
    val colors = CryptasaTheme.colors
    if (progress == null) {
        CircularProgressIndicator(
            modifier = modifier.size(diameter),
            color = colors.primary,
            trackColor = colors.surfaceVariant,
            strokeWidth = strokeWidth,
        )
    } else {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier.size(diameter),
            color = colors.primary,
            trackColor = colors.surfaceVariant,
            strokeWidth = strokeWidth,
        )
    }
}
