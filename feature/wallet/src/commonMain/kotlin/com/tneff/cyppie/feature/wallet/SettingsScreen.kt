package com.tneff.cyppie.feature.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaDialog
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.SelectionCard
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.evm.network.NetworkEnvironment
import com.tneff.cyppie.feature.wallet.generated.resources.Res
import com.tneff.cyppie.feature.wallet.generated.resources.common_cancel
import com.tneff.cyppie.feature.wallet.generated.resources.net_active_sessions
import com.tneff.cyppie.feature.wallet.generated.resources.net_current
import com.tneff.cyppie.feature.wallet.generated.resources.net_mainnet
import com.tneff.cyppie.feature.wallet.generated.resources.net_mainnet_desc
import com.tneff.cyppie.feature.wallet.generated.resources.net_section
import com.tneff.cyppie.feature.wallet.generated.resources.net_settings_title
import com.tneff.cyppie.feature.wallet.generated.resources.net_switch_confirm
import com.tneff.cyppie.feature.wallet.generated.resources.net_switch_title
import com.tneff.cyppie.feature.wallet.generated.resources.net_testnet
import com.tneff.cyppie.feature.wallet.generated.resources.net_testnet_desc
import com.tneff.cyppie.feature.wallet.generated.resources.net_to_mainnet_body
import com.tneff.cyppie.feature.wallet.generated.resources.net_to_mainnet_title
import com.tneff.cyppie.feature.wallet.generated.resources.net_to_testnet_body
import org.jetbrains.compose.resources.stringResource

/** Mainnet/Testnet are brand/network names — NOT translated (UX spec). */
private fun NetworkEnvironment.displayName(): String = if (this == NetworkEnvironment.MAINNET) "Mainnet" else "Testnet"

/**
 * KAN-173 / KAN-175 §1-2-4 — the Settings screen (PRD-09, net-new `WalletDest.Settings`). MVP hosts only the
 * Network section: shows the active network + a Mainnet/Testnet selector. Picking the **other** network opens
 * the graded switch-warning ([CryptasaDialog], explicit Cancel) — confirming calls [onSwitchNetwork], which
 * persists+emits the new env → the shell's `key(activeEnv)` soft-relaunch tears this screen down.
 *
 * [activeSessionCount] (active DCA/Copy/Strategy sessions on the current net) drives the "they keep running"
 * note when > 0 — informational, never a block/revoke.
 */
@Composable
internal fun SettingsScreen(
    activeEnv: NetworkEnvironment,
    onSwitchNetwork: (NetworkEnvironment) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    loadActiveSessionCount: suspend () -> Int = { 0 },
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    // The network the user tapped to switch TO (null = no pending switch / dialog closed).
    var pendingSwitch by remember { mutableStateOf<NetworkEnvironment?>(null) }
    // KAN-173 safety fix: load the active Copy+DCA session count on the current net so the switch-warning's
    // "they keep running on <net>" note actually fires (best-effort; a failure leaves it 0 → the note is omitted).
    var activeSessionCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { activeSessionCount = runCatching { loadActiveSessionCount() }.getOrDefault(0) }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.net_settings_title), onBack = onBack)
            Column(
                Modifier.fillMaxSize().padding(horizontal = spacing.xl).testTag("net_settings_title"),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(stringResource(Res.string.net_section), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                Text(
                    "${stringResource(Res.string.net_current)}: ${activeEnv.displayName()}",
                    style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant,
                    modifier = Modifier.testTag("net_current"),
                )
                SelectionCard(
                    title = stringResource(Res.string.net_mainnet),
                    description = stringResource(Res.string.net_mainnet_desc),
                    selected = activeEnv == NetworkEnvironment.MAINNET,
                    onClick = { if (activeEnv != NetworkEnvironment.MAINNET) pendingSwitch = NetworkEnvironment.MAINNET },
                    modifier = Modifier.fillMaxWidth().testTag("net_mainnet"),
                )
                SelectionCard(
                    title = stringResource(Res.string.net_testnet),
                    description = stringResource(Res.string.net_testnet_desc),
                    selected = activeEnv == NetworkEnvironment.TESTNET,
                    onClick = { if (activeEnv != NetworkEnvironment.TESTNET) pendingSwitch = NetworkEnvironment.TESTNET },
                    modifier = Modifier.fillMaxWidth().testTag("net_testnet"),
                )
            }
        }
    }

    // Graded switch-warning (KAN-175 §4): strongest framing on Testnet→Mainnet ("real-funds mode"); the
    // "no real money" framing on entering testnet; the active-sessions note (no block / no revoke). Explicit Cancel.
    pendingSwitch?.let { target ->
        val enteringMainnet = target == NetworkEnvironment.MAINNET
        val sessionsNote = if (activeSessionCount > 0)
            "\n\n" + stringResource(Res.string.net_active_sessions, activeSessionCount, activeEnv.displayName()) else ""
        CryptasaDialog(
            title = stringResource(if (enteringMainnet) Res.string.net_to_mainnet_title else Res.string.net_switch_title),
            body = stringResource(if (enteringMainnet) Res.string.net_to_mainnet_body else Res.string.net_to_testnet_body) + sessionsNote,
            confirmText = stringResource(Res.string.net_switch_confirm),
            onConfirm = { pendingSwitch = null; onSwitchNetwork(target) }, // persist+emit → soft-relaunch tears this down
            dismissText = stringResource(Res.string.common_cancel),
            onDismiss = { pendingSwitch = null },
            modifier = Modifier.testTag("net_switch_title"),
        )
    }
}
