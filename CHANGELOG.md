# Changelog

All notable changes to Cyppie are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project tracks work per Jira ticket
(project `KAN`). Entries are summarised changes (what/why + ticket key), not raw commit logs.

## [Unreleased]

### Added
- **KAN-112 — Alchemy/RPC key-proxy (`:server`, ADR-0021, security).** The Ktor `:server` module now
  fronts Alchemy (Data / Prices / NFT REST + JSON-RPC) as a **pass-through proxy** so **no API key ships
  in the client binary** (reverses the "on-device, no backend" line specifically for key safety). The
  key is read **server-side only** at startup — JVM system property `alchemyApiKey` (the build bridges it
  from the machine-wide `~/.gradle/gradle.properties`, never committed) or `$ALCHEMY_API_KEY` (deploy);
  the build wires the gradle property into the `run` task so `./gradlew :server:run` just works locally.
  Proxy routes `/alchemy/{data|prices|nft|rpc}/...` inject the key into the upstream URL and relay
  method/query/body + upstream status/body **verbatim**. Abuse guards: a network **allow-list**
  (eth-mainnet / base-mainnet — no open relay), a per-client in-memory **rate limit** (dependency-free
  fixed window), and a **503** when the key is unconfigured. Client side, `:rpc` gains `AlchemyProxyConfig`
  (produces the proxy base URLs the Alchemy clients expect) + shared `AlchemyNetworks` mapping; a down /
  keyless proxy already maps to `RpcException.AllProvidersFailed` → clean **degraded** behaviour (FR-4),
  no key, no leak. 7 `:server` + 3 `:rpc` tests (key-injection, pass-through, query forwarding, network
  reject, missing-key 503, rate-limit, health-no-leak). ADR-0021 (Key-Proxy-Architektur) drafted separately.
- **KAN-103 — Live Wallet-Home (app-shell Home wired end-to-end).** The Home destination renders the
  real wallet instead of a placeholder: a new non-web `SeedSession` (`:storage`) holds the unlocked
  `SecureSeedSource` (set by unlock, cleared by auto-lock), and a `WalletShell` (`:feature:wallet`)
  builds `WalletRepository(AccountManager(EvmKeyManager(seedSource)), rpcByChain)` from it — RPC over
  **public dev/test endpoints** (publicnode ETH/Base, no key; release keys via build-config = follow-up)
  — and hosts **Home → Receive / Add-token** nav (unblocks the Receive E2E). `WalletHomeScreen` gained
  `onReceive`/`onAddToken` entry points (receive always available, even on an empty wallet) and now
  formats **token rows with per-token `decimals`/`symbol` from `TokenCatalog`** (KAN-89 M1 gate — never
  `NATIVE_DECIMALS`, which would render USDC/USDT as "0"). Bridged into `:app:shared` via a non-web
  `WalletShellRoot` seam (web = stub, never routed). Curated tokens query per chain; persisting
  added-by-contract tokens = follow-up. Builds on every target incl. web + APK; wallet/onboarding tests green.
- **KAN-102 — Portfolio transfer history → FIFO / 24h (PRD-03, Slice 4 — part 2).** Stage 1: `:rpc`
  `AlchemyTransfersClient` (`alchemy_getAssetTransfers` JSON-RPC) → normalized `AssetTransfer`
  (external / internal / erc20 / erc721 / erc1155, block timestamp, raw value + decimals, contract),
  one direction per call (API constraint) + `pageKey` — web-capable, MockEngine-tested; the transfer
  feed for the FIFO holdings-over-time reconstruction. Folded the KAN-100 review-low (defensive):
  `PortfolioPerformance.unrealizedPnl` now uses a **saturating subtract**, and the drawdown / Sharpe
  ratios are computed **overflow-safe + float-free** (same saturating-discipline family as KAN-98-L1).
  Stage 2: `CostBasisEngine` — **FIFO** cost-basis / realized-P&L reconstruction over a token's
  time-ordered acquire/dispose events (deterministic, float-free; disposing more than tracked →
  zero-cost + `INCOMPLETE_TRANSFERS` / `WALLET_PREDATES_TRACKING` flags, FR-9). Added `Quantity.minus`
  (256-bit, for FIFO lot consumption). Reference-vector tested. Stage 3: `PriceSource.priceHistory`
  (the interface growth — PRD-04 drop-in, default-empty) + `PricePoint`; `PortfolioTimeSeries` —
  **value-over-time series** (cumulative net holdings from transfers × historical prices) feeding the
  drawdown/Sharpe metrics + the chart, and a **robust 24h change** (current holdings × price delta);
  integer / float-free (FR-6). Stage 4 (live wiring): `AlchemyPriceClient.historicalByAddress` (Alchemy
  Historical Prices REST) + `AlchemyPriceSource.priceHistory` — ISO↔epoch via the **stdlib**
  `kotlin.time.Instant` (Kotlin 2.4, `@OptIn(ExperimentalTime)`), so **no new dependency** and **no
  dependency-verification entry** (resolves the ADR-0018 gate without adding kotlinx-datetime). `RichPortfolio`
  E2E assembler: pulls the **full** transfer history (both directions, all `pageKey` pages — L2) and
  prices each transfer at its block time → FIFO cost-basis + value series; a page-cap hit sets
  `AccountHistory.truncated` → forces `INCOMPLETE_TRANSFERS` (no silent truncation for whale wallets —
  L1). 27 `:portfolio` + 14 `:rpc` tests; compiles incl. web/iOS.
