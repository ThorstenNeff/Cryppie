package com.tneff.cyppie.feature.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.foundation.clickableIcon
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.walletcore.ChainBalances
import com.tneff.cyppie.walletcore.TokenCatalog
import com.tneff.cyppie.feature.wallet.generated.resources.Res
import com.tneff.cyppie.feature.wallet.generated.resources.home_account_label
import com.tneff.cyppie.feature.wallet.generated.resources.home_accounts
import com.tneff.cyppie.feature.wallet.generated.resources.home_degraded
import com.tneff.cyppie.feature.wallet.generated.resources.home_empty
import com.tneff.cyppie.feature.wallet.generated.resources.home_error
import com.tneff.cyppie.feature.wallet.generated.resources.home_refresh_cd
import com.tneff.cyppie.feature.wallet.generated.resources.home_retry
import com.tneff.cyppie.feature.wallet.generated.resources.home_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Native asset ticker — Ethereum and Base both settle in ETH (PRD-02). */
private const val NATIVE_TICKER = "ETH"
private const val NATIVE_DECIMALS = 18

/**
 * ONB Wallet-Home (KAN-81 / D1): account switcher + per-chain native & ERC-20 balances via
 * `:walletcore` (read-only, no fiat — PRD-02). Loading/empty/error/degraded states; amounts via the
 * big-integer-safe [formatTokenAmount]. The repository is provided by app-shell DI (KAN-89).
 */
@Composable
fun WalletHomeScreen(
    viewModel: WalletHomeViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val state = viewModel.uiState

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = spacing.xl)) {
            // Header: title + refresh.
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(Res.string.home_title),
                    style = CryptasaTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
                val refreshCd = stringResource(Res.string.home_refresh_cd)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(CryptasaTheme.radius.full))
                        .clickableIcon(contentDescription = refreshCd, onClick = { viewModel.refresh() })
                        .testTag(WalletTestTags.HOME_REFRESH),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(CryptasaIcons.Refresh, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }

            // Account switcher.
            if (viewModel.accounts.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.home_accounts),
                    style = CryptasaTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = spacing.sm)
                        .testTag(WalletTestTags.HOME_ACCOUNT_LIST),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    viewModel.accounts.forEachIndexed { i, account ->
                        AccountChip(
                            label = stringResource(Res.string.home_account_label, account.index + 1),
                            address = account.address.short(),
                            selected = i == viewModel.selectedAccount,
                            onClick = { viewModel.selectAccount(i) },
                            modifier = Modifier.testTag(WalletTestTags.homeAccountItem(i)),
                        )
                    }
                }
            }

            if (state is WalletHomeUiState.Content && state.degraded) {
                CryptasaBanner(
                    title = stringResource(Res.string.home_degraded),
                    tone = CryptasaBannerTone.Warning,
                    modifier = Modifier.testTag(WalletTestTags.HOME_DEGRADED_BANNER),
                )
            }

            when (state) {
                WalletHomeUiState.Loading -> CenteredBox {
                    ProgressRing(diameter = 48.dp)
                }
                WalletHomeUiState.Empty -> CenteredBox {
                    Text(
                        text = stringResource(Res.string.home_empty),
                        style = CryptasaTheme.typography.body,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.testTag(WalletTestTags.HOME_EMPTY),
                    )
                }
                WalletHomeUiState.Error -> CenteredBox {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Text(
                            text = stringResource(Res.string.home_error),
                            style = CryptasaTheme.typography.body,
                            color = colors.danger,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag(WalletTestTags.HOME_ERROR),
                        )
                        CryptasaButton(
                            text = stringResource(Res.string.home_retry),
                            onClick = { viewModel.refresh() },
                            modifier = Modifier.testTag(WalletTestTags.HOME_RETRY),
                        )
                    }
                }
                is WalletHomeUiState.Content -> Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg),
                ) {
                    state.balances.forEach { ChainSection(it) }
                }
            }
        }
    }
}

@Composable
private fun ChainSection(balances: ChainBalances) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val chain = balances.chain.displayName
    Column(
        modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.homeChainSection(chain)),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(text = chain, style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
        BalanceRow(
            symbol = NATIVE_TICKER,
            amount = formatTokenAmount(balances.native, NATIVE_DECIMALS),
            modifier = Modifier.testTag(WalletTestTags.homeBalanceNative(chain)),
        )
        // Token rows: symbol + decimals come from the curated token metadata (KAN-89 M1 gate — never
        // NATIVE_DECIMALS, which would render USDC/USDT/6-dp tokens as "0"). Non-curated added-by-contract
        // tokens carry their own metadata once persisted (KAN-83 follow-up); skipped here rather than
        // misformatted.
        balances.tokens.forEach { (token, amount) ->
            val meta = TokenCatalog.find(token, balances.chain)
            if (meta != null) {
                BalanceRow(
                    symbol = meta.symbol,
                    amount = formatTokenAmount(amount, meta.decimals),
                    modifier = Modifier.testTag(WalletTestTags.homeBalanceToken(chain, token.value)),
                )
            }
        }
    }
}

@Composable
private fun BalanceRow(symbol: String, amount: String, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = symbol, style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant)
        Text(text = amount, style = CryptasaTheme.typography.body, color = colors.onSurface)
    }
}

@Composable
private fun AccountChip(
    label: String,
    address: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CryptasaTheme.radius.md))
            .background(if (selected) colors.primarySurface else colors.surfaceVariant)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.primary else colors.outline,
                shape = RoundedCornerShape(CryptasaTheme.radius.md),
            )
            .selectable(selected = selected, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(text = label, style = CryptasaTheme.typography.labelSmall, color = colors.onSurface)
        Text(text = address, style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Middle-truncated EIP-55 address for compact display. */
private fun EvmAddress.short(): String = value.let { "${it.take(6)}…${it.takeLast(4)}" }
