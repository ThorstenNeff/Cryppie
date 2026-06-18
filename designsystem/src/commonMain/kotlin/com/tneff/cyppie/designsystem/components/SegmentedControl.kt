package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * Segmented control (HANDOFF §3 inline patterns; used by the seed length 12/24 toggle). Selected
 * segment binds `surface` + `on-surface`; track binds `surface-variant`. Each segment is a
 * [Role.Tab]. Token-bound; segments fill the width equally so it works in LTR and RTL.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: (T) -> String,
    enabled: Boolean = true,
    optionTestTag: ((T) -> String?)? = null,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val outerShape = RoundedCornerShape(CryptasaTheme.radius.md)
    val innerShape = RoundedCornerShape(CryptasaTheme.radius.sm)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(colors.surfaceVariant)
            .padding(spacing.xxs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val tag = optionTestTag?.invoke(option)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .padding(horizontal = spacing.xxs)
                    .then(if (tag != null) Modifier.testTag(tag) else Modifier)
                    .clip(innerShape)
                    .background(if (isSelected) colors.surface else colors.surfaceVariant)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { onSelect(option) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = CryptasaTheme.typography.labelSmall,
                    color = if (isSelected) colors.onSurface else colors.onSurfaceVariant,
                )
            }
        }
    }
}