- **KAN-100 — Portfolio assembler + performance metrics (PRD-03, Slice 4 — part 1).** `PortfolioService`
  end-to-end assembler: fetch ERC-20 (Alchemy Data) + native (L3) across accounts × chains → price
  (native ETH via its chain's audited WETH) → `PortfolioValuator` (deps injected as functions / the
  `PriceSource` interface → unit-testable, web-capable). `PortfolioPerformance` — the deterministic,
  **float-free** rich-metric math (FR-6), every result `Metric.approximate(reasons)` (FR-9): unrealized
  P&L (value − cost basis), max-drawdown (bps of the running peak), Sharpe-like ratio (×1000, integer
  std-dev via `isqrt`). `Money.plus` is now **saturating** (KAN-98 L1 fix — capped-absurd values can't
  wrap a portfolio total). Tested on JVM; compiles incl. web. Part 2 (separate): FIFO cost-basis from
  `getAssetTransfers` + historical prices (`priceHistory`) → the value series these metrics consume + 24h.
- **KAN-98 — Portfolio balances + valuation + allocation (`:portfolio` / `:rpc`, PRD-03 P1+P2).**
  **Stage A:** `:rpc` Alchemy **Data API** (`tokens/by-address` — multi-chain ERC-20 balances + metadata
  per holder, **follows `pageKey` to the end** so token-rich wallets aren't under-counted) + **Prices
  API** (`tokens/by-address`, **exact-currency only** — never a wrong-currency value) REST clients —
  web-capable, MockEngine-tested.
  **Stage B:** the valuation core — big-int-safe `Quantity.divPow10` + `Valuation` (`rawBalance(wei) ×
  price / 10^…` → fiat cents; no float → deterministic FR-6; >64-bit intermediate, Long result, capped
  on absurd inputs); `Money.plus` guards currency + scale (+ `atScale`); `AlchemyPriceSource`
  (`PriceSource` impl, decimal → fixed-point `Money` @ scale 8); `PortfolioValuator` → total value +
  allocation (sums to exactly 10_000 bps, FR-6), a **robust** total that flips to
  `Approximate(STALE_PRICES)` on a stale price (Q8), and the spam/curation filter (catalog-known-good-in
  OR ≥ dust threshold, Q5 — not a strict allowlist). Tested on JVM (parse/value KATs incl. a >Long
  product, total / allocation-100% / stale / dust); compiles incl. web. The end-to-end fetch→value
  assembler (incl. native-ETH priced via WETH) wires in with the Portfolio VM; 24h + rich metrics = P4.
- **KAN-96 — Portfolio logic foundation (`:portfolio`, PRD-03 / ADR-0017).** New **web-capable** module
  (android/ios/jvm + **js/wasm** — the first fully web feature, FR-7; depends `:rpc` + `:evm` only, takes
  account addresses as input, no key derivation). Stage-1 foundation: the **FR-9 metric-confidence core**
  — `Metric<T>(value, MetricConfidence: Robust | Approximate(reasons))` with `ApproxReason`
  (cost-basis-ambiguity / incomplete-transfers / unverified-tokens / wallet-predates-tracking /
  stale-prices), so a value and its "≈" caveat never get separated — plus domain models (`Money`
  fixed-point fiat, `PortfolioToken`, `Holding`, `AllocationSlice`, `Portfolio`) and the migratable
  `PriceSource` abstraction (Q4 Alchemy-now) with `TokenPrice` staleness (Q8 TTL 60s prices / 30s
  balances). Tested on JVM; compiles incl. web. Stages 2 (valuation/allocation) / 3 (balances + history)
  / 4 (FIFO cost-basis + P&L/Sharpe/drawdown, all `Approximate`) = separate stories.
- **KAN-90 — TokenCatalog authoritative address audit (pre-release security gate).** Cross-checked
  every curated `TokenCatalog` entry's address + symbol + decimals against Etherscan / BaseScan and
  pinned them in `TokenCatalogAuditTest`: a fixture audit (catches a typo to *another valid* address —
  which EIP-55 can't) **plus** a resolve-vs-fixture check (the catalog's claimed symbol/decimals ==
  what `resolveErc20` reads from chain). **Base USDT decision — omitted:** there's no official native
  Tether on Base; the only USDT (`0xfde4…`) is a *bridged* token Tether disclaims, so the curated list
  doesn't endorse it (users add it via Add-by-Contract). 4 Ethereum + 3 Base entries confirmed. JVM tests.
- **KAN-95 — File-backed `CiphertextStore` (single seed-file source).** `:storage` now owns the
  canonical encrypted-seed file: `CiphertextStore.file(path)` + `CiphertextStore.defaultFile()` over a
  platform `defaultSeedFilePath()` — Android `filesDir` (`AndroidStoragePaths.init(filesDir)` at
  startup), iOS Application Support (**excluded from backup**), Desktop `~/.cyppie`. Atomic writes
  (temp + rename / `writeToFile atomically`) via expect/actual file IO. One source of truth for where
  the seed lives, so launch / unlock / onboarding read the same file; ONB-8's per-platform `WalletStore`
  file store dedupes onto this. Round-trip test (write/read/overwrite/clear) on JVM; compiles iOS/Android.
- **KAN-81 — Wallet-Home (accounts + multi-chain balances + switcher).** `WalletHomeScreen` +
  `WalletHomeViewModel` in `:feature:wallet` over `:walletcore` (`accounts` / `accountPortfolio`):
  BIP-44 account switcher (EIP-55 truncated addresses), per-chain (Ethereum/Base) native + ERC-20
  balances, `degraded` warning banner (FR-4), and Loading / Empty / Error+Retry states. Read-only, no
  fiat valuation (PRD-02). Amounts via a new **big-integer-safe** `formatTokenAmount` (commonMain, no
  BigInteger; truncating so a balance is never overstated; 8 unit tests incl. `>Long.MAX`). Copy from
  `composeResources` (`home_*`, en+de; rest → KAN-76), tokens-only, testTags `home_*`, adaptive
  (≤480 dp); added `CryptasaIcons.Refresh`. The `WalletRepository` is DI-injected — its live
  construction (unlocked `SeedSource` + RPC config + nav entry-points) is the app-shell story KAN-89.
- **KAN-88 — NFT read logic (Alchemy NFT API v3).** New **web-capable** `:rpc` `NftReadClient` +
  `AlchemyNftClient` (`getNFTsForOwner` v3, `eth-mainnet`/`base-mainnet`): paging (`pageKey`, default
  pageSize 50), `excludeFilters=SPAM` default + `isSpam` passthrough, **Alchemy-cached media URLs only**
  (privacy — no fetch from arbitrary NFT origins/IPFS), ERC-721/1155 mapping (`balance` for 1155),
  retry/timeout from the shared Ktor stack, errors → `RpcException`. Domain models `NftItem` / `NftPage`
  / `NftType`, keyed on `chainId` to keep `:rpc` free of `:walletcore`'s `EvmChain` (UI maps via
  `EvmChain.fromChainId`). `WalletRepository.nfts(accountIndex, chain, pageKey)` wraps it (non-configured
  chain throws). Read-only, no valuation/floor (PRD-03). API key = build-config (never committed).
  Tested on JVM (MockEngine: ERC721+1155 parse, paging, spam-filter, missing-fields→null/UNKNOWN,
  HTTP-error→AllProvidersFailed; `:walletcore` passthrough / pageKey / unconfigured-chain); compiles
  incl. web. Gate for the NFT-grid UI (KAN-51).
