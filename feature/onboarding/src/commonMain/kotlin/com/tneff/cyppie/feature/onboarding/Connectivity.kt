package com.tneff.cyppie.feature.onboarding

import kotlinx.coroutines.flow.Flow

/**
 * Observes network connectivity for ONB-2 (SPEC_ONBOARDING_SCREEN2 §KMP): emits `true` while online,
 * `false` while offline. The path screen only *reacts* to this (offline banner) — it never gates
 * wallet creation, which works offline.
 *
 * Platform-specific (`expect`/`actual`, ADR-0003): Android `ConnectivityManager`, iOS `NWPathMonitor`.
 * Until those land the `actual`s emit a constant `true` (online) — structure first, real monitors next.
 */
expect fun observeConnectivity(): Flow<Boolean>
