package com.tneff.cyppie.feature.dca

import com.tneff.cyppie.feature.dca.generated.resources.Res
import com.tneff.cyppie.feature.dca.generated.resources.common_cancel
import com.tneff.cyppie.feature.dca.generated.resources.common_retry
import com.tneff.cyppie.feature.dca.generated.resources.dca_account
import com.tneff.cyppie.feature.dca.generated.resources.dca_active_sessions
import com.tneff.cyppie.feature.dca.generated.resources.dca_cap
import com.tneff.cyppie.feature.dca.generated.resources.dca_chain
import com.tneff.cyppie.feature.dca.generated.resources.dca_empty
import com.tneff.cyppie.feature.dca.generated.resources.dca_load_error
import com.tneff.cyppie.feature.dca.generated.resources.dca_max_ops
import com.tneff.cyppie.feature.dca.generated.resources.dca_new
import com.tneff.cyppie.feature.dca.generated.resources.dca_password
import com.tneff.cyppie.feature.dca.generated.resources.dca_paused_body
import com.tneff.cyppie.feature.dca.generated.resources.dca_paused_title
import com.tneff.cyppie.feature.dca.generated.resources.dca_pay_token
import com.tneff.cyppie.feature.dca.generated.resources.dca_pending
import com.tneff.cyppie.feature.dca.generated.resources.dca_receive_token
import com.tneff.cyppie.feature.dca.generated.resources.dca_review_sign
import com.tneff.cyppie.feature.dca.generated.resources.dca_revoke
import com.tneff.cyppie.feature.dca.generated.resources.dca_router
import com.tneff.cyppie.feature.dca.generated.resources.dca_sign_submit
import com.tneff.cyppie.feature.dca.generated.resources.dca_spend
import com.tneff.cyppie.feature.dca.generated.resources.dca_title
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.tneff.cyppie.aa.PendingDca
import com.tneff.cyppie.aa.SessionConfig
import com.tneff.cyppie.designsystem.components.CryptasaBanner
import com.tneff.cyppie.designsystem.components.CryptasaBannerTone
import com.tneff.cyppie.designsystem.components.CryptasaButton
import com.tneff.cyppie.designsystem.components.CryptasaButtonStyle
import com.tneff.cyppie.designsystem.components.CryptasaTextField
import com.tneff.cyppie.designsystem.components.CryptasaTopAppBar
import com.tneff.cyppie.designsystem.components.DisclosureRow
import com.tneff.cyppie.designsystem.components.ProgressRing
import com.tneff.cyppie.designsystem.theme.CryptasaTheme

// Pre-i18n labels (dca_* keys → UX follow-up; no composeResources change → checkI18n unaffected).

/**
 * DCA overview (PRD-05 Ph1): the kill-switch banner, pending DCA ops (no-blind disclosure + inline
 * re-auth → on-device sign), and running sessions (limits + revoke). New schedule via [onCreate].
 */
