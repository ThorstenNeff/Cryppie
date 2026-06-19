package com.tneff.cyppie.aa

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The canonical Smart-Session config (AA Ph0 contract §2) — built on-device in the grant UX and stored
 * by the backend in the session registry. Limits map 1:1 to Smart-Sessions policies (per-op +
 * rolling-window + total-exposure). Addresses/selectors/amounts are **hex / decimal Strings** (the wire
 * shape; FR-6 — amounts are base-unit decimal strings, never float). [account] is the 7702-SCA = the
 * SIWE-identity EOA (same address).
 */
@Serializable
data class SessionConfig(
    val sessionId: String? = null,        // backend-assigned; ties registry ↔ on-chain session
    val chainId: Long,                    // 1 (ETH) | 8453 (Base)
    val account: String,                  // 0x<scaAddress>
    val signer: SessionSigner = SessionSigner.OnDevice,
    val actions: List<ScopedAction>,
    val totalExposureCap: ExposureCap? = null,
)

/** Who signs ops for the session. DCA = on-device (Ph1); Copy/Vaults = backend (Ph2, HSM/KMS). */
@Serializable
enum class SessionSigner {
    @SerialName("on-device") OnDevice,
    @SerialName("backend") Backend,
}

/** One allowed (contract, function) with its on-chain-enforced limits (Smart Sessions reverts out-of-policy). */
@Serializable
data class ScopedAction(
    val target: String,                   // allowed contract (0x..)
    val selector: String,                 // allowed function selector (0x........)
    val spendingLimits: List<SpendingLimit> = emptyList(),
    val rollingWindowSeconds: Long,       // rolling window for the cap
    val usageLimit: Int,                  // max ops over the session
    val validUntil: Long,                 // unix expiry
)

/** A per-token spending cap (base units, decimal String). */
@Serializable
data class SpendingLimit(val token: String, val cap: String)

/** The backend cross-session aggregate cap (Q7; off-chain pre-check before submit). */
@Serializable
data class ExposureCap(val token: String, val cap: String)
