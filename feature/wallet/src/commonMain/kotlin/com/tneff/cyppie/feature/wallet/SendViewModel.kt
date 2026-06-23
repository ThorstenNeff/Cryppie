package com.tneff.cyppie.feature.wallet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.evm.abi.Erc20Abi
import com.tneff.cyppie.rpc.FeeData
import com.tneff.cyppie.rpc.ReceiptStatus
import com.tneff.cyppie.send.DecodedCall
import com.tneff.cyppie.send.PreparedSend
import com.tneff.cyppie.send.SendError
import com.tneff.cyppie.send.SendInput
import com.tneff.cyppie.send.SendOrchestrator
import com.tneff.cyppie.wallet.EvmAccount
import com.tneff.cyppie.wallet.SeedSource
import com.tneff.cyppie.walletcore.EvmChain
import com.tneff.cyppie.walletcore.TokenCatalog
import com.tneff.cyppie.walletcore.WalletRepository
import kotlinx.coroutines.launch

private const val NATIVE_TICKER = "ETH"
private const val NATIVE_DECIMALS = 18
private const val NATIVE_GAS = 21_000L // standard ETH-transfer gas, for the native Max fee estimate

/** A sendable asset — native ETH ([token] == null) or a curated ERC-20 on [chain]. */
data class SendAsset(val chain: EvmChain, val symbol: String, val decimals: Int, val token: EvmAddress?)

/** EIP-1559 fee speed (KAN-110 §Fee). Derived from one `getFeeData` so a single fetch drives all three. */
enum class SendFeeTier { SLOW, NORMAL, FAST }

enum class SendStep { AssetSelect, Form, Confirm, Authorize, Status }

/** Recoverable form-level error (stays on the Form; spec error states). */
sealed interface SendFormError {
    /** Balance can't cover amount + worst-case fee; [maxFormatted] is the largest sendable amount. */
    data class Insufficient(val maxFormatted: String) : SendFormError
    /** Fee/prepare transport failure — retryable; Continue blocked. */
    data object Network : SendFormError
    /** A node revert during preparation (e.g. estimateGas) — would fail; not retryable as-is. */
    data class Rejected(val message: String) : SendFormError
}

/** Terminal status after broadcast (spec Status frames). */
sealed interface SendStatus {
    data class Pending(val txHash: String) : SendStatus
    data class Confirmed(val txHash: String) : SendStatus
    /** [rejected] = the node refused (authoritative, no hash); otherwise a network/receipt failure. */
    data class Failed(val txHash: String?, val rejected: Boolean) : SendStatus
}

/**
 * KAN-110 — drives the Send flow over the [SendOrchestrator] (prepare → disclose → sign → broadcast).
 * No blind-signing: [continueToConfirm] runs `prepare` (completes nonce/fee/gas + balance check) and the
 * Confirm screen shows that exact [PreparedSend.disclosure]; [confirmAndSign] signs precisely it
 * (Guardrail #1). Native + ERC-20 (ERC-20 = `transfer` calldata to the token, value 0). Read balances /
 * fee data via [repository] / [feeData]; receipt via [awaitReceipt]. Non-web (in `:feature:wallet`).
 */
