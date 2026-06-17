package com.tneff.cyppie.feature.onboarding

import androidx.compose.ui.Modifier

/**
 * Maps Compose `testTag`s to Android resource-ids so Maestro can select via `id:` (§5.1, KAN-10).
 * Applied once at the onboarding root. Only Android needs the bridge; on every other target this is
 * a no-op (iOS already exposes `testTag` as the accessibility identifier; Desktop/Web are not driven
 * by Maestro). Follows the `expect`/`actual` platform pattern (CLAUDE.md, ADR-0003).
 */
expect fun Modifier.enableTestTagsAsResourceId(): Modifier
