package com.tneff.cyppie.designsystem.foundation

import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * Clickable icon helper: marks the node as a [Role.Button] for screen readers. Callers are
 * responsible for the ≥48 dp touch target (§5.4) via surrounding size/padding.
 */
fun Modifier.clickableIcon(
    contentDescription: String? = null,
    onClick: () -> Unit,
): Modifier = this.clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
