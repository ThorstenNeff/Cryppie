package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.foundation.clickableIcon
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * Blocking dialog (HANDOFF §3 "Dialog" + §5.2.1). Renders a full-size scrim (black 55 %) with a
 * centred card (`surface-raised`, max-width 480 per ADR-0012): icon badge 48 · title (`titleSmall`)
 * · body (`body`, centred) · full-width primary button · optional secondary text action.
 *
 * Caller renders this on top of the screen (e.g. as the last child of a Box) while it should block.
 */
@Composable
fun CryptasaDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    icon: ImageVector = CryptasaIcons.Error,
    badgeSurface: Color? = null,
    badgeTint: Color? = null,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val scrim = Color.Black.copy(alpha = 0.55f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(scrim),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(CryptasaTheme.radius.xl),
            color = colors.surfaceRaised,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(spacing.xl)
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(badgeSurface ?: colors.dangerSurface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeTint ?: colors.danger,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Text(
                    text = title,
                    style = CryptasaTheme.typography.titleSmall,
                    color = colors.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = body,
                    style = CryptasaTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                CryptasaButton(
                    text = confirmText,
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (dismissText != null && onDismiss != null) {
                    Text(
                        text = dismissText,
                        style = CryptasaTheme.typography.label,
                        color = colors.primary,
                        modifier = Modifier
                            .padding(top = spacing.xxs)
                            .clickableIcon(contentDescription = dismissText, onClick = onDismiss),
                    )
                }
            }
        }
    }
}
