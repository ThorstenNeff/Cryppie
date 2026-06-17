package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * Selectable option card (HANDOFF §3 inline patterns; used by ONB-2 "Pfad wählen"). Resting binds
 * `surface` + `outline`; selected binds `primary-surface` tint + `primary` outline. Exposed as a
 * radio-style [Role.RadioButton] for screen readers. Token-bound, direction-agnostic.
 */
@Composable
fun SelectionCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val shape = RoundedCornerShape(CryptasaTheme.radius.lg)

    val background = if (selected) colors.primarySurface else colors.surface
    val outline = if (selected) colors.primary else colors.outline
    val borderWidth = if (selected) 2.dp else 1.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(borderWidth, outline, shape)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) colors.primary else colors.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(text = title, style = CryptasaTheme.typography.label, color = colors.onSurface)
            if (description != null) {
                Text(text = description, style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant)
            }
        }
    }
}
