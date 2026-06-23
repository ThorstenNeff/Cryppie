package com.tneff.cyppie.evm

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Recomputes the Rhinestone **Smart-Sessions ENABLE digest** on-device (PRD-05 Ph1, approach C / KAN-143) so
 * the app can verify — before signing — that the disclosed grant matches the 32-byte digest the backend asks
 * it to sign (the GRANT is the P0: its signature *sets* the on-chain caps, so on-chain enforcement is circular
 * there and only an on-device recompute closes it).
 *
 * The digest is a pure EIP-712 `hashTypedData` over a `MultiChainSession` ([Eip712], the shared `:evm` builder)
 * — fully offline-computable; the only runtime input is the session [nonce]. The `getSessionDigest` contract
 * call is NOT part of the signed digest. Type table + domain are pinned 1:1 from `@rhinestone/module-sdk`
 * 0.3.1 (backend spec `smart-sessions-enable-spec.md`); proven against 4 SDK==raw-viem reference vectors.
 *
 * 🔒 **Domain-pinning (the soundness condition):** the EIP-712 domain is `{name:"SmartSession",version:"1"}`
 * with **no chainId / no verifyingContract** (chain binding lives in the body: [ChainSession] `chainId` +
 * `SignedSession.smartSession`). [SMART_SESSION_ADDRESS] + the chain ids are **client-pinned constants here**,
 * NEVER taken from the backend response — else a spoofed module/chain would validate. The caller MUST pass
 * [account] = the device-derived owner EOA (EIP-7702 same-address: SCA address == owner EOA), not a
 * backend-supplied address.
 */
object SmartSessionEnableDigest {

    /** `SMART_SESSIONS_ADDRESS` — CREATE2-deterministic, identical on ETH + Base. Client-pinned. */
    const val SMART_SESSION_ADDRESS: String = "0x00000000008bDABA73cD9815d79069c247Eb4bDA"

    /** The chains the AA stack supports (ADR-0024/0027). A `chainId` outside this fails closed. Sourced from the
     *  [com.tneff.cyppie.evm.network.NetworkProfiles] registry (single-source, union of all envs); mainnet `{1,8453}`
     *  unchanged, testnet `{84532,11155111}` added behind the active-env data gating. */
    val SUPPORTED_CHAIN_IDS: Set<Long> = com.tneff.cyppie.evm.network.NetworkProfiles.allChainIds

    private const val DOMAIN_NAME = "SmartSession"
    private const val DOMAIN_VERSION = "1"

    data class PolicyData(val policy: String, val initData: String)

    data class ActionData(
        val actionTargetSelector: String,
        val actionTarget: String,
        val actionPolicies: List<PolicyData>,
    )

    data class Erc7739Context(val appDomainSeparator: String, val contentName: List<String>)

    data class Erc7739Data(
        val allowedERC7739Content: List<Erc7739Context> = emptyList(),
        val erc1271Policies: List<PolicyData> = emptyList(),
    )

    data class SignedPermissions(
        val permitGenericPolicy: Boolean = false,
        val permitAdminAccess: Boolean = false,
        val ignoreSecurityAttestations: Boolean = false,
        val permitERC4337Paymaster: Boolean = false,
        val userOpPolicies: List<PolicyData> = emptyList(),
        val erc7739Policies: Erc7739Data = Erc7739Data(),
        val actions: List<ActionData> = emptyList(),
    )

    /**
     * The 32-byte enable digest for a single-chain `MultiChainSession`. [account] = device owner EOA (7702
     * same-address); [smartSession] defaults to the pinned [SMART_SESSION_ADDRESS] (override only in tests).
     *
     * @throws IllegalArgumentException if [chainId] is not a [SUPPORTED_CHAIN_IDS] (fail closed).
     */
    fun enableDigest(
        account: String,
        chainId: Long,
        sessionValidator: String,
        sessionValidatorInitData: String,
        salt: String,
        nonce: String,
        permissions: SignedPermissions,
        smartSession: String = SMART_SESSION_ADDRESS,
    ): ByteArray {
        require(chainId in SUPPORTED_CHAIN_IDS) { "unsupported chainId $chainId (pinned: $SUPPORTED_CHAIN_IDS)" }
        val typedData = buildJsonObject {
            put("types", TYPES)
            put("primaryType", "MultiChainSession")
            putJsonObject("domain") {
                put("name", DOMAIN_NAME)
                put("version", DOMAIN_VERSION)
            }
            putJsonObject("message") {
                putJsonArray("sessionsAndChainIds") {
                    addJsonObject {
                        put("chainId", chainId.toString())
                        put(
                            "session",
                            sessionObject(account, sessionValidator, sessionValidatorInitData, salt, nonce, smartSession, permissions),
                        )
                    }
                }
            }
        }
        return Eip712.encode(typedData.toString())
    }

