package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.foundation.clickableIcon
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * Onboarding top app bar (HANDOFF §3 inline patterns; Android chrome / back affordance). Optional
 * back button (auto-mirrored arrow for RTL, ≥48 dp touch target, §5.4) and optional centred title.
 * Token-bound; `contentDescription` for the back button comes from string resources at the call site.
 */
@Composable
fun CryptasaTopAppBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String? = null,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing

    Box(
        modifier = modifier
            .fillMaxWidth()
            // KAN-85/A1: keep the back button/title below the status bar (app is edge-to-edge).
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant) // KAN-85/A5: visible back-circle affordance
                    .clickableIcon(contentDescription = backContentDescription, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CryptasaIcons.ArrowBack,
                    contentDescription = backContentDescription,
                    tint = colors.onSurface,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (title != null) {
            Text(
                text = title,
                style = CryptasaTheme.typography.titleSmall,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 56.dp),
            )
        }
    }
}
