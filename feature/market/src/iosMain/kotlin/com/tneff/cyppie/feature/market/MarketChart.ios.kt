package com.tneff.cyppie.feature.market

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// iOS: Compose Canvas (the shared native renderer).
@Composable
actual fun MarketChart(points: List<MarketChartPoint>, modifier: Modifier) = CanvasMarketChart(points, modifier)