- **KAN-91 — Send-flow orchestrator (`:send`).** New non-web `:send` module — the **one** orchestrator
  (ADR-0019 pending) for both entry points (in-app send + WalletConnect `eth_sendTransaction`).
  `prepare(input, accounts)`: resolves the signer account (#3 — from the wallet's cached public
  addresses, no seed pre-approval) → completes the tx via `:rpc` (nonce / `eth_feeHistory` /
  `eth_estimateGas` / chainId, **fill-missing-not-override** #2) → validates `balance ≥ value +
  gasLimit*maxFee` → assembles the disclosure of the **completed** tx (#1 signed == disclosed), with
  ERC-20 `transfer`/`transferFrom`/`approve` decoded human-readably (unknown calldata → raw + warning,
  fail-safe #5). `signAndBroadcast(prepared, seedSource)`: signs via L2 over the unlocked seed,
  **zeroizes it immediately** (M1, minimal seed window), then broadcasts — node-rejections are
  authoritative (no retry), transport → retryable `NetworkError`. Chain-bound (#4: built + signed on
  the request's chain id). Prereqs added: `:rpc.estimateGas` (`eth_estimateGas`) + 256-bit `Quantity`
  `plus`/`times`/`compareTo` (balance math). Tested on JVM (9 — complete/disclose, fill-not-override,
  account/chain binding, signed-tx **recovers to the bound account**, insufficient-funds, ERC-20 decode,
  sign+zeroize+broadcast, node-reject); compiles iOS/Android. UI = separate story; the WalletConnect
  adapter (decode → `SendInput`, respond) folds in when `:walletconnect` merges.
- **KAN-89 — App-shell launch routing.** `AppRoot()` in `:app:shared` drives the app start:
  **`walletExists()`** → **onboarding** (no wallet) vs **unlock** (returning user) → hand off to
  **Home**; `App()` renders `AppRoot()` instead of `OnboardingRoot()`. `OnboardingRoot` gained an
  `onComplete` callback (Biometrics finish zeroizes secrets, then hands off). `walletExists()` is a
  non-web `expect`/`actual` seam — android/ios/jvm read the shared `SeedVault(CiphertextStore.defaultFile())`
  (**KAN-95**, off-main); js/wasm = false (onboarding/read-only). Onboarding's `WalletStore` is **deduped**
  onto the same `CiphertextStore.defaultFile()` (drops the per-module file IO, incl. the iOS NSData store)
  so launch-check and persistence share one seed file; `AndroidStoragePaths.init(filesDir)` runs in
  `CyppieApplication.onCreate` (robust across entry points). Home stays a placeholder until the live
  `WalletRepository` is wired. Compiles on every target incl. web.
- **KAN-92 — Returning-user unlock (SPEC_UNLOCK).** `UnlockScreen` + `UnlockViewModel` replace the
  app-shell stub: app password → `UnlockSupport.unlock` (non-web seam: `SeedVault(...).unlock` off-main →
  holds the `SecureSeedSource` session) → Home. Reuses the ONB-3 masked field; wrong password shows a
  no-reveal error and, from the 5th attempt, **exponential backoff** (30 s→1 m→5 m→15 m→30 m cap) disables
  input with a live `mm:ss` countdown — **no auto-wipe** (KDF + backoff are the defense, ADR-0009; pure
  throttle, 5 unit tests). **Biometric unlock** (Android): the system `BiometricPrompt` releases the app
  password from the auth-bound Keystore (`:storage`, KAN-80) and drives the same unlock path; password is
  always the fallback (iOS biometric unlock = follow-up). **Auto-lock**: backgrounded 5 min clears the
  in-memory `SeedSource` (lifecycle observer) → unlock again. **"Forgot password?"** → blocking
  recover-confirm → import flow (non-custodial recovery, no destructive reset). `FLAG_SECURE`, password
  zeroized in the seam, `unlock_*` i18n/testTags, adaptive ≤480 dp. Web has no local seed → never routed
  to unlock. Builds on every target incl. web + APK.
- **KAN-82 — Reset wipes the biometric enroll (security, M1).** Unified the biometric-unlock Keystore
  alias into one shared `SEED_UNLOCK_ALIAS`: `SeedVault.store()`/`clear()` and the Android enroll
  (`AndroidSecureKeyStore.enableBiometricUnlock`) now use the **same** alias, so a wallet reset/
  overwrite wipes the biometric credential — no stale password left recoverable behind a wiped seed.
  Added zeroize-ownership KDoc on the Android enroll/retrieve (caller owns/zeroizes the password
  bytes). Robolectric test asserts `SeedVault.clear()` wipes the enroll. Lands before Dev-1's KAN-33.
- **KAN-80 — `:storage` Android biometric-unlock enroll API.** `AndroidSecureKeyStore` now exposes a
  wallet-level API over a **private, fixed Keystore alias** (`cyppie_seed_unlock`) so the app never
  names it: `biometricEnrollCipher()` + `enableBiometricUnlock(password, authenticatedCipher)` (enroll
  after the password is set), `biometricUnlockCipher()` + `retrieveUnlockPassword(authenticatedCipher)`
  (unlock), and `disableBiometricUnlock()`. The alias-parameter wrap/unwrap helpers are now private;
  the `BiometricPrompt` UI stays in ONB-9 (Dev-1) via the `CryptoObject` cipher. iOS Keychain
  unchanged. Robolectric androidHostTest round-trips enroll→unlock and disable with a fake-HW Cipher.
- **KAN-83 — Token add/list (Stage 1: ERC-20 resolver).** `WalletRepository.resolveErc20(address, chain)`
  → `TokenResolution` (Resolved/NotErc20/NetworkError): reads `symbol()` + `decimals()` via `eth_call`
  (`Erc20Abi`, bytes32-symbol fallback), mapping `Node`-revert / undecodable → not-ERC-20 and
  transport / all-providers-failed → network-error. EIP-55 enforced by the `EvmAddress` param. Added
  `Erc20Token` / `TokenResolution` models. Tested on JVM (resolve, revert, EOA/empty, transport,
  no-provider).
  - **Stage 2 (`AddTokenScreen`, KAN-49):** add an ERC-20 by contract address — LTR contract field
    (EIP-55 validated locally), resolve → token card (`Symbol · Decimals` + check) / `NotErc20` field
    error / `NetworkError` retry banner; Add CTA disabled until resolved; resolution announced as a
    `liveRegion` (a11y). Dedup is the host's job ([onAdd]), not the screen. `token_*` testTags/i18n,
    tokens-only, adaptive. Desktop `runComposeUiTest` covers resolved / not-ERC-20 / invalid / network
    + retry. (Generic invalid-address copy + 12 locales → KAN-76.)
  - **Stage 3 (`TokenCatalog`):** bundled, curated default token list for Ethereum + Base (USDC/USDT/
    DAI/WETH majors) embedded in-code (no network fetch — supply-chain-safe), canonical EIP-55,
    `forChain` / `find`. Tested on JVM.
- **KAN-78 — Wallet D2: Receive screen (`:feature:wallet`).** New Compose-MP feature module
  (android/ios/jvm; depends `:designsystem`+`:walletcore`), modeled on `:feature:onboarding`, with
  `ReceiveScreen(address, onBack)` (KAN-47): segmented chain selector (Ethereum/Base — **same EVM
  address**, switching only re-labels + frames the warning), QR via **qrose 1.1.2**
  (`rememberQrCodePainter`, bare address, fixed black-on-white **always-light** carrier for
  scannability), full **LTR** untruncated selectable address + content-copy, a primary copy button
  with copied-state, and a `CryptasaBanner(Warning)` network warning. testTags = i18n keys
  (`receive_*`); tokens-only, adaptive (max-width 480). Added `CryptasaIcons.ContentCopy`. Desktop
  `runComposeUiTest` verifies the surface + copy→copied. i18n: en/de imported; the other 12 locales
  fall back to en until the design i18n adds the wallet/receive keys (designer follow-up).
- **KAN-77 — Wallet-Core read-side domain.** New non-web `:walletcore` module (android · ios · jvm;
  depends on `:wallet`+`:rpc`) orchestrating the read paths: `AccountManager` (derive/list EVM accounts
  via L1, addresses only) and `WalletRepository` — native + ERC-20 balances and nonce per `EvmChain`
  (Ethereum/Base) via L3, `receiveInfo` (address + **EIP-681** QR payload), `chainBalances`/
  `accountPortfolio` aggregation with `degraded` surfacing (FR-4) and **per-token isolation** (one
  failing `balanceOf` doesn't blank wallet-home). PRD-02-scoped (NFT / fiat valuation = PRD-03);
  write/send/seed/WC paths fold in later. M1 note: reads are address-based so a later web-read-only
  split is trivial (ADR-0016). Tested on **JVM and iOS** (Hardhat addresses, EIP-681, aggregation,
  missing-chain/degraded/per-token-isolation via a fake RPC).
- **KAN-17 — ONB-8 Wallet-setup screen.** `WalletSetupScreen`: on entry, auto-encrypts the seed under
  the app password and persists it (not cancelable, no back). Goes through a new `WalletStore`
  `expect`/`actual` **seam** to `:storage` `SeedVault` (PBKDF2-HMAC-SHA512 → AES-GCM, ADR-0009) —
  android/ios/jvm only (js/wasm `Unsupported`, Web read-only). The 64-byte BIP-39 seed
  (`Mnemonic.toSeed()`) is the only thing stored — no account model / derived keys (PRD-02). Backing
  is a dependency-free, app-private, **non-synced** file (Android `filesDir`; iOS Application Support,
  **excluded from iCloud backup**; Desktop `~/.cyppie`). Success → "wallet ready" → zeroizes the
  in-memory mnemonic → auto-advances to ONB-9; failures map to the spec frames — encryption / keystore
  → blocking `CryptasaDialog` (retry / cancel-resets-flow), storage-full → danger `CryptasaBanner`
  (retry). `ProgressRing`, live-region loading announce, copy from `composeResources` (`onb_setup_*`),
  testTags `onb_setup_*`. (Android backup-exclusion rules + iOS `lock` icon = follow-ups.)
- **KAN-16 — ONB-7 Confirm-backup screen.** `ConfirmBackupScreen` (create flow): challenges 3 random
  positions of the generated phrase (positions picked once per session in `OnboardingViewModel`); the
  user re-enters those words. Only a *wrong* field is flagged (`onb_backup_err_wrong` — never reveals
  the expected word); "confirm" is disabled until all challenge fields are filled and any flagged one
  is corrected. From the 3rd failed attempt a `CryptasaBannerTone.Warning` banner offers "show
  recovery phrase again" (→ ONB-6, identical phrase). Screenshots blocked (`SecureScreenEffect`), no
  plaintext logging. Entries/positions/attempts live in the VM (survive navigation). Copy from
  `composeResources` (`onb_backup_*`; added base `onb_backup_word_label` — needs i18n translation),
  tokens only, testTags `onb_backup_*` (per-slot cells + error + continue).
- **KAN-15 — ONB-6 Show-seed screen.** `ShowSeedScreen` (create flow): generates a fresh BIP-39 phrase
  once via the `:wallet` CSPRNG through the `MnemonicSupport` seam (held in `OnboardingViewModel`,
  identical across navigation). Words are **covered until tapped** (mask + eye affordance) behind a
  hard danger warning banner; "continue" is gated on the "I've written it down" `CryptasaCheckbox`.
  On generation failure a **blocking** `CryptasaDialog` appears with a single retry action and **no
  insecure fallback** (`onb_show_seed_error_dialog`). No share/cloud action; copy is opt-in. Screenshots
  blocked (`SecureScreenEffect` / Android `FLAG_SECURE`); phrase never logged. Read-only `SeedWordCell`
  grid, copy from `composeResources` (`onb_seedshow_*`), tokens only, testTags `onb_show_seed_*`.
- **KAN-14 — ONB-5 Seed-import screen.** `ImportSeedScreen` (12/24 `SegmentedControl` + editable word
  grid): live per-word validation against the BIP-39 list, prefix autocomplete suggestions, and a
  full-phrase checksum check on "Import wallet" (banner above the grid on failure). Read-only paste
  from the clipboard (count/character checks; never written back). Consumes L1 `:wallet` through a new
  `MnemonicSupport` `expect`/`actual` **seam** — `:wallet` (no js/wasm; ADR-0008/0016) is wired only on
  android/ios/jvm; js/wasm are `unsupported`, so `:feature:onboarding` stays web-compilable and the
  seed screens are non-web (Web read-only). New `SecureScreenEffect` `expect`/`actual` sets Android
  `FLAG_SECURE` (screenshot/recents protection; other targets no-op for now). Words live in the flow
  `OnboardingViewModel` (in-memory, survive navigation; zeroization deferred to Screen 8). Copy from
  `composeResources` (`onb_seedin_*`), tokens only, testTags `onb_seed_*` (incl. per-cell + banner);
  `SegmentedControl` gained an optional `optionTestTag`. Letters-only/lowercase input, no logging (§5.3).
- **KAN-75 — Wallet-Core: secure seed storage / KDF (ADR-0009), core + seam.** New non-web
  `:storage` module (android · iosArm64 · iosSimulatorArm64 · jvm; depends on `:wallet`):
  - `SeedVault(CiphertextStore, SecureKeyStore)` — `store(seed, password)` / `unlock(password):
    SeedSource` / `clear()`. Per-wallet 16-byte salt → **PBKDF2-HMAC-SHA512** (210k iters, OWASP-aligned)
    derives a 256-bit KEK → **AES-GCM** encrypts the seed (random IV prepended, auth tag) via
    cryptography-kotlin (`provider-optimal`: JDK on jvm/android, Apple on iOS). Versioned record
    header (v1; Argon2id = future v2). No clear-seed persisted; KEK/password bytes zeroized; the
    decrypted seed lives only inside L1's `SeedSource.withSeed`.
  - `CiphertextStore` (persistence-agnostic seam + in-memory impl) and `SecureKeyStore` (biometric/
    hardware convenience seam; `NoopSecureKeyStore` = password-primary default).
  - Platform `SecureKeyStore`s: **iOS `KeychainSecureKeyStore`** — self-contained, generic-password
    item under `SecAccessControl(.biometryCurrentSet)` so the system drives Face/Touch ID on read,
    no app UI. **Android `AndroidSecureKeyStore(context)`** — AES key in AndroidKeyStore
    (`setUserAuthenticationRequired`), wrap/unwrap + wrapped-secret persistence; `retrieve`/`protect`
    take an app-supplied biometric-authenticated `Cipher` (`CryptoObject`), so the `BiometricPrompt`
    UI stays in ONB-9 (Dev-1) — documented cross-agent seam.
  - Audit-gate tests on **JVM and iOS**: round-trip, wrong-password & tampered-ciphertext →
    `InvalidPassword`, bad version → `CorruptData`, not-initialized, fresh salt/IV per store. Unlock
    returns a closeable `SecureSeedSource` (zeroizes the seed on `close`); password bytes are UTF-8
    encoded without an intermediate `String` and zeroized (unpaired surrogates → U+FFFD, N1); the
    caller owns the input `CharArray`.
- **KAN-13 — ONB-4 Confirm-password screen.** `ConfirmPasswordScreen(value, password, onValueChange,
  onNext, onBack)`: masked re-entry field (eye toggle) compared live against the ONB-3 password via
  `validateConfirmPassword` (empty / mismatch); "continue" stays visible but disabled until they
  match, errors surface on focus-loss and clear when fixed. After match it branches by the chosen
  path — import → seed entry (Screen 5), create → seed display (Screen 6). Both password inputs live
  in the flow `OnboardingViewModel` so neither is lost on navigation. Copy from `composeResources`
  (`onb_pwc_*`), tokens only, testTags `onb_confirm_*` (incl. error), adaptive (≤480 dp, scroll).
- **KAN-12 — ONB-3 Set-password screen.** `SetPasswordScreen(value, onValueChange, onNext, onBack)`:
  masked `CryptasaTextField` with an eye toggle (reveal; state via contentDescription), live
  `PasswordStrengthIndicator` (rule-based `evaluatePasswordStrength` → KAN-64 status colours), and a
  "continue" button that stays visible but disabled until valid. Live validation (`validatePassword`:
  empty / whitespace-only / too short <8 / too weak) surfaces on focus-loss and clears when fixed;
  copy from `composeResources` (`onb_pw_*`), tokens only, testTags `onb_password_*`, adaptive
  (≤480 dp, scroll). Security (§5.3): masked, `autoCorrect=false`, no capitalization/suggestions, no
  logging; the value is held in-memory in the flow `OnboardingViewModel` (survives navigation) —
  at-rest handling/zeroization is deferred to the secure-storage work (Screen 8, ADR-0009). Added a
  single `Visibility` icon to `CryptasaIcons` (eye-off variant is a follow-up).
- **KAN-72 — Onboarding seed gate: `Mnemonic.generate` + BIP-39 wordlist access.** `:wallet`'s
  `Mnemonic` companion is now the single seam for ONB-5/ONB-6:
  - `generate(wordCount = 12|24)` — fresh phrase from CSPRNG entropy drawn in the crypto layer
    (`EvmCrypto.secureRandomBytes` via cryptography-kotlin `CryptographyRandom`, ADR-0008), never in
    the UI. 12 words = 128-bit, 24 = 256-bit; round-trips through `of`/`isValid`.
  - `wordlist` / `isWord(word)` / `suggestions(prefix, limit)` — expose the canonical 2048-word
    BIP-39 English list (from bitcoin-kmp) for per-word validation + autocomplete (ONB-5), so the UI
    never duplicates it. Case-insensitive/trimmed; suggestions are alphabetical, guarded on empty
    prefix / non-positive limit.
  - Property-tested on **JVM and iOS** (distinct each call, valid checksum, unsupported word counts
    rejected; wordlist size/bounds, `isWord`, prefix suggestions).
- **KAN-5 — ONB-1 Welcome screen.** Real `WelcomeScreen(onStart, onImport, state)` (replaces the
  placeholder): brand hero with the fixed green→blue `CryptasaBrandGradient` (new design-system
  token) + a full-width dark scrim band across the hero middle so the white hero text keeps WCAG AA
  (wordmark ≥3:1, tagline ≥4.5:1), over a `surface` bottom sheet
  (rounded top) with headline/body/CTA/import-link. All copy from `composeResources`
  (`onb_welcome_*`, `common_close`), all values from tokens; actions tagged `WELCOME_START`/
  `WELCOME_IMPORT`; nav start→ChoosePath, import→ImportSeed. Launch integrity check via
  `expect/actual verifyAppIntegrity()` (stub `true` until ADR-0009); on failure a blocking,
  non-dismissable `CryptasaDialog` (`WELCOME_START_ERROR_DIALOG`). Content capped to 480 dp (adaptive).
- **KAN-11 — ONB-2 Choose-path screen.** `PathScreen(onCreate, onImport, onBack, isOffline)`: back
  bar, title/subtitle, two navigation `SelectionCard`s (create/import, icon badge + chevron) and a
  reactive offline `CryptasaBanner` (creating stays enabled). Copy from `composeResources`
  (`onb_path_*`), tokens only, `onb_path_*` testTags, adaptive (≤480 dp, scroll). The chosen path is
  persisted in the Koin `OnboardingViewModel`; connectivity via
  `expect/actual observeConnectivity(): Flow<Boolean>` (stub online until platform monitors land).
  Design-system: `SelectionCard` upgraded to a `Role.Button` navigation card with icon badge + optional
  trailing icon; added `AddCircle`/`Download`/`ChevronRight` (RTL-mirrored) to `CryptasaIcons`.
  Also corrects the ONB-1 welcome import-link to route via the mandatory app password
  (`choosePath(Import)` + `SetPassword`) instead of skipping straight to seed entry (KAN-5 flow).
- **KAN-60 — Wallet-Core L3: RPC / networking layer (Ktor, Alchemy→Infura failover).** New `:rpc`
  module (Ktor client, ADR-0010; web-capable — engines per target OkHttp/Darwin/CIO/Js via
  `expect`/`actual`; depends on `:evm`, no secp256k1): `EvmRpcClient` with **rate-limit-aware**
  provider failover + visible `degraded` state (FR-4) — rolls over on transport errors, 429/5xx, and
  retryable JSON-RPC codes, while surfacing authoritative node errors (revert/invalid-params).
  `eth_getBalance`, ERC-20 `balanceOf` via `eth_call`, nonce, `eth_feeHistory`→EIP-1559 `FeeData`
  with an `eth_gasPrice`/`eth_maxPriorityFeePerGas` **fallback** when a provider lacks feeHistory,
  `eth_sendRawTransaction` (consumes L2's `rawTransactionHex`), and `eth_getTransactionReceipt` +
  `awaitReceipt` polling (pending→confirmed/failed). Per-provider retry with exponential backoff +
  jitter (respects `Retry-After`); JSON via kotlinx.serialization. RPC keys come from build-config
  and are **never committed** (PRD-02 §9). Verified with Ktor `MockEngine` tests (reads, broadcast,
  fee data + gasPrice fallback, receipt polling, transport & rate-limit failover/degraded,
  all-providers-failed, node error); official endpoint vectors/fakes are KAN-61.
- **KAN-58 — Wallet-Core L2: EVM transaction stack (EIP-1559 signer).** Self-built, deterministic,
  audit-isolated stack stacked on L1 (ADR-0014):
  - `tx.Eip1559Transaction` (Type-2) + `tx.EvmTransactionSigner` (`:wallet`) — builds
    `0x02 || rlp(fields)` from `:evm`'s `Rlp`/`Quantity`, hashes with keccak-256, signs via L1
    (`EvmKeyManager`, private key never enters L2), sets the typed-tx `v` = signature y-parity
    (`recId`); chainId carried in-payload (ETH 1 / Base 8453). Emits `SignedTransaction`
    (broadcast-ready `rawTransaction`/hex + tx hash); `recoverSigner` self-verifies.
  - Uses `:evm`'s `Rlp` codec and `Erc20Abi` (`transfer`/`balanceOf`/`decimals`/`symbol`; selectors
    `a9059cbb`/`70a08231`/`313ce567`/`95d89b41`). `EvmCrypto` SPI gained `recoverPublicKey`.
  - Verified on **JVM and iOS**: RLP/ABI known-answers and signed txs that cryptographically recover
    to the signer; official signed-tx vectors are KAN-59.
- **KAN-56 — Wallet-Core L1: Key/Account layer (BIP-44 HD + EIP-55).** Two modules (ADR-0016):
  - **`:evm`** — web-safe EVM primitives (targets android · iosArm64 · iosSimulatorArm64 · jvm ·
    **js · wasmJs**): `EvmAddress` (always EIP-55 checksummed), `Quantity` (pure-Kotlin
    minimal-big-endian 256-bit), `Hex`, `Keccak` (keccak-256 via KotlinCrypto sha3 — multiplatform
    incl. web, no SPI), `Rlp` codec, `Erc20Abi`, sealed `EvmException`. No secp256k1 → fully
    web-capable, which is what makes Web read-only viable (FR-6).
  - **`:wallet`** (depends on `:evm`; android · iosArm64 · iosSimulatorArm64 · jvm — **no js/wasm**,
    since secp256k1-kmp has no web binding; ADR-0008): L1 API in `commonMain` — `EvmKeyManager`
    (BIP-44 `m/44'/60'/0'/0/i`, multi-account via index `i`; `deriveAccount`, `deriveAddress`,
    `sign`), `Mnemonic` (BIP-39 checksum validated at construction), `EvmAccount`,
    `RecoverableSignature` (`r`/`s` low-S + `recId` for L2's `v`), `SeedSource` (unlock→sign gate,
    ADR-0009), sealed `WalletKeyException`. Keys are derived **on demand** inside the seed scope,
    used, then zeroized — never returned, stringified, persisted, or logged. The secp256k1 stack
    (ACINQ bitcoin-kmp `0.31.0` / secp256k1-kmp `0.23.0`) is isolated behind the internal `EvmCrypto`
    expect/actual SPI, keeping `commonMain` dependency-free and compilable for every target.
  - Verified against the Hardhat default-mnemonic addresses and the canonical EIP-55 vectors on
    **JVM and iOS** (`commonTest`); the authoritative vector suite is KAN-57.
