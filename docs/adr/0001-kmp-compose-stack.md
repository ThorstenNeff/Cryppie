# ADR-0001 — App-Stack: Kotlin Multiplatform + Compose Multiplatform

- **Status:** Accepted (2026-06-17)
- **Kontext:** Mobile-first non-custodial Wallet für Android, iOS, Desktop und Web; geteilte UI und Logik gewünscht, plus ein Ktor-Server. Eine Codebasis statt N native Apps.
- **Entscheidung:** Kotlin Multiplatform mit **Compose Multiplatform** für geteilte UI. Targets: Android, iOS, Desktop (JVM), Web (JS + Wasm); Server als Kotlin/JVM (Ktor). iOS bindet das `Shared`-Framework via Xcode ein. (Siehe `../../CLAUDE.md`.)
- **Konsequenzen:** Eine geteilte Codebasis; Plattformvarianz über `expect`/`actual` ([[0003]]). Compose-freie, serverfähige Logik in `:core`. iOS-Einstieg native via Xcode.
- **Alternativen:** Native pro Plattform (verworfen: Duplikation), Flutter/React Native (verworfen: Kotlin-Ökosystem + Krypto-Anforderungen).
