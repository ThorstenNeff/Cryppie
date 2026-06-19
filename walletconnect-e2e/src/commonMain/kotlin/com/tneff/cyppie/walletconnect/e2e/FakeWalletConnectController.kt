package com.tneff.cyppie.walletconnect.e2e

import com.tneff.cyppie.evm.EvmAddress
import com.tneff.cyppie.walletconnect.WcDappMetadata
import com.tneff.cyppie.walletconnect.WcEvent
import com.tneff.cyppie.walletconnect.WcSessionProposal
import com.tneff.cyppie.walletconnect.WcSessionRequest
import com.tneff.cyppie.walletconnect.WcTransport
import com.tneff.cyppie.walletconnect.approvedAddressesFrom
import com.tneff.cyppie.walletconnect.approvedChainIdsFrom
import com.tneff.cyppie.walletconnect.caip2ChainIdOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * KAN-127 — deterministic test double for the WalletConnect transport ([WcTransport]), so the QA team can
 * drive the KAN-50 WC approval/sign **E2E** (Maestro) without a live relay or a real dApp: the flow is
 * reproducible, offline, and CI-safe.
 *
 * **It is NOT a relay.** It does not connect anywhere. A [WcE2eScript] (delivered via env var or deep-link —
 * see [WcE2e]) decides exactly which [WcEvent]s the app receives and when:
 * - [pair] emits the scripted `OnSessionProposal` (mirrors "user scanned the QR / opened the wc: link").
 * - [approveSession] records the approval, emits `OnSessionSettled`, then replays the scripted requests as
 *   `OnSessionRequest`s — so the approval sheet *and* the request-disclosure/sign sheet both appear.
 * - outbound calls ([approveSession]/[respondRequest]/[rejectRequest]/[rejectSession]/[disconnect]) are
 *   **recorded** ([approvedSessions]/[respondedRequests]/…) for in-process integration assertions.
 *
 * 🔒 **Never mainnet (structural):** [WcE2eScript] rejects any production mainnet chain
 * ([PRODUCTION_MAINNET_CHAIN_IDS]) at parse time — an E2E script can only target a testnet, so the harness
 * can never drive a real-funds chain even if mis-wired.
 *
 * 🔒 **Prod-path separation:** this type lives in the **debug-only `:walletconnect-e2e` module** — the app binds
 * it via `debugImplementation` only, so it is physically absent from the release binary. [WcE2e.fakeOrNull] adds
 * a runtime `isDebugBuild` belt on top. Production DI binds the real `WalletConnectController`.
 */
class FakeWalletConnectController(private val script: WcE2eScript) : WcTransport {

    /** A wallet-side approval the app sent. */
    data class ApprovedSession(val proposalId: String, val accounts: List<String>)
    /** A request response the app sent (signature hex / tx hash). */
    data class RespondedRequest(val requestId: Long, val topic: String, val result: String)
    /** A request/session rejection the app sent. */
    data class Rejected(val id: String, val reason: String)

    val approvedSessions = mutableListOf<ApprovedSession>()
    val respondedRequests = mutableListOf<RespondedRequest>()
    val rejectedRequests = mutableListOf<Rejected>()
    val rejectedSessions = mutableListOf<Rejected>()
    val pairedUris = mutableListOf<String>()
    val disconnectedTopics = mutableListOf<String>()

    // replay so a collector that subscribes slightly after pair()/approve() still observes the scripted events.
    private val _events = MutableSharedFlow<WcEvent>(replay = 16, extraBufferCapacity = 64)
    override val events: Flow<WcEvent> = _events.asSharedFlow()

    // Approved chains/accounts per topic, seeded from the script's accounts on approval (#4 / #3 binding under test).
    private val approvedChainsByTopic = mutableMapOf<String, Set<Long>>()
    private val approvedAccountsByTopic = mutableMapOf<String, Set<EvmAddress>>()

    override suspend fun pair(uri: String) {
        pairedUris += uri
        _events.emit(WcEvent.OnSessionProposal(script.toProposal()))
    }

    override suspend fun approveSession(proposalId: String, accounts: List<String>) {
        approvedSessions += ApprovedSession(proposalId, accounts)
        // Bind the session's approved chains/accounts exactly as the real controller would derive them.
        approvedChainsByTopic[script.topic] = approvedChainIdsFrom(chains = listOf(script.chain), accounts = accounts)
        approvedAccountsByTopic[script.topic] = approvedAddressesFrom(accounts)
        _events.emit(WcEvent.OnSessionSettled(script.topic))
        // Now the dApp's signing requests arrive — the disclosure/sign sheet under test.
        script.requests.forEach { req ->
            _events.emit(
                WcEvent.OnSessionRequest(
                    WcSessionRequest(
                        requestId = req.requestId,
                        topic = script.topic,
                        chainId = script.chain,
                        method = req.method,
                        params = req.params,
                        dapp = WcDappMetadata(script.dappName, "", script.dappUrl, verifyContext = script.verifyContext),
                    ),
                ),
            )
        }
    }

    override suspend fun approvedChains(topic: String): Set<Long> = approvedChainsByTopic[topic] ?: emptySet()

    override suspend fun approvedAccounts(topic: String): Set<EvmAddress> = approvedAccountsByTopic[topic] ?: emptySet()

    override suspend fun rejectSession(proposalId: String, reason: String) {
        rejectedSessions += Rejected(proposalId, reason)
    }

    override suspend fun respondRequest(requestId: Long, topic: String, result: String) {
        respondedRequests += RespondedRequest(requestId, topic, result)
    }

