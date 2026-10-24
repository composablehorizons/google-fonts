plugins {
    id("com.composables.kpm")

    kotlin("jvm")
    `java-gradle-plugin`
}

kotlin { jvmToolchain(17) }

gradlePlugin {
    plugins {
        create("googleFonts") {
            id = "com.composables.google-fonts"
            implementationClass = "com.composables.googlefonts.gradle.GoogleFontsPlugin"
            displayName = "Compose Google Fonts"
            description = "Bundle Google Fonts automatically in Android and Compose Multiplatform apps."
        }
    }
}

dependencies {
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    compileOnly("org.jetbrains.compose:compose-gradle-plugin:1.12.0")
    compileOnly("com.android.tools.build:gradle-api:9.2.0")
    testImplementation(gradleTestKit())
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    dependsOn("publishAllPublicationsToFunctionalTestRepository")
    systemProperty("googleFonts.testRepository", layout.buildDirectory.dir("functional-test-repository").get().asFile.absolutePath)
    systemProperty("googleFonts.testVersion", project.version.toString())
    maxParallelForks = 1
    systemProperty("java.awt.headless", "true")
}

publishing {
    repositories {
        maven {
            name = "functionalTest"
            url = layout.buildDirectory.dir("functional-test-repository").get().asFile.toURI()
        }
    }
}
