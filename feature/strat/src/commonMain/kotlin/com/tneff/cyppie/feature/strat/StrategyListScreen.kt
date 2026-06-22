package com.tneff.cyppie.feature.strat

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import com.tneff.cyppie.feature.strat.generated.resources.Res
import com.tneff.cyppie.feature.strat.generated.resources.common_cancel
import com.tneff.cyppie.feature.strat.generated.resources.common_retry
import com.tneff.cyppie.feature.strat.generated.resources.strat_active
import com.tneff.cyppie.feature.strat.generated.resources.strat_budget
import com.tneff.cyppie.feature.strat.generated.resources.strat_current
import com.tneff.cyppie.feature.strat.generated.resources.strat_drift
import com.tneff.cyppie.feature.strat.generated.resources.strat_pause
import com.tneff.cyppie.feature.strat.generated.resources.strat_performance
import com.tneff.cyppie.feature.strat.generated.resources.strat_resume
import com.tneff.cyppie.feature.strat.generated.resources.strat_target_short
import com.tneff.cyppie.feature.strat.generated.resources.strat_empty
import com.tneff.cyppie.feature.strat.generated.resources.strat_err_password
import com.tneff.cyppie.feature.strat.generated.resources.strat_err_submit
import com.tneff.cyppie.feature.strat.generated.resources.strat_last_rebalance
import com.tneff.cyppie.feature.strat.generated.resources.strat_load_error
import com.tneff.cyppie.feature.strat.generated.resources.strat_new
import com.tneff.cyppie.feature.strat.generated.resources.strat_password
import com.tneff.cyppie.feature.strat.generated.resources.strat_revoke
import com.tneff.cyppie.feature.strat.generated.resources.strat_revoke_body
import com.tneff.cyppie.feature.strat.generated.resources.strat_revoke_confirm
import com.tneff.cyppie.feature.strat.generated.resources.strat_revoke_title
import com.tneff.cyppie.feature.strat.generated.resources.strat_revoking
import com.tneff.cyppie.feature.strat.generated.resources.strat_status_active
import com.tneff.cyppie.feature.strat.generated.resources.strat_status_paused
import org.jetbrains.compose.resources.stringResource

/**
 * KAN-166 — the Strategy area landing (`Strat2-List`): a "New strategy" CTA into the Setup flow + the list of
 * running strategies, each tappable to Revoke (on-chain, owner-signed, no-blind — fail-safe). FLAG_SECURE is
 * owned here (KAN-168 — addresses + revoke re-auth/sign). [budgetTokenDecimals] scales budget to human units.
 */
@Composable
fun StrategyListScreen(
    viewModel: StrategyListViewModel,
    budgetTokenDecimals: Int,
    formatRebalance: (Long) -> String,
    onNewStrategy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SecureScreenEffect()
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    LaunchedEffect(Unit) { viewModel.load() }

    Box(modifier = modifier.fillMaxSize().background(colors.surface), contentAlignment = Alignment.TopCenter) {
        val detail = viewModel.detailTarget
        if (detail != null) {
            StrategyDetailScreen(viewModel, detail, budgetTokenDecimals, formatRebalance)
        } else Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
            CryptasaTopAppBar(title = stringResource(Res.string.strat_active), onBack = onBack)
            CryptasaButton(
                text = stringResource(Res.string.strat_new),
                onClick = onNewStrategy,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xl, vertical = spacing.md).testTag(StrategyTestTags.NEW_CTA),
            )
            when (viewModel.listState) {
                StratListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primary)
                }
                StratListState.Empty -> Box(Modifier.fillMaxSize().padding(horizontal = spacing.xl), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(Res.string.strat_empty),
                        style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant,
                        modifier = Modifier.testTag(StrategyTestTags.EMPTY),
                    )
                }
                StratListState.Error -> Column(Modifier.fillMaxSize().padding(horizontal = spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                    CryptasaBanner(title = stringResource(Res.string.strat_load_error), tone = CryptasaBannerTone.Danger, modifier = Modifier.testTag(StrategyTestTags.LOAD_ERROR))
                    CryptasaButton(text = stringResource(Res.string.common_retry), onClick = viewModel::load, style = CryptasaButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
                }
                StratListState.Loaded -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = spacing.xl).testTag(StrategyTestTags.ACTIVE_SCREEN),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    items(viewModel.sessions, key = { it.sessionId }) { session ->
                        StrategyCard(session, budgetTokenDecimals, formatRebalance) { viewModel.openDetail(session) }
                    }
                }
            }
        }
        viewModel.revokeTarget?.let { StrategyRevokeDialog(viewModel) }
    }
}

@Composable
private fun StrategyCard(
    session: StrategySession,
    budgetTokenDecimals: Int,
    formatRebalance: (Long) -> String,
    onClick: () -> Unit,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val active = session.status != "paused"

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(CryptasaTheme.radius.md)).background(colors.surfaceVariant).clickable(onClick = onClick).padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(BidiSanitizer.sanitize(session.name), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
        DisclosureRow(stringResource(Res.string.strat_budget), humanAmount(session.budgetBaseUnits, budgetTokenDecimals), ltr = true)
        DisclosureRow(stringResource(Res.string.strat_drift), "${session.driftBps / 100}%", ltr = true)

        Row(
            Modifier.testTag(StrategyTestTags.SESSION_STATUS),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (active) CryptasaIcons.CheckCircle else CryptasaIcons.Info,
                contentDescription = null,
                tint = if (active) colors.success else colors.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                stringResource(if (active) Res.string.strat_status_active else Res.string.strat_status_paused),
                style = CryptasaTheme.typography.helper,
                color = if (active) colors.success else colors.onSurfaceVariant,
            )
        }
        Text(
            stringResource(Res.string.strat_last_rebalance, formatRebalance(session.lastRebalanceEpochSeconds)),
            style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant,
        )
    }
}

