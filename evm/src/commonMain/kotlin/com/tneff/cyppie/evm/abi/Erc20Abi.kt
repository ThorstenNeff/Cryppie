package com.tneff.cyppie.evm.abi

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.EvmException
import com.tneff.cyppie.evm.Keccak
import com.tneff.cyppie.evm.Quantity

/**
 * Minimal ABI encoder/decoder for the ERC-20 calls the wallet needs (ADR-0014 — *not* a general
 * ABI codec): `transfer`, `balanceOf`, `decimals`, `symbol`. Encodes calldata (4-byte selector +
 * 32-byte head words) and decodes the simple static / single-string return values.
 */
object Erc20Abi {

    // 4-byte function selectors = keccak256(signature)[0..4).
    val TRANSFER_SELECTOR: ByteArray = selector("transfer(address,uint256)")
    val BALANCE_OF_SELECTOR: ByteArray = selector("balanceOf(address)")
    val DECIMALS_SELECTOR: ByteArray = selector("decimals()")
    val SYMBOL_SELECTOR: ByteArray = selector("symbol()")

    /** Calldata for `transfer(address to, uint256 amount)`. */
    fun transfer(to: EvmAddress, amount: Quantity): ByteArray =
        TRANSFER_SELECTOR + encodeAddress(to) + amount.toBytes32()

    /** Calldata for `balanceOf(address owner)`. */
    fun balanceOf(owner: EvmAddress): ByteArray =
        BALANCE_OF_SELECTOR + encodeAddress(owner)

    /** Calldata for `decimals()`. */
    fun decimals(): ByteArray = DECIMALS_SELECTOR

    /** Calldata for `symbol()`. */
    fun symbol(): ByteArray = SYMBOL_SELECTOR

    /** Decodes a 32-byte ABI `uint256` return value (e.g. `balanceOf`). */
    fun decodeUint(result: ByteArray): Quantity {
        if (result.size < 32) throw EvmException.InvalidAbi("uint256 return too short")
        return Quantity.ofBytes(result.copyOfRange(0, 32))
    }

    /** Decodes a 32-byte ABI `uint8` return value (e.g. `decimals`). */
    fun decodeUint8(result: ByteArray): Int = decodeUint(result).toMinimalBytes().let { b ->
        if (b.isEmpty()) 0 else b[b.size - 1].toInt() and 0xFF
    }

    /**
     * Decodes an ABI dynamic `string` return (e.g. `symbol`): `[offset][length][utf8 bytes…]`.
     * Tolerates the non-standard fixed-bytes32 form some legacy tokens use as a fallback.
     */
    fun decodeString(result: ByteArray): String {
        if (result.size == 32) {
            // bytes32-style: trim trailing NULs.
            val end = result.indexOfFirst { it.toInt() == 0 }.let { if (it < 0) 32 else it }
            return result.copyOfRange(0, end).decodeToString()
        }
        if (result.size < 64) throw EvmException.InvalidAbi("string return too short")
        val offset = Quantity.ofBytes(result.copyOfRange(0, 32)).toMinimalBytes().toIntChecked()
        val len = Quantity.ofBytes(result.copyOfRange(offset, offset + 32)).toMinimalBytes().toIntChecked()
        val start = offset + 32
        if (start + len > result.size) throw EvmException.InvalidAbi("string return overran")
        return result.copyOfRange(start, start + len).decodeToString()
    }

    private fun selector(signature: String): ByteArray =
        Keccak.keccak256(signature.encodeToByteArray()).copyOfRange(0, 4)

    /** ABI `address` = left zero-padded to 32 bytes. */
    private fun encodeAddress(address: EvmAddress): ByteArray {
        val out = ByteArray(32)
        address.bytes.copyInto(out, destinationOffset = 12)
        return out
    }

    private fun ByteArray.toIntChecked(): Int {
        if (size > 4) throw EvmException.InvalidAbi("ABI length too large")
        var v = 0
        for (b in this) v = (v shl 8) or (b.toInt() and 0xFF)
        return v
    }
}
