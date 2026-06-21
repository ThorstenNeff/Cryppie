package com.tneff.cyppie.evm

/**
 * Minimal decoder for the ERC-7579 / Kernel `execute(bytes32 mode, bytes executionCalldata)` batch
 * (selector `0xe9ae5c53`) used by the enable userOp (KAN-154/KAN-159). Batch mode (`executionCalldata =
 * abi.encode(Execution[])`, `Execution = (address target, uint256 value, bytes callData)`). Only what
 * `verifyEnableUserOp` needs: structural decode of the calls so it can assert the op is EXACTLY the
 * install(SmartSessions) + enableSessions pair on the expected targets — fail-closed on anything else.
 */
object KernelExecuteBatch {

    const val EXECUTE_SELECTOR: String = "0xe9ae5c53"

    /** One decoded call in the batch. */
    data class Execution(val target: String, val callData: ByteArray)

    /** Decodes the batch [callDataHex] into its [Execution]s. Throws on a non-batch / malformed encoding. */
    fun decodeBatch(callDataHex: String): List<Execution> {
        val data = bytes(callDataHex)
        require(data.size >= 4 && hex4(data, 0) == "e9ae5c53") { "not an execute(bytes32,bytes) call" }
        val args = 4 // after the selector
        // execute(bytes32 mode, bytes executionCalldata): word0 = mode, word1 = offset to executionCalldata.
        val mode = word(data, args)
        require((mode[0].toInt() and 0xFF) == 0x01) { "not a batch execute (call-type ${mode[0]})" }
        val execOff = wordToInt(data, args + 32)
        val execStart = args + execOff
        val execLen = wordToInt(data, execStart)
        val exec = data.copyOfRange(execStart + 32, execStart + 32 + execLen) // = abi.encode(Execution[])

        // executionCalldata = abi.encode(Execution[]): word0 = offset to the array.
        val arr = wordToInt(exec, 0)
        val n = wordToInt(exec, arr)
        val elems = arr + 32 // start of the per-element offset table (offsets are relative to here)
        val out = ArrayList<Execution>(n)
        for (i in 0 until n) {
            val e = elems + wordToInt(exec, elems + i * 32) // Execution start (target/value/callData-offset)
            val target = address(exec, e)
            val cdOff = wordToInt(exec, e + 64) // offset to callData, relative to the Execution start
            val cd = e + cdOff
            val cdLen = wordToInt(exec, cd)
            out.add(Execution(target, exec.copyOfRange(cd + 32, cd + 32 + cdLen)))
        }
        return out
    }

    /**
     * Decodes a Kernel **single-call** `execute` (call-type `0x00`): `executionCalldata = encodePacked(address
     * target, uint256 value, bytes callData)` (NOT an abi-encoded array — that is batch mode). Used by the revoke
     * userOp (KAN-157), which is one `removeSession` call. Throws on a non-single / malformed encoding.
     */
    fun decodeSingle(callDataHex: String): Execution {
        val data = bytes(callDataHex)
        require(data.size >= 4 && hex4(data, 0) == "e9ae5c53") { "not an execute(bytes32,bytes) call" }
        val args = 4
        val mode = word(data, args)
        require((mode[0].toInt() and 0xFF) == 0x00) { "not a single-call execute (call-type ${mode[0]})" }
        val execOff = wordToInt(data, args + 32)
        val execStart = args + execOff
        val execLen = wordToInt(data, execStart)
        val exec = data.copyOfRange(execStart + 32, execStart + 32 + execLen) // packed: target(20) ‖ value(32) ‖ callData
        require(exec.size >= 52) { "single-call execution too short" }
        val target = EvmAddress.fromBytes(exec.copyOfRange(0, 20)).value
        return Execution(target, exec.copyOfRange(52, exec.size))
    }

    /** The function selector (`0x…`) of an execution's callData. */
    fun selectorOf(execution: Execution): String = "0x" + Hex.encode(execution.callData.copyOfRange(0, 4))

    // ── helpers ──
    private fun bytes(hex: String): ByteArray =
        Hex.decodeOrNull(hex.removePrefix("0x").removePrefix("0X")) ?: throw IllegalArgumentException("bad hex")

    private fun word(b: ByteArray, off: Int): ByteArray = b.copyOfRange(off, off + 32)

    private fun wordToInt(b: ByteArray, off: Int): Int {
        // ABI offsets/lengths fit well within Int; reject anything that doesn't (defensive).
        for (i in off until off + 28) if (b[i].toInt() != 0) throw IllegalArgumentException("oversized offset/length")
        var v = 0L
        for (i in off + 28 until off + 32) v = (v shl 8) or (b[i].toLong() and 0xFF)
        if (v > Int.MAX_VALUE) throw IllegalArgumentException("offset/length too large")
        return v.toInt()
    }

    /** EIP-55 address from the low 20 bytes of the word at [off]. */
    private fun address(b: ByteArray, off: Int): String = EvmAddress.fromBytes(b.copyOfRange(off + 12, off + 32)).value

    private fun hex4(b: ByteArray, off: Int): String = Hex.encode(b.copyOfRange(off, off + 4))
}
