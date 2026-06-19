package com.tneff.cyppie.feature.dca

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

    LaunchedEffect(viewModel.granted) { if (viewModel.granted) onDone() }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = "New DCA", onBack = onBack)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.xl).testTag("dca_grant_screen"),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                CryptasaTextField(
                    value = viewModel.capAmount,
                    onValueChange = viewModel::setCap,
                    label = "Amount per buy",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth().testTag("dca_grant_amount"),
                )
                SegmentedControl(
                    options = DcaFrequency.entries.toList(),
                    selected = viewModel.frequency,
                    onSelect = viewModel::selectFrequency,
                    label = { it.label },
                    optionTestTag = { "dca_freq_${it.name.lowercase()}" },
                )

                // No-blind disclosure of the exact §2 session being authorized (what gets signed).
                val config = viewModel.buildConfig()
                val action = config.actions.first()
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant).padding(spacing.lg).testTag("dca_grant_disclosure"),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text("You authorize", style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                    DisclosureRow("Router", action.target, ltr = true)
                    action.spendingLimits.firstOrNull()?.let {
                        DisclosureRow("Spend token", it.token, ltr = true)
                        DisclosureRow("Cap per buy", it.cap, ltr = true, valueTestTag = "dca_grant_cap")
                    }
                    DisclosureRow("Frequency", viewModel.frequency.label, ltr = true)
                    DisclosureRow("Max buys", action.usageLimit.toString(), ltr = true)
                    DisclosureRow("Expires (unix)", action.validUntil.toString(), ltr = true)
                }

                viewModel.error?.let { CryptasaBanner(title = it, tone = CryptasaBannerTone.Danger, modifier = Modifier.testTag("dca_grant_error")) }

                // Re-auth gate (ADR-0009) → on-device enable-sign of the disclosed session.
                CryptasaTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    modifier = Modifier.fillMaxWidth().testTag("dca_grant_password"),
                )
                CryptasaButton(
                    text = if (viewModel.submitting) "Authorizing…" else "Authorize session",
                    onClick = { viewModel.grant(password) },
                    enabled = !viewModel.submitting && password.isNotBlank() && viewModel.capAmount.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xl).testTag("dca_grant_authorize"),
                )
            }
        }
    }
}
