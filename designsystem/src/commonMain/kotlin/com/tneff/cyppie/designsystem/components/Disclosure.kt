package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * A no-blind disclosure row: `label` → `value` (Send / WalletConnect / DCA-grant). Numeric/address values
 * pass `ltr = true` to stay an LTR island even in RTL locales. Lifted to `:designsystem` (KAN-138) so the
 * Send/WC/DCA flows share one auditable disclosure primitive.
 *
 * `truncate` (default true) single-lines + ellipsizes the value (fine for short amounts/counts). For a
 * **security-critical address/hash** pass `truncate = false`: a truncated address ("0x68b3…Fc45") hides a
 * malicious middle/suffix the user must be able to verify, so the full value wraps onto multiple lines.
 */
@Composable
fun DisclosureRow(
    label: String,
    value: String,
    ltr: Boolean = false,
    valueTestTag: String? = null,
    truncate: Boolean = true,
) {
    val colors = CryptasaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CryptasaTheme.spacing.md),
        verticalAlignment = if (truncate) Alignment.CenterVertically else Alignment.Top,
    ) {
        Text(label, style = CryptasaTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        // The value takes the remaining width + right-aligns; a full address wraps instead of shoving the label.
        val valueModifier = Modifier.weight(1f).let { if (valueTestTag != null) it.testTag(valueTestTag) else it }
        val text: @Composable () -> Unit = {
            Text(
                value,
                style = CryptasaTheme.typography.body,
                color = colors.onSurface,
                textAlign = TextAlign.End,
                maxLines = if (truncate) 1 else Int.MAX_VALUE,
                overflow = if (truncate) TextOverflow.Ellipsis else TextOverflow.Clip,
                modifier = valueModifier,
            )
        }
        if (ltr) LtrIsland { text() } else text()
    }
}

/** Wraps content in a forced-LTR scope (addresses/amounts/hashes stay LTR even in RTL locales). */
@Composable
fun LtrIsland(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { content() }
}
