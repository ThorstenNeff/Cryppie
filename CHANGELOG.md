# Changelog

All notable changes to Cyppie are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project tracks work per Jira ticket
(project `KAN`). Entries are summarised changes (what/why + ticket key), not raw commit logs.

## [Unreleased]

### Added
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
