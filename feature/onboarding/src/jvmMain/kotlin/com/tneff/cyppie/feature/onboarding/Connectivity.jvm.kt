package com.tneff.cyppie.feature.onboarding

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// Stub until the real monitor lands (see expect doc): always online.
actual fun observeConnectivity(): Flow<Boolean> = flowOf(true)
