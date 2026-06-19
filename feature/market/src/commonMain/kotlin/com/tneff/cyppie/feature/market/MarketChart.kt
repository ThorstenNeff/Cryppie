package com.tneff.cyppie.feature.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

/**
 * The market price-chart renderer — an **expect/actual web-seam** (KAN-126-style, PRD-04 / ADR-0025).
 * Native targets (Android/iOS/Desktop) render via Compose `Canvas` ([CanvasMarketChart]); the Web
 * `actual` can later swap a JS/HTML-Canvas charting renderer in without touching callers (Dev-2's
 * data layer is web-capable). All actuals currently delegate to the shared Compose-Canvas impl — the
 * seam is established for that future divergence.
 */
@Composable
expect fun MarketChart(points: List<MarketChartPoint>, modifier: Modifier)

/**
 * Shared cross-platform line chart drawn with Compose `Canvas` (works on every Compose-MP target).
 * A simple min/max-scaled polyline — the scaffold renderer; axes/gradients/crosshair are follow-ups.
 */
@Composable
internal fun CanvasMarketChart(points: List<MarketChartPoint>, modifier: Modifier) {
    val lineColor = CryptasaTheme.colors.primary
    Canvas(modifier = modifier.fillMaxWidth().height(180.dp)) {
        if (points.size < 2) return@Canvas
        val prices = points.map { it.price }
        val min = prices.min()
        val max = prices.max()
        val span = (max - min).takeIf { it > 0.0 } ?: 1.0
        val dx = size.width / (points.size - 1)
        val path = Path()
        points.forEachIndexed { i, p ->
            val x = dx * i
            // y inverted: higher price = higher on screen (smaller y).
            val y = size.height * (1f - ((p.price - min) / span).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 3f))
        // Endpoint dot for the latest price.
        val last = points.last()
        val lastY = size.height * (1f - ((last.price - min) / span).toFloat())
        drawCircle(color = lineColor, radius = 5f, center = Offset(size.width, lastY))
    }
}
