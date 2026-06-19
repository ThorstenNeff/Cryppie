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
    }

    companion object {
        const val DOMAIN = "auth.cyppie.com"        // = the Keycloak authenticator domain-binding
        const val URI = "https://auth.cyppie.com"
        const val STATEMENT = "Sign in to Cyppie"   // shown in the no-blind consent; must be non-empty
        const val DEFAULT_CHAIN_ID = 1L             // authenticator accepts 1 or 8453

        /** Builds the Cyppie sign-in message for [address] with the endpoint [nonce] + [issuedAt]. */
        fun forSignIn(address: EvmAddress, nonce: String, issuedAt: String, chainId: Long = DEFAULT_CHAIN_ID): SiweMessage =
            SiweMessage(
                domain = DOMAIN,
                address = address.value,
                statement = STATEMENT,
                uri = URI,
                version = "1",
                chainId = chainId,
                nonce = nonce,
                issuedAt = issuedAt,
            )
    }
}

/** Signs an [SiweMessage] personal_sign-style (EIP-191) on-device → `0x`-65-byte hex. The single
 *  auditable EIP-191 path lives in `:wallet` (shared with WalletConnect); the impl is wired there. */
interface SiweSigner {
    /** Re-auth → fresh [seedSource] → EIP-191 sign of [message].canonical() as [owner]; source zeroized. */
    fun sign(message: SiweMessage, owner: EvmAddress, seedSource: com.tneff.cyppie.wallet.SeedSource): String
}
