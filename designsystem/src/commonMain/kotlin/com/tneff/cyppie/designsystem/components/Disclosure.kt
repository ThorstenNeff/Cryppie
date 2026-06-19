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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * A no-blind disclosure row: `label` → `value` (Send / WalletConnect / DCA-grant). Numeric/address values
 * pass `ltr = true` to stay an LTR island even in RTL locales. Lifted to `:designsystem` (KAN-138) so the
 * Send/WC/DCA flows share one auditable disclosure primitive.
 */
@Composable
fun DisclosureRow(label: String, value: String, ltr: Boolean = false, valueTestTag: String? = null) {
    val colors = CryptasaTheme.colors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = CryptasaTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        val valueModifier = if (valueTestTag != null) Modifier.testTag(valueTestTag) else Modifier
        val text: @Composable () -> Unit = {
            Text(value, style = CryptasaTheme.typography.body, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = valueModifier)
        }
        if (ltr) LtrIsland { text() } else text()
    }
}

/** Wraps content in a forced-LTR scope (addresses/amounts/hashes stay LTR even in RTL locales). */
@Composable
fun LtrIsland(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { content() }
}
