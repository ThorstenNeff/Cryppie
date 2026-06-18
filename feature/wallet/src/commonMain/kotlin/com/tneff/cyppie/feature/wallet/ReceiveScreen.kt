package com.tneff.cyppie.feature.wallet

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.SegmentedControl
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.wallet.generated.resources.Res
import com.tneff.cyppie.feature.wallet.generated.resources.common_close
import com.tneff.cyppie.feature.wallet.generated.resources.receive_address_label
import com.tneff.cyppie.feature.wallet.generated.resources.receive_copied
import com.tneff.cyppie.feature.wallet.generated.resources.receive_copy
import com.tneff.cyppie.feature.wallet.generated.resources.receive_qr_cd
import com.tneff.cyppie.feature.wallet.generated.resources.receive_warning
import com.tneff.cyppie.feature.wallet.generated.resources.wallet_action_receive
import com.tneff.cyppie.feature.wallet.generated.resources.wallet_chain_base
import com.tneff.cyppie.feature.wallet.generated.resources.wallet_chain_eth
import com.tneff.cyppie.walletcore.EvmChain
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import org.jetbrains.compose.resources.stringResource

/**
 * D2 — Receive (KAN-47/KAN-78). Shows the active account's EVM [address] as text + QR with copy and
 * phishing-aware UX. The address is **chain-identical** for Ethereum and Base, so the segmented
 * control only re-labels the address + frames the warning — it never changes the address or QR.
 *
 * Stateless wrt the account: the caller passes the EIP-55 [address] (from
 * `WalletRepository.receiveInfo`). The QR encodes the bare address (max scanner compatibility), drawn
 * black-on-white **always light** (scannability, even in dark mode — fixed `#FFF`/`#000` asset values).
 */
@Composable
fun ReceiveScreen(
    address: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val radius = CryptasaTheme.radius
    val typography = CryptasaTheme.typography
    val clipboard = LocalClipboardManager.current

    var selectedChain by remember { mutableStateOf(EvmChain.ETHEREUM) }
    var copied by remember { mutableStateOf(false) }

    val ethName = stringResource(Res.string.wallet_chain_eth)
    val baseName = stringResource(Res.string.wallet_chain_base)
    val chainName: (EvmChain) -> String = { if (it == EvmChain.ETHEREUM) ethName else baseName }

    fun copyAddress() {
        clipboard.setText(AnnotatedString(address))
        copied = true
    }

    Box(
        modifier = modifier.fillMaxSize().background(colors.surface),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxSize().padding(horizontal = spacing.xl),
        ) {
            CryptasaTopAppBar(
                title = stringResource(Res.string.wallet_action_receive),
                onBack = onBack,
                backContentDescription = stringResource(Res.string.common_close),
            )

            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SegmentedControl(
                    options = listOf(EvmChain.ETHEREUM, EvmChain.BASE),
                    selected = selectedChain,
                    onSelect = { selectedChain = it },
                    label = chainName,
                    modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.RECEIVE_CHAIN),
                )

                // QR on a fixed white carrier — always light for scannability (HANDOFF §4.1).
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(radius.lg)).background(Color.White).padding(spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = rememberQrCodePainter(address),
                        contentDescription = stringResource(Res.string.receive_qr_cd),
                        modifier = Modifier.size(220.dp).testTag(WalletTestTags.RECEIVE_QR),
                    )
                }

                Text(
                    text = stringResource(Res.string.receive_address_label, chainName(selectedChain)),
                    style = typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Address box: full address, never truncated, LTR even in RTL locales (spec), selectable.
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(radius.md)).background(colors.surfaceVariant)
                        .padding(start = spacing.md, top = spacing.sm, bottom = spacing.sm, end = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        SelectionContainer(modifier = Modifier.weight(1f)) {
                            Text(
                                text = address,
                                style = typography.bodySmall,
                                color = colors.onSurface,
                                modifier = Modifier.testTag(WalletTestTags.RECEIVE_ADDRESS),
                            )
                        }
                    }
                    IconButton(onClick = { copyAddress() }) {
                        Icon(
                            imageVector = CryptasaIcons.ContentCopy,
                            contentDescription = stringResource(Res.string.receive_copy),
                            tint = colors.primary,
                        )
                    }
                }

                CryptasaBanner(
                    title = stringResource(Res.string.receive_warning),
                    tone = CryptasaBannerTone.Warning,
                    modifier = Modifier.fillMaxWidth().testTag(WalletTestTags.RECEIVE_WARNING),
                )
            }

            CryptasaButton(
                text = if (copied) stringResource(Res.string.receive_copied) else stringResource(Res.string.receive_copy),
                onClick = { copyAddress() },
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.md).testTag(WalletTestTags.RECEIVE_COPY),
            )
        }
    }
}