@Composable
fun DcaOverviewScreen(
    viewModel: DcaViewModel,
    onCreate: () -> Unit = {},
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    var signingId by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.dca_title), onBack = onBack)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.xl).testTag("dca_screen"),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                when (val state = viewModel.uiState) {
                    is DcaUiState.Loading ->
                        Box(Modifier.fillMaxWidth().padding(top = spacing.xl), contentAlignment = Alignment.Center) { ProgressRing(diameter = 32.dp) }
                    is DcaUiState.Error ->
                        CryptasaBanner(title = stringResource(Res.string.dca_load_error), tone = CryptasaBannerTone.Danger, actionText = stringResource(Res.string.common_retry), onActionClick = viewModel::retry, modifier = Modifier.padding(top = spacing.md).testTag("dca_error"))
                    is DcaUiState.Content -> {
                        if (state.paused) {
                            CryptasaBanner(title = stringResource(Res.string.dca_paused_title), description = stringResource(Res.string.dca_paused_body), tone = CryptasaBannerTone.Warning, modifier = Modifier.testTag("dca_paused"))
                        }
                        CryptasaButton(text = stringResource(Res.string.dca_new), onClick = onCreate, modifier = Modifier.fillMaxWidth().testTag("dca_create"))

                        if (state.pending.isNotEmpty()) {
                            Text(stringResource(Res.string.dca_pending), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                            state.pending.forEach { dca ->
                                PendingCard(
                                    dca = dca,
                                    enabled = !state.paused,
                                    isSigning = signingId == dca.id,
                                    password = password,
                                    error = viewModel.signError,
                                    onPasswordChange = { password = it },
                                    onStartSign = { signingId = dca.id; password = "" },
                                    onConfirm = { viewModel.signPending(dca, password); signingId = null; password = "" },
                                    onCancel = { signingId = null; password = "" },
                                )
                            }
                        }

                        Text(stringResource(Res.string.dca_active_sessions), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
                        if (state.sessions.isEmpty()) {
                            Text(stringResource(Res.string.dca_empty), style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant, modifier = Modifier.testTag("dca_empty"))
                        } else {
                            state.sessions.forEach { SessionCard(it, onRevoke = { it.sessionId?.let(viewModel::revoke) }) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingCard(
    dca: PendingDca,
    enabled: Boolean,
    isSigning: Boolean,
    password: String,
    error: DcaError?,
    onPasswordChange: (String) -> Unit,
    onStartSign: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant).padding(spacing.lg).testTag("dca_pending"),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        // No-blind disclosure of the op the user is about to authorize.
        DisclosureRow(stringResource(Res.string.dca_spend), dca.action.amountIn, ltr = true, valueTestTag = "dca_amount_in")
        DisclosureRow(stringResource(Res.string.dca_pay_token), dca.action.tokenIn, ltr = true)
        DisclosureRow(stringResource(Res.string.dca_receive_token), dca.action.tokenOut, ltr = true)
        DisclosureRow(stringResource(Res.string.dca_router), dca.action.router, ltr = true)
        if (isSigning) {
            // Re-auth gate (ADR-0009) — a correct password yields a fresh seed source that AaSigner zeroizes.
            CryptasaTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = stringResource(Res.string.dca_password),
                errorText = error?.text(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
                modifier = Modifier.fillMaxWidth().padding(top = spacing.sm).testTag("dca_password"),
            )
            CryptasaButton(text = stringResource(Res.string.dca_sign_submit), onClick = onConfirm, enabled = password.isNotBlank(), modifier = Modifier.fillMaxWidth().testTag("dca_confirm"))
            CryptasaButton(text = stringResource(Res.string.common_cancel), onClick = onCancel, style = CryptasaButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        } else {
            CryptasaButton(text = stringResource(Res.string.dca_review_sign), onClick = onStartSign, enabled = enabled, modifier = Modifier.fillMaxWidth().padding(top = spacing.sm).testTag("dca_sign"))
        }
    }
}

@Composable
private fun SessionCard(session: SessionConfig, onRevoke: () -> Unit) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant).padding(spacing.lg).testTag("dca_session"),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        DisclosureRow(stringResource(Res.string.dca_account), session.account, ltr = true)
        DisclosureRow(stringResource(Res.string.dca_chain), session.chainId.toString(), ltr = true)
        session.actions.firstOrNull()?.let { a ->
            a.spendingLimits.firstOrNull()?.let { DisclosureRow(stringResource(Res.string.dca_cap), it.cap, ltr = true) }
            DisclosureRow(stringResource(Res.string.dca_max_ops), a.usageLimit.toString(), ltr = true)
        }
        CryptasaButton(text = stringResource(Res.string.dca_revoke), onClick = onRevoke, style = CryptasaButtonStyle.Secondary, modifier = Modifier.fillMaxWidth().padding(top = spacing.sm).testTag("dca_revoke"))
    }
}
