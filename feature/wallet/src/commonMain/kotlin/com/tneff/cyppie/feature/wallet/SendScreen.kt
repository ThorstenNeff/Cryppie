package com.tneff.cyppie.feature.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.DisclosureRow
import com.tneff.cyppie.designsystem.components.LtrIsland
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.NumberFormatProfile
import com.tneff.cyppie.designsystem.BidiSanitizer
import com.tneff.cyppie.designsystem.components.SegmentedControl
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.send.PreparedSend
import com.tneff.cyppie.feature.wallet.generated.resources.Res
import com.tneff.cyppie.feature.wallet.generated.resources.common_cancel
import com.tneff.cyppie.feature.wallet.generated.resources.send_amount_label
import com.tneff.cyppie.feature.wallet.generated.resources.send_auth_error
import com.tneff.cyppie.feature.wallet.generated.resources.send_auth_password
import com.tneff.cyppie.feature.wallet.generated.resources.send_auth_subtitle
import com.tneff.cyppie.feature.wallet.generated.resources.send_auth_title
import com.tneff.cyppie.feature.wallet.generated.resources.send_asset_select_title
import com.tneff.cyppie.feature.wallet.generated.resources.send_available
import com.tneff.cyppie.feature.wallet.generated.resources.send_confirm_note
import com.tneff.cyppie.feature.wallet.generated.resources.send_continue
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_asset
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_fee
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_gas
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_maxfee
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_network
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_nonce
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_to
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_total
import com.tneff.cyppie.feature.wallet.generated.resources.send_done
import com.tneff.cyppie.feature.wallet.generated.resources.send_err_invalid_address
import com.tneff.cyppie.feature.wallet.generated.resources.send_err_insufficient
import com.tneff.cyppie.feature.wallet.generated.resources.send_err_network_body
import com.tneff.cyppie.feature.wallet.generated.resources.send_err_network_title
import com.tneff.cyppie.feature.wallet.generated.resources.send_err_rejected_body
import com.tneff.cyppie.feature.wallet.generated.resources.send_err_rejected_title
import com.tneff.cyppie.feature.wallet.generated.resources.send_explorer
import com.tneff.cyppie.feature.wallet.generated.resources.send_fee_fast
import com.tneff.cyppie.feature.wallet.generated.resources.send_fee_label
import com.tneff.cyppie.feature.wallet.generated.resources.send_fee_normal
import com.tneff.cyppie.feature.wallet.generated.resources.send_fee_slow
import com.tneff.cyppie.feature.wallet.generated.resources.send_max
import com.tneff.cyppie.feature.wallet.generated.resources.send_recipient_label
import com.tneff.cyppie.feature.wallet.generated.resources.send_recipient_placeholder
import com.tneff.cyppie.feature.wallet.generated.resources.send_resolved_preview
import com.tneff.cyppie.feature.wallet.generated.resources.send_review_title
import com.tneff.cyppie.feature.wallet.generated.resources.send_sign
import com.tneff.cyppie.feature.wallet.generated.resources.send_status_confirmed_title
import com.tneff.cyppie.feature.wallet.generated.resources.send_status_failed_body
import com.tneff.cyppie.feature.wallet.generated.resources.send_status_failed_title
import com.tneff.cyppie.feature.wallet.generated.resources.send_status_pending_body
import com.tneff.cyppie.feature.wallet.generated.resources.send_status_pending_title
import com.tneff.cyppie.feature.wallet.generated.resources.send_title
import com.tneff.cyppie.feature.wallet.generated.resources.wc_signer_label
import org.jetbrains.compose.resources.stringResource

/**
 * KAN-110 — the Send flow host. Asset → Form → **Confirm (full disclosure of the prepared tx)** → sign
 * → Status, over [SendViewModel]/[com.tneff.cyppie.send.SendOrchestrator]. No blind-signing; amounts /
 * addresses / hashes / nonce / gas render as LTR islands (even in RTL). FLAG_SECURE on Form/Confirm/
 * Status. Non-web by module (the whole `:feature:wallet` is android/ios/jvm — web entry is hidden).
 */
