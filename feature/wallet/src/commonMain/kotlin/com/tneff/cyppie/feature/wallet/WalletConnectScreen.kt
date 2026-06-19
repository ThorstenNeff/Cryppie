package com.tneff.cyppie.feature.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.intl.Locale
import com.tneff.cyppie.designsystem.BidiSanitizer
import com.tneff.cyppie.designsystem.NumberFormatProfile
import com.tneff.cyppie.send.DecodedCall
import com.tneff.cyppie.walletconnect.SendTransactionParams
import com.tneff.cyppie.walletcore.TokenCatalog
import com.tneff.cyppie.designsystem.components.DisclosureRow
import com.tneff.cyppie.designsystem.components.LtrIsland
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.components.SegmentedControl
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.walletconnect.WcDecodedRequest
import com.tneff.cyppie.walletconnect.WcSessionProposal
import com.tneff.cyppie.walletconnect.WcSessionRequest
import com.tneff.cyppie.walletconnect.WcSigningRequest
import com.tneff.cyppie.walletconnect.WcVerify
import com.tneff.cyppie.feature.wallet.generated.resources.Res
import com.tneff.cyppie.feature.wallet.generated.resources.common_cancel
import com.tneff.cyppie.feature.wallet.generated.resources.send_auth_error
import com.tneff.cyppie.feature.wallet.generated.resources.send_auth_password
import com.tneff.cyppie.feature.wallet.generated.resources.send_auth_title
import com.tneff.cyppie.feature.wallet.generated.resources.wallet_action_connect
import com.tneff.cyppie.feature.wallet.generated.resources.wc_accounts_label
import com.tneff.cyppie.feature.wallet.generated.resources.wc_approve
import com.tneff.cyppie.feature.wallet.generated.resources.wc_chains_label
import com.tneff.cyppie.feature.wallet.generated.resources.wc_events_label
import com.tneff.cyppie.feature.wallet.generated.resources.wc_methods_label
import com.tneff.cyppie.feature.wallet.generated.resources.wc_origin_label
import com.tneff.cyppie.feature.wallet.generated.resources.wc_pairing_paste
import com.tneff.cyppie.feature.wallet.generated.resources.wc_pairing_paste_placeholder
import com.tneff.cyppie.feature.wallet.generated.resources.wc_pairing_scan
import com.tneff.cyppie.feature.wallet.generated.resources.wc_pairing_scan_hint
import com.tneff.cyppie.feature.wallet.generated.resources.wc_pairing_title
import com.tneff.cyppie.feature.wallet.generated.resources.wc_proposal_note
import com.tneff.cyppie.feature.wallet.generated.resources.wc_proposal_title
import com.tneff.cyppie.feature.wallet.generated.resources.wc_reject
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_fee
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_gas
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_maxfee
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_network
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_nonce
import com.tneff.cyppie.feature.wallet.generated.resources.send_disclosure_to
import com.tneff.cyppie.feature.wallet.generated.resources.wc_req_approve_send
import com.tneff.cyppie.feature.wallet.generated.resources.wc_req_approve_sign
import com.tneff.cyppie.feature.wallet.generated.resources.wc_req_method
import com.tneff.cyppie.feature.wallet.generated.resources.wc_req_raw
import com.tneff.cyppie.feature.wallet.generated.resources.wc_send_amount_label
import com.tneff.cyppie.feature.wallet.generated.resources.wc_req_sign_title
import com.tneff.cyppie.feature.wallet.generated.resources.wc_req_typed_title
import com.tneff.cyppie.feature.wallet.generated.resources.wc_sign_message_label
import com.tneff.cyppie.feature.wallet.generated.resources.wc_signer_label
import com.tneff.cyppie.feature.wallet.generated.resources.wc_sign_warning
import com.tneff.cyppie.feature.wallet.generated.resources.wc_typed_domain
import com.tneff.cyppie.feature.wallet.generated.resources.wc_typed_message
import com.tneff.cyppie.feature.wallet.generated.resources.wc_typed_warning
import com.tneff.cyppie.feature.wallet.generated.resources.wc_verify_invalid
import com.tneff.cyppie.feature.wallet.generated.resources.wc_verify_unknown
import com.tneff.cyppie.feature.wallet.generated.resources.wc_verify_verified
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jetbrains.compose.resources.stringResource

