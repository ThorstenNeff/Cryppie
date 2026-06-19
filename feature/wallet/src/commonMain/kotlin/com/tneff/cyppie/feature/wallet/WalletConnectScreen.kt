package com.tneff.cyppie.feature.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.BidiSanitizer
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.components.SegmentedControl
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.walletconnect.WcSessionProposal
import com.tneff.cyppie.feature.wallet.generated.resources.Res
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
import com.tneff.cyppie.feature.wallet.generated.resources.wc_req_method
import com.tneff.cyppie.feature.wallet.generated.resources.wc_verify_invalid
import com.tneff.cyppie.feature.wallet.generated.resources.wc_verify_unknown
import com.tneff.cyppie.feature.wallet.generated.resources.wc_verify_verified
import org.jetbrains.compose.resources.stringResource

/** Pairing tabs (SPEC_WC §1). */
private enum class WcPairTab { Scan, Paste }

/**
 * KAN-126 — the WalletConnect bottom-sheet flow host. [WcUiState.Idle] → Pairing; a proposal →
 * Session-Approval (Et.2); a request → Request-Disclosure (Et.3 placeholder). FLAG_SECURE: every WC
 * frame is a signature/identity context. [WalletConnectViewModel.error] is shown in whichever screen is
 * active (review L1). All dApp-controlled text is BidiSanitizer-sanitized (KAN-122).
 */
@Composable
fun WalletConnectRoot(viewModel: WalletConnectViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    SecureScreenEffect()
    when (val state = viewModel.uiState) {
        is WcUiState.Idle -> WcPairingScreen(viewModel, onBack = onExit, modifier)
        is WcUiState.Proposal -> WcProposalScreen(viewModel, state.proposal, onBack = onExit, modifier)
        is WcUiState.Request -> WcPlaceholder(
            title = stringResource(Res.string.wc_req_method, state.request.method),
            detail = BidiSanitizer.sanitize(state.request.dapp.name),
            error = viewModel.error,
            onBack = onExit,
            modifier = modifier,
        )
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
                // Camera viewfinder is a device-only follow-up sub-task (CameraX/AVFoundation); the hint
                // tells the user to paste meanwhile. Paste-pairing covers the first E2E flows.
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
                    if (viewModel.busy) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ProgressRing(diameter = 28.dp) }
                    }
                }
            }
            WcErrorBanner(viewModel.error)
        }
    }
}

/**
 * Session-Approval (SPEC_WC §2): dApp identity + Origin + the verifyContext badge + the requested
 * scopes (chains/accounts/methods/events) + Approve/Reject. Reject is a clearly-separated secondary
 * action (never destructive-hidden). All dApp-supplied strings are sanitized (KAN-122).
 */
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
            Text(
                text = BidiSanitizer.sanitize(proposal.dapp.name),
                style = CryptasaTheme.typography.titleLarge,
                color = colors.onSurface,
            )
            WcScopeRow(stringResource(Res.string.wc_origin_label), BidiSanitizer.sanitize(proposal.dapp.url), ltr = true)
            VerifyBadge(proposal.dapp.verifyContext)

            WcScopeRow(stringResource(Res.string.wc_chains_label), proposal.chains.joinToString { BidiSanitizer.sanitize(it) })
            if (viewModel.sharedAccountLabels.isNotEmpty()) {
                WcScopeRow(stringResource(Res.string.wc_accounts_label), viewModel.sharedAccountLabels.joinToString(), ltr = true)
            }
            WcScopeRow(stringResource(Res.string.wc_methods_label), proposal.methods.joinToString { BidiSanitizer.sanitize(it) })
            if (proposal.events.isNotEmpty()) {
                WcScopeRow(stringResource(Res.string.wc_events_label), proposal.events.joinToString { BidiSanitizer.sanitize(it) })
            }

            Text(
                text = stringResource(Res.string.wc_proposal_note),
                style = CryptasaTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
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
            if (viewModel.busy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ProgressRing(diameter = 28.dp) }
            }
        }
    }
}

/** verifyContext badge — icon + text + colour (never colour alone; FR-3 / WCAG-AA, SPEC_WC §2). */
@Composable
private fun VerifyBadge(verifyContext: String?) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val (icon: ImageVector, tint: Color, text: String) = when (verifyContext?.uppercase()) {
        "VERIFIED" -> Triple(CryptasaIcons.CheckCircle, colors.success, stringResource(Res.string.wc_verify_verified))
        "SCAM", "INVALID" -> Triple(CryptasaIcons.Error, colors.danger, stringResource(Res.string.wc_verify_invalid))
        else -> Triple(CryptasaIcons.Warning, colors.warning, stringResource(Res.string.wc_verify_unknown)) // UNKNOWN / null
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

/** Label + value scope row; [ltr] wraps the value as an LTR island (addresses/origins, even in `ar`). */
@Composable
private fun WcScopeRow(label: String, value: String, ltr: Boolean = false) {
    val colors = CryptasaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(CryptasaTheme.spacing.xs)) {
        Text(label, style = CryptasaTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        if (ltr) {
            LtrIsland { Text(value, style = CryptasaTheme.typography.body, color = colors.onSurface) }
        } else {
            Text(value, style = CryptasaTheme.typography.body, color = colors.onSurface)
        }
    }
}

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

/** Et.3 stand-in for the Request-Disclosure screen (SendTx / personal_sign / EIP-712). */
@Composable
private fun WcPlaceholder(title: String, detail: String, error: String?, onBack: () -> Unit, modifier: Modifier) {
    Scaffolded(title = title, onBack = onBack, modifier = modifier) {
        Column(modifier = Modifier.padding(vertical = CryptasaTheme.spacing.md)) {
            Text(detail, style = CryptasaTheme.typography.body, color = CryptasaTheme.colors.onSurface)
            WcErrorBanner(error)
        }
    }
}
