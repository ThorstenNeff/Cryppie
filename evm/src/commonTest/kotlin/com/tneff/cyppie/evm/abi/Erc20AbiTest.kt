package com.tneff.cyppie.evm.abi

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Hex
import com.tneff.cyppie.evm.Quantity
import kotlin.test.Test
import kotlin.test.assertEquals

class Erc20AbiTest {

    @Test
    fun selectorsMatchKnownValues() {
        assertEquals("a9059cbb", Hex.encode(Erc20Abi.TRANSFER_SELECTOR))
        assertEquals("70a08231", Hex.encode(Erc20Abi.BALANCE_OF_SELECTOR))
        assertEquals("313ce567", Hex.encode(Erc20Abi.DECIMALS_SELECTOR))
        assertEquals("95d89b41", Hex.encode(Erc20Abi.SYMBOL_SELECTOR))
    }

    @Test
    fun encodesTransferCalldata() {
        val to = EvmAddress.parse("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")
        val calldata = Erc20Abi.transfer(to, Quantity.of(1))
        val expected =
            "a9059cbb" +
                "0000000000000000000000005aaeb6053f3e94c9b9a09f33669435e7ef1beaed" +
                "0000000000000000000000000000000000000000000000000000000000000001"
        assertEquals(expected, Hex.encode(calldata))
    }

    @Test
    fun encodesBalanceOfCalldata() {
        val owner = EvmAddress.parse("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed")
        val expected =
            "70a08231" +
                "0000000000000000000000005aaeb6053f3e94c9b9a09f33669435e7ef1beaed"
        assertEquals(expected, Hex.encode(Erc20Abi.balanceOf(owner)))
    }

    @Test
    fun decodesUintAndString() {
        // balanceOf result = 256 (0x100) as a 32-byte word
        val uint = Hex.decodeOrNull("0".repeat(60) + "0100")!!
        assertEquals(Quantity.ofHex("0x100"), Erc20Abi.decodeUint(uint))
        assertEquals(8, Erc20Abi.decodeUint8(Hex.decodeOrNull("0".repeat(62) + "08")!!))

        // symbol() dynamic string "USDC": offset=0x20, len=4, "USDC" + padding
        val str = Hex.decodeOrNull(
            "0000000000000000000000000000000000000000000000000000000000000020" +
                "0000000000000000000000000000000000000000000000000000000000000004" +
                "5553444300000000000000000000000000000000000000000000000000000000",
        )!!
        assertEquals("USDC", Erc20Abi.decodeString(str))
    }
}
