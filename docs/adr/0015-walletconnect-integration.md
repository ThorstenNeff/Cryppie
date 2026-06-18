# ADR-0015 — WalletConnect-Integration (nativ pro Plattform via expect/actual)

- **Status:** Accepted (2026-06-17) — gilt für den Wallet-Core-Slice (PRD-02).
- **Kontext:** WalletConnect v2 ist **nicht Kotlin-Multiplatform**: Android = Reown **WalletKit** (Kotlin, benötigt Android-`Application`-Context), iOS = **reown-swift** (Swift Package), Desktop/Web = **kein** offizielles SDK. Die Signatur-Ausführung selbst läuft über den EVM-Stack [[0014]].
- **Entscheidung:**
  - Gemeinsame Abstraktion **`WalletConnectController` als `expect`/`actual`** ([[0003]]) in einem eigenen Feature-Modul `:feature:walletconnect`; Events (SessionProposal, SessionRequest, Sessions, ConnectionState) werden als Flow/Callbacks nach `commonMain` gebracht.
  - **Android (`androidMain`):** Reown WalletKit; Init in der `androidApp`-`Application`-Klasse (Context-Pflicht); Delegate → common.
  - **iOS (`iosMain`):** reown-swift, angebunden über einen **Swift-Shim**, aufgerufen aus Kotlin/Native.
  - **Desktop/Web (`jvmMain`/`jsMain`/`wasmJsMain`):** **No-op-`actual`** (WC nicht verfügbar); die UI gated diese Aktionen plattformbedingt aus (passt zu Web-read-only, PRD-02 §2).
  - **Unterstützte Methoden (MVP):** `eth_sendTransaction`, `personal_sign`, **`eth_signTypedData_v4`** — jeweils mit **voller Payload-Offenlegung** ([[0005]], PRD-02 FR-3).
  - **WalletConnect-Cloud Project-ID** = Config/Secret, **nicht committed** ([[0005]]).
  - Genaue Artefakt-Koordinaten (`com.reown:walletkit` vs. `com.walletconnect:web3wallet`; reown-swift Package) beim Implementieren pinnen (laufender Reown-Rebrand).
- **Konsequenzen:** WC auf Android+iOS mit nativer Reife; klar isoliertes Modul; Signaturen laufen über denselben EVM-Stack [[0014]]; Desktop/Web bewusst ohne WC. Preis: Wartung zweier nativer SDKs pro Plattform.
- **Alternativen (verworfen):** WC-v2-Protokoll selbst über den Relay implementieren (zu groß/riskant); WC nur auf Android (verschiebt iOS-Parität ohne Not).