private enum class WcPairTab { Scan, Paste }

private val typedDataJson = Json { ignoreUnknownKeys = true }

/**
 * KAN-126 — the WalletConnect flow host. [WcUiState.Idle]→Pairing; proposal→Session-Approval (Et.2);
 * request→Request-Disclosure (Et.3a sign-now). FLAG_SECURE on every WC frame. All dApp-controlled text
 * is BidiSanitizer-sanitized (KAN-122); addresses/hex/payload render as LTR islands. No blind-signing.
 */
@Composable
fun WalletConnectRoot(viewModel: WalletConnectViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    SecureScreenEffect()
    when (val state = viewModel.uiState) {
        is WcUiState.Idle -> WcPairingScreen(viewModel, onBack = onExit, modifier)
        is WcUiState.Proposal -> WcProposalScreen(viewModel, state.proposal, onBack = onExit, modifier)
        is WcUiState.Request -> WcRequestScreen(viewModel, state.request, state.decoded, onBack = onExit, modifier)
    }
}

@Composable
private fun WcPairingScreen(viewModel: WalletConnectViewModel, onBack: () -> Unit, modifier: Modifier) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    var tab by remember { mutableStateOf(WcPairTab.Paste) }
    var uri by remember { mutableStateOf("") }
    val scanLabel = stringResource(Res.string.wc_pairing_scan)
    val pasteLabel = stringResource(Res.string.wc_pairing_paste)

    Scaffolded(title = stringResource(Res.string.wc_pairing_title), onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(vertical = spacing.md).testTag(WalletTestTags.WC_PAIRING),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            SegmentedControl(
                options = listOf(WcPairTab.Scan, WcPairTab.Paste),
                selected = tab,
                onSelect = { tab = it },
                label = { if (it == WcPairTab.Scan) scanLabel else pasteLabel },
                optionTestTag = { if (it == WcPairTab.Scan) WalletTestTags.WC_PAIRING_SCAN else WalletTestTags.WC_PAIRING_PASTE },
            )
            when (tab) {
                WcPairTab.Scan -> Text(
                    text = stringResource(Res.string.wc_pairing_scan_hint),
                    style = CryptasaTheme.typography.body,
                    color = colors.onSurfaceVariant,
                )
                WcPairTab.Paste -> {
                    CryptasaTextField(
                        value = uri,
                        onValueChange = { uri = it },
                        label = stringResource(Res.string.wc_pairing_paste_placeholder),
                        keyboardType = KeyboardType.Uri,
                        autoCorrect = false,
                        capitalization = KeyboardCapitalization.None,
                        modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.WC_PAIRING_INPUT),
                    )
                    CryptasaButton(
                        text = stringResource(Res.string.wallet_action_connect),
                        onClick = { viewModel.pair(uri) },
                        enabled = uri.isNotBlank() && !viewModel.busy,
                        modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.WC_PAIRING_CONNECT),
                    )
                    if (viewModel.busy) BusyRing()
                }
            }
            WcErrorBanner(viewModel.error)
        }
    }
}

