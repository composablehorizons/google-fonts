# Changelog

## [Unreleased]

## [0.1.1] - 2026-10-18

### Fixed

- Prevent loaded fonts from briefly reverting to the fallback when a screen is recreated.

## [0.1.0] - 2026-10-16

### Added

- Load Google Fonts on Android, iOS, JVM desktop, and Wasm browsers.
- Persist downloaded JVM fonts and share concurrent downloads.
- Show a fallback font while loading or if the request fails.
