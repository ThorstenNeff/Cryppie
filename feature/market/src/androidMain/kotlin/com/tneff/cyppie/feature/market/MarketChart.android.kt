package com.tneff.cyppie.feature.market

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Android: Compose Canvas (the shared native renderer).
@Composable
actual fun MarketChart(candles: List<CandleBar>, modifier: Modifier) = CanvasCandlestickChart(candles, modifier)
