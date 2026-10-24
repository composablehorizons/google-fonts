# Compose Google Fonts

Use Google Fonts in Android and Compose Multiplatform apps. Load families dynamically at runtime, or bundle selected fonts automatically at build time.

## Dynamic fonts

```kotlin
val fontFamily = rememberGoogleFontFamily("Inter", weights = listOf(400, 700))
```

Import `com.composables.googlefonts.rememberGoogleFontFamily`. The function returns `FontFamily.SansSerif` while the font loads, or the fallback you pass. It keeps the fallback if loading fails. Loaded families are reused across compositions, so recreating a screen does not briefly switch back to its fallback. This API fetches fonts from Google Fonts when needed.

## Bundled fonts

The Gradle plugin downloads your selected fonts during normal builds and packages them with your app. No separate download command or runtime Google Fonts dependency is required.

Add Maven Central to `pluginManagement.repositories` in your app's `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Apply the plugin in your Android app/library or CMP module:

```kotlin
plugins {
    // Your existing Android or Kotlin Multiplatform and Compose plugins.
    id("com.composables.google-fonts") version "0.2.0"
}

googleFonts {
    family("Inter") {
        weights(400, 500, 700)
        italic() // Include normal and italic faces for each weight.
    }
    family("Open Sans") {
        weights(400, 700)
    }
}
```

Use the generated accessors in a composition:

```kotlin
import com.composables.googlefonts.generated.GoogleFonts

val family = GoogleFonts.Inter
val headingFamily = GoogleFonts.OpenSans
```

The default weight is 400. Set `accessor = "MyFont"` inside a family block to override its generated property name. Set `googleFonts.packageName` to change the generated Kotlin package, particularly when bundling fonts in multiple modules.

Android uses native `res/font` resources and needs no CMP resource dependency. CMP requires `org.jetbrains.compose`; the plugin adds the matching Compose resources dependency automatically. Generated fonts, Kotlin, and licenses live under the module's `build/generated/googleFonts/`. Existing `src/commonMain/composeResources` contents are merged into the generated CMP resource directory. If you use a custom common resource directory, set `googleFonts.resourcesDirectory` to that directory instead of configuring `compose.resources.customDirectory("commonMain", ...)`.

Commit the generated `google-fonts.lock` beside your module's build file. It pins font and license URLs and SHA-256 checksums. Subsequent builds reuse downloads from `~/.gradle/caches/com.composables.google-fonts/v1/`, including after `clean`. A fresh machine needs network access for the locked files once; afterward `--offline` works. Downloads appear in Gradle's build output, and failures stop the build. To update one face, remove all lockfile lines with its resource-name prefix and rebuild; delete the lockfile to resolve everything again.

Font licenses are included in Android assets or CMP files at `googleFonts/licenses/`.

### Wasm startup

Wasm deployments include the font files alongside the application. Browsers still fetch them from your host. To wait until every selected font is ready, use the generated helper from your Wasm startup composition:

```kotlin
import com.composables.googlefonts.generated.GoogleFonts
import com.composables.googlefonts.generated.preload

@Composable
fun WebApp() {
    if (GoogleFonts.preload()) {
        App()
    }
}
```

Preloading is optional. Without it, Compose can show a fallback while the bundled resources load.

## Installation

Add Maven Central to your dependency repositories and add the library to `commonMain`:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.composables:google-fonts:0.2.0")
        }
    }
}
```

## Build locally

Use JDK 17, Xcode for iOS, and Android SDK platform 37. From this directory:

```sh
./gradlew jvmTest :gradle-plugin:check publishToMavenLocal
```

The standalone build includes all supported targets.

## Releasing

Install KPM:

```sh
npm install --global @composablehorizons/kpm@0.4.0
```

Set the release notes in `CHANGELOG.md`, then run from this directory:

```sh
./gradlew jvmTest :gradle-plugin:check publishToMavenLocal
kpm version patch --no-git-tag-version
release_version="$(kpm version)"
(cd gradle-plugin && kpm version "$release_version" --no-git-tag-version)
git add CHANGELOG.md package.yml gradle-plugin/package.yml
git commit -m "Release $release_version"
git push origin main
git tag "v$release_version"
git push origin "v$release_version"
```

The root and `gradle-plugin/package.yml` files own their Maven publishing metadata. Keep their versions equal before tagging a release. The tagged GitHub Actions release checks both versions, runs runtime and plugin tests, builds all publications, and publishes signed artifacts for both modules with KPM. After every artifact is downloadable from Maven Central, it creates a draft GitHub release from the changelog. Review and publish that draft separately.

Publishing uses the `maven-central` environment and the organization secrets `OSSRH_USERNAME`, `OSSRH_PASSWORD`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`, and `SIGNING_KEY`.
