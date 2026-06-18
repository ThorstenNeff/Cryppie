# ADR-0003 — Modulstruktur & Plattformvarianz

- **Status:** Accepted (2026-06-17)
- **Kontext:** Mehrere Features (auth, onboarding, …) sollen unabhängig, testbar und plattformübergreifend wachsen.
- **Entscheidung:** **Feature-Module** `:feature:<name>` mit öffentlichem `*Root()`-Einstieg (Muster: `:feature:auth`). **`commonMain`-first**; Plattformunterschiede ausschließlich über **`expect`/`actual`** (Vorbild `Platform.kt`). `app:shared/App()` delegiert an die Feature-Roots. Geteilte, Compose-freie Logik in `:core`.
- **Konsequenzen:** Onboarding entsteht als `:feature:onboarding` analog zu `:feature:auth`. Plattform-Layer (Keystore, Biometrie, CSPRNG, Clipboard, Connectivity) als `expect`/`actual`.
- **Alternativen:** Monolithisches `app:shared` (verworfen: schlechte Isolation/Testbarkeit).
