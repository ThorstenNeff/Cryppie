# ADR-0024 — Unbeaufsichtigte Automatisierung: ERC-4337 Account-Abstraction + scoped Session-Keys

- **Status:** **Accepted** (2026-06-19, PRD-05-Kickoff — alle Entscheidungen gepinnt, s. „Finalisierung" unten; Epic KAN-138) — Foundation für **PRD-05 (DCA-Bot) / PRD-06 (Copy-Trading) / PRD-07 (Vaults)**; **GA-verpflichtend** (User-Entscheid F2 = a). Baut auf [[0005]] (Non-custodial), [[0023]] (Backend/Auth≠Custody), [[0026]] (SIWE-Identität = SCA-Adresse), [[0014]] (EVM-Tx).
- **Kontext:** Die Super-App-Features 05–07 brauchen **unbeaufsichtigtes Handeln** (DCA-Käufe nach Zeitplan, Copy-Trades in Echtzeit, Vault-Rebalancing) — **auch wenn die App geschlossen ist / das Gerät offline ist**. Das kollidiert scheinbar mit der Non-custodial-Invariante ([[0005]]): der Haupt-Seed/Key liegt **on-device** und darf das Gerät **nie** verlassen → das Backend kann nicht „einfach signieren". Lösung: **delegierte, eng begrenzte, widerrufbare Signier-Befugnis** via **ERC-4337 Account-Abstraction (AA)** + **Session-Keys**, deren Grenzen **on-chain** erzwungen werden — der Haupt-Key bleibt on-device.
- **Entscheidung:** Automatisierung läuft über einen **ERC-4337 Smart-Account (SCA)** mit einem **Session-Key-Validation-Modul**, das eine vom User **on-device autorisierte, gescopte** Befugnis on-chain durchsetzt.
  - **(A) On-device autorisierte scoped Session-Keys.** Der User signiert **einmal** mit dem **Haupt-Key on-device** eine begrenzte Erlaubnis: **erlaubte Asset(s)/Ziel-Contracts + Selektoren**, **Betrags-Cap (pro Op + kumulativ)**, **Frequenz/Rate**, **Laufzeit/Expiry**. Diese Grant-Signatur installiert den Session-Key ins Modul. **Der Haupt-Seed/Key verlässt das Gerät nie; das Backend bekommt ihn NIE** ([[0005]]/[[0023]], Auth≠Custody).
  - **(B) Smart-Account-Seite (Contract-Track, externes Audit — harter GA-Gate).** ERC-4337-SCA + Session-Key-Validation-Modul (modular, Standard prüfen — ERC-7579/-6900). Das Modul **erzwingt die Limits on-chain**: out-of-policy UserOps **reverten** (fail-closed). Beide Contracts brauchen ein **externes Sicherheits-Audit**, bevor sie echtes Geld halten.
  - **(C) Trigger-Pfad.** **Backend-Signal → Session-Key-signierte `UserOperation`** (innerhalb der Limits) → Bundler → EntryPoint → SCA-Modul prüft Policy → führt aus. **Nie** der Haupt-Key; **nie** ein backend-gehaltener Haupt-Key. **Revoke** (User kann den Session-Key jederzeit on-chain killen) + **Expiry** (Auto-Ablauf) + **Limit-Überschreitung = revert/fail-closed**.
  - **(D) Gas/Bundler/Paymaster** (offen, s. u.): UserOps gehen über einen **Bundler**; Gas zahlt entweder der **SCA** (braucht ETH-Guthaben) oder ein **Paymaster** (gesponsert). Wer zahlt + welcher Bundler = Entscheidung.
  - **🔑 Non-custodial-Nuance (ehrlich, verbindlich):** Dies ist **non-custodial bzgl. Haupt-Seed/Key** (der bleibt on-device). Der Session-Key ist eine **delegierte Capability** — der Backend/Bot hält (im unbeaufsichtigten Modell) einen **gescopten, widerrufbaren, on-chain-limitierten** Schlüssel, **nicht** den Haupt-Key und **keine** Custody der Funds. **Worst-Case bei Session-Key-Kompromittierung = die gecappte/ablaufende Befugnis** (z. B. „max X USDC bis T an Ziel Y"), **kein Drain**. Das ist die Sicherheits-Eigenschaft, die das Modul garantieren **muss**.
  - **(E) Interim-Fallback (falls AA-Reife/Audit zu spät für GA):** v1-**DCA** = „**Backend-Signal → on-device-Sign**" (die App signiert die DCA-Tx, wenn sie läuft — voll non-custodial, kein Session-Key, aber **nur wenn das Gerät verfügbar ist**). **GA-Ziel bleibt echte Session-Keys** (für Copy/Vaults, die Echtzeit/24-7 brauchen, ist on-device-only unzureichend).
- **Konsequenzen:**
  - **Cross-cutting:** berührt **Wallet (on-device Grant-Sig + Key-Pfad), Backend (Signal/Trigger + Session-Key-Storage), Contracts (SCA+Modul+Audit)** — drei Tracks, koordiniert. **GA-Timeline ist an den Contract-Audit gekoppelt** (der Long-Pole).
  - **Blast-Radius minimiert:** kompromittierter Session-Key/Backend ⇒ nur gecappte/ablaufende Aktionen, kein Seed-Leak, kein Drain. Revoke ist die Notbremse.
  - **Backend-Session-Key-Storage** (im unbeaufsichtigten Modell) muss geschützt sein (HSM/KMS) — auch eine gecappte Befugnis verdient Schutz.
  - **EOA→SCA-Migration** nötig (s. offene Frage) — bestehende Seed-EOAs müssen SCA-fähig werden.
- **Alternativen (verworfen / aufgeschoben):** **Backend hält den Haupt-Key / server-side Signing** — **verworfen** (bricht [[0005]], unbeschränkte Custody). **Nur on-device-Sign-auf-Signal als Dauerlösung** — verworfen als GA-Ziel (kein 24/7/Echtzeit für Copy/Vaults; als **Interim/DCA** akzeptiert, (E)). **Custodial-„managed accounts"** — verworfen (Produktidentität non-custodial). **Eigenes Delegation-Schema statt ERC-4337** — verworfen (Standard + Audit-Ökosystem + Bundler/Paymaster-Infra überwiegen).

## Offene Fragen / Lücken (Entscheidungs-Paket vor PRD-05)
1. **🔑 Session-Key-Custody-Modell (die zentrale):** **(a) Backend-gehaltene** Session-Keys (echtes 24/7-unbeaufsichtigt, gebundene Delegation) **vs. (b) on-device** Session-Keys (voll non-custodial, braucht Gerät) **vs. (c) per-Feature** — **DCA on-device (E), Copy/Vaults backend-gehalten**. *(Tendenz: (c) — DCA tolerant für on-device/Interim; Copy/Vaults brauchen backend-gehaltene scoped Keys, weil Echtzeit/App-zu.)* Bestimmt Backend-Key-Storage + Threat-Model.
2. **AA-Stack-Wahl:** SCA-Implementierung (Safe{Core} / ZeroDev / Alchemy Account-Kit / Kernel …) + Session-Key-Modul-Standard (**ERC-7579** modular vs ERC-6900). Rahmt Contract-Track + Audit-Scope. *(Tendenz: etablierte, bereits-auditierte Basis + ERC-7579-Modul, statt from-scratch — reduziert Audit-Risiko.)*
3. **EOA → SCA-Migration:** **neuer SCA** (neue Adresse, Funds müssen migrieren — UX-Bruch) **vs. EIP-7702** (Pectra: bestehende **EOA** delegiert an Contract-Code, **gleiche Adresse**, SCA-Fähigkeiten in-place — großer UX-Gewinn, Reife prüfen). *(Tendenz: EIP-7702 evaluieren — vermeidet Adress-/Fund-Migration für Bestands-Nutzer.)*
4. **Bundler + Paymaster:** self-hosted Bundler vs Drittanbieter (Pimlico/Alchemy/Stackup) + **gesponsertes Gas (Paymaster, Projekt zahlt)** vs **user-funded SCA**. Kosten- + Abuse-Modell (gesponsertes Gas = DoS-/Kosten-Vektor → Rate/Policy nötig).
5. **Audit-Timing ↔ GA-Kopplung:** der Modul-Audit ist der **Long-Pole** für GA. Realistische Sequenz + ob 05–07 als **Fast-Follow** nach Kern-GA dürfen, falls der Audit spät ist (koppelt an PRD-08-F5).
6. **Chain-Support:** ERC-4337-Infra (EntryPoint/Bundler/Paymaster) auf **ETH + Base** bestätigen (Base hat gute 4337-Unterstützung).
7. **Limit-Semantik:** kumulative Caps über Zeit (Rolling-Window) vs per-Op; mehrere parallele Session-Keys (DCA **und** Copy gleichzeitig) → Policy-Komposition + Gesamt-Exposure-Cap.

## GA-Risiko-Akzeptanz (User-Entscheid, 2026-06-19)

**(a) Entscheidung:** **Bot/Copy-Trading (PRD-05/06) gehen OHNE externes Smart-Contract-Audit zum GA** — der **User (Owner)** akzeptiert das Risiko **bewusst, am 2026-06-19, gegen die dokumentierte PO/Assistenten-Empfehlung** (Empfehlung war: kein funds-haltender Contract ungeprüft zum GA; Audit = harter Gate, s. Entscheidung (B)/Q5). Diese Akzeptanz gilt **ausschließlich** für die hier gelisteten Harm-Reduction-Bedingungen + den Revisit-Trigger; sie ist **keine** generelle Freigabe ungeprüfter funds-haltender Contracts.

**(b) Harm-Reduction-Plan (entschärft das fehlende Audit deutlich — verbindlich für den Contract-Track):**
- **Auditierte SCA-Basis** (Safe{Core}/Kernel/ZeroDev) — **nur das dünne Session-Key-Modul ist custom** → minimale ungeprüfte Fläche (der funds-haltende Account-Core ist fremd-auditiert).
- **Enge On-chain-Caps** von Anfang an — per-Op + Rolling-Window + **Gesamt-Exposure-Cap** (Q7); **Worst-Case bei Modul-Bug/Kompromittierung = der Cap**, kein Drain.
- **Staged-Rollout:** Start mit **niedrigen Caps** (kleine Beträge/wenige User) → schrittweise hochziehen, nur nach unauffälligem Betrieb + Monitoring.
- **Interner Mehr-Augen-Review** des Moduls (≥2 unabhängige Reviewer) **+ Bug-Bounty** (öffentlich, vor/zum GA scharf) als Audit-Ersatz-Netz.
- **Pause/Revoke-Kill-Switch:** der User kann Session-Keys jederzeit on-chain revoken; zusätzlich ein **globaler Pause-Schalter** (Modul/Backend stoppt neue UserOps sofort) für den Incident-Fall.

**(c) Revisit-Trigger (verbindlich):** das **externe Audit wird nachgeschoben — BEVOR die Caps signifikant hochgezogen werden** (d. h. vor dem Übergang von „niedrige Staged-Caps" auf produktive/hohe Limits). Bis das Audit vorliegt, **bleiben die Caps im niedrigen Staged-Bereich**. Weitere Trigger: jeder Modul-relevante Incident/Bounty-Fund → Audit-Vorzug. Diese Risiko-Linie ist die dokumentierte Vorgabe für den **PRD-05/07-Contract-Track**.

## Stack-Konkretisierung (Recon 2026-06-19, Backend-Agent — non-binding, Input für den PRD-05-Kickoff)

Recon via Context7 (permissionless.js/Pimlico, Rhinestone SDK). Beantwortet Q2–Q4 und schärft die Resourcing-/Audit-Linie:

- **Q2 SCA-Stack → ERC-7579** (nicht ERC-6900: 7579 trägt das Ökosystem + die auditierten Module). Basis: **Kernel (ZeroDev)** [7579-nativ, leichtgewichtig, 7702-ready] oder **Safe7579** [maximal auditiert] — beide via permissionless.js. **Session-Key-Modul = Rhinestone Smart Sessions** (auditiertes 7579-Modul): `ScopedAction` (target + selector) + Policies (`SpendingLimits` pro Token, Time-Frame, Usage-Limit) — liefert (A)/(C)/Q7 als **fertige, auditierte** Primitive.
- **Q3 EOA→SCA → EIP-7702 (same-address)** primär (post-Pectra, ETH + Base): die on-device-EOA wird zur SCA an **derselben Adresse = der SIWE-Platform-Identität** ([[0026]]) → **keine** Adress-/Fund-Migration. Counterfactual-neuer-SCA nur als Fallback wo 7702 nicht verfügbar.
- **Q4 Bundler/Paymaster → Pimlico** (permissionless.js) primär — ETH + Base, reife 7579/7702/SmartSessions-Integration, Paymaster (Gas-Sponsoring / ERC-20-Gas), EntryPoint 0.7. **Alchemy Account-Kit** = Infra-Synergie-Alternative ([[0021]]-Proxy), aber schwächeres Session-Tooling → Kostenvergleich beim Kickoff.
- **🎯 Resourcing/Audit (entschärft die GA-Risiko-Linie weiter):** Session-/Policy-Logik = **auditierte Off-the-shelf-Module** (Smart Sessions + Kernel/Safe7579) → **kein/minimaler Custom-Solidity** für **DCA v1** (Standard-Policies target/selector/spending/time genügen). Folge: **(1)** kein Solidity-Spezialist für v1 — die Arbeit ist **Integration** (permissionless.js / Rhinestone-SDK + on-device-Grant-UX + Trigger-Pfad), Dev-machbar; **(2)** die in (B)/Q5 + der GA-Risiko-Akzeptanz beschriebene **„ungeprüfter Funds-Contract"-Fläche schrumpft drastisch** (auditierte Module statt Eigenbau) — das Audit-Restrisiko ist deutlich kleiner als ursprünglich angenommen. Ein bespoke Policy-Modul (mit eigenem on-chain-Audit) erst, falls 06/07 (Copy/Vaults) es brauchen.

Verbindlich gepinnt wird der Stack beim **PRD-05-Kickoff** (zusammen mit Q1 Custody-Modell, Q5 Audit-Timing, Q6 Chain-Support).

## Finalisierung (PRD-05-Kickoff 2026-06-19, User — **Accepted**; Epic KAN-138)

Die offenen Fragen sind entschieden; diese Beschlüsse sind **verbindlich** für den AA-Foundation-Build (KAN-139):

- **Q1 Custody-Modell → (c) per-Feature:** **DCA (PRD-05) = on-device** (die App signiert die DCA-Tx, voll non-custodial, Interim (E)); **Copy/Vaults (PRD-06/07) = backend-gehaltene scoped Session-Keys** (Echtzeit/24-7, App-zu) — Backend-Key-Storage in **HSM/KMS**.
- **Q2 Stack → ERC-7579 + Kernel (ZeroDev)** als SCA-Basis + **Rhinestone Smart Sessions** als Session-Key-/Policy-Modul (auditiert; `ScopedAction` + `SpendingLimits`/Time/Usage).
- **Q3 EOA→SCA → EIP-7702 (same-address):** Upgrade der on-device-EOA zur Kernel-SCA an **derselben Adresse = SIWE-Identität** ([[0026]]); kein Adress-/Fund-Bruch.
- **Q4 Bundler/Paymaster → Pimlico** (permissionless.js), ETH + Base.
- **Q5 Audit/GA → auditierte Off-the-shelf-Module + Harm-Reduction, KEIN Extra-Audit** (User-Entscheid, gegen die ursprüngliche Empfehlung — s. „GA-Risiko-Akzeptanz"). Da nur audited Module + ggf. ein dünnes/kein Custom-Modul: die ungeprüfte Fläche ist minimal; die Harm-Reduction (enge Caps, staged Rollout, Mehr-Augen-Review/Bounty, Pause/Revoke) bleibt verbindlich.
- **Q6 Chains → ETH + Base.**
- **Q7 Limit-Semantik → Rolling-Window + Per-Op + Gesamt-Exposure-Cap** (über alle aktiven Session-Keys eines Users).

**Build:** AA-Foundation-Build-Plan = **KAN-139** (Proposal Backend-Agent → PO → sequenziert Backend + App-Seite Dev-1/Dev-2).

## Harm-Reduction — Threat-Model: On-Device-Signing der AA-Sign-Kette (Ph1, approach C)

> Quelle: Dev-2 Ph1-Integrations-Security-Review (KAN-141) + P0-Reconcile, 2026-06-19. **Finaler Text** nach User-Entscheid **(C)** + V1+decode. Implementiert in KAN-143/KAN-144 (`:evm`). Operationalisiert die obige Harm-Reduction für den on-device-Signing-Pfad.

### Asymmetrie: ENABLE (Grant) vs. OP (DCA)

Die App zeigt menschenlesbare Daten und signiert on-device einen 32-Byte-Digest. Zwei Pfade, **unterschiedliche Risiko-Klasse**:

- **GRANT / session-enable = P0 (unbounded).** Der Enable-Digest **setzt** die Smart-Session-Policy (Scope/Selector/Limits) → „on-chain-Caps schützen" ist hier **zirkulär** (der Enable *definiert* die Caps). Ein blind-signierter, manipulierter Enable könnte eine Session mit abweichender/unbeschränkter Policy anlegen → der Session-Key (Ph1 on-device) wäre **Drain-fähig** → bricht „Worst-Case = gecappte Befugnis".
- **DCA-OP = bounded (MED).** Die Per-Op-Signatur läuft durch die **bereits enabled** Session; Scope/Selector/Limits werden **on-chain erzwungen**, out-of-policy revertet. Worst-Case = eine Op **innerhalb der konsentierten Caps**.

### Mitigation (User-Entscheid C + V1+decode, 2026-06-19) — implementiert

Der GRANT wird in Ph1 **on-device kryptografisch verifiziert** (nicht mehr Rest-Trust), über eine einzige fail-closed Funktion `SmartSessionGrantVerifier.verifyGrant` (`:evm`), bevor signiert wird:

1. **Enable-Digest-Recompute:** die App rechnet den EIP-712-Digest über die `MultiChainSession` aus der disclosed `SessionConfig` selbst nach (`Eip712`/`SmartSessionEnableDigest`, byte-exakt gegen 4 SDK==raw-viem-Vektoren bewiesen) und signiert **nur**, wenn `recomputed == backend.digestToSign`.
2. **Policy-Adress-Pinning:** die Spending-Limit- + Time-Frame-**Policy-Adressen sind client-feste Konstanten** (Rhinestone GLOBAL_CONSTANTS, on-chain verifiziert, CREATE2-uniform ETH+Base) — eine getauschte Policy wird gefangen.
3. **Cap-/Window-Decode (schließt den Cap-Blind):** ein reiner Digest-Match ließe die Spending-**Cap** opak (Backend könnte „Cap 5" zeigen, Riesen-Cap encoden → Digest matcht über die Riesen-Cap-Bytes → Value-Loss bis Token-Balance via die erlaubte Action). Die App **decodet** daher die Policy-`initData` (`abi.encode(address token, uint256 cap)` / `(uint start, uint end)`) und zeigt **`token`/`cap`/`window` aus den signierten Bytes** → Anzeige == signierte Realität, kann nicht lügen. (**Decode** statt Re-Encode: kann den ehrlichen Grant nie brechen.)
4. **Broad-Access-Gate:** `permitAdminAccess`/`permitGenericPolicy`/`ignoreSecurityAttestations` müssen aus sein (sonst Scope über die angezeigte Action hinaus) → fail-closed.

→ Die UI rendert **ausschließlich** das zurückgegebene `VerifiedGrant`, nie die rohen Backend-Felder. Der P0 ist geschlossen: der User verifiziert kryptografisch Struktur **und** Cap/Token/Window der Session, die er enabled.

**⚠️ Soundness-Bedingung (kritisch).** Domain + Modul-Bindung sind **client-gepinnt, nie backend-geliefert:** EIP-712-Domain `{name:"SmartSession",version:"1"}` (Chain-Bindung im Body: `ChainSession.chainId` + `SignedSession.smartSession`); `smartSession`-Adresse + die Policy-Adressen + `account`-SCA (= Geräte-Owner-EOA, 7702 same-address) als Code-Konstanten. Sonst validierte der Recompute einen Digest für ein gespooftes Modul/Policy/Account.

### DCA-OP — bounded Rest-Trust (akzeptiert für Ph1)

Innerhalb des **verifizierten** Scopes vertraut die App darauf, dass das Backend den Op-Digest korrekt aus der angezeigten Op berechnet; wirksamer Guardrail = die on-chain Smart-Session-Policy (jetzt vom User verifiziert). Off-chain-Exposure-Pre-Check (Q7) + Pause/Revoke-Kill-Switch begrenzen den Blast-Radius.

### Ph2-Defense-in-Depth (geplant)

On-device-**userOpHash-Recompute** für den Op-Pfad (volles EntryPoint-0.7-Packing + Kernel-callData) → schließt auch die Op-Display↔Hash-Lücke. Höherer Aufwand, version-gekoppelt, geringerer Grenzgewinn (Op ist bereits bounded) → Ph2.

### Owner-Bind (beide Pfade)

Jede Signatur ist owner-gebunden: der Signer muss zur SCA/SIWE-Identität (`m/44'/60'/0'/0/0`) ableiten, sonst `SignerMismatch`.

### Scope

GRANT-Verify (`verifyGrant`) gilt für `GrantViewModel.grant()` (`enable.digestToSign`). Op-Rest-Trust gilt für `DcaViewModel.signPending()` (`userOpHash`).
