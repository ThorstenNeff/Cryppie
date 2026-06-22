package com.tneff.cyppie.feature.strat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.tneff.cyppie.designsystem.SecureScreenEffect
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.DisclosureRow
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.strat.generated.resources.Res
import com.tneff.cyppie.feature.strat.generated.resources.strat_add_token
import com.tneff.cyppie.feature.strat.generated.resources.strat_advisory
import com.tneff.cyppie.feature.strat.generated.resources.strat_advisory_note
import com.tneff.cyppie.feature.strat.generated.resources.strat_allowed
import com.tneff.cyppie.feature.strat.generated.resources.strat_authorize
import com.tneff.cyppie.feature.strat.generated.resources.strat_authorizing
import com.tneff.cyppie.feature.strat.generated.resources.strat_basket
import com.tneff.cyppie.feature.strat.generated.resources.strat_budget
import com.tneff.cyppie.feature.strat.generated.resources.strat_cap
import com.tneff.cyppie.feature.strat.generated.resources.strat_caveat
import com.tneff.cyppie.feature.strat.generated.resources.strat_continue
import com.tneff.cyppie.feature.strat.generated.resources.strat_disclosure
import com.tneff.cyppie.feature.strat.generated.resources.strat_err_budget
import com.tneff.cyppie.feature.strat.generated.resources.strat_err_min
import com.tneff.cyppie.feature.strat.generated.resources.strat_err_password
import com.tneff.cyppie.feature.strat.generated.resources.strat_err_submit
import com.tneff.cyppie.feature.strat.generated.resources.strat_err_sum
import com.tneff.cyppie.feature.strat.generated.resources.strat_err_verify
import com.tneff.cyppie.feature.strat.generated.resources.strat_guaranteed
import com.tneff.cyppie.feature.strat.generated.resources.strat_guaranteed_note
import com.tneff.cyppie.feature.strat.generated.resources.strat_password
import com.tneff.cyppie.feature.strat.generated.resources.strat_review_title
import com.tneff.cyppie.feature.strat.generated.resources.strat_router
import com.tneff.cyppie.feature.strat.generated.resources.strat_err_token
import com.tneff.cyppie.feature.strat.generated.resources.strat_setup_title
import com.tneff.cyppie.feature.strat.generated.resources.strat_target
import com.tneff.cyppie.feature.strat.generated.resources.strat_total
import com.tneff.cyppie.feature.strat.generated.resources.strat_weight
import com.tneff.cyppie.feature.strat.generated.resources.strat_weights
import com.tneff.cyppie.feature.strat.generated.resources.strat_window
import com.tneff.cyppie.feature.strat.generated.resources.strat_you_authorize
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Map a [StratError] to its strat_ message. */
private fun StratError.res(): StringResource = when (this) {
    StratError.ENTER_BUDGET -> Res.string.strat_err_budget
    StratError.SUM_NOT_100 -> Res.string.strat_err_sum
    StratError.MIN_TOKENS -> Res.string.strat_err_min
    StratError.INVALID_TOKEN -> Res.string.strat_err_token
    StratError.WRONG_PASSWORD -> Res.string.strat_err_password
    StratError.VERIFY_FAILED -> Res.string.strat_err_verify
    StratError.SUBMIT_FAILED -> Res.string.strat_err_submit
}

/** `Strat1-Setup` — target allocation (≥2 tokens, weights = 100%) + budget + risk caveat → review. */
@Composable
internal fun StrategySetupScreen(viewModel: StrategyViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.strat_setup_title), onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl).testTag(StrategyTestTags.SETUP_SCREEN),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Text(stringResource(Res.string.strat_target), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)

                viewModel.rows.forEachIndexed { index, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                        CryptasaTextField(
                            value = row.token,
                            onValueChange = { viewModel.setToken(index, it) },
                            label = "0x…",
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier.weight(2f),
                        )
                        CryptasaTextField(
                            value = row.weight,
                            onValueChange = { viewModel.setWeight(index, it) },
                            label = stringResource(Res.string.strat_weight),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                CryptasaButton(
                    text = stringResource(Res.string.strat_add_token),
                    onClick = viewModel::addToken,
                    style = CryptasaButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth().testTag(StrategyTestTags.ADD_TOKEN),
                )

                // Live total (the donut/stacked-bar viz is visual polish for a follow-up; the % total is the gate).
                DisclosureRow(stringResource(Res.string.strat_total), "${viewModel.totalWeight}%", ltr = true, valueTestTag = StrategyTestTags.TOTAL)

                CryptasaTextField(
                    value = viewModel.budget,
                    onValueChange = viewModel::enterBudget,
                    label = stringResource(Res.string.strat_budget),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth().testTag(StrategyTestTags.BUDGET_INPUT),
                )
                CryptasaBanner(
                    title = stringResource(Res.string.strat_caveat),
                    tone = CryptasaBannerTone.Info,
                )
                viewModel.error?.let { CryptasaBanner(title = stringResource(it.res()), tone = CryptasaBannerTone.Danger) }

                CryptasaButton(
                    text = stringResource(Res.string.strat_continue),
                    onClick = viewModel::review,
                    enabled = viewModel.setupReady && !viewModel.submitting,
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xl).testTag(StrategyTestTags.CONTINUE),
                )
            }
        }
    }
}