- **KAN-4 — Onboarding foundations / design system.**
  - New `:designsystem` module: central `CryptasaTheme` with light/dark semantic colour, spacing,
    radius and typography tokens (CompositionLocals; System/Light/Dark mode); foundation components
    `CryptasaButton`, `CryptasaTextField` (incl. inline error), `CryptasaBanner`, `CryptasaDialog`;
    reusable patterns `SelectionCard`, `CryptasaCheckbox`, `SegmentedControl`, `SeedWordCell`,
    `PasswordStrengthIndicator`, `ProgressRing`, `CryptasaTopAppBar`. All token-bound, RTL-ready (`start`/`end`), a11y
    touch targets ≥ 48 dp, errors conveyed via icon + text (ADR-0004).
  - New `:feature:onboarding` module: `OnboardingRoot()` with Compose Navigation 3 back stack
    (ADR-0006), adaptive `OnboardingScaffold(sizeClass)` driven by Material 3 Window Size Classes
    (Compact / Compact-landscape / Medium / Expanded, max-width 480; ADR-0012), `OnboardingViewModel`,
    and the feature's Koin module (ADR-0007). Welcome/route placeholders carry `testTag`s
    (`onb_*`) ready for Maestro (KAN-10); real screens follow in KAN-5+.
  - Dependency injection via Koin: app aggregates feature modules in `App()` (`KoinApplication`).
  - `README.md` rewritten as the source of truth for running **and** testing every target
    (incl. JAVA_HOME, module overview, test tooling matrix); this `CHANGELOG.md` added.
