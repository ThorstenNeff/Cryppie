package com.tneff.cyppie.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NonceResponse(val nonce: String, val expiresInSeconds: Long = 300)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/**
 * Keycloak SIWE client (KAN-141). [baseUrl] = `https://auth.cyppie.com`. (1) [nonce] → single-use nonce
 * (300s). (2) [token] → form-urlencoded **Direct-Grant / ROPC** exchange (`grant_type=password`, no client
 * secret; the **SIWE signature is the credential** per ADR-0026, not a user password) with `siwe_message`
 * + `siwe_signature` → RS256 access token. Field names are exact per the backend contract. [httpClient]
 * must have `ContentNegotiation(Json)`.
 */
class KeycloakClient(
    private val baseUrl: String,
    private val httpClient: HttpClient,
) {
    private val base = baseUrl.trimEnd('/')

    suspend fun nonce(): NonceResponse =
        httpClient.get("$base/realms/cyppie/siwe/nonce").body()

    suspend fun token(siweMessage: String, siweSignature: String): TokenResponse =
        httpClient.submitForm(
            url = "$base/realms/cyppie/protocol/openid-connect/token",
            formParameters = parameters {
                append("grant_type", "password")
                append("client_id", CLIENT_ID)
                append("siwe_message", siweMessage)
                append("siwe_signature", siweSignature)
            },
        ).body()

    companion object {
        const val BASE_URL = "https://auth.cyppie.com"
        const val CLIENT_ID = "cyppie-app" // Direct-Grant/ROPC client, no secret (SIWE sig = credential, ADR-0026)
    }
}