/**
 * `Strat3-Detail` — Current vs Target allocation + drift + performance, with the instant off-chain
 * Pause/Resume kill-switch and the on-chain Revoke (→ the re-auth dialog). FLAG_SECURE is owned by the host
 * [StrategyListScreen]. Reachable only from a list row (the stub list is empty until the engine/backend fills it).
 */
@Composable
private fun StrategyDetailScreen(
    viewModel: StrategyListViewModel,
    session: StrategySession,
    budgetTokenDecimals: Int,
    formatRebalance: (Long) -> String,
) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    val active = session.status != "paused"

    Column(Modifier.widthIn(max = 480.dp).fillMaxSize()) {
        CryptasaTopAppBar(title = BidiSanitizer.sanitize(session.name), onBack = viewModel::closeDetail)
        Column(
            Modifier.fillMaxSize().padding(horizontal = spacing.xl).testTag(StrategyTestTags.DETAIL_CURRENT),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            DisclosureRow(stringResource(Res.string.strat_budget), humanAmount(session.budgetBaseUnits, budgetTokenDecimals), ltr = true)
            DisclosureRow(stringResource(Res.string.strat_drift), "${session.driftBps / 100}%", ltr = true, valueTestTag = StrategyTestTags.DETAIL_DRIFT)
            session.performance?.let { DisclosureRow(stringResource(Res.string.strat_performance), BidiSanitizer.sanitize(it), ltr = true) }
            // KAN-170 S5: a normal Label+Value row (was the formatted sentence as label with an empty value —
            // inconsistent with the rows above). The "Last rebalance %1$s" key with an empty arg → the bare label;
            // the date is the value. (List-card subtitle above still uses the full formatted sentence.)
            DisclosureRow(stringResource(Res.string.strat_last_rebalance, "").trim(), formatRebalance(session.lastRebalanceEpochSeconds), ltr = true)

            // Current vs Target allocation — two weight columns (no donut yet; weights are readable values).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing.lg)) {
                AllocationColumn(stringResource(Res.string.strat_current), session.currentWeights, Modifier.weight(1f))
                AllocationColumn(stringResource(Res.string.strat_target_short), session.targets, Modifier.weight(1f))
            }

            CryptasaButton(
                text = stringResource(if (active) Res.string.strat_pause else Res.string.strat_resume),
                onClick = viewModel::togglePause,
                enabled = !viewModel.togglingPause,
                style = CryptasaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag(if (active) StrategyTestTags.PAUSE else StrategyTestTags.RESUME),
            )
            CryptasaButton(
                text = stringResource(Res.string.strat_revoke),
                onClick = { viewModel.askRevoke(session) },
                style = CryptasaButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth().testTag(StrategyTestTags.REVOKE),
            )
        }
    }
}

@Composable
private fun AllocationColumn(title: String, weights: List<BasketTarget>, modifier: Modifier = Modifier) {
    val colors = CryptasaTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CryptasaTheme.spacing.xs)) {
        Text(title, style = CryptasaTheme.typography.titleSmall, color = colors.onSurface)
        if (weights.isEmpty()) {
            Text("—", style = CryptasaTheme.typography.helper, color = colors.onSurfaceVariant)
        } else weights.forEach { w ->
            DisclosureRow(BidiSanitizer.sanitize(w.token), "${w.weightPercent}%", ltr = true)
        }
    }
}

/** `Strat-Revoke-Confirm` — consequence + ADR-0009 re-auth. fail-safe: a failure keeps the strategy active. */
@Composable
private fun StrategyRevokeDialog(viewModel: StrategyListViewModel) {
    val colors = CryptasaTheme.colors
    val spacing = CryptasaTheme.spacing
    var password by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(CryptasaTheme.radius.xl),
            color = colors.surfaceRaised,
            contentColor = colors.onSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(spacing.xl).widthIn(max = 480.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(spacing.xl), verticalArrangement = Arrangement.spacedBy(spacing.md)) {
                Text(stringResource(Res.string.strat_revoke_title), style = CryptasaTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.testTag(StrategyTestTags.REVOKE_TITLE))
                Text(stringResource(Res.string.strat_revoke_body), style = CryptasaTheme.typography.body, color = colors.onSurfaceVariant)
                CryptasaTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(Res.string.strat_password),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    enabled = !viewModel.revoking,
                    modifier = Modifier.fillMaxWidth().testTag(StrategyTestTags.PASSWORD),
                )
                viewModel.revokeError?.let { err ->
                    val msg = when (err) {
                        StratRevokeError.WrongPassword -> stringResource(Res.string.strat_err_password)
                        StratRevokeError.Failed -> stringResource(Res.string.strat_err_submit)
                    }
                    CryptasaBanner(title = msg, tone = CryptasaBannerTone.Danger)
                }
                CryptasaButton(
                    text = if (viewModel.revoking) stringResource(Res.string.strat_revoking) else stringResource(Res.string.strat_revoke_confirm),
                    onClick = { viewModel.confirmRevoke(password) },
                    enabled = !viewModel.revoking && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag(StrategyTestTags.REVOKE_CONFIRM),
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