- **KAN-10 — testTag bootstrap.** Central `OnboardingTestTags` catalog enumerating the full
  `onb_<screen>_<element>` selector contract (`../Tests/.maestro/README.md`) as the single source of
  truth for the test agent; welcome placeholder wired to `WELCOME_START`/`WELCOME_IMPORT` (remaining
  IDs applied by their screen tickets, KAN-5+). Robolectric Compose test verifies the root sets
  `testTagsAsResourceId=true` (Android resource-id) and the welcome tags are selectable; iOS exposes
  `testTag` as `accessibilityIdentifier` automatically (no bridge needed).
- **KAN-35 — i18n + RTL bootstrap.** Wired Compose `composeResources` in `:feature:onboarding` and
  imported all **14 locales** from `../Cryptasa/i18n` (en base + de · fr · pl · sv · da · no · es ·
  pt-rBR · ru · tr · vi · zh-rCN + **ar** RTL; untranslated keys fall through to the base). Strings
  are exposed via the generated `Res` (`com.tneff.cyppie.feature.onboarding.generated.resources`,
  key scheme `onb_*`/`common_*`/`cd_*`, positional placeholders). The welcome sample now renders
  entirely from string resources (no raw text, §5.5). Robolectric tests verify the Arabic CTA
  resolves under locale `ar` and the adaptive scaffold mirrors its brand column under
  `LayoutDirection.Rtl`. Device-level RTL Maestro smoke stays with the test agent.

