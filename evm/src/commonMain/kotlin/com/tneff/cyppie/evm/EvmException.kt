package com.tneff.cyppie.evm

/**
 * Sealed failure types for the web-safe EVM primitives (`:evm`). Sealed so callers and tests can
 * assert deterministically on negative cases. Messages never contain secret material.
 */
sealed class EvmException(message: String) : Exception(message) {

    /** A hex string is not a well-formed EVM address or fails its EIP-55 checksum. */
    class InvalidAddress(message: String) : EvmException(message)

    /** A value is not a valid 256-bit unsigned quantity (negative, too large, or bad hex). */
    class InvalidQuantity(message: String) : EvmException(message)

    /** Malformed RLP input during decoding. */
    class InvalidRlp(message: String) : EvmException(message)

    /** Malformed ABI input/return data. */
    class InvalidAbi(message: String) : EvmException(message)
}
