package com.tneff.cyppie.feature.copy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tneff.cyppie.aa.CopyGrantPreview
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.SeedSource

/**
 * KAN-155 — the Copy / Follow-Trader flow host. Linear nav over [FollowViewModel.step]: choose trader →
 * pick mirror mode (fixed/dynamic, KAN-161) → budget → verified disclosure + sign → done. The app shell
 * injects the two crypto/network seams ([prepareGrant]/[authorizeGrant], bound to Dev-2's `:aa`
 * `FollowGrantService` with the full scope assembled around them), the [owner] address (self-copy guard),
 * the budget-token [budgetTokenDecimals], and the [reauth] source factory; [onExit] leaves the flow (back
 * from screen 1, or after activation — the Active-Copies overview is the KAN-157 follow-up).
 */
@Composable
fun CopyRoot(
    owner: EvmAddress,
    budgetTokenDecimals: Int,
    prepareGrant: suspend (trader: String, budgetBaseUnits: String, tokenOut: String?) -> CopyGrantPreview,
    authorizeGrant: suspend (preview: CopyGrantPreview, seed: SeedSource) -> Unit,
    reauth: suspend (password: String) -> SeedSource?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    allowlistTokens: List<CopyToken> = emptyList(),
) {
    val viewModel: FollowViewModel = viewModel(key = "copy_follow") {
        FollowViewModel(
            owner = owner,
            budgetTokenDecimals = budgetTokenDecimals,
            prepareGrant = prepareGrant,
            authorizeGrant = authorizeGrant,
            reauth = reauth,
            allowlistTokens = allowlistTokens,
        )
    }
    when (viewModel.step) {
        FollowStep.SelectTrader -> FollowTraderScreen(viewModel, onBack = onExit, modifier = modifier)
        FollowStep.ModeSelect -> ModeSelectScreen(viewModel, onBack = viewModel::back, modifier = modifier)
        FollowStep.Budget -> BudgetScreen(viewModel, onBack = viewModel::back, modifier = modifier)
        FollowStep.Review -> FollowReviewScreen(viewModel, onBack = viewModel::back, modifier = modifier)
        FollowStep.Done -> LaunchedEffect(Unit) { onExit() } // activated → leave the flow (overview = KAN-157)
    }
}
