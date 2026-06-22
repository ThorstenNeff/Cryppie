package com.tneff.cyppie.feature.copy

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.aa.CopyGrantPreview
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.launch

/** The Follow-Trader flow steps (KAN-155): trader → mirror-mode → budget → verified disclosure+sign → done. */
enum class FollowStep { SelectTrader, ModeSelect, Budget, Review, Done }

/**
 * The mirror mode (KAN-161, user-selectable): [FIXED] copies the trader into ONE pre-chosen [tokenOut] (the
 * receive token is disclosed up-front); [DYNAMIC] mirrors whatever the trader buys (limited to the backend's
 * curated allowlist — off-list buys are skipped server-side), which needs an explicit risk acknowledgement.
 * The mode only sets the **mirror-time** `tokenOut` (fixed = the picked token, dynamic = null) — it is NOT in
 * the signed enable digest, so the on-chain guarantee (cap/spend-token/router/window) is identical either way.
 */
enum class CopyMode { FIXED, DYNAMIC }

/** A curated receive-token for the fixed-mode picker (KAN-168 F5) — from the app shell's TokenCatalog. */
data class CopyToken(val address: String, val symbol: String, val name: String)

/**
 * KAN-155 — the Copy / Follow-Trader flow VM. Linear: [toMode] validates the trader (EIP-55 + self-check) →
 * [toBudget] requires a chosen [mode] (fixed → a valid [tokenOut]; dynamic → the [dynRiskAck] checkbox) →
 * [review] builds + **on-device verifies** the grant ([prepareGrant], fail-closed) and exposes [prepared]
 * (Dev-2's `:aa` [CopyGrantPreview] — the disclosure renders only its verified fields) → [confirm] re-auths
 * → on-device sign + submit ([authorizeGrant]). No blind signing: [confirm] is valid only while [prepared] is
 * present, and editing the trader/mode/budget clears it.
 *
 * Crypto/network is injected as two suspending seams (testable, decoupled from `:aa` construction): the app
 * shell binds them to Dev-2's `FollowGrantService.prepareGrant`/`authorizeGrant` and assembles the full
 * `CopyScopeRequest` (router/selector/chain/window pins) around (trader, budget, tokenOut).
 */
