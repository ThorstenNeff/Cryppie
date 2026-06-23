# Running the app against a testnet (Base Sepolia / Sepolia) — app-side config

Pre-GA validation guide for pointing the **client** at a testnet. This covers the **app/`:server` half only**;
the bundler/paymaster/User-Service half (aa-trigger + Pimlico on testnet) is the Backend agent's parallel doc.

> **Mechanism, up front (no guessing):** there is **no build flavor and no runtime network toggle**. The chain
> set is **hardcoded in source constants** (Ethereum `1` + Base `8453`). Switching to a testnet means **editing a
> handful of constants** and rebuilding. The app shell points all data traffic at the local `:server` key-proxy.
> Target chain ids: **Base Sepolia `84532`**, **Sepolia `11155111`**.

---

## 0. Prerequisites (this worktree)

- `local.properties` with `sdk.dir=…` and `keystore.properties` (storeFile/storePassword/keyAlias/keyPassword) must
  exist or every Gradle task fails at `:app:androidApp` (signing config is read eagerly — `app/androidApp/build.gradle.kts:62`).
- An **Alchemy API key** in `~/.gradle/gradle.properties` as `alchemyApiKey=…` (out-of-repo, never committed). The
  same key works for testnet — Alchemy keys are multi-network. This is the **R2 / KAN-104** key-proxy path (ADR-0021);
  the key lives **only** in `:server`, never in the client binary.
- JDK 21 (`JAVA_HOME=…/amazon-corretto-21.jdk/Contents/Home`).

---

## 1. Start the key-proxy (`:server`) and allow the testnet networks

All Alchemy/RPC traffic is routed through `:server`, which injects the key server-side.

**Run it:**
```bash
./gradlew :server:run    # serves http://localhost:8080 ; bridges alchemyApiKey from ~/.gradle/gradle.properties
```

**Allow the testnet upstreams** — the proxy is an allow-list (abuse guard) that is **mainnet-only today** and is
**not** env-overridable, so this is a source edit:

`server/src/main/kotlin/com/tneff/cyppie/ProxyConfig.kt:42`
```kotlin
// before
val DEFAULT_NETWORKS: Set<String> = setOf("eth-mainnet", "base-mainnet")
// after
val DEFAULT_NETWORKS: Set<String> = setOf("eth-mainnet", "base-mainnet", "eth-sepolia", "base-sepolia")
```
The proxy builds the upstream as `https://{network}.g.alchemy.com/v2/{key}` (`ProxyConfig.kt:95`), so the slugs
must be Alchemy's: **`eth-sepolia`**, **`base-sepolia`**.

---

## 2. Client chain ↔ network-slug maps (THREE places — they are duplicated)

The chain-id → Alchemy-slug mapping is **copied in three spots**. All three must learn the testnet chains or the
clients silently drop them (`else -> null`).

1. `rpc/.../AlchemyProxy.kt` — the canonical one, drives RPC/NFT/portfolio:
   - `AlchemyNetworks.of(chainId)` (line 5): add `84532L -> "base-sepolia"`, `11155111L -> "eth-sepolia"`.
   - `AlchemyNetworks.supportedChainIds` (line 12): `listOf(1L, 8453L)` → add `84532L` and/or `11155111L`
     (this list is what `WalletShell` iterates to build per-chain RPC/NFT/portfolio clients).
2. `rpc/.../AlchemyPriceClient.kt:112` — its own private `alchemyNetwork()` + `networkChainId()` copies.
3. `rpc/.../AlchemyDataClient.kt:80` — likewise.

**Chain model** (display + CAIP-2) — `walletcore/.../EvmChain.kt`:
```kotlin
enum class EvmChain(val chainId: Long, val caip2: String, val displayName: String) {
    ETHEREUM(1L, "eip155:1", "Ethereum"),
    BASE(8453L, "eip155:8453", "Base"),
    BASE_SEPOLIA(84532L, "eip155:84532", "Base Sepolia"),     // add
    SEPOLIA(11155111L, "eip155:11155111", "Sepolia"),         // add
}
```
`EvmChain.fromChainId()` backs the token-catalog lookup in `WalletShell` (`portfolioKnownGood`, `marketWatchlist`).

---

## 3. AA contract addresses — what changes vs what stays

Most AA infra is **CREATE2-deterministic → identical address on every chain** (incl. testnet) and is client-pinned.
**No edit** for these, but you **must confirm each is actually deployed** on the target testnet (CREATE2 ⇒ same
address *if* deployed):

| Contract | Constant / location | Per-chain? | Testnet action |
|---|---|---|---|
| EntryPoint v0.7 `0x0000000071727De…32` | `evm/Erc4337UserOp.kt:19` `ENTRY_POINT_V07` | same all chains | none (verify deployed) |
| Kernel v3.3 / 7702 target `0xd6CEDDe84…b28` | `evm/Eip7702Authorization.kt:22` `KERNEL_V3_3_IMPLEMENTATION` | same all chains | none (already byte-tested vs **84532** vector) |
| Permit2 `0x0000…78BA3` | `evm/SmartSessionGrantVerifier.kt:79` `PERMIT2` | same all chains | none |
| SmartSession module `0x…8bDABA…` | `evm/SmartSessionEnableDigest.kt:33` | same all chains | none |
| SESSION_VALIDATOR `0x…13fdB5…` | `evm/SmartSessionGrantVerifier.kt:70` | same all chains | none |
| SpendingLimits `0x…33212e…` / TimeFrame `0x…D30f61…` | `SmartSessionGrantVerifier.kt:65-66` | same all chains | none |
| **UniversalRouter** | `evm/SmartSessionGrantVerifier.kt:86` `universalRouter(chainId)` | **YES — per-chain map** | **must add testnet UR** |

