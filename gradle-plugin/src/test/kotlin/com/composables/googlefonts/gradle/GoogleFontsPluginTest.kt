package com.composables.googlefonts.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile
import kotlin.test.*

class GoogleFontsPluginTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `plain JVM bundles fonts automatically and preserves main resources offline`() {
        val root = fixture("""
            plugins {
                kotlin("jvm") version "2.4.0"
                id("com.composables.google-fonts") version "${System.getProperty("googleFonts.testVersion")}"
                id("org.jetbrains.compose") version "1.12.0"
                id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
            }
            group = "example"
            dependencies { implementation("org.jetbrains.compose.ui:ui:1.12.0") }
            compose.resources {
                packageOfResClass = "example.resources"
                nameOfResClass = "AppRes"
            }
        """)
        root.resolve("src/main/composeResources/files/existing.txt").apply {
            parentFile.mkdirs(); writeText("existing resource")
        }
        root.resolve("src/main/kotlin/Usage.kt").apply {
            parentFile.mkdirs()
            writeText("""
                import androidx.compose.runtime.Composable
                import com.composables.googlefonts.generated.GoogleFonts
                @Composable fun useBundledFont() = GoogleFonts.Inter
            """.trimIndent())
        }
        val first = run(root, "jar")
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateGoogleFonts")?.outcome)
        val jar = root.resolve("build/libs").listFiles()!!.single { it.extension == "jar" }
        ZipFile(jar).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(4, entries.count { it.endsWith(".ttf") })
            assertTrue(entries.any { it.endsWith("googleFonts/licenses/Inter.txt") })
            assertTrue(entries.any { it.endsWith("files/existing.txt") })
            assertTrue(entries.any { it.endsWith("GoogleFonts.class") })
        }
        val second = run(root, "jar")
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateGoogleFonts")?.outcome)
        assertContains(second.output, "Reusing configuration cache")
        val lock = root.resolve("google-fonts.lock").readText()
        run(root, "clean", "jar", "--offline")
        assertEquals(lock, root.resolve("google-fonts.lock").readText())
    }

    @Test fun `CMP builds JVM and Wasm automatically and preserves existing resources`() {
        val root = fixture("""
            plugins {
                kotlin("multiplatform") version "2.4.0"
                id("com.android.kotlin.multiplatform.library") version "9.2.0"
                id("org.jetbrains.compose") version "1.12.0"
                id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
                id("com.composables.google-fonts") version "${System.getProperty("googleFonts.testVersion")}"
            }
            group = "example"
            kotlin {
                jvm()
                wasmJs { browser() }
                iosSimulatorArm64()
                targets.withType<com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget>().configureEach {
                    namespace = "example.shared"
                    compileSdk = 37
                    minSdk = 23
                }
                sourceSets.commonMain.dependencies {
                    implementation("org.jetbrains.compose.ui:ui:1.12.0")
                }
            }
            compose.resources {
                packageOfResClass = "example.resources"
                nameOfResClass = "AppRes"
            }
        """)
        root.resolve("src/commonMain/composeResources/files/existing.txt").apply { parentFile.mkdirs(); writeText("existing resource") }
        root.resolve("src/commonMain/kotlin/Usage.kt").apply {
            parentFile.mkdirs()
            writeText("""
                import androidx.compose.runtime.Composable
                import com.composables.googlefonts.generated.GoogleFonts
                @Composable fun useBundledFont() = GoogleFonts.Inter
            """.trimIndent())
        }
        root.resolve("src/wasmJsMain/kotlin/Preload.kt").apply {
            parentFile.mkdirs()
            writeText("""
                import androidx.compose.runtime.Composable
                import com.composables.googlefonts.generated.GoogleFonts
                import com.composables.googlefonts.generated.preload
                @Composable fun fontsReady() = GoogleFonts.preload()
            """.trimIndent())
        }
        val first = run(root, "compileKotlinJvm", "compileKotlinWasmJs", "compileKotlinIosSimulatorArm64", "bundleAndroidMainAar", "jvmJar")
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateGoogleFonts")?.outcome)
        assertTrue(root.resolve("build/generated/googleFonts/composeResources/files/existing.txt").isFile)
        assertTrue(root.resolve("build/generated/googleFonts/composeResources/files/googleFonts/licenses/Inter.txt").isFile)
        val jar = root.resolve("build/libs").listFiles()!!.single { it.extension == "jar" }
        ZipFile(jar).use { zip ->
            assertTrue(zip.entries().asSequence().any { it.name.endsWith("googlefonts_inter_400_normal.ttf") })
            assertTrue(zip.entries().asSequence().any { it.name.endsWith("googleFonts/licenses/Inter.txt") })
        }
        val aar = root.resolve("build/outputs/aar").listFiles()!!.single { it.extension == "aar" }
        ZipFile(aar).use { zip ->
            assertTrue(zip.entries().asSequence().any { it.name.endsWith("googlefonts_inter_400_normal.ttf") })
            assertTrue(zip.entries().asSequence().any { it.name.endsWith("googleFonts/licenses/Inter.txt") })
        }
        val second = run(root, "compileKotlinJvm", "compileKotlinWasmJs", "compileKotlinIosSimulatorArm64", "bundleAndroidMainAar", "jvmJar")
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateGoogleFonts")?.outcome)
        assertContains(second.output, "Reusing configuration cache")
        val lock = root.resolve("google-fonts.lock").readText()
        run(root, "clean", "compileKotlinJvm", "--offline")
        assertEquals(lock, root.resolve("google-fonts.lock").readText())
    }

    @Test fun `Android builds without CMP and packages fonts and licenses`() {
        val root = fixture("""
            plugins {
                id("com.android.application") version "9.2.0"
                id("org.jetbrains.kotlin.plugin.compose") version "2.4.0"
                id("com.composables.google-fonts") version "${System.getProperty("googleFonts.testVersion")}"
            }
            android {
                namespace = "example.app"
                compileSdk = 37
                defaultConfig { minSdk = 23 }
                buildFeatures { compose = true }
            }
            dependencies { implementation("org.jetbrains.compose.ui:ui:1.12.0") }
        """)
        val sdk = System.getenv("ANDROID_HOME") ?: "${System.getProperty("user.home")}/Library/Android/sdk"
        root.resolve("local.properties").writeText("sdk.dir=$sdk\n")
        root.resolve("src/main/AndroidManifest.xml").apply {
            parentFile.mkdirs()
            writeText("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"><application /></manifest>")
        }
        root.resolve("src/main/kotlin/Usage.kt").apply {
            parentFile.mkdirs()
            writeText("""
                import androidx.compose.runtime.Composable
                import com.composables.googlefonts.generated.GoogleFonts
                @Composable fun useBundledFont() = GoogleFonts.Inter
            """.trimIndent())
        }
        val first = run(root, "assembleDebug")
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateGoogleFonts")?.outcome)
        val source = root.resolve("build/generated/googleFonts/kotlin/com/composables/googlefonts/generated/GoogleFonts.kt").readText()
        assertFalse(source.contains("org.jetbrains.compose.resources"))
        val apk = root.resolve("build/outputs/apk/debug/fixture-debug.apk")
        ZipFile(apk).use { zip ->
            assertNotNull(zip.getEntry("res/font/googlefonts_inter_400_normal.ttf"))
            assertNotNull(zip.getEntry("assets/googleFonts/licenses/Inter.txt"))
        }
        val second = run(root, "assembleDebug")
        assertContains(second.output, "Reusing configuration cache")
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateGoogleFonts")?.outcome)
    }

    private fun fixture(plugins: String): File {
        val root = temporary.newFolder()
        root.resolve("settings.gradle.kts").writeText("""
            pluginManagement { repositories {
                maven { url = uri("${File(System.getProperty("googleFonts.testRepository")).toURI()}") }
                google(); mavenCentral(); gradlePluginPortal()
            } }
            dependencyResolutionManagement { repositories { google(); mavenCentral() } }
            rootProject.name = "fixture"
        """.trimIndent())
        root.resolve("gradle.properties").writeText("android.useAndroidX=true\norg.gradle.jvmargs=-Xmx2g -Djava.awt.headless=true\n")
        val sdk = System.getenv("ANDROID_HOME") ?: "${System.getProperty("user.home")}/Library/Android/sdk"
        root.resolve("local.properties").writeText("sdk.dir=$sdk\n")
        root.resolve("build.gradle.kts").writeText(plugins.trimIndent() + "\n" + """
            googleFonts { family("Inter") { weights(400, 700); italic() } }
            tasks.named<com.composables.googlefonts.gradle.GenerateGoogleFonts>("generateGoogleFonts") {
                cacheDirectory.set(layout.projectDirectory.dir("font-cache"))
            }
        """.trimIndent())
        val font = byteArrayOf(0, 1, 0, 0) + ByteArray(20) { 7 }
        val license = "SIL OPEN FONT LICENSE".toByteArray()
        val cache = root.resolve("font-cache").apply { mkdirs() }
        cache.resolve(sha256(font)).writeBytes(font)
        cache.resolve(sha256(license)).writeBytes(license)
        root.resolve("google-fonts.lock").writeText(buildString {
            appendLine("Inter.license.family=Inter")
            appendLine("Inter.license.url=https://raw.githubusercontent.com/google/fonts/main/ofl/inter/OFL.txt")
            appendLine("Inter.license.sha256=${sha256(license)}")
            BundledFamily("Inter", "Inter", listOf(400, 700), true).faces.forEach {
                appendLine("${it.resourceName}.family=Inter")
                appendLine("${it.resourceName}.url=https://fonts.gstatic.com/font.ttf")
                appendLine("${it.resourceName}.sha256=${sha256(font)}")
            }
        })
        return root
    }

    private fun run(root: File, vararg tasks: String) = GradleRunner.create()
        .withProjectDir(root)
        .withArguments(*tasks, "--configuration-cache", "--stacktrace", "--console=plain")
        .build()
}
