package com.tneff.cyppie.evm

/**
 * ERC-4337 (EntryPoint v0.7) userOp-hash recompute (KAN-154/KAN-159, GAP-B). The enable broadcast has the
 * **owner** sign `hashMessage(userOpHash)` via the Kernel ROOT validator — i.e. **full account authority**. So the
 * app MUST recompute the `userOpHash` on-device from the `/v1/userop/build` fields and bind its signature to it,
 * else it would blind-sign whatever op the backend chose (drain risk). This is the security-critical preimage of
 * that binding (the calldata decode + `verifyGrant` live in the enable-userOp verifier on top of this).
 *
 * v0.7 PackedUserOperation hash:
 * `userOpHash = keccak256(abi.encode(keccak256(packed), entryPoint, chainId))` where
 * `packed = (sender, nonce, keccak256(initCode), keccak256(callData), accountGasLimits, preVerificationGas,
 * gasFees, keccak256(paymasterAndData))`. `accountGasLimits = verificationGasLimit<<128 | callGasLimit`,
 * `gasFees = maxPriorityFeePerGas<<128 | maxFeePerGas`, `paymasterAndData = paymaster ‖ vGas(16B) ‖ postOp(16B) ‖ data`.
 */
object Erc4337UserOp {

    /** The canonical EntryPoint v0.7 address (identical on every chain). */
    const val ENTRY_POINT_V07: String = "0x0000000071727De22E5E9d8BAf0edAc6f37da032"

    /**
     * A v0.7 user operation as returned by `/v1/userop/build` — gas/paymaster pre-packed. [initCode] is the
     * 7702/factory init (empty `0x` for a 7702 same-address account). [accountGasLimits]/[gasFees]/
     * [paymasterAndData] are the already-packed byte strings (hex). [nonce]/[preVerificationGas] are decimal/hex.
     */
    data class PackedUserOp(
        val sender: String,
        val nonce: String,
        val initCode: String,
        val callData: String,
        val accountGasLimits: String,
        val preVerificationGas: String,
        val gasFees: String,
        val paymasterAndData: String,
    )

    /**
     * Packs the **unpacked** v0.7 fields as `/v1/userop/build` returns them (the contract serializes gas/paymaster
     * unpacked) into a [PackedUserOp] for [userOpHash]. [factory]/[paymaster] empty/null ⇒ no initCode / no
     * paymasterAndData (the EIP-7702 case). All numeric args are hex (`0x…`) or decimal strings.
     */
    fun pack(
        sender: String,
        nonce: String,
        callData: String,
        callGasLimit: String,
        verificationGasLimit: String,
        preVerificationGas: String,
        maxFeePerGas: String,
        maxPriorityFeePerGas: String,
        factory: String? = null,
        factoryData: String? = null,
        paymaster: String? = null,
        paymasterVerificationGasLimit: String? = null,
        paymasterPostOpGasLimit: String? = null,
        paymasterData: String? = null,
    ): PackedUserOp {
        val initCode = if (factory.isNullOrBlank() || factory == "0x") "0x"
        else "0x" + strip(factory) + strip(factoryData ?: "0x")
        val paymasterAndData = if (paymaster.isNullOrBlank() || paymaster == "0x") "0x"
        else "0x" + strip(paymaster) + Hex.encode(pad16(paymasterVerificationGasLimit ?: "0x0")) +
            Hex.encode(pad16(paymasterPostOpGasLimit ?: "0x0")) + strip(paymasterData ?: "0x")
        return PackedUserOp(
            sender = sender, nonce = nonce, initCode = initCode, callData = callData,
            accountGasLimits = "0x" + Hex.encode(pad16(verificationGasLimit) + pad16(callGasLimit)),
            preVerificationGas = preVerificationGas,
            gasFees = "0x" + Hex.encode(pad16(maxPriorityFeePerGas) + pad16(maxFeePerGas)),
            paymasterAndData = paymasterAndData,
        )
    }

    /** The 32-byte `userOpHash` for [op] at [entryPoint] on [chainId] (the value the owner authorizes). */
    fun userOpHash(op: PackedUserOp, chainId: Long, entryPoint: String = ENTRY_POINT_V07): ByteArray {
        val packed = addr32(op.sender) +
            uint256(op.nonce) +
            Keccak.keccak256(bytes(op.initCode)) +
            Keccak.keccak256(bytes(op.callData)) +
            bytes32(op.accountGasLimits) +
            uint256(op.preVerificationGas) +
            bytes32(op.gasFees) +
            Keccak.keccak256(bytes(op.paymasterAndData))
        val packedHash = Keccak.keccak256(packed)
        val outer = packedHash + addr32(entryPoint) + uint256(BigUint.ofLong(chainId))
        return Keccak.keccak256(outer)
    }

    /** `digestToSign` = EIP-191 `hashMessage(userOpHash)` — the 32 bytes the owner raw-signs (Kernel root). */
    fun digestToSign(userOpHash: ByteArray): ByteArray = Eip191.personalSignDigest(userOpHash)

    // ── 32-byte ABI word helpers ──
    private fun bytes(hex: String): ByteArray = Hex.decodeOrNull(hex.removePrefix("0x").removePrefix("0X"))
        ?: throw IllegalArgumentException("invalid hex: $hex")

    private fun addr32(hex: String): ByteArray {
        val b = bytes(hex)
        require(b.size == 20) { "address must be 20 bytes: $hex" }
        return ByteArray(32).also { b.copyInto(it, 32 - b.size) }
    }

    private fun bytes32(hex: String): ByteArray {
        val b = bytes(hex)
        require(b.size == 32) { "expected a 32-byte word: $hex" }
        return b
    }

    /** uint256 → 32-byte big-endian; accepts a hex (`0x…`) or decimal string. */
    private fun uint256(value: String): ByteArray =
        if (value.startsWith("0x") || value.startsWith("0X")) Quantity.ofHex(value).toBytes32() else BigUint.ofDecimal(value).toBytes32()

    private fun uint256(q: Quantity): ByteArray = q.toBytes32()

    private fun strip(hex: String): String = hex.removePrefix("0x").removePrefix("0X")

    /** Low 16 bytes (uint128) of [value] — the per-field width inside accountGasLimits / gasFees / paymasterAndData. */
    private fun pad16(value: String): ByteArray = uint256(value).copyOfRange(16, 32)
}

/** Minimal helper to lift a Long/decimal into a [Quantity] for 32-byte encoding without a hex round-trip. */
private object BigUint {
    fun ofLong(v: Long): Quantity = Quantity.of(v)
    fun ofDecimal(dec: String): Quantity {
        var q = Quantity.of(0)
        val ten = Quantity.of(10)
        for (ch in dec) {
            require(ch in '0'..'9') { "non-decimal: $dec" }
            q = q * ten + Quantity.of((ch - '0').toLong())
        }
        return q
    }
}