@Composable
fun SendFlow(viewModel: SendViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    when (viewModel.step) {
        SendStep.AssetSelect -> SendAssetSelect(viewModel, onBack = onExit, modifier)
        SendStep.Form -> SendForm(viewModel, onBack = viewModel::back, modifier)
        SendStep.Confirm -> SendConfirm(viewModel, onBack = viewModel::back, modifier)
        SendStep.Authorize -> SendAuthorize(viewModel, onBack = viewModel::back, modifier)
        SendStep.Status -> SendStatusScreen(viewModel, onDone = onExit, modifier)
    }
}

@Composable
private fun SendAssetSelect(viewModel: SendViewModel, onBack: () -> Unit, modifier: Modifier) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Scaffolded(title = stringResource(Res.string.send_asset_select_title), onBack = onBack, modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag(WalletTestTags.SEND_ASSET_SELECT),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            items(viewModel.assets) { asset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CryptasaTheme.radius.md))
                        .clickable { viewModel.selectAsset(asset) }
                        .testTag(WalletTestTags.sendAsset(asset.chain.displayName, asset.symbol))
                        .padding(vertical = spacing.md, horizontal = spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(BidiSanitizer.sanitize(asset.symbol), style = CryptasaTheme.typography.body, color = colors.onSurface)
                    Text(asset.chain.displayName, style = CryptasaTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SendForm(viewModel: SendViewModel, onBack: () -> Unit, modifier: Modifier) {
    SecureScreenEffect()
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val asset = viewModel.asset ?: return
    val error = viewModel.formError
    val profile = NumberFormatProfile.forLanguageTag(Locale.current.toLanguageTag()) // locale amount format (KAN-120)

    Scaffolded(title = stringResource(Res.string.send_title), onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            // Asset header (tap to change).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CryptasaTheme.radius.md))
                    .background(colors.surfaceVariant)
                    .clickable { viewModel.back() }
                    .padding(spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("${BidiSanitizer.sanitize(asset.symbol)} · ${asset.chain.displayName}", style = CryptasaTheme.typography.body, color = colors.onSurface)
                    val avail = viewModel.available
                    if (avail != null) {
                        Text(
                            stringResource(Res.string.send_available, formatTokenAmount(avail, asset.decimals, profile)),
                            style = CryptasaTheme.typography.helper,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                Icon(CryptasaIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }

            // Amount (numeric, LTR island) + Max.
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                LtrIsland {
                    CryptasaTextField(
                        value = viewModel.amount,
                        onValueChange = viewModel::updateAmount,
                        label = stringResource(Res.string.send_amount_label),
                        keyboardType = KeyboardType.Decimal,
                        errorText = (error as? SendFormError.Insufficient)?.let {
                            stringResource(Res.string.send_err_insufficient, it.maxFormatted)
                        },
                        errorTestTag = WalletTestTags.SEND_INSUFFICIENT,
                        modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.SEND_AMOUNT_FIELD),
                    )
                }
                CryptasaButton(
                    text = stringResource(Res.string.send_max),
                    onClick = viewModel::applyMax,
                    style = CryptasaButtonStyle.Secondary,
                    modifier = Modifier.testTag(WalletTestTags.SEND_MAX),
                )
            }

            // Recipient (LTR island, EIP-55).
            val showAddrError = !viewModel.recipientValid && viewModel.recipient.trim().length >= 42
            LtrIsland {
                CryptasaTextField(
                    value = viewModel.recipient,
                    onValueChange = viewModel::updateRecipient,
                    label = stringResource(Res.string.send_recipient_label),
                    placeholder = stringResource(Res.string.send_recipient_placeholder),
                    helperText = stringResource(Res.string.send_resolved_preview),
                    errorText = if (showAddrError) stringResource(Res.string.send_err_invalid_address) else null,
                    keyboardType = KeyboardType.Ascii,
                    autoCorrect = false,
                    capitalization = KeyboardCapitalization.None,
                    errorTestTag = WalletTestTags.SEND_RECIPIENT_ERROR,
                    modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.SEND_RECIPIENT_FIELD),
                )
            }

            // Fee tier (radio) + estimate, or network error.
            if (viewModel.feeUnavailable) {
                CryptasaBanner(
                    title = stringResource(Res.string.send_err_network_title),
                    description = stringResource(Res.string.send_err_network_body),
                    tone = CryptasaBannerTone.Danger,
                    modifier = Modifier.testTag(WalletTestTags.SEND_NETWORK_BANNER),
                )
            } else {
                val slow = stringResource(Res.string.send_fee_slow)
                val normal = stringResource(Res.string.send_fee_normal)
                val fast = stringResource(Res.string.send_fee_fast)
                Column(verticalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.testTag(WalletTestTags.SEND_FEE)) {
                    SegmentedControl(
                        options = SendFeeTier.entries,
                        selected = viewModel.feeTier,
                        onSelect = viewModel::selectFeeTier,
                        label = { when (it) { SendFeeTier.SLOW -> slow; SendFeeTier.NORMAL -> normal; SendFeeTier.FAST -> fast } },
                        optionTestTag = { WalletTestTags.sendFeeTier(it.name) },
                    )
                    val gwei = viewModel.feeEstimateGwei()
                    Text(
                        text = stringResource(Res.string.send_fee_label) + (gwei?.let { " · $it gwei" } ?: ""),
                        style = CryptasaTheme.typography.helper,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            if (error is SendFormError.Rejected) {
                CryptasaBanner(
                    title = stringResource(Res.string.send_err_rejected_title),
                    description = stringResource(Res.string.send_err_rejected_body),
                    tone = CryptasaBannerTone.Danger,
                )
            }

            CryptasaButton(
                text = stringResource(Res.string.send_continue),
                onClick = viewModel::continueToConfirm,
                enabled = viewModel.canContinue && !viewModel.preparing,
                modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.SEND_CONTINUE),
            )
            if (viewModel.preparing) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ProgressRing(diameter = 28.dp) }
            }
        }
    }
}

@Composable
private fun SendConfirm(viewModel: SendViewModel, onBack: () -> Unit, modifier: Modifier) {
    SecureScreenEffect()
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val asset = viewModel.asset ?: return
    val prepared = viewModel.prepared ?: return
    val d = prepared.disclosure
    val isNative = asset.token == null
    val total = if (isNative) d.value + d.maxNetworkFee else d.maxNetworkFee
    val profile = NumberFormatProfile.forLanguageTag(Locale.current.toLanguageTag()) // locale amount format (KAN-120)

    Scaffolded(title = stringResource(Res.string.send_review_title), onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            // Amount (big, LTR) — from the prepared tx, not the form state (single source of truth, L1).
            LtrIsland {
                Text(
                    "${formatTokenAmount(viewModel.disclosedAmount(prepared), asset.decimals, profile)} ${BidiSanitizer.sanitize(asset.symbol)}",
                    style = CryptasaTheme.typography.titleLarge,
                    color = colors.onSurface,
                )
            }

            // Disclosure of the completed tx (Guardrail #1).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CryptasaTheme.radius.lg))
                    .background(colors.surfaceVariant)
                    .padding(spacing.lg)
                    .testTag(WalletTestTags.SEND_DISCLOSURE),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                // KAN-122: bidi-sanitize attacker-controlled disclosure strings — a hostile ERC-20
                // symbol (or, defensively, the recipient) must not spoof via RTL-override/zero-width.
                DisclosureRow(stringResource(Res.string.send_disclosure_asset), BidiSanitizer.sanitize(asset.symbol), valueTestTag = WalletTestTags.SEND_DISCLOSURE_ASSET)
                // Signing account (KAN-126 M2): the `from` shown consistently with the WC-Request disclosure.
                DisclosureRow(stringResource(Res.string.wc_signer_label), prepared.disclosure.from.value, ltr = true, valueTestTag = WalletTestTags.SEND_DISCLOSURE_FROM)
                DisclosureRow(stringResource(Res.string.send_disclosure_to), BidiSanitizer.sanitize(viewModel.disclosedRecipient(prepared).value), ltr = true, valueTestTag = WalletTestTags.SEND_DISCLOSURE_TO)
                DisclosureRow(stringResource(Res.string.send_disclosure_network), d.chain.displayName, valueTestTag = WalletTestTags.SEND_DISCLOSURE_NETWORK)
                DisclosureRow(stringResource(Res.string.send_disclosure_nonce), d.nonce.toLong().toString(), ltr = true, valueTestTag = WalletTestTags.SEND_DISCLOSURE_NONCE)
                DisclosureRow(stringResource(Res.string.send_disclosure_gas), d.gasLimit.toLong().toString(), ltr = true, valueTestTag = WalletTestTags.SEND_DISCLOSURE_GAS)
                DisclosureRow(stringResource(Res.string.send_disclosure_maxfee), "${formatTokenAmount(d.maxFeePerGas, 9, profile)} gwei", ltr = true, valueTestTag = WalletTestTags.SEND_DISCLOSURE_MAXFEE)
                DisclosureRow(stringResource(Res.string.send_disclosure_fee), "${formatTokenAmount(d.maxNetworkFee, 18, profile)} ETH", ltr = true, valueTestTag = WalletTestTags.SEND_DISCLOSURE_FEE)
                DisclosureRow(stringResource(Res.string.send_disclosure_total), "${formatTokenAmount(total, 18, profile)} ETH", ltr = true, valueTestTag = WalletTestTags.SEND_DISCLOSURE_TOTAL)
            }

            Text(
                stringResource(Res.string.send_confirm_note),
                style = CryptasaTheme.typography.helper,
                color = colors.onSurfaceVariant,
            )

            CryptasaButton(
                text = stringResource(Res.string.send_sign),
                onClick = viewModel::requestAuth,
                modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.SEND_SIGN),
            )
            CryptasaButton(
                text = stringResource(Res.string.common_cancel),
                onClick = onBack,
                style = CryptasaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Re-auth gate (ADR-0009) between disclosure and signing — fail-closed; signs only on a correct password. */
@Composable
private fun SendAuthorize(viewModel: SendViewModel, onBack: () -> Unit, modifier: Modifier) {
    SecureScreenEffect()
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    // KAN-119: on entering the gate, auto-present the biometric prompt. Success → fresh per-sign source
    // → sign (M1). Cancel/unavailable → fall through to the password field below (always-available
    // fallback). Fail-closed: only a non-null source ever signs.
    val biometric = rememberBiometricSign()
    LaunchedEffect(Unit) {
        if (biometric.available()) {
            biometric.authorize()?.let(viewModel::submitBiometricSource)
        }
    }
    Scaffolded(title = stringResource(Res.string.send_auth_title), onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            Text(
                stringResource(Res.string.send_auth_subtitle),
                style = CryptasaTheme.typography.body,
                color = colors.onSurfaceVariant,
            )
            CryptasaTextField(
                value = viewModel.authPassword,
                onValueChange = viewModel::updateAuthPassword,
                label = stringResource(Res.string.send_auth_password),
                keyboardType = KeyboardType.Password,
                autoCorrect = false,
                capitalization = KeyboardCapitalization.None,
                visualTransformation = PasswordVisualTransformation(),
                errorText = if (viewModel.authError) stringResource(Res.string.send_auth_error) else null,
                errorTestTag = WalletTestTags.SEND_AUTH_ERROR,
                modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.SEND_AUTH_PASSWORD),
            )
            CryptasaButton(
                text = stringResource(Res.string.send_sign),
                onClick = viewModel::authorizeAndSign,
                enabled = viewModel.authPassword.isNotEmpty() && !viewModel.authorizing,
                modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.SEND_AUTH_SUBMIT),
            )
            if (viewModel.authorizing) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ProgressRing(diameter = 28.dp) }
            }
        }
    }
}