class FollowViewModel(
    private val owner: EvmAddress,
    private val budgetTokenDecimals: Int,
    private val prepareGrant: suspend (trader: String, budgetBaseUnits: String, tokenOut: String?) -> CopyGrantPreview,
    private val authorizeGrant: suspend (preview: CopyGrantPreview, seed: SeedSource) -> Unit,
    private val reauth: suspend (password: String) -> SeedSource?,
    // KAN-168 F5: the curated receive-token allowlist for fixed mode (from the app shell's TokenCatalog). Empty
    // = the picker shows the no-tokens state. Picking is the ONLY way to set tokenOut (no free hex entry).
    val allowlistTokens: List<CopyToken> = emptyList(),
) : ViewModel() {

    var step: FollowStep by mutableStateOf(FollowStep.SelectTrader); private set
    var trader: String by mutableStateOf(""); private set
    var mode: CopyMode? by mutableStateOf(null); private set
    var tokenOut: String by mutableStateOf(""); private set     // fixed mode: the receive token to copy
    var dynRiskAck: Boolean by mutableStateOf(false); private set // dynamic mode: the mandatory risk consent
    var budget: String by mutableStateOf(""); private set        // whole units
    var preparing: Boolean by mutableStateOf(false); private set
    var submitting: Boolean by mutableStateOf(false); private set
    var error: CopyError? by mutableStateOf(null); private set
    var prepared: CopyGrantPreview? by mutableStateOf(null); private set
    var tokenPickerOpen: Boolean by mutableStateOf(false); private set // KAN-168 F5: the token-picker bottom-sheet

    /** The currently-selected fixed-mode token (for the picker entry row), or null if none picked yet. */
    val selectedToken: CopyToken? get() = allowlistTokens.firstOrNull { it.address.equals(tokenOut, ignoreCase = true) }

    fun openTokenPicker() { tokenPickerOpen = true }
    fun dismissTokenPicker() { tokenPickerOpen = false }
    /** Pick a curated token (the only way to set the fixed-mode receive token — no free hex entry). */
    fun selectToken(token: CopyToken) { tokenOut = token.address; tokenPickerOpen = false; error = null; prepared = null }

    fun enterTrader(value: String) { trader = value.trim(); error = null; prepared = null }
    fun enterTokenOut(value: String) { tokenOut = value.trim(); error = null; prepared = null }
    fun acknowledgeRisk(checked: Boolean) { dynRiskAck = checked; error = null }
    fun enterBudget(value: String) { budget = value.filter { it.isDigit() }; error = null; prepared = null }

    /** Pick the mirror mode; selecting it resets the now-irrelevant other-mode input + any prior verification. */
    fun selectMode(value: CopyMode) {
        mode = value
        error = null
        prepared = null
        if (value == CopyMode.FIXED) dynRiskAck = false else tokenOut = ""
    }

    /** Whether the mode step can proceed: a mode is chosen and its mode-specific gate is satisfied. */
    val modeReady: Boolean get() = when (mode) {
        CopyMode.FIXED -> EvmAddress.isValid(tokenOut)
        CopyMode.DYNAMIC -> dynRiskAck
        null -> false
    }

    /** S1 → mode: EIP-55 validity (mixed-case checksum enforced; all-lower/upper allowed) + self-copy guard. */
    fun toMode() {
        if (!EvmAddress.isValid(trader)) { error = CopyError.INVALID_ADDRESS; return }
        if (EvmAddress.parse(trader) == owner) { error = CopyError.SELF_COPY; return }
        error = null
        step = FollowStep.ModeSelect
    }

    /**
     * mode → S2: require a chosen mode + its gate (fixed = valid token, dynamic = risk ack). The Continue
     * button is disabled until [modeReady], so an unmet gate simply no-ops here (fixed → INVALID_ADDRESS is
     * the one case worth surfacing inline, if the field holds a non-empty but malformed address).
     */
    fun toBudget() {
        if (!modeReady) {
            if (mode == CopyMode.FIXED && tokenOut.isNotBlank()) error = CopyError.INVALID_ADDRESS
            return
        }
        error = null
        step = FollowStep.Budget
    }

    /** S2 → S3: build + on-device-verify the grant (fail-closed); on success expose [prepared] for disclosure. */
    fun review() {
        if (budget.isBlank()) { error = CopyError.ENTER_BUDGET; return }
        error = null
        preparing = true
        val tokenOutArg = if (mode == CopyMode.FIXED) tokenOut else null // dynamic → backend derives per trade
        viewModelScope.launch {
            runCatching { prepareGrant(trader, scaledBudget(), tokenOutArg) }
                .onSuccess { prepared = it; step = FollowStep.Review }
                .onFailure { prepared = null; error = CopyError.VERIFY_FAILED }
            preparing = false
        }
    }

    /** S3: re-auth → on-device sign of the VERIFIED grant → submit. Valid only with [prepared] present. */
    fun confirm(password: String) {
        val p = prepared ?: run { error = CopyError.VERIFY_FAILED; return }
        if (submitting) return // busy-guard
        error = null
        submitting = true
        viewModelScope.launch {
            val outcome = runCatching {
                val source = reauth(password) ?: return@runCatching CopyError.WRONG_PASSWORD
                // 🔒 Seed-ownership: the unlocked [source] is handed to [authorizeGrant] (Dev-2's
                // EnableBroadcaster), which owns zeroizing it on ALL paths — verify-throw, network-fail, or
                // success (try/finally inside the broadcaster). We must NOT close it here too (no double-close).
                authorizeGrant(p, source)
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
            FollowStep.ModeSelect -> FollowStep.SelectTrader
            FollowStep.Budget -> FollowStep.ModeSelect
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
