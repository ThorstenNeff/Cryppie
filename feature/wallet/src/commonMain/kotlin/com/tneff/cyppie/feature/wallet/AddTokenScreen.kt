package com.tneff.cyppie.feature.wallet

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.feature.wallet.generated.resources.Res
import com.tneff.cyppie.feature.wallet.generated.resources.common_close
import com.tneff.cyppie.feature.wallet.generated.resources.common_retry
import com.tneff.cyppie.feature.wallet.generated.resources.token_add_cta
import com.tneff.cyppie.feature.wallet.generated.resources.token_add_subtitle
import com.tneff.cyppie.feature.wallet.generated.resources.token_add_title
import com.tneff.cyppie.feature.wallet.generated.resources.token_contract_helper
import com.tneff.cyppie.feature.wallet.generated.resources.token_contract_label
import com.tneff.cyppie.feature.wallet.generated.resources.token_contract_placeholder
import com.tneff.cyppie.feature.wallet.generated.resources.token_err_invalid
import com.tneff.cyppie.feature.wallet.generated.resources.token_err_network_body
import com.tneff.cyppie.feature.wallet.generated.resources.token_err_network_title
import com.tneff.cyppie.feature.wallet.generated.resources.token_err_not_erc20
import com.tneff.cyppie.feature.wallet.generated.resources.token_resolved_detail
import com.tneff.cyppie.walletcore.Erc20Token
import com.tneff.cyppie.walletcore.EvmChain
import com.tneff.cyppie.walletcore.TokenResolution
import org.jetbrains.compose.resources.stringResource

private sealed interface AddTokenUiState {
    data object Idle : AddTokenUiState
    data object InvalidAddress : AddTokenUiState
    data object Resolving : AddTokenUiState
    data class Resolved(val token: Erc20Token) : AddTokenUiState
    data object NotErc20 : AddTokenUiState
    data object NetworkError : AddTokenUiState
}

/**
 * D4 — Add an ERC-20 token by contract address (KAN-49/KAN-83). EIP-55 is validated locally
 * ([EvmAddress.parse]); a valid address is resolved via [onResolve] (`symbol`/`decimals`) into the
 * Resolved / NotErc20 / NetworkError states. The Add CTA stays disabled until an ERC-20 resolves.
 * Stateless wrt the wallet: [onResolve] is wired to `WalletRepository.resolveErc20`.
 *
 * Duplicate detection is **not** done here (L3): [onAdd] is the host's hook to persist the token, and
 * the host de-duplicates against the already-added set (a token may legitimately re-resolve). The
 * screen only resolves + emits.
 */
@Composable
fun AddTokenScreen(
    chain: EvmChain,
    onResolve: suspend (EvmAddress, EvmChain) -> TokenResolution,
    onAdd: (Erc20Token) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing

    var contract by rememberSaveable { mutableStateOf("") }
    var retryTick by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<AddTokenUiState>(AddTokenUiState.Idle) }

    LaunchedEffect(contract, retryTick) {
        val text = contract.trim()
        if (text.isEmpty()) {
            state = AddTokenUiState.Idle
            return@LaunchedEffect
        }
        val address = runCatching { EvmAddress.parse(text) }.getOrNull()
        if (address == null) {
            // Only flag invalid once it's a full-length address — don't shout while still typing.
            state = if (text.length >= 42) AddTokenUiState.InvalidAddress else AddTokenUiState.Idle
            return@LaunchedEffect
        }
        state = AddTokenUiState.Resolving
        state = when (val resolution = onResolve(address, chain)) {
            is TokenResolution.Resolved -> AddTokenUiState.Resolved(resolution.token)
            TokenResolution.NotErc20 -> AddTokenUiState.NotErc20
            TokenResolution.NetworkError -> AddTokenUiState.NetworkError
        }
    }

    val current = state
    val errorText = when (current) {
        AddTokenUiState.InvalidAddress -> stringResource(Res.string.token_err_invalid)
        AddTokenUiState.NotErc20 -> stringResource(Res.string.token_err_not_erc20)
        else -> null
    }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = spacing.xl)) {
            CryptasaTopAppBar(
                title = stringResource(Res.string.token_add_title),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.common_close),
            )

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    text = stringResource(Res.string.token_add_subtitle),
                    style = CryptasaTheme.typography.body,
                    color = colors.onSurfaceVariant,
                )

                // Contract address is an LTR island even in RTL locales (spec).
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    CryptasaTextField(
                        value = contract,
                        onValueChange = { contract = it },
                        label = stringResource(Res.string.token_contract_label),
                        placeholder = stringResource(Res.string.token_contract_placeholder),
                        helperText = stringResource(Res.string.token_contract_helper),
                        errorText = errorText,
                        keyboardType = KeyboardType.Ascii,
                        autoCorrect = false,
                        capitalization = KeyboardCapitalization.None,
                        errorTestTag = WalletTestTags.TOKEN_ERROR,
                        modifier = Modifier.testTag(WalletTestTags.TOKEN_CONTRACT_FIELD),
                    )
                }

                // Resolution outcome — announced to screen readers as it changes (L2 a11y).
                Column(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                    when (current) {
                        AddTokenUiState.Resolving ->
                            CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(28.dp))
                        is AddTokenUiState.Resolved -> ResolvedCard(current.token)
                        AddTokenUiState.NetworkError -> CryptasaBanner(
                            title = stringResource(Res.string.token_err_network_title),
                            description = stringResource(Res.string.token_err_network_body),
                            tone = CryptasaBannerTone.Danger,
                            actionText = stringResource(Res.string.common_retry),
                            onActionClick = { retryTick++ },
                            modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.TOKEN_NETWORK),
                        )
                        else -> Unit
                    }
                }
            }

            CryptasaButton(
                text = stringResource(Res.string.token_add_cta),
                onClick = { (state as? AddTokenUiState.Resolved)?.let { onAdd(it.token) } },
                enabled = current is AddTokenUiState.Resolved,
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md).testTag(WalletTestTags.TOKEN_ADD),
            )
        }
    }
}

@Composable
private fun ResolvedCard(token: Erc20Token) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val radius = CryptasaTheme.radius
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(radius.md)).background(colors.surfaceVariant)
            .padding(spacing.md).testTag(WalletTestTags.TOKEN_RESOLVED),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(imageVector = CryptasaIcons.Check, contentDescription = null, tint = colors.success)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = token.symbol, style = CryptasaTheme.typography.label, color = colors.onSurface)
            Text(
                text = stringResource(Res.string.token_resolved_detail, token.symbol, token.decimals),
                style = CryptasaTheme.typography.helper,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
