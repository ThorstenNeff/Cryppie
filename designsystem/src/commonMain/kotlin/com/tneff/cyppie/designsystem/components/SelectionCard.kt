package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
 * Selection / navigation card (HANDOFF §3 inline patterns; ONB-2 "Pfad wählen"). Leading icon badge,
 * title + description, optional [trailingIcon] (e.g. a chevron for navigation). Resting binds
 * `surface` + `outline`; [selected] binds `primary-surface` tint + `primary` outline. Exposed as a
 * [Role.Button] (these cards navigate / commit a choice on tap). Token-bound, direction-agnostic;
 * the whole row is one ≥48 dp touch target (§5.4).
 */
@Composable
fun SelectionCard(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    description: String? = null,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
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
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(background)
            .border(borderWidth, outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // Icon badge: primary-surface circle + primary icon (HANDOFF §3 Auswahl-Card).
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.primarySurface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(text = title, style = CryptasaTheme.typography.label, color = colors.onSurface)
            if (description != null) {
                Text(text = description, style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant)
            }
        }
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
