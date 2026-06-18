# ADR-0007 — Dependency Injection: Koin

- **Status:** Accepted (2026-06-17)
- **Kontext:** Wachsende Zahl von ViewModels/Repositories/Plattform-Implementierungen braucht eine konsistente Verdrahtung über Module hinweg.
- **Entscheidung:** **Koin** (Kotlin Multiplatform) als DI-Framework. Ein DI-Modul je Feature/Schicht; Compose-Integration für ViewModels; plattformspezifische `actual`-Bindings je Target.
- **Konsequenzen:** (+) Einfach, breite KMP-Nutzung, gute Compose-Integration. (−) Laufzeit-Auflösung ohne Compile-Time-Check → durch DI-Verifikationstests/Smoke absichern. Feature-Module definieren ihre Koin-Module selbst.
- **Alternativen:** Manuelles DI (mehr Boilerplate), Kodein (kleinere Community), Compile-time-DI (für KMP/Compose unausgereift).
