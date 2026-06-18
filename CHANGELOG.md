# Changelog

All notable changes to Cyppie are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project tracks work per Jira ticket
(project `KAN`). Entries are summarised changes (what/why + ticket key), not raw commit logs.

## [Unreleased]

### Added
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
- **KAN-64 / KAN-66 — Status-token WCAG-AA contrast.** Updated the sub-AA status colours in
  `:designsystem` `Color.kt` to the KAN-64 design delivery (HANDOFF §2.4): Light `success`
  `#12B82C`→`#0C7322` (white/success 6.0:1, success/successSurface 5.5:1) and `warning`
  `#FFBD00`→`#A87600` (icon-tint warning/warningSurface 3.7:1 ≥3:1, black/warning 5.3:1); Dark
  `onDanger` `#FFFFFF`→`#2A1416` (onDanger/danger 5.7:1). Only the three failing values changed;
  conforming pairs untouched. The `WcagContrastTest` baseline (`knownSubAaStatusPairs`) was narrowed
  to the single remaining icon-tint pair `Light:warning/warningSurface` (3.7:1, 3:1 bar) so
  `:designsystem:jvmTest` stays green; value confirmed by the test agent (KAN-67).
