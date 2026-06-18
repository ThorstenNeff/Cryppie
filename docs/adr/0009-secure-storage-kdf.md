# ADR-0009 — Sichere Storage + KDF

- **Status:** Accepted (2026-06-17) — **Gate für ONB-8 (KAN-17) erfüllt.**
- **Kontext:** Verschlüsselte Wallet/Seed + App-Passwort müssen auf jedem Target sicher abgelegt werden; Komfort-Unlock via Biometrie (ONB-9). Verifikation via Context7: **Argon2-KMP** ist nicht verlässlich verfügbar (kein indiziertes Binding; libsodium-kmp unbestätigt + native Abhängigkeit), **PBKDF2** ist solide (`cryptography-kotlin`); Wrapper-Libs (KVault nicht gelistet; `multiplatform-settings` = nur Key-Value, Android unverschlüsselt) decken **kein** Hardware-Key-/Biometrie-Layer ab.
- **Entscheidung:**
  - **Schutzmodell:** Seed mit **passwort-abgeleitetem Schlüssel** (AES-GCM) verschlüsseln → Ciphertext + Salt + Nonce in App-Storage. **Passwort = kryptografisches Gate** (Seed ohne Passwort wertlos, auch bei Geräte-Dump). **Hardware-Keystore + Biometrie = Komfort-Unlock**, gibt Passwort/Schlüssel frei (verbindet ONB-9).
  - **KDF:** **PBKDF2-HMAC-SHA512**, hohe, an OWASP orientierte Iterationszahl (Richtwert ~210k für SHA-512, am Gerät kalibrieren), zufälliger 16-Byte-Salt — via `cryptography-kotlin`. **Argon2id = dokumentierter Upgrade-Pfad**, sobald ein KMP-Binding (z. B. ionspin libsodium-kmp) über alle Targets validiert ist.
  - **Verschlüsselung:** AES-GCM (`cryptography-kotlin`, [[0008]]).
  - **Storage:** **nativ via `expect`/`actual`** — Android: **Keystore-gebundener AES-Key** (+ AES-GCM), **kein** Jetpack `EncryptedFile` (deprecated); iOS: **Keychain/Secure Enclave**; Desktop: OS-Keychain (macOS Keychain · Windows Credential Manager/DPAPI · Linux Secret Service) bzw. passwort-primär. Optional `multiplatform-settings` (1.3.0) nur zum Ablegen von Ciphertext/Metadaten.
  - **Biometrie-Unlock:** biometrie-gebundener Eintrag (Android Keystore `setUserAuthenticationRequired` · iOS `LAContext` + Keychain Access Control) gibt Schlüssel/Passwort frei.
  - **Versions-Header:** Ciphertext trägt eine **KDF-/Format-Versionskennung** → spätere Migration **PBKDF2 → Argon2id** ohne Daten-Reset.
- **Scope:** Android + iOS zuerst; **Desktop** passwort-primär (Hardware-Backing uneinheitlich); **Web/Wasm: kein Wallet-at-rest im MVP** (konsistent [[0008]]).
- **Konsequenzen:**
  - Kein zusätzlicher Krypto-Stack (PBKDF2 + AES-GCM aus `cryptography-kotlin`); Hardware-/Biometrie-Layer = native `expect`/`actual` (Wrapper decken es nicht ab).
  - ⚠️ Jetpack Security (`EncryptedFile`/`EncryptedSharedPreferences`) ist **deprecated** → nicht verwenden.
  - Hängt an [[0005]] (Schlüssel on-device) und [[0008]] (AES-GCM).
- **Alternativen (nicht gewählt):** Argon2id ab Start (KMP-Verfügbarkeit/Risiko); KVault (Context7 nicht gelistet); `multiplatform-settings` als alleinige Sicherheitslösung (kein Hardware-Key/Biometrie).
