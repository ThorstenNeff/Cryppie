package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * Checkbox with label (HANDOFF §3 inline patterns; used by the "sicher notiert" confirmation on the
 * seed screen). Whole row is one `Role.Checkbox` toggle target (≥48 dp, §5.4). Token-bound.
 */
@Composable
fun CryptasaCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val boxShape = RoundedCornerShape(CryptasaTheme.radius.sm)

    Row(
        modifier = modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox) { onCheckedChange(it) }
            .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(boxShape)
                .background(if (checked) colors.primary else colors.surface)
                .border(
                    width = if (checked) 0.dp else 1.dp,
                    color = if (checked) colors.primary else colors.outlineStrong,
                    shape = boxShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(text = label, style = CryptasaTheme.typography.bodySmall, color = colors.onSurface)
    }
}
