# ADR-0012 — Adaptive Layouts & Breakpoints (Window Size Classes)

- **Status:** Accepted (2026-06-17) — **Android**; iOS später auf demselben System.
- **Kontext:** Android 16 / Play verlangt **resizable** Apps (Portrait **und** Landscape, Multi-Window, Foldables); ab ≥ 600 dp werden Orientierungs-/Resize-Sperren ignoriert. Das Onboarding muss adaptiv sein. Quelle: `../../Cryptasa/HANDOFF_Onboarding_Android.md` §7a + `../../Cryptasa/tickets/README.md`; Figma-Seite `Adaptive / Onboarding`.
- **Entscheidung:** Layout über **Material-3 Window Size Classes** (`currentWindowAdaptiveInfo().windowSizeClass`) — **nicht** gerätemodellbasiert. Breakpoints (Breite): **Compact < 600 · Medium 600–839 · Expanded ≥ 840** (Large ≥ 1200, XL ≥ 1600 = wie Expanded); **Höhe** zusätzlich (Compact-Höhe < 480). Ein gemeinsames **`OnboardingScaffold(sizeClass)`** wählt das Layout:
  - **Compact (Portrait):** 1-spaltig, Scroll, CTA unten gepinnt (= bestehende Mobile-Frames).
  - **Compact-Höhe (Landscape-Handy, z. B. 800×360):** schmales Marken-/Titelband (~300) links, Inhalt rechts im Scroll-Container, CTA sticky, Hero kollabiert — **kein** Tablet-Zweispalter.
  - **Medium:** zentrierte Karte (max-width **480**) auf `surface`, Dialoge mittig.
  - **Expanded:** Zweispalter Marken-Spalte + Karte (= Desktop-Frames).
- **Regeln:** Inhalt/Karte **max-width 480**; Eingabe-Screens (3–7) `verticalScroll` + `imePadding`, CTA erreichbar; **Seed-Grid 3 → 4 Spalten** (Compact → Medium/Expanded); Marken-/Gradient-Spalte **nur ab Expanded** (bzw. schmales Band im Landscape-Handy); **keine** `screenOrientation`-Sperre, `android:resizeableActivity="true"`; sensible Screens (Seed/Backup/Passwort) auch im Landscape `FLAG_SECURE`.
- **Konsequenzen:**
  - Nutzt `androidx.compose.material3.adaptive` — passt zu Nav3 `material3.adaptive.navigation3` ([[0006]]). Tokens/Theme unverändert ([[0004]]); Layout-Logik in `commonMain` über Size-Class → iOS/Desktop teilen dasselbe System (Expanded = die bereits gebauten Desktop-Frames).
  - Foldables: nach Size-Class (gefaltet = Compact, entfaltet = Medium/Expanded).
  - ⚠️ Figma-Seite `Adaptive / Onboarding` ist bisher **nur exemplarisch für Android und nur Light Mode**; Dark- und iOS-Adaptive-Frames stehen aus. Dev leitet Dark per Token-Flip ab und validiert gegen die Light-Adaptive-Frames; **iOS-Adaptive folgt** (gleiche Size-Class-Logik).
- **Alternativen (nicht gewählt):** Orientierung sperren / nur 1-spaltig (verworfen — Play-Vorgabe, Tablets/Foldables); separate Layouts je Gerätemodell (verworfen — nicht wartbar).