    private fun sessionObject(
        account: String,
        sessionValidator: String,
        sessionValidatorInitData: String,
        salt: String,
        nonce: String,
        smartSession: String,
        permissions: SignedPermissions,
    ): JsonObject = buildJsonObject {
        put("account", account)
        put("permissions", permissionsObject(permissions))
        put("sessionValidator", sessionValidator)
        put("sessionValidatorInitData", sessionValidatorInitData)
        put("salt", salt)
        put("smartSession", smartSession)
        put("nonce", nonce)
    }

    private fun permissionsObject(p: SignedPermissions): JsonObject = buildJsonObject {
        put("permitGenericPolicy", p.permitGenericPolicy)
        put("permitAdminAccess", p.permitAdminAccess)
        put("ignoreSecurityAttestations", p.ignoreSecurityAttestations)
        put("permitERC4337Paymaster", p.permitERC4337Paymaster)
        putJsonArray("userOpPolicies") { p.userOpPolicies.forEach { add(policyObject(it)) } }
        put("erc7739Policies", erc7739Object(p.erc7739Policies))
        putJsonArray("actions") { p.actions.forEach { add(actionObject(it)) } }
    }

    private fun erc7739Object(d: Erc7739Data): JsonObject = buildJsonObject {
        putJsonArray("allowedERC7739Content") {
            d.allowedERC7739Content.forEach { ctx ->
                addJsonObject {
                    put("appDomainSeparator", ctx.appDomainSeparator)
                    putJsonArray("contentName") { ctx.contentName.forEach { add(it) } }
                }
            }
        }
        putJsonArray("erc1271Policies") { d.erc1271Policies.forEach { add(policyObject(it)) } }
    }

    private fun actionObject(a: ActionData): JsonObject = buildJsonObject {
        put("actionTargetSelector", a.actionTargetSelector)
        put("actionTarget", a.actionTarget)
        putJsonArray("actionPolicies") { a.actionPolicies.forEach { add(policyObject(it)) } }
    }

    private fun policyObject(p: PolicyData): JsonObject = buildJsonObject {
        put("policy", p.policy)
        put("initData", p.initData)
    }

    // The EIP-712 type table, pinned 1:1 from @rhinestone/module-sdk 0.3.1 (order is significant). EIP712Domain
    // is added explicitly (only name+version — matching the chainId/verifyingContract-less domain).
    private val TYPES: JsonObject = Json.parseToJsonElement(
        """
        {
          "EIP712Domain": [{"name":"name","type":"string"},{"name":"version","type":"string"}],
          "PolicyData": [{"name":"policy","type":"address"},{"name":"initData","type":"bytes"}],
          "ActionData": [
            {"name":"actionTargetSelector","type":"bytes4"},
            {"name":"actionTarget","type":"address"},
            {"name":"actionPolicies","type":"PolicyData[]"}
          ],
          "ERC7739Context": [{"name":"appDomainSeparator","type":"bytes32"},{"name":"contentName","type":"string[]"}],
          "ERC7739Data": [
            {"name":"allowedERC7739Content","type":"ERC7739Context[]"},
            {"name":"erc1271Policies","type":"PolicyData[]"}
          ],
          "SignedPermissions": [
            {"name":"permitGenericPolicy","type":"bool"},
            {"name":"permitAdminAccess","type":"bool"},
            {"name":"ignoreSecurityAttestations","type":"bool"},
            {"name":"permitERC4337Paymaster","type":"bool"},
            {"name":"userOpPolicies","type":"PolicyData[]"},
            {"name":"erc7739Policies","type":"ERC7739Data"},
            {"name":"actions","type":"ActionData[]"}
          ],
          "SignedSession": [
            {"name":"account","type":"address"},
            {"name":"permissions","type":"SignedPermissions"},
            {"name":"sessionValidator","type":"address"},
            {"name":"sessionValidatorInitData","type":"bytes"},
            {"name":"salt","type":"bytes32"},
            {"name":"smartSession","type":"address"},
            {"name":"nonce","type":"uint256"}
          ],
          "ChainSession": [{"name":"chainId","type":"uint64"},{"name":"session","type":"SignedSession"}],
          "MultiChainSession": [{"name":"sessionsAndChainIds","type":"ChainSession[]"}]
        }
        """.trimIndent(),
    ).let { it as JsonObject }
}
