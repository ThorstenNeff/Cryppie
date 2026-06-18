# ADR-0014 — EVM-Transaktions-Stack (RLP / EIP-1559 / ABI, Eigenbau)

- **Status:** Accepted (2026-06-17) — gilt für den Wallet-Core-Slice (PRD-02). Nicht relevant für Onboarding.
- **Kontext:** Send (native + ERC-20) auf **Ethereum + Base** braucht Transaktions-Bau + -Signatur. Context7-Recherche (2026-06-17): **keine reife KMP-EVM-Bibliothek** (web3j, KEthereum = JVM-only → kein iOS). Verfügbar sind die Krypto-Primitive aus [[0008]]: `secp256k1-kmp`/`bitcoin-kmp` (ECDSA, BIP-32) + `keccak`.
- **Entscheidung:** Den EVM-Transaktions-Stack **selbst in `commonMain`** implementieren — dünn, deterministisch, testbar:
  - **RLP:** eigener Encoder/Decoder.
  - **Transaktionstyp:** **EIP-1559 (Type-2)** primär (ETH+Base unterstützen beides), Legacy (Type-0) nur falls nötig; **EIP-155** chainId-Schutz.
  - **ABI:** **minimaler** Encoder nur für die gebrauchten ERC-20-Funktionen (`transfer`, `balanceOf`, `decimals`, `symbol`) — **kein** genereller ABI-Codec.
  - **Signatur:** ECDSA über `secp256k1-kmp`/`bitcoin-kmp`; die EVM-**Recovery-ID `v`** per Trial-Recovery gegen den bekannten Public Key bestimmen. **Keccak-256** für Hashing; **EIP-55**-Checksum für Adressen.
  - **Absicherung (Pflicht):** offizielle **Test-Vektoren** (EIP-1559-Encoding/-Signatur, ERC-20-`transfer`, EIP-55) als `commonTest` ([[0011]]); der Stack ist Review-/Audit-Gate.
- **Plattformen:** Android/iOS/Desktop (`secp256k1-kmp` hat kein JS/Wasm → Web read-only, [[0008]], PRD-02 §2).
- **Konsequenzen:** Volle Kontrolle, keine JVM-only-Abhängigkeit, on-device/non-custodial ([[0005]]). Neue EVM-Chains = nur chainId/Config. Preis: sicherheitsnaher Eigenbau → strenge Vektor-Tests + Code-Review verpflichtend; isoliert hinter einer schmalen API im Wallet-Layer.
- **Alternativen (verworfen):** web3j / KEthereum (JVM-only → kein iOS); Tx-Bau serverseitig (widerspricht on-device/non-custodial, [[0005]]).
