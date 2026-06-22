package com.tneff.cyppie.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.BidiSanitizer
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/** One selectable token in a [TokenPicker] — a curated-allowlist entry (KAN-168 F5 / KAN-170 S3). */
data class TokenPickerItem(val address: String, val symbol: String, val name: String)

/**
 * A bottom-sheet that lists a **curated token allowlist** with a search box (KAN-168 F5 / KAN-170 S3). Shared
 * by Copy (receive token) and Strat (basket tokens): a free `0x…` field is replaced by selecting an allowlist
 * token, so a hex-typo / fake-token can't be picked. All copy is passed in ([title]/[searchLabel]/[emptyText])
 * so each caller keeps its own i18n keys + test tags; a letter-avatar stands in for the token icon (real icons
 * = follow). [onDismiss] fires on a scrim tap; [onSelect] on a row tap.
 */
@Composable
fun TokenPicker(
    tokens: List<TokenPickerItem>,
    title: String,
    searchLabel: String,
    emptyText: String,
    onSelect: (TokenPickerItem) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    titleTestTag: String? = null,
    searchTestTag: String? = null,
    emptyTestTag: String? = null,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    var query by remember { mutableStateOf("") }
    val matches = tokens.filter {
        query.isBlank() || it.symbol.contains(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
    }

    Box(
        modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = CryptasaTheme.radius.xl, topEnd = CryptasaTheme.radius.xl),
            color = colors.surfaceRaised,
            contentColor = colors.onSurface,
            // Consume taps on the sheet so they don't dismiss via the scrim.
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).clickable(enabled = false) {},
        ) {
            Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    title, style = CryptasaTheme.typography.titleSmall, color = colors.onSurface,
                    modifier = if (titleTestTag != null) Modifier.testTag(titleTestTag) else Modifier,
                )
                CryptasaTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = searchLabel,
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.fillMaxWidth().let { if (searchTestTag != null) it.testTag(searchTestTag) else it },
                )
                if (matches.isEmpty()) {
                    Text(
                        emptyText, style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant,
                        modifier = if (emptyTestTag != null) Modifier.testTag(emptyTestTag) else Modifier,
                    )
                } else LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(matches, key = { it.address }) { token -> TokenPickerRow(token) { onSelect(token) } }
                }
            }
        }
    }
}

@Composable
private fun TokenPickerRow(token: TokenPickerItem, onClick: () -> Unit) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).clickable(onClick = onClick).padding(spacing.md),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Letter-avatar placeholder (real token icons = follow): first char of the symbol in a tinted circle.
        Box(Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(token.symbol.take(1).uppercase(), style = CryptasaTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(BidiSanitizer.sanitize(token.symbol), style = CryptasaTheme.typography.body, color = colors.onSurface)
            Text(BidiSanitizer.sanitize(token.name), style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant)
        }
    }
}
