package com.tneff.cyppie.feature.dca

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.aa.AaSigner
import com.tneff.cyppie.aa.BuiltEnable
import com.tneff.cyppie.aa.DcaApi
import com.tneff.cyppie.aa.DcaEnableBuilder
import com.tneff.cyppie.aa.ScopedAction
import com.tneff.cyppie.aa.SessionConfig
import com.tneff.cyppie.aa.SpendingLimit
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.GrantVerificationException
import com.tneff.cyppie.evm.SmartSessionGrantVerifier
import com.tneff.cyppie.evm.VerifiedGrant
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
    val spendTokenDecimals: Int, // P1-5: scale the human cap to base units (e.g. USDC = 6)
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
    // KAN-144: the enable is built ON-DEVICE (no backend endpoint). [readNonce] reads the SmartSession
    // module nonce via RPC (keyed by permissionId+account); [genSalt] is the app-chosen 32-byte session salt.
    private val readNonce: suspend (permissionId: String, account: String, chainId: Long) -> Long,
    private val genSalt: () -> String,
    private val reauth: suspend (password: String) -> SeedSource?,
) : ViewModel() {

    var capAmount: String by mutableStateOf(""); private set
    var frequency: DcaFrequency by mutableStateOf(DcaFrequency.WEEKLY); private set
    var durationDays: Int by mutableStateOf(90); private set
    var reviewing: Boolean by mutableStateOf(false); private set
    var submitting: Boolean by mutableStateOf(false); private set
    var error: DcaError? by mutableStateOf(null); private set
    var granted: Boolean by mutableStateOf(false); private set

    /**
     * KAN-144 — the **on-device verified** grant: every field is decoded from the bytes inside the signed
     * enable digest. Non-null only after a successful [review]; the disclosure renders THIS (never the raw
     * backend material), and [grant] signs only while it's present. Editing inputs clears it (re-review).
     */
    var verified: VerifiedGrant? by mutableStateOf(null); private set

    // P1-6 (TOCTOU): one immutable config is memoized + invalidated on input change, so the config sent to
    // the backend is byte-stable; KAN-144 then proves the returned digest encodes exactly that.
    private var configCache: SessionConfig? = null
    // The verified on-device enable (its digestToSign is signed) + the config it was built from (registered).
    private var pendingEnable: BuiltEnable? = null
    private var pendingConfig: SessionConfig? = null

    fun setCap(value: String) { capAmount = value.filter { it.isDigit() }; invalidate() }
    fun selectFrequency(value: DcaFrequency) { frequency = value; invalidate() }
    fun setDuration(days: Int) { durationDays = days; invalidate() }

    /** Any input change invalidates a prior review — the user must re-verify what they're about to sign. */
    private fun invalidate() { configCache = null; verified = null; pendingEnable = null; pendingConfig = null }

    /** P1-5: scale the whole-unit [capAmount] to base units, float-free (append 10^decimals zeros). */
    private fun scaledCap(): String =
        if (capAmount.isBlank()) "0" else capAmount + "0".repeat(params.spendTokenDecimals)

    /**
     * The VERIFIED cap rendered in whole units, float-free, via the configured token decimals. If the
     * verified token differs from the one we configured (tamper signal), decimals would be wrong — so we
     * fall back to raw base units; the disclosure always also shows [VerifiedGrant.spendToken] so the
     * mismatch is itself visible.
     */
    fun capHuman(v: VerifiedGrant): String {
        if (!v.spendToken.equals(params.spendToken, ignoreCase = true)) return v.capBaseUnits
        val d = params.spendTokenDecimals
        if (d <= 0) return v.capBaseUnits
        val digits = v.capBaseUnits.trimStart('0').ifEmpty { "0" }
        val padded = digits.padStart(d + 1, '0')
        val frac = padded.takeLast(d).trimEnd('0')
        return if (frac.isEmpty()) padded.dropLast(d) else "${padded.dropLast(d)}.$frac"
    }

    /** The §2 session the user is authorizing — memoized; what the disclosure shows AND what gets signed. */
    fun buildConfig(): SessionConfig {
        configCache?.let { return it }
        val validUntil = nowEpochSeconds() + durationDays.toLong() * 86_400L
        val maxOps = (durationDays.toLong() * 86_400L / frequency.seconds).toInt().coerceAtLeast(1)
        return SessionConfig(
            chainId = params.chainId,
            account = owner.value,
            actions = listOf(
                ScopedAction(
                    target = params.router,
                    selector = params.swapSelector,
                    spendingLimits = listOf(SpendingLimit(token = params.spendToken, cap = scaledCap())),
                    rollingWindowSeconds = frequency.seconds,
                    usageLimit = maxOps,
                    validUntil = validUntil,
                ),
            ),
        ).also { configCache = it }
    }

    /**
     * Phase 1 (KAN-144) — **review**: build the enable **entirely on-device** (no backend endpoint —
     * [DcaEnableBuilder] over client-pinned constants; the only runtime input is the RPC-read [readNonce]),
     * then **`verifyGrant`** as a defensive self-check (re-derive the display from the signed bytes; catches
     * any construction/serialization bug). Success exposes [verified]; a [GrantVerificationException] is
     * fail-closed — [verified] stays null so [grant] can't sign. No seed is in scope here.
     */
    fun review() {
        if (capAmount.isBlank()) { error = DcaError.ENTER_AMOUNT; return }
        error = null
        reviewing = true
        viewModelScope.launch {
            val outcome = runCatching {
                val config = buildConfig()
                val action = config.actions.first()
                val windowStart = action.validUntil - durationDays.toLong() * 86_400L // = the config's grant-time start
                val salt = genSalt()
                val initData = DcaEnableBuilder.ownableInitData(owner.value)
                val permissionId = DcaEnableBuilder.permissionId(DcaEnableBuilder.OWNABLE_VALIDATOR, initData, salt)
                val nonce = readNonce(permissionId, owner.value, config.chainId) // SmartSession module, via RPC
                val enable = DcaEnableBuilder.build(config, owner = owner.value, salt = salt, nonce = nonce, windowStart = windowStart)
                val v = SmartSessionGrantVerifier.verifyGrant(
                    account = owner.value, // = keyManager.deriveAddress(0); never a backend address
                    chainId = enable.chainId,
                    sessionValidator = enable.sessionValidator,
                    sessionValidatorInitData = enable.sessionValidatorInitData,
                    salt = enable.salt,
                    nonce = enable.nonce,
                    permissions = enable.permissions, // already the :evm SignedPermissions
                    digestToSign = enable.digestToSign,
                ) // throws GrantVerificationException on ANY mismatch → we must NOT sign
                pendingEnable = enable
                pendingConfig = config
                v
            }
            reviewing = false
            outcome
                .onSuccess { verified = it }
                .onFailure { e ->
                    verified = null; pendingEnable = null; pendingConfig = null
                    error = if (e is GrantVerificationException) DcaError.VERIFY_FAILED else DcaError.AUTHORIZE_FAILED
                }
        }
    }

    /**
     * Phase 2 — **sign** the verified grant: re-auth → on-device sign of the (already-verified) enable
     * digest → register. Only valid after a successful [review] ([verified]/[pendingEnable] present). The
     * seed window is minimal (HIGH-1/M1): reauth → sign → zeroize, with the grantSession network call only
     * AFTER the seed is closed.
     */
    fun grant(password: String) {
        val enable = pendingEnable
        val config = pendingConfig
        if (verified == null || enable == null || config == null) { error = DcaError.VERIFY_FAILED; return }
        error = null
        submitting = true
        viewModelScope.launch {
            val outcome = runCatching {
                val source = reauth(password) ?: return@runCatching DcaError.WRONG_PASSWORD
                val signature = try {
                    signer.signDigest(enable.digestToSign, owner, source) // signs the VERIFIED digest + zeroizes
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
