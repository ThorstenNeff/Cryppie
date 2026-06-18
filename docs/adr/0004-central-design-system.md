# ADR-0004 — Ein zentrales Design-System/Theme

- **Status:** Accepted (2026-06-17)
- **Kontext:** In `:feature:auth` existiert bereits ein `CryptasaTheme`. Mehrere Features brauchen dasselbe Token-/Komponentenset (Hell/Dunkel, später RTL).
- **Entscheidung:** **Ein zentrales `CryptasaTheme`** (extrahiert nach `:app:shared` bzw. ein eigenes Design-Modul), das alle Features teilen. **Keine Rohwerte** — jeder Wert referenziert ein semantisches Token. Tokens stammen aus dem UI/UX-Handoff (`../../../Cryptasa`).
- **Konsequenzen:** KAN-4 (Foundations) zentralisiert das bestehende Theme statt ein zweites zu bauen. i18n/RTL ([[0001]], SPEC §5.5) und A11y hängen daran.
- **Alternativen:** Theme je Feature (verworfen: Drift/Inkonsistenz).
