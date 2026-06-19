package com.tneff.cyppie.feature.portfolio

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.tneff.cyppie.designsystem.theme.CryptasaTheme
import com.tneff.cyppie.designsystem.theme.ThemeMode
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.portfolio.AllocationSlice
import com.tneff.cyppie.portfolio.ApproxReason
import com.tneff.cyppie.portfolio.Holding
import com.tneff.cyppie.portfolio.Metric
import com.tneff.cyppie.market.Money
import com.tneff.cyppie.portfolio.Portfolio
import com.tneff.cyppie.portfolio.PortfolioToken
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * PF-1 Overview screen behaviour on Desktop (`runComposeUiTest`, ADR-0011). Covers the four states and
 * the FR-9 "≈"/caveat surfacing — the screen is state-driven (no VM/DI) so it stays web-safe + testable.
 */
@OptIn(ExperimentalTestApi::class)
class PortfolioOverviewScreenTest {

    private val account = EvmAddress.parse("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")
    private val eth = PortfolioToken(1L, null, "ETH", 18)
    private val usdc = PortfolioToken(8453L, null, "USDC", 6)
    private fun usd(cents: Long) = Money(cents, 2, "USD")

    private fun overview(pnl: Metric<Money>? = null) = PortfolioOverview(
        portfolio = Portfolio(
            holdings = listOf(Holding(account, eth, Quantity.of(1), usd(500_000))),
            totalValue = Metric.robust(usd(750_000)),
            change24h = Metric.robust(usd(12_345)),
            allocation = listOf(
                AllocationSlice(eth, usd(500_000), 6_667),
                AllocationSlice(usdc, usd(250_000), 3_333),
            ),
        ),
        pnl = pnl,
    )

    @Test
    fun rendersHeadlineMetricsAndAllocation() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                PortfolioOverviewScreen(PortfolioOverviewUiState.Content(overview()), onRetry = {})
            }
        }
        onNodeWithTag(PortfolioTestTags.TOTAL_VALUE).assertTextEquals("$7,500.00")
        onNodeWithTag(PortfolioTestTags.CHANGE_24H).assertTextEquals("+$123.45")
        onNodeWithTag(PortfolioTestTags.ALLOCATION_LIST).assertIsDisplayed()
        onNodeWithTag(PortfolioTestTags.allocationItem(0)).assertIsDisplayed()
        onNodeWithTag(PortfolioTestTags.allocationItem(1)).assertIsDisplayed()
    }

    @Test
    fun robustOverviewHidesCaveatBanner() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                PortfolioOverviewScreen(PortfolioOverviewUiState.Content(overview()), onRetry = {})
            }
        }
        onNodeWithTag(PortfolioTestTags.APPROXIMATE_BANNER).assertDoesNotExist()
    }

    @Test
    fun approximatePnlShowsMarkerAndCaveat() = runComposeUiTest {
        val pnl = Metric.approximate(usd(-5_000), ApproxReason.COST_BASIS_AMBIGUITY)
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                PortfolioOverviewScreen(PortfolioOverviewUiState.Content(overview(pnl)), onRetry = {})
            }
        }
        onNodeWithTag(PortfolioTestTags.APPROXIMATE_BANNER).assertIsDisplayed()
        onNodeWithTag(PortfolioTestTags.PNL).assertTextEquals("≈ -$50.00")
    }

    @Test
    fun emptyStateRendered() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                PortfolioOverviewScreen(PortfolioOverviewUiState.Empty, onRetry = {})
            }
        }
        onNodeWithTag(PortfolioTestTags.SCREEN).assertIsDisplayed()
        onNodeWithTag(PortfolioTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun errorStateRetryInvokesCallback() = runComposeUiTest {
        var retried = false
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                PortfolioOverviewScreen(PortfolioOverviewUiState.Error, onRetry = { retried = true })
            }
        }
        onNodeWithTag(PortfolioTestTags.ERROR).assertIsDisplayed()
        onNodeWithTag(PortfolioTestTags.RETRY).assertIsDisplayed().performClick()
        assertTrue(retried)
    }

    @Test
    fun loadingStateShowsSpinner() = runComposeUiTest {
        setContent {
            CryptasaTheme(ThemeMode.Light) {
                PortfolioOverviewScreen(PortfolioOverviewUiState.Loading, onRetry = {})
            }
        }
        onNodeWithTag(PortfolioTestTags.LOADING).assertIsDisplayed()
    }
}
