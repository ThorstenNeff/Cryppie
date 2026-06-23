package com.tneff.cyppie.evm.network

import com.tneff.cyppie.evm.Erc4337UserOp
import com.tneff.cyppie.evm.Eip7702Authorization
import com.tneff.cyppie.evm.SmartSessionEnableDigest
import com.tneff.cyppie.evm.SmartSessionGrantVerifier

/**
 * ADR-0027 — the **single source of truth** for everything that varies by EVM network (PRD-09 runtime
 * network-switching). One [NetworkProfile] per [NetworkEnvironment]; every chain-id / RPC-slug / per-chain
 * contract address / AA pin / backend URL flows from here instead of being hardcoded across ~12 sites + ~37
 * bare `1L`/`8453L` literals (recon §2).
 *
 * 🔒 **MAINNET is byte-identical to the pre-ADR constants** — the registry returns the exact values the code
 * pinned before, so the verifier-family byte-pins + every `:evm` test stay regression-green. TESTNET is gated
 * behind [NetworkEnvironment.TESTNET]; its per-chain addresses that genuinely differ (UniversalRouter, DCA
 * router) are **placeholders until sourced from the official testnet deployments** — never guessed.
 */
enum class NetworkEnvironment {
    MAINNET,
    TESTNET,
    ;

    val profile: NetworkProfile
        get() = when (this) {
            MAINNET -> NetworkProfiles.MAINNET
            TESTNET -> NetworkProfiles.TESTNET
        }

    val isTestnet: Boolean get() = this == TESTNET
}

/** Per-chain data that genuinely varies by chain (and by env, for testnet chains). */
data class ChainProfile(
    val chainId: Long,
    /** Alchemy network slug (`eth-mainnet`, `base-sepolia`) — the proxy `/alchemy/rpc/v2/{slug}` routing key. */
    val alchemySlug: String,
    /** Uniswap UniversalRouter for this chain (per-chain, NOT CREATE2-uniform). */
    val universalRouter: String,
    /** Uniswap SwapRouter02 — the DCA buy router for this chain; `null` where not yet wired/sourced. */
    val dcaRouter: String? = null,
    /** Wrapped-native (WETH) for this chain — drives native-token pricing; `null` where not yet sourced. */
    val wrappedNative: String? = null,
)

/**
 * The CREATE2-uniform AA contract addresses — the **same value on every chain** (deterministic deployments),
 * but pinned per-env so the per-net boot address-gate (Backend `verifyAddressesOnChain`) has an explicit anchor.
 * Defaults mirror the existing `:evm` constants exactly.
 */
data class AaAddresses(
    val entryPoint: String = Erc4337UserOp.ENTRY_POINT_V07,
    val kernelV3Implementation: String = Eip7702Authorization.KERNEL_V3_3_IMPLEMENTATION,
    val smartSession: String = SmartSessionEnableDigest.SMART_SESSION_ADDRESS,
    val ownableValidator: String = SmartSessionGrantVerifier.SESSION_VALIDATOR,
    val spendingLimits: String = SmartSessionGrantVerifier.SPENDING_LIMIT_POLICY,
    val timeFrame: String = SmartSessionGrantVerifier.TIMEFRAME_POLICY,
    val permit2: String = SmartSessionGrantVerifier.PERMIT2,
)

/** Per-env backend base URLs — the app routes all traffic per-env (ADR-0027 host-split). */
data class BackendUrls(
    /** `:server` key-proxy base (Alchemy/CoinGecko). Android-emulator caveat: `10.0.2.2`, not `localhost`. */
    val proxyBaseUrl: String,
    val keycloakBaseUrl: String,
    val userServiceBaseUrl: String,
    /** AA-trigger (DCA/Copy/Strategy automation) base — the testnet host runs `AA_ALLOW_TESTNET=1`. */
    val aaTriggerBaseUrl: String,
)

