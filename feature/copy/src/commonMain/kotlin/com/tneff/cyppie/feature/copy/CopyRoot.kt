package com.tneff.cyppie.feature.copy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.SeedSource

/**
 * KAN-155 — the Copy / Follow-Trader flow host. Linear nav over [FollowViewModel.step]: choose trader →
 * budget → verified disclosure + sign → done. The app shell DI-s [service] (Dev-2's copy grant/crypto,
 * KAN-154 — or the stub until then), the [owner] address (self-copy guard), the budget-token
 * [budgetTokenDecimals], and the [reauth] source factory; [onExit] leaves the flow (back from screen 1, or
 * after activation — the Active-Copies overview is a follow-up once the copy list/revoke API lands).
 */
@Composable
fun CopyRoot(
    service: FollowGrantService,
    owner: EvmAddress,
    budgetTokenDecimals: Int,
    reauth: suspend (password: String) -> SeedSource?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: FollowViewModel = viewModel(key = "copy_follow") {
        FollowViewModel(service = service, owner = owner, budgetTokenDecimals = budgetTokenDecimals, reauth = reauth)
    }
    when (viewModel.step) {
        FollowStep.SelectTrader -> FollowTraderScreen(viewModel, onBack = onExit, modifier = modifier)
        FollowStep.Budget -> BudgetScreen(viewModel, onBack = viewModel::back, modifier = modifier)
        FollowStep.Review -> FollowReviewScreen(viewModel, onBack = viewModel::back, modifier = modifier)
        FollowStep.Done -> LaunchedEffect(Unit) { onExit() } // activated → leave the flow (overview = follow-up)
    }
}