### Changed
- `app:shared` `App()` now renders the wallet onboarding flow (`OnboardingRoot()`) instead of
  `AuthRoot()`; Android manifest set to `resizeableActivity="true"` with no orientation lock (ADR-0012).
- Centralised the `CryptasaTheme` out of `:feature:auth` into `:designsystem` (ADR-0004). The KAN-1
  example login/registration screens remain (deferred to PRD-08) but keep only a clearly-marked
  legacy colour palette and no longer define a competing theme.

### Fixed
- **KAN-94 — Welcome adaptive (landscape / Medium / Expanded).** `WelcomeScreen` now branches on the
  window size class: tall portrait keeps the brand hero / bottom-sheet design, while **short height
  (landscape) or Medium/Expanded width** switch to a single centred, vertically-scrolling, ≤480-dp
  column — so the body no longer overlaps the "get started" CTA on short windows and content stays
  clamped/centred on wide ones. Hero/text/actions extracted to shared composables; both layouts scroll
  the body. No copy/token changes.
- **KAN-85 / KAN-84 — Onboarding QA + security fixes (pre-merge, uniform on `develop`).**
  - **FLAG_SECURE (KAN-84 + A2):** ref-counted `SecureScreenEffect` survives secure→secure nav
    (no dispose-order race); applied uniformly to the password **and** seed screens (ONB-3/4/5/6/7).
  - **A1 top-inset:** `CryptasaTopAppBar` now insets for the status bar (edge-to-edge) so the back
    button/title no longer overlap the status bar (ONB-2..7).
  - **A3 system-back:** `android:enableOnBackInvokedCallback=true` so the Nav3 back handler fires on
    Android 13+ (system back steps back through the flow instead of exiting / losing input).
  - **A4 apostrophes:** unescaped `\'`→`'` across all 14 onboarding locales (compose-resources parses
    XML directly; the backslash rendered literally).
  - **A5 polish:** password strength bar is now 4 segments (Weak 1 · Medium 2 · Strong 4); visible
    back-circle affordance on the top bar.
  - **Welcome (landscape):** hero height capped so the (already scrollable, ≤480 dp) bottom sheet keeps
    room for its actions on short windows.
  - Verified: ONB-8 setup has no back/cancel (by design); `de` carries the `onb_backup_*` strings
    (`word_label` falls back to en, tracked KAN-76); `:designsystem:jvmTest` (KAN-64 WCAG baseline) green.
- **KAN-64 / KAN-66 — Status-token WCAG-AA contrast.** Updated the sub-AA status colours in
  `:designsystem` `Color.kt` to the KAN-64 design delivery (HANDOFF §2.4): Light `success`
  `#12B82C`→`#0C7322` (white/success 6.0:1, success/successSurface 5.5:1) and `warning`
  `#FFBD00`→`#A87600` (icon-tint warning/warningSurface 3.7:1 ≥3:1, black/warning 5.3:1); Dark
  `onDanger` `#FFFFFF`→`#2A1416` (onDanger/danger 5.7:1). Only the three failing values changed;
  conforming pairs untouched. The `WcagContrastTest` baseline (`knownSubAaStatusPairs`) was narrowed
  to the single remaining icon-tint pair `Light:warning/warningSurface` (3.7:1, 3:1 bar) so
  `:designsystem:jvmTest` stays green; value confirmed by the test agent (KAN-67).
