package com.tneff.cyppie.walletconnect

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Quantity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Dapp-provided `eth_sendTransaction` parameters — typically **incomplete** (no nonce/fees).
 *
 * **Send-layer contract (security, ADR-0015 / FR-3):**
 * 1. **Ordering (no TOCTOU):** decode → complete (fill nonce/fees) → `disclose` the *completed* tx →
 *    user approve → sign → `respondSessionRequest`. Never approve partial params and fill fees after.
 * 2. **Fill-missing, not override:** preserve every dapp-supplied field ([to]/[value]/[data] and any
 *    dapp [gasLimit]/[maxFeePerGas]); L3 fills only what's null (typically [nonce], and fees if absent).
 *    The signed tx must equal the disclosed tx exactly.
 * 3. **Account binding:** check [from] against the session-approved account and
 *    `WalletConnectSigner.requireSignerMatches` — no silent account override.
 * 4. **Chain binding (replay):** verify [chainId] is among the approved session chains and equals the
 *    signed tx's chainId (ETH 1 / Base 8453).
 */
data class SendTransactionParams(
    val chainId: String, // CAIP-2 of the request (for chain-binding checks)
    val from: EvmAddress,
    val to: EvmAddress?,
    val value: Quantity,
    val data: ByteArray,
    val gasLimit: Quantity?,
    val maxFeePerGas: Quantity?,
    val maxPriorityFeePerGas: Quantity?,
    val nonce: Quantity?,
)

/** Result of [WcSessionRequest.decode]: sign immediately, or complete-then-sign. */
sealed interface WcDecodedRequest {
    /** `personal_sign` / `eth_signTypedData_v4` — sign now via [WalletConnectSigner]. */
    data class SignNow(val request: WcSigningRequest) : WcDecodedRequest

    /** `eth_sendTransaction` — complete (nonce/fees) in the app layer, then sign. */
    data class SendTransaction(val params: SendTransactionParams) : WcDecodedRequest
}

private val requestJson = Json { ignoreUnknownKeys = true }

/**
 * Decodes the raw [WcSessionRequest.method]/[WcSessionRequest.params] into a typed [WcDecodedRequest].
 * Fail-closed: any malformed/unsupported input throws [WalletConnectException.UnsupportedRequest].
 */
fun WcSessionRequest.decode(): WcDecodedRequest = try {
    val args = requestJson.parseToJsonElement(params).jsonArray
    when (method) {
        "personal_sign" -> WcDecodedRequest.SignNow(
            // personal_sign params: [message, address]
            WcSigningRequest.PersonalSign(decodeMessage(args[0].jsonPrimitive.content), address(args[1].jsonPrimitive.content)),
        )
        "eth_signTypedData_v4", "eth_signTypedData" -> WcDecodedRequest.SignNow(
            // typed-data params: [address, typedDataJson] (JSON may arrive as a string or an object)
            WcSigningRequest.SignTypedDataV4(address(args[0].jsonPrimitive.content), typedDataAsString(args[1])),
        )
        "eth_sendTransaction" -> {
            val tx = args[0].jsonObject
            WcDecodedRequest.SendTransaction(
                SendTransactionParams(
                    chainId = chainId,
                    from = address(tx.getValue("from").jsonPrimitive.content),
                    to = tx["to"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { address(it) },
                    value = tx["value"]?.toQuantity() ?: Quantity.ZERO,
                    data = tx["data"]?.jsonPrimitive?.contentOrNull?.let { Hex.decodeOrNull(it) } ?: ByteArray(0),
                    gasLimit = (tx["gas"] ?: tx["gasLimit"])?.toQuantity(),
                    maxFeePerGas = tx["maxFeePerGas"]?.toQuantity(),
                    maxPriorityFeePerGas = tx["maxPriorityFeePerGas"]?.toQuantity(),
                    nonce = tx["nonce"]?.toQuantity(),
                ),
            )
        }
        else -> throw WalletConnectException.UnsupportedRequest("Unsupported WalletConnect method: $method")
    }
} catch (e: WalletConnectException) {
    throw e
} catch (e: Exception) {
    throw WalletConnectException.UnsupportedRequest("Malformed WalletConnect request: ${e.message}")
}

/** personal_sign data is hex-encoded per spec; tolerate plain UTF-8 some dapps send. */
private fun decodeMessage(s: String): ByteArray =
    if (s.startsWith("0x") || s.startsWith("0X")) (Hex.decodeOrNull(s) ?: s.encodeToByteArray()) else s.encodeToByteArray()

private fun typedDataAsString(element: JsonElement): String =
    if (element is JsonObject) element.toString() else element.jsonPrimitive.content

private fun address(hex: String): EvmAddress {
    val bytes = Hex.decodeOrNull(hex) ?: throw WalletConnectException.UnsupportedRequest("Invalid address: $hex")
    return EvmAddress.fromBytes(bytes) // raw 20 bytes; dapp addresses aren't reliably EIP-55
}

private fun JsonElement.toQuantity(): Quantity {
    val c = jsonPrimitive.content
    return if (c.startsWith("0x") || c.startsWith("0X")) Quantity.ofHex(c) else Quantity.of(c.toLong())
}