/**
 * `Strat-Confirm` — the no-blind, **sell-side** 2-section disclosure + re-auth/sign. 🔒 = the verified grant
 * (sell-cap / basket / router / allowed-fn / window, decoded from the signed bytes); ℹ️ = the advisory target
 * weights (mirror-time buy direction, NOT in the enable). FLAG_SECURE is owned here (KAN-168). Fail-closed.
 */
@Composable
internal fun StrategyReviewScreen(viewModel: StrategyViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    SecureScreenEffect() // KAN-168: this screen OWNS its protection (password + sign context)
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    var password by remember { mutableStateOf("") }
    val preview = viewModel.prepared
    if (preview == null) { onBack(); return } // defensive: no verified grant → back to setup
    val capHuman = viewModel.capHuman(preview.sellCapBaseUnits)

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.strat_review_title), onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl).testTag(StrategyTestTags.REVIEW_SCREEN),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                Text(stringResource(Res.string.strat_you_authorize), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                Text(
                    stringResource(Res.string.strat_disclosure, capHuman, BidiSanitizer.sanitize(preview.router)),
                    style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant,
                )

                // ── Section 1: ON-CHAIN GUARANTEED (sell-side verified grant) ──
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant).padding(spacing.lg).testTag(StrategyTestTags.DISCLOSURE),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Text(stringResource(Res.string.strat_guaranteed), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                    Text(stringResource(Res.string.strat_guaranteed_note), style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant)
                    DisclosureRow(stringResource(Res.string.strat_cap), BidiSanitizer.sanitize(capHuman), ltr = true, valueTestTag = StrategyTestTags.CAP)
                    DisclosureRow(stringResource(Res.string.strat_basket), preview.basketTokens.joinToString(", ") { BidiSanitizer.sanitize(it) }, ltr = true, truncate = false)
                    DisclosureRow(stringResource(Res.string.strat_router), BidiSanitizer.sanitize(preview.router), ltr = true, truncate = false)
                    DisclosureRow(stringResource(Res.string.strat_allowed), BidiSanitizer.sanitize(preview.actionSelector), ltr = true, truncate = false)
                    DisclosureRow(stringResource(Res.string.strat_window), "${preview.windowStartEpochSeconds} – ${preview.windowEndEpochSeconds}", ltr = true)
                }

                // ── Section 2: ADVISORY (target weights — mirror-time, not in the enable) ──
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    CryptasaBanner(
                        title = stringResource(Res.string.strat_advisory),
                        description = stringResource(Res.string.strat_advisory_note),
                        tone = CryptasaBannerTone.Info,
                    )
                    DisclosureRow(
                        stringResource(Res.string.strat_weights),
                        preview.targets.joinToString(" · ") { "${BidiSanitizer.sanitize(it.token)} ${it.weightPercent}%" },
                        ltr = true, truncate = false,
                    )
                }

                viewModel.error?.let { CryptasaBanner(title = stringResource(it.res()), tone = CryptasaBannerTone.Danger, modifier = Modifier.testTag(StrategyTestTags.ERROR)) }

                CryptasaTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(Res.string.strat_password),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    modifier = Modifier.fillMaxWidth().testTag(StrategyTestTags.PASSWORD),
                )
                CryptasaButton(
                    text = if (viewModel.submitting) stringResource(Res.string.strat_authorizing) else stringResource(Res.string.strat_authorize),
                    onClick = { viewModel.confirm(password) },
                    enabled = !viewModel.submitting && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.xl).testTag(StrategyTestTags.AUTHORIZE),
                )
            }
        }
    }
}
