package com.tneff.cyppie.feature.market

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Web (JS): the shared Compose-Canvas renderer for now. This actual is the seam where a richer
// JS/HTML-Canvas charting renderer can be dropped in later, without touching callers (PRD-04).
@Composable
actual fun MarketChart(candles: List<CandleBar>, modifier: Modifier) = CanvasCandlestickChart(candles, modifier)