class SendViewModel(
    private val repository: WalletRepository,
    private val orchestrator: SendOrchestrator,
    private val feeData: suspend (EvmChain) -> FeeData,
    private val awaitReceipt: suspend (EvmChain, String) -> ReceiptStatus,
    /** Re-auth: decrypt a FRESH per-sign [SeedSource] from the password, or null on a wrong password. */
    private val reauth: suspend (CharArray) -> SeedSource?,
    private val accounts: List<EvmAccount>,
    private val accountIndex: Int,
) : ViewModel() {

    /** All sendable assets across chains: native ETH + curated ERC-20s (TokenCatalog). */
    val assets: List<SendAsset> = EvmChain.entries.flatMap { chain ->
        listOf(SendAsset(chain, NATIVE_TICKER, NATIVE_DECIMALS, null)) +
            TokenCatalog.forChain(chain).map { SendAsset(chain, it.symbol, it.decimals, it.address) }
    }

    var step: SendStep by mutableStateOf(SendStep.AssetSelect); private set
    var asset: SendAsset? by mutableStateOf(null); private set
    var amount: String by mutableStateOf(""); private set
    var recipient: String by mutableStateOf(""); private set
    var feeTier: SendFeeTier by mutableStateOf(SendFeeTier.NORMAL); private set

    /** Selected asset's balance (native or token), once loaded. */
    var available: Quantity? by mutableStateOf(null); private set
    private var fees: FeeData? by mutableStateOf(null)
    var feeUnavailable: Boolean by mutableStateOf(false); private set

    var formError: SendFormError? by mutableStateOf(null); private set
    var preparing: Boolean by mutableStateOf(false); private set
    var prepared: PreparedSend? by mutableStateOf(null); private set
    var signing: Boolean by mutableStateOf(false); private set
    var status: SendStatus? by mutableStateOf(null); private set

    // Re-auth (KAN-110, ADR-0009): a password prompt between disclosure and sign. The correct password
    // decrypts a FRESH per-sign source that signs and is zeroized immediately after (M1) — the shared
    // session never signs. Fail-closed: no sign path runs without passing this prompt.
    var authPassword: String by mutableStateOf(""); private set
    var authError: Boolean by mutableStateOf(false); private set
    var authorizing: Boolean by mutableStateOf(false); private set

    val recipientValid: Boolean get() = EvmAddress.isValid(recipient.trim())
    val amountWei: Quantity? get() = asset?.let { parseTokenAmount(amount, it.decimals) }

    /** Whether [continueToConfirm] is allowed — required fields valid and within the local balance. */
    val canContinue: Boolean
        get() {
            asset ?: return false
            val amt = amountWei ?: return false
            if (amt <= Quantity.ZERO) return false
            if (!recipientValid) return false
            if (formError is SendFormError.Network || feeUnavailable) return false
            val avail = available
            if (avail != null && amt > avail) return false // amount alone over balance (fee checked at prepare)
            return true
        }

    fun selectAsset(a: SendAsset) {
        asset = a
        amount = ""; recipient = ""; feeTier = SendFeeTier.NORMAL
        available = null; fees = null; feeUnavailable = false; formError = null
        step = SendStep.Form
        loadContext(a)
    }

    fun updateAmount(value: String) {
        amount = value
        if (formError is SendFormError.Insufficient) formError = null
    }

    fun updateRecipient(value: String) { recipient = value }

    fun selectFeeTier(tier: SendFeeTier) { feeTier = tier }

    /** Selected tier's max fee per gas formatted in gwei (form hint); null until fee data loads. */
    fun feeEstimateGwei(): String? = tierFees(fees, feeTier)?.let { formatTokenAmount(it.first, 9) }

    /** Largest sendable amount of the selected asset; native subtracts the worst-case fee. */
    fun applyMax() {
        val a = asset ?: return
        val avail = available ?: return
        amount = if (a.token == null) {
            val (maxFee, _) = tierFees(fees, feeTier) ?: return
            val fee = Quantity.of(NATIVE_GAS) * maxFee
            val net = if (avail > fee) avail - fee else Quantity.ZERO
            formatTokenAmount(net, a.decimals, maxFractionDigits = a.decimals)
        } else {
            formatTokenAmount(avail, a.decimals, maxFractionDigits = a.decimals)
        }
    }

    fun back() {
        when (step) {
            SendStep.Form -> { step = SendStep.AssetSelect; asset = null }
            SendStep.Confirm -> { step = SendStep.Form; prepared = null }
            SendStep.Authorize -> { step = SendStep.Confirm; authPassword = ""; authError = false }
            else -> {}
        }
    }

    /** Confirm → open the re-auth gate (does not sign yet). */
    fun requestAuth() {
        if (prepared == null) return
        authPassword = ""
        authError = false
        step = SendStep.Authorize
    }

    fun updateAuthPassword(value: String) {
        authPassword = value
        if (authError) authError = false
    }

    /**
     * Re-auth then sign (fail-closed, ADR-0009): decrypt a FRESH per-sign source from the password; only
     * on success does [signWith] run `signAndBroadcast` (signs exactly `prepared` — TOCTOU preserved —
     * then zeroizes that source, M1). A wrong password stays on the Authorize step and never signs.
     */
    fun authorizeAndSign() {
        if (prepared == null || authorizing) return
        val pw = authPassword.toCharArray()
        authorizing = true
        authError = false
        viewModelScope.launch {
            val src = runCatching { reauth(pw) }.getOrNull()
            pw.fill(' ')
            authorizing = false
            if (src != null) {
                authPassword = "" // drop the password reference once it's served its purpose (review a)
                signWith(src)
            } else {
                authError = true
            }
        }
    }

    /**
     * Biometric re-auth (KAN-119): sign with a fresh biometric-unlocked [source] — same fail-closed/M1
     * path as the password gate ([signWith] signs exactly `prepared`, then zeroizes the source). The
     * caller (Authorize screen) passes a non-null source only on a successful biometric prompt.
     */
    fun submitBiometricSource(source: SeedSource) {
        if (prepared == null || authorizing || signing) return
        authPassword = ""
        signWith(source)
    }

    /** The disclosed amount in the asset's units (from `prepared`, not the form — L1). */
    fun disclosedAmount(p: PreparedSend): Quantity =
        when (val c = p.disclosure.call) {
            is DecodedCall.Erc20Transfer -> c.amount
            else -> p.disclosure.value
        }

    /** Run prepare with the chosen tier's fee (fill-not-override) → Confirm, or map the error to the form. */
    fun continueToConfirm() {
        val a = asset ?: return
        val amt = amountWei ?: return
        val to = runCatching { EvmAddress.parse(recipient.trim()) }.getOrNull() ?: return
        val (maxFee, maxPriority) = tierFees(fees, feeTier) ?: run { formError = SendFormError.Network; return }
        val from = accounts[accountIndex].address
        preparing = true
        formError = null
        viewModelScope.launch {
            val input = if (a.token == null) {
                SendInput(from = from, to = to, value = amt, chain = a.chain, maxFeePerGas = maxFee, maxPriorityFeePerGas = maxPriority)
            } else {
                SendInput(
                    from = from, to = a.token, value = Quantity.ZERO, data = Erc20Abi.transfer(to, amt),
                    chain = a.chain, maxFeePerGas = maxFee, maxPriorityFeePerGas = maxPriority,
                )
            }
            runCatching { orchestrator.prepare(input, accounts) }
                .onSuccess { prepared = it; step = SendStep.Confirm }
                .onFailure { e ->
                    formError = when (e) {
                        is SendError.InsufficientFunds -> SendFormError.Insufficient(insufficientMax(a, e, amt))
                        is SendError.TransactionRejected -> SendFormError.Rejected(e.message ?: "")
                        else -> SendFormError.Network
                    }
                }
            preparing = false
        }
    }

    /**
     * Sign exactly the disclosed tx with the freshly-unlocked [src] and broadcast, then track the
     * receipt. [src] is the per-sign source from the re-auth gate; `signAndBroadcast` closes/zeroizes it
     * right after signing (M1 — minimal signing-key window). The shared session is never used to sign.
     */
    private fun signWith(src: SeedSource) {
        val p = prepared ?: return
        signing = true
        status = null
        step = SendStep.Status
        viewModelScope.launch {
            runCatching { orchestrator.signAndBroadcast(p, src) }
                .onSuccess { result ->
                    val hash = result.txHash
                    status = SendStatus.Pending(hash)
                    runCatching { awaitReceipt(p.disclosure.chain, hash) }
                        .onSuccess { rs ->
                            status = if (rs == ReceiptStatus.SUCCESS) SendStatus.Confirmed(hash)
                                     else SendStatus.Failed(hash, rejected = false)
                        }
                        // Receipt timeout/transport: the tx may still confirm — keep Pending (explorer link).
                        .onFailure { /* leave as Pending(hash) */ }
                }
                .onFailure { e ->
                    status = when (e) {
                        is SendError.TransactionRejected -> SendStatus.Failed(null, rejected = true)
                        else -> SendStatus.Failed(null, rejected = false)
                    }
                }
            signing = false
        }
    }

    private fun loadContext(a: SendAsset) {
        viewModelScope.launch {
            runCatching {
                val bal = if (a.token == null) repository.nativeBalance(accountIndex, a.chain)
                          else repository.tokenBalance(a.token, accountIndex, a.chain)
                bal to feeData(a.chain)
            }.onSuccess { (bal, fd) -> available = bal; fees = fd; feeUnavailable = false }
             .onFailure { feeUnavailable = true; fees = null }
        }
    }

    private fun tierFees(fd: FeeData?, tier: SendFeeTier): Pair<Quantity, Quantity>? {
        fd ?: return null
        return when (tier) {
            SendFeeTier.NORMAL -> fd.maxFeePerGas to fd.maxPriorityFeePerGas
            SendFeeTier.SLOW -> (fd.baseFeePerGas + fd.maxPriorityFeePerGas) to fd.maxPriorityFeePerGas
            SendFeeTier.FAST -> {
                val p = fd.maxPriorityFeePerGas * Quantity.of(2)
                (fd.maxFeePerGas + p) to p
            }
        }
    }

    private fun insufficientMax(a: SendAsset, e: SendError.InsufficientFunds, amt: Quantity): String =
        if (a.token == null) {
            val fee = if (e.required > amt) e.required - amt else Quantity.ZERO
            val max = if (e.balance > fee) e.balance - fee else Quantity.ZERO
            formatTokenAmount(max, a.decimals)
        } else {
            formatTokenAmount(available ?: Quantity.ZERO, a.decimals)
        }

    /** The actual recipient (for ERC-20 it's inside the decoded transfer, not `disclosure.to` = token). */
    fun disclosedRecipient(p: PreparedSend): EvmAddress =
        when (val c = p.disclosure.call) {
            is DecodedCall.Erc20Transfer -> c.recipient
            else -> p.disclosure.to
        }
}

/** Block-explorer tx URL (no explorer in EvmChain yet — KAN-110 wires the two MVP chains). */
fun explorerTxUrl(chain: EvmChain, txHash: String): String = when (chain) {
    EvmChain.ETHEREUM -> "https://etherscan.io/tx/$txHash"
    EvmChain.BASE -> "https://basescan.org/tx/$txHash"
    EvmChain.BASE_SEPOLIA -> "https://sepolia.basescan.org/tx/$txHash"
}
