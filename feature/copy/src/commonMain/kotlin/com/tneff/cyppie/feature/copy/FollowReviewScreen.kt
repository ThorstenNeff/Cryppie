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
import com.tneff.cyppie.feature.copy.generated.resources.copy_cap
import com.tneff.cyppie.feature.copy.generated.resources.copy_disclosure
import com.tneff.cyppie.feature.copy.generated.resources.copy_password
import com.tneff.cyppie.feature.copy.generated.resources.copy_review_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_router
import com.tneff.cyppie.feature.copy.generated.resources.copy_trader
import com.tneff.cyppie.feature.copy.generated.resources.copy_you_authorize
import org.jetbrains.compose.resources.stringResource

/**
 * Screen 3 (`Copy3-Confirm`, KAN-155) — the **no-blind disclosure** of the on-device-VERIFIED copy grant
 * (same DCA-grant pattern: signed == disclosed), then re-auth → sign. Every field (trader / cap / router /
 * allowed-function) comes from [FollowViewModel.prepared] (decoded from the signed bytes, KAN-154
 * verifyGrant) — never backend text; external strings sanitized (BidiSanitizer), addresses full + LTR. N
 * display-legs are rendered (router.execute display-targets); a verify mismatch never reaches here
 * ([prepared] null, fail-closed). FLAG_SECURE is applied by the app-shell host (signature context).
 */
@Composable
internal fun FollowReviewScreen(viewModel: FollowViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    var password by remember { mutableStateOf("") }
    val prepared = viewModel.prepared
    if (prepared == null) { onBack(); return } // defensive: no verified grant → back to budget
    val capHuman = viewModel.capHuman(prepared.capBaseUnits)

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.copy_review_title), onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl).testTag(CopyTestTags.REVIEW_SCREEN),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                // One-line plain-language summary: "…trade up to {cap} on router {router}…" (copy_disclosure).
                Text(
                    stringResource(Res.string.copy_disclosure, capHuman, BidiSanitizer.sanitize(prepared.primaryRouter)),
                    style = CryptasaTheme.typography.body,
                    color = colors.onSurfaceVariant,
                )
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant).padding(spacing.lg).testTag(CopyTestTags.DISCLOSURE),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(stringResource(Res.string.copy_you_authorize), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                    DisclosureRow(stringResource(Res.string.copy_trader), BidiSanitizer.sanitize(prepared.trader), ltr = true, truncate = false)
                    DisclosureRow(stringResource(Res.string.copy_cap), BidiSanitizer.sanitize(capHuman), ltr = true, valueTestTag = CopyTestTags.DISCLOSURE_CAP)
                    // N display-legs: each authorized (router → allowed function) the grant encodes.
                    prepared.legs.forEach { leg ->
                        DisclosureRow(stringResource(Res.string.copy_router), BidiSanitizer.sanitize(leg.router), ltr = true, truncate = false)
                        DisclosureRow(stringResource(Res.string.copy_allowed), BidiSanitizer.sanitize(leg.allowedFunction), ltr = true, truncate = false)
                    }
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
