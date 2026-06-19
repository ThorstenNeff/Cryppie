package com.tneff.cyppie.aa

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable

@Serializable
private data class GrantRequest(val config: SessionConfig, val enableSignature: String)

@Serializable
private data class SignatureRequest(val signature: String)

/**
 * Ktor [DcaApi] over the JWT User-Service ([baseUrl]). [bearerToken] supplies the user JWT per call (the
 * session/auth layer owns refresh). [httpClient] must have `ContentNegotiation(Json)` installed (the app
 * shell configures it, like the `:rpc`/`:market` clients). Paths mirror Ph0 §4/§5 and reconcile with the
 * backend's published User-Service surface.
 */
class KtorDcaApi(
    private val baseUrl: String,
    private val httpClient: HttpClient,
    private val bearerToken: suspend () -> String,
) : DcaApi {

    private val base = baseUrl.trimEnd('/')

    override suspend fun buildSessionEnable(config: SessionConfig): SessionEnable =
        httpClient.post("$base/v1/me/sessions/enable") {
            bearerAuth(bearerToken()); contentType(ContentType.Application.Json)
            setBody(config)
        }.body()

    override suspend fun grantSession(config: SessionConfig, enableSignature: String): GrantResult =
        httpClient.post("$base/v1/me/sessions") {
            bearerAuth(bearerToken()); contentType(ContentType.Application.Json)
            setBody(GrantRequest(config, enableSignature))
        }.body()

    override suspend fun listSessions(): List<SessionConfig> =
        httpClient.get("$base/v1/me/sessions") { bearerAuth(bearerToken()) }.body()

    override suspend fun revokeSession(sessionId: String) {
        httpClient.delete("$base/v1/me/sessions/$sessionId") { bearerAuth(bearerToken()) }
    }

    override suspend fun pendingDca(): List<PendingDca> =
        httpClient.get("$base/v1/me/dca/pending") { bearerAuth(bearerToken()) }.body()

    override suspend fun submitSignature(dcaId: String, signature: String) {
        httpClient.post("$base/v1/me/dca/$dcaId/signature") {
            bearerAuth(bearerToken()); contentType(ContentType.Application.Json)
            setBody(SignatureRequest(signature))
        }
    }

    override suspend fun opStatus(chainId: Long, userOpHash: String): OpStatus =
        httpClient.get("$base/v1/userop/$chainId/$userOpHash") { bearerAuth(bearerToken()) }.body()

    override suspend fun aaStatus(): AaStatus =
        httpClient.get("$base/v1/me/aa/status") { bearerAuth(bearerToken()) }.body()
}
