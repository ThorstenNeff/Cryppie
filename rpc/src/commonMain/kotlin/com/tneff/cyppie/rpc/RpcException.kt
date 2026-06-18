package com.tneff.cyppie.rpc

/** Sealed failures for the RPC layer, so callers can react to network vs. node vs. all-down. */
sealed class RpcException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Transport-level failure (connectivity, timeout, non-2xx) talking to one provider. */
    class Transport(message: String, cause: Throwable? = null) : RpcException(message, cause)

    /** The node returned a JSON-RPC `error` object (e.g. execution revert, invalid params). */
    class Node(val code: Int, message: String) : RpcException("RPC error $code: $message")

    /** Every configured provider failed in turn (FR-4 failover exhausted). */
    class AllProvidersFailed(message: String, cause: Throwable? = null) : RpcException(message, cause)

    /** A response could not be decoded into the expected shape. */
    class Decoding(message: String) : RpcException(message)

    /** Receipt polling exceeded its timeout without the tx being mined. */
    class ReceiptTimeout(message: String) : RpcException(message)
}