@Composable
private fun WcProposalScreen(
    viewModel: WalletConnectViewModel,
    proposal: WcSessionProposal,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Scaffolded(title = stringResource(Res.string.wc_proposal_title), onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(vertical = spacing.md).testTag(WalletTestTags.WC_PROPOSAL),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(BidiSanitizer.sanitize(proposal.dapp.name), style = CryptasaTheme.typography.titleLarge, color = colors.onSurface)
            WcScopeRow(stringResource(Res.string.wc_origin_label), BidiSanitizer.sanitize(proposal.dapp.url), ltr = true)
            VerifyBadge(proposal.dapp.verify)
            WcScopeRow(stringResource(Res.string.wc_chains_label), proposal.chains.joinToString { BidiSanitizer.sanitize(it) })
            if (viewModel.sharedAccountLabels.isNotEmpty()) {
                WcScopeRow(stringResource(Res.string.wc_accounts_label), viewModel.sharedAccountLabels.joinToString(), ltr = true)
            }
            WcScopeRow(stringResource(Res.string.wc_methods_label), proposal.methods.joinToString { BidiSanitizer.sanitize(it) })
            if (proposal.events.isNotEmpty()) {
                WcScopeRow(stringResource(Res.string.wc_events_label), proposal.events.joinToString { BidiSanitizer.sanitize(it) })
            }
            Text(stringResource(Res.string.wc_proposal_note), style = CryptasaTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            WcErrorBanner(viewModel.error)
            CryptasaButton(
                text = stringResource(Res.string.wc_approve),
                onClick = { viewModel.approveSession(proposal) },
                enabled = !viewModel.busy,
                modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.WC_APPROVE),
            )
            CryptasaButton(
                text = stringResource(Res.string.wc_reject),
                onClick = { viewModel.rejectSession(proposal) },
                enabled = !viewModel.busy,
                style = CryptasaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.WC_REJECT),
            )
            if (viewModel.busy) BusyRing()
        }
    }
}

/**
 * Request-Disclosure (SPEC_WC §3, Et.3a sign-now). Header = origin + verifyContext + the signing
 * account; body = the full human-readable payload (personal_sign message / EIP-712 domain+message tree;
 * SendTx → Et.3b placeholder). "Sign" → re-auth panel (password + biometric) → sign over a fresh source.
 */
@Composable
private fun WcRequestScreen(
    viewModel: WalletConnectViewModel,
    request: WcSessionRequest,
    decoded: WcDecodedRequest,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val title = when (decoded) {
        is WcDecodedRequest.SignNow -> when (decoded.request) {
            is WcSigningRequest.SignTypedDataV4 -> stringResource(Res.string.wc_req_typed_title)
            else -> stringResource(Res.string.wc_req_sign_title)
        }
        is WcDecodedRequest.SendTransaction -> stringResource(Res.string.wc_req_method, request.method)
    }
    Scaffolded(title = title, onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(vertical = spacing.md).testTag(WalletTestTags.WC_REQUEST),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // Context header (SPEC §3): origin + verifyContext + the signing account + the chain. The
            // signing account (M2) is the full address as an LTR island — essential disclosure for any
            // signature; consistent with the Send-Confirm `from` row.
            WcScopeRow(stringResource(Res.string.wc_origin_label), BidiSanitizer.sanitize(request.dapp.url), ltr = true)
            VerifyBadge(request.dapp.verify)
            val signerAddress = when (decoded) {
                is WcDecodedRequest.SignNow -> decoded.request.address.value
                is WcDecodedRequest.SendTransaction -> decoded.params.from.value
            }
            WcScopeRow(stringResource(Res.string.wc_signer_label), BidiSanitizer.sanitize(signerAddress), ltr = true)
            WcScopeRow(stringResource(Res.string.wc_chains_label), BidiSanitizer.sanitize(request.chainId), ltr = true)

            // M1 (review, sign-what-you-saw): EIP-712 is parsed ONCE here; the same parse drives the
            // disclosure AND the sign-gate. If it fails to parse, the disclosure can't be shown → signing
            // is blocked (not merely warned), so we never sign a payload the user couldn't see.
            val signReq = (decoded as? WcDecodedRequest.SignNow)?.request
            val typedRoot = (signReq as? WcSigningRequest.SignTypedDataV4)?.let { parseTypedData(it.typedDataJson) }
            val isSendTx = decoded is WcDecodedRequest.SendTransaction
            val disclosable = when (decoded) {
                is WcDecodedRequest.SignNow -> when (decoded.request) {
                    is WcSigningRequest.PersonalSign -> true
                    is WcSigningRequest.SignTypedDataV4 -> typedRoot != null
                    is WcSigningRequest.SendTransaction -> false
                }
                // Et.3b: ready once prepare (complete + #3/#4 bind) succeeded; signs exactly the prepared tx.
                is WcDecodedRequest.SendTransaction -> viewModel.preparedSend != null
            }

            when (decoded) {
                is WcDecodedRequest.SignNow -> when (val req = decoded.request) {
                    is WcSigningRequest.PersonalSign -> PersonalSignBody(req)
                    is WcSigningRequest.SignTypedDataV4 -> TypedDataBody(req, typedRoot)
                    is WcSigningRequest.SendTransaction -> Text(stringResource(Res.string.wc_req_method, request.method))
                }
                is WcDecodedRequest.SendTransaction -> SendTxBody(viewModel, decoded.params)
            }

            WcErrorBanner(viewModel.error)

            if (viewModel.authorizing) {
                WcAuthorizePanel(viewModel, sendTx = isSendTx)
            } else {
                CryptasaButton(
                    text = stringResource(if (isSendTx) Res.string.wc_req_approve_send else Res.string.wc_req_approve_sign),
                    onClick = { viewModel.startAuthorize() },
                    enabled = disclosable && !viewModel.busy,
                    modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.WC_REQ_APPROVE),
                )
            }
        }
    }
}

