package com.tneff.cyppie.evm.abi

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KAN-59 — authoritative ERC-20 **ABI** known-answer vectors (ADR-0014 audit gate). The 4-byte
 * function selectors are the canonical `keccak256(signature)[0..4)` values; calldata is the
 * selector followed by 32-byte left-padded big-endian arguments. Values are well-known/public.
 */
class Erc20AbiVectorsTest {

    private val to = EvmAddress.parse("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")

    @Test
    fun functionSelectorsAreCanonical() {
        assertEquals("a9059cbb", Hex.encode(Erc20Abi.TRANSFER_SELECTOR))   // transfer(address,uint256)
        assertEquals("70a08231", Hex.encode(Erc20Abi.BALANCE_OF_SELECTOR)) // balanceOf(address)
        assertEquals("313ce567", Hex.encode(Erc20Abi.DECIMALS_SELECTOR))   // decimals()
        assertEquals("95d89b41", Hex.encode(Erc20Abi.SYMBOL_SELECTOR))     // symbol()
    }

    @Test
    fun transferEncodesSelectorPlusPaddedArgs() {
        // transfer(0x7099…79C8, 1_000_000) → a9059cbb + pad32(to) + pad32(0xf4240)
        val expected = "a9059cbb" +
            "00000000000000000000000070997970c51812dc3a010c7d01b50e0d17dc79c8" +
            "00000000000000000000000000000000000000000000000000000000000f4240"
        assertEquals(expected, Hex.encode(Erc20Abi.transfer(to, Quantity.of(1_000_000L))))
    }

    @Test
    fun transferEncodesZeroAmountAndMaxLikeValues() {
        val zero = "a9059cbb" +
            "00000000000000000000000070997970c51812dc3a010c7d01b50e0d17dc79c8" +
            "0000000000000000000000000000000000000000000000000000000000000000"
        assertEquals(zero, Hex.encode(Erc20Abi.transfer(to, Quantity.ZERO)))
        // 1 token at 18 decimals = 10^18 = 0x0de0b6b3a7640000.
        val oneEth = "a9059cbb" +
            "00000000000000000000000070997970c51812dc3a010c7d01b50e0d17dc79c8" +
            "0000000000000000000000000000000000000000000000000de0b6b3a7640000"
        assertEquals(oneEth, Hex.encode(Erc20Abi.transfer(to, Quantity.of(1_000_000_000_000_000_000L))))
    }

    @Test
    fun balanceOfEncodesSelectorPlusPaddedAddress() {
        val expected = "70a08231" +
            "00000000000000000000000070997970c51812dc3a010c7d01b50e0d17dc79c8"
        assertEquals(expected, Hex.encode(Erc20Abi.balanceOf(to)))
    }

    @Test
    fun decodesUintReturnData() {
        // A 32-byte big-endian word decodes to the expected quantity / uint8.
        val word = "00000000000000000000000000000000000000000000000000000000000f4240"
        assertEquals(Quantity.of(1_000_000L), Erc20Abi.decodeUint(Hex.decodeOrNull(word)!!))
        val eighteen = "0000000000000000000000000000000000000000000000000000000000000012" // 0x12 = 18
        assertEquals(18, Erc20Abi.decodeUint8(Hex.decodeOrNull(eighteen)!!))
    }
}
