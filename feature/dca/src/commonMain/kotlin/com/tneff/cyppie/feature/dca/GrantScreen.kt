package com.tneff.cyppie.feature.dca

import com.tneff.cyppie.feature.dca.generated.resources.Res
import com.tneff.cyppie.feature.dca.generated.resources.dca_amount_per_buy
import com.tneff.cyppie.feature.dca.generated.resources.dca_authorize
import com.tneff.cyppie.feature.dca.generated.resources.dca_authorizing
import com.tneff.cyppie.feature.dca.generated.resources.dca_cap_per_buy
import com.tneff.cyppie.feature.dca.generated.resources.dca_expires
import com.tneff.cyppie.feature.dca.generated.resources.dca_freq_daily
import com.tneff.cyppie.feature.dca.generated.resources.dca_freq_weekly
import com.tneff.cyppie.feature.dca.generated.resources.dca_frequency
import com.tneff.cyppie.feature.dca.generated.resources.dca_max_buys
import com.tneff.cyppie.feature.dca.generated.resources.dca_new
import com.tneff.cyppie.feature.dca.generated.resources.dca_password
import com.tneff.cyppie.feature.dca.generated.resources.dca_router
import com.tneff.cyppie.feature.dca.generated.resources.dca_spend_token
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
import com.tneff.cyppie.designsystem.components.DisclosureRow
import com.tneff.cyppie.designsystem.components.SegmentedControl
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

// Pre-i18n labels (dca_* keys → UX follow-up; no composeResources change → checkI18n unaffected).

/**
 * DCA Smart-Session grant (PRD-05 Ph1): set the per-buy amount + frequency + duration, see the **no-blind
 * disclosure** of the exact §2 session being authorized (allowed router, cap+token, frequency, max ops,
 * expiry), then re-auth → on-device enable-sign. On success → [onDone].
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

                // No-blind disclosure of the exact §2 session being authorized (what gets signed).
                val config = viewModel.buildConfig()
                val action = config.actions.first()
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant).padding(spacing.lg).testTag("dca_grant_disclosure"),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(stringResource(Res.string.dca_you_authorize), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                    DisclosureRow(stringResource(Res.string.dca_router), action.target, ltr = true)
                    action.spendingLimits.firstOrNull()?.let {
                        DisclosureRow(stringResource(Res.string.dca_spend_token), it.token, ltr = true)
                        DisclosureRow(stringResource(Res.string.dca_cap_per_buy), it.cap, ltr = true, valueTestTag = "dca_grant_cap")
                    }
                    DisclosureRow(stringResource(Res.string.dca_frequency), (if (viewModel.frequency == DcaFrequency.DAILY) freqDaily else freqWeekly), ltr = true)
                    DisclosureRow(stringResource(Res.string.dca_max_buys), action.usageLimit.toString(), ltr = true)
                    DisclosureRow(stringResource(Res.string.dca_expires), action.validUntil.toString(), ltr = true)
                }

                viewModel.error?.let { CryptasaBanner(title = it.text(), tone = CryptasaBannerTone.Danger, modifier = Modifier.testTag("dca_grant_error")) }

                // Re-auth gate (ADR-0009) → on-device enable-sign of the disclosed session.
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
                    enabled = !viewModel.submitting && password.isNotBlank() && viewModel.capAmount.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xl).testTag("dca_grant_authorize"),
                )
            }
        }
    }
}
