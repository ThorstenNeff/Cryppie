package com.tneff.cyppie.auth

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.wallet.SeedSource

/** The bearer-token source the User-Service clients (DCA + all 04–07) consume. */
interface SessionTokenProvider {
    /** A valid (non-expired, refreshed if needed) access token, or null if not signed in / refresh failed. */
    suspend fun token(): String?
}

/** A persisted token (access + refresh + absolute expiry). */
data class StoredToken(val accessToken: String, val refreshToken: String?, val expiresAtEpochSeconds: Long)

/**
 * Token persistence (P1-8). ⚠️ The JWT must NOT live in the biometric `:storage` `SecureKeyStore` (that's
 * the **seed-KEK** store — gating the bearer on a biometric prompt per call breaks Android sign-in and
 * no-ops on Desktop). The token is short-lived + held in-memory ([InMemoryTokenVault]); a persistent
 * **non-biometric** platform vault (Keychain item / Android Keystore-encrypted prefs, no auth-gate) is a
 * `:storage` follow-up — swap the impl then.
 */
interface TokenVault {
    suspend fun save(token: StoredToken)
    suspend fun load(): StoredToken?
    suspend fun clear()
}

class InMemoryTokenVault : TokenVault {
    private var stored: StoredToken? = null
    override suspend fun save(token: StoredToken) { stored = token }
    override suspend fun load(): StoredToken? = stored
    override suspend fun clear() { stored = null }
}

private const val NONCE_MIN_LENGTH = 8
private const val EXPIRY_SKEW_SECONDS = 30L
private const val SIWE_EXPIRY_SECONDS = 300L // SIWE message validity window (N2 replay mitigation)

/**
 * App-side SIWE auth session (KAN-141). [signIn] runs the on-device exchange: nonce (validated, P1-9) →
 * EIP-4361 message (issuedAt pinned UTC, P1-9) → personal_sign ([SiweSigner], re-auth fresh source,
 * zeroized) → Keycloak → token. [token] returns a valid bearer, **refreshing** an expired access token via
 * the refresh token (P1-7), fail-closed (refresh failure → cleared → null → re-login). The caller shows the
 * no-blind SIWE consent (`message.statement`) before [signIn].
 */
class AuthSession(
    private val keycloak: KeycloakClient,
    private val siweSigner: SiweSigner,
    private val vault: TokenVault,
    private val owner: EvmAddress,
    private val nowEpochSeconds: () -> Long,
    private val iso8601: (epochSeconds: Long) -> String, // UTC RFC3339 (…Z) for a given epoch
    private val chainId: Long = SiweMessage.DEFAULT_CHAIN_ID,
) : SessionTokenProvider {

    override suspend fun token(): String? {
        val stored = vault.load() ?: return null
        if (nowEpochSeconds() < stored.expiresAtEpochSeconds - EXPIRY_SKEW_SECONDS) return stored.accessToken
        // Expired → refresh (P1-7), fail-closed: no refresh token or a failed refresh → clear → re-login.
        val refreshToken = stored.refreshToken ?: run { vault.clear(); return null }
        val refreshed = runCatching { keycloak.refresh(refreshToken) }.getOrNull()
            ?: run { vault.clear(); return null }
        persist(refreshed)
        return refreshed.accessToken
    }

    /** Full SIWE login with the freshly-unlocked [seedSource] (zeroized by [SiweSigner]). Throws on failure. */
    suspend fun signIn(seedSource: SeedSource) {
        val nonce = keycloak.nonce().nonce
        require(isValidNonce(nonce)) { "Keycloak SIWE nonce malformed" } // P1-9
        val nowSec = nowEpochSeconds()
        val issuedAt = iso8601(nowSec)
        require(issuedAt.endsWith("Z")) { "issuedAt must be UTC RFC3339 (…Z)" } // P1-9
        // N2: bound the signature's validity window (replay mitigation beyond the single-use nonce, LOW-4/P1-9).
        val expirationTime = iso8601(nowSec + SIWE_EXPIRY_SECONDS)
        val message = SiweMessage.forSignIn(owner, nonce = nonce, issuedAt = issuedAt, chainId = chainId, expirationTime = expirationTime)
        val signature = siweSigner.sign(message, owner, seedSource) // zeroizes the source
        persist(keycloak.token(siweMessage = message.canonical(), siweSignature = signature))
    }

    suspend fun signOut() = vault.clear()

    private suspend fun persist(resp: TokenResponse) =
        vault.save(StoredToken(resp.accessToken, resp.refreshToken, nowEpochSeconds() + resp.expiresIn))

    private fun isValidNonce(nonce: String): Boolean =
        nonce.length >= NONCE_MIN_LENGTH && nonce.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
}
