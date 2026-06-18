# ADR-0011 — Test-Strategie & -Stack

- **Status:** Accepted (2026-06-17)
- **Kontext:** KMP-App mit mehreren Test-Ebenen (Logik/Unit, UI-E2E) über mehrere Targets (Android, iOS, Desktop/JVM, Web). Maestro ([[maestro-testing]]) deckt nicht alle Targets ab.
- **Entscheidung:**
  - **Unit-/Logik-Tests:** `kotlin.test` (bereits genutzt) + **Turbine** (Flow/`StateFlow`-Assertions) + **MockK** (Mocking). Logik in `commonTest`, plattformspezifisches in den jeweiligen Test-Sourcesets.
  - **UI-E2E:** **Maestro** für **Android & iOS** (Flows in `../../Tests/.maestro/`).
  - **Desktop (JVM):** **Compose UI Tests** (`runComposeUiTest` / `@Composable`-Tests) — **Maestro unterstützt Desktop nicht**. Diese Tests laufen als JVM-Tests in den Test-Sourcesets des App-Moduls.
- **Konsequenzen:**
  - ⚠️ **MockK ist JVM/Android-only** (kein Native/JS/Wasm). → MockK **nur** in `jvmTest`/`androidHostTest`; in `commonTest` **handgeschriebene Fakes/Test-Doubles** statt Mocks. **Turbine ist multiplatform** → in `commonTest` nutzbar.
  - **Compose UI Tests** decken Desktop ab (laufen auch auf Android-Host); geteilte UI ist so plattformnah prüfbar.
  - **Ownership:** Maestro-Flows liegen beim Test-Agenten (`./Tests`); Unit- und Compose-UI-Tests liegen in den Test-Sourcesets der App (`./Cyppie`) und werden **Dev ↔ Test abgestimmt**.
  - Per-Target-Test-Tasks/JAVA_HOME: siehe `../../CLAUDE.md`.
- **Alternativen (nicht gewählt):** Mockito (JVM-only); für `commonMain`-Mocking KMP-Mocking-Libs wie Mokkery/Mockative (falls künftig nötig); Maestro-only (kann Desktop nicht testen).
