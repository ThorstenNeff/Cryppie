package com.tneff.cyppie.feature.copy

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tneff.cyppie.aa.CopySession
import com.tneff.cyppie.designsystem.BidiSanitizer
import com.tneff.cyppie.designsystem.SecureScreenEffect
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.DisclosureRow
import com.tneff.cyppie.designsystem.icons.CryptasaIcons
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.feature.copy.generated.resources.Res
import com.tneff.cyppie.feature.copy.generated.resources.common_cancel
import com.tneff.cyppie.feature.copy.generated.resources.common_retry
import com.tneff.cyppie.feature.copy.generated.resources.copy_active
import com.tneff.cyppie.feature.copy.generated.resources.copy_cap
import com.tneff.cyppie.feature.copy.generated.resources.copy_empty
import com.tneff.cyppie.feature.copy.generated.resources.copy_err_password
import com.tneff.cyppie.feature.copy.generated.resources.copy_err_revoke
import com.tneff.cyppie.feature.copy.generated.resources.copy_load_error
import com.tneff.cyppie.feature.copy.generated.resources.copy_password
import com.tneff.cyppie.feature.copy.generated.resources.copy_remaining
import com.tneff.cyppie.feature.copy.generated.resources.copy_revoke
import com.tneff.cyppie.feature.copy.generated.resources.copy_revoke_body
import com.tneff.cyppie.feature.copy.generated.resources.copy_revoke_confirm
import com.tneff.cyppie.feature.copy.generated.resources.copy_revoke_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_revoking
import com.tneff.cyppie.feature.copy.generated.resources.copy_since
import com.tneff.cyppie.feature.copy.generated.resources.copy_source
import com.tneff.cyppie.feature.copy.generated.resources.copy_status
import com.tneff.cyppie.feature.copy.generated.resources.copy_status_active
import com.tneff.cyppie.feature.copy.generated.resources.copy_status_paused
import com.tneff.cyppie.feature.copy.generated.resources.copy_title
import com.tneff.cyppie.feature.copy.generated.resources.copy_used
import org.jetbrains.compose.resources.stringResource

/**
 * KAN-157 — the Copy area's landing (`Copy0-Active`): a "Copy a trader" CTA into the KAN-155 Follow-flow
 * plus the list of running copies, each tappable to **Revoke** (on-chain, owner-signed, no-blind). Trust is
 * consistent with the Confirm pattern: [CopySession.source] is rendered as **advisory** (not a guarantee);
 * the verified cap is what actually bounds the damage. [tokenDecimals] scales the cap/used/remaining base
 * units to human amounts; [formatSince] renders the grant time. FLAG_SECURE is applied by the host shell.
 */
@Composable
fun CopyActiveScreen(
    viewModel: CopySessionsViewModel,
    tokenDecimals: Int,
    formatSince: (Long) -> String,
    onCopyTrader: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecureScreenEffect() // KAN-168: this screen OWNS its protection (trader addresses + the revoke re-auth/sign)
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.copy_active), onBack = onBack)
            // The CTA is always visible (top of the area) — copying a trader is reachable from every state.
            CryptasaButton(
                text = stringResource(Res.string.copy_title),
                onClick = onCopyTrader,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.md).testTag(CopyTestTags.COPY_CTA),
            )
            when (viewModel.listState) {
                CopyListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
                CopyListState.Empty -> Box(Modifier.fillMaxSize().padding(horizontal = spacing.xl), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.copy_empty),
                        style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant,
                        modifier = Modifier.testTag(CopyTestTags.EMPTY),
                    )
                }
                CopyListState.Error -> Column(Modifier.fillMaxSize().padding(horizontal = spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    CryptasaBanner(
                        title = stringResource(Res.string.copy_load_error),
                        tone = CryptasaBannerTone.Danger,
                        modifier = Modifier.testTag(CopyTestTags.LOAD_ERROR),
                    )
                    CryptasaButton(
                        text = stringResource(Res.string.common_retry),
                        onClick = viewModel::load,
                        style = CryptasaButtonStyle.Secondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                CopyListState.Loaded -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = spacing.xl).testTag(CopyTestTags.ACTIVE_SCREEN),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    items(viewModel.sessions, key = { it.permissionId }) { session ->
                        CopySessionCard(
                            session = session,
                            tokenDecimals = tokenDecimals,
                            formatSince = formatSince,
                            onRevoke = { viewModel.askRevoke(session) },
                        )
                    }
                }
            }
        }

        // Revoke-Confirm (Copy-Revoke-Confirm) — blocking dialog on top of the list.
        viewModel.revokeTarget?.let {
            CopyRevokeDialog(viewModel = viewModel)
        }
    }
}

