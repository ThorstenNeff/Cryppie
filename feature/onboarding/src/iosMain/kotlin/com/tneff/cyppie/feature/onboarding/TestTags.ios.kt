package com.tneff.cyppie.feature.onboarding

import androidx.compose.ui.Modifier

/**
 * No-op on iOS: the Android `testTagsAsResourceId` flag has no iOS counterpart and is not needed.
 * Compose Multiplatform exports the semantics tree to UIAccessibility and surfaces each
 * `Modifier.testTag` as the element's `accessibilityIdentifier` automatically, which is exactly the
 * selector Maestro uses on iOS (`id:`). The [OnboardingTestTags] contract therefore holds on iOS
 * without any bridge here.
 */
actual fun Modifier.enableTestTagsAsResourceId(): Modifier = this
