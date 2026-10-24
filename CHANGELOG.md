# Changelog

## [Unreleased]

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
