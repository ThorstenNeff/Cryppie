package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/** Password strength level. The [score] (filled segments) and accent are derived per level. */
enum class PasswordStrength(val score: Int) { None(0), Weak(1), Medium(2), Strong(4) }

/**
 * Password strength indicator (HANDOFF §3 inline patterns; used by ONB-3). Three segments fill by
 * [strength]; accent binds `danger`/`warning`/`success`. State is conveyed by **bar + text label**,
 * never colour alone (§5.4). [label] text comes from string resources at the call site (no raw text).
 */
@Composable
fun PasswordStrengthIndicator(
    strength: PasswordStrength,
    label: String,
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val segments = 4 // KAN-85/A5: 4-segment bar (Weak 1 · Medium 2 · Strong 4) per SPEC_ONBOARDING_SCREEN3

    val accent: Color = when (strength) {
        PasswordStrength.None -> colors.outline
        PasswordStrength.Weak -> colors.danger
        PasswordStrength.Medium -> colors.warning
        PasswordStrength.Strong -> colors.success
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.fillMaxWidth()) {
            repeat(segments) { i ->
                val filled = i < strength.score
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(CryptasaTheme.radius.full))
                        .background(if (filled) accent else colors.surfaceVariant),
                )
            }
        }
        Text(
            text = label,
            style = CryptasaTheme.typography.helper,
            color = if (strength == PasswordStrength.None) colors.onSurfaceVariant else accent,
        )
    }
}
