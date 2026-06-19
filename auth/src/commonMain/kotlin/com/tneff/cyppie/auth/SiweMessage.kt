package com.tneff.cyppie.auth

import com.tneff.cyppie.evm.EvmAddress

/**
 * An EIP-4361 "Sign in with Ethereum" message (KAN-141). [canonical] renders the exact ABNF the
 * Keycloak SIWE authenticator verifies. ⚠️ [domain] MUST be `auth.cyppie.com` (the authenticator's
 * domain-binding) and [statement] non-empty, else the backend rejects the login.
 */
data class SiweMessage(
    val domain: String,
    val address: String,      // EIP-55 checksummed
    val statement: String,
    val uri: String,
    val version: String,
    val chainId: Long,
    val nonce: String,        // single-use, from /siwe/nonce
    val issuedAt: String,     // ISO-8601
    val expirationTime: String? = null, // ISO-8601 — narrows the signature validity window (LOW-4)
    val notBefore: String? = null,      // ISO-8601 (optional)
) {
    /** The canonical EIP-4361 string the app signs (personal_sign) and sends as `siwe_message`. */
    fun canonical(): String = buildString {
        append(domain).append(" wants you to sign in with your Ethereum account:\n")
        append(address).append("\n\n")
        append(statement).append("\n\n")
        append("URI: ").append(uri).append('\n')
        append("Version: ").append(version).append('\n')
        append("Chain ID: ").append(chainId).append('\n')
        append("Nonce: ").append(nonce).append('\n')
        append("Issued At: ").append(issuedAt)
        expirationTime?.let { append("\nExpiration Time: ").append(it) }
        notBefore?.let { append("\nNot Before: ").append(it) }
    }

    companion object {
        const val DOMAIN = "auth.cyppie.com"        // = the Keycloak authenticator domain-binding
        const val URI = "https://auth.cyppie.com"
        const val STATEMENT = "Sign in to Cyppie"   // shown in the no-blind consent; must be non-empty
        const val DEFAULT_CHAIN_ID = 1L             // authenticator accepts 1 or 8453

        /** Builds the Cyppie sign-in message for [address]. [expirationTime] (ISO-8601, e.g. issuedAt +
         *  ~5min) narrows the signature window beyond the single-use nonce (LOW-4). */
        fun forSignIn(
            address: EvmAddress,
            nonce: String,
            issuedAt: String,
            chainId: Long = DEFAULT_CHAIN_ID,
            expirationTime: String? = null,
        ): SiweMessage =
            SiweMessage(
                domain = DOMAIN,
                address = address.value,
                statement = STATEMENT,
                uri = URI,
                version = "1",
                chainId = chainId,
                nonce = nonce,
                issuedAt = issuedAt,
                expirationTime = expirationTime,
            )
    }
}

/** Signs an [SiweMessage] personal_sign-style (EIP-191) on-device → `0x`-65-byte hex. The single
 *  auditable EIP-191 path lives in `:wallet` (shared with WalletConnect); the impl is wired there. */
interface SiweSigner {
    /** Re-auth → fresh [seedSource] → EIP-191 sign of [message].canonical() as [owner]; source zeroized. */
    fun sign(message: SiweMessage, owner: EvmAddress, seedSource: com.tneff.cyppie.wallet.SeedSource): String
}
