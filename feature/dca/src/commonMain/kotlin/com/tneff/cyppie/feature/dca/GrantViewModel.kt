package com.tneff.cyppie.feature.dca

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.aa.AaSigner
import com.tneff.cyppie.aa.DcaApi
import com.tneff.cyppie.aa.ScopedAction
import com.tneff.cyppie.aa.SessionConfig
import com.tneff.cyppie.aa.SpendingLimit
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.SeedSource
import kotlinx.coroutines.launch

/** How often a DCA buy runs (→ the Smart-Session rolling-window for the per-op cap). */
enum class DcaFrequency(val seconds: Long, val label: String) {
    DAILY(86_400L, "Daily"),
    WEEKLY(604_800L, "Weekly"),
}

/** Fixed routing/token params for the DCA grant (the allowed router/selector/spend token; from config). */
data class DcaGrantParams(
    val chainId: Long,
    val router: String,
    val swapSelector: String,
    val spendToken: String,
)

/**
 * KAN-138 (PRD-05 Ph1) — the Smart-Session **grant** VM. Builds a §2 [SessionConfig] from the user's
 * inputs (per-buy [capAmount], [frequency], [durationDays]) over the fixed [params], then grants it:
 * re-auth → backend builds the session-enable owner-userOp ([DcaApi.buildSessionEnable]) → on-device sign
 * of its digest ([AaSigner]) → [DcaApi.grantSession]. The disclosed config is exactly what gets signed
 * (no-blind). The seed source is zeroized by [AaSigner].
 */
class GrantViewModel(
    private val api: DcaApi,
    private val signer: AaSigner,
    private val owner: EvmAddress,
    private val params: DcaGrantParams,
    private val nowEpochSeconds: () -> Long,
    private val reauth: suspend (password: String) -> SeedSource?,
) : ViewModel() {

    var capAmount: String by mutableStateOf(""); private set
    var frequency: DcaFrequency by mutableStateOf(DcaFrequency.WEEKLY); private set
    var durationDays: Int by mutableStateOf(90); private set
    var submitting: Boolean by mutableStateOf(false); private set
    var error: DcaError? by mutableStateOf(null); private set
    var granted: Boolean by mutableStateOf(false); private set

    fun setCap(value: String) { capAmount = value.filter { it.isDigit() } }
    fun selectFrequency(value: DcaFrequency) { frequency = value }
    fun setDuration(days: Int) { durationDays = days }

    /** The §2 session the user is authorizing — built from the inputs; what the disclosure shows + signs. */
    fun buildConfig(): SessionConfig {
        val validUntil = nowEpochSeconds() + durationDays.toLong() * 86_400L
        val maxOps = (durationDays.toLong() * 86_400L / frequency.seconds).toInt().coerceAtLeast(1)
        return SessionConfig(
            chainId = params.chainId,
            account = owner.value,
            actions = listOf(
                ScopedAction(
                    target = params.router,
                    selector = params.swapSelector,
                    spendingLimits = listOf(SpendingLimit(token = params.spendToken, cap = capAmount.ifBlank { "0" })),
                    rollingWindowSeconds = frequency.seconds,
                    usageLimit = maxOps,
                    validUntil = validUntil,
                ),
            ),
        )
    }

    fun grant(password: String) {
        if (capAmount.isBlank()) { error = DcaError.ENTER_AMOUNT; return }
        error = null
        submitting = true
        viewModelScope.launch {
            // HIGH-1 fix: build the enable op (network) FIRST — it needs only the JWT, not the seed. Only
            // THEN re-auth + sign + zeroize, with NOTHING (no network) in between (the SendOrchestrator-M1
            // minimal seed window): a build/grant failure can never leave a decrypted seed un-zeroized.
            val outcome = runCatching {
                val config = buildConfig()
                val enable = api.buildSessionEnable(config) // no seed in scope yet
                val source = reauth(password) ?: return@runCatching DcaError.WRONG_PASSWORD
                val signature = try {
                    signer.signDigest(enable.digestToSign, owner, source) // signs + zeroizes
                } finally {
                    (source as? AutoCloseable)?.close() // defensive: zeroize even if signing throws
                }
                api.grantSession(config, signature) // network AFTER the seed window is closed
                null // success
            }.getOrElse { DcaError.AUTHORIZE_FAILED }
            submitting = false
            when (outcome) {
                null -> granted = true
                else -> error = outcome
            }
        }
    }
}
