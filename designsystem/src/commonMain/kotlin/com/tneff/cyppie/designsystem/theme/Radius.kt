package com.tneff.cyppie.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radius scale (`Cryptasa/Radius`, HANDOFF §2.2). Use these instead of raw `dp` values.
 */
@Immutable
data class Radius(
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val full: Dp = 999.dp,
)

val CryptasaRadius: Radius = Radius()
