# Cyppie

Non-custodial DeFi wallet built with **Kotlin Multiplatform** + **Compose Multiplatform**, targeting
**Android, iOS, Desktop (JVM), Web (JS + Wasm)** and a **Ktor server**. All UI is shared via Compose.

> Architecture, module graph, `expect`/`actual` pattern and coding conventions live in
> [`CLAUDE.md`](./CLAUDE.md); architecture decisions in [`docs/adr/`](./docs/adr/INDEX.md). This file
> is the single source of truth for **how to run and test** each target.

## Prerequisites — JAVA_HOME

This machine has no Java on the `PATH`. Use the Gradle-managed JDK (Amazon Corretto 21) and export it
before any `./gradlew` call:

```bash
export JAVA_HOME=/Users/customer/.gradle/jdks/amazon_com_inc_-21-aarch64-os_x.2/amazon-corretto-21.jdk/Contents/Home
```

## Module overview

| Module | Purpose |
|---|---|
| `:core` | Platform-agnostic logic, no Compose; usable by the server too. |
| `:designsystem` | Central `CryptasaTheme` (tokens, light/dark) + foundation components & patterns (ADR-0004). Shared by every feature. |
| `:feature:onboarding` | Wallet onboarding flow: `OnboardingRoot()`, Nav3 navigation, adaptive `OnboardingScaffold`, Koin module. |
| `:feature:auth` | KAN-1 example account-auth screens — **deferred to PRD-08**, not wired into the app (kept as the feature-module reference). |
| `:app:shared` | Shared Compose entry `App()` (bootstraps Koin, renders `OnboardingRoot()`). |
| `:app:androidApp` / `:desktopApp` / `:webApp` / `:iosApp` | Thin per-platform entry points. |
| `:server` | Ktor (Netty) server, depends on `:core` only. |

## Running the app

| Target | Command |
|---|---|
| Android | `./gradlew :app:androidApp:assembleDebug` (then install the APK) |
| Desktop (hot reload) | `./gradlew :app:desktopApp:hotRun --auto` |
| Desktop (standard) | `./gradlew :app:desktopApp:run` |
| Server | `./gradlew :server:run` (listens on `0.0.0.0:8080`) |
| Web — Wasm (modern browsers) | `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun` |
| Web — JS (older browsers) | `./gradlew :app:webApp:jsBrowserDevelopmentRun` |
| iOS | Open [`/app/iosApp`](./app/iosApp) in Xcode and run (consumes the `Shared` framework). |

## Testing

Tooling matrix (ADR-0011 / ADR-0013):

| Layer | Tool | Where |
|---|---|---|
| Unit / logic | `kotlin.test` + **Turbine** (Flows) + **MockK** (JVM/Android only) | `commonTest` (multiplatform fakes, no MockK), `jvmTest`/`androidHostTest` |
| Android Compose-UI **& config-changes** (rotation, size class, dark/light, locale/RTL, font scale, state retention) | **Robolectric** (`@Config`/`setQualifiers`, `DeviceConfigurationOverride`) | `androidHostTest` |
| Desktop Compose-UI | **`runComposeUiTest`** | `jvmTest` |
| Device E2E (Android/iOS) | **Maestro** | `../Tests/.maestro/` (owned by the test agent; expects our `testTag`s) |

There is **no single "test everything" task** — Compose UI/logic tests run **per target**:

| Target | Command |
|---|---|
| Desktop (JVM) | `./gradlew :app:shared:jvmTest` |
| Android (host/unit, incl. Robolectric) | `./gradlew :app:shared:testAndroidHostTest` |
| iOS simulator | `./gradlew :app:shared:iosSimulatorArm64Test` |
| Web — Wasm | `./gradlew :app:shared:wasmJsTest` |
| Web — JS | `./gradlew :app:shared:jsTest` |
| Server | `./gradlew :server:test` |

Run a single test class/method with the standard filter, e.g.:
`./gradlew :app:shared:jvmTest --tests "com.tneff.cyppie.AppKoinTest"`

Per-module tests (e.g. the design system / onboarding) follow the same per-target tasks, e.g.
`./gradlew :designsystem:jvmTest`, `./gradlew :feature:onboarding:jvmTest`.

### Maestro (Android/iOS device flows)

Maestro runs against a **built, installed** app on a device/emulator. Flows live in
[`../Tests/.maestro/`](../Tests/.maestro) and select elements by `id:`, which maps to our Compose
`testTag`s (the onboarding root enables `testTagsAsResourceId`, §5.1 / KAN-10). Until the screens are
built, the `.maestro/onb-*.yaml` files are templates.

## Build / quality

- Full build: `./gradlew build`
- Clean: `./gradlew clean`
- Code style: `kotlin.code.style=official` (no separate lint/format task configured).

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)
and [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform).
