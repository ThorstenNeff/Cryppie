package com.tneff.cyppie.auth

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.storage.SecureKeyStore
import com.tneff.cyppie.wallet.SeedSource

/** The bearer-token source the User-Service clients (DCA + all 04–07) consume. */
interface SessionTokenProvider {
    /** The current access token, or null if not signed in. */
    suspend fun token(): String?
}

/** OS-backed JWT persistence (KAN-141, Q2) — over [SecureKeyStore] (Keychain / Android Keystore). The
 *  token is short-lived; kept in-memory in [AuthSession] for the session, persisted here for restore. */
class TokenStore(private val keyStore: SecureKeyStore) {
    suspend fun save(jwt: String) = keyStore.protect(ALIAS, jwt.encodeToByteArray())
    suspend fun load(): String? = keyStore.retrieve(ALIAS)?.decodeToString()
    suspend fun clear() = keyStore.clear(ALIAS)

    companion object { const val ALIAS = "cyppie_auth_jwt" }
}

/**
 * App-side SIWE auth session (KAN-141). Holds the JWT in-memory for the session + persists it via
 * [TokenStore]; exposes the bearer via [SessionTokenProvider]. [signIn] runs the full on-device exchange:
 * nonce → EIP-4361 message → personal_sign ([SiweSigner], re-auth fresh source, zeroized) → Keycloak
 * Direct-Grant → RS256 JWT. The caller shows the no-blind SIWE consent (`message.statement`) before this.
 */
class AuthSession(
    private val keycloak: KeycloakClient,
    private val siweSigner: SiweSigner,
    private val tokenStore: TokenStore,
    private val owner: EvmAddress,
    private val nowIso8601: () -> String,
    private val chainId: Long = SiweMessage.DEFAULT_CHAIN_ID,
) : SessionTokenProvider {

    private var jwt: String? = null

    override suspend fun token(): String? = jwt ?: tokenStore.load()?.also { jwt = it }

    /** Full SIWE login with the freshly-unlocked [seedSource] (zeroized by [SiweSigner]). Throws on failure. */
    suspend fun signIn(seedSource: SeedSource) {
        val nonce = keycloak.nonce().nonce
        val message = SiweMessage.forSignIn(owner, nonce = nonce, issuedAt = nowIso8601(), chainId = chainId)
        val signature = siweSigner.sign(message, owner, seedSource) // zeroizes the source
        val token = keycloak.token(siweMessage = message.canonical(), siweSignature = signature).accessToken
        jwt = token
        tokenStore.save(token)
    }

    suspend fun signOut() {
        jwt = null
        tokenStore.clear()
    }
}
