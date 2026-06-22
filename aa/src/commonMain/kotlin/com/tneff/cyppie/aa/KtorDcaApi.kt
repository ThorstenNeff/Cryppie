package com.tneff.cyppie.aa

import com.tneff.cyppie.rpc.jsonHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
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
    private val bearerToken: suspend () -> String,
    private val httpClient: HttpClient = jsonHttpClient(),
) : DcaApi {

    private val base = baseUrl.trimEnd('/')

    init {
        // P2 (https-pin): a JWT-bearing User-Service must be TLS — never ship the bearer over cleartext.
        // Loopback dev hosts (proxy / Android-emulator 10.0.2.2) are the only http exception.
        require(base.startsWith("https://") || base.startsWith("http://localhost") || base.startsWith("http://10.0.2.2")) {
            "User-Service base URL must be HTTPS (got: $base)"
        }
    }

    /** P2 (bearer fail-closed): never send a request with a blank/absent bearer — a null token (not signed
     *  in / failed refresh) must fail BEFORE the call, not hit the User-Service unauthenticated. */
    private suspend fun bearer(): String =
        bearerToken().ifBlank { throw IllegalStateException("no auth token — sign in required") }

    // KAN-159: the enable is broadcast on-chain (EnableBroadcastApi) before register, so `enableSignature`
    // is dead data — kept as an empty field for wire-compatibility until the backend drops it from the body.
    override suspend fun grantSession(config: SessionConfig): GrantResult =
        httpClient.post("$base/v1/me/sessions") {
            expectSuccess = true; bearerAuth(bearer()); contentType(ContentType.Application.Json)
            setBody(GrantRequest(config, enableSignature = ""))
        }.body()

    override suspend fun listSessions(): List<SessionConfig> =
        httpClient.get("$base/v1/me/sessions") { expectSuccess = true; bearerAuth(bearer()) }.body()

    override suspend fun revokeSession(sessionId: String) {
        httpClient.delete("$base/v1/me/sessions/$sessionId") { expectSuccess = true; bearerAuth(bearer()) }
    }

    override suspend fun pendingDca(): List<PendingDca> =
        httpClient.get("$base/v1/me/dca/pending") { expectSuccess = true; bearerAuth(bearer()) }.body()

    override suspend fun submitSignature(dcaId: String, signature: String) {
        httpClient.post("$base/v1/me/dca/$dcaId/signature") {
            expectSuccess = true; bearerAuth(bearer()); contentType(ContentType.Application.Json)
            setBody(SignatureRequest(signature))
        }
    }

    override suspend fun buildEnableUserOp(request: BuildEnableRequest): BuiltEnableUserOp =
        httpClient.post("$base/v1/userop/build") {
            expectSuccess = true; bearerAuth(bearer()); contentType(ContentType.Application.Json); setBody(request)
        }.body()

    override suspend fun submitEnableUserOp(request: SubmitEnableRequest): String =
        httpClient.post("$base/v1/userop/submit") {
            expectSuccess = true; bearerAuth(bearer()); contentType(ContentType.Application.Json); setBody(request)
        }.body<SubmittedUserOp>().userOpHash

    override suspend fun opStatus(chainId: Long, userOpHash: String): OpStatus =
        httpClient.get("$base/v1/userop/$chainId/$userOpHash") { expectSuccess = true; bearerAuth(bearer()) }.body()

    override suspend fun aaStatus(): AaStatus =
        httpClient.get("$base/v1/me/aa/status") { expectSuccess = true; bearerAuth(bearer()) }.body()
}
