# ADR-0005 — Non-custodial Sicherheitsmodell

- **Status:** Accepted (2026-06-17)
- **Kontext:** Wallet ist non-custodial; Schlüsselhoheit liegt beim Nutzer. Sicherheit hat höchste Priorität.
- **Entscheidung:** Private Keys/Seeds **verlassen niemals das Gerät** und gehen **nie** ans Backend. Sichere Storage/KDF/Biometrie/CSPRNG über `expect`/`actual` (Android Keystore · iOS Keychain/Secure Enclave · Desktop OS-Keystore). Sensible Screens mit Screenshot-Schutz/`FLAG_SECURE`; kein Klartext-Logging; Secrets als `CharArray`/`ByteArray` mit Zeroize; kein Clipboard-Write-back.
- **Konsequenzen:** Account-Auth am Backend (Keycloak, PRD-08) ist **getrennt** vom Wallet-Schutz (lokales Passwort/Biometrie, PRD-01). Konkrete Storage-/Krypto-Libs: [[0008]], [[0009]].
- **Alternativen:** Custodial/Server-seitige Schlüssel (verworfen: widerspricht Produktversprechen).