@Composable
private fun SendStatusScreen(viewModel: SendViewModel, onDone: () -> Unit, modifier: Modifier) {
    SecureScreenEffect()
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val uriHandler = LocalUriHandler.current
    val chain = viewModel.prepared?.disclosure?.chain
    val status = viewModel.status

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            when (status) {
                null, is SendStatus.Pending -> {
                    val hash = (status as? SendStatus.Pending)?.txHash
                    ProgressRing(diameter = 56.dp, modifier = Modifier.testTag(WalletTestTags.SEND_STATUS_PENDING))
                    Centered(stringResource(Res.string.send_status_pending_title), CryptasaTheme.typography.titleLarge, colors.onSurface)
                    Centered(stringResource(Res.string.send_status_pending_body), CryptasaTheme.typography.body, colors.onSurfaceVariant)
                    if (hash != null && chain != null) TxHashAndExplorer(hash, chain, uriHandler)
                }
                is SendStatus.Confirmed -> {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(CryptasaTheme.radius.full)).background(colors.successSurface), contentAlignment = Alignment.Center) {
                        Icon(CryptasaIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(32.dp))
                    }
                    Centered(stringResource(Res.string.send_status_confirmed_title), CryptasaTheme.typography.titleLarge, colors.onSurface, WalletTestTags.SEND_STATUS_CONFIRMED)
                    if (chain != null) TxHashAndExplorer(status.txHash, chain, uriHandler)
                    CryptasaButton(stringResource(Res.string.send_done), onDone, modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.SEND_DONE))
                }
                is SendStatus.Failed -> {
                    val rejected = status.rejected
                    Centered(
                        stringResource(if (rejected) Res.string.send_err_rejected_title else Res.string.send_status_failed_title),
                        CryptasaTheme.typography.titleLarge, colors.danger, WalletTestTags.SEND_STATUS_FAILED,
                    )
                    Centered(
                        stringResource(if (rejected) Res.string.send_err_rejected_body else Res.string.send_status_failed_body),
                        CryptasaTheme.typography.body, colors.onSurfaceVariant,
                        if (rejected) WalletTestTags.SEND_REJECTED_DIALOG else null,
                    )
                    if (status.txHash != null && chain != null) TxHashAndExplorer(status.txHash, chain, uriHandler)
                    CryptasaButton(stringResource(Res.string.send_done), onDone, modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.SEND_DONE))
                }
            }
        }
    }
}

@Composable
private fun TxHashAndExplorer(txHash: String, chain: com.tneff.cyppie.walletcore.EvmChain, uriHandler: androidx.compose.ui.platform.UriHandler) {
    val colors = CryptasaTheme.colors
    LtrIsland {
        Text(txHash, style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    CryptasaButton(
        text = stringResource(Res.string.send_explorer),
        onClick = { uriHandler.openUri(explorerTxUrl(chain, txHash)) },
        style = CryptasaButtonStyle.Secondary,
        modifier = Modifier.testTag(WalletTestTags.SEND_EXPLORER),
    )
}

@Composable
private fun Centered(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color, testTag: String? = null) {
    Text(
        text = text,
        style = style,
        color = color,
        textAlign = TextAlign.Center,
        modifier = (if (testTag != null) Modifier.testTag(testTag) else Modifier).fillMaxWidth(),
    )
}

@Composable
internal fun Scaffolded(title: String, onBack: () -> Unit, modifier: Modifier, content: @Composable () -> Unit) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = title, onBack = onBack)
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.xl)) { content() }
        }
    }
}
