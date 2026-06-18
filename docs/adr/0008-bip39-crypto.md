# ADR-0008 — BIP-39 / Krypto-Bausteine

- **Status:** Accepted (2026-06-17) — gilt ab KAN-14 / KAN-15 (ONB-5 Import / ONB-6 Anzeige).
- **Kontext:** Wallet braucht BIP-39-Mnemonic (Wortliste, Prüfsumme, 12/24), HD-Key-Ableitung (BIP-32/SLIP-0010), Signatur-Kurven und sichere Zufallszahlen — über mehrere Chains (EVM + Solana). Grundsatz aus [[0005]]: kein Eigenbau von Krypto-Kernen.
- **Entscheidung — Stack:**
  - **`cryptography-kotlin`** (whyoleg) als Krypto-Fundament: Hashing (SHA-256/512, RIPEMD160), HMAC, **PBKDF2**, **AES-GCM**, HKDF, **EdDSA (ed25519)**, sichere RNG. Native Provider je Target (JDK · Apple/iOS · WebCrypto/JS · OpenSSL). Deckt die BIP-39-Primitive, Solana-Signing **und** die At-Rest-Verschlüsselung ([[0009]]) ab.
  - **ACINQ `bitcoin-kmp` + `secp256k1-kmp`** für BIP-39-Mnemonic + BIP-32-HD + **secp256k1** (EVM-Chains).
  - **keccak-256** ergänzen (z. B. KotlinCrypto/hash) für Ethereum-Adressen — `cryptography-kotlin` bietet SHA3, aber nicht keccak.
  - Nur die **dünne BIP-39-Verdrahtung** (Wortliste/Prüfsumme/Seed) ist eigener Code; alle Primitive kommen aus den Libs.
- **Konsequenzen:**
  - **Zwei Kurven-Familien:** secp256k1 (EVM) via `secp256k1-kmp`; ed25519 (Solana, SLIP-0010) via `cryptography-kotlin`. Dünne `KeyDerivation`-Abstraktion in `commonMain` darüber.
  - ⚠️ **Web/Wasm:** `secp256k1-kmp` ist eine native Lib **ohne JS/Wasm**. Folge: **EVM-Wallet-Key-Ops laufen im MVP nicht auf Web/Wasm.** Stützt „EVM zuerst, Solana später" (PRD-02). Web bleibt für Nicht-Key-Funktionen/Navigation voll dabei ([[0006]]). Endgültige Web-Scope-Entscheidung für Wallet-Keys offen (PRD-02).
  - CSPRNG/`CryptographyRandom` statt eigener RNG; Wortliste(n) als Ressource bündeln.
  - **Vor dem Festschreiben in Code:** aktuelle Versionen + Target-Matrix (iOS-arm64, JS/Wasm) von `bitcoin-kmp`/`secp256k1-kmp` final gegenprüfen (in Context7 nicht indexiert).
- **Alternativen (nicht gewählt):** reine Plattform-Krypto via `expect`/`actual` (mehr Glue-Code); Eigenimplementierung BIP-39/HD (abgeraten — Audit-/Sicherheitsrisiko).
