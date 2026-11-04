![Google Font family names, each set in its own typeface](art/hero.png)

# Compose Google Fonts

Use any Google Font in Android and Compose Multiplatform apps, either loaded at runtime or bundled into your app at build time.

[![Maven Central](https://img.shields.io/maven-central/v/com.composables/google-fonts)](https://central.sonatype.com/artifact/com.composables/google-fonts)
[![Verify](https://github.com/composablehorizons/google-fonts/actions/workflows/ci.yml/badge.svg)](https://github.com/composablehorizons/google-fonts/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

## Features

- **Bundled fonts without manual downloads:** a Gradle plugin fetches the fonts you list during your normal build and packages them with your app.
- **Any Google Font by name:** load `"Inter"` or `"Open Sans"` at runtime with one composable call.
- **Typed accessors:** use bundled families as `GoogleFonts.Inter`, with names checked by the compiler.
- **Reproducible builds:** a lockfile pins every font file by checksum, and cached downloads keep `--offline` and `clean` builds working.
- **Every Compose target:** Android, iOS, desktop (JVM) and web (Wasm), including Android-only and Compose Desktop modules.
- **No flicker on recreation:** loaded families are reused across screens, so a recreated screen does not briefly fall back to the default font.
- **Licenses included:** each bundled font ships with its license file.

## Installation

There are two ways to use Google Fonts, and you can use either or both:

### Bundle fonts at build time

Choose this for the fonts your app's design uses. They ship inside your app and need no network at runtime.

Add Maven Central to `pluginManagement.repositories` in your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
```

Then apply the plugin in your Android, Compose Multiplatform or Compose Desktop module:

```kotlin
plugins {
    // Your existing Android or Kotlin and Compose plugins.
    id("com.composables.google-fonts") version "0.3.0"
}
```

Compose Multiplatform modules need the `org.jetbrains.compose` plugin; the Google Fonts plugin adds the Compose resources dependency for you.

### Load fonts at runtime

Choose this when the font is only known while the app runs, such as a font the user picks.

Add the library to `commonMain`:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.composables:google-fonts:0.3.0")
        }
    }
}
```

## Quick start

Display text in Inter bundled with your app, by listing it in the `googleFonts` block of your module's build file:

```kotlin
googleFonts {
    family("Inter") {
        weights(400, 700)
    }
}
```

Build the module, then pass the generated `GoogleFonts.Inter` to `Text`:

```kotlin
import com.example.app.fonts.GoogleFonts

Text("Hello, Inter", fontFamily = GoogleFonts.Inter)
```

`GoogleFonts` is generated in your module's package: `<namespace>.fonts` in Android modules, or `<group>.<module name>.fonts` in Compose Multiplatform and Desktop modules. A family without `weights(...)` includes only weight 400.

## Examples

### Load a Google Font at runtime

Display text in a font chosen while the app runs, by passing the family returned by `rememberGoogleFontFamily()` to `Text`:

```kotlin
import com.composables.googlefonts.rememberGoogleFontFamily

@Composable
fun Greeting(fontName: String) {
    val family = rememberGoogleFontFamily(fontName, 400, 700)
    Text("Hello, $fontName", fontFamily = family)
}
```

The text renders in `FontFamily.SansSerif` until the font finishes loading, then switches to it. Without weights, only weight 400 is loaded.

### Include italic styles

Bundle the italic face of every listed weight, by calling `italic()` in the family block:

```kotlin
googleFonts {
    family("Inter") {
        weights(400, 700)
        italic()
    }
}
```

Load italics at runtime the same way, by passing `italic = true` to `rememberGoogleFontFamily()`:

```kotlin
val inter = rememberGoogleFontFamily("Inter", 400, 700, italic = true)
```

Either family then renders both upright and italic text, picking the face that matches `fontStyle`:

```kotlin
Text("Emphasis", fontFamily = inter, fontStyle = FontStyle.Italic)
```

### Show a different font while loading

Keep your own font on screen while a Google Font loads, or if it fails to load, by passing it as `fallback`:

```kotlin
val lora = rememberGoogleFontFamily("Lora", fallback = FontFamily.Serif)
```

### Rename a generated font accessor

Access a bundled family under your own name, by setting `accessor` in its family block:

```kotlin
googleFonts {
    family("Playfair Display") {
        accessor = "Heading"
    }
}
```

The family is then available as `GoogleFonts.Heading` instead of `GoogleFonts.PlayfairDisplay`.

### Choose the package of generated fonts

Import bundled fonts from a package of your choice, by setting `packageName` in the `googleFonts` block:

```kotlin
googleFonts {
    packageName.set("com.example.design.fonts")
    family("Inter")
}
```

### Wait for bundled fonts before showing a web app

Avoid a fallback font on the first frame of a Wasm app, by showing your UI only once `GoogleFonts.preload()` returns `true`:

```kotlin
import com.example.app.fonts.GoogleFonts
import com.example.app.fonts.preload

@Composable
fun WebApp() {
    if (GoogleFonts.preload()) {
        App()
    }
}
```

Browsers still fetch bundled fonts from your host, so without preloading Compose can show a fallback while they arrive.

### Update a bundled font to its latest version

The plugin writes a `google-fonts.lock` file beside your module's build file. It pins each font and license file by checksum, so commit it.

To fetch a newer version of one face, remove all lockfile lines starting with its resource name, such as `googlefonts_inter_400_normal`, and rebuild. Delete the lockfile to update every font.

## Setup for custom Compose resource directories

The plugin merges its fonts with your `src/commonMain/composeResources` folder. If your module keeps common resources somewhere else, the plugin needs to know where so it can merge them into the generated resources.

Set `resourcesDirectory` instead of calling `compose.resources.customDirectory("commonMain", ...)`:

```kotlin
googleFonts {
    resourcesDirectory.set(layout.projectDirectory.dir("resources/common"))
}
```

## Platform notes

- **Android:** bundled fonts become `res/font` resources and licenses go to `assets/googleFonts/licenses/`; no Compose resources dependency is needed.
- **Compose Multiplatform and Desktop:** bundled fonts become Compose resources, and licenses are packaged under `files/googleFonts/licenses/`.
- **Web (Wasm):** bundled fonts are served from your host alongside the app.

## Troubleshooting

- **The build fails while downloading fonts.** The first build on a machine needs network access to fetch the locked files. Later builds reuse `~/.gradle/caches/com.composables.google-fonts/v1/`, including after `clean` and with `--offline`.
- **A font stays on its fallback.** `rememberGoogleFontFamily()` fetches from Google Fonts at runtime, so the device needs network access and the family name must match Google Fonts exactly. The fallback is kept when loading fails.