/** One running copy as the UX `Copy0-Active` row. Addresses/amounts = LTR islands; status = icon+text (never color-only). */
@Composable
private fun CopySessionCard(
    session: CopySession,
    tokenDecimals: Int,
    formatSince: (Long) -> String,
    onRevoke: () -> Unit,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val active = session.status != "paused"

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant).padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        // Source = advisory (the followed trader), not an on-chain guarantee — same trust framing as Confirm.
        DisclosureRow(stringResource(Res.string.copy_source), BidiSanitizer.sanitize(session.source), ltr = true)
        DisclosureRow(stringResource(Res.string.copy_cap), humanAmount(session.cap, tokenDecimals), ltr = true)
        DisclosureRow(stringResource(Res.string.copy_used), humanAmount(session.used, tokenDecimals), ltr = true)
        DisclosureRow(stringResource(Res.string.copy_remaining), humanAmount(session.remaining, tokenDecimals), ltr = true)

        // Status badge — icon + text (WCAG: never color alone). Active = success; paused = neutral.
        Row(
            Modifier.testTag(CopyTestTags.SESSION_STATUS),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.copy_status), style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant)
            Icon(
                imageVector = if (active) CryptasaIcons.CheckCircle else CryptasaIcons.Info,
                contentDescription = null,
                tint = if (active) colors.success else colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(if (active) Res.string.copy_status_active else Res.string.copy_status_paused),
                style = CryptasaTheme.typography.helper,
                color = if (active) colors.success else colors.onSurfaceVariant,
            )
        }
        Text(
            stringResource(Res.string.copy_since, formatSince(session.since)),
            style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant,
        )
        CryptasaButton(
            text = stringResource(Res.string.copy_revoke),
            onClick = onRevoke,
            style = CryptasaButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.REVOKE),
        )
    }
}

/**
 * `Copy-Revoke-Confirm` — the blocking consequence dialog + ADR-0009 re-auth. The danger framing (icon/title/
 * body) communicates the irreversible on-chain end clearly; the password field gates the on-device sign.
 * fail-safe: a failure keeps the dialog + the session (the VM never removes a row without a confirmed receipt).
 */
@Composable
private fun CopyRevokeDialog(viewModel: CopySessionsViewModel) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    var password by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(CryptasaTheme.radius.xl),
            color = colors.surfaceRaised,
            contentColor = colors.onSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(spacing.xl).widthIn(max = 480.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(
                    stringResource(Res.string.copy_revoke_title),
                    style = CryptasaTheme.typography.titleSmall, color = colors.onSurface,
                    modifier = Modifier.testTag(CopyTestTags.REVOKE_TITLE),
                )
                Text(stringResource(Res.string.copy_revoke_body), style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant)

                CryptasaTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(Res.string.copy_password),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    enabled = !viewModel.revoking,
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.PASSWORD),
                )
                viewModel.revokeError?.let { err ->
                    val msg = when (err) {
                        RevokeError.WrongPassword -> stringResource(Res.string.copy_err_password)
                        RevokeError.Failed -> stringResource(Res.string.copy_err_revoke)
                    }
                    CryptasaBanner(title = msg, tone = CryptasaBannerTone.Danger)
                }

                CryptasaButton(
                    text = if (viewModel.revoking) stringResource(Res.string.copy_revoking) else stringResource(Res.string.copy_revoke_confirm),
                    onClick = { viewModel.confirmRevoke(password) },
                    enabled = !viewModel.revoking && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag(CopyTestTags.REVOKE_CONFIRM),
                )
                CryptasaButton(
                    text = stringResource(Res.string.common_cancel),
                    onClick = viewModel::dismissRevoke,
                    style = CryptasaButtonStyle.Secondary,
                    enabled = !viewModel.revoking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Float-free base-units → human amount via [decimals] (e.g. 30000000 @ 6 → "30"). Defensive on non-digits. */
private fun humanAmount(base: String, decimals: Int): String {
    if (base.isEmpty() || base.any { it !in '0'..'9' }) return base
    val digits = base.trimStart('0').ifEmpty { "0" }
    if (decimals <= 0) return digits
    val padded = digits.padStart(decimals + 1, '0')
    val frac = padded.takeLast(decimals).trimEnd('0')
    val whole = padded.dropLast(decimals)
    return if (frac.isEmpty()) whole else "$whole.$frac"
}
