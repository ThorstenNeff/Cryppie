# ADR-0006 — Navigation: Compose Navigation 3 (Nav3)

- **Status:** Accepted (2026-06-17)
- **Kontext:** Die CMP-App braucht App-weite Navigation. Bisher navigiert `:feature:auth` state-basiert im ViewModel ohne Lib. Onboarding ist ein linearer Flow mit Create/Import-Verzweigung.
- **Entscheidung:** **Compose Navigation 3 (Nav3).** Artefakte: `androidx.navigation3.runtime`, `androidx.navigation3.ui`, `androidx.lifecycle.viewmodel.navigation3`, `androidx.material3.adaptive.navigation3`. Modell: **Backstack als beobachtbarer State** (`mutableStateListOf`/`rememberNavBackStack`), `NavDisplay` mit `entryProvider`/`NavEntry`, Routen als `NavKey` (kotlinx.serialization für Persistenz über Konfig-/Prozesstod). Routen-Keys folgen, wo sinnvoll, dem testTag-/i18n-Namensschema.
- **Multiplatform-Status (geklärt, 2026-06-17):** Compose Multiplatform unterstützt **ab Version 1.10** offiziell die Nutzung von Navigation 3 in Multiplatform-Projekten auf **allen** Targets — Android, iOS, Desktop, Web (Quelle: offizielle JetBrains-/CMP-Doku). Das Projekt ist auf **CMP 1.11.1** (`../../CLAUDE.md`) → Nav3 ist plattformübergreifend unterstützt. Der zuvor hier notierte „alpha/Android-first"-Vorbehalt **entfällt**.
- **Konsequenzen:**
  - (+) Backstack-als-State passt zum state-basierten Ansatz aus [[0002]]; adaptive Layouts via `material3.adaptive` helfen Desktop ([[0001]]).
  - (+) kotlinx.serialization wird ohnehin gebraucht ([[0010]]).
  - Auflage: **CMP-Version ≥ 1.10 halten**; Nav3-Artefakte sind noch jung — mit API-Anpassungen bei Updates rechnen.
- **Alternativen (nicht gewählt):** Decompose, Voyager, Navigation 2 (Compose Navigation), reines state-based. Da die CMP-Unterstützung bestätigt ist, dienen sie nicht mehr als Risiko-Fallback.
