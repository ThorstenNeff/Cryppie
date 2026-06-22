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
 * Ktor [StrategyApi] over the JWT User-Service ([baseUrl]) — the concrete client Dev-1 DI-wires in the app shell
 * for KAN-166 (mirrors [KtorCopyApi]). [bearerToken] supplies the user JWT per call; [httpClient] must have
 * `ContentNegotiation(Json)` installed. Paths per `strategy-enable-scope-contract.md`; the build/submit/poll
 * surface is the shared generic userop endpoints (same as Copy/DCA).
 */
class KtorStrategyApi(
    private val baseUrl: String,
    private val bearerToken: suspend () -> String,
    private val httpClient: HttpClient = jsonHttpClient(),
) : StrategyApi {

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

    override suspend fun prepare(request: StrategyScopeRequest): StrategyPrepare =
        httpClient.post("$base/v1/strategy/session/prepare") {
            expectSuccess = true; bearerAuth(bearer()); contentType(ContentType.Application.Json); setBody(request)
        }.body()

    override suspend fun grantSession(request: StrategyGrantRequest) {
        httpClient.post("$base/v1/strategy/session/grant") {
            expectSuccess = true; bearerAuth(bearer()); contentType(ContentType.Application.Json); setBody(request)
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
}
