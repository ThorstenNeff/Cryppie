package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.wallet.tx.Eip1559Transaction

/**
 * The relay's phishing-check verdict (reown `verifyContext.validation`), normalised to a typed status
 * at the controller edge so the UI never string-matches raw SDK tokens (KAN-126 review M1). The reown
 * Validation enums differ subtly across platforms/versions (Android `VALID/INVALID/UNKNOWN`, plus
 * `VERIFIED/SCAM` aliases some surfaces use) — [from] folds them all; anything unrecognised is
 * [Unknown] (fail-safe: an unverifiable origin warns, never silently "verified").
 */
enum class WcVerify {
    Verified, Unknown, Invalid;

    companion object {
        fun from(raw: String?): WcVerify = when (raw?.trim()?.uppercase()) {
            "VALID", "VERIFIED" -> Verified
            "INVALID", "SCAM", "MALICIOUS" -> Invalid
            else -> Unknown // UNKNOWN / null / anything unrecognised
        }
    }
}

/** Dapp identity shown in approval UIs, incl. the relay's phishing-check verdict (FR-3). */
data class WcDappMetadata(
    val name: String,
    val description: String,
    val url: String,
    val icons: List<String> = emptyList(),
    val verify: WcVerify = WcVerify.Unknown,
)

/** An incoming session proposal (chains/methods the dapp requests). */
data class WcSessionProposal(
    val proposalId: String,
    val dapp: WcDappMetadata,
    val chains: List<String>, // CAIP-2, e.g. "eip155:1", "eip155:8453"
    val methods: List<String>,
    val events: List<String> = emptyList(),
)

/** A signing request the wallet must approve & fulfil. The MVP methods (ADR-0015). */
sealed interface WcSigningRequest {
    val address: EvmAddress

    /** `personal_sign` — sign an arbitrary message (EIP-191). */
    data class PersonalSign(val message: ByteArray, override val address: EvmAddress) : WcSigningRequest

    /** `eth_sendTransaction` — sign (and the app broadcasts) a tx the orchestration has completed. */
    data class SendTransaction(override val address: EvmAddress, val transaction: Eip1559Transaction) : WcSigningRequest

    /** `eth_signTypedData_v4` — EIP-712 typed data (digest computed in Chunk 2). */
    data class SignTypedDataV4(override val address: EvmAddress, val typedDataJson: String) : WcSigningRequest
}

/**
 * A request bound to a live session, surfaced **raw** (method + JSON params). `eth_sendTransaction`
 * cannot be fully typed here — it needs L3 completion (nonce/fees) in the app layer — so callers run
 * [decode] and then either sign now or complete-then-sign (see [WcDecodedRequest]).
 */
data class WcSessionRequest(
    val requestId: Long,
    val topic: String,
    val chainId: String, // CAIP-2
    val method: String,
    val params: String, // raw JSON-RPC params array
    val dapp: WcDappMetadata,
)

/** Controller events delivered to `commonMain` as a Flow (ADR-0015). */
sealed interface WcEvent {
    data class OnSessionProposal(val proposal: WcSessionProposal) : WcEvent
    data class OnSessionRequest(val request: WcSessionRequest) : WcEvent
    data class OnSessionSettled(val topic: String) : WcEvent
    data class OnSessionDeleted(val topic: String) : WcEvent
    data class OnError(val message: String) : WcEvent
}

/** Human-readable, full-disclosure view of a request for the approval sheet (FR-3 — no blind signing). */
data class WcRequestDisclosure(
    val method: String,
    val signer: EvmAddress,
    val details: Map<String, String>,
)

/** Full payload disclosure (FR-3): everything the user must see before approving. */
fun WcSigningRequest.disclose(): WcRequestDisclosure = when (this) {
    is WcSigningRequest.PersonalSign -> WcRequestDisclosure(
        method = "personal_sign",
        signer = address,
        details = mapOf("message" to message.asTextOrHex(), "rawHex" to "0x" + Hex.encode(message)),
    )
    is WcSigningRequest.SendTransaction -> WcRequestDisclosure(
        method = "eth_sendTransaction",
        signer = address,
        details = mapOf(
            "to" to (transaction.to?.value ?: "(contract creation)"),
            "value (wei)" to transaction.value.toHex(),
            "data" to "0x" + Hex.encode(transaction.data),
            "nonce" to transaction.nonce.toHex(),
            "gasLimit" to transaction.gasLimit.toHex(),
            "maxFeePerGas" to transaction.maxFeePerGas.toHex(),
            "maxPriorityFeePerGas" to transaction.maxPriorityFeePerGas.toHex(),
            "chainId" to transaction.chainId.toString(),
        ),
    )
    is WcSigningRequest.SignTypedDataV4 -> WcRequestDisclosure(
        method = "eth_signTypedData_v4",
        signer = address,
        details = mapOf("typedData" to typedDataJson),
    )
}

/** Show a message as UTF-8 text when it is printable, otherwise as hex (avoids garbled display). */
private fun ByteArray.asTextOrHex(): String {
    val text = decodeToString()
    val printable = text.none { it.isISOControl() && it != '\n' && it != '\t' && it != '\r' }
    return if (printable) text else "0x" + Hex.encode(this)
}