/** One network environment, fully described. The single object every consumer reads. */
data class NetworkProfile(
    val environment: NetworkEnvironment,
    val chains: List<ChainProfile>,
    val aa: AaAddresses,
    val backend: BackendUrls,
) {
    val isTestnet: Boolean get() = environment.isTestnet

    /** Active chain ids, in declaration order (first = the primary/default chain). */
    val chainIds: List<Long> get() = chains.map { it.chainId }

    /** The Alchemy slugs the proxy/clients may reach for this env (the allow-list source). */
    val alchemySlugs: Set<String> get() = chains.map { it.alchemySlug }.toSet()

    fun chain(chainId: Long): ChainProfile? = chains.firstOrNull { it.chainId == chainId }

    fun alchemySlug(chainId: Long): String? = chain(chainId)?.alchemySlug

    fun universalRouter(chainId: Long): String? = chain(chainId)?.universalRouter

    fun supports(chainId: Long): Boolean = chains.any { it.chainId == chainId }
}

/** The two concrete profiles. MAINNET values are byte-identical to the pre-ADR `:evm` constants. */
object NetworkProfiles {

    val MAINNET = NetworkProfile(
        environment = NetworkEnvironment.MAINNET,
        chains = listOf(
            ChainProfile(
                chainId = 1L,
                alchemySlug = "eth-mainnet",
                universalRouter = "0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af",
                dcaRouter = "0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45", // Uniswap SwapRouter02 (mainnet)
                wrappedNative = "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2", // WETH (mainnet)
            ),
            ChainProfile(
                chainId = 8453L,
                alchemySlug = "base-mainnet",
                universalRouter = "0x6fF5693b99212Da76ad316178A184AB56D299b43",
                wrappedNative = "0x4200000000000000000000000000000000000006", // WETH (Base predeploy)
            ),
        ),
        aa = AaAddresses(),
        backend = BackendUrls(
            proxyBaseUrl = "http://localhost:8080",
            keycloakBaseUrl = "https://auth.cyppie.com",
            userServiceBaseUrl = "https://auth.cyppie.com",
            aaTriggerBaseUrl = "https://auth.cyppie.com",
        ),
    )

    /**
     * v1 testnet = **Base Sepolia (84532) only** (PO 2026-06-23: Eth-Sepolia 11155111 is not a backend-supported
     * chain — backend `CHAINS=1/8453/84532`). All addresses are verified: V4 UniversalRouter (Context7 vs official
     * Uniswap V4 deployments) + V3 SwapRouter02 (Backend-verified, canonical) + WETH (OP-stack predeploy) + the
     * CREATE2-uniform AA pins. Sepolia is a deferred follow (re-add a ChainProfile here when the backend supports it).
     */
    val TESTNET = NetworkProfile(
        environment = NetworkEnvironment.TESTNET,
        chains = listOf(
            ChainProfile(
                chainId = 84532L,
                alchemySlug = "base-sepolia",
                // V4 UniversalRouter (Copy/Strategy) — Context7-verified vs official Uniswap V4 deployments.
                universalRouter = "0x492e6456d9528771018deb9e87ef7750ef184104",
                // V3 SwapRouter02 (DCA buy) — Backend-verified vs two official Uniswap V3 sources + eth_getCode
                // (24497 B, canonical, not a fork). NB testnet pools may lack liquidity → live swaps can revert
                // (KAN-153), but the router-config path is correct.
                dcaRouter = "0x94cC0AaC535CCDB3C01d6787D6413C739ae12bc4",
                wrappedNative = "0x4200000000000000000000000000000000000006", // WETH — OP-stack predeploy (same as Base)
            ),
        ),
        aa = AaAddresses(), // CREATE2-uniform → same as mainnet (verify deployed on the testnet at boot)
        backend = BackendUrls(
            proxyBaseUrl = "http://localhost:8080",            // testnet proxy host (AA_ALLOW_TESTNET=1) — set at deploy
            keycloakBaseUrl = "https://auth.cyppie.com",
            userServiceBaseUrl = "https://auth.cyppie.com",
            aaTriggerBaseUrl = "https://auth.cyppie.com",       // TODO(KAN-172): separate testnet aa-trigger host
        ),
    )

    /** Every known profile. Chain ids are disjoint across envs, so a chain id resolves to exactly one. */
    val all: List<NetworkProfile> = listOf(MAINNET, TESTNET)

    /** Every chain id the AA stack knows how to handle, across all envs (the union the hard gate uses). */
    val allChainIds: Set<Long> = all.flatMap { it.chainIds }.toSet()

    /** Resolves a chain id to its [ChainProfile] across all envs (the per-chain address source). */
    fun chainProfile(chainId: Long): ChainProfile? = all.firstNotNullOfOrNull { it.chain(chainId) }
}
