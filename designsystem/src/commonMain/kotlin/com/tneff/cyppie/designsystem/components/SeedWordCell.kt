package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * Single numbered seed word cell (HANDOFF §3 inline patterns; used by the seed grid on ONB-5/6).
 *
 * Shows the BIP-39 word index and the word itself. [error] turns the outline + index `danger`
 * without revealing what the expected word is (§5.3 — error messages must not leak secrets).
 * The word is a forced-LTR bidi island ([LocaleList] is UI-agnostic) so it renders correctly even
 * in RTL locales (SPEC_I18N_RTL: addresses/seed words stay LTR).
 */
@Composable
fun SeedWordCell(
    index: Int,
    word: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val shape = RoundedCornerShape(CryptasaTheme.radius.sm)
    val outline = if (error) colors.danger else colors.outline

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(colors.surfaceVariant)
            .border(1.dp, outline, shape)
            .padding(horizontal = spacing.sm, vertical = spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = "$index",
            style = CryptasaTheme.typography.helper,
            color = if (error) colors.danger else colors.onSurfaceVariant,
        )
        Text(
            text = word,
            style = CryptasaTheme.typography.bodySmall,
            color = colors.onSurface,
        )
    }
}
