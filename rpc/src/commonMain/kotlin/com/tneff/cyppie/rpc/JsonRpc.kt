package com.tneff.cyppie.rpc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/** JSON-RPC 2.0 request envelope. */
@Serializable
internal data class JsonRpcRequest(
    val id: Int,
    val method: String,
    val params: JsonArray,
    val jsonrpc: String = "2.0",
)

/** JSON-RPC 2.0 response envelope; exactly one of [result]/[error] is present. */
@Serializable
internal data class JsonRpcResponse(
    val id: Int? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
    val jsonrpc: String? = null,
)

@Serializable
internal data class JsonRpcError(val code: Int, val message: String)
