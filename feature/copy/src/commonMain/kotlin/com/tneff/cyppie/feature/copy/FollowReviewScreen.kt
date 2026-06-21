package com.tneff.cyppie.feature.copy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.BidiSanitizer
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.DisclosureRow
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.copy.generated.resources.Res
import com.tneff.cyppie.feature.copy.generated.resources.copy_allowed
import com.tneff.cyppie.feature.copy.generated.resources.copy_authorize
import com.tneff.cyppie.feature.copy.generated.resources.copy_authorizing
import com.tneff.cyppie.feature.copy.generated.resources.copy_advisory
import com.tneff.cyppie.feature.copy.generated.resources.copy_advisory_note
import com.tneff.cyppie.feature.copy.generated.resources.copy_allocation
import com.tneff.cyppie.feature.copy.generated.resources.copy_cap
import com.tneff.cyppie.feature.copy.generated.resources.copy_disclosure
import com.tneff.cyppie.feature.copy.generated.resources.copy_guaranteed
import com.tneff.cyppie.feature.copy.generated.resources.copy_guaranteed_note
import com.tneff.cyppie.feature.copy.generated.resources.copy_password
import com.tneff.cyppie.feature.copy.generated.resources.copy_review_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_router
import com.tneff.cyppie.feature.copy.generated.resources.copy_receives
import com.tneff.cyppie.feature.copy.generated.resources.copy_source
import com.tneff.cyppie.feature.copy.generated.resources.copy_spend_token
import com.tneff.cyppie.feature.copy.generated.resources.copy_token_any
import com.tneff.cyppie.feature.copy.generated.resources.copy_window
import com.tneff.cyppie.feature.copy.generated.resources.copy_you_authorize
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 3 (`Copy3-Confirm`, KAN-155) — the **no-blind disclosure** + re-auth/sign. Two clearly-separated
 * trust sections (Dev-2 KAN-154 crypto-UX contract):
 *  1. **On-chain guaranteed** — ONLY [CopyGrantPreview.verifiedGrant] (cap / router / allowed-function),
 *     decoded from the signed bytes; same DCA-grant no-blind pattern; BidiSanitizer + full LTR addresses.
 *  2. **Advisory / context** — the followed [source] + allocation; mirror-time metadata, NOT in the signed
 *     enable → explicitly marked "not part of the on-chain guarantee" (Info banner), never as the guarantee.
 * A verify mismatch never reaches here ([prepared] null, fail-closed). FLAG_SECURE applied by the host.
 * (UX shipped the dedicated separation keys — copy_guaranteed / copy_advisory / copy_allocation; a richer
 * separation pattern, if UX ships one, swaps in here.)
 */
@Composable
internal fun FollowReviewScreen(viewModel: FollowViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    var password by remember { mutableStateOf("") }
    val preview = viewModel.prepared
    if (preview == null) { onBack(); return } // defensive: no verified grant → back to budget
    val v = preview.verifiedGrant
    val capHuman = viewModel.capHuman(v.capBaseUnits)

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.copy_review_title), onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl).testTag(CopyTestTags.REVIEW_SCREEN),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Text(stringResource(Res.string.copy_you_authorize), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                // Plain-language summary from the VERIFIED grant: "…trade up to {cap} on router {router}…".
                Text(
                    stringResource(Res.string.copy_disclosure, capHuman, BidiSanitizer.sanitize(v.actionTarget)),
                    style = CryptasaTheme.typography.body,
                    color = colors.onSurfaceVariant,
                )

                // ── Section 1: ON-CHAIN GUARANTEED (only the verified grant) ──
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant).padding(spacing.lg).testTag(CopyTestTags.DISCLOSURE),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(stringResource(Res.string.copy_guaranteed), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                    Text(stringResource(Res.string.copy_guaranteed_note), style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant)
                    DisclosureRow(stringResource(Res.string.copy_cap), BidiSanitizer.sanitize(capHuman), ltr = true, valueTestTag = CopyTestTags.DISCLOSURE_CAP)
                    DisclosureRow(stringResource(Res.string.copy_spend_token), BidiSanitizer.sanitize(v.spendToken), ltr = true, truncate = false)
                    DisclosureRow(stringResource(Res.string.copy_router), BidiSanitizer.sanitize(v.actionTarget), ltr = true, truncate = false)
                    DisclosureRow(stringResource(Res.string.copy_allowed), BidiSanitizer.sanitize(v.actionSelector), ltr = true, truncate = false)
                    // Limit window [start, end] (epoch s; readable-date is the KAN-148 follow, shared with DCA).
                    DisclosureRow(stringResource(Res.string.copy_window), "${v.windowStartEpochSeconds} – ${v.windowEndEpochSeconds}", ltr = true)
                }

                // ── Section 2: ADVISORY / CONTEXT (not crypto-guaranteed) — visually separated (Dev-2 crypto-UX) ──
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    CryptasaBanner(
                        title = stringResource(Res.string.copy_advisory),
                        description = stringResource(Res.string.copy_advisory_note),
                        tone = CryptasaBannerTone.Info,
                        modifier = Modifier.testTag("copy_advisory"),
                    )
                    DisclosureRow(stringResource(Res.string.copy_source), BidiSanitizer.sanitize(preview.source), ltr = true, truncate = false)
                    // Mirror token (KAN-161) — advisory, NOT in the signed enable: fixed → the exact receive
                    // token; dynamic → "any listed token" (the webhook derives + allowlist-gates it per trade).
                    val mirrorToken = if (viewModel.mode == CopyMode.FIXED)
                        BidiSanitizer.sanitize(viewModel.tokenOut) else stringResource(Res.string.copy_token_any)
                    DisclosureRow(stringResource(Res.string.copy_receives), mirrorToken, ltr = true, truncate = false, valueTestTag = CopyTestTags.RECEIVES)
                    DisclosureRow(stringResource(Res.string.copy_allocation), "${preview.allocationBps / 100}%", ltr = true)
                }

                viewModel.error?.let { CryptasaBanner(title = it.text(), tone = CryptasaBannerTone.Danger, modifier = Modifier.testTag(CopyTestTags.ERROR)) }

                // Re-auth gate (ADR-0009) → on-device sign of the VERIFIED grant.
                CryptasaTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(Res.string.copy_password),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.PASSWORD),
                )
                CryptasaButton(
                    text = if (viewModel.submitting) stringResource(Res.string.copy_authorizing) else stringResource(Res.string.copy_authorize),
                    onClick = { viewModel.confirm(password) },
                    enabled = !viewModel.submitting && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xl).testTag(CopyTestTags.AUTHORIZE),
                )
            }
        }
    }
}
