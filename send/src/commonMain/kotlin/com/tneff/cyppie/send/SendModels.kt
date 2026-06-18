package com.tneff.cyppie.send

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.evm.Quantity
import com.tneff.cyppie.wallet.tx.Eip1559Transaction
import com.tneff.cyppie.walletcore.EvmChain

/**
 * Normalized send input — **both** entry points produce this (one orchestrator, ADR-0019): an in-app
 * send (user picks to/asset/amount) and a WalletConnect `eth_sendTransaction` (`SendTransactionParams`
 * from `decode()`). Pre-filled fee/gas/nonce fields are honoured as **fill-missing, never override**
 * (Guardrail #2): a dApp may pin a value, but the orchestrator completes only what's absent.
 */
data class SendInput(
    val from: EvmAddress,
    val to: EvmAddress,
    val value: Quantity,
    val data: ByteArray = ByteArray(0),
    val chain: EvmChain,
    val nonce: Quantity? = null,
    val gasLimit: Quantity? = null,
    val maxFeePerGas: Quantity? = null,
    val maxPriorityFeePerGas: Quantity? = null,
)

/** Human-readable decode of the calldata (no blind signing, FR-2 / O4). */
sealed interface DecodedCall {
    /** Plain native (ETH) transfer — empty calldata. */
    data object NativeTransfer : DecodedCall
    data class Erc20Transfer(val recipient: EvmAddress, val amount: Quantity) : DecodedCall
    data class Erc20TransferFrom(val from: EvmAddress, val recipient: EvmAddress, val amount: Quantity) : DecodedCall
    data class Erc20Approve(val spender: EvmAddress, val amount: Quantity) : DecodedCall
    /** Unknown calldata → raw hex; the UI must show it with a warning (fail-safe). */
    data class Unknown(val dataHex: String) : DecodedCall
}

/**
 * What the approval UI shows — the **completed** transaction (Guardrail #1: signed == disclosed). All
 * fields are final; no fee/gas is filled after this point.
 */
data class SendDisclosure(
    val from: EvmAddress,
    val to: EvmAddress,
    val chain: EvmChain,
    val value: Quantity,
    val nonce: Quantity,
    val gasLimit: Quantity,
    val maxFeePerGas: Quantity,
    val maxPriorityFeePerGas: Quantity,
    /** Worst-case network fee = `gasLimit * maxFeePerGas`. */
    val maxNetworkFee: Quantity,
    val call: DecodedCall,
)

/** Output of [SendOrchestrator.prepare]: the completed tx + resolved signer index + disclosure. */
data class PreparedSend(
    val transaction: Eip1559Transaction,
    val accountIndex: Int,
    val disclosure: SendDisclosure,
)

/** The broadcast result. */
data class SendResult(val txHash: String)
