package com.tneff.cyppie.feature.onboarding

/**
 * Launch-time app/store integrity check (ONB-1, SPEC_ONBOARDING_SCREEN1 §"Verhalten").
 *
 * Returns `true` when the app and its on-device encrypted store look intact, `false` when the
 * installation appears corrupted — in which case [com.tneff.cyppie.feature.onboarding.OnboardingRoot]
 * shows the blocking start-error dialog instead of the welcome content.
 *
 * Platform-specific (`expect`/`actual`, ADR-0003). The real check is tied to the secure-storage
 * decision (ADR-0009, still open); until then the `actual`s return `true` (no false positives that
 * would block launch). When ADR-0009 lands, wire the actual store/signature verification here.
 */
expect fun verifyAppIntegrity(): Boolean
