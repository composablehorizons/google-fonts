# Changelog

## [Unreleased]

## [0.4.0] - 2026-11-04

### Changed

- **Breaking:** `rememberGoogleFontFamily()` takes weights as arguments, such as `rememberGoogleFontFamily("Inter", 400, 700)`, and `italic = true` now loads italic faces alongside the regular ones, so one family renders both upright and italic text. This replaces the `weights` list and `style` parameters.
- **Breaking:** Bundled fonts are generated in each module's own package by default, such as `com.example.app.fonts.GoogleFonts`, so several modules can bundle fonts without clashing. Android modules use their `namespace`; Compose Multiplatform and Desktop modules use the project group and name. Set `googleFonts.packageName` to keep the previous `com.composables.googlefonts.generated` package.

## [0.3.0] - 2026-10-28

### Removed

- Remove the JVM `googlefonts.verificationFallback` system property and the `GoogleFontLoading` load observer. Both existed only for one consumer's headless tooling; wait for text layout to settle instead of hooking into font loading.

## [0.2.1] - 2026-10-24

### Fixed

- Support automatic font bundling in Compose Desktop projects using the Kotlin/JVM plugin, without requiring a multiplatform module.

## [0.2.0] - 2026-10-24

### Added

- Bundle selected Google Fonts automatically during Android and Compose Multiplatform builds with the `com.composables.google-fonts` Gradle plugin.
- Access bundled families through composable properties such as `GoogleFonts.Inter`.
- Preload bundled fonts before displaying a Wasm app with `GoogleFonts.preload()`.
- Keep bundled font versions consistent with a checksum lockfile and reuse cached downloads for offline builds.

## [0.1.1] - 2026-10-18

### Fixed

- Prevent loaded fonts from briefly reverting to the fallback when a screen is recreated.

## [0.1.0] - 2026-10-16

### Added

- Load Google Fonts on Android, iOS, JVM desktop, and Wasm browsers.
- Persist downloaded JVM fonts and share concurrent downloads.
- Show a fallback font while loading or if the request fails.
