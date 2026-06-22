package com.tneff.cyppie.feature.copy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.BidiSanitizer
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaCheckbox
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.TokenPicker
import com.tneff.cyppie.designsystem.components.TokenPickerItem
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.SelectionCard
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.copy.generated.resources.Res
import com.tneff.cyppie.feature.copy.generated.resources.copy_budget_caveat
import com.tneff.cyppie.feature.copy.generated.resources.copy_budget_caveat_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_budget_label
import com.tneff.cyppie.feature.copy.generated.resources.copy_budget_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_continue
import com.tneff.cyppie.feature.copy.generated.resources.copy_dyn_risk_ack
import com.tneff.cyppie.feature.copy.generated.resources.copy_dyn_risk_body
import com.tneff.cyppie.feature.copy.generated.resources.copy_dyn_risk_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_mode_dynamic
import com.tneff.cyppie.feature.copy.generated.resources.copy_mode_dynamic_desc
import com.tneff.cyppie.feature.copy.generated.resources.copy_mode_fixed
import com.tneff.cyppie.feature.copy.generated.resources.copy_mode_fixed_desc
import com.tneff.cyppie.feature.copy.generated.resources.copy_mode_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_select_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_token_none
import com.tneff.cyppie.feature.copy.generated.resources.copy_token_pick
import com.tneff.cyppie.feature.copy.generated.resources.copy_token_search
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
                    onClick = viewModel::toMode,
                    enabled = viewModel.trader.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.CONTINUE),
                )
            }
        }
    }
}

/**
 * Screen 2 (`Copy2-ModeSelect`, KAN-161) — pick the mirror mode. Two [SelectionCard]s: **Fixed** (copy the
 * trader into ONE token you choose → a token field) vs **Dynamic** (mirror all the trader's listed-token buys
 * → a prominent danger consent with a MANDATORY acknowledgement checkbox). The mode only sets the mirror-time
 * `tokenOut` (NOT the signed enable), so the on-chain guarantee is identical either way. Continue is gated on
 * [FollowViewModel.modeReady]. (v1 fixed-token entry is an address field; a curated token picker is a UX
 * follow-up — the testTag/key contract `copy_token_pick` is stable across that change.)
 */
@Composable
internal fun ModeSelectScreen(viewModel: FollowViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.copy_mode_title), onBack = onBack)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.xl).testTag(CopyTestTags.MODE_SCREEN),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                SelectionCard(
                    title = stringResource(Res.string.copy_mode_fixed),
                    description = stringResource(Res.string.copy_mode_fixed_desc),
                    selected = viewModel.mode == CopyMode.FIXED,
                    onClick = { viewModel.selectMode(CopyMode.FIXED) },
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.MODE_FIXED),
                )
                SelectionCard(
                    title = stringResource(Res.string.copy_mode_dynamic),
                    description = stringResource(Res.string.copy_mode_dynamic_desc),
                    selected = viewModel.mode == CopyMode.DYNAMIC,
                    onClick = { viewModel.selectMode(CopyMode.DYNAMIC) },
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.MODE_DYNAMIC),
                )

                // Mode-specific gate: fixed → the receive token; dynamic → the mandatory risk acknowledgement.
                when (viewModel.mode) {
                    // KAN-168 F5: pick from the curated allowlist (no free hex entry → no fake-token risk).
                    CopyMode.FIXED -> SelectionCard(
                        title = viewModel.selectedToken?.let { "${it.symbol} — ${it.name}" } ?: stringResource(Res.string.copy_token_pick),
                        selected = viewModel.selectedToken != null,
                        onClick = viewModel::openTokenPicker,
                        modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.TOKEN_PICK),
                    )
                    CopyMode.DYNAMIC -> {
                        CryptasaBanner(
                            title = stringResource(Res.string.copy_dyn_risk_title),
                            description = stringResource(Res.string.copy_dyn_risk_body),
                            tone = CryptasaBannerTone.Danger,
                            modifier = Modifier.testTag(CopyTestTags.DYN_RISK),
                        )
                        CryptasaCheckbox(
                            checked = viewModel.dynRiskAck,
                            onCheckedChange = viewModel::acknowledgeRisk,
                            label = stringResource(Res.string.copy_dyn_risk_ack),
                            modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.DYN_RISK_ACK),
                        )
                    }
                    null -> Unit
                }

                CryptasaButton(
                    text = stringResource(Res.string.copy_continue),
                    onClick = viewModel::toBudget,
                    enabled = viewModel.modeReady,
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.CONTINUE),
                )
            }
        }
        if (viewModel.tokenPickerOpen) TokenPicker(
            tokens = viewModel.allowlistTokens.map { TokenPickerItem(it.address, it.symbol, it.name) },
            title = stringResource(Res.string.copy_token_pick),
            searchLabel = stringResource(Res.string.copy_token_search),
            emptyText = stringResource(Res.string.copy_token_none),
            onSelect = { viewModel.selectToken(CopyToken(it.address, it.symbol, it.name)) },
            onDismiss = viewModel::dismissTokenPicker,
            titleTestTag = CopyTestTags.TOKEN_PICK,
            searchTestTag = CopyTestTags.TOKEN_SEARCH,
            emptyTestTag = CopyTestTags.TOKEN_NONE,
        )
    }
}

/** Screen 3 (`Copy3-Budget`) — set the total budget (= the on-chain cap; v1 fixed budget cap) + risk caveat. */
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
                // Risk / expectation caveat (non-custodial, revocable) — neutral hint, Info tone (KAN-158).
                // KAN-168 F2: short title + the sentence as description (helper) → correct banner hierarchy.
                CryptasaBanner(
                    title = stringResource(Res.string.copy_budget_caveat_title),
                    description = stringResource(Res.string.copy_budget_caveat),
                    tone = CryptasaBannerTone.Info,
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
