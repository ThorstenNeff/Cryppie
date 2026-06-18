# ADR-0016 — Wallet-/EVM-Modulstruktur (`:evm` / `:wallet` / `:rpc`, Web-read-only)

- **Status:** Accepted (2026-06-18) — Wallet-Core-Slice (PRD-02). Initial in KAN-56 (`:wallet`); zur 3-Modul-Struktur verfeinert in KAN-60 (RPC), damit **Web read-only** trägt.
- **Kontext:** `secp256k1-kmp` hat **kein js/wasm**; `:core` zielt u. a. auf js/wasm. PRD-02 verlangt **Web read-only** (Adressen/Balances/NFT anzeigen, FR-6) — der **RPC-Layer muss also web-fähig** sein. Schlüssel: die **web-sicheren EVM-Primitive** (Adress/EIP-55, RLP, ABI, keccak, Hex, Quantity) sind **nicht** secp256k1-abhängig; nur **Key-Ableitung + ECDSA-Signatur** sind non-web.
- **Entscheidung — drei Module:**
  - **`:evm`** — web-sichere EVM-Primitive: `Hex`, `Quantity`, `EvmAddress` (EIP-55), `Rlp`, `Erc20Abi`, **keccak (KotlinCrypto `sha3`, multiplatform inkl. js/wasm)**. Targets: **alle inkl. js/wasm**; dependency-arm. → kein SPI für keccak nötig.
  - **`:wallet`** — secp256k1-Keys/HD-Ableitung/Signatur (L1) + EIP-1559-Tx-Signatur (L2). Targets **android, iosArm64, iosSimulatorArm64, jvm** (**kein** js/wasm). `depends on :evm`; secp256k1 hinter interner **`EvmCrypto`-`expect`/`actual`-SPI** ([[0003]], [[0014]]).
  - **`:rpc`** — Ktor JSON-RPC + Provider-Failover (Alchemy→Infura), Reads/Broadcast/Receipt (L3, [[0010]]). **Web-fähig** (inkl. js/wasm). `depends on :evm`.
  - `:core` bleibt unverändert (plattformagnostisch, keine Krypto).
- **Konsequenzen:** **Web read-only inkl. ERC-20-Balances/NFT** möglich (über `:evm` + `:rpc`) — **ohne** secp256k1. Signing/Key-Ops nur non-web (`:wallet`). Öffentliche API rein in Kotlin-Typen; Private Keys nie exponiert (zeroized, [[0005]]/§5.3). `settings.gradle.kts` führt `:evm`/`:wallet`/`:rpc` (geteilte Datei — Merge-Disziplin). WalletConnect ([[0015]]) baut auf `:wallet`/`:evm`.
- **Alternativen (verworfen):** RPC in `:wallet` (bräche Web read-only / FR-6); `:rpc` mit eigenem keccak/ABI (Duplikat); `expect`/`actual` in `:core` mit werfenden js/wasm-Stubs (verwässert API, Risiko dass Web-Code Key-Ops aufruft).
