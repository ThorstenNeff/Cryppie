package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.foundation.clickableIcon
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

enum class CryptasaBannerTone { Danger, Warning, Offline, Info }

/**
 * Screen banner (HANDOFF §3 "Banner"). Leading icon · title (`labelSmall`) + description (`helper`)
 * · optional trailing text action. Surface and accent bind to the [tone]; text uses `on-surface`.
 * Always conveys state with **icon + text** (never colour alone, §5.4); announced as a live region.
 */
@Composable
fun CryptasaBanner(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    tone: CryptasaBannerTone = CryptasaBannerTone.Danger,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing

    val background: Color
    val accent: Color
    val icon: ImageVector
    when (tone) {
        CryptasaBannerTone.Danger -> {
            background = colors.dangerSurface; accent = colors.danger; icon = CryptasaIcons.Error
        }
        CryptasaBannerTone.Warning -> {
            background = colors.warningSurface; accent = colors.warning; icon = CryptasaIcons.Warning
        }
        CryptasaBannerTone.Offline -> {
            background = colors.surfaceVariant; accent = colors.primary; icon = CryptasaIcons.CloudOff
        }
        CryptasaBannerTone.Info -> {
            // Neutral hint (KAN-158): brand-blue tonal container, NOT a solid fill (≠ the primary CTA).
            background = colors.infoSurface; accent = colors.info; icon = CryptasaIcons.Info
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CryptasaTheme.radius.md))
            .background(background)
            .padding(spacing.md)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(text = title, style = CryptasaTheme.typography.labelSmall, color = colors.onSurface)
            if (description != null) {
                Text(text = description, style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant)
            }
        }

        if (actionText != null && onActionClick != null) {
            // ≥48 dp hit area around the action text (§5.4 touch target).
            Box(
                modifier = Modifier
                    .padding(start = spacing.xs)
                    .heightIn(min = 48.dp)
                    .clickableIcon(contentDescription = actionText, onClick = onActionClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionText,
                    style = CryptasaTheme.typography.labelSmall,
                    color = accent,
                )
            }
        }
    }
}
