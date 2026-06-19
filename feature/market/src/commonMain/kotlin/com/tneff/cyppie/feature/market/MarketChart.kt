package com.tneff.cyppie.feature.market

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The market candlestick renderer — an **expect/actual web-seam** (PRD-04 / ADR-0020). Native targets
 * (Android/iOS/Desktop) render via Compose `Canvas` ([CanvasCandlestickChart]); the Web `actual` is the
 * documented swap point for a JS/HTML-Canvas charting renderer (e.g. TradingView Advanced as the Rich
 * fast-follow, ADR-0020) without touching callers. All actuals currently delegate to the shared
 * Compose-Canvas impl.
 */
@Composable
expect fun MarketChart(candles: List<CandleBar>, modifier: Modifier)

private const val RIGHT_AXIS_PAD = 56f // px reserved for the right-edge price labels
private const val GRID_LINES = 4

/**
 * Shared cross-platform candlestick chart (KAN-133) — works on every Compose-MP target. Draws bodies +
 * wicks (up=success/down=danger), a price-axis grid with right-edge labels, and a press/drag **crosshair**
 * that reads out the touched candle's close. OHLC are render-Doubles (FR-6: financial values stay
 * String/Money upstream; the parse-to-Double happened at the VM edge). FR-4: empty → nothing drawn (the
 * screen shows its empty/error state around this).
 */
@Composable
internal fun CanvasCandlestickChart(candles: List<CandleBar>, modifier: Modifier) {
    val colors = CryptasaTheme.colors
    val upColor = colors.success
    val downColor = colors.danger
    val gridColor = colors.onSurfaceVariant.copy(alpha = 0.25f)
    val crosshairColor = colors.onSurface
    val labelStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val crosshairLabelStyle = TextStyle(color = colors.onSurface, fontSize = 10.sp)
    val textMeasurer = rememberTextMeasurer()
    var crosshairX by remember { mutableStateOf<Float?>(null) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(candles) {
                awaitEachGesture {
                    var change = awaitFirstDown(requireUnconsumed = false)
                    crosshairX = change.position.x
                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull() ?: break
                        crosshairX = if (change.pressed) change.position.x else null
                    }
                    crosshairX = null
                }
            },
    ) {
        if (candles.isEmpty()) return@Canvas
        val priceMin = candles.minOf { it.low }
        val priceMax = candles.maxOf { it.high }
        val span = (priceMax - priceMin).takeIf { it > 0.0 } ?: 1.0
        val plotWidth = (size.width - RIGHT_AXIS_PAD).coerceAtLeast(1f)
        val slot = plotWidth / candles.size
        val bodyWidth = (slot * 0.6f).coerceAtLeast(1f)
        fun yOf(price: Double): Float = size.height * (1f - ((price - priceMin) / span).toFloat())

        // Price grid + right-edge labels.
        for (i in 0..GRID_LINES) {
            val price = priceMin + span * i / GRID_LINES
            val y = yOf(price)
            drawLine(gridColor, Offset(0f, y), Offset(plotWidth, y), strokeWidth = 1f)
            val layout = textMeasurer.measure(formatAxisPrice(price), labelStyle)
            drawText(layout, topLeft = Offset(plotWidth + 4f, (y - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)))
        }

        // Candles: wick (high→low) + body (open↔close).
        candles.forEachIndexed { i, c ->
            val cx = slot * i + slot / 2f
            val color = if (c.up) upColor else downColor
            drawLine(color, Offset(cx, yOf(c.high)), Offset(cx, yOf(c.low)), strokeWidth = 1.5f)
            val bodyTop = yOf(maxOf(c.open, c.close))
            val bodyBottom = yOf(minOf(c.open, c.close))
            drawRect(
                color = color,
                topLeft = Offset(cx - bodyWidth / 2f, bodyTop),
                size = Size(bodyWidth, (bodyBottom - bodyTop).coerceAtLeast(1f)),
            )
        }

        // Crosshair (press/drag): vertical at the touched candle, horizontal + label at its close.
        crosshairX?.let { x ->
            val idx = (x / slot).toInt().coerceIn(0, candles.size - 1)
            val c = candles[idx]
            val cx = slot * idx + slot / 2f
            val cy = yOf(c.close)
            val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            drawLine(crosshairColor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1f, pathEffect = dash)
            drawLine(crosshairColor, Offset(0f, cy), Offset(plotWidth, cy), strokeWidth = 1f, pathEffect = dash)
            val layout = textMeasurer.measure(formatAxisPrice(c.close), crosshairLabelStyle)
            drawText(layout, topLeft = Offset(plotWidth + 4f, (cy - layout.size.height / 2f).coerceIn(0f, size.height - layout.size.height)))
        }
    }
}

/** Compact axis price label (render-only). ≥1 → 2 decimals; small prices → 6 (crypto dust). No float math upstream. */
private fun formatAxisPrice(value: Double): String {
    val factor = if (abs(value) >= 1.0) 100.0 else 1_000_000.0
    val rounded = (value * factor).roundToLong() / factor
    val s = rounded.toString()
    return if (s.endsWith(".0")) s.dropLast(2) else s
}