@Composable
private fun PersonalSignBody(req: WcSigningRequest.PersonalSign) {
    val colors = CryptasaTheme.colors
    // Render the decoded message as UTF-8 if it's printable text, else as 0x-hex (LTR island).
    val text = req.message.decodeToString()
    val isText = req.message.isNotEmpty() && text.none { it.isISOControl() && it != '\n' && it != '\t' && it != '\r' }
    val display = if (isText) BidiSanitizer.sanitize(text) else "0x" + Hex.encode(req.message)
    WcSectionLabel(stringResource(Res.string.wc_sign_message_label))
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp)
            .clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant)
            .padding(CryptasaTheme.spacing.md).verticalScroll(rememberScrollState())
            .testTag(WalletTestTags.WC_SIGN_MESSAGE),
    ) {
        if (isText) {
            Text(display, style = CryptasaTheme.typography.body, color = colors.onSurface)
        } else {
            LtrIsland { Text(display, style = CryptasaTheme.typography.body, color = colors.onSurface) }
        }
    }
    WcWarning(stringResource(Res.string.wc_sign_warning))
}

/** Parse the typed-data JSON for display; null → can't disclose → the caller blocks signing (M1). */
private fun parseTypedData(json: String): JsonObject? =
    runCatching { typedDataJson.parseToJsonElement(json).jsonObject }.getOrNull()

@Composable
private fun TypedDataBody(req: WcSigningRequest.SignTypedDataV4, root: JsonObject?) {
    if (root == null) {
        // Parse failed → the disclosure can't be rendered; the Sign button is disabled (M1). Show why.
        WcWarning(stringResource(Res.string.wc_typed_warning))
        return
    }
    val domain = root["domain"] as? JsonObject
    val message = root["message"]
    WcSectionLabel(stringResource(Res.string.wc_typed_domain))
    if (domain != null) JsonTree(domain, depth = 0) else Text("—", color = CryptasaTheme.colors.onSurfaceVariant)
    WcSectionLabel(stringResource(Res.string.wc_typed_message))
    if (message != null) JsonTree(message, depth = 0) else Text("—", color = CryptasaTheme.colors.onSurfaceVariant)
    WcWarning(stringResource(Res.string.wc_typed_warning))
}

