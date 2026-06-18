package com.tneff.cyppie.evm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QuantityArithmeticTest {

    @Test
    fun addsAndMultiplies() {
        assertEquals(Quantity.of(8), Quantity.of(5) + Quantity.of(3))
        assertEquals(Quantity.of(0), Quantity.ZERO + Quantity.ZERO)
        // gasLimit * maxFeePerGas (21000 * 2 gwei = 42_000_000_000_000 wei)
        val fee = Quantity.of(21_000) * Quantity.of(2_000_000_000)
        assertEquals(42_000_000_000_000L, fee.toLong())
        assertEquals(Quantity.ZERO, Quantity.of(123) * Quantity.ZERO)
    }

    @Test
    fun balanceCheckShape() {
        // required = value + gasLimit*maxFee; balance comparison drives insufficient-funds
        val value = Quantity.of(1_000_000)
        val required = value + Quantity.of(21_000) * Quantity.of(50) // 1_000_000 + 1_050_000 = 2_050_000
        assertEquals(Quantity.of(1_000_000 + 21_000L * 50), required)
        assertTrue(Quantity.of(3_000_000) > required)
        assertTrue(Quantity.of(1_000) < required)
        assertEquals(0, required.compareTo(required))
    }

    @Test
    fun handlesLargeValuesBeyondLong() {
        // 2^200 + 2^200 = 2^201 — well beyond Long, exact in 256-bit.
        val twoPow200 = Quantity.ofHex("0x" + "1" + "0".repeat(50)) // 1 followed by 50 hex zeros = 16^50 = 2^200
        val sum = twoPow200 + twoPow200
        assertEquals(Quantity.ofHex("0x" + "2" + "0".repeat(50)), sum)
        assertTrue(sum > twoPow200)
    }

    @Test
    fun overflowThrows() {
        val max = Quantity.ofHex("0x" + "ff".repeat(32)) // 2^256 - 1
        assertFailsWith<IllegalArgumentException> { max + Quantity.of(1) }
    }
}
