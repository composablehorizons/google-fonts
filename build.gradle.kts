import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget

plugins {
    id("com.android.kotlin.multiplatform.library") version "9.2.0" apply false
    alias(libs.plugins.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose.compiler)
    id("com.composables.kpm") version "0.1.0" apply false
}

val standalone = project == rootProject
val wasmOnly =
    !standalone &&
        providers.gradleProperty("storyboard.wasmOnly").map(String::toBooleanStrict).getOrElse(false)
val development =
    !standalone && !wasmOnly &&
        providers
            .gradleProperty("storyboard.development")
            .map(String::toBooleanStrict)
            .getOrElse(true)
val mobile = standalone || (!development && !wasmOnly)

if (mobile) pluginManager.apply("com.android.kotlin.multiplatform.library")

kotlin {
    jvmToolchain(17)
    if (!wasmOnly) jvm()
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    if (!development) wasmJs { browser() }
    if (mobile) {
        targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
            namespace = "com.composables.googlefonts"
            compileSdk = 37
            minSdk = 23
        }
        iosArm64()
        iosSimulatorArm64()
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.ui)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
        }
        if (!development) wasmJsMain.dependencies { implementation("io.ktor:ktor-client-js:3.6.0") }
        if (mobile) androidMain.dependencies { implementation("io.ktor:ktor-client-okhttp:3.6.0") }
        if (mobile) iosMain.dependencies { implementation("io.ktor:ktor-client-darwin:3.6.0") }
        if (!wasmOnly) jvmTest.dependencies { implementation(kotlin("test")) }
    }
}

if (standalone) pluginManager.apply("com.composables.kpm")
