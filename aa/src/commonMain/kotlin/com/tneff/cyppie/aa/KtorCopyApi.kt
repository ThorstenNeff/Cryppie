package com.tneff.cyppie.aa

import com.tneff.cyppie.rpc.jsonHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Ktor [CopyApi] over the JWT User-Service ([baseUrl]) — the concrete client Dev-1 DI-wires in the app shell
 * (mirrors [KtorDcaApi]). [bearerToken] supplies the user JWT per call; [httpClient] must have
 * `ContentNegotiation(Json)` installed. Paths mirror `docs/copy-trading-enable-submit-contract.md` and reconcile
 * with the backend's published User-Service surface.
 */
class KtorCopyApi(
    private val baseUrl: String,
    private val bearerToken: suspend () -> String,
    private val httpClient: HttpClient = jsonHttpClient(),
) : CopyApi {

    private val base = baseUrl.trimEnd('/')

    init {
        // https-pin: a JWT-bearing User-Service must be TLS (loopback dev hosts are the only http exception).
        require(base.startsWith("https://") || base.startsWith("http://localhost") || base.startsWith("http://10.0.2.2")) {
            "User-Service base URL must be HTTPS (got: $base)"
        }
    }

    // bearer fail-closed: never send a request without a token (not signed in / failed refresh).
    private suspend fun bearer(): String =
        bearerToken().ifBlank { throw IllegalStateException("no auth token — sign in required") }

    override suspend fun prepare(request: CopyScopeRequest): CopyPrepare =
        httpClient.post("$base/v1/copy/session/prepare") {
            expectSuccess = true; bearerAuth(bearer()); contentType(ContentType.Application.Json); setBody(request)
        }.body()

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

    override suspend fun grantSession(request: CopyGrantRequest) {
        httpClient.post("$base/v1/copy/session/grant") {
            expectSuccess = true; bearerAuth(bearer()); contentType(ContentType.Application.Json); setBody(request)
        }
    }
}
