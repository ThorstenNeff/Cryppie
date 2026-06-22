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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.LayoutDirection
import com.tneff.cyppie.designsystem.NumberFormatProfile
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
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
import com.tneff.cyppie.feature.wallet.generated.resources.home_add_token
import com.tneff.cyppie.feature.wallet.generated.resources.home_degraded
import com.tneff.cyppie.feature.wallet.generated.resources.home_empty
import com.tneff.cyppie.feature.wallet.generated.resources.home_error
import com.tneff.cyppie.feature.wallet.generated.resources.home_nfts
import com.tneff.cyppie.feature.wallet.generated.resources.home_copy
import com.tneff.cyppie.feature.wallet.generated.resources.home_dca
import com.tneff.cyppie.feature.wallet.generated.resources.home_market
import com.tneff.cyppie.feature.wallet.generated.resources.home_portfolio
import com.tneff.cyppie.feature.wallet.generated.resources.home_strat
import com.tneff.cyppie.feature.wallet.generated.resources.home_receive
import com.tneff.cyppie.feature.wallet.generated.resources.home_refresh_cd
import com.tneff.cyppie.feature.wallet.generated.resources.home_retry
import com.tneff.cyppie.feature.wallet.generated.resources.home_title
import com.tneff.cyppie.feature.wallet.generated.resources.wallet_action_connect
import com.tneff.cyppie.feature.wallet.generated.resources.wallet_action_send
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
    onReceive: () -> Unit = {},
    onSend: () -> Unit = {},
    onAddToken: () -> Unit = {},
    onNfts: () -> Unit = {},
    onPortfolio: () -> Unit = {},
    onConnect: () -> Unit = {},
    onMarket: () -> Unit = {},
    onDca: () -> Unit = {},
    onCopy: () -> Unit = {},
    onStrat: () -> Unit = {},
    viewModel: WalletHomeViewModel = koinViewModel(),
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val state = viewModel.uiState
    // Locale-aware amount formatting (KAN-120/KAN-116), float-free.
    val languageTag = Locale.current.toLanguageTag()
    val numberProfile = remember(languageTag) { NumberFormatProfile.forLanguageTag(languageTag) }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        // H5 (KAN-113): clear the status bar — Home has its own header (not CryptasaTopAppBar, which
        // already insets), so apply the same statusBars inset here.
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = spacing.xl),
        ) {
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

            // Send (KAN-110) — the primary action; full-width above the secondary entry points.
            CryptasaButton(
                text = stringResource(Res.string.wallet_action_send),
                onClick = onSend,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm).testTag(WalletTestTags.HOME_SEND),
            )

            // Portfolio overview (KAN-114 / PF-1) — full-width secondary entry.
            CryptasaButton(
                text = stringResource(Res.string.home_portfolio),
                onClick = onPortfolio,
                style = CryptasaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.HOME_PORTFOLIO),
            )

            // WalletConnect entry (KAN-126) — full-width secondary; opens pairing.
            CryptasaButton(
                text = stringResource(Res.string.wallet_action_connect),
                onClick = onConnect,
                style = CryptasaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.HOME_CONNECT),
            )

            // Market overview entry (KAN-131, MD-3) — full-width secondary.
            CryptasaButton(
                text = stringResource(Res.string.home_market),
                onClick = onMarket,
                style = CryptasaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag("home_market"),
            )

            // DCA / Auto-Invest entry (PRD-05 Ph1, KAN-138) — full-width secondary. Label is pre-i18n
            // (home_dca = UX follow-up, same as home_market was); the destination is JWT-gated (SIWE).
            CryptasaButton(
                text = stringResource(Res.string.home_dca),
                onClick = onDca,
                style = CryptasaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag("home_dca"),
            )

            // Copy-Trading / Follow-Trader entry (PRD-06, KAN-155) — full-width secondary; native-only (web entry hidden).
            CryptasaButton(
                text = stringResource(Res.string.home_copy),
                onClick = onCopy,
                style = CryptasaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag("home_copy"),
            )

            // Smart-Strategies entry (PRD-07b, KAN-166) — full-width secondary; native-only.
            CryptasaButton(
                text = stringResource(Res.string.home_strat),
                onClick = onStrat,
                style = CryptasaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag("home_strat"),
            )

            // Entry points (KAN-103): receive is always available (even on an empty wallet, to fund it).
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                CryptasaButton(
                    text = stringResource(Res.string.home_receive),
                    onClick = onReceive,
                    style = CryptasaButtonStyle.Secondary,
                    modifier = Modifier.weight(1f).testTag(WalletTestTags.HOME_RECEIVE),
                )
                CryptasaButton(
                    text = stringResource(Res.string.home_add_token),
                    onClick = onAddToken,
                    style = CryptasaButtonStyle.Secondary,
                    modifier = Modifier.weight(1f).testTag(WalletTestTags.HOME_ADD_TOKEN),
                )
                CryptasaButton(
                    text = stringResource(Res.string.home_nfts),
                    onClick = onNfts,
                    style = CryptasaButtonStyle.Secondary,
                    modifier = Modifier.weight(1f).testTag(WalletTestTags.HOME_NFTS),
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
                    state.balances.forEach { ChainSection(it, numberProfile) }
                }
            }
        }
    }
}

@Composable
private fun ChainSection(balances: ChainBalances, profile: NumberFormatProfile) {
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
            amount = formatTokenAmount(balances.native, NATIVE_DECIMALS, profile),
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
                    amount = formatTokenAmount(amount, meta.decimals, profile),
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
        // H4 (KAN-113): amounts are an LTR island so digits/decimals don't reorder in RTL (ar).
        LtrText(text = amount, style = CryptasaTheme.typography.body, color = colors.onSurface)
    }
}

/** A Text forced LTR (KAN-113 H4) — for amounts / inline addresses that must not reorder in RTL. */
@Composable
private fun LtrText(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(text = text, style = style, color = color)
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
        // H3 (KAN-113): the selected account is marked with a check_circle (not border-only).
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            Text(text = label, style = CryptasaTheme.typography.labelSmall, color = colors.onSurface)
            if (selected) {
                Icon(
                    imageVector = CryptasaIcons.CheckCircle,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // H4 (KAN-113): the address is an LTR island (defensive Bidi-safety in ar).
        LtrText(text = address, style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** Middle-truncated EIP-55 address for compact display. */
internal fun EvmAddress.short(): String = value.let { "${it.take(6)}…${it.takeLast(4)}" }
