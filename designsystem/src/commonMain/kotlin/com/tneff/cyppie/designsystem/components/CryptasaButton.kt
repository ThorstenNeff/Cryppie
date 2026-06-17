package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

enum class CryptasaButtonStyle { Primary, Secondary }

/**
 * Primary/Secondary action button (HANDOFF §3 "Button"). Height 52, radius `md`, full-width,
 * label `Semi Bold 16`. States: default / pressed (`primary-hover` resp. `primary-surface`) /
 * disabled (`surface-variant` + `on-surface-variant`). Colours/typography/radius are token-bound;
 * the fixed 52 dp height is the component spec and already satisfies the ≥48 dp touch target (§5.4).
 */
@Composable
fun CryptasaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CryptasaButtonStyle = CryptasaButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val colors = CryptasaTheme.colors
    val radius = CryptasaTheme.radius
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val container: Color
    val content: Color
    var border: BorderStroke? = null

    when {
        !enabled -> {
            container = colors.surfaceVariant
            content = colors.onSurfaceVariant
        }
        style == CryptasaButtonStyle.Primary -> {
            container = if (pressed) colors.primaryHover else colors.primary
            content = colors.onPrimary
        }
        else -> { // Secondary
            container = if (pressed) colors.primarySurface else colors.surface
            content = colors.primary
            border = BorderStroke(1.dp, colors.primary)
        }
    }

    val shape = RoundedCornerShape(radius.md)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .background(container)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = CryptasaTheme.spacing.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = CryptasaTheme.typography.label,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}
