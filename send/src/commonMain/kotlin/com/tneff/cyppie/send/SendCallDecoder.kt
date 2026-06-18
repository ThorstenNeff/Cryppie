package com.tneff.cyppie.send

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Quantity

/**
 * Decodes the common ERC-20 methods for human-readable disclosure (O4): `transfer`, `transferFrom`,
 * `approve`. Anything else (or malformed args) → raw hex, which the UI shows **with a warning**
 * (fail-safe — never present unknown calldata as if it were understood).
 */
internal object SendCallDecoder {

    private val TRANSFER = byteArrayOf(0xa9.toByte(), 0x05, 0x9c.toByte(), 0xbb.toByte())
    private val APPROVE = byteArrayOf(0x09, 0x5e, 0xa7.toByte(), 0xb3.toByte())
    private val TRANSFER_FROM = byteArrayOf(0x23, 0xb8.toByte(), 0x72, 0xdd.toByte())

    fun decode(data: ByteArray): DecodedCall {
        if (data.isEmpty()) return DecodedCall.NativeTransfer
        if (data.size < 4) return raw(data)
        val selector = data.copyOfRange(0, 4)
        return when {
            selector.contentEquals(TRANSFER) && hasWords(data, 2) ->
                DecodedCall.Erc20Transfer(addressArg(data, 0), uintArg(data, 1))
            selector.contentEquals(APPROVE) && hasWords(data, 2) ->
                DecodedCall.Erc20Approve(addressArg(data, 0), uintArg(data, 1))
            selector.contentEquals(TRANSFER_FROM) && hasWords(data, 3) ->
                DecodedCall.Erc20TransferFrom(addressArg(data, 0), addressArg(data, 1), uintArg(data, 2))
            else -> raw(data)
        }
    }

    private fun hasWords(data: ByteArray, n: Int): Boolean = data.size >= 4 + n * 32

    private fun raw(data: ByteArray) = DecodedCall.Unknown("0x" + Hex.encode(data))

    /** The address occupies the low 20 bytes of the i-th 32-byte ABI word. */
    private fun addressArg(data: ByteArray, i: Int): EvmAddress {
        val word = data.copyOfRange(4 + i * 32, 4 + i * 32 + 32)
        return EvmAddress.fromBytes(word.copyOfRange(12, 32))
    }

    private fun uintArg(data: ByteArray, i: Int): Quantity =
        Quantity.ofBytes(data.copyOfRange(4 + i * 32, 4 + i * 32 + 32))
}
