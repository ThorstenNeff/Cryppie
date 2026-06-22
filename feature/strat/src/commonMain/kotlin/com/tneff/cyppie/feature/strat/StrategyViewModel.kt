package com.tneff.cyppie.feature.strat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.launch

enum class StratStep { Setup, Review, Done }

enum class StratError { ENTER_BUDGET, SUM_NOT_100, MIN_TOKENS, INVALID_TOKEN, WRONG_PASSWORD, VERIFY_FAILED, SUBMIT_FAILED }

/** One editable target-allocation row in `Strat1-Setup` (basket token + its weight %). */
data class TargetRow(val token: String = "", val weight: String = "")

/**
 * KAN-166 — the Strategy **setup → review → sign** VM (PRD-07b). Pure functional lambda-seams (like Copy's
 * FollowViewModel) so it stays unit-testable + decoupled from `:aa`: [prepareGrant] builds the no-blind
 * sell-side [StrategyGrantPreview], [authorizeGrant] runs the on-device verify→sign→submit (Dev-2 KAN-165),
 * [reauth] mints a fresh per-op [SeedSource] (ADR-0009). The app shell binds them to [StubStrategyGrantService]
 * now and to the real `:aa` service later (the VM doesn't change).
 */
class StrategyViewModel(
    private val budgetTokenDecimals: Int,
    private val prepareGrant: suspend (targets: List<BasketTarget>, budgetBaseUnits: String) -> StrategyGrantPreview,
    private val authorizeGrant: suspend (preview: StrategyGrantPreview, seed: SeedSource) -> Unit,
    private val reauth: suspend (password: String) -> SeedSource?,
) : ViewModel() {

    var step: StratStep by mutableStateOf(StratStep.Setup); private set
    var rows: List<TargetRow> by mutableStateOf(listOf(TargetRow(), TargetRow())); private set // min two tokens
    var budget: String by mutableStateOf(""); private set
    var error: StratError? by mutableStateOf(null); private set
    var submitting: Boolean by mutableStateOf(false); private set
    var prepared: StrategyGrantPreview? by mutableStateOf(null); private set

    fun setToken(index: Int, value: String) { rows = rows.mapIndexed { i, r -> if (i == index) r.copy(token = value) else r }; invalidate() }
    fun setWeight(index: Int, value: String) { rows = rows.mapIndexed { i, r -> if (i == index) r.copy(weight = value.filter { it.isDigit() }) else r }; invalidate() }
    fun addToken() { rows = rows + TargetRow() }
    fun removeToken(index: Int) { if (rows.size > 2) { rows = rows.filterIndexed { i, _ -> i != index }; invalidate() } }
    fun enterBudget(value: String) { budget = value.filter { it.isDigit() }; invalidate() } // not setBudget (clashes with var budget's setter)

    /** Any setup edit invalidates a prior review — the user must re-verify what they're about to sign. */
    private fun invalidate() { prepared = null; error = null }

    /** Live sum of the entered weights (the donut/bar + the `strat_total` line render this). */
    val totalWeight: Int get() = rows.sumOf { it.weight.toIntOrNull() ?: 0 }

    /** Continue is enabled only at a valid allocation (≥2 tokens, weights = 100%) + a budget. */
    val setupReady: Boolean get() = rows.count { it.token.isNotBlank() } >= 2 && totalWeight == 100 && budget.isNotBlank()

    private fun validTargets(): List<BasketTarget>? {
        val filled = rows.filter { it.token.isNotBlank() || it.weight.isNotBlank() }
        if (filled.count { it.token.isNotBlank() } < 2) { error = StratError.MIN_TOKENS; return null }
        if (filled.any { !EvmAddress.isValid(it.token) }) { error = StratError.INVALID_TOKEN; return null }
        if (filled.sumOf { it.weight.toIntOrNull() ?: 0 } != 100) { error = StratError.SUM_NOT_100; return null }
        return filled.map { BasketTarget(it.token, it.weight.toInt()) }
    }

    /** Scale the whole-unit [budget] to base units, float-free (append 10^decimals zeros). */
    private fun scaledBudget(): String = if (budget.isBlank()) "0" else budget + "0".repeat(budgetTokenDecimals)

    /** Phase 1 — build + verify the grant on-device, then expose [prepared] for the no-blind disclosure. */
    fun review() {
        if (budget.isBlank()) { error = StratError.ENTER_BUDGET; return }
        val targets = validTargets() ?: return
        error = null; submitting = true
        viewModelScope.launch {
            runCatching { prepareGrant(targets, scaledBudget()) }
                .onSuccess { prepared = it; submitting = false; step = StratStep.Review }
                .onFailure { prepared = null; submitting = false; error = StratError.VERIFY_FAILED }
        }
    }

    /** Phase 2 — re-auth → on-device sign of the VERIFIED grant (the seam owns the seed-zeroize; no double-close). */
    fun confirm(password: String) {
        val preview = prepared ?: run { error = StratError.VERIFY_FAILED; return }
        error = null; submitting = true
        viewModelScope.launch {
            val result: StratError? = runCatching {
                val source = reauth(password) ?: return@runCatching StratError.WRONG_PASSWORD
                authorizeGrant(preview, source)
                null
            }.getOrElse { StratError.SUBMIT_FAILED }
            submitting = false
            if (result == null) step = StratStep.Done else error = result
        }
    }

    fun backToSetup() { step = StratStep.Setup; error = null }

    /** The verified sell-cap in whole units (float-free) for the disclosure. */
    fun capHuman(base: String): String = humanAmount(base, budgetTokenDecimals)
}
