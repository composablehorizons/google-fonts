# Compose Google Fonts

Load a Google Fonts family in a Compose Multiplatform app. The library supports Android, iOS, JVM desktop, and Wasm browsers.

```kotlin
val fontFamily = rememberGoogleFontFamily("Inter", weights = listOf(400, 700))
```

Import `com.composables.googlefonts.rememberGoogleFontFamily`. The function returns `FontFamily.SansSerif` while the font loads, or the fallback you pass. It keeps the fallback if loading fails. Fonts are fetched from Google Fonts when needed; this repository does not bundle font files.

## Installation

Add Maven Central to your dependency repositories and add the library to `commonMain`:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.composables:google-fonts:0.1.0")
        }
    }
}

## Build locally

Use JDK 17, Xcode for iOS, and Android SDK platform 37. From this directory:

```sh
./gradlew jvmTest publishToMavenLocal
```

The standalone build includes all supported targets.

## Releasing

Install KPM:

```sh
npm install --global @composablehorizons/kpm@0.1.0
```

Set the release notes in `CHANGELOG.md`, then run from this directory:

```sh
./gradlew jvmTest publishToMavenLocal
kpm version patch
git push --follow-tags
```

`package.yml` owns the version and Maven publishing metadata. The tagged GitHub Actions release checks the version, runs JVM tests, builds all platform publications, and publishes signed artifacts with `kpm publish`. After every platform artifact is downloadable from Maven Central, it creates a draft GitHub release from the changelog. Review and publish that draft separately.

Publishing uses the `maven-central` environment and the organization secrets `OSSRH_USERNAME`, `OSSRH_PASSWORD`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`, and `SIGNING_KEY`.
