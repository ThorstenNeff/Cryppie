# Changelog

All notable changes to Cyppie are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project tracks work per Jira ticket
(project `KAN`). Entries are summarised changes (what/why + ticket key), not raw commit logs.

## [Unreleased]

### Added
- **KAN-4 — Onboarding foundations / design system.**
  - New `:designsystem` module: central `CryptasaTheme` with light/dark semantic colour, spacing,
    radius and typography tokens (CompositionLocals; System/Light/Dark mode); foundation components
    `CryptasaButton`, `CryptasaTextField` (incl. inline error), `CryptasaBanner`, `CryptasaDialog`;
    reusable patterns `SelectionCard`, `CryptasaCheckbox`, `SegmentedControl`, `SeedWordCell`,
    `PasswordStrengthIndicator`, `ProgressRing`, `CryptasaTopAppBar`. All token-bound, RTL-ready (`start`/`end`), a11y
    touch targets ≥ 48 dp, errors conveyed via icon + text (ADR-0004).
  - New `:feature:onboarding` module: `OnboardingRoot()` with Compose Navigation 3 back stack
    (ADR-0006), adaptive `OnboardingScaffold(sizeClass)` driven by Material 3 Window Size Classes
    (Compact / Compact-landscape / Medium / Expanded, max-width 480; ADR-0012), `OnboardingViewModel`,
    and the feature's Koin module (ADR-0007). Welcome/route placeholders carry `testTag`s
    (`onb_*`) ready for Maestro (KAN-10); real screens follow in KAN-5+.
  - Dependency injection via Koin: app aggregates feature modules in `App()` (`KoinApplication`).
  - `README.md` rewritten as the source of truth for running **and** testing every target
    (incl. JAVA_HOME, module overview, test tooling matrix); this `CHANGELOG.md` added.

### Changed
- `app:shared` `App()` now renders the wallet onboarding flow (`OnboardingRoot()`) instead of
  `AuthRoot()`; Android manifest set to `resizeableActivity="true"` with no orientation lock (ADR-0012).
- Centralised the `CryptasaTheme` out of `:feature:auth` into `:designsystem` (ADR-0004). The KAN-1
  example login/registration screens remain (deferred to PRD-08) but keep only a clearly-marked
  legacy colour palette and no longer define a competing theme.
