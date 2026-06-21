package com.tneff.cyppie.aa

import com.tneff.cyppie.evm.SmartSessionEnableDigest.SignedPermissions
import kotlinx.serialization.Serializable

/**
 * The **shared** enable-broadcast surface (KAN-154 Copy + KAN-159 DCA): build the owner-signed
 * `installModule(SmartSessions)+enableSessions` userOp, submit it for the backend to broadcast (Approach B), and
 * poll its receipt. Both [CopyApi] and the DCA enable path implement this so the on-device orchestration
 * ([EnableBroadcaster]) — recompute + 7702-pin + verifyEnableUserOp + owner-sign — is written ONCE.
 */
interface EnableBroadcastApi {

    /** Backend builds the enable userOp for the prepared session (`/v1/userop/build`). */
    suspend fun buildEnableUserOp(request: BuildEnableRequest): BuiltEnableUserOp

    /** Submit the owner-signed enable userOp; the backend submits via Pimlico (`/v1/userop/submit`). Returns the userOpHash. */
    suspend fun submitEnableUserOp(request: SubmitEnableRequest): String

    /** Op status / receipt (`/v1/userop/{chainId}/{hash}`). */
    suspend fun opStatus(chainId: Long, userOpHash: String): OpStatus
}

/**
 * The use-case-agnostic expected session the app verifies the built op against — produced on-device by
 * `CopyEnableBuilder` (Copy) or `DcaEnableBuilder` (DCA). [account] is the device owner (7702 same-address).
 */
data class ExpectedEnable(
    val chainId: Long,
    val account: String,
    val permissionId: String,
    val sessionValidator: String,
    val sessionValidatorInitData: String,
    val salt: String,
    val permissions: SignedPermissions,
)

@Serializable
data class BuildEnableRequest(val permissionId: String)

/**
 * The `/v1/userop/build` response: the final (gas-estimated, paymaster-applied) op + the hash + the EIP-191 digest.
 * [authorizationToSign] is present ONLY on the FIRST op of a fresh follower (the EIP-7702 delegation); the app
 * must pin its delegate-target ([com.tneff.cyppie.evm.Eip7702Authorization]) and owner-sign it too (KAN-160).
 */
@Serializable
data class BuiltEnableUserOp(
    val userOp: UnpackedUserOp,
    val userOpHash: String,
    val digestToSign: String,
    val authorizationToSign: AuthorizationTuple? = null,
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

/** The unsigned EIP-7702 authorization tuple (delegate the EOA's code to [address] = the Kernel implementation). */
@Serializable
data class AuthorizationTuple(val chainId: Long, val address: String, val nonce: Long)

/** The app-signed 7702 authorization, passed back to [EnableBroadcastApi.submitEnableUserOp]. */
@Serializable
data class SignedAuthorization(
    val chainId: Long,
    val address: String,
    val nonce: Long,
    val r: String,
    val s: String,
    val yParity: Int,
)

@Serializable
data class SubmitEnableRequest(
    val userOpHash: String,
    val signature: String,
    val signedAuthorization: SignedAuthorization? = null,
)

@Serializable
data class SubmittedUserOp(val userOpHash: String)
