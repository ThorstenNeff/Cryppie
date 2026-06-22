package com.tneff.cyppie.feature.strat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tneff.cyppie.wallet.SeedSource

/**
 * KAN-166 — public entry for the Smart-Strategies **setup → review → sign** flow (PRD-07b). The app shell
 * injects the crypto/network seams ([prepareGrant]/[authorizeGrant], bound to [StubStrategyGrantService] now
 * and Dev-2's `:aa` strategy service later), the budget-token [budgetTokenDecimals], and the [reauth] source
 * factory; [onExit] leaves the flow (back from Setup, or after a completed grant → the Strat list). Mirrors
 * `CopyRoot`.
 */
@Composable
fun StrategyRoot(
    budgetTokenDecimals: Int,
    prepareGrant: suspend (targets: List<BasketTarget>, budgetBaseUnits: String) -> StrategyGrantPreview,
    authorizeGrant: suspend (preview: StrategyGrantPreview, seed: SeedSource) -> Unit,
    reauth: suspend (password: String) -> SeedSource?,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StrategyViewModel = viewModel(key = "strat_setup") {
        StrategyViewModel(
            budgetTokenDecimals = budgetTokenDecimals,
            prepareGrant = prepareGrant,
            authorizeGrant = authorizeGrant,
            reauth = reauth,
        )
    }
    when (viewModel.step) {
        StratStep.Setup -> StrategySetupScreen(viewModel, onBack = onExit, modifier = modifier)
        StratStep.Review -> StrategyReviewScreen(viewModel, onBack = viewModel::backToSetup, modifier = modifier)
        StratStep.Done -> LaunchedEffect(Unit) { onExit() } // granted → leave the flow (overview = Strat list)
    }
}
