package com.tneff.cyppie.feature.market

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.market.generated.resources.Res
import com.tneff.cyppie.feature.market.generated.resources.mkt_last_updated
import com.tneff.cyppie.feature.market.generated.resources.mkt_live
import com.tneff.cyppie.feature.market.generated.resources.mkt_stale
import kotlin.math.abs
import kotlin.math.roundToLong
import org.jetbrains.compose.resources.stringResource

/** Compact number label (render-only): 1_234_567 → "1.23M". Western digits; large market values stay readable. */
fun formatCompact(value: Double): String {
    val absValue = abs(value)
    val (divisor, suffix) = when {
        absValue >= 1e12 -> 1e12 to "T"
        absValue >= 1e9 -> 1e9 to "B"
        absValue >= 1e6 -> 1e6 to "M"
        absValue >= 1e3 -> 1e3 to "K"
        else -> 1.0 to ""
    }
    val scaled = (value / divisor * 100).roundToLong() / 100.0
    val text = if (scaled % 1.0 == 0.0) scaled.toLong().toString() else scaled.toString()
    return text + suffix
}

/** A text run forced LTR (SPEC: prices/amounts are an LTR island even under `ar`). */
@Composable
fun LtrText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = 1) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(text = text, modifier = modifier, style = style, color = color, maxLines = maxLines)
    }
}

/** 24h change: arrow glyph + sign + % (success ▲ / danger ▼) — never colour-only (SPEC A11y). testTag mkt_change_24h. */
@Composable
fun ChangeBadge(changePct: String?, style: TextStyle = CryptasaTheme.typography.body, modifier: Modifier = Modifier) {
    if (changePct == null) return
    val colors = CryptasaTheme.colors
    val trimmed = changePct.trim()
    val negative = trimmed.startsWith("-")
    val arrow = if (negative) "▼" else "▲"
    val sign = if (!negative && !trimmed.startsWith("+")) "+" else ""
    LtrText("$arrow $sign$trimmed%", style, if (negative) colors.danger else colors.success, modifier.testTag("mkt_change_24h"))
}

/** Freshness line: live dot + "Live", or "Last updated %1$s", or the warning "delayed" badge (icon+text). */
@Composable
fun FreshnessLabel(freshness: Freshness?, modifier: Modifier = Modifier) {
    if (freshness == null) return
    val colors = CryptasaTheme.colors
    val typography = CryptasaTheme.typography
    when (freshness) {
        Freshness.Live -> Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(colors.success))
            Text(stringResource(Res.string.mkt_live), style = typography.bodySmall, color = colors.onSurfaceVariant)
        }
        is Freshness.Delayed ->
            if (freshness.stale) {
                Text(
                    text = "⚠ " + stringResource(Res.string.mkt_stale, freshness.label),
                    style = typography.bodySmall,
                    color = colors.warning,
                    modifier = modifier.testTag("mkt_stale"),
                )
            } else {
                Text(stringResource(Res.string.mkt_last_updated, freshness.label), style = typography.bodySmall, color = colors.onSurfaceVariant, modifier = modifier)
            }
    }
}

/** A label→value stat row (SPEC §4): value is an LTR island; null → "—". */
@Composable
fun StatRow(label: String, value: String?, testTag: String) {
    val colors = CryptasaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant)
        LtrText(value ?: "—", CryptasaTheme.typography.body, colors.onSurface)
    }
}

/** Mini trend sparkline (MD-3, ~64×24): line coloured by trend (success/danger). Decorative (aria-hidden);
 *  the trend is redundant via the change text. Render-Doubles only (FR-6 values stay String upstream). */
@Composable
fun MiniSparkline(values: List<Double>, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    val up = values.size < 2 || values.last() >= values.first()
    val color = if (up) colors.success else colors.danger
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val dx = size.width / (values.size - 1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = dx * i
            val y = size.height * (1f - ((v - min) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 2f))
    }
}

/** Skeleton placeholder (loading shimmer) — a rounded box pulsing between alphas. */
@Composable
fun SkeletonBox(modifier: Modifier) {
    val colors = CryptasaTheme.colors
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
    )
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(colors.surfaceVariant.copy(alpha = alpha)))
}
