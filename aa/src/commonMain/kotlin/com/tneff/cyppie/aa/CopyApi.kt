package com.tneff.cyppie.aa

import kotlinx.serialization.Serializable

/**
 * The app-facing Copy-trading API (PRD-06, KAN-154) — the app talks ONLY to the JWT User-Service. Unlike DCA
 * (the enable digest is built fully on-device), Copy's enable is an **owner-signed enable userOp** the backend
 * builds + submits via Pimlico (GAP-B / Approach B): the app `prepare`s the scope, rebuilds + verifies the op
 * on-device ([EnableUserOpVerifier]), owner-signs the `digestToSign`, and the backend submits. Paths/shapes
 * mirror `docs/copy-trading-enable-submit-contract.md` and are reconciled with the backend surface.
 */
interface CopyApi {

    /** Scope for a follow relationship — the session the app will enable (`/v1/copy/session/prepare`). */
    suspend fun prepare(followId: String): CopyPrepare

    /** Backend builds the `installModule(SmartSessions)+enableSessions` enable userOp (`/v1/userop/build`). */
    suspend fun buildEnableUserOp(request: BuildEnableRequest): BuiltEnableUserOp

    /** Submit the owner-signed enable userOp; the backend submits via Pimlico (`/v1/userop/submit`). Returns the userOpHash. */
    suspend fun submitEnableUserOp(request: SubmitEnableRequest): String

    /** Op status / receipt (`/v1/userop/{chainId}/{hash}`), surfaced via the User-Service. */
    suspend fun opStatus(chainId: Long, userOpHash: String): OpStatus

    /** Mark the session active after a successful enable receipt (`/v1/copy/session/grant`). */
    suspend fun grantSession(request: CopyGrantRequest)
}

/**
 * The session the app will enable. [follower] is the SCA (= device owner EOA, 7702 same-address) — the app
 * MUST cross-check it against the device-derived owner and refuse if it differs (never trust a backend address).
 * [source]/[allocationBps] are advisory disclosure context (NOT in the signed enable — not crypto-verifiable).
 */
@Serializable
data class CopyPrepare(
    val chainId: Long,
    val follower: String,
    val sessionPublicKey: String,
    val spendToken: String,
    val capBaseUnits: String,
    val windowStart: Long,
    val windowEnd: Long,
    val salt: String,
    val nonce: Long,
    val source: String,
    val allocationBps: Int,
)

@Serializable
data class BuildEnableRequest(val followId: String, val permissionId: String)

/** The `/v1/userop/build` response: the final (gas-estimated, paymaster-applied) op + the hash + the EIP-191 digest. */
@Serializable
data class BuiltEnableUserOp(
    val userOp: UnpackedUserOp,
    val userOpHash: String,
    val digestToSign: String,
)

/** A v0.7 user operation with UNPACKED gas/paymaster fields (as the build endpoint serializes them). */
@Serializable
data class UnpackedUserOp(
    val sender: String,
    val nonce: String,
    val callData: String,
    val callGasLimit: String,
    val verificationGasLimit: String,
    val preVerificationGas: String,
    val maxFeePerGas: String,
    val maxPriorityFeePerGas: String,
    val factory: String? = null,
    val factoryData: String? = null,
    val paymaster: String? = null,
    val paymasterVerificationGasLimit: String? = null,
    val paymasterPostOpGasLimit: String? = null,
    val paymasterData: String? = null,
)

@Serializable
data class SubmitEnableRequest(val userOpHash: String, val signature: String)

@Serializable
data class CopyGrantRequest(val permissionId: String, val followId: String)