**Two per-chain edits are mandatory for AA on testnet:**

1. `evm/.../SmartSessionGrantVerifier.kt:86` `universalRouter(chainId)` returns `null` for any non-mainnet chain →
   every Strategy/Copy grant verify fails closed. Add the **Base Sepolia / Sepolia UniversalRouter** addresses.
   ⚠️ These differ per chain and are **not** in the codebase — take them from Uniswap's official deployment list
   for `84532` / `11155111`; **do not guess**.
2. `evm/.../SmartSessionEnableDigest.kt:36` `SUPPORTED_CHAIN_IDS = setOf(1L, 8453L)` is a **fail-closed gate**
   (`require(chainId in SUPPORTED_CHAIN_IDS)`, line 82) → add the testnet chain ids or `getNonce`/enable throws.

> DCA buy uses **SwapRouter02** (`0x68b3465833fb72A70ecDF485E0e4C7bD8665Fc45`, mainnet). It is **passed in**
> (not a per-chain constant in the verifier), via `dcaGrantParams.router` / the backend-published config — so its
> testnet address is set in §5, not here.

---

## 4. Token catalog (testnet token addresses)

`walletcore/.../TokenCatalog.kt` hardcodes per-chain USDC/USDT/DAI/WETH for `1` + `8453` only. Add testnet rows
keyed by `84532` / `11155111`, or the curated/priced set (`portfolioKnownGood`, `marketWatchlist`) is empty/wrong.
⚠️ Testnet ERC-20s differ per chain — source from the official faucet/deployment; **do not guess**. Note:
**WETH on Base Sepolia is the canonical predeploy `0x4200000000000000000000000000000000000006`** (same as Base
mainnet); USDC testnet addresses are different and must be filled in.

---

## 5. Hardcoded feature params + proxy host (app shell)

`feature/wallet/.../WalletShell.kt`:

- **Proxy base URL** (line 126): `private const val PROXY_BASE_URL = "http://localhost:8080"`. On the **Android
  emulator** the host proxy is `http://10.0.2.2:8080`, not `localhost` (see the comment at line 124). There is **no
  build-config field** for this yet (release injection is a noted follow-up) — edit the constant for emulator runs.
- **DCA grant params** (line 192, `dcaGrantParams`): hardcoded **mainnet** `chainId = 1L` + mainnet
  UniversalRouter/USDC/WETH. For a testnet DCA run, switch `chainId` to `84532`/`11155111` and the
  router/spendToken/buyToken to their testnet addresses (these otherwise drive the grant UX + on-device disclosure).
- **Backend URLs** (lines 187-188): `KEYCLOAK_BASE_URL` / `USER_SERVICE_BASE_URL = "https://auth.cyppie.com"` —
  the AA/DCA backend (User-Service + SIWE). **This is the Backend agent's half**: those URLs must point at the
  testnet User-Service, and its bundler/paymaster (Pimlico) must target the testnet. The app only consumes them.

WalletConnect: `WC_PROJECT_ID` (buildConfigField, `app/androidApp/build.gradle.kts:51`, from `wcProjectId` in
`~/.gradle/gradle.properties`) — chain-agnostic, but needed for real WC pairing.

---

## 6. Minimal checklist

1. `~/.gradle/gradle.properties`: `alchemyApiKey=…` (and `wcProjectId=…` for WC).
2. `ProxyConfig.kt:42` → add `eth-sepolia` / `base-sepolia` to `DEFAULT_NETWORKS`.
3. `AlchemyProxy.kt` (`of` + `supportedChainIds`) **+** `AlchemyPriceClient.kt:112` **+** `AlchemyDataClient.kt:80` → add testnet slugs.
4. `EvmChain.kt` → add `BASE_SEPOLIA` / `SEPOLIA`.
5. `SmartSessionGrantVerifier.kt:86` `universalRouter()` → add testnet UR addresses (from Uniswap docs).
6. `SmartSessionEnableDigest.kt:36` `SUPPORTED_CHAIN_IDS` → add testnet chain ids.
7. `TokenCatalog.kt` → add testnet tokens (WETH Base-Sepolia = `0x4200…0006`; USDC etc. from faucet docs).
8. `WalletShell.kt`: `PROXY_BASE_URL` (emulator → `10.0.2.2:8080`) + `dcaGrantParams` testnet + confirm backend URLs (Backend half).
9. `./gradlew :server:run`, then build/run the app.

## Open caveats (flag before relying on a green run)

- **CREATE2 deployment is not guaranteed on a given testnet.** Same address only *if* the module is deployed there.
  Verify EntryPoint/Kernel/Permit2/SmartSessions/validators/policies are live on `84532`/`11155111` before signing.
- **No env knob for `allowedNetworks`** — `ProxyConfig.fromEnvironment()` never reads it, so the §1 edit is a code change.
- **Three duplicated network maps** (§2) — easy to update one and miss the others; the symptom is a chain that prices
  but won't RPC, or vice-versa.
- **Release proxy-URL injection** (a `BuildConfig` field for `PROXY_BASE_URL`) is an open follow-up — today it's a constant.
- Addresses **not** in the codebase (testnet UniversalRouter, SwapRouter02, USDC) are intentionally left as
  "fill from the official source" — **do not invent them.**
