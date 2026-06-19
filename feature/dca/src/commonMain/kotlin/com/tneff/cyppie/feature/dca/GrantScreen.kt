package com.tneff.cyppie.feature.dca

import com.tneff.cyppie.feature.dca.generated.resources.Res
import com.tneff.cyppie.feature.dca.generated.resources.dca_amount_per_buy
import com.tneff.cyppie.feature.dca.generated.resources.dca_authorize
import com.tneff.cyppie.feature.dca.generated.resources.dca_account
import com.tneff.cyppie.feature.dca.generated.resources.dca_chain
import com.tneff.cyppie.feature.dca.generated.resources.dca_authorizing
import com.tneff.cyppie.feature.dca.generated.resources.dca_expires
import com.tneff.cyppie.feature.dca.generated.resources.dca_freq_daily
import com.tneff.cyppie.feature.dca.generated.resources.dca_freq_weekly
import com.tneff.cyppie.feature.dca.generated.resources.dca_new
import com.tneff.cyppie.feature.dca.generated.resources.dca_password
import com.tneff.cyppie.feature.dca.generated.resources.dca_router
import com.tneff.cyppie.feature.dca.generated.resources.dca_selector
import com.tneff.cyppie.feature.dca.generated.resources.dca_spend_token
import com.tneff.cyppie.feature.dca.generated.resources.dca_total_cap
import com.tneff.cyppie.feature.dca.generated.resources.dca_you_authorize
import org.jetbrains.compose.resources.stringResource

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
import androidx.compose.runtime.LaunchedEffect
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
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.BidiSanitizer
import com.tneff.cyppie.designsystem.components.DisclosureRow
import com.tneff.cyppie.designsystem.components.SegmentedControl
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

// Pre-i18n labels (dca_* keys → UX follow-up; no composeResources change → checkI18n unaffected).

/**
 * DCA Smart-Session grant (PRD-05 Ph1, KAN-144). Two phases: set the buy amount + frequency → **Review**
 * (backend enable op → **on-device `verifyGrant`**), then the **no-blind disclosure renders ONLY the
 * verified grant** (account/target/selector/token/cap/window — decoded from the bytes inside the signed
 * digest, never the raw backend material) → re-auth → on-device sign of the verified digest. A verification
 * failure is fail-closed (no disclosure, no sign). On success → [onDone].
 */
@Composable
fun GrantScreen(viewModel: GrantViewModel, onDone: () -> Unit, onBack: () -> Unit = {}, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    var password by remember { mutableStateOf("") }
    val freqDaily = stringResource(Res.string.dca_freq_daily)
    val freqWeekly = stringResource(Res.string.dca_freq_weekly)

    LaunchedEffect(viewModel.granted) { if (viewModel.granted) onDone() }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.dca_new), onBack = onBack)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.xl).testTag("dca_grant_screen"),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                CryptasaTextField(
                    value = viewModel.capAmount,
                    onValueChange = viewModel::setCap,
                    label = stringResource(Res.string.dca_amount_per_buy),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth().testTag("dca_grant_amount"),
                )
                SegmentedControl(
                    options = DcaFrequency.entries.toList(),
                    selected = viewModel.frequency,
                    onSelect = viewModel::selectFrequency,
                    label = { if (it == DcaFrequency.DAILY) freqDaily else freqWeekly },
                    optionTestTag = { "dca_freq_${it.name.lowercase()}" },
                )

                viewModel.error?.let { CryptasaBanner(title = it.text(), tone = CryptasaBannerTone.Danger, modifier = Modifier.testTag("dca_grant_error")) }

                val verified = viewModel.verified
                if (verified == null) {
                    // Phase 1 (KAN-144): the disclosure is the ON-DEVICE-VERIFIED grant, so we must first run
                    // verifyGrant against the backend enable digest. No blind config preview here.
                    CryptasaButton(
                        text = if (viewModel.reviewing) stringResource(Res.string.dca_authorizing) else "Review grant", // dca_review = UX (parity pending)
                        onClick = { viewModel.review() },
                        enabled = !viewModel.reviewing && viewModel.capAmount.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xl).testTag("dca_grant_review"),
                    )
                } else {
                    // No-blind disclosure — renders ONLY the VerifiedGrant (decoded from the bytes inside the
                    // signed enable digest), never the raw backend material. External strings sanitized.
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant).padding(spacing.lg).testTag("dca_grant_disclosure"),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        Text(stringResource(Res.string.dca_you_authorize), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                        DisclosureRow(stringResource(Res.string.dca_account), BidiSanitizer.sanitize(verified.account), ltr = true, truncate = false)
                        DisclosureRow(stringResource(Res.string.dca_chain), verified.chainId.toString(), ltr = true)
                        DisclosureRow(stringResource(Res.string.dca_router), BidiSanitizer.sanitize(verified.actionTarget), ltr = true, truncate = false)
                        DisclosureRow(stringResource(Res.string.dca_selector), BidiSanitizer.sanitize(verified.actionSelector), ltr = true, truncate = false)
                        DisclosureRow(stringResource(Res.string.dca_spend_token), BidiSanitizer.sanitize(verified.spendToken), ltr = true, truncate = false)
                        DisclosureRow(stringResource(Res.string.dca_total_cap), viewModel.capHuman(verified), ltr = true, valueTestTag = "dca_grant_cap")
                        DisclosureRow("Active from", verified.windowStartEpochSeconds.toString(), ltr = true) // dca_window_start = UX (parity pending)
                        DisclosureRow(stringResource(Res.string.dca_expires), verified.windowEndEpochSeconds.toString(), ltr = true)
                    }

                    // Re-auth gate (ADR-0009) → on-device sign of the VERIFIED enable digest.
                    CryptasaTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(Res.string.dca_password),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardType = KeyboardType.Password,
                        modifier = Modifier.fillMaxWidth().testTag("dca_grant_password"),
                    )
                    CryptasaButton(
                        text = if (viewModel.submitting) stringResource(Res.string.dca_authorizing) else stringResource(Res.string.dca_authorize),
                        onClick = { viewModel.grant(password) },
                        enabled = !viewModel.submitting && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xl).testTag("dca_grant_authorize"),
                    )
                }
            }
        }
    }
}
