# ADR-0013 — Android Config-Change- & Compose-UI-Tests mit Robolectric (JVM)

- **Status:** Accepted (2026-06-17) — **Android**; ergänzt [[0011]].
- **Kontext:** Pro Screen müssen **Konfigurationswechsel** geprüft werden: Orientierung/Rotation, Fenster-/Bildschirmgröße (→ Window Size Class, [[0012]]), Dark/Light, Locale/RTL, FontScale — inkl. **State-Erhalt** über Recomposition/Activity-Recreation (keine Datenverluste). **Maestro kann das nicht** (Black-Box-E2E auf dem Gerät, kein deterministischer Config-Override pro Screen). Referenz-Setup: kmpbits „Robolectric + Compose".
- **Entscheidung:** **Robolectric** als JVM-Runner für **Android-Compose-UI- und Config-Change-Tests** — schnell, deterministisch, **ohne Emulator**, im Android-Host-Test-Sourceset **`androidHostTest`** (bzw. Android-Unit-Test-Set des Feature-Moduls). Mechanik:
  - **`@Config(qualifiers=…)`** / **`RuntimeEnvironment.setQualifiers("+land" / "+night" / "+w600dp" / "+sw600dp" / "+fontscale1.5" / "+de" / "+ar")`** (`+` = relativ ändern) — Robolectric wendet die Konfiguration an.
  - **`DeviceConfigurationOverride`** (`androidx.compose.ui:ui-test`: `ForcedSize`, `FontScale`, `DarkMode`, `LayoutDirection`, `Locales`) zum Wrappen des Composables — Config-Variation **ohne** Activity-Recreation, ideal pro Screen.
  - **State-Erhalt:** `createAndroidComposeRule<ComponentActivity>()` + `scenario.recreate()` (bzw. `ActivityController.configurationChange()`) → prüfen, dass `rememberSaveable`/ViewModel-State (z. B. Seed-Wörter, Passwortfeld, Fehlerzustände) den Wechsel überlebt.
  - **Dependencies:** `org.robolectric:robolectric`, `androidx.compose.ui:ui-test-junit4`, `androidx.test:core`; `android { testOptions { unitTests.isIncludeAndroidResources = true } }`.
- **Konsequenzen:**
  - **Werkzeug-Aufteilung** (ergänzt [[0011]]): **Robolectric** = Android-Compose-UI- **& Config-Change-Tests** (JVM, pro Screen) · **`runComposeUiTest`** = Desktop-Compose-UI-Tests · **Maestro** = geräteweite E2E-Flows (Android/iOS) · **Turbine/MockK/kotlin.test** = Unit/Logik.
  - Verifiziert die Adaptive-Anforderung [[0012]] **pro Screen** (Compact/Medium/Expanded + Landscape) deterministisch **und** den State-Erhalt bei Rotation/Resize.
  - **Android-only** (Robolectric ist Android); iOS-Config-Verhalten separat (später). Tests liegen in `../Cyppie`-Test-Sourcesets — Dev ↔ Test abgestimmt ([[0011]] Ownership).
  - Hinweis: Der kmpbits-Artikel zeigt die Robolectric-Compose-Basis; `setQualifiers`/`DeviceConfigurationOverride` ist etablierte Robolectric-/AndroidX-API und hier festgeschrieben.
- **Alternativen (nicht gewählt):** nur Maestro (kann Config-Changes/State-Erhalt pro Screen nicht deterministisch); Instrumented-Tests auf Emulator (langsam, geräteabhängig — als Ergänzung möglich, nicht Default).