/** Recursive label→value tree for EIP-712 domain/message (no blind-signing — every field is shown). */
@Composable
private fun JsonTree(element: kotlinx.serialization.json.JsonElement, depth: Int) {
    val colors = CryptasaTheme.colors
    val indent = (depth * 12).dp
    when (element) {
        is JsonObject -> Column(modifier = Modifier.padding(start = indent), verticalArrangement = Arrangement.spacedBy(CryptasaTheme.spacing.xs)) {
            element.forEach { (key, value) ->
                if (value is JsonPrimitive) {
                    LeafRow(BidiSanitizer.sanitize(key), BidiSanitizer.sanitize(value.content))
                } else {
                    Text(BidiSanitizer.sanitize(key), style = CryptasaTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    JsonTree(value, depth + 1)
                }
            }
        }
        is JsonArray -> Column(modifier = Modifier.padding(start = indent)) { element.forEach { JsonTree(it, depth + 1) } }
        is JsonPrimitive -> LeafRow("", BidiSanitizer.sanitize(element.content))
    }
}

@Composable
private fun LeafRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CryptasaTheme.spacing.sm)) {
        if (label.isNotEmpty()) Text("$label:", style = CryptasaTheme.typography.bodySmall, color = CryptasaTheme.colors.onSurfaceVariant)
        LtrIsland { Text(value, style = CryptasaTheme.typography.bodySmall, color = CryptasaTheme.colors.onSurface) }
    }
}

/**
 * Et.3b — SendTx disclosure: the COMPLETED+bound tx (no TOCTOU). While preparing, a spinner; once
 * prepared, the tx fields (reusing the Send-Confirm [DisclosureRow]) + a `wc_req_raw` expandable with
 * the raw to/value/data. The signer (`from`) is already in the header (M2). All amounts locale-formatted.
 */
@Composable
private fun SendTxBody(viewModel: WalletConnectViewModel, params: SendTransactionParams) {
    val colors = CryptasaTheme.colors
    val prepared = viewModel.preparedSend
    if (viewModel.preparing) { BusyRing(); return }
    if (prepared == null) return // failure surfaced by WcErrorBanner
    val d = prepared.disclosure
    val profile = remember { NumberFormatProfile.forLanguageTag(Locale.current.toLanguageTag()) }
    // Recipient: the decoded ERC-20 recipient when present, else the tx `to` (native). Honest either way;
    // the raw section below shows the literal to/value/data.
    val recipient = (d.call as? DecodedCall.Erc20Transfer)?.recipient?.value ?: d.to.value
    DisclosureRow(stringResource(Res.string.send_disclosure_to), BidiSanitizer.sanitize(recipient), ltr = true, valueTestTag = WalletTestTags.SEND_DISCLOSURE_TO)
    DisclosureRow(stringResource(Res.string.send_disclosure_network), d.chain.displayName)
    // Amount (review Medium, no-blind-signing): for an ERC-20 `transfer` the token AMOUNT is in the
    // calldata, not `value` — decode + show it. Resolve decimals/symbol from the curated TokenCatalog by
    // the contract (`d.to`); an unknown token shows RAW integer units + the contract (never mis-scaled).
    val amountText = when (val c = d.call) {
        is DecodedCall.Erc20Transfer -> {
            val token = TokenCatalog.forChain(d.chain).firstOrNull { it.address == d.to }
            if (token != null) "${formatTokenAmount(c.amount, token.decimals, profile)} ${token.symbol}"
            else "${formatTokenAmount(c.amount, 0, profile)} units (${d.to.short()})"
        }
        else -> "${formatTokenAmount(d.value, 18, profile)} ETH" // native ETH value
    }
    DisclosureRow(stringResource(Res.string.wc_send_amount_label), amountText, ltr = true, valueTestTag = WalletTestTags.WC_SEND_AMOUNT)
    DisclosureRow(stringResource(Res.string.send_disclosure_nonce), d.nonce.toLong().toString(), ltr = true)
    DisclosureRow(stringResource(Res.string.send_disclosure_gas), d.gasLimit.toLong().toString(), ltr = true)
    DisclosureRow(stringResource(Res.string.send_disclosure_maxfee), "${formatTokenAmount(d.maxFeePerGas, 9, profile)} gwei", ltr = true)
    DisclosureRow(stringResource(Res.string.send_disclosure_fee), "${formatTokenAmount(d.maxNetworkFee, 18, profile)} ETH", ltr = true)

    var rawShown by remember { mutableStateOf(false) }
    CryptasaButton(
        text = stringResource(Res.string.wc_req_raw),
        onClick = { rawShown = !rawShown },
        style = CryptasaButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.WC_REQ_RAW),
    )
    if (rawShown) {
        val raw = "to: ${params.to?.value ?: "—"}\nvalue: ${params.value.toLong()}\ndata: 0x${Hex.encode(params.data)}"
        LtrIsland { Text(raw, style = CryptasaTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
}

/** Re-auth panel (mirrors Send): biometric auto-prompt on entry; password is the always-on fallback. */
@Composable
private fun WcAuthorizePanel(viewModel: WalletConnectViewModel, sendTx: Boolean) {
    val biometric = rememberBiometricSign()
    LaunchedEffect(Unit) {
        if (biometric.available()) biometric.authorize()?.let(viewModel::submitBiometricSource)
    }
    Text(stringResource(Res.string.send_auth_title), style = CryptasaTheme.typography.body, color = CryptasaTheme.colors.onSurfaceVariant)
    CryptasaTextField(
        value = viewModel.authPassword,
        onValueChange = viewModel::updateAuthPassword,
        label = stringResource(Res.string.send_auth_password),
        keyboardType = KeyboardType.Password,
        autoCorrect = false,
        capitalization = KeyboardCapitalization.None,
        visualTransformation = PasswordVisualTransformation(),
        errorText = if (viewModel.authError) stringResource(Res.string.send_auth_error) else null,
        modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.WC_AUTH_PASSWORD),
    )
    CryptasaButton(
        text = stringResource(if (sendTx) Res.string.wc_req_approve_send else Res.string.wc_req_approve_sign),
        onClick = viewModel::authorizeAndSign,
        enabled = viewModel.authPassword.isNotEmpty() && !viewModel.busy,
        modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.WC_AUTH_SUBMIT),
    )
    CryptasaButton(
        text = stringResource(Res.string.common_cancel),
        onClick = viewModel::cancelAuthorize,
        enabled = !viewModel.busy,
        style = CryptasaButtonStyle.Secondary,
        modifier = Modifier.fillMaxWidth(),
    )
    if (viewModel.busy) BusyRing()
}

