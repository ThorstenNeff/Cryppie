package com.tneff.cyppie.feature.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.BidiSanitizer
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.components.SegmentedControl
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.wallet.generated.resources.Res
import com.tneff.cyppie.feature.wallet.generated.resources.wallet_action_connect
import com.tneff.cyppie.feature.wallet.generated.resources.wc_pairing_paste
import com.tneff.cyppie.feature.wallet.generated.resources.wc_pairing_paste_placeholder
import com.tneff.cyppie.feature.wallet.generated.resources.wc_pairing_scan
import com.tneff.cyppie.feature.wallet.generated.resources.wc_pairing_scan_hint
import com.tneff.cyppie.feature.wallet.generated.resources.wc_pairing_title
import com.tneff.cyppie.feature.wallet.generated.resources.wc_proposal_title
import com.tneff.cyppie.feature.wallet.generated.resources.wc_req_method
import org.jetbrains.compose.resources.stringResource

/** Pairing tabs (SPEC_WC §1). */
private enum class WcPairTab { Scan, Paste }

/**
 * KAN-126 — the WalletConnect bottom-sheet flow host. [WcUiState.Idle] → Pairing; an incoming proposal
 * or request takes over with full disclosure (no blind-signing, SPEC_WC). FLAG_SECURE: every WC frame
 * is a signature/identity context. Session-Approval (Et.2) and Request-Disclosure (Et.3) replace the
 * placeholders below.
 */
@Composable
fun WalletConnectRoot(viewModel: WalletConnectViewModel, onExit: () -> Unit, modifier: Modifier = Modifier) {
    SecureScreenEffect()
    when (val state = viewModel.uiState) {
        is WcUiState.Idle -> WcPairingScreen(viewModel, onBack = onExit, modifier)
        is WcUiState.Proposal -> WcPlaceholder(
            title = stringResource(Res.string.wc_proposal_title),
            // dApp name is attacker-controlled → sanitize even in the Et.1 placeholder (KAN-122).
            detail = BidiSanitizer.sanitize(state.proposal.dapp.name),
            onBack = onExit,
            modifier = modifier,
        )
        is WcUiState.Request -> WcPlaceholder(
            title = stringResource(Res.string.wc_req_method, state.request.method),
            detail = BidiSanitizer.sanitize(state.request.dapp.name),
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
                        enabled = uri.isNotBlank() && !viewModel.pairing,
                        modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.WC_PAIRING_CONNECT),
                    )
                    if (viewModel.pairing) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { ProgressRing(diameter = 28.dp) }
                    }
                }
            }
            viewModel.pairError?.let { err ->
                CryptasaBanner(
                    title = BidiSanitizer.sanitize(err),
                    tone = CryptasaBannerTone.Danger,
                    modifier = Modifier.testTag(WalletTestTags.WC_ERROR),
                )
            }
        }
    }
}

/** Et.1 stand-in for the Session-Approval (Et.2) / Request-Disclosure (Et.3) screens. */
@Composable
private fun WcPlaceholder(title: String, detail: String, onBack: () -> Unit, modifier: Modifier) {
    Scaffolded(title = title, onBack = onBack, modifier = modifier) {
        Text(
            text = detail,
            style = CryptasaTheme.typography.body,
            color = CryptasaTheme.colors.onSurface,
            modifier = Modifier.padding(vertical = CryptasaTheme.spacing.md),
        )
    }
}
