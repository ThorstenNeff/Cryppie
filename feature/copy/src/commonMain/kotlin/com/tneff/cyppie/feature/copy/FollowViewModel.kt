package com.tneff.cyppie.feature.copy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.launch

/** The Follow-Trader flow steps (KAN-155): pick trader → budget → verified disclosure+sign → done. */
enum class FollowStep { SelectTrader, Budget, Review, Done }

/**
 * KAN-155 — the Copy / Follow-Trader flow VM. Linear: [toBudget] validates the trader (EIP-55 + self-check)
 * → [review] builds + **on-device verifies** the grant ([FollowGrantService.prepare], fail-closed) and
 * exposes [prepared] (what the disclosure renders — only verified fields) → [confirm] re-auths → on-device
 * sign + activate. No blind signing: [confirm] is valid only while [prepared] is present, and editing the
 * budget clears it.
 */
class FollowViewModel(
    private val service: FollowGrantService,
    private val owner: EvmAddress,
    private val budgetTokenDecimals: Int,
    private val reauth: suspend (password: String) -> SeedSource?,
) : ViewModel() {

    var step: FollowStep by mutableStateOf(FollowStep.SelectTrader); private set
    var trader: String by mutableStateOf(""); private set
    var budget: String by mutableStateOf(""); private set // whole units
    var preparing: Boolean by mutableStateOf(false); private set
    var submitting: Boolean by mutableStateOf(false); private set
    var error: CopyError? by mutableStateOf(null); private set
    var prepared: PreparedFollow? by mutableStateOf(null); private set

    fun enterTrader(value: String) { trader = value.trim(); error = null; prepared = null }
    fun enterBudget(value: String) { budget = value.filter { it.isDigit() }; error = null; prepared = null }

    /** S1 → S2: EIP-55 validity (mixed-case checksum enforced; all-lower/upper allowed) + self-copy guard. */
    fun toBudget() {
        if (!EvmAddress.isValid(trader)) { error = CopyError.INVALID_ADDRESS; return }
        if (EvmAddress.parse(trader) == owner) { error = CopyError.SELF_COPY; return }
        error = null
        step = FollowStep.Budget
    }

    /** S2 → S3: build + on-device-verify the grant (fail-closed); on success expose [prepared] for disclosure. */
    fun review() {
        if (budget.isBlank()) { error = CopyError.ENTER_BUDGET; return }
        error = null
        preparing = true
        viewModelScope.launch {
            runCatching { service.prepare(trader, scaledBudget()) }
                .onSuccess { prepared = it; step = FollowStep.Review }
                .onFailure { prepared = null; error = CopyError.VERIFY_FAILED }
            preparing = false
        }
    }

    /** S3: re-auth → on-device sign of the VERIFIED grant → activate. Valid only with [prepared] present. */
    fun confirm(password: String) {
        val p = prepared ?: run { error = CopyError.VERIFY_FAILED; return }
        if (submitting) return // busy-guard
        error = null
        submitting = true
        viewModelScope.launch {
            val outcome = runCatching {
                val source = reauth(password) ?: return@runCatching CopyError.WRONG_PASSWORD
                service.activate(p, source) // signs + zeroizes the source
                null
            }.getOrElse { CopyError.SUBMIT_FAILED }
            submitting = false
            if (outcome == null) step = FollowStep.Done else error = outcome
        }
    }

    /** Back-nav within the flow; clears the prepared grant when leaving Review (re-verify required). */
    fun back() {
        error = null
        step = when (step) {
            FollowStep.Budget -> FollowStep.SelectTrader
            FollowStep.Review -> { prepared = null; FollowStep.Budget }
            else -> step
        }
    }

    /** A base-unit cap → whole units, float-free, via the budget-token decimals (for display). */
    fun capHuman(capBaseUnits: String): String {
        val d = budgetTokenDecimals
        if (d <= 0) return capBaseUnits
        val digits = capBaseUnits.trimStart('0').ifEmpty { "0" }
        val padded = digits.padStart(d + 1, '0')
        val frac = padded.takeLast(d).trimEnd('0')
        return if (frac.isEmpty()) padded.dropLast(d) else "${padded.dropLast(d)}.$frac"
    }

    /** Whole-unit [budget] → base units, float-free (append 10^decimals zeros). */
    private fun scaledBudget(): String =
        if (budget.isBlank()) "0" else budget + "0".repeat(budgetTokenDecimals)
}
