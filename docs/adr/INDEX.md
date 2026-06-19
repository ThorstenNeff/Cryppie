# Architecture Decision Records — Cyppie (App)

App-Architektur-Entscheidungen. Format kurz: Status · Kontext · Entscheidung · Konsequenzen · Alternativen.
Stehende *Regeln* stehen in `../../SPEC_Dev_Agent.md`; hier liegen die *Entscheidungen* mit Begründung.

| ADR | Titel | Status |
| --- | ----- | ------ |
| [0001](0001-kmp-compose-stack.md) | App-Stack: Kotlin Multiplatform + Compose Multiplatform | Accepted |
| [0002](0002-clean-mvvm-repository.md) | Schichtung: Clean Architecture + MVVM + Repository | Accepted |
| [0003](0003-feature-module-pattern.md) | Modulstruktur & Plattformvarianz (Feature-Module, expect/actual) | Accepted |
| [0004](0004-central-design-system.md) | Ein zentrales Design-System/Theme | Accepted |
| [0005](0005-non-custodial-security.md) | Non-custodial Sicherheitsmodell | Accepted |
| [0006](0006-navigation-nav3.md) | Navigation: Compose Navigation 3 (Nav3) | Accepted |
| [0007](0007-di-koin.md) | Dependency Injection: Koin | Accepted |
| [0008](0008-bip39-crypto.md) | BIP-39 / Krypto-Bausteine | Accepted |
| [0009](0009-secure-storage-kdf.md) | Sichere Storage + KDF (PBKDF2 + nativ Keystore/Keychain + AES-GCM) | Accepted |
| [0010](0010-serialization-networking.md) | Serialisierung & Networking (kotlinx.serialization + Ktor-Client) | Accepted (Feindetails ab PRD-03/08) |
| [0011](0011-test-strategy.md) | Test-Strategie & -Stack (MockK, Turbine, Maestro, Compose UI) | Accepted |
| [0012](0012-adaptive-layouts-breakpoints.md) | Adaptive Layouts & Breakpoints (Window Size Classes) | Accepted (Android; iOS später) |
| [0013](0013-robolectric-config-change-tests.md) | Android Config-Change- & Compose-UI-Tests (Robolectric, JVM) | Accepted (Android) |
| [0014](0014-evm-transaction-stack.md) | EVM-Transaktions-Stack (RLP/EIP-1559/ABI, Eigenbau) | Accepted (PRD-02) |
| [0015](0015-walletconnect-integration.md) | WalletConnect-Integration (nativ pro Plattform via expect/actual) | Accepted (PRD-02) |
| [0016](0016-wallet-module-structure.md) | Wallet-/EVM-Modulstruktur (`:evm`/`:wallet`/`:rpc`, Web-read-only) | Accepted (PRD-02) |
| [0017](0017-portfolio-data-sourcing.md) | Portfolio-Datenbeschaffung & Bewertung (`:portfolio`, on-device) | Accepted (PRD-03) |
| [0018](0018-supply-chain-jitpack-dependency-verification.md) | Supply-Chain-Policy (JitPack für WC-Android + Dependency-Verification) | Accepted (KAN-62) |
| [0019](0019-send-orchestrator-module.md) | `:send`-Modul: ein Send-Orchestrator (In-App + WalletConnect) | Accepted (KAN-91) |
| [0020](0020-charting-integration.md) | Charting-Integration: Compose-Canvas-Candlesticks (GA) + TradingView-Advanced-Rich (Fast-Follow) über den MarketChart-Seam | Accepted (PRD-04) |
| [0021](0021-server-side-api-key-proxy.md) | Server-seitiger API-Key-Proxy (`:server`) für Alchemy/RPC | Accepted (KAN-112) |
| [0022](0022-proxy-deployment-hosting.md) | Proxy-Deployment: gehosteter Key-Proxy (Mac Mini @ Oakhost, TLS) | Accepted (KAN-125) |
| [0023](0023-minimal-backend-prd08-v1.md) | Minimal-Backend (PRD-08) v1: API-Gateway + Keycloak + User-Service + Postgres (Mac Mini, Docker-Compose) | Accepted (PRD-08) |
| [0024](0024-unattended-automation-aa-session-keys.md) | Unbeaufsichtigte Automatisierung: ERC-4337 AA + scoped Session-Keys (Kernel+SmartSessions+7702+Pimlico, per-Feature-Custody) | Accepted (PRD-05) |
| [0025](0025-money-pricing-foundation-in-market.md) | Money/Pricing-Foundation in `:market` (zyklenfrei; reconcile w/ Dev-2-Modellen) | Accepted (PRD-04) |
| [0026](0026-siwe-keycloak-integration.md) | SIWE-Keycloak-Integration: Custom Authenticator-SPI + `siwe-java` | Accepted (PRD-08) |

**ADRs 0001–0022 accepted** (0020 = Charting/PRD-04, ratifiziert). (Stand 2026-06-19.) 0001–0013 tragen den Onboarding-Slice; **0014/0015/0016** den Wallet-Core-Slice (PRD-02); **0017** den Portfolio-Slice (PRD-03, on-device); **0018** die WC-Android-Supply-Chain (ratifiziert); **0019** den Send-Slice (`:send`, PRD-02); **0021** den server-seitigen API-Key-Proxy (`:server`, R2/KAN-104); **0022** dessen Prod-Deployment (Mac Mini @ Oakhost, TLS). 0010 trägt ein accepted Portfolio-Update (REST Data/Prices); Auth-Refresh-Feindetails bleiben offen für PRD-08. **0020** trägt die Charting-Integration (PRD-04): Compose-Canvas-Candlesticks für GA + TradingView-Advanced als Rich-Fast-Follow über den `MarketChart`-Seam. **PRD-08 (Platform-Backend):** **0023** (Minimal-Backend v1, Laufzeit Docker-Compose) + **0026** (SIWE-Keycloak via Custom-Authenticator-SPI + `siwe-java`) accepted; **0024** (ERC-4337 AA / Session-Keys für 05–07) **accepted** (PRD-05-Kickoff: Kernel + Rhinestone Smart Sessions + EIP-7702-same-address + Pimlico; per-Feature-Custody [DCA on-device, Copy/Vaults backend-scoped]; ETH+Base; Epic KAN-138).