/** verifyContext badge — icon + text + colour (never colour alone; FR-3 / WCAG-AA). Matches the typed
 * [WcVerify] (normalised at the controller edge — review M1), so no raw SDK-token matching in the UI. */
@Composable
private fun VerifyBadge(verify: WcVerify) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val (icon: ImageVector, tint: Color, text: String) = when (verify) {
        WcVerify.Verified -> Triple(CryptasaIcons.CheckCircle, colors.success, stringResource(Res.string.wc_verify_verified))
        WcVerify.Invalid -> Triple(CryptasaIcons.Error, colors.danger, stringResource(Res.string.wc_verify_invalid))
        WcVerify.Unknown -> Triple(CryptasaIcons.Warning, colors.warning, stringResource(Res.string.wc_verify_unknown))
    }
    Row(
        modifier = Modifier.testTag(WalletTestTags.WC_VERIFY),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(text, style = CryptasaTheme.typography.bodySmall, color = tint)
    }
}

@Composable
private fun WcScopeRow(label: String, value: String, ltr: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(CryptasaTheme.spacing.xs)) {
        WcSectionLabel(label)
        if (ltr) LtrIsland { Text(value, style = CryptasaTheme.typography.body, color = CryptasaTheme.colors.onSurface) }
        else Text(value, style = CryptasaTheme.typography.body, color = CryptasaTheme.colors.onSurface)
    }
}

@Composable
private fun WcSectionLabel(label: String) =
    Text(label, style = CryptasaTheme.typography.labelSmall, color = CryptasaTheme.colors.onSurfaceVariant)

@Composable
private fun WcWarning(text: String) =
    CryptasaBanner(title = text, tone = CryptasaBannerTone.Warning, modifier = Modifier.fillMaxWidth())

@Composable
private fun WcErrorBanner(error: String?) {
    error?.let {
        CryptasaBanner(
            title = BidiSanitizer.sanitize(it),
            tone = CryptasaBannerTone.Danger,
            modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.WC_ERROR),
        )
    }
}

@Composable
private fun BusyRing() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ProgressRing(diameter = 28.dp) }
}