    override suspend fun rejectRequest(requestId: Long, topic: String, reason: String) {
        rejectedRequests += Rejected(requestId.toString(), reason)
    }

    override suspend fun disconnect(topic: String) {
        disconnectedTopics += topic
        _events.emit(WcEvent.OnSessionDeleted(topic))
    }
}

/** Chain ids the E2E harness must never script — real funds live here. Testnets (e.g. Sepolia) are allowed. */
val PRODUCTION_MAINNET_CHAIN_IDS: Set<Long> = setOf(1L, 8453L) // Ethereum, Base (the app's supported mainnets)

/** A single scripted dApp request the [FakeWalletConnectController] replays after session approval. */
data class WcE2eRequest(val requestId: Long, val method: String, val params: String)

/**
 * A deterministic WalletConnect scenario for [FakeWalletConnectController]. Built from JSON via [parse]
 * (env var / deep-link payload). [chain] must be a **testnet** — [parse] rejects [PRODUCTION_MAINNET_CHAIN_IDS].
 */
data class WcE2eScript(
    val topic: String,
    val chain: String, // CAIP-2, testnet only (e.g. "eip155:11155111" = Sepolia)
    val accounts: List<String> = emptyList(), // CAIP-10 the session offers
    val dappName: String = "E2E dApp",
    val dappUrl: String = "https://e2e.test",
    val verifyContext: String? = "UNKNOWN",
    val requests: List<WcE2eRequest> = emptyList(),
) {
    init {
        val chainId = caip2ChainIdOrNull(chain)
        require(chainId != null) { "WcE2eScript.chain must be an eip155 CAIP-2 ref, was: $chain" }
        require(chainId !in PRODUCTION_MAINNET_CHAIN_IDS) {
            "WcE2eScript refuses a production mainnet chain ($chainId) — E2E must target a testnet (never mainnet)"
        }
    }

    internal fun toProposal() = WcSessionProposal(
        proposalId = topic,
        dapp = WcDappMetadata(dappName, "", dappUrl, verifyContext = verifyContext),
        chains = listOf(chain),
        methods = requests.map { it.method }.distinct().ifEmpty { DEFAULT_METHODS },
    )

    companion object {
        private val DEFAULT_METHODS = listOf("eth_sendTransaction", "personal_sign", "eth_signTypedData_v4")
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Parses a script from its JSON form, e.g.
         * `{"topic":"t1","chain":"eip155:11155111","accounts":["eip155:11155111:0x…"],
         *   "requests":[{"id":1,"method":"personal_sign","params":"[\"0x48…\",\"0x…\"]"}]}`.
         * @throws IllegalArgumentException on malformed JSON, a missing topic/chain, or a mainnet chain.
         */
        fun parse(jsonText: String): WcE2eScript {
            val obj = try {
                json.parseToJsonElement(jsonText).jsonObject
            } catch (e: Exception) {
                throw IllegalArgumentException("Malformed WcE2eScript JSON: ${e.message}")
            }
            val topic = obj["topic"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("WcE2eScript requires a 'topic'")
            val chain = obj["chain"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("WcE2eScript requires a 'chain' (CAIP-2 testnet)")
            val accounts = obj["accounts"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val requests = obj["requests"]?.jsonArray?.map { el ->
                val r = el.jsonObject
                WcE2eRequest(
                    requestId = (r["id"] ?: r["requestId"])?.jsonPrimitive?.let { it.longOrNull ?: it.intOrNull?.toLong() }
                        ?: throw IllegalArgumentException("WcE2eScript request requires a numeric 'id'"),
                    method = r["method"]?.jsonPrimitive?.contentOrNull
                        ?: throw IllegalArgumentException("WcE2eScript request requires a 'method'"),
                    params = r["params"]?.jsonPrimitive?.contentOrNull ?: "[]",
                )
            } ?: emptyList()
            return WcE2eScript(
                topic = topic,
                chain = chain,
                accounts = accounts,
                dappName = obj["dappName"]?.jsonPrimitive?.contentOrNull ?: "E2E dApp",
                dappUrl = obj["dappUrl"]?.jsonPrimitive?.contentOrNull ?: "https://e2e.test",
                verifyContext = obj["verifyContext"]?.jsonPrimitive?.contentOrNull ?: "UNKNOWN",
                requests = requests,
            )
        }
    }
}

/**
 * Fail-closed factory for the WC E2E test double. The app calls this with the script JSON read from the
 * environment variable [ENV_KEY] **or** the [DEEP_LINK_SCHEME] deep-link's `script` query param (the app does
 * the platform-specific getenv / URL-decode; commonMain has neither).
 *
 * Defense-in-depth: this module already ships **debug-only** (the app binds it via `debugImplementation`, so it
 * is absent from release). [fakeOrNull] adds a runtime belt — it returns a fake **only** when [isDebugBuild] is
 * true **and** [scriptJson] is a non-blank, valid (testnet) script; otherwise `null`, so DI falls back to the
 * real `WalletConnectController`. Pass `isDebugBuild` from the app layer (`BuildConfig.DEBUG` / iOS compile flag).
 */
object WcE2e {
    const val ENV_KEY: String = "CYPPIE_WC_E2E"
    const val DEEP_LINK_SCHEME: String = "cyppie://wc-e2e"

    fun fakeOrNull(scriptJson: String?, isDebugBuild: Boolean): FakeWalletConnectController? {
        if (!isDebugBuild) return null // L2 belt — never activate the harness in a release build
        if (scriptJson.isNullOrBlank()) return null
        val script = runCatching { WcE2eScript.parse(scriptJson) }.getOrNull() ?: return null
        return FakeWalletConnectController(script)
    }
}
