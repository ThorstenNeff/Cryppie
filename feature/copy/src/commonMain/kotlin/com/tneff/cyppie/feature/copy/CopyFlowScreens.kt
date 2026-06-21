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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.copy.generated.resources.Res
import com.tneff.cyppie.feature.copy.generated.resources.copy_budget_caveat
import com.tneff.cyppie.feature.copy.generated.resources.copy_budget_label
import com.tneff.cyppie.feature.copy.generated.resources.copy_budget_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_continue
import com.tneff.cyppie.feature.copy.generated.resources.copy_select_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_trader_label
import com.tneff.cyppie.feature.copy.generated.resources.copy_trader_placeholder
import org.jetbrains.compose.resources.stringResource

/** Screen 1 (`Copy1-SelectTrader`) — pick the trader to copy. EIP-55 + self-copy guard (copy_err_*). */
@Composable
internal fun FollowTraderScreen(viewModel: FollowViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.copy_select_title), onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl).testTag(CopyTestTags.SELECT_SCREEN),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                CryptasaTextField(
                    value = viewModel.trader,
                    onValueChange = viewModel::enterTrader,
                    label = stringResource(Res.string.copy_trader_label),
                    placeholder = stringResource(Res.string.copy_trader_placeholder),
                    // EIP-55 / self-copy errors surface inline on the field.
                    errorText = viewModel.error?.takeIf { it == CopyError.INVALID_ADDRESS || it == CopyError.SELF_COPY }?.text(),
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.TRADER_INPUT),
                )
                CryptasaButton(
                    text = stringResource(Res.string.copy_continue),
                    onClick = viewModel::toBudget,
                    enabled = viewModel.trader.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.CONTINUE),
                )
            }
        }
    }
}

/** Screen 2 (`Copy2-Budget`) — set the total budget (= the on-chain cap; v1 fixed budget cap) + risk caveat. */
@Composable
internal fun BudgetScreen(viewModel: FollowViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.copy_budget_title), onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl).testTag(CopyTestTags.BUDGET_SCREEN),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                CryptasaTextField(
                    value = viewModel.budget,
                    onValueChange = viewModel::enterBudget,
                    label = stringResource(Res.string.copy_budget_label),
                    errorText = viewModel.error?.takeIf { it == CopyError.ENTER_BUDGET }?.text(),
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.BUDGET_INPUT),
                )
                // Risk / expectation caveat (non-custodial, revocable). Spec asks for an Info tone; the DS has
                // only Danger/Warning/Offline (no new DS components per spec) → Warning. Info-tone = UX/DS gap.
                CryptasaBanner(
                    title = stringResource(Res.string.copy_budget_caveat),
                    tone = CryptasaBannerTone.Warning,
                    modifier = Modifier.testTag(CopyTestTags.BUDGET_CAVEAT),
                )
                CryptasaButton(
                    text = stringResource(Res.string.copy_continue),
                    onClick = viewModel::review,
                    enabled = !viewModel.preparing && viewModel.budget.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.CONTINUE),
                )
            }
        }
    }
}
