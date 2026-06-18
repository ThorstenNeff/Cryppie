# ADR-0002 — Schichtung: Clean Architecture + MVVM + Repository

- **Status:** Accepted (2026-06-17)
- **Kontext:** Testbare, AI-navigierbare Struktur mit klarer Trennung von UI, Zustand und Datenzugriff.
- **Entscheidung:** **Clean Architecture + MVVM + Repository.** UI → ViewModel (expliziter State + Intents) → Repository (Interface in `commonMain`, Implementierung ggf. plattform-/datenquellenspezifisch). Navigation state-basiert (vgl. `feature:auth`).
- **Konsequenzen:** Logik in `commonMain`/`:core` ist in `commonTest` testbar; UI bleibt dünn. Klare Verantwortlichkeiten.
- **Alternativen:** Reines MVI (möglich, aber mehr Zeremonie), MVP (veraltet für Compose).
